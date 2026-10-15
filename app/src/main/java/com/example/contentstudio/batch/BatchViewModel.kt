package com.example.contentstudio.batch

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.contentstudio.processing.*
import com.example.contentstudio.documents.*
import com.example.contentstudio.storage.StorageCapacityException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class BatchState(val batch: BatchQueue = BatchQueue(), val busy: Boolean = true,
    val ready: Boolean = false, val progress: Int? = null, val approved: Set<String> = emptySet(),
    val archive: ProcessingOutput? = null, val error: Boolean = false, val saved: Boolean = false,
    val documentApproved: Set<String> = emptySet(), val documentError: BatchDocumentIssue? = null,
    val capacityError: Boolean = false)

abstract class BatchViewModel(application: Application, val mediaKind: BatchKind) : AndroidViewModel(application) {
    private val engine = BatchEngine(application, mediaKind)
    private val jobs = ProcessingJobs.get(application)
    private val kind = mediaKind.processing
    private val mutable = MutableStateFlow(BatchState(batch = BatchQueue(options = BatchOptions(kind = mediaKind))))
    val state = mutable.asStateFlow()
    private var operation: Job? = null
    private var packingRevision: String? = null
    init { viewModelScope.launch {
        jobs.awaitReady()
        if (jobs.records.value[kind]?.status?.active == true)
            jobs.records.first { it[kind]?.status?.active != true }
        reload()
        jobs.records.map { it[kind] }.distinctUntilChanged().collect { record ->
            if (record?.status?.active == true) mutable.update { it.copy(busy = true, progress = record.progress) }
            else if (state.value.busy && operation?.isActive != true) {
                val revision = packingRevision
                val archive = record?.output?.takeIf {
                    record.status == ProcessingStatus.SUCCEEDED && it.name.endsWith(".zip") && revision == state.value.batch.revision
                }
                packingRevision = null
                reload(archive, revision, record?.status == ProcessingStatus.FAILED)
            }
        }
    } }
    private suspend fun reload(archive: ProcessingOutput? = state.value.archive,
        revision: String? = state.value.batch.revision, failed: Boolean = false) {
        try {
            val keep = jobs.records.value[kind]?.output?.name
            val saved = withContext(Dispatchers.IO) { engine.read().also { engine.pruneOutputs(keep) } }
            mutable.update { it.copy(batch = saved, busy = false, ready = true, progress = null,
                archive = archive?.takeIf { revision == saved.revision }, error = it.error || failed,
                approved = it.approved.filterTo(mutableSetOf()) { key -> saved.items.any { item -> item.copyKey == key } },
                documentApproved = it.documentApproved.filterTo(mutableSetOf()) { key -> saved.items.any { item -> item.document?.key(item, saved.options) == key } }) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { mutable.update { it.copy(busy = false, ready = true, archive = null, error = true) } }
    }
    private fun edit(work: suspend () -> BatchQueue) {
        if (state.value.busy || !state.value.ready) return
        val oldArchive = state.value.archive
        mutable.update { it.copy(busy = true, error = false, saved = false, archive = null, documentError = null, capacityError = false) }
        operation = viewModelScope.launch {
            try {
                val next = withContext(Dispatchers.IO) { work() }
                mutable.update { it.copy(batch = next) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { mutable.update { it.copy(error = true, capacityError = failure is StorageCapacityException,
                documentError = (failure as? DocumentException)?.let { e -> BatchDocumentIssue.valueOf(e.issue.name) }) } }
            catch (_: OutOfMemoryError) { mutable.update { it.copy(error = true) } }
            finally {
                withContext(NonCancellable) {
                    withContext(Dispatchers.IO) { oldArchive?.let { runCatching { it.resolve(engine.folder).delete() } } }
                    reload()
                }
            }
        }
    }
    fun add(uris: List<Uri>) = edit { engine.add(uris) }
    fun options(value: BatchOptions) { if (value != state.value.batch.options) edit { engine.options(value) } }
    fun include(id: String, value: Boolean) = edit { engine.include(id, value) }
    fun remove(id: String) = edit { engine.remove(id) }
    fun document(id: String, sheet: Int, mode: OfficeCellMode) = edit { engine.document(id, sheet, mode) }
    fun approveDocument(key: String, approved: Boolean) {
        val current = state.value
        if (current.busy || current.batch.items.none { item -> item.document?.let { d -> d.office != null && d.sheet >= 0 &&
            current.batch.options.documentTarget in DocumentPolicy.targets(d.kind) && d.key(item, current.batch.options) == key } == true }) return
        mutable.update { it.copy(documentApproved = if (approved) it.documentApproved + key else it.documentApproved - key,
            archive = null, saved = false) }
        current.archive?.let { archive -> viewModelScope.launch(Dispatchers.IO) { runCatching { archive.resolve(engine.folder).delete() } } }
    }
    fun clear() = edit { withContext(Dispatchers.Main.immediate) { jobs.forget(kind) }; engine.clear() }
    suspend fun clearSavedWork(): Boolean {
        jobs.awaitReady()
        if (jobs.records.value[kind]?.status?.active == true) return false
        state.first { it.ready }
        if (state.value.busy) return false
        clear()
        operation?.join()
        return !state.value.error && state.value.batch.items.isEmpty()
    }
    fun confirmCopy(key: String) {
        if (!state.value.busy && state.value.batch.items.any { it.copyKey == key && it.needsConsent })
            mutable.update { it.copy(approved = it.approved + key) }
    }
    fun convert() {
        val current = state.value
        if (current.busy || !current.ready || current.batch.items.isEmpty() || current.batch.items.all { it.output != null }) return
        start(null) { progress -> withContext(Dispatchers.IO) {
            engine.convert(current.batch.revision, progress, current.documentApproved) { batch, percent ->
                mutable.update { it.copy(batch = batch) }; progress(percent)
            }
        } }
    }
    /** The review dialog supplies its captured revision, including all excluded/failed rows. */
    fun pack(reviewedRevision: String) {
        val current = state.value
        if (current.busy || current.batch.revision != reviewedRevision || current.archive != null) return
        if (runCatching { BatchPolicy.requireExportable(current.batch, current.approved, current.documentApproved) }.isFailure) return
        start(reviewedRevision) { progress -> withContext(Dispatchers.IO) {
            engine.zip(reviewedRevision, current.approved, current.documentApproved) { progress(it) }
        } }
    }
    private fun start(zipRevision: String?, work: suspend ((Int?) -> Unit) -> ProcessingOutput) {
        val oldArchive = state.value.archive
        mutable.update { it.copy(busy = true, error = false, saved = false, archive = null, documentError = null, capacityError = false) }
        packingRevision = zipRevision
        try { jobs.start(kind, BatchPolicy.encode(state.value.batch)) { progress ->
            withContext(Dispatchers.IO) { oldArchive?.let { runCatching { it.resolve(engine.folder).delete() } } }
            try { work(progress) }
            catch (failure: StorageCapacityException) {
                mutable.update { it.copy(capacityError = true) }
                throw failure
            }
        } }
        catch (_: Exception) { packingRevision = null; mutable.update { it.copy(busy = false, error = true) } }
    }
    fun export(uri: Uri, archiveName: String?) {
        val current = state.value; val archive = current.archive
        if (current.busy || archive == null || archive.name != archiveName) {
            // A stale callback cannot establish ownership of its target. Never delete it:
            // it may refer to an original from a queue that has since been cleared.
            mutable.update { it.copy(error = true) }; return
        }
        mutable.update { it.copy(busy = true, error = false, saved = false, capacityError = false) }
        operation = viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { engine.export(archive, current.batch.revision, current.approved, uri, current.documentApproved) }
                mutable.update { it.copy(saved = true) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { mutable.update { it.copy(error = true, capacityError = failure is StorageCapacityException) } }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
    fun cancel() { if (jobs.records.value[kind]?.status?.active == true) jobs.cancel(kind) else operation?.cancel() }
}

class BatchImageViewModel(application: Application) : BatchViewModel(application, BatchKind.IMAGE)
class BatchVideoViewModel(application: Application) : BatchViewModel(application, BatchKind.VIDEO)
class BatchAudioViewModel(application: Application) : BatchViewModel(application, BatchKind.AUDIO)
class BatchDocumentViewModel(application: Application) : BatchViewModel(application, BatchKind.DOCUMENT)

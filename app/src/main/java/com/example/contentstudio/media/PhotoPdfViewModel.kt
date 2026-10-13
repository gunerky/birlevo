package com.example.contentstudio.media

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import com.example.contentstudio.processing.*
import java.io.File

enum class PhotoPdfMessage { IMPORT_ERROR, GENERATE_ERROR, EXPORT_ERROR, EXPORTED }
data class PhotoPdfState(val pages: List<PhotoPage> = emptyList(), val maxEdge: Int = 2048,
    val result: PresentationPdfResult? = null, val preview: Bitmap? = null, val pageIndex: Int = 0,
    val busy: Boolean = false, val progress: Int? = null, val message: PhotoPdfMessage? = null,
    val issue: ImportIssue? = null, val workStatus: ProcessingStatus? = null)

class PhotoPdfViewModel @JvmOverloads constructor(application: Application,
    storage: MediaSessionStorage = AtomicMediaSession(File(application.noBackupFilesDir, "photo-pdf"))) : AndroidViewModel(application) {
    private val engine = PhotoPdf(application)
    private val mutable = MutableStateFlow(PhotoPdfState(busy = true))
    val state = mutable.asStateFlow()
    private var operation: Job? = null
    @Volatile private var generation = 0
    private val session = MediaSessionWriter(viewModelScope, storage)
    val sessionStatus = session.status
    private var restoreFailed = false
    private val processing = ProcessingJobs.get(application)
    private val restored = CompletableDeferred<Unit>()
    private var resetJob: Job? = null
    val sessionRestoreFailed get() = restoreFailed
    init { restoreSession(); observeProcessing() }
    private fun restoreSession() {
        runOperation(PhotoPdfMessage.IMPORT_ERROR) { _ ->
            try {
                val text = session.read()
                if (text != null) {
                    val saved = withContext(Dispatchers.IO) { MediaSessionCodec.photos(text) }
                    val pages = withContext(Dispatchers.IO) { engine.restore(saved) }
                    mutable.value = PhotoPdfState(pages = pages, maxEdge = saved.edge, busy = true)
                }
                session.initialize(checkpoint()); restoreFailed = false
                processing.awaitReady()
                if (processing.recoveryFailed && state.value.pages.isNotEmpty()) mutable.update { it.copy(workStatus = ProcessingStatus.INTERRUPTED) }
                val retained = state.value.pages.map { it.file }.toSet()
                val request = checkpoint().json
                withContext(Dispatchers.IO) { runCatching { engine.cleanOldCache(retained + setOfNotNull(
                    processing.retainedOutput(ProcessingKind.PHOTO_PDF, request))) } }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Throwable) { restoreFailed = true; throw failure }
            finally { restored.complete(Unit) }
        }
    }
    private fun checkpoint(): SessionSnapshot {
        val value = state.value
        return SessionSnapshot(MediaSessionCodec.photos(PhotoSession(value.pages.map(MediaSessionCodec::asset), value.maxEdge)), value.pages.map { it.file }.toSet())
    }
    fun flushSession() { viewModelScope.launch { session.flush() } }
    suspend fun awaitSession(): Boolean { resetJob?.join(); return session.flush() }
    fun retrySession() { if (restoreFailed && !state.value.busy) restoreSession() else session.retry() }

    fun add(uris: List<Uri>) {
        val snapshot = state.value
        if (snapshot.busy || uris.isEmpty()) return
        runOperation(PhotoPdfMessage.IMPORT_ERROR) { token ->
            var imported = emptyList<PhotoPage>()
            var attached = false
            try {
                withContext(Dispatchers.IO) {
                    imported = engine.importPages(uris, PhotoPdf.MAX_PAGES - snapshot.pages.size,
                        PhotoPdf.MAX_BATCH_BYTES - snapshot.pages.sumOf { it.bytes }) { updateProgress(token, it) }
                }
                if (token == generation) {
                    invalidate(snapshot.pages + imported, snapshot.maxEdge)
                    attached = true
                }
            } finally {
                if (!attached) withContext(NonCancellable + Dispatchers.IO) { imported.forEach { it.file.delete() } }
            }
        }
    }
    /** Wait for the owned snapshot before the producing tool removes its working file. */
    suspend fun addOutput(uri: Uri): Boolean {
        if (state.value.busy || state.value.pages.size >= PhotoPdf.MAX_PAGES) return false
        val before = state.value.pages.map { it.id }
        add(listOf(uri))
        val job = operation ?: return false
        job.join()
        return state.value.pages.size == before.size + 1 && state.value.pages.take(before.size).map { it.id } == before
    }
    fun move(id: String, offset: Int) {
        if (state.value.busy || offset !in listOf(-1, 1)) return
        val pages = state.value.pages.toMutableList()
        val index = pages.indexOfFirst { it.id == id }
        if (index < 0 || index + offset !in pages.indices) return
        java.util.Collections.swap(pages, index, index + offset)
        invalidate(pages, state.value.maxEdge)
    }
    fun remove(id: String) {
        if (state.value.busy) return
        if (state.value.pages.none { it.id == id }) return
        invalidate(state.value.pages.filterNot { it.id == id }, state.value.maxEdge)
    }
    fun setMaxEdge(edge: Int) {
        require(edge in listOf(2048, 4096))
        if (!state.value.busy && state.value.maxEdge != edge) invalidate(state.value.pages, edge)
    }
    private fun invalidate(pages: List<PhotoPage>, edge: Int) {
        processing.dropFinished(ProcessingKind.PHOTO_PDF)
        val removed = state.value.pages.map { it.file }.toSet() - pages.map { it.file }.toSet()
        val old = state.value.result?.file
        mutable.update { it.copy(pages = pages, maxEdge = edge, result = null, preview = null, pageIndex = 0, message = null) }
        restoreFailed = false; session.update(checkpoint(), removed)
        viewModelScope.launch(Dispatchers.IO) { old?.delete() }
    }
    fun generate() {
        val snapshot = state.value
        if (snapshot.busy || snapshot.pages.isEmpty()) return
        mutable.update { it.copy(busy = true, message = null, issue = null, progress = null) }
        try { processing.start(ProcessingKind.PHOTO_PDF, checkNotNull(checkpoint().json)) { progress ->
            check(session.flush()) { "Source session could not be saved" }
            var output: ProcessingOutput? = null
            var delivered = false
            try {
                withContext(Dispatchers.IO) {
                    val result = engine.create(snapshot.pages, snapshot.maxEdge) { progress(it) }
                    output = ProcessingOutput(result.file.name, result.bytes, result.pages)
                }
                checkNotNull(output).also { delivered = true }
            } finally { if (!delivered) withContext(NonCancellable + Dispatchers.IO) {
                output?.let { it.resolve(File(getApplication<Application>().noBackupFilesDir, "photo-pdf")).delete() }
            } }
        } } catch (_: Exception) { mutable.update { it.copy(busy = false, message = PhotoPdfMessage.GENERATE_ERROR) } }
    }
    private fun observeProcessing() { viewModelScope.launch {
        restored.await(); processing.awaitReady()
        processing.records.map { it[ProcessingKind.PHOTO_PDF] }.distinctUntilChanged().collectLatest { record ->
            record ?: return@collectLatest
            if (record.request != ProcessingCodec.fingerprint(checkpoint().json)) return@collectLatest
            if (record.status.active) {
                mutable.update { it.copy(busy = true, progress = record.progress, workStatus = record.status) }
            } else if (record.status == ProcessingStatus.SUCCEEDED && state.value.result?.file?.name != record.output?.name) {
                var result: PresentationPdfResult? = null; var bitmap: Bitmap? = null; var delivered = false
                mutable.update { it.copy(busy = true, workStatus = record.status) }
                try {
                    withContext(Dispatchers.IO) {
                        val output = checkNotNull(record.output)
                        result = engine.restoreOutput(output.resolve(File(getApplication<Application>().noBackupFilesDir, "photo-pdf")), output.bytes, output.pages)
                        bitmap = engine.render(checkNotNull(result), 0)
                    }
                    if (record.request == ProcessingCodec.fingerprint(checkpoint().json)) {
                        val old = state.value.result?.file
                        mutable.update { it.copy(result = result, preview = bitmap, pageIndex = 0, busy = false, progress = null, message = null) }
                        delivered = true
                        if (old != result?.file) withContext(Dispatchers.IO) { old?.delete() }
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { mutable.update { it.copy(busy = false, message = PhotoPdfMessage.GENERATE_ERROR) } }
                finally { if (!delivered) bitmap?.recycle() }
            } else if (record.status != ProcessingStatus.SUCCEEDED) {
                mutable.update { it.copy(busy = false, progress = null, workStatus = record.status,
                    message = if (record.status == ProcessingStatus.FAILED) PhotoPdfMessage.GENERATE_ERROR else null) }
            }
        }
    } }
    fun showPage(index: Int) {
        val snapshot = state.value
        val result = snapshot.result ?: return
        if (snapshot.busy || index == snapshot.pageIndex || index !in 0 until result.pages) return
        runOperation(PhotoPdfMessage.GENERATE_ERROR) { token ->
            var bitmap: Bitmap? = null
            var delivered = false
            try {
                withContext(Dispatchers.IO) { bitmap = engine.render(result, index) }
                if (token == generation) { mutable.update { it.copy(preview = bitmap, pageIndex = index) }; delivered = true }
            } finally { if (!delivered) bitmap?.recycle() }
        }
    }
    fun export(uri: Uri, expectedResult: String? = state.value.result?.file?.name, pickerOriginals: List<String> = emptyList()) {
        val snapshot = state.value
        val result = snapshot.result
        if (snapshot.pages.any { it.sourceUri == uri } || uri.toString() in pickerOriginals) {
            mutable.update { it.copy(message = PhotoPdfMessage.EXPORT_ERROR) }; return
        }
        if (snapshot.busy || result == null || result.file.name != expectedResult) {
            viewModelScope.launch(Dispatchers.IO) { runCatching {
                DocumentsContract.deleteDocument(getApplication<Application>().contentResolver, uri) } }
            mutable.update { it.copy(message = PhotoPdfMessage.EXPORT_ERROR) }; return
        }
        runOperation(PhotoPdfMessage.EXPORT_ERROR) { _ ->
            withContext(Dispatchers.IO) { engine.export(result, snapshot.pages, uri) }
            mutable.update { it.copy(message = PhotoPdfMessage.EXPORTED) }
        }
    }
    fun cancel() { processing.cancel(ProcessingKind.PHOTO_PDF); operation?.cancel() }
    fun leave() { if (state.value.workStatus?.active != true) operation?.cancel(); flushSession() }
    fun reset() {
        generation++
        val previous = operation
        previous?.cancel()
        processing.cancel(ProcessingKind.PHOTO_PDF)
        val snapshot = state.value
        mutable.value = PhotoPdfState(busy = true)
        restoreFailed = false
        resetJob = viewModelScope.launch {
            previous?.join()
            processing.forget(ProcessingKind.PHOTO_PDF)
            session.update(SessionSnapshot(null), snapshot.pages.map { it.file }.toSet())
            withContext(Dispatchers.IO) { snapshot.result?.file?.delete() }
            session.flush()
            mutable.update { it.copy(busy = false) }
        }
    }

    suspend fun clearSavedWork(): Boolean {
        restored.await()
        processing.awaitReady()
        val ready = state.first { !it.busy || it.workStatus?.active == true }
        if (ready.busy || ready.workStatus?.active == true || processing.records.value[ProcessingKind.PHOTO_PDF]?.status?.active == true) return false
        reset()
        resetJob?.join()
        return session.flush()
    }
    private fun updateProgress(token: Int, value: Int) { if (generation == token) mutable.update { it.copy(progress = value) } }
    private fun runOperation(error: PhotoPdfMessage, block: suspend (Int) -> Unit) {
        val token = ++generation
        val previous = operation
        mutable.update { it.copy(busy = true, message = null, progress = null, issue = null) }
        operation = viewModelScope.launch {
            try { previous?.cancelAndJoin(); block(token) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (token == generation) mutable.update { it.copy(message = error, issue = importIssue(failure)) } }
            catch (_: OutOfMemoryError) { if (token == generation) mutable.update { it.copy(message = error, issue = ImportIssue.MEMORY) } }
            finally { if (token == generation) mutable.update { it.copy(busy = false, progress = null) } }
        }
    }
}

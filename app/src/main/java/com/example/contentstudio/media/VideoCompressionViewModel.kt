package com.example.contentstudio.media

import android.app.Application
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import com.example.contentstudio.processing.*

data class VideoCompressionState(val source: VideoSource? = null, val result: CompressedVideo? = null,
    val quality: VideoQuality = VideoQuality.BALANCED, val maxEdge: Int = 1280,
    val trim: VideoTrim? = null, val busy: Boolean = false, val progress: Int? = null,
    val message: VideoMessage? = null, val issue: ImportIssue? = null, val workStatus: ProcessingStatus? = null)
enum class VideoMessage { LOAD_ERROR, PROCESS_ERROR, EXPORT_ERROR, EXPORTED }

class VideoCompressionViewModel @JvmOverloads constructor(application: Application,
    storage: MediaSessionStorage = AtomicMediaSession(File(application.noBackupFilesDir, "video-compression"))) : AndroidViewModel(application) {
    private val engine = VideoCompressor(application)
    private val mutableState = MutableStateFlow(VideoCompressionState(busy = true))
    val state = mutableState.asStateFlow()
    private var operation: Job? = null
    private var generation = 0
    private var confirmedCopy: File? = null
    private val session = MediaSessionWriter(viewModelScope, storage)
    val sessionStatus = session.status
    private var restoreFailed = false
    private val processing = ProcessingJobs.get(application)
    private val restored = CompletableDeferred<Unit>()
    private var resetJob: Job? = null
    val sessionRestoreFailed get() = restoreFailed

    init { restoreSession(); observeProcessing() }
    private fun restoreSession() {
        runOperation(VideoMessage.LOAD_ERROR) {
            var source: VideoSource? = null
            var delivered = false
            try {
                val text = session.read()
                if (text != null) {
                    val saved = withContext(Dispatchers.IO) { MediaSessionCodec.video(text) }
                    withContext(Dispatchers.IO) { source = engine.restore(saved.asset) }
                    val loaded = checkNotNull(source)
                    require(saved.trim.endMs <= loaded.info.durationMs)
                    mutableState.value = VideoCompressionState(source = loaded, quality = saved.quality,
                        maxEdge = saved.edge, trim = saved.trim, busy = true)
                }
                session.initialize(checkpoint())
                processing.awaitReady()
                if (processing.recoveryFailed && source != null) mutableState.update { it.copy(workStatus = ProcessingStatus.INTERRUPTED) }
                val request = checkpoint().json
                delivered = true; restoreFailed = false
                withContext(Dispatchers.IO) { runCatching { engine.cleanOldCache(setOfNotNull(source?.file,
                    processing.retainedOutput(ProcessingKind.VIDEO, request))) } }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Throwable) { restoreFailed = true; throw failure }
            finally { if (!delivered) source?.info?.poster?.recycle(); restored.complete(Unit) }
        }
    }
    private fun checkpoint(): SessionSnapshot {
        val value = state.value
        val source = value.source ?: return SessionSnapshot(null)
        return SessionSnapshot(MediaSessionCodec.video(VideoSession(MediaSessionCodec.asset(source), value.quality,
            value.maxEdge, value.trim ?: VideoTrim(0, source.info.durationMs))), setOf(source.file))
    }
    fun flushSession() { viewModelScope.launch { session.flush() } }
    suspend fun awaitSession(): Boolean { resetJob?.join(); return session.flush() }
    fun retrySession() { if (restoreFailed && !state.value.busy) restoreSession() else session.retry() }

    fun load(uri: Uri) {
        if (state.value.busy) return
        runOperation(VideoMessage.LOAD_ERROR) {
            var imported: VideoSource? = null
            var committed = false
            try {
                // Assign on IO before the cancellable dispatcher handoff, retaining cleanup ownership.
                withContext(Dispatchers.IO) { imported = engine.importVideo(uri) }
                val loaded = requireNotNull(imported)
                val old = state.value
                processing.dropFinished(ProcessingKind.VIDEO)
                mutableState.value = VideoCompressionState(source = loaded,
                    trim = VideoTrim(0, loaded.info.durationMs), busy = true)
                session.update(checkpoint(), setOfNotNull(old.source?.file))
                restoreFailed = false; confirmedCopy = null
                old.result?.file?.let(::removeFile)
                committed = true
            } finally {
                if (!committed) withContext(NonCancellable + Dispatchers.IO) {
                    imported?.file?.delete(); imported?.info?.poster?.recycle()
                }
            }
        }
    }

    fun setOptions(quality: VideoQuality = state.value.quality, maxEdge: Int = state.value.maxEdge) {
        if (state.value.busy || (quality == state.value.quality && maxEdge == state.value.maxEdge)) return
        require(maxEdge in listOf(640, 1280, 1920))
        processing.dropFinished(ProcessingKind.VIDEO)
        state.value.result?.file?.let(::removeFile)
        mutableState.update { it.copy(quality = quality, maxEdge = maxEdge, result = null, message = null) }
        confirmedCopy = null; session.update(checkpoint())
    }

    fun setTrim(startMs: Long, endMs: Long) {
        val snapshot = state.value
        val source = snapshot.source ?: return
        if (snapshot.busy) return
        val trim = VideoTrim(startMs, endMs)
        require(trim.endMs <= source.info.durationMs)
        if (trim == snapshot.trim) return
        processing.dropFinished(ProcessingKind.VIDEO)
        snapshot.result?.file?.let(::removeFile)
        mutableState.update { it.copy(trim = trim, result = null, message = null) }
        confirmedCopy = null; session.update(checkpoint())
    }

    fun preview() {
        val snapshot = state.value
        val source = snapshot.source ?: return
        if (snapshot.busy) return
        confirmedCopy = null
        val trim = snapshot.trim ?: VideoTrim(0, source.info.durationMs)
        mutableState.update { it.copy(busy = true, message = null, issue = null, progress = null) }
        try { processing.start(ProcessingKind.VIDEO, checkNotNull(checkpoint().json)) { progress ->
            check(session.flush()) { "Source session could not be saved" }
            var output: ProcessingOutput? = null
            var delivered = false
            try {
                withContext(Dispatchers.IO) {
                    val result = engine.compress(source, snapshot.quality, snapshot.maxEdge, trim, progress)
                    result.info.poster?.recycle()
                    output = ProcessingOutput(result.file.name, result.bytes, elapsedMs = result.elapsedMs)
                }
                checkNotNull(output).also { delivered = true }
            } finally { if (!delivered) withContext(NonCancellable + Dispatchers.IO) {
                output?.let { it.resolve(File(getApplication<Application>().noBackupFilesDir, "video-compression")).delete() }
            } }
        } } catch (_: Exception) { mutableState.update { it.copy(busy = false, message = VideoMessage.PROCESS_ERROR) } }
    }

    private fun observeProcessing() { viewModelScope.launch {
        restored.await(); processing.awaitReady()
        processing.records.map { it[ProcessingKind.VIDEO] }.distinctUntilChanged().collectLatest { record ->
            record ?: return@collectLatest
            if (record.request != ProcessingCodec.fingerprint(checkpoint().json)) return@collectLatest
            if (record.status.active) {
                mutableState.update { it.copy(busy = true, progress = record.progress, workStatus = record.status) }
            } else if (record.status == ProcessingStatus.SUCCEEDED && state.value.result?.file?.name != record.output?.name) {
                var result: CompressedVideo? = null
                var delivered = false
                mutableState.update { it.copy(busy = true, workStatus = record.status) }
                try {
                    withContext(Dispatchers.IO) { val output = checkNotNull(record.output)
                        result = engine.restoreOutput(output.resolve(File(getApplication<Application>().noBackupFilesDir, "video-compression")), output.bytes, output.elapsedMs) }
                    if (record.request == ProcessingCodec.fingerprint(checkpoint().json)) {
                        val old = state.value.result?.file
                        mutableState.update { it.copy(result = result, busy = false, progress = null, message = null) }
                        confirmedCopy = null; delivered = true
                        if (old != result?.file) old?.let(::removeFile)
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { mutableState.update { it.copy(busy = false, message = VideoMessage.PROCESS_ERROR) } }
                finally { if (!delivered) result?.info?.poster?.recycle() }
            } else if (record.status != ProcessingStatus.SUCCEEDED) {
                mutableState.update { it.copy(busy = false, progress = null, workStatus = record.status,
                    message = if (record.status == ProcessingStatus.FAILED) VideoMessage.PROCESS_ERROR else null) }
            }
        }
    } }

    fun confirmNonSmallerCopy(file: File): Boolean {
        if (state.value.busy || state.value.result?.file != file) return false
        confirmedCopy = file
        return true
    }

    fun export(uri: Uri, expectedResult: String? = state.value.result?.file?.name, pickerOriginal: String? = null) {
        val snapshot = state.value
        val source = snapshot.source
        val result = snapshot.result
        if (uri == source?.originalUri || uri.toString() == pickerOriginal) {
            mutableState.update { it.copy(message = VideoMessage.EXPORT_ERROR) }; return
        }
        if (snapshot.busy || source == null || result == null || result.file.name != expectedResult) {
            // A newly created document can return after process death; avoid leaving an empty output.
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { DocumentsContract.deleteDocument(getApplication<Application>().contentResolver, uri) }
            }
            mutableState.update { it.copy(message = VideoMessage.EXPORT_ERROR) }
            return
        }
        runOperation(VideoMessage.EXPORT_ERROR) {
            withContext(Dispatchers.IO) { engine.export(source, result, uri,
                allowNonSmallerCopy = confirmedCopy == result.file) }
            mutableState.update { it.copy(message = VideoMessage.EXPORTED) }
        }
    }

    fun cancel() { processing.cancel(ProcessingKind.VIDEO); operation?.cancel() }
    fun leave() { if (state.value.workStatus?.active != true) operation?.cancel(); flushSession() }
    fun reset() {
        confirmedCopy = null
        generation++
        val previous = operation
        previous?.cancel()
        processing.cancel(ProcessingKind.VIDEO)
        val old = state.value
        mutableState.value = VideoCompressionState(busy = true)
        restoreFailed = false
        resetJob = viewModelScope.launch {
            previous?.join(); processing.forget(ProcessingKind.VIDEO)
            session.update(SessionSnapshot(null), setOfNotNull(old.source?.file))
            old.result?.file?.let(::removeFile); session.flush()
            mutableState.update { it.copy(busy = false) }
        }
    }

    private fun runOperation(errorMessage: VideoMessage, block: suspend () -> Unit) {
        val token = ++generation
        val previous = operation
        mutableState.update { it.copy(busy = true, progress = null, message = null, issue = null) }
        operation = viewModelScope.launch {
            try { previous?.cancelAndJoin(); block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                android.util.Log.e("VideoCompression", "Operation failed: $errorMessage", error)
                if (token == generation) mutableState.update { it.copy(message = errorMessage, issue = importIssue(error)) }
            }
            catch (_: OutOfMemoryError) { if (token == generation) mutableState.update { it.copy(message = errorMessage, issue = ImportIssue.MEMORY) } }
            finally { if (token == generation) mutableState.update { it.copy(busy = false, progress = null) } }
        }
    }

    private fun removeFile(file: File) { viewModelScope.launch(Dispatchers.IO) { file.delete() } }
}

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

data class VideoCompressionState(val source: VideoSource? = null, val result: CompressedVideo? = null,
    val quality: VideoQuality = VideoQuality.BALANCED, val maxEdge: Int = 1280,
    val trim: VideoTrim? = null, val busy: Boolean = false, val progress: Int? = null,
    val message: VideoMessage? = null, val issue: ImportIssue? = null)
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
    val sessionRestoreFailed get() = restoreFailed

    init { restoreSession() }
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
                delivered = true; restoreFailed = false
                withContext(Dispatchers.IO) { runCatching { engine.cleanOldCache(setOfNotNull(source?.file)) } }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Throwable) { restoreFailed = true; throw failure }
            finally { if (!delivered) source?.info?.poster?.recycle() }
        }
    }
    private fun checkpoint(): SessionSnapshot {
        val value = state.value
        val source = value.source ?: return SessionSnapshot(null)
        return SessionSnapshot(MediaSessionCodec.video(VideoSession(MediaSessionCodec.asset(source), value.quality,
            value.maxEdge, value.trim ?: VideoTrim(0, source.info.durationMs))), setOf(source.file))
    }
    fun flushSession() { viewModelScope.launch { session.flush() } }
    suspend fun awaitSession(): Boolean = session.flush()
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
        runOperation(VideoMessage.PROCESS_ERROR) {
            var result: CompressedVideo? = null
            var committed = false
            try {
                withContext(Dispatchers.IO) {
                    result = engine.compress(source, snapshot.quality, snapshot.maxEdge, trim) { progress ->
                        mutableState.update { it.copy(progress = progress) }
                    }
                }
                state.value.result?.file?.let(::removeFile)
                mutableState.update { it.copy(result = result) }
                committed = true
            } finally {
                if (!committed) withContext(NonCancellable + Dispatchers.IO) {
                    result?.file?.delete(); result?.info?.poster?.recycle()
                }
            }
        }
    }

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

    fun cancel() { operation?.cancel() }
    fun reset() {
        confirmedCopy = null
        generation++
        val previous = operation
        previous?.cancel()
        val old = state.value
        mutableState.value = VideoCompressionState()
        restoreFailed = false
        session.update(SessionSnapshot(null), setOfNotNull(old.source?.file))
        viewModelScope.launch { previous?.join(); old.result?.file?.let(::removeFile); session.flush() }
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

package com.example.contentstudio.media

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class VideoFrameState(val target: VideoFrameTarget? = null, val timeMs: Long = 0,
    val frame: VideoFrame? = null, val busy: Boolean = false, val error: Boolean = false)

class VideoFrameViewModel(application: Application) : AndroidViewModel(application) {
    private val extractor = VideoFrameExtractor(application)
    private val mutable = MutableStateFlow(VideoFrameState())
    val state = mutable.asStateFlow()
    private var operation: Job? = null
    private var generation = 0

    init {
        viewModelScope.launch(Dispatchers.IO) {
            com.example.contentstudio.storage.OwnedFileCleanup.files(
                File(application.cacheDir, "video-frames"), Regex("frame-[0-9]+\\.png"),
            )
        }
    }

    fun select(target: VideoFrameTarget, timeMs: Long = 0) {
        if (state.value.busy) return
        require(timeMs in 0 until target.durationMs)
        if (state.value.target == target && state.value.timeMs == timeMs) return
        mutable.value = VideoFrameState(target, timeMs)
    }

    fun preview() = run { snapshot ->
        var frame: VideoFrame? = null
        var delivered = false
        try {
            withContext(Dispatchers.IO) { frame = extractor.read(checkNotNull(snapshot.target), snapshot.timeMs, true) }
            mutable.value = snapshot.copy(frame = frame, busy = true)
            delivered = true
        } finally { if (!delivered) frame?.bitmap?.recycle() }
    }

    fun transfer(originals: Set<Uri>, accept: suspend (File, Set<Uri>) -> Boolean, onSuccess: () -> Unit) = run { snapshot ->
        var file: File? = null
        try {
            withContext(Dispatchers.IO) { file = extractor.write(checkNotNull(snapshot.target), snapshot.timeMs) }
            check(accept(checkNotNull(file), originals)) { "Image import failed" }
            onSuccess()
        } finally { withContext(NonCancellable + Dispatchers.IO) { file?.delete() } }
    }

    fun cancel() { operation?.cancel() }
    fun clear() {
        generation++; operation?.cancel()
        mutable.value = VideoFrameState()
        // Published previews belong to Compose/RenderThread and are released by GC.
    }
    private fun run(block: suspend (VideoFrameState) -> Unit) {
        val snapshot = state.value
        if (snapshot.busy || snapshot.target == null) return
        val token = ++generation
        val previous = operation
        mutable.value = snapshot.copy(busy = true, error = false)
        operation = viewModelScope.launch {
            try { previous?.join(); block(snapshot) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (token == generation) mutable.value = mutable.value.copy(error = true) }
            catch (_: OutOfMemoryError) { if (token == generation) mutable.value = mutable.value.copy(error = true) }
            finally { if (token == generation) mutable.value = mutable.value.copy(busy = false) }
        }
    }
}

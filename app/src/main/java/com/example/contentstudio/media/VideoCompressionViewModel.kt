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
    val message: VideoMessage? = null, val issue: ImportIssue? = null, val workStatus: ProcessingStatus? = null,
    val format: VideoOutputFormat = VideoOutputFormat.MP4_AVC,
    val outputSupport: Map<VideoOutputFormat, VideoOutputIssue?> = emptyMap(),
    val clips: List<VideoClip> = emptyList(), val canvas: VideoCanvas = VideoCanvas.FIRST_CLIP,
    val fit: VideoFit = VideoFit.FIT, val speed: Float = 1f, val textLayer: VideoTextLayer? = null,
    val imageLayer: VideoImageLayer? = null, val imagePreview: android.graphics.Bitmap? = null, val audioLayer: VideoAudioLayer? = null) {
    val videoClips get() = clips.ifEmpty { source?.let { listOf(VideoClip(it, trim ?: VideoTrim(0, it.info.durationMs))) } ?: emptyList() }
    val sourceBytes get() = videoClips.sumOf { it.source.bytes }
    val durationMs get() = videoClips.sumOf { it.trim.durationMs }
    val outputDurationMs get() = kotlin.math.round(durationMs / speed.toDouble()).toLong()
    val originalUris get() = videoClips.map { it.source.originalUri.toString() } + listOfNotNull(imageLayer?.asset?.uri, audioLayer?.asset?.uri)
    fun edit() = VideoEdit(videoClips, canvas, fit, speed, textLayer, imageLayer, audioLayer)
}
enum class VideoMessage { AUDIO_LOAD_ERROR, IMAGE_LOAD_ERROR, LOAD_ERROR, PROCESS_ERROR, EXPORT_ERROR, EXPORTED }

class VideoCompressionViewModel @JvmOverloads constructor(application: Application,
    storage: MediaSessionStorage = AtomicMediaSession(File(application.noBackupFilesDir, "video-compression"))) : AndroidViewModel(application) {
    private val engine = VideoCompressor(application)
    private val imageAssets = VideoImageAssets(application)
    private val audioAssets = VideoAudioAssets(application)
    private val mutableState = MutableStateFlow(VideoCompressionState(busy = true))
    val state = mutableState.asStateFlow()
    private var operation: Job? = null
    private var supportJob: Job? = null
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
            val imported = mutableListOf<VideoSource>()
            var delivered = false
            var imagePreview: android.graphics.Bitmap? = null
            try {
                val text = session.read()
                if (text != null) {
                    val saved = withContext(Dispatchers.IO) { MediaSessionCodec.video(text) }
                    val clips = withContext(Dispatchers.IO) { saved.clips.map { clip ->
                        val source = engine.restore(clip.asset).also { imported += it }
                        VideoClip(source, clip.trim)
                    } }
                    withContext(Dispatchers.IO) { imagePreview = saved.imageLayer?.let { imageAssets.restore(it) }; saved.audioLayer?.let { audioAssets.restore(it) } }
                    val support = withContext(Dispatchers.IO) { supportFor(clips, saved.quality, saved.edge, saved.canvas, saved.fit, saved.speed, saved.textLayer, saved.imageLayer, saved.audioLayer) }
                    mutableState.value = VideoCompressionState(source = clips.first().source, quality = saved.quality,
                        maxEdge = saved.edge, trim = clips.first().trim, busy = true, format = saved.format, outputSupport = support, clips = clips, canvas = saved.canvas, fit = saved.fit, speed = saved.speed, textLayer = saved.textLayer, imageLayer = saved.imageLayer, imagePreview = imagePreview, audioLayer = saved.audioLayer)
                    delivered = true // The UI owns these posters from this point.
                }
                session.initialize(checkpoint())
                processing.awaitReady()
                if (processing.recoveryFailed && imported.isNotEmpty()) mutableState.update { it.copy(workStatus = ProcessingStatus.INTERRUPTED) }
                val request = checkpoint().json
                delivered = true; restoreFailed = false
                withContext(Dispatchers.IO) { runCatching { engine.cleanOldCache(checkpoint().files + setOfNotNull(
                    processing.retainedOutput(ProcessingKind.VIDEO, request))) } }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Throwable) { restoreFailed = true; throw failure }
            finally { if (!delivered) { imported.forEach { it.info.poster?.recycle() }; imagePreview?.recycle() }; restored.complete(Unit) }
        }
    }
    private fun checkpoint(): SessionSnapshot {
        val value = state.value
        val source = value.source ?: return SessionSnapshot(null)
        val clips = value.videoClips
        return SessionSnapshot(MediaSessionCodec.video(VideoSession(MediaSessionCodec.asset(source), value.quality,
            value.maxEdge, clips.first().trim, value.format,
            clips.map { SavedVideoClip(MediaSessionCodec.asset(it.source), it.trim) }, value.canvas, value.fit, value.speed, value.textLayer, value.imageLayer, value.audioLayer)), ownedFiles(value))
    }
    private fun assetFile(name: String) = File(getApplication<Application>().noBackupFilesDir, "video-compression/$name")
    private fun imageFiles(value: VideoCompressionState) = listOfNotNull(value.imageLayer?.asset?.file?.let(::assetFile)).toSet()
    private fun audioFiles(value: VideoCompressionState) = listOfNotNull(value.audioLayer?.asset?.file?.let(::assetFile)).toSet()
    private fun ownedFiles(value: VideoCompressionState) = value.videoClips.map { it.source.file }.toSet() + imageFiles(value) + audioFiles(value)
    fun flushSession() { viewModelScope.launch { session.flush() } }
    suspend fun awaitSession(): Boolean { resetJob?.join(); return session.flush() }
    fun retrySession() { if (restoreFailed && !state.value.busy) restoreSession() else session.retry() }

    fun load(uri: Uri) = importSource(uri, append = false)
    fun addSource(uri: Uri) = importSource(uri, append = true)
    private fun importSource(uri: Uri, append: Boolean) {
        if (state.value.busy || (append && state.value.videoClips.size != 1)) return
        supportJob?.cancel()
        runOperation(VideoMessage.LOAD_ERROR) {
            var imported: VideoSource? = null
            var committed = false
            try {
                // Assign on IO before the cancellable dispatcher handoff, retaining cleanup ownership.
                withContext(Dispatchers.IO) { imported = engine.importVideo(uri) }
                val loaded = requireNotNull(imported)
                val old = state.value
                val clips = (if (append) old.videoClips else emptyList()) + VideoClip(loaded)
                val next = if (append) old.copy(source = clips.first().source, trim = clips.first().trim,
                    clips = clips, result = null, workStatus = null) else VideoCompressionState(source = loaded,
                    trim = clips.first().trim, clips = clips, busy = true)
                val support = withContext(Dispatchers.IO) { supportFor(clips, next.quality, next.maxEdge, next.canvas, next.fit, next.speed, next.textLayer, next.imageLayer, next.audioLayer) }
                processing.dropFinished(ProcessingKind.VIDEO)
                mutableState.value = next.copy(outputSupport = support)
                session.update(checkpoint(), if (append) emptySet() else ownedFiles(old))
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

    private fun supportFor(clips: List<VideoClip>, quality: VideoQuality, edge: Int, canvas: VideoCanvas, fit: VideoFit, speed: Float, textLayer: VideoTextLayer?, imageLayer: VideoImageLayer?, audioLayer: VideoAudioLayer?): Map<VideoOutputFormat, VideoOutputIssue?> {
        if (clips.isEmpty() || clips.sumOf { it.trim.durationMs } > FormatSupport.MAX_VIDEO_DURATION_MS) return emptyMap()
        return engine.outputSupport(VideoEdit(clips, canvas, fit, speed, textLayer, imageLayer, audioLayer), quality, edge)
    }

    fun setOptions(quality: VideoQuality = state.value.quality, maxEdge: Int = state.value.maxEdge,
        format: VideoOutputFormat = state.value.format, canvas: VideoCanvas = state.value.canvas,
        fit: VideoFit = state.value.fit, speed: Float = state.value.speed) {
        require(maxEdge in listOf(640, 1280, 1920) && speed in videoSpeeds)
        updateEdit(state.value.copy(quality = quality, maxEdge = maxEdge, format = format, canvas = canvas, fit = fit, speed = speed))
    }
    fun loadImage(uri: Uri) {
        if (state.value.busy || state.value.source == null) return
        supportJob?.cancel()
        runOperation(VideoMessage.IMAGE_LOAD_ERROR) {
            var imported: ImportedVideoImage? = null
            var delivered = false
            try {
                withContext(Dispatchers.IO) { imported = imageAssets.importImage(uri) }
                val loaded = checkNotNull(imported)
                val previous = state.value
                val next = previous.copy(imageLayer = loaded.layer, imagePreview = loaded.preview, result = null, workStatus = null)
                val support = withContext(Dispatchers.IO) { supportFor(next.videoClips, next.quality, next.maxEdge,
                    next.canvas, next.fit, next.speed, next.textLayer, next.imageLayer, next.audioLayer) }
                processing.dropFinished(ProcessingKind.VIDEO)
                mutableState.value = next.copy(outputSupport = support)
                val oldImage = imageFiles(previous)
                session.update(checkpoint(), oldImage)
                confirmedCopy = null; previous.result?.file?.let(::removeFile); delivered = true
            } finally { if (!delivered) withContext(NonCancellable + Dispatchers.IO) {
                imported?.let { it.preview.recycle(); imageAssets.resolve(it.layer).delete() }
            } }
        }
    }
    fun setImageLayer(layer: VideoImageLayer?) {
        val previous = state.value.imageLayer ?: return
        require(layer == null || (layer.asset == previous.asset && layer.width == previous.width && layer.height == previous.height))
        val retire = if (layer == null) imageFiles(state.value) else emptySet()
        updateEdit(state.value.copy(imageLayer = layer, imagePreview = if (layer == null) null else state.value.imagePreview), retire)
    }
    fun loadAudio(uri: Uri) {
        if (state.value.busy || state.value.source == null) return
        supportJob?.cancel()
        runOperation(VideoMessage.AUDIO_LOAD_ERROR) {
            var imported: VideoAudioLayer? = null; var delivered = false
            try {
                withContext(Dispatchers.IO) { imported = audioAssets.importAudio(uri) }
                val loaded = checkNotNull(imported); val previous = state.value
                val next = previous.copy(audioLayer = loaded, result = null, workStatus = null)
                val support = withContext(Dispatchers.IO) { supportFor(next.videoClips, next.quality, next.maxEdge,
                    next.canvas, next.fit, next.speed, next.textLayer, next.imageLayer, next.audioLayer) }
                processing.dropFinished(ProcessingKind.VIDEO)
                mutableState.value = next.copy(outputSupport = support)
                session.update(checkpoint(), audioFiles(previous))
                confirmedCopy = null; previous.result?.file?.let(::removeFile); delivered = true
            } finally { if (!delivered) withContext(NonCancellable + Dispatchers.IO) { imported?.let { audioAssets.resolve(it).delete() } } }
        }
    }
    fun setAudioLayer(layer: VideoAudioLayer?) {
        val previous = state.value.audioLayer ?: return
        require(layer == null || (layer.asset == previous.asset && layer.durationMs == previous.durationMs &&
            layer.rate == previous.rate && layer.channels == previous.channels))
        updateEdit(state.value.copy(audioLayer = layer), if (layer == null) audioFiles(state.value) else emptySet())
    }
    fun setTextLayer(layer: VideoTextLayer?) { updateEdit(state.value.copy(textLayer = layer)) }
    fun setTrim(startMs: Long, endMs: Long) { state.value.source?.file?.let { setClipTrim(it, startMs, endMs) } }
    fun setClipTrim(file: File, startMs: Long, endMs: Long) {
        val value = state.value
        if (value.busy) return
        val trim = VideoTrim(startMs, endMs)
        val clips = value.videoClips.map { if (it.source.file == file) it.copy(trim = trim) else it }
        if (clips == value.videoClips) return
        updateEdit(value.copy(source = clips.first().source, trim = clips.first().trim, clips = clips))
    }
    fun swapSources() {
        val clips = state.value.videoClips.reversed()
        if (clips.size == 2) updateEdit(state.value.copy(source = clips.first().source, trim = clips.first().trim, clips = clips))
    }
    fun removeSource(file: File) {
        val clips = state.value.videoClips.filterNot { it.source.file == file }
        if (state.value.videoClips.size == 2 && clips.size == 1)
            updateEdit(state.value.copy(source = clips.first().source, trim = clips.first().trim, clips = clips), setOf(file))
    }
    private fun updateEdit(next: VideoCompressionState, retire: Set<File> = emptySet()) {
        if (state.value.busy || next == state.value) return
        val previous = state.value
        val sameProfile = previous.quality == next.quality && previous.maxEdge == next.maxEdge &&
            previous.canvas == next.canvas && previous.fit == next.fit && previous.speed == next.speed && previous.textLayer == next.textLayer && previous.imageLayer == next.imageLayer && previous.audioLayer == next.audioLayer &&
            ((next.textLayer == null && next.imageLayer == null && next.audioLayer == null) || previous.outputDurationMs == next.outputDurationMs) &&
            previous.videoClips.map { it.source.file } == next.videoClips.map { it.source.file } &&
            previous.durationMs <= FormatSupport.MAX_VIDEO_DURATION_MS && next.durationMs <= FormatSupport.MAX_VIDEO_DURATION_MS
        val retainedSupport = if (sameProfile) previous.outputSupport else emptyMap()
        processing.dropFinished(ProcessingKind.VIDEO)
        state.value.result?.file?.let(::removeFile)
        mutableState.value = next.copy(result = null, message = null, issue = null, workStatus = null, outputSupport = retainedSupport)
        confirmedCopy = null; session.update(checkpoint(), retire)
        supportJob?.cancel()
        if (next.videoClips.isNotEmpty() && retainedSupport.isEmpty()) supportJob = viewModelScope.launch {
            try {
                val support = withContext(Dispatchers.IO) { supportFor(next.videoClips, next.quality, next.maxEdge, next.canvas, next.fit, next.speed, next.textLayer, next.imageLayer, next.audioLayer) }
                ensureActive()
                mutableState.update { if (it.videoClips == next.videoClips && it.quality == next.quality && it.maxEdge == next.maxEdge && it.canvas == next.canvas && it.fit == next.fit && it.speed == next.speed && it.textLayer == next.textLayer && it.imageLayer == next.imageLayer && it.audioLayer == next.audioLayer)
                    it.copy(outputSupport = support) else it }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableState.update { it.copy(message = VideoMessage.PROCESS_ERROR) } }
        }
    }

    fun preview() {
        val snapshot = state.value
        val edit = runCatching { snapshot.edit() }.getOrNull() ?: return
        if (snapshot.busy || !snapshot.outputSupport.containsKey(snapshot.format) || snapshot.outputSupport[snapshot.format] != null) return
        confirmedCopy = null
        mutableState.update { it.copy(busy = true, message = null, issue = null, progress = null) }
        try { processing.start(ProcessingKind.VIDEO, checkNotNull(checkpoint().json)) { progress ->
            check(session.flush()) { "Source session could not be saved" }
            var output: ProcessingOutput? = null
            var delivered = false
            try {
                withContext(Dispatchers.IO) {
                    val result = engine.compress(edit, snapshot.quality, snapshot.maxEdge, snapshot.format, progress)
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
                        result = engine.restoreOutput(output.resolve(File(getApplication<Application>().noBackupFilesDir, "video-compression")), output.bytes, output.elapsedMs, state.value.format)
                        val snapshot = state.value
                        engine.validateEditOutput(snapshot.edit(), checkNotNull(result).info, snapshot.quality, snapshot.maxEdge, snapshot.format) }
                    if (record.request == ProcessingCodec.fingerprint(checkpoint().json)) {
                        val old = state.value.result?.file
                        // A replacement import can fail while the old result is being restored.
                        // Keep that newer error; recovering the old output does not resolve it.
                        mutableState.update { it.copy(result = result, busy = false, progress = null) }
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

    fun export(uri: Uri, expectedResult: String? = state.value.result?.file?.name, pickerOriginal: String? = null, pickerOriginals: List<String> = emptyList()) {
        val snapshot = state.value
        val source = snapshot.source
        val result = snapshot.result
        if (uri.toString() in snapshot.originalUris || uri.toString() == pickerOriginal || uri.toString() in pickerOriginals) {
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
            withContext(Dispatchers.IO) { engine.export(snapshot.edit(), result, uri,
                allowNonSmallerCopy = confirmedCopy == result.file) }
            mutableState.update { it.copy(message = VideoMessage.EXPORTED) }
        }
    }

    fun cancel() { processing.cancel(ProcessingKind.VIDEO); operation?.cancel() }
    fun leave() { if (state.value.workStatus?.active != true) operation?.cancel(); flushSession() }
    fun reset() {
        supportJob?.cancel()
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
            session.update(SessionSnapshot(null), ownedFiles(old))
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

package com.example.contentstudio.media

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.contentstudio.data.StudioProject
import com.example.contentstudio.processing.*
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class SlideshowMessage {
    IMPORT_ERROR,
    GENERATE_ERROR,
    EXPORT_ERROR,
    EXPORTED,
    RESTORE_ERROR,
    LIMIT,
    MEMORY,
}

data class SlideshowState(
    val timeline: SlideshowTimeline = SlideshowTimeline(),
    val selectedId: String? = null,
    val preview: Bitmap? = null,
    val result: CompressedVideo? = null,
    val busy: Boolean = true,
    val progress: Int? = null,
    val message: SlideshowMessage? = null,
    val issue: VideoOutputIssue? = null,
    val supportReady: Boolean = false,
    val workStatus: ProcessingStatus? = null,
)

class SlideshowViewModel
@JvmOverloads
constructor(
    application: Application,
    storage: MediaSessionStorage =
        AtomicMediaSession(File(application.noBackupFilesDir, SlideshowEngine.FOLDER)),
) : AndroidViewModel(application) {
    private val engine = SlideshowEngine(application)
    private val processing = ProcessingJobs.get(application)
    private val writer = MediaSessionWriter(viewModelScope, storage)
    val sessionStatus = writer.status
    private val mutable = MutableStateFlow(SlideshowState())
    val state = mutable.asStateFlow()
    private var operation: Job? = null
    private var previewJob: Job? = null
    private var supportJob: Job? = null
    private var operationGeneration = 0
    private val restored = CompletableDeferred<Unit>()
    private var restoreFailed = false

    init {
        restore()
        observeJobs()
    }

    private fun checkpoint(timeline: SlideshowTimeline = state.value.timeline): SessionSnapshot {
        return SessionSnapshot(
            if (timeline.frames.isEmpty()) null else SlideshowCodec.encode(timeline),
            timeline.assets.map { File(engine.folder, it.file) }.toSet(),
        )
    }

    private fun restore() {
        runOperation(SlideshowMessage.RESTORE_ERROR) {
            try {
                val json = writer.read()
                val timeline =
                    if (json == null) SlideshowTimeline()
                    else withContext(Dispatchers.IO) { engine.restore(SlideshowCodec.decode(json)) }
                mutable.update {
                    it.copy(timeline = timeline, selectedId = timeline.frames.firstOrNull()?.id)
                }
                writer.initialize(checkpoint())
                restoreFailed = false
                processing.awaitReady()
                val record = processing.records.value[ProcessingKind.SLIDESHOW]
                if (record?.status?.active != true) {
                    val retained = checkpoint().files.toMutableSet()
                    record?.output?.let { retained += File(engine.folder, it.name) }
                    withContext(Dispatchers.IO) { engine.removeStaleFiles(retained) }
                }
                if (processing.recoveryFailed && timeline.frames.isNotEmpty())
                    mutable.update { it.copy(workStatus = ProcessingStatus.INTERRUPTED) }
            } catch (failure: Throwable) {
                restoreFailed = true
                throw failure
            } finally {
                restored.complete(Unit)
            }
            refreshSupport()
            showPreview()
        }
    }

    fun retrySession() {
        if (restoreFailed && !state.value.busy) restore() else writer.retry()
    }

    fun flushSession() {
        viewModelScope.launch { writer.flush() }
    }

    suspend fun awaitSession() = writer.flush()

    fun add(uris: List<Uri>) {
        val before = state.value.timeline
        if (state.value.busy || uris.isEmpty()) return
        runOperation(SlideshowMessage.IMPORT_ERROR) {
            var after: SlideshowTimeline? = null
            var delivered = false
            try {
                withContext(Dispatchers.IO) {
                    after =
                        engine.importImages(uris, before) { p ->
                            mutable.update { it.copy(progress = p) }
                        }
                }
                replace(requireNotNull(after))
                delivered = true
            } finally {
                if (!delivered)
                    withContext(NonCancellable + Dispatchers.IO) {
                        after
                            ?.assets
                            ?.filter { it.file !in before.assets.map { a -> a.file } }
                            ?.forEach { File(engine.folder, it.file).delete() }
                    }
            }
        }
    }

    /** Copies the producer's staged output into this timeline before returning to the caller. */
    suspend fun addOutput(uri: Uri): Boolean {
        restored.await()
        if (state.value.busy) return false
        val before = state.value.timeline.frames.map { it.id }
        add(listOf(uri))
        operation?.join()
        val after = state.value.timeline.frames
        return after.size == before.size + 1 &&
            after.take(before.size).map { it.id } == before &&
            writer.flush()
    }

    /** User-approved snapshot; later project edits never mutate this video timeline. */
    fun usePresentation(project: StudioProject) {
        if (state.value.busy) return
        runOperation(SlideshowMessage.GENERATE_ERROR) {
            var after: SlideshowTimeline? = null
            var delivered = false
            try {
                withContext(Dispatchers.IO) {
                    after =
                        engine.importPresentation(project) { p ->
                            mutable.update { it.copy(progress = p) }
                        }
                }
                replace(requireNotNull(after))
                delivered = true
            } finally {
                if (!delivered)
                    withContext(NonCancellable + Dispatchers.IO) {
                        after?.assets?.forEach { File(engine.folder, it.file).delete() }
                    }
            }
        }
    }

    fun select(id: String) {
        if (state.value.timeline.frames.none { it.id == id }) return
        mutable.update { it.copy(selectedId = id) }
        showPreview()
    }

    fun duration(id: String, ms: Long) {
        if (state.value.busy || ms !in 500..60_000) return
        edit {
            it.copy(frames = it.frames.map { f -> if (f.id == id) f.copy(durationMs = ms) else f })
        }
    }

    fun move(id: String, offset: Int) {
        if (state.value.busy || offset !in listOf(-1, 1)) return
        val timeline = state.value.timeline
        val index = timeline.frames.indexOfFirst { it.id == id }
        if (index < 0 || index + offset !in timeline.frames.indices) return
        val frames = timeline.frames.toMutableList()
        java.util.Collections.swap(frames, index, index + offset)
        replace(timeline.copy(frames = frames))
    }

    fun remove(id: String) {
        if (!state.value.busy) edit { it.copy(frames = it.frames.filterNot { f -> f.id == id }) }
    }

    fun options(
        canvas: VideoCanvas = state.value.timeline.canvas,
        fit: VideoFit = state.value.timeline.fit,
        quality: VideoQuality = state.value.timeline.quality,
        edge: Int = state.value.timeline.edge,
    ) {
        if (!state.value.busy)
            edit { it.copy(canvas = canvas, fit = fit, quality = quality, edge = edge) }
    }

    private fun edit(change: (SlideshowTimeline) -> SlideshowTimeline) {
        try {
            val value = change(state.value.timeline)
            value.validate()
            if (value != state.value.timeline) replace(value)
        } catch (_: IllegalArgumentException) {
            mutable.update { it.copy(message = SlideshowMessage.LIMIT) }
        }
    }

    private fun replace(timeline: SlideshowTimeline) {
        timeline.validate()
        val nextCheckpoint = checkpoint(timeline)
        processing.dropFinished(ProcessingKind.SLIDESHOW)
        val old = state.value
        val kept = timeline.assets.map { File(engine.folder, it.file) }.toSet()
        mutable.update {
            it.copy(
                timeline = timeline,
                result = null,
                preview = null,
                selectedId =
                    it.selectedId?.takeIf { id -> timeline.frames.any { f -> f.id == id } }
                        ?: timeline.frames.firstOrNull()?.id,
                message = null,
                progress = null,
                supportReady = false,
                workStatus = null,
            )
        }
        restoreFailed = false
        writer.update(
            nextCheckpoint,
            old.timeline.assets.map { File(engine.folder, it.file) }.toSet() - kept,
        )
        viewModelScope.launch(Dispatchers.IO) { old.result?.file?.delete() }
        refreshSupport()
        showPreview()
    }

    private fun refreshSupport() {
        supportJob?.cancel()
        val timeline = state.value.timeline
        mutable.update { it.copy(supportReady = false, issue = null) }
        supportJob =
            viewModelScope.launch {
                try {
                    val issue = withContext(Dispatchers.IO) { engine.issue(timeline) }
                    if (timeline == state.value.timeline)
                        mutable.update { it.copy(issue = issue, supportReady = true) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (timeline == state.value.timeline)
                        mutable.update {
                            it.copy(issue = VideoOutputIssue.VIDEO_ENCODER, supportReady = true)
                        }
                }
            }
    }

    private fun showPreview() {
        previewJob?.cancel()
        val value = state.value
        val index = value.timeline.frames.indexOfFirst { it.id == value.selectedId }
        if (index < 0) {
            mutable.update { it.copy(preview = null) }
            return
        }
        previewJob =
            viewModelScope.launch {
                var bitmap: Bitmap? = null
                var delivered = false
                try {
                    bitmap = PreviewLoader.load { engine.preview(value.timeline, index) }
                    if (
                        value.timeline == state.value.timeline &&
                            value.selectedId == state.value.selectedId
                    ) {
                        mutable.update { it.copy(preview = bitmap) }
                        delivered = true
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    mutable.update { it.copy(message = SlideshowMessage.GENERATE_ERROR) }
                } catch (_: OutOfMemoryError) {
                    mutable.update { it.copy(message = SlideshowMessage.MEMORY) }
                } finally {
                    if (!delivered) bitmap?.recycle()
                }
            }
    }

    fun generate() {
        val value = state.value
        if (
            value.busy ||
                value.timeline.frames.isEmpty() ||
                !value.supportReady ||
                value.issue != null
        )
            return
        val json = checkNotNull(checkpoint().json)
        mutable.update { it.copy(busy = true, progress = null, message = null) }
        try {
            processing.start(ProcessingKind.SLIDESHOW, json) { progress ->
                check(writer.flush()) { "Could not retain still-video sources" }
                var result: CompressedVideo? = null
                var delivered = false
                try {
                    val output =
                        withContext(Dispatchers.IO) {
                            engine.create(value.timeline, progress).also {
                                result = it
                                it.info.poster?.recycle()
                            }
                        }
                    ProcessingOutput(output.file.name, output.bytes, elapsedMs = output.elapsedMs)
                        .also { delivered = true }
                } finally {
                    if (!delivered)
                        withContext(NonCancellable + Dispatchers.IO) { result?.file?.delete() }
                }
            }
        } catch (_: Exception) {
            mutable.update { it.copy(busy = false, message = SlideshowMessage.GENERATE_ERROR) }
        }
    }

    private fun observeJobs() {
        viewModelScope.launch {
            restored.await()
            processing.awaitReady()
            processing.records
                .map { it[ProcessingKind.SLIDESHOW] }
                .distinctUntilChanged()
                .collectLatest { record ->
                    record ?: return@collectLatest
                    if (record.request != ProcessingCodec.fingerprint(checkpoint().json))
                        return@collectLatest
                    if (record.status.active)
                        mutable.update {
                            it.copy(
                                busy = true,
                                progress = record.progress,
                                workStatus = record.status,
                            )
                        }
                    else if (
                        record.status == ProcessingStatus.SUCCEEDED &&
                            record.output?.name != state.value.result?.file?.name
                    ) {
                        var result: CompressedVideo? = null
                        var delivered = false
                        val value = state.value.timeline
                        mutable.update { it.copy(busy = true, workStatus = record.status) }
                        try {
                            withContext(Dispatchers.IO) {
                                val out = checkNotNull(record.output)
                                result =
                                    engine.restoreOutput(
                                        value,
                                        out.resolve(engine.folder),
                                        out.bytes,
                                        out.elapsedMs,
                                    )
                            }
                            if (record.request == ProcessingCodec.fingerprint(checkpoint().json)) {
                                val old = state.value.result?.file
                                mutable.update {
                                    it.copy(
                                        result = result,
                                        busy = false,
                                        progress = null,
                                        message = null,
                                    )
                                }
                                delivered = true
                                if (old != result?.file)
                                    withContext(Dispatchers.IO) { old?.delete() }
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            mutable.update {
                                it.copy(busy = false, message = SlideshowMessage.GENERATE_ERROR)
                            }
                        } catch (_: OutOfMemoryError) {
                            mutable.update {
                                it.copy(busy = false, message = SlideshowMessage.MEMORY)
                            }
                        } finally {
                            if (!delivered) result?.info?.poster?.recycle()
                        }
                    } else if (record.status != ProcessingStatus.SUCCEEDED)
                        mutable.update {
                            it.copy(
                                busy = false,
                                progress = null,
                                workStatus = record.status,
                                message =
                                    if (record.status == ProcessingStatus.FAILED)
                                        SlideshowMessage.GENERATE_ERROR
                                    else null,
                            )
                        }
                }
        }
    }

    fun export(uri: Uri, expectedPath: String) {
        val value = state.value
        val output = value.result
        if (value.busy || output == null || output.file.path != expectedPath) {
            mutable.update { it.copy(message = SlideshowMessage.EXPORT_ERROR) }
            viewModelScope.launch(Dispatchers.IO) {
                if (value.timeline.assets.none { it.uri == uri.toString() })
                    runCatching {
                        android.provider.DocumentsContract.deleteDocument(
                            getApplication<Application>().contentResolver,
                            uri,
                        )
                    }
            }
            return
        }
        runOperation(SlideshowMessage.EXPORT_ERROR) {
            withContext(Dispatchers.IO) { engine.export(value.timeline, output, uri) }
            mutable.update { it.copy(message = SlideshowMessage.EXPORTED) }
        }
    }

    fun cancel() {
        processing.cancel(ProcessingKind.SLIDESHOW)
        operation?.cancel()
    }

    fun leave() {
        if (state.value.workStatus?.active != true) operation?.cancel()
        flushSession()
    }

    fun reset() {
        if (state.value.busy) return
        replace(SlideshowTimeline())
    }

    suspend fun clearSavedWork(): Boolean {
        restored.await()
        processing.awaitReady()
        val ready = state.first { !it.busy || it.workStatus?.active == true }
        if (ready.busy || ready.workStatus?.active == true || processing.records.value[ProcessingKind.SLIDESHOW]?.status?.active == true) return false
        reset()
        return writer.flush()
    }

    private fun runOperation(error: SlideshowMessage, block: suspend () -> Unit) {
        val token = ++operationGeneration
        val previous = operation
        previous?.cancel()
        mutable.update { it.copy(busy = true, message = null) }
        operation =
            viewModelScope.launch {
                try {
                    previous?.join()
                    block()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (token == operationGeneration) mutable.update { it.copy(message = error) }
                } catch (_: OutOfMemoryError) {
                    if (token == operationGeneration)
                        mutable.update { it.copy(message = SlideshowMessage.MEMORY) }
                } finally {
                    if (token == operationGeneration && state.value.workStatus?.active != true)
                        mutable.update { it.copy(busy = false, progress = null) }
                }
            }
    }
}

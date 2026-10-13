package com.example.contentstudio.media

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.contentstudio.data.StudioProject
import com.example.contentstudio.processing.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class PresentationPptxState(
    val project: StudioProject? = null,
    val options: PptxOptions = PptxOptions(),
    val selected: Int = 0,
    val result: PresentationPptxResult? = null,
    val preview: Bitmap? = null,
    val busy: Boolean = true,
    val progress: Int? = null,
    val workStatus: ProcessingStatus? = null,
    val message: SlideImageMessage? = null,
)

class PresentationPptxViewModel(application: Application, private val handle: SavedStateHandle) :
    AndroidViewModel(application) {
    private val engine = PresentationPptxEngine(application)
    private val processing = ProcessingJobs.get(application)
    private val writer =
        MediaSessionWriter(viewModelScope, AtomicMediaSession(engine.folder, PptxRequest.MAX_BYTES))
    private val mutable = MutableStateFlow(PresentationPptxState())
    val state = mutable.asStateFlow()
    val sessionStatus = writer.status
    private val ready = CompletableDeferred<Unit>()
    private var operation: Job? = null
    private var previewJob: Job? = null
    private var revision = 0
    private var currentRequest: String? = null
    private var currentFingerprint: String? = null
    private var restored: Pair<StudioProject, PptxOptions>? = null

    init {
        viewModelScope.launch {
            try {
                restored = writer.read()?.let { PptxRequest.decode(it) }
                writer.initialize(
                    SessionSnapshot(restored?.let { PptxRequest.encode(it.first, it.second) })
                )
                mutable.update { it.copy(options = restored?.second ?: PptxOptions()) }
                processing.awaitReady()
                val record = processing.records.value[ProcessingKind.PRESENTATION_PPTX]
                if (record?.status?.active != true)
                    withContext(Dispatchers.IO) { engine.cleanOldFiles(record?.output?.name) }
            } catch (_: Exception) {
                writer.initialize(SessionSnapshot(null))
                mutable.update { it.copy(message = SlideImageMessage.GENERATE_ERROR) }
                processing.awaitReady()
            } finally {
                ready.complete(Unit)
            }
        }
        viewModelScope.launch {
            ready.await()
            processing.records
                .map { it[ProcessingKind.PRESENTATION_PPTX] }
                .distinctUntilChanged()
                .collectLatest { record ->
                    val request = request() ?: return@collectLatest
                    if (record == null || record.request != currentFingerprint) return@collectLatest
                    when {
                        record.status.active ->
                            mutable.update {
                                it.copy(
                                    busy = true,
                                    progress = record.progress,
                                    workStatus = record.status,
                                )
                            }
                        record.status == ProcessingStatus.SUCCEEDED &&
                            record.output?.name != state.value.result?.file?.name -> {
                            val token = revision
                            mutable.update { it.copy(busy = true, workStatus = record.status) }
                            try {
                                val output = requireNotNull(record.output)
                                val result =
                                    withContext(Dispatchers.IO) {
                                        engine.restore(
                                            output.resolve(engine.folder),
                                            output.pages,
                                            output.bytes,
                                            state.value.options,
                                        )
                                    }
                                if (token == revision && request == request()) {
                                    val old = state.value.result
                                    mutable.update {
                                        it.copy(result = result, busy = false, progress = null)
                                    }
                                    if (old?.file != result.file)
                                        withContext(Dispatchers.IO) {
                                            old?.let { engine.discard(it.file) }
                                        }
                                    showPreview()
                                }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                if (token == revision)
                                    mutable.update {
                                        it.copy(
                                            busy = false,
                                            message = SlideImageMessage.GENERATE_ERROR,
                                        )
                                    }
                            } catch (_: OutOfMemoryError) {
                                if (token == revision)
                                    mutable.update {
                                        it.copy(
                                            busy = false,
                                            message = SlideImageMessage.GENERATE_ERROR,
                                        )
                                    }
                            }
                        }
                        !record.status.active && record.status != ProcessingStatus.SUCCEEDED ->
                            mutable.update {
                                it.copy(
                                    busy = false,
                                    progress = null,
                                    workStatus = record.status,
                                    message =
                                        if (record.status == ProcessingStatus.FAILED)
                                            SlideImageMessage.GENERATE_ERROR
                                        else null,
                                )
                            }
                    }
                }
        }
    }

    /**
     * Project is authoritative; restored output is exposed only for matching content and options.
     */
    suspend fun prepare(project: StudioProject) {
        ready.await()
        val before = state.value.project
        if (
            before != null &&
                PptxRequest.snapshot(before, state.value.options) ==
                    PptxRequest.snapshot(project, state.value.options)
        ) {
            mutable.update { it.copy(project = project) }
            if (state.value.result != null && state.value.preview == null) showPreview()
            return
        }
        val next = PptxRequest.encode(project, state.value.options)
        ++revision
        val old = state.value.result
        mutable.update { it.copy(result = null, preview = null, busy = true) }
        setRequest(next)
        operation?.cancelAndJoin()
        previewJob?.cancelAndJoin()
        val record = processing.records.value[ProcessingKind.PRESENTATION_PPTX]
        val matches = record?.request == currentFingerprint
        if (!matches) processing.forget(ProcessingKind.PRESENTATION_PPTX)
        mutable.update {
            it.copy(
                project = project,
                selected =
                    (handle.get<Int>("pptx-selected") ?: 0).coerceIn(
                        0,
                        (project.slides.size - 1).coerceAtLeast(0),
                    ),
                result = null,
                preview = null,
                busy = matches && record?.status?.active == true,
                workStatus = if (matches) record?.status else null,
            )
        }
        writer.update(SessionSnapshot(next))
        if (!matches)
            withContext(Dispatchers.IO) {
                old?.let { engine.discard(it.file) }
                record?.output?.let { engine.discard(java.io.File(engine.folder, it.name)) }
            }
        if (matches && record?.status == ProcessingStatus.SUCCEEDED) {
            mutable.update { it.copy(busy = true) }
            try {
                val output = requireNotNull(record.output)
                val result =
                    withContext(Dispatchers.IO) {
                        engine.restore(
                            output.resolve(engine.folder),
                            output.pages,
                            output.bytes,
                            state.value.options,
                        )
                    }
                if (next == request()) {
                    mutable.update { it.copy(result = result, busy = false) }
                    showPreview()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutable.update { it.copy(busy = false, message = SlideImageMessage.GENERATE_ERROR) }
            }
        }
    }

    fun options(options: PptxOptions) {
        if (state.value.busy || state.value.options == options) return
        processing.dropFinished(ProcessingKind.PRESENTATION_PPTX)
        ++revision
        previewJob?.cancel()
        val old = state.value.result
        mutable.update {
            it.copy(
                options = options,
                result = null,
                preview = null,
                message = null,
                workStatus = null,
            )
        }
        setRequest(state.value.project?.let { PptxRequest.encode(it, options) })
        writer.update(SessionSnapshot(request()))
        viewModelScope.launch(Dispatchers.IO) { old?.let { engine.discard(it.file) } }
    }

    fun select(index: Int) {
        if (index !in state.value.project?.slides.orEmpty().indices) return
        mutable.update { it.copy(selected = index) }
        handle["pptx-selected"] = index
        showPreview()
    }

    private fun showPreview() {
        previewJob?.cancel()
        val result = state.value.result ?: return
        val index = state.value.selected
        val project = state.value.project
        mutable.update { it.copy(preview = null) }
        previewJob =
            viewModelScope.launch {
                var bitmap: Bitmap? = null
                var delivered = false
                try {
                    bitmap = withContext(Dispatchers.IO) { engine.preview(result, index, project) }
                    if (state.value.result == result && state.value.selected == index) {
                        mutable.update { it.copy(preview = bitmap) }
                        delivered = true
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    mutable.update { it.copy(message = SlideImageMessage.GENERATE_ERROR) }
                } catch (_: OutOfMemoryError) {
                    mutable.update { it.copy(message = SlideImageMessage.GENERATE_ERROR) }
                } finally {
                    if (!delivered) bitmap?.recycle()
                }
            }
    }

    fun generate() {
        val value = state.value
        val project = value.project ?: return
        if (value.busy || value.result != null || project.slides.isEmpty()) return
        val json = requireNotNull(request())
        mutable.update { it.copy(busy = true, message = null, result = null, preview = null) }
        try {
            processing.start(ProcessingKind.PRESENTATION_PPTX, json) { progress ->
                check(writer.flush()) { "Could not save PPTX settings" }
                var result: PresentationPptxResult? = null
                var delivered = false
                try {
                    val output =
                        withContext(Dispatchers.IO) {
                            engine.create(project, value.options, progress).also { result = it }
                        }
                    ProcessingOutput(output.file.name, output.bytes, pages = output.pages).also {
                        delivered = true
                    }
                } finally {
                    if (!delivered)
                        withContext(NonCancellable + Dispatchers.IO) {
                            result?.let { engine.discard(it.file) }
                        }
                }
            }
        } catch (_: Exception) {
            mutable.update { it.copy(busy = false, message = SlideImageMessage.GENERATE_ERROR) }
        }
    }

    fun export(uri: Uri, expectedPath: String) {
        val value = state.value
        val result = value.result
        if (value.busy || result == null || result.file.path != expectedPath) {
            mutable.update { it.copy(message = SlideImageMessage.EXPORT_ERROR) }
            return
        }
        mutable.update { it.copy(busy = true, message = null) }
        operation =
            viewModelScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        engine.export(requireNotNull(value.project), result, uri)
                    }
                    mutable.update { it.copy(message = SlideImageMessage.EXPORTED) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    mutable.update { it.copy(message = SlideImageMessage.EXPORT_ERROR) }
                } finally {
                    mutable.update { it.copy(busy = false) }
                }
            }
    }

    suspend fun verify(result: PresentationPptxResult) {
        check(result == state.value.result)
        withContext(Dispatchers.IO) { engine.verify(result) }
        check(result == state.value.result)
    }

    fun cancel() {
        processing.cancel(ProcessingKind.PRESENTATION_PPTX)
        operation?.cancel()
    }

    fun leave() {
        operation?.cancel()
        previewJob?.cancel()
        viewModelScope.launch { writer.flush() }
    }

    fun retrySession() {
        writer.retry()
    }

    suspend fun awaitSession() = writer.flush()

    private fun request() = currentRequest

    private fun setRequest(value: String?) {
        currentRequest = value
        currentFingerprint = ProcessingCodec.fingerprint(value)
    }
}

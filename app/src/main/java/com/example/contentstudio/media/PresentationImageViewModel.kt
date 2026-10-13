package com.example.contentstudio.media

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.contentstudio.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class SlideImageMessage {
    GENERATE_ERROR,
    OVERFLOW,
    EXPORT_ERROR,
    EXPORTED,
}

data class PresentationImageState(
    val project: StudioProject? = null,
    val slideId: String? = null,
    val options: SlideImageOptions = SlideImageOptions(),
    val result: CompressedImage? = null,
    val busy: Boolean = false,
    val message: SlideImageMessage? = null,
)

class PresentationImageViewModel(application: Application, private val handle: SavedStateHandle) :
    AndroidViewModel(application) {
    private val engine = PresentationImageEngine(application)
    private val mutable =
        MutableStateFlow(
            PresentationImageState(
                options =
                    SlideImageOptions(
                        format =
                            runCatching {
                                    ImageOutputFormat.valueOf(
                                        handle.get<String>("slide-image-format") ?: "PNG"
                                    )
                                }
                                .getOrDefault(ImageOutputFormat.PNG),
                        edge =
                            handle.get<Int>("slide-image-edge")?.takeIf {
                                it in listOf(1024, 2048, 4096)
                            } ?: 2048,
                        quality = (handle.get<Int>("slide-image-quality") ?: 80).coerceIn(40, 95),
                    )
            )
        )
    val state = mutable.asStateFlow()
    private var operation: Job? = null
    private var generation = 0

    init {
        viewModelScope.launch(Dispatchers.IO) { engine.cleanOldCache() }
    }

    fun prepare(project: StudioProject, selectedId: String? = null) {
        val visible = project.visualSnapshot()
        val id =
            selectedId?.takeIf { value -> visible.slides.any { it.id == value } }
                ?: state.value.slideId?.takeIf { value ->
                    state.value.project?.id == project.id && visible.slides.any { it.id == value }
                }
                ?: handle.get<String>("slide-image-selected")?.takeIf { value ->
                    handle.get<String>("slide-image-project") == project.id &&
                        visible.slides.any { it.id == value }
                }
                ?: visible.slides.firstOrNull()?.id
        // A repository save timestamp is not a new visual source or new export settings.
        if (
            state.value.project?.copy(updatedAt = visible.updatedAt) == visible &&
                state.value.slideId == id
        )
            return
        invalidate(state.value.copy(project = visible, slideId = id))
    }

    fun select(id: String) {
        if (
            !state.value.busy &&
                state.value.project?.slides?.any { it.id == id } == true &&
                id != state.value.slideId
        ) {
            invalidate(state.value.copy(slideId = id))
        }
    }

    fun options(options: SlideImageOptions) {
        if (!state.value.busy && options != state.value.options) {
            invalidate(state.value.copy(options = options))
        }
    }

    private fun invalidate(next: PresentationImageState) {
        val previous = operation
        previous?.cancel()
        ++generation
        val old = state.value.result
        mutable.value = next.copy(result = null, busy = false, message = null)
        handle["slide-image-project"] = next.project?.id
        handle["slide-image-selected"] = next.slideId
        handle["slide-image-format"] = next.options.format.name
        handle["slide-image-edge"] = next.options.edge
        handle["slide-image-quality"] = next.options.quality
        viewModelScope.launch(Dispatchers.IO) {
            previous?.join()
            old?.file?.delete()
        }
    }

    fun generate() {
        val value = state.value
        val project = value.project ?: return
        val id = value.slideId ?: return
        if (value.busy) return
        run(SlideImageMessage.GENERATE_ERROR) {
            var result: CompressedImage? = null
            var delivered = false
            try {
                withContext(Dispatchers.IO) { result = engine.create(project, id, value.options) }
                val old = state.value.result
                mutable.update { it.copy(result = result) }
                delivered = true
                withContext(Dispatchers.IO) { old?.file?.delete() }
            } finally {
                if (!delivered)
                    withContext(NonCancellable + Dispatchers.IO) {
                        result?.file?.delete()
                        result?.preview?.recycle()
                    }
            }
        }
    }

    fun export(uri: Uri, expectedPath: String) {
        val value = state.value
        val output = value.result
        if (value.busy || output == null || output.file.path != expectedPath) {
            mutable.update { it.copy(message = SlideImageMessage.EXPORT_ERROR) }
            return
        }
        run(SlideImageMessage.EXPORT_ERROR) {
            withContext(Dispatchers.IO) { engine.export(checkNotNull(value.project), output, uri) }
            mutable.update { it.copy(message = SlideImageMessage.EXPORTED) }
        }
    }

    fun cancel() {
        operation?.cancel()
    }

    fun leave() = cancel()

    private fun run(error: SlideImageMessage, work: suspend () -> Unit) {
        val previous = operation
        previous?.cancel()
        val token = ++generation
        mutable.update { it.copy(busy = true, message = null) }
        operation =
            viewModelScope.launch {
                try {
                    previous?.join()
                    work()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: PdfTextOverflow) {
                    if (token == generation)
                        mutable.update { it.copy(message = SlideImageMessage.OVERFLOW) }
                } catch (_: Exception) {
                    if (token == generation) mutable.update { it.copy(message = error) }
                } catch (_: OutOfMemoryError) {
                    if (token == generation) mutable.update { it.copy(message = error) }
                } finally {
                    if (token == generation) mutable.update { it.copy(busy = false) }
                }
            }
    }
}

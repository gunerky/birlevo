package com.example.contentstudio.media

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.contentstudio.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class PresentationPlayerState(
    val project: StudioProject? = null,
    val slideId: String? = null,
    val elapsedMs: Long = 0,
    val running: Boolean = false,
    val busy: Boolean = false,
    val preview: Bitmap? = null,
    val error: Boolean = false,
) {
    val index
        get() = project?.slides?.indexOfFirst { it.id == slideId }?.coerceAtLeast(0) ?: 0
}

class PresentationPlayerViewModel(application: Application, private val handle: SavedStateHandle) :
    AndroidViewModel(application) {
    private val painter = PresentationPdf(application)
    private val mutable = MutableStateFlow(PresentationPlayerState())
    val state = mutable.asStateFlow()
    private var clock =
        PresentationClock((handle.get<Long>("present-elapsed") ?: 0L).coerceAtLeast(0))
    private var render: Job? = null
    private var ticker: Job? = null

    init {
        handle.setSavedStateProvider("present-timer") {
            android.os.Bundle().apply { putLong("elapsed", clock.elapsed(now())) }
        }
        handle.get<android.os.Bundle>("present-timer")?.let {
            clock = PresentationClock(it.getLong("elapsed").coerceAtLeast(0))
        }
    }

    private fun now() = android.os.SystemClock.elapsedRealtime()

    fun prepare(project: StudioProject) {
        require(project.kind == ProjectKind.PRESENTATION && project.slides.size in 1..100)
        // Autosave timestamps do not change the slide or interrupt a running stopwatch.
        if (state.value.project?.copy(updatedAt = project.updatedAt) == project) {
            if (state.value.preview == null && !state.value.busy) load()
            return
        }
        pause()
        val same = handle.get<String>("present-project") == project.id
        if (!same) {
            clock = PresentationClock()
            handle["present-elapsed"] = 0L
        }
        val selected =
            handle.get<String>("present-slide").takeIf {
                same && project.slides.any { s -> s.id == it }
            } ?: project.slides.first().id
        mutable.value = PresentationPlayerState(project, selected, clock.elapsed(now()))
        handle["present-project"] = project.id
        handle["present-slide"] = selected
        load()
    }

    fun select(index: Int) {
        val project = state.value.project ?: return
        if (index !in project.slides.indices) return
        val id = project.slides[index].id
        if (id == state.value.slideId) return
        mutable.update { it.copy(slideId = id, preview = null, error = false) }
        handle["present-slide"] = id
        load()
    }

    fun retry() = load()

    private fun load() {
        render?.cancel()
        val snapshot = state.value
        val project = snapshot.project ?: return
        mutable.update { it.copy(busy = true, preview = null, error = false) }
        render =
            viewModelScope.launch {
                var bitmap: Bitmap? = null
                var delivered = false
                try {
                    bitmap =
                        PreviewLoader.load {
                            painter.renderSlideBitmap(
                                project.visualSnapshot(),
                                snapshot.index,
                                1280,
                                720,
                            )
                        }
                    if (state.value.slideId == snapshot.slideId && state.value.project == project) {
                        mutable.update { it.copy(preview = bitmap, busy = false) }
                        delivered = true
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (state.value.slideId == snapshot.slideId && state.value.project == project)
                        mutable.update { it.copy(busy = false, error = true) }
                } catch (_: OutOfMemoryError) {
                    if (state.value.slideId == snapshot.slideId && state.value.project == project)
                        mutable.update { it.copy(busy = false, error = true) }
                } finally {
                    if (!delivered) bitmap?.recycle()
                }
            }
    }

    fun toggleTimer() {
        if (state.value.running) pause()
        else {
            clock = clock.start(now())
            mutable.update { it.copy(running = true) }
            ticker?.cancel()
            ticker =
                viewModelScope.launch {
                    while (isActive) {
                        mutable.update { it.copy(elapsedMs = clock.elapsed(now())) }
                        delay(500)
                    }
                }
        }
    }

    fun pause() {
        ticker?.cancel()
        clock = clock.pause(now())
        handle["present-elapsed"] = clock.accumulatedMs
        mutable.update { it.copy(elapsedMs = clock.accumulatedMs, running = false) }
    }

    fun resetTimer() {
        pause()
        clock = clock.reset()
        handle["present-elapsed"] = 0L
        mutable.update { it.copy(elapsedMs = 0) }
    }

    fun leave() {
        pause()
        render?.cancel()
        mutable.update { it.copy(busy = false) }
    }
}

package com.example.contentstudio.advertising

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal enum class BannerPhase { IDLE, LOADING, VISIBLE, UNAVAILABLE }

internal interface BannerHandle {
    fun load(loaded: () -> Unit, failed: () -> Unit)
    fun reveal()
    fun destroy()
}

/** Main-thread owner. Provider callbacks are dispatched back to the owning UI scope. */
internal class HomeBannerSession(
    private val scope: CoroutineScope,
    private val eligible: () -> Boolean,
    private val reserveAttempt: () -> Boolean,
    private val timeoutMs: Long = 15_000,
    private val callbackDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) {
    private var generation = 0L
    private var handle: BannerHandle? = null
    private var deadline: Job? = null
    private val mutable = MutableStateFlow(BannerPhase.IDLE)
    val phase = mutable.asStateFlow()

    fun start(next: BannerHandle) {
        stop()
        val token = generation
        if (!eligible() || !reserveAttempt()) {
            next.destroy(); mutable.value = BannerPhase.UNAVAILABLE; return
        }
        handle = next
        mutable.value = BannerPhase.LOADING
        deadline = scope.launch(callbackDispatcher) {
            delay(timeoutMs)
            finish(token)
        }
        try {
            next.load(loaded = {
                scope.launch(callbackDispatcher) {
                    if (token == generation && eligible() && mutable.value == BannerPhase.LOADING) {
                        deadline?.cancel(); deadline = null
                        next.reveal()
                        mutable.value = BannerPhase.VISIBLE
                    } else if (token == generation) finish(token)
                    else next.destroy()
                }
            }, failed = { scope.launch(callbackDispatcher) { finish(token) } })
        } catch (_: Exception) { finish(token) }
    }

    private fun finish(token: Long) {
        if (token != generation) return
        stop()
        mutable.value = BannerPhase.UNAVAILABLE
    }

    fun stop() {
        generation++
        deadline?.cancel(); deadline = null
        val previous = handle
        handle = null
        previous?.destroy()
        mutable.value = BannerPhase.IDLE
    }
}

/** Limits app-initiated attempts, including failed loads, across Activity/process recreation. */
internal class BannerAttemptLimiter(
    private val now: () -> Long,
    private val read: () -> Long?,
    private val write: (Long) -> Unit,
    private val intervalMs: Long = 60_000,
) {
    fun reserve(): Boolean {
        val time = now()
        val last = read()
        if (last != null) {
            // Rebase a clock rollback once; it must not produce an unlimited retry loop.
            if (time < last) { write(time); return false }
            if (time - last < intervalMs) return false
        }
        write(time)
        return true
    }
}

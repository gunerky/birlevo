package com.example.contentstudio.data

/** Monotonic stopwatch. Background/process-restored presentations stay paused. */
data class PresentationClock(val accumulatedMs: Long = 0, val startedAtMs: Long? = null) {
    init {
        require(accumulatedMs >= 0 && (startedAtMs == null || startedAtMs >= 0))
    }

    fun elapsed(now: Long): Long =
        accumulatedMs + (startedAtMs?.let { (now - it).coerceAtLeast(0) } ?: 0)

    fun start(now: Long) = if (startedAtMs == null) copy(startedAtMs = now) else this

    fun pause(now: Long) = PresentationClock(elapsed(now))

    fun reset() = PresentationClock()
}

/** Notes are editor/presenter data; output snapshots intentionally contain only slide content. */
fun StudioProject.visualSnapshot() = copy(slides = slides.map { it.copy(notes = "") })

fun presentationClockLabel(ms: Long): String {
    val total = ms.coerceAtLeast(0) / 1000
    return "%02d:%02d:%02d".format(java.util.Locale.ROOT, total / 3600, total / 60 % 60, total % 60)
}

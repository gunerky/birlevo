package com.example.contentstudio.media

/** Times refer to the final, speed-adjusted composition, never to an individual source. */
data class VideoTextLayer(val text: String = "", val position: VideoTextPosition = VideoTextPosition.BOTTOM_CENTER,
    val size: Float = 0.06f, val color: VideoTextColor = VideoTextColor.WHITE,
    val startMs: Long = 0, val endMs: Long? = null) {
    init {
        require(text.length <= MAX_LENGTH && text.count { it == '\n' } < 6)
        require(size.isFinite() && size in 0.025f..0.10f)
        require(startMs in 0..FormatSupport.MAX_VIDEO_DURATION_MS * 2)
        require(endMs == null || endMs in (startMs + 1)..FormatSupport.MAX_VIDEO_DURATION_MS * 2)
    }
    val enabled get() = text.isNotBlank()
    fun end(durationMs: Long) = minOf(endMs ?: durationMs, durationMs)
    fun validTime(durationMs: Long) = !enabled || startMs < end(durationMs)
    fun visible(timeUs: Long, durationMs: Long) = enabled && timeUs >= startMs * 1000 && timeUs < end(durationMs) * 1000
    companion object { const val MAX_LENGTH = 200 }
}

enum class VideoTextPosition(val column: Int, val row: Int) {
    TOP_LEFT(0, 0), TOP_CENTER(1, 0), TOP_RIGHT(2, 0),
    CENTER_LEFT(0, 1), CENTER(1, 1), CENTER_RIGHT(2, 1),
    BOTTOM_LEFT(0, 2), BOTTOM_CENTER(1, 2), BOTTOM_RIGHT(2, 2)
}
enum class VideoTextColor(val argb: Int) {
    WHITE(-1), CORAL(0xFFFC5B54.toInt()), ICE(0xFFB9E7FF.toInt()), NAVY(0xFF08164D.toInt()), BLACK(0xFF000000.toInt())
}

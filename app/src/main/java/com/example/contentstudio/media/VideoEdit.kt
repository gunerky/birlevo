package com.example.contentstudio.media

/** Ordered, non-overlapping clips; original source files are never modified. */
data class VideoClip(val source: VideoSource, val trim: VideoTrim = VideoTrim(0, source.info.durationMs)) {
    init { require(trim.endMs <= source.info.durationMs) }
}

enum class VideoCanvas { FIRST_CLIP, PORTRAIT_9_16, LANDSCAPE_16_9, SQUARE }
enum class VideoFit { FIT, CENTER_CROP }
val videoSpeeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

data class VideoEdit(val clips: List<VideoClip>, val canvas: VideoCanvas = VideoCanvas.FIRST_CLIP,
    val fit: VideoFit = VideoFit.FIT, val speed: Float = 1f, val textLayer: VideoTextLayer? = null,
    val imageLayer: VideoImageLayer? = null) {
    init {
        require(clips.size in 1..2 && clips.map { it.source.file }.distinct().size == clips.size)
        require(speed in videoSpeeds)
        require(sourceDurationMs in 1..FormatSupport.MAX_VIDEO_DURATION_MS)
    }
    val sourceDurationMs get() = clips.sumOf { it.trim.durationMs }
    val durationMs get() = kotlin.math.round(sourceDurationMs / speed.toDouble()).toLong().coerceAtLeast(1L)
    val sourceBytes get() = clips.sumOf { it.source.bytes }
    val joined get() = clips.size > 1
    val hasAudio get() = clips.any { it.source.info.hasAudio }
    val channels get() = clips.filter { it.source.info.hasAudio }.maxOfOrNull { it.source.info.audioChannels } ?: 2
    /** Audio layout and effective frame rate; canvas geometry is selected separately. */
    fun profile() = clips.first().source.info.copy(durationMs = durationMs, hasAudio = hasAudio,
        audioMime = clips.firstOrNull { it.source.info.hasAudio }?.source?.info?.audioMime,
        audioSampleRate = if (joined) 48000 else clips.first().source.info.audioSampleRate,
        audioChannels = channels,
        frameRate = clips.mapNotNull { it.source.info.frameRate }.maxOrNull()?.times(speed))

    fun target(edge: Int, quality: VideoQuality): VideoTarget {
        val first = clips.first().source.info
        require(edge in listOf(640, 1280, 1920))
        if (canvas == VideoCanvas.FIRST_CLIP) return videoTarget(first.width, first.height, edge, quality)
        // Bound canvas size by the first source's long edge. Explicit fill may enlarge and crop content.
        val longEdge = minOf(edge, maxOf(first.width, first.height))
        val (width, height) = when (canvas) {
            VideoCanvas.PORTRAIT_9_16 -> (longEdge / 32 * 18).coerceAtLeast(18) to (longEdge / 32 * 32).coerceAtLeast(32)
            VideoCanvas.LANDSCAPE_16_9 -> (longEdge / 32 * 32).coerceAtLeast(32) to (longEdge / 32 * 18).coerceAtLeast(18)
            VideoCanvas.SQUARE -> longEdge / 2 * 2 to longEdge / 2 * 2
            VideoCanvas.FIRST_CLIP -> error("Handled above")
        }
        return videoTarget(width, height, edge, quality)
    }
}

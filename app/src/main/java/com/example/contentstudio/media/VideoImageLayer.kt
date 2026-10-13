package com.example.contentstudio.media

/** An immutable owned image plus placement on the final, speed-adjusted video. */
data class VideoImageLayer(val asset: SessionAsset, val width: Int, val height: Int,
    val position: VideoTextPosition = VideoTextPosition.TOP_RIGHT, val size: Float = .25f,
    val opacity: Float = 1f, val startMs: Long = 0, val endMs: Long? = null) {
    init {
        require(width > 0 && height > 0 && width.toLong() * height <= FormatSupport.MAX_IMAGE_PIXELS)
        require(asset.file == java.io.File(asset.file).name && asset.file.startsWith("overlay-") &&
            !asset.file.contains('/') && !asset.file.contains('\\') && asset.bytes in 1..FormatSupport.MAX_IMAGE_BYTES)
        require(asset.id.isNotBlank() && asset.id.length <= 128 && asset.name.length <= 256 && asset.uri.length <= 8192)
        require(size.isFinite() && size in .05f.. .8f && opacity.isFinite() && opacity in .05f..1f)
        require(startMs in 0..FormatSupport.MAX_VIDEO_DURATION_MS * 2)
        require(endMs == null || endMs in (startMs + 1)..FormatSupport.MAX_VIDEO_DURATION_MS * 2)
    }
    fun end(durationMs: Long) = minOf(endMs ?: durationMs, durationMs)
    fun validTime(durationMs: Long) = startMs < end(durationMs)
    fun visible(timeUs: Long, durationMs: Long) = timeUs >= startMs * 1000 && timeUs < end(durationMs) * 1000
    /** Width setting and 90% frame-height bound, preserving the complete image aspect ratio. */
    fun dimensions(frameWidth: Int, frameHeight: Int): Pair<Float, Float> {
        val ratio = minOf(frameWidth * size / width, frameHeight * .9f / height)
        return width * ratio to height * ratio
    }
}

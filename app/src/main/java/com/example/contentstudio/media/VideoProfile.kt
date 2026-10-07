package com.example.contentstudio.media

import kotlin.math.max

enum class VideoQuality(val bitrate720: Int) { SMALL(1_000_000), BALANCED(2_000_000), HIGH(4_000_000) }
enum class VideoOutputFormat(val mime: String, val extension: String, val videoMime: String, val audioMime: String) {
    MP4_AVC("video/mp4", "mp4", "video/avc", "audio/mp4a-latm"),
    MP4_HEVC("video/mp4", "mp4", "video/hevc", "audio/mp4a-latm"),
    WEBM_VP8("video/webm", "webm", "video/x-vnd.on2.vp8", "audio/opus")
}
enum class VideoOutputIssue { PLATFORM, VIDEO_ENCODER, AUDIO_ENCODER, PREVIEW_DECODER, ORIENTATION, TEXT_TIMING, TEXT_LAYOUT }

fun videoOutputConstraint(format: VideoOutputFormat, hasAudio: Boolean, api: Int, rotation: Int): VideoOutputIssue? = when {
    format == VideoOutputFormat.WEBM_VP8 && hasAudio && api < 29 -> VideoOutputIssue.PLATFORM
    // Media3 preserves a 180-degree MP4 hint; platform WebM cannot represent it.
    format == VideoOutputFormat.WEBM_VP8 && rotation % 360 == 180 -> VideoOutputIssue.ORIENTATION
    else -> null
}
data class VideoTarget(val width: Int, val height: Int, val bitrate: Int)

fun videoTarget(width: Int, height: Int, maxEdge: Int, quality: VideoQuality): VideoTarget {
    require(width >= 16 && height >= 16 && maxEdge in listOf(640, 1280, 1920))
    val scale = minOf(1.0, maxEdge.toDouble() / max(width, height))
    val targetWidth = max(2, (width * scale).toInt() / 2 * 2)
    val targetHeight = max(2, (height * scale).toInt() / 2 * 2)
    val bitrate = (quality.bitrate720.toLong() * targetWidth * targetHeight / (1280 * 720))
        .coerceIn(200_000, 8_000_000).toInt()
    return VideoTarget(targetWidth, targetHeight, bitrate)
}

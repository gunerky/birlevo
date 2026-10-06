package com.example.contentstudio.media

import kotlin.math.max

enum class VideoQuality(val bitrate720: Int) { SMALL(1_000_000), BALANCED(2_000_000), HIGH(4_000_000) }
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

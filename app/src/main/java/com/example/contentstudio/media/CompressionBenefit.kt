package com.example.contentstudio.media

/** Use actual file bytes, not codec estimates, to decide whether compression saved space. */
enum class CompressionBenefit { SMALLER, SAME_SIZE, LARGER }

fun compressionBenefit(sourceBytes: Long, outputBytes: Long): CompressionBenefit {
    require(sourceBytes > 0 && outputBytes > 0)
    return when {
        outputBytes < sourceBytes -> CompressionBenefit.SMALLER
        outputBytes == sourceBytes -> CompressionBenefit.SAME_SIZE
        else -> CompressionBenefit.LARGER
    }
}

/** An advisory only: hardware encoders can depart from the requested bitrate. */
fun videoSizeIncreaseRisk(sourceBytes: Long, durationMs: Long, requestedVideoBitrate: Int): Boolean {
    require(sourceBytes > 0 && durationMs > 0 && requestedVideoBitrate > 0)
    val sourceTotalBitrate = sourceBytes.toDouble() * 8_000.0 / durationMs
    // Total source bitrate includes audio and container bytes: this is a conservative warning.
    return requestedVideoBitrate >= sourceTotalBitrate
}

package com.example.contentstudio.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder

enum class AudioTarget(val extension: String, val mime: String, val codec: String, val label: String, val lossy: Boolean = false) {
    WAV("wav", "audio/wav", "audio/raw", "WAV"),
    M4A("m4a", "audio/mp4", "audio/mp4a-latm", "M4A", true),
    OGG("ogg", "audio/ogg", "audio/opus", "OGG · Opus", true),
    FLAC("flac", "audio/flac", "audio/flac", "FLAC")
}

enum class AudioOutputIssue { ANDROID_VERSION, ENCODER, DECODER, CHECK_FAILED }

fun audioOutputConstraint(target: AudioTarget, sdk: Int): AudioOutputIssue? =
    if (target == AudioTarget.OGG && sdk < 29) AudioOutputIssue.ANDROID_VERSION else null

fun AudioTarget.sampleRate(sourceRate: Int): Int = if (this == AudioTarget.OGG) 48_000 else sourceRate

/** Container names are not codec guarantees. Inspect the actual audio track before starting. */
object AudioPolicy {
    const val MAX_BYTES = 512L * 1024 * 1024
    const val MAX_DURATION_MS = 600_000L
    val inputMimes = listOf("audio/mpeg", "audio/mp4", "audio/aac", "audio/wav", "audio/ogg", "audio/opus", "audio/flac")
    val codecs = setOf("audio/mpeg", "audio/mp4a-latm", "audio/raw", "audio/opus", "audio/vorbis", "audio/flac")
    val bitrates = listOf(64_000, 128_000, 256_000)
    fun validate(mime: String, rate: Int, channels: Int, durationMs: Long, output: Boolean = false) {
        // Encoded audio may include codec padding at the ten-minute input boundary.
        val maximum = MAX_DURATION_MS + if (output) MAX_DURATION_MS / 100 else 0
        require(mime in codecs && rate in 8_000..192_000 && channels in 1..2 && durationMs in 1..maximum)
    }
    fun wavHeader(rate: Int, channels: Int, dataBytes: Long): ByteArray {
        require(rate in 8_000..192_000 && channels in 1..2 && dataBytes in 0..MAX_BYTES - 44 && dataBytes % (channels * 2) == 0L)
        return ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII)); putInt((dataBytes + 36).toInt())
            put("WAVEfmt ".toByteArray(Charsets.US_ASCII)); putInt(16); putShort(1); putShort(channels.toShort())
            putInt(rate); putInt(rate * channels * 2); putShort((channels * 2).toShort()); putShort(16)
            put("data".toByteArray(Charsets.US_ASCII)); putInt(dataBytes.toInt())
        }.array()
    }
}

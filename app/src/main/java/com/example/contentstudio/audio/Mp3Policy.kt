package com.example.contentstudio.audio

/** MPEG rates and CBR bounds. Unsupported requests remain unavailable, never resampled. */
internal object Mp3Policy {
    private val rates = setOf(8000, 11025, 12000, 16000, 22050, 24000, 32000, 44100, 48000)
    fun supports(rate: Int, channels: Int, bitrate: Int): Boolean =
        rate in rates && channels in 1..2 && bitrate in AudioPolicy.bitrates &&
            bitrate <= when { rate >= 32000 -> 320000; rate >= 16000 -> 160000; else -> 64000 }
}

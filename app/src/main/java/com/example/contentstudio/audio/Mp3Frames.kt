package com.example.contentstudio.audio

import java.io.File
import java.io.RandomAccessFile

/** Validates our metadata-free CBR Layer III stream independently of its extension/extractor. */
internal object Mp3Frames {
    data class Info(val rate: Int, val channels: Int, val bitrate: Int, val frames: Int)
    private val mpeg1 = intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0)
    private val mpeg2 = intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, 0)
    fun inspect(file: File): Info = RandomAccessFile(file, "r").use { input ->
        require(input.length() in 1..AudioPolicy.MAX_BYTES)
        var first: Info? = null; var count = 0
        while (input.filePointer < input.length()) {
            val start = input.filePointer
            require(input.length() - start >= 4)
            val header = input.readInt()
            val version = (header ushr 19) and 3
            require(header ushr 21 == 0x7ff && version != 1 && (header ushr 17) and 3 == 1)
            val rateIndex = (header ushr 10) and 3
            require(rateIndex < 3)
            val rate = intArrayOf(44100, 48000, 32000)[rateIndex] / when (version) { 3 -> 1; 2 -> 2; else -> 4 }
            val bitrate = (if (version == 3) mpeg1 else mpeg2)[(header ushr 12) and 15] * 1000
            val channels = if ((header ushr 6) and 3 == 3) 1 else 2
            require(bitrate > 0)
            val length = (if (version == 3) 144 else 72) * bitrate / rate + ((header ushr 9) and 1)
            require(length >= 4 && start + length <= input.length())
            val info = Info(rate, channels, bitrate, 0)
            if (first == null) first = info else require(first == info)
            count++; require(count <= 100_000)
            input.seek(start + length)
        }
        require(count >= 2)
        checkNotNull(first).copy(frames = count)
    }
}

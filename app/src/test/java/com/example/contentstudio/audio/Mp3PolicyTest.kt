package com.example.contentstudio.audio

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class Mp3PolicyTest {
    @Test fun unsupportedRateAndBitrateAreNotSilentlyReplaced() {
        for (rate in listOf(32000, 44100, 48000)) for (channels in 1..2) for (bitrate in AudioPolicy.bitrates)
            assertTrue(Mp3Policy.supports(rate, channels, bitrate))
        assertTrue(Mp3Policy.supports(16000, 1, 128000))
        assertFalse(Mp3Policy.supports(16000, 1, 256000))
        assertTrue(Mp3Policy.supports(8000, 1, 64000))
        assertFalse(Mp3Policy.supports(8000, 1, 128000))
        assertFalse(Mp3Policy.supports(96000, 2, 128000))
        assertFalse(Mp3Policy.supports(48000, 3, 128000))
        assertFalse(Mp3Policy.supports(48000, 2, 127999))
        assertEquals(44100, AudioTarget.MP3.sampleRate(44100))
    }
    @Test fun renamedContainersTruncatedFramesAndMixedBitratesAreRejected() {
        val file = File.createTempFile("mp3-test", ".mp3")
        try {
            fun frame(header: Int, size: Int) = java.nio.ByteBuffer.allocate(size).putInt(header).array()
            val low = frame(0xfffb9400.toInt(), 384) // 128 kbps, 48 kHz stereo.
            file.writeBytes(low + low)
            val info = Mp3Frames.inspect(file)
            assertEquals(48000, info.rate); assertEquals(128000, info.bitrate); assertEquals(2, info.channels)
            assertEquals(2, info.frames)
            file.writeBytes(low + frame(0xfffbd400.toInt(), 768))
            assertThrows(IllegalArgumentException::class.java) { Mp3Frames.inspect(file) }
            file.writeBytes((low + low).copyOf(767))
            assertThrows(IllegalArgumentException::class.java) { Mp3Frames.inspect(file) }
            file.writeText("RIFFrenamedWAVE")
            assertThrows(IllegalArgumentException::class.java) { Mp3Frames.inspect(file) }
        } finally { file.delete() }
    }
}

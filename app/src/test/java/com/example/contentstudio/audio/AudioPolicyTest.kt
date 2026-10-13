package com.example.contentstudio.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class AudioPolicyTest {
    @Test fun wavHeaderDescribesExactPcmBytesAndStereoAlignment() {
        val header = AudioPolicy.wavHeader(48000, 2, 576000)
        val bytes = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("RIFF", String(header, 0, 4, Charsets.US_ASCII))
        assertEquals(576036, bytes.getInt(4)); assertEquals(2, bytes.getShort(22).toInt())
        assertEquals(48000, bytes.getInt(24)); assertEquals(192000, bytes.getInt(28))
        assertEquals(4, bytes.getShort(32).toInt()); assertEquals(16, bytes.getShort(34).toInt())
        assertEquals(576000, bytes.getInt(40))
    }
    @Test fun unsupportedSurroundOversizeAndMisalignedPcmCannotBecomeValidOutput() {
        listOf<() -> Unit>(
            { AudioPolicy.validate("audio/flac", 48000, 6, 1000) },
            { AudioPolicy.validate("audio/unknown", 48000, 2, 1000) },
            { AudioPolicy.validate("audio/mpeg", 48000, 2, 600001) },
            { AudioPolicy.wavHeader(48000, 2, 5) },
            { AudioPolicy.wavHeader(48000, 2, AudioPolicy.MAX_BYTES) },
        ).forEach { action -> try { action(); fail("Invalid audio accepted") } catch (_: IllegalArgumentException) { } }
    }
    @Test fun codecPaddingIsAllowedOnlyOnOutputsAtTheInputDurationBoundary() {
        AudioPolicy.validate("audio/mp4a-latm", 48000, 2, 600100, output = true)
        try { AudioPolicy.validate("audio/mp4a-latm", 48000, 2, 600100); fail("Input duration limit bypassed") }
        catch (_: IllegalArgumentException) { }
    }
}

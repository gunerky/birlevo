package com.example.contentstudio.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class FlacHeaderTest {
    private fun header() = ByteBuffer.allocate(60).order(ByteOrder.BIG_ENDIAN).apply {
        put("fLaC".toByteArray()); put(0); put(0); put(0); put(34)
        putShort(4096); putShort(4096); position(18)
        putLong((48_000L shl 44) or (1L shl 41) or (15L shl 36))
    }.array()
    @Test fun nativeHeaderBecomesSingleStreamInfoWithActualFrameCountAndPcmDigest() {
        val normalized = FlacHeader.fromCodec(header(), 48_000, 2)
        val digest = ByteArray(16) { (it * 7).toByte() }
        val finished = FlacHeader.finish(normalized, 144_000, digest)
        assertEquals(42, finished.size); assertEquals(0x80, finished[4].toInt() and 0xff)
        assertEquals((48_000L shl 44) or (1L shl 41) or (15L shl 36) or 144_000,
            ByteBuffer.wrap(finished).order(ByteOrder.BIG_ENDIAN).getLong(18))
        assertArrayEquals(digest, finished.copyOfRange(26, 42))
        assertArrayEquals(ByteArray(6), finished.copyOfRange(12, 18))
        assertFalse(normalized.contentEquals(finished))
    }
    @Test fun malformedCodecMetadataCannotMasqueradeAsExpectedAudio() {
        val invalid = listOf(header().copyOf(41), header().apply { this[0] = 0 },
            header().apply { this[7] = 35 }, header().apply { this[4] = 1 }, header().apply { this[8] = 0; this[9] = 0 })
        invalid.forEach { data -> assertThrows(IllegalArgumentException::class.java) { FlacHeader.fromCodec(data, 48_000, 2) } }
        assertThrows(IllegalArgumentException::class.java) { FlacHeader.fromCodec(header(), 44_100, 2) }
        assertThrows(IllegalArgumentException::class.java) { FlacHeader.fromCodec(header(), 48_000, 1) }
        assertThrows(IllegalArgumentException::class.java) { FlacHeader.finish(ByteArray(42), 0, ByteArray(16)) }
    }
    @Test fun oggVersionAndRateRulesDoNotReduceOtherOutputs() {
        assertEquals(AudioOutputIssue.ANDROID_VERSION, audioOutputConstraint(AudioTarget.OGG, 28))
        assertNull(audioOutputConstraint(AudioTarget.OGG, 29))
        for (target in listOf(AudioTarget.WAV, AudioTarget.M4A, AudioTarget.FLAC)) {
            assertNull(audioOutputConstraint(target, 24))
            assertEquals(44_100, target.sampleRate(44_100))
        }
        assertEquals(48_000, AudioTarget.OGG.sampleRate(44_100))
    }
}

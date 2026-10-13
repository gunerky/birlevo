package com.example.contentstudio.audio

import com.example.contentstudio.media.SessionAsset
import com.example.contentstudio.processing.ProcessingCodec
import org.junit.Assert.*
import org.junit.Test

class AudioSessionCodecTest {
    private val asset = SessionAsset("audio", "source-fixed.media", "content://original/1", "original.wav", 1000)
    @Test fun oldWavM4aSessionsKeepVersionNamesAndRequestIdentity() {
        for (target in listOf(AudioTarget.WAV, AudioTarget.M4A)) {
            val session = AudioSession(asset, target, 128_000)
            val encoded = AudioSessionCodec.encode(session)
            assertEquals(session, AudioSessionCodec.decode(encoded))
            assertEquals(ProcessingCodec.fingerprint(encoded), ProcessingCodec.fingerprint(AudioSessionCodec.encode(AudioSessionCodec.decode(encoded))))
            assertTrue(encoded.contains("\"version\":1")); assertTrue(encoded.contains("\"target\":\"${target.name}\""))
        }
    }
    @Test fun newTargetsRemainDistinctAndUnknownTargetsAreNotSubstituted() {
        val requests = AudioTarget.entries.map { target ->
            val session = AudioSession(asset, target, 256_000)
            val encoded = AudioSessionCodec.encode(session)
            assertEquals(session, AudioSessionCodec.decode(encoded))
            ProcessingCodec.fingerprint(encoded)
        }
        assertEquals(AudioTarget.entries.size, requests.toSet().size)
        val invalid = AudioSessionCodec.encode(AudioSession(asset, AudioTarget.FLAC, 128_000)).replace("FLAC", "UNKNOWN")
        assertThrows(IllegalArgumentException::class.java) { AudioSessionCodec.decode(invalid) }
    }
}

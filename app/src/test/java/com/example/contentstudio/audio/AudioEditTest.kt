package com.example.contentstudio.audio

import com.example.contentstudio.media.SessionAsset
import com.example.contentstudio.processing.ProcessingCodec
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class AudioEditTest {
    private val first = SessionAsset("audio", "source-one.media", "content://one", "one.wav", 1000)
    private val second = first.copy(file = "source-two.media", uri = "content://two", name = "two.wav")
    @Test fun exactDecimalMillisecondsAndFullEndSemantics() {
        assertEquals(AudioRange(125, 2251), AudioRange.parse("0.125", "2,251", 3000))
        assertEquals(AudioRange(500), AudioRange.parse("0.500", "3", 3000))
        assertEquals(AudioRange(500, 1500), AudioRange.parse("٠٫٥", "١٫٥", 3000))
        assertEquals(AudioRange(500, 1500), AudioRange.parse("०.५", "१.५", 3000))
        assertEquals("0.001", AudioRange.seconds(1)); assertEquals("600", AudioRange.seconds(600000))
        for ((start, end) in listOf("NaN" to "3", "-1" to "3", "0" to "3.001", "0" to "0", "3" to "2", "0.0001" to "2", "1e0" to "2")) {
            assertNull("$start/$end", AudioRange.parse(start, end, 3000))
        }
        assertThrows(IllegalArgumentException::class.java) { AudioRange(3000).duration(3000) }
    }
    @Test fun editsRoundTripAndEachOrderRangeAndProfileHasSeparateRequestIdentity() {
        val clips = listOf(SavedAudioClip(first, AudioRange(500, 1500)), SavedAudioClip(second, AudioRange(250)))
        val edit = AudioSession(first, AudioTarget.MP3, 128000, clips, 48000, 2)
        val variants = listOf(edit, edit.copy(asset = second, clips = clips.reversed()), edit.copy(rate = 44100),
            edit.copy(channels = 1), edit.copy(clips = clips.mapIndexed { i, clip -> if (i == 0) clip.copy(range = AudioRange(600, 1500)) else clip }))
        val requests = variants.map { value ->
            val encoded = AudioSessionCodec.encode(value)
            assertEquals(value, AudioSessionCodec.decode(encoded))
            assertTrue(encoded.contains("\"version\":2"))
            ProcessingCodec.fingerprint(encoded)
        }
        assertEquals(variants.size, requests.toSet().size)
        val legacy = AudioSession(first, AudioTarget.WAV, 128000)
        assertTrue(AudioSessionCodec.encode(legacy).contains("\"version\":1"))
    }
    @Test fun malformedEditMetadataNeverBecomesAnUneditedFallback() {
        val edit = AudioSession(first, AudioTarget.WAV, 128000, listOf(SavedAudioClip(first, AudioRange(1))))
        for (invalid in listOf(edit.copy(rate = 1), edit.copy(channels = 3), edit.copy(clips = emptyList()),
            edit.copy(clips = List(3) { SavedAudioClip(first) }), edit.copy(clips = listOf(SavedAudioClip(first), SavedAudioClip(first))),
            edit.copy(clips = listOf(SavedAudioClip(first, AudioRange(100, 99)))))) {
            assertThrows(IllegalArgumentException::class.java) { AudioSessionCodec.encode(invalid) }
        }
        assertThrows(IllegalArgumentException::class.java) { AudioSessionCodec.decode(AudioSessionCodec.encode(edit).replace("\"version\":2", "\"version\":3")) }
        val missing = JSONObject(AudioSessionCodec.encode(edit))
        missing.getJSONArray("clips").getJSONObject(0).remove("end")
        assertThrows(IllegalArgumentException::class.java) { AudioSessionCodec.decode(missing.toString()) }
    }
}

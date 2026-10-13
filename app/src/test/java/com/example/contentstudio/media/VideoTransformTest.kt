package com.example.contentstudio.media

import org.junit.Assert.*
import org.junit.Test

class VideoTransformTest {
    private val asset = SessionAsset("video", "source-a.mp4", "content://test/a", "a.mp4", 100)
    @Test fun transformedSessionsKeepSettingsAndDefaultsKeepOldBytes() {
        val original = VideoSession(asset, VideoQuality.HIGH, 1920, VideoTrim(0, 600000))
        val legacy = MediaSessionCodec.video(original)
        assertEquals(legacy, MediaSessionCodec.video(MediaSessionCodec.video(legacy)))
        for (canvas in VideoCanvas.entries) for (fit in VideoFit.entries) for (speed in videoSpeeds) {
            val value = original.copy(canvas = canvas, fit = fit, speed = speed)
            assertEquals(value, MediaSessionCodec.video(MediaSessionCodec.video(value)))
        }
        val transformed = org.json.JSONObject(MediaSessionCodec.video(original.copy(speed = 0.5f)))
        assertEquals(3, transformed.getInt("version"))
        transformed.remove("canvas")
        assertThrows(org.json.JSONException::class.java) { MediaSessionCodec.video(transformed.toString()) }
        assertThrows(IllegalArgumentException::class.java) { MediaSessionCodec.video(original.copy(speed = Float.NaN)) }
    }
}

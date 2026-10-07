package com.example.contentstudio.media

import org.junit.Assert.*
import org.junit.Test

class VideoTextLayerTest {
    private val asset = SessionAsset("video", "source-test.mp4", "content://original/video", "video.mp4", 100)
    private val base = VideoSession(asset, VideoQuality.BALANCED, 1280, VideoTrim(0, 3000))
    @Test fun allOldRequestVersionsRemainByteStableWhenNoLayerIsAdded() {
        for (session in listOf(base, base.copy(clips = listOf(SavedVideoClip(asset, base.trim),
            SavedVideoClip(asset.copy(file = "source-two.mp4"), base.trim))), base.copy(speed = .5f))) {
            val json = MediaSessionCodec.video(session)
            assertEquals(json, MediaSessionCodec.video(MediaSessionCodec.video(json)))
            assertNull(MediaSessionCodec.video(json).textLayer)
        }
    }
    @Test fun unicodeLayerRoundTripsAndEverySettingChangesCompletedJobIdentity() {
        val layer = VideoTextLayer("İğ 中文 مرحبا\nSecond line", endMs = 2000)
        val session = base.copy(textLayer = layer)
        val encoded = MediaSessionCodec.video(session)
        assertEquals(4, org.json.JSONObject(encoded).getInt("version"))
        assertEquals(session, MediaSessionCodec.video(encoded))
        val variants = listOf(layer, layer.copy(text = "Different"), layer.copy(position = VideoTextPosition.TOP_RIGHT),
            layer.copy(color = VideoTextColor.CORAL), layer.copy(size = .08f), layer.copy(startMs = 100), layer.copy(endMs = null))
        assertEquals(variants.size, variants.map {
            com.example.contentstudio.processing.ProcessingCodec.fingerprint(MediaSessionCodec.video(base.copy(textLayer = it)))
        }.distinct().size)
        assertFalse(encoded.contains("confirmedCopy"))
    }
    @Test fun outputTimeUsesInclusiveStartExclusiveEndAndDynamicVideoEnd() {
        val layer = VideoTextLayer("Title", startMs = 1000, endMs = 2000)
        assertFalse(layer.visible(999999, 3000)); assertTrue(layer.visible(1000000, 3000))
        assertTrue(layer.visible(1999999, 3000)); assertFalse(layer.visible(2000000, 3000))
        assertFalse(layer.validTime(1000)); assertTrue(layer.validTime(1500))
        assertEquals(1500L, layer.end(1500))
        assertTrue(layer.copy(endMs = null).visible(3999999, 4000))
        assertFalse(VideoTextLayer("  \n ").visible(0, 3000))
    }
    @Test fun malformedTimingSizesEnumsAndUnboundedTextAreRejected() {
        for (action in listOf<() -> Unit>({ VideoTextLayer("x".repeat(201)) }, { VideoTextLayer("\n".repeat(6)) },
            { VideoTextLayer(size = Float.NaN) }, { VideoTextLayer(size = .2f) },
            { VideoTextLayer(startMs = -1) }, { VideoTextLayer(startMs = 100, endMs = 100) })) {
            assertThrows(IllegalArgumentException::class.java) { action() }
        }
        val json = org.json.JSONObject(MediaSessionCodec.video(base.copy(textLayer = VideoTextLayer("A"))))
        json.getJSONObject("textLayer").put("position", "UNSUPPORTED")
        assertThrows(IllegalArgumentException::class.java) { MediaSessionCodec.video(json.toString()) }
    }
}

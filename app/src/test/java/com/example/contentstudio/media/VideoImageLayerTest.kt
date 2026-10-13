package com.example.contentstudio.media

import org.junit.Assert.*
import org.junit.Test

class VideoImageLayerTest {
    private val video = SessionAsset("video", "source-test.mp4", "content://video", "video.mp4", 100)
    private val image = SessionAsset("logo", "overlay-test.image", "content://image", "logo.png", 200)
    private val base = VideoSession(video, VideoQuality.BALANCED, 1280, VideoTrim(0, 3000))
    @Test fun versionFiveRoundTripsImageAloneAndBothLayersWithoutChangingOlderRequests() {
        val layer = VideoImageLayer(image, 100, 50, endMs = 2000)
        for (text in listOf(null, VideoTextLayer("Title"))) {
            val session = base.copy(imageLayer = layer, textLayer = text)
            val json = MediaSessionCodec.video(session)
            assertEquals(5, org.json.JSONObject(json).getInt("version"))
            assertEquals(session, MediaSessionCodec.video(json))
        }
        for (session in listOf(base, base.copy(textLayer = VideoTextLayer("Title")))) {
            val json = MediaSessionCodec.video(session)
            assertEquals(json, MediaSessionCodec.video(MediaSessionCodec.video(json)))
            assertNull(MediaSessionCodec.video(json).imageLayer)
        }
    }
    @Test fun everyAssetAndSettingInvalidatesOldOutputIdentity() {
        val layer = VideoImageLayer(image, 100, 50, endMs = 2000)
        val variants = listOf(layer, layer.copy(asset = image.copy(file = "overlay-new.image")),
            layer.copy(position = VideoTextPosition.BOTTOM_LEFT), layer.copy(size = .4f),
            layer.copy(opacity = .5f), layer.copy(startMs = 500), layer.copy(endMs = null))
        assertEquals(variants.size, variants.map { com.example.contentstudio.processing.ProcessingCodec.fingerprint(
            MediaSessionCodec.video(base.copy(imageLayer = it))) }.distinct().size)
    }
    @Test fun completeAspectRatioAndInclusiveStartExclusiveEndArePreserved() {
        val layer = VideoImageLayer(image, 100, 50, size = .4f, startMs = 100, endMs = 1000)
        assertEquals(72f to 36f, layer.dimensions(180, 320))
        val tall = layer.copy(width = 50, height = 100)
        val dimensions = tall.dimensions(320, 100)
        assertEquals(45f, dimensions.first, .001f); assertEquals(90f, dimensions.second, .001f)
        assertFalse(layer.visible(99999, 3000)); assertTrue(layer.visible(100000, 3000))
        assertFalse(layer.visible(1000000, 3000)); assertFalse(layer.validTime(100))
        assertTrue(layer.copy(endMs = null).visible(2999999, 3000))
    }
    @Test fun malformedPathsDimensionsAndSettingsAreRejected() {
        for (action in listOf<() -> Unit>({ VideoImageLayer(image.copy(file = "../overlay-x"), 100, 50) },
            { VideoImageLayer(image.copy(file = "source-x.mp4"), 100, 50) },
            { VideoImageLayer(image.copy(bytes = 0), 100, 50) },
            { VideoImageLayer(image, 0, 50) }, { VideoImageLayer(image, 20000, 20000) },
            { VideoImageLayer(image, 100, 50, size = Float.NaN) },
            { VideoImageLayer(image, 100, 50, opacity = 0f) },
            { VideoImageLayer(image, 100, 50, startMs = 100, endMs = 100) }))
            assertThrows(IllegalArgumentException::class.java) { action() }
    }
}

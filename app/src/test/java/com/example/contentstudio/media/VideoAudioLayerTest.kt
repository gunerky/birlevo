package com.example.contentstudio.media

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat

class VideoAudioLayerTest {
    private val video = SessionAsset("video", "source-test.mp4", "content://video", "video.mp4", 100)
    private val sound = SessionAsset("sound", "sound-test.media", "content://sound", "tone.wav", 200)
    private val layer = VideoAudioLayer(sound, 3000, 44100, 1)
    private val base = VideoSession(video, VideoQuality.BALANCED, 640, VideoTrim(0, 3000))
    @Test fun versionSixRoundTripsAllLayersWhileOlderRequestsStayStable() {
        val image = VideoImageLayer(SessionAsset("image", "overlay-test.image", "content://image", "logo.png", 300), 100, 50)
        for (session in listOf(base.copy(audioLayer = layer), base.copy(audioLayer = layer, imageLayer = image, textLayer = VideoTextLayer("Title")))) {
            val json = MediaSessionCodec.video(session)
            assertEquals(6, org.json.JSONObject(json).getInt("version")); assertEquals(session, MediaSessionCodec.video(json))
        }
        for (session in listOf(base, base.copy(textLayer = VideoTextLayer("Title")), base.copy(imageLayer = image))) {
            val json = MediaSessionCodec.video(session)
            assertEquals(json, MediaSessionCodec.video(MediaSessionCodec.video(json)))
            assertNull(MediaSessionCodec.video(json).audioLayer)
        }
    }
    @Test fun trimsPlacementAndEveryLevelChangeInvalidateCompletedOutput() {
        val variants = listOf(layer, layer.copy(sourceStartMs = 100), layer.copy(sourceEndMs = 2000),
            layer.copy(startMs = 500), layer.copy(volume = 0f), layer.copy(originalVolume = 0f),
            layer.copy(asset = sound.copy(file = "sound-other.media")))
        assertEquals(variants.size, variants.map { com.example.contentstudio.processing.ProcessingCodec.fingerprint(
            MediaSessionCodec.video(base.copy(audioLayer = it))) }.distinct().size)
        assertEquals(1500L, layer.copy(startMs = 500).length(2000))
        assertFalse(layer.copy(startMs = 3000).validTime(3000))
    }
    @Test fun malformedSourcePathsRangesAndGainsAreRejected() {
        for (action in listOf<() -> Unit>({ layer.copy(asset = sound.copy(file = "../sound-x")) },
            { layer.copy(sourceStartMs = 3000) }, { layer.copy(sourceEndMs = 4000) },
            { layer.copy(startMs = -1) }, { layer.copy(rate = 0) }, { layer.copy(channels = 6) },
            { layer.copy(volume = Float.NaN) }, { layer.copy(originalVolume = 1.1f) }))
            assertThrows(IllegalArgumentException::class.java) { action() }
    }
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    @Test fun gainIsExplicitBoundedAndEmptyInputDoesNotAllocateOrCorruptSamples() {
        val processor = AudioGainProcessor(.5f)
        processor.configure(AudioFormat(48000, 2, C.ENCODING_PCM_16BIT)); processor.flush()
        val data = ByteBuffer.allocateDirect(8).order(ByteOrder.nativeOrder())
        for (value in listOf(12000, -12000, 32766, -32768)) data.putShort(value.toShort())
        data.flip(); processor.queueInput(data)
        val output = processor.output.order(ByteOrder.nativeOrder())
        assertEquals(listOf<Short>(6000,-6000,16383,-16384), List(4) { output.short })
        processor.queueInput(ByteBuffer.allocate(0)); assertFalse(processor.output.hasRemaining()); processor.reset()
    }
}

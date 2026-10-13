package com.example.contentstudio

import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.test.platform.app.InstrumentationRegistry
import com.example.contentstudio.media.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

@androidx.annotation.OptIn(UnstableApi::class)
class VideoTextDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun uri(name: String) = Uri.parse("content://com.example.contentstudio.test.videos/$name")
    private fun textAt(result: CompressedVideo, timeMs: Long, visible: Boolean, color: Int = Color.WHITE) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(result.file.path)
            val frame = retriever.getFrameAtTime(timeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST)!!
            try {
                var matches = 0
                for (y in 0 until frame.height / 3) for (x in 0 until frame.width * 3 / 4) {
                    val value = frame.getPixel(x, y)
                    if (kotlin.math.abs(Color.red(value) - Color.red(color)) < 35 &&
                        kotlin.math.abs(Color.green(value) - Color.green(color)) < 35 &&
                        kotlin.math.abs(Color.blue(value) - Color.blue(color)) < 35) matches++
                }
                if (visible) assertTrue("No text at $timeMs ms: $matches", matches > 12)
                else assertTrue("Text leaked outside range at $timeMs ms: $matches", matches < 4)
            } finally { frame.recycle() }
        } finally { retriever.release() }
    }
    @Test fun allFormatsShowTextOnlyInGlobalOutputRangeAcrossJoinedSpeedAdjustedClips() = runBlocking<Unit>(Dispatchers.IO) {
        val engine = VideoCompressor(context)
        val first = engine.importVideo(uri("edit-first.mp4")); val second = engine.importVideo(uri("edit-second.mp4"))
        val originals = listOf(first.file.readBytes(), second.file.readBytes())
        val folder = File(context.getExternalFilesDir(null), "qa/video-text").apply { mkdirs() }
        try {
            for (speed in listOf(.5f, 2f)) for (format in VideoOutputFormat.entries) {
                val slow = speed == .5f
                val layer = VideoTextLayer("Birlevo İğ\nHello", VideoTextPosition.TOP_LEFT, .08f,
                    if (slow) VideoTextColor.WHITE else VideoTextColor.CORAL,
                    if (slow) 1500 else 300, if (slow) 2500 else 700)
                val edit = VideoEdit(listOf(VideoClip(first, VideoTrim(1100, 2100)),
                    VideoClip(second, VideoTrim(1700, 2700))), VideoCanvas.PORTRAIT_9_16,
                    VideoFit.CENTER_CROP, speed, layer)
                assertNull(engine.outputSupport(edit, VideoQuality.BALANCED, 640)[format])
                val result = engine.compress(edit, VideoQuality.BALANCED, 640, format) {}
                try {
                    result.file.copyTo(File(folder, "$speed-${format.name}.${format.extension}"), overwrite = true)
                    engine.validateEditOutput(edit, result.info, VideoQuality.BALANCED, 640, format)
                    assertEquals(180, result.info.width); assertEquals(320, result.info.height)
                    // This emulator's retriever seeks WebM to an earlier keyframe even with
                    // OPTION_CLOSEST. Host QA decodes every actual timestamp for all formats.
                    if (format != VideoOutputFormat.WEBM_VP8) {
                        textAt(result, if (slow) 750 else 100, false, layer.color.argb)
                        textAt(result, if (slow) 1750 else 400, true, layer.color.argb)
                        textAt(result, if (slow) 2250 else 600, true, layer.color.argb)
                        textAt(result, if (slow) 3250 else 850, false, layer.color.argb)
                    }
                    engine.export(edit, result, uri("export.mp4"), allowNonSmallerCopy = true)
                    assertArrayEquals(result.file.readBytes(), context.contentResolver.openInputStream(uri("export.mp4"))!!.use { it.readBytes() })
                } finally { result.file.delete(); result.info.poster?.recycle() }
            }
            assertArrayEquals(originals[0], first.file.readBytes()); assertArrayEquals(originals[1], second.file.readBytes())
        } finally { first.file.delete(); second.file.delete(); first.info.poster?.recycle(); second.info.poster?.recycle() }
    }
    @Test fun textureIsCachedUnicodePositionsFitAndInvalidLayoutsDoNotSilentlyShrink() {
        for (position in VideoTextPosition.entries) {
            val layer = VideoTextLayer("İğ 中文 مرحبا\nHello", position, .06f, startMs = 200, endMs = 800)
            val overlay = VideoTextOverlay(layer, 1000)
            overlay.configure(Size(320, 180))
            val bitmap = overlay.getBitmap(0)
            assertSame(bitmap, overlay.getBitmap(400000)); val generation = bitmap.generationId
            assertEquals(0f, overlay.getOverlaySettings(199999).alphaScale)
            assertEquals(1f, overlay.getOverlaySettings(200000).alphaScale)
            assertEquals(0f, overlay.getOverlaySettings(800000).alphaScale)
            assertEquals(generation, overlay.getBitmap(900000).generationId)
            overlay.release(); assertTrue(bitmap.isRecycled)
        }
        val long = VideoTextLayer("W".repeat(200), size = .10f)
        assertFalse(VideoTextRenderer.fits(long, 320, 180))
        assertThrows(IllegalArgumentException::class.java) { VideoTextRenderer.bitmap(long, 320, 180) }
    }
    @Test fun cancelledTextExportRemovesWorkingFilesAndPreservesSource() = runBlocking<Unit>(Dispatchers.IO) {
        val engine = VideoCompressor(context)
        val source = engine.importVideo(uri("cancel.mp4"))
        try {
            val before = source.file.parentFile!!.listFiles()!!.toSet()
            val started = CompletableDeferred<Unit>()
            val job = launch { engine.compress(VideoEdit(listOf(VideoClip(source)), textLayer = VideoTextLayer("Cancel test")),
                VideoQuality.HIGH, 1280, VideoOutputFormat.MP4_AVC) { started.complete(Unit) } }
            withTimeout(30000) { started.await() }; delay(150); job.cancelAndJoin()
            assertEquals(before, source.file.parentFile!!.listFiles()!!.toSet()); assertTrue(source.file.isFile)
        } finally { source.file.delete(); source.info.poster?.recycle() }
    }
}

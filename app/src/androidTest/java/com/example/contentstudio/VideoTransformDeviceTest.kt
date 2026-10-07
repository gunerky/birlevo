package com.example.contentstudio

import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.example.contentstudio.media.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.math.abs

class VideoTransformDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun uri(name: String) = Uri.parse("content://com.example.contentstudio.test.videos/$name")
    @Test fun slowTenMinuteSourceRemainsSupportedAndTwentyMinuteOutputRestores() = runBlocking<Unit>(Dispatchers.IO) {
        val engine = VideoCompressor(context)
        val source = engine.importVideo(uri("long-duration.mp4"))
        try {
            assertEquals(600000L, source.info.durationMs)
            val edit = VideoEdit(listOf(VideoClip(source)), speed = 0.5f)
            assertEquals(1200000L, edit.durationMs)
            val result = engine.compress(edit, VideoQuality.BALANCED, 640, VideoOutputFormat.MP4_AVC) {}
            try {
                engine.validateEditOutput(edit, result.info, VideoQuality.BALANCED, 640, result.format)
                assertTrue(result.info.durationMs > FormatSupport.MAX_VIDEO_DURATION_MS)
                val restored = engine.restoreOutput(result.file, result.bytes, result.elapsedMs, result.format)
                try { engine.validateEditOutput(edit, restored.info, VideoQuality.BALANCED, 640, restored.format) }
                finally { restored.info.poster?.recycle() }
            } finally { result.file.delete(); result.info.poster?.recycle() }
        } finally { source.file.delete(); source.info.poster?.recycle() }
    }
    private fun frame(result: CompressedVideo, timeUs: Long, expected: Int, borders: Boolean) {
        val reader = MediaMetadataRetriever()
        try {
            reader.setDataSource(result.file.path)
            val image = reader.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)!!
            try {
                val center = image.getPixel(image.width / 2, image.height / 2)
                for (channel in listOf(Color::red, Color::green, Color::blue))
                    assertTrue(abs(channel(center) - channel(expected)) < 35)
                if (borders) {
                    val top = image.getPixel(image.width / 2, image.height / 10)
                    assertTrue(Color.red(top) + Color.green(top) + Color.blue(top) < 30)
                }
            } finally { image.recycle() }
        } finally { reader.release() }
    }
    @Test fun slowPortraitFitAndFastSquareCropKeepOrderTimingAudioAndAllFormats() = runBlocking<Unit>(Dispatchers.IO) {
        val engine = VideoCompressor(context)
        val first = engine.importVideo(uri("edit-first.mp4")); val second = engine.importVideo(uri("edit-second.mp4"))
        val originals = listOf(first.file.readBytes(), second.file.readBytes())
        try {
            for (speed in listOf(0.5f, 2f)) for (format in VideoOutputFormat.entries) {
                val edit = VideoEdit(listOf(VideoClip(first, VideoTrim(1100, 2100)), VideoClip(second, VideoTrim(1700, 2700))),
                    if (speed == 0.5f) VideoCanvas.PORTRAIT_9_16 else VideoCanvas.SQUARE,
                    if (speed == 0.5f) VideoFit.FIT else VideoFit.CENTER_CROP, speed)
                assertNull(engine.outputSupport(edit, VideoQuality.BALANCED, 640)[format])
                val result = engine.compress(edit, VideoQuality.BALANCED, 640, format) {}
                try {
                    assertEquals(if (speed == 0.5f) 180 else 320, result.info.width)
                    assertEquals(320, result.info.height)
                    assertTrue(abs(result.info.durationMs - edit.durationMs) < 180)
                    assertTrue(result.info.hasAudio); assertEquals(48000, result.info.audioSampleRate)
                    assertEquals(2, result.info.audioChannels)
                    frame(result, (250000 / speed).toLong(), Color.GREEN, speed == 0.5f)
                    frame(result, (1250000 / speed).toLong(), Color.YELLOW, false)
                    val folder = File(context.getExternalFilesDir(null), "qa/video-transforms").apply { mkdirs() }
                    result.file.copyTo(File(folder, "speed-$speed-${format.name}.${format.extension}"), overwrite = true)
                } finally { result.file.delete(); result.info.poster?.recycle() }
            }
            val reference = engine.compress(VideoEdit(listOf(VideoClip(first, VideoTrim(1100, 2100)),
                VideoClip(second, VideoTrim(1700, 2700)))), VideoQuality.BALANCED, 640, VideoOutputFormat.MP4_AVC) {}
            try {
                val folder = File(context.getExternalFilesDir(null), "qa/video-transforms").apply { mkdirs() }
                reference.file.copyTo(File(folder, "reference-1x.mp4"), overwrite = true)
            } finally { reference.file.delete(); reference.info.poster?.recycle() }
            assertArrayEquals(originals[0], first.file.readBytes()); assertArrayEquals(originals[1], second.file.readBytes())
        } finally { first.file.delete(); second.file.delete(); first.info.poster?.recycle(); second.info.poster?.recycle() }
    }
    @Test fun speedAlsoChangesGeneratedSilenceWithoutShiftingSound() = runBlocking<Unit>(Dispatchers.IO) {
        val engine = VideoCompressor(context)
        val first = engine.importVideo(uri("silent.mp4")); val second = engine.importVideo(uri("edit-first.mp4"))
        try {
            for (speed in listOf(0.5f, 2f)) {
                val edit = VideoEdit(listOf(VideoClip(first, VideoTrim(0, 1000)), VideoClip(second, VideoTrim(1100, 2100))), speed = speed)
                val result = engine.compress(edit, VideoQuality.BALANCED, 640, VideoOutputFormat.MP4_AVC) {}
                try {
                    assertTrue(abs(result.info.durationMs - edit.durationMs) < 180)
                    val folder = File(context.getExternalFilesDir(null), "qa/video-transforms").apply { mkdirs() }
                    result.file.copyTo(File(folder, "silence-$speed.mp4"), overwrite = true)
                } finally { result.file.delete(); result.info.poster?.recycle() }
            }
        } finally { first.file.delete(); second.file.delete(); first.info.poster?.recycle(); second.info.poster?.recycle() }
    }
}

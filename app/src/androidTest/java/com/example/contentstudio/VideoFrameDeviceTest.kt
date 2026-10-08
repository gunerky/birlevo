package com.example.contentstudio

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.contentstudio.media.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class VideoFrameDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun uri(name: String) = Uri.parse("content://com.example.contentstudio.test.videos/$name")
    private val report get() = File(context.getExternalFilesDir(null), "qa/video-frame").apply { mkdirs() }
    private fun save(bitmap: Bitmap, name: String) { File(report, "$name.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
    private suspend fun await(block: () -> Boolean) { withTimeout(30000) { while (!block()) delay(50) } }

    @Test fun originalAndLayeredOutputsKeepActualTimeResolutionRotationAndPixels() = runBlocking<Unit>(Dispatchers.IO) {
        val engine = VideoCompressor(context); val extractor = VideoFrameExtractor(context)
        val first = engine.importVideo(uri("edit-first.mp4")); val second = engine.importVideo(uri("edit-second.mp4"))
        val portrait = engine.importVideo(uri("portrait.mp4"))
        val snapshots = listOf(first, second, portrait).map { it.file.readBytes() }
        try {
            for (source in listOf(first, portrait)) {
                val name = if (source == first) "source" else "portrait"
                source.file.copyTo(File(report, "$name.mp4"), overwrite = true)
                val target = VideoFrameTarget(source.file, source.info.durationMs, source.name)
                for (time in listOf(0L, 1300L, source.info.durationMs - 1)) {
                    val frame = extractor.read(target, time, false)
                    try {
                        assertEquals(source.info.width, frame.bitmap.width); assertEquals(source.info.height, frame.bitmap.height)
                        assertTrue(frame.timeUs <= source.info.durationMs * 1000)
                        if (time == 1300L) assertTrue(frame.timeUs >= 1300000L && frame.timeUs < 1400000L)
                        save(frame.bitmap, "$name-$time")
                        File(report, "$name-$time.pts").writeText(frame.timeUs.toString())
                    } finally { frame.bitmap.recycle() }
                }
                val preview = extractor.read(target, 1300, true)
                try { assertTrue(maxOf(preview.bitmap.width, preview.bitmap.height) <= 512) }
                finally { preview.bitmap.recycle() }
            }
            val logo = VideoImageAssets(context).importImage(Uri.parse("content://com.example.contentstudio.test.fixtures/overlay-logo.png"))
            try {
                for (format in VideoOutputFormat.entries) {
                    val edit = VideoEdit(listOf(VideoClip(first, VideoTrim(1100, 2100)), VideoClip(second, VideoTrim(1700, 2700))),
                        VideoCanvas.PORTRAIT_9_16, VideoFit.CENTER_CROP, textLayer = VideoTextLayer("Birlevo", VideoTextPosition.TOP_LEFT),
                        imageLayer = logo.layer.copy(startMs = 1000, endMs = 2000))
                    val output = engine.compress(edit, VideoQuality.BALANCED, 640, format) {}
                    try {
                        output.file.copyTo(File(report, "${format.name}.${format.extension}"), overwrite = true)
                        for (time in listOf(500L, 1500L)) {
                            val frame = extractor.read(VideoFrameTarget(output.file, output.info.durationMs, "result"), time, false)
                            try {
                                assertEquals(output.info.width, frame.bitmap.width); assertEquals(output.info.height, frame.bitmap.height)
                                assertTrue(frame.timeUs >= time * 1000 && frame.timeUs < (time + 100) * 1000)
                                val center = frame.bitmap.getPixel(frame.bitmap.width / 2, frame.bitmap.height / 2)
                                assertTrue(Color.green(center) > 180); assertTrue(Color.blue(center) < 40)
                                save(frame.bitmap, "${format.name}-$time"); File(report, "${format.name}-$time.pts").writeText(frame.timeUs.toString())
                            } finally { frame.bitmap.recycle() }
                        }
                    } finally { output.file.delete(); output.info.poster?.recycle() }
                }
            } finally { VideoImageAssets(context).resolve(logo.layer).delete(); logo.preview.recycle() }
            listOf(first, second, portrait).forEachIndexed { index, source -> assertArrayEquals(snapshots[index], source.file.readBytes()) }
        } finally { listOf(first, second, portrait).forEach { it.file.delete(); it.info.poster?.recycle() } }
    }

    @Test fun fullPngHandoffOwnsBytesProtectsOriginalsAndRejectsFailureWithoutReplacingImage() = runBlocking<Unit>(Dispatchers.IO) {
        val video = VideoCompressor(context).importVideo(uri("audio.mp4"))
        val original = video.file.readBytes(); val store = ViewModelStore()
        lateinit var model: ImageCompressionViewModel
        withContext(Dispatchers.Main) { model = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(context.applicationContext as Application))[ImageCompressionViewModel::class.java] }
        try {
            val frame = VideoFrameExtractor(context).write(VideoFrameTarget(video.file, video.info.durationMs, video.name), 1234)
            val png = frame.readBytes()
            withContext(Dispatchers.Main) { assertTrue(model.addFrame(frame, setOf(video.originalUri))) }
            val source = model.state.value.source!!
            assertEquals(1280, source.width); assertEquals(720, source.height); assertEquals("image/png", source.mime)
            assertArrayEquals(png, source.file.readBytes()); frame.delete(); assertTrue(source.file.isFile)
            for (format in ImageOutputFormat.entries) {
                withContext(Dispatchers.Main) { model.setOptions(format = format); model.preview() }; await { !model.state.value.busy }
                val result = model.state.value.result!!
                var denied = false
                try { ImageCompressor(context).export(source, result, video.originalUri, true) } catch (_: IllegalArgumentException) { denied = true }
                assertTrue(denied)
                val staged = MediaOutputs(context).stage(result.file, format.mime, "frame.${format.extension}", source.bytes, true)
                assertArrayEquals(result.file.readBytes(), context.contentResolver.openInputStream(staged.uri)!!.use { it.readBytes() })
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(result.file.path, bounds); assertEquals(format.mime, bounds.outMimeType)
                if (result.bytes >= source.bytes) {
                    var rejected = false
                    try { MediaOutputs(context).stage(result.file, format.mime, "denied", source.bytes) } catch (_: IllegalArgumentException) { rejected = true }
                    assertTrue(rejected)
                }
            }
            val old = model.state.value
            val invalid = File(context.cacheDir, "video-frames/frame-invalid.png").apply { writeText("invalid") }
            try { withContext(Dispatchers.Main) { assertFalse(model.addFrame(invalid, setOf(video.originalUri))) } }
            finally { invalid.delete() }
            assertEquals(old.source, model.state.value.source); assertEquals(old.result, model.state.value.result)
            val files = File(context.cacheDir, "video-frames").listFiles()!!.toSet()
            val operation = launch { VideoFrameExtractor(context).write(VideoFrameTarget(video.file, video.info.durationMs, "cancel"), 5000) }
            delay(10); operation.cancelAndJoin()
            assertEquals(files, File(context.cacheDir, "video-frames").listFiles()!!.toSet())
            assertArrayEquals(original, video.file.readBytes())
        } finally { withContext(Dispatchers.Main) { model.reset(); store.clear() }; video.file.delete(); video.info.poster?.recycle() }
    }
}

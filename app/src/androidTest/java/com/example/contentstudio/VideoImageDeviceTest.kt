package com.example.contentstudio

import android.app.Application
import android.graphics.Color
import android.net.Uri
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.test.platform.app.InstrumentationRegistry
import com.example.contentstudio.media.*
import com.example.contentstudio.processing.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException

@androidx.annotation.OptIn(UnstableApi::class)
class VideoImageDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun video(name: String) = Uri.parse("content://com.example.contentstudio.test.videos/$name")
    private fun image(name: String) = Uri.parse("content://com.example.contentstudio.test.fixtures/$name")
    private suspend fun main(block: suspend () -> Unit) = withContext(Dispatchers.Main) { block() }
    private suspend fun await(block: () -> Boolean) = withTimeout(120000) { while (!block()) delay(50) }

    @Test fun sixRealOutputsContainBothLayersWithTransparencyTimingAndUntouchedOriginals() = runBlocking<Unit>(Dispatchers.IO) {
        val engine = VideoCompressor(context); val assets = VideoImageAssets(context)
        val first = engine.importVideo(video("edit-first.mp4")); val second = engine.importVideo(video("edit-second.mp4"))
        val imported = assets.importImage(image("overlay-logo.png"))
        val originals = listOf(first.file.readBytes(), second.file.readBytes(), assets.resolve(imported.layer).readBytes())
        assertArrayEquals(originals[2], context.contentResolver.openInputStream(image("overlay-logo.png"))!!.use { it.readBytes() })
        val folder = File(context.getExternalFilesDir(null), "qa/video-image").apply { mkdirs() }
        try {
            for (speed in listOf(.5f, 2f)) for (format in VideoOutputFormat.entries) {
                val slow = speed == .5f
                val layer = imported.layer.copy(position = VideoTextPosition.BOTTOM_RIGHT, size = .4f, opacity = .5f,
                    startMs = if (slow) 1500 else 300, endMs = if (slow) 2500 else 700)
                val edit = VideoEdit(listOf(VideoClip(first, VideoTrim(1100, 2100)), VideoClip(second, VideoTrim(1700, 2700))),
                    VideoCanvas.PORTRAIT_9_16, VideoFit.CENTER_CROP, speed,
                    VideoTextLayer("Birlevo", VideoTextPosition.TOP_LEFT, .08f), layer)
                assertNull(engine.outputSupport(edit, VideoQuality.BALANCED, 640)[format])
                val result = engine.compress(edit, VideoQuality.BALANCED, 640, format) {}
                try {
                    engine.validateEditOutput(edit, result.info, VideoQuality.BALANCED, 640, format)
                    result.file.copyTo(File(folder, "$speed-${format.name}.${format.extension}"), overwrite = true)
                    for (uri in listOf(first.originalUri, second.originalUri, image("overlay-logo.png"))) {
                        var denied = false
                        try { engine.export(edit, result, uri, true) } catch (_: IllegalArgumentException) { denied = true }
                        assertTrue("Original overwrite allowed: $uri", denied)
                    }
                    engine.export(edit, result, video("export.mp4"), true)
                    assertArrayEquals(result.file.readBytes(), context.contentResolver.openInputStream(video("export.mp4"))!!.use { it.readBytes() })
                } finally { result.file.delete(); result.info.poster?.recycle() }
            }
            assertArrayEquals(originals[0], first.file.readBytes()); assertArrayEquals(originals[1], second.file.readBytes())
            assertArrayEquals(originals[2], assets.resolve(imported.layer).readBytes())
        } finally { first.file.delete(); second.file.delete(); assets.resolve(imported.layer).delete()
            first.info.poster?.recycle(); second.info.poster?.recycle(); imported.preview.recycle() }
    }

    @Test fun ownedDecoderPreservesAlphaExifAndCachedTextureWhileCancellationCleansOnlyUndeliveredWork() = runBlocking<Unit>(Dispatchers.IO) {
        val assets = VideoImageAssets(context)
        for (name in listOf("overlay-logo.png", "alpha.webp", "orientation-6.jpg")) {
            val imported = assets.importImage(image(name))
            try {
                val layer = imported.layer.copy(startMs = 200, endMs = 800, opacity = .5f)
                assertTrue(imported.preview.width <= 512 && imported.preview.height <= 512)
                if (name == "overlay-logo.png") assertEquals(0, Color.alpha(imported.preview.getPixel(50, 25)))
                if (name.startsWith("orientation")) { assertEquals(80, layer.width); assertEquals(120, layer.height) }
                val overlay = VideoImageOverlay(assets, layer, 1000); overlay.configure(Size(320, 180))
                val bitmap = overlay.getBitmap(0); assertSame(bitmap, overlay.getBitmap(500000))
                assertEquals(0f, overlay.getOverlaySettings(199999).alphaScale)
                assertEquals(.5f, overlay.getOverlaySettings(200000).alphaScale)
                assertEquals(0f, overlay.getOverlaySettings(800000).alphaScale)
                overlay.release(); assertTrue(bitmap.isRecycled)
                assertFalse(imported.preview.isRecycled)
            } finally { assets.resolve(imported.layer).delete(); imported.preview.recycle() }
        }
        val engine = VideoCompressor(context); val source = engine.importVideo(video("cancel.mp4"))
        val imported = assets.importImage(image("overlay-logo.png"))
        try {
            val before = source.file.parentFile!!.listFiles()!!.toSet()
            val started = CompletableDeferred<Unit>()
            val job = launch { engine.compress(VideoEdit(listOf(VideoClip(source)), imageLayer = imported.layer),
                VideoQuality.HIGH, 1280, VideoOutputFormat.MP4_AVC) { started.complete(Unit) } }
            withTimeout(30000) { started.await() }; delay(150); job.cancelAndJoin()
            assertEquals(before, source.file.parentFile!!.listFiles()!!.toSet())
            val copyJob = launch { assets.importImage(image("slow.jpg")) }
            delay(100); copyJob.cancelAndJoin()
            assertEquals(before, source.file.parentFile!!.listFiles()!!.toSet())
        } finally { source.file.delete(); source.info.poster?.recycle(); assets.resolve(imported.layer).delete(); imported.preview.recycle() }
    }

    private class Failing(private val storage: MediaSessionStorage): MediaSessionStorage {
        var fail = false
        override fun read() = storage.read()
        override fun write(json: String?) { if (fail) throw IOException("Synthetic disk error"); storage.write(json) }
    }
    @Test fun freshRestoreAndFailedAtomicReplacementRemovalProtectOwnedImageAndOldResult() = runBlocking<Unit> {
        val storage = AtomicMediaSession(File(context.noBackupFilesDir, "video-compression")); storage.write(null)
        val failing = Failing(storage); val store = ViewModelStore(); val models = mutableListOf<VideoCompressionViewModel>()
        fun own(): VideoCompressionViewModel {
            val model = VideoCompressionViewModel(context.applicationContext as Application, failing)
            ViewModelProvider(store, object: ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST") override fun <T: androidx.lifecycle.ViewModel> create(type: Class<T>): T = model as T
            })["model-${models.size}", VideoCompressionViewModel::class.java]; models += model; return model
        }
        lateinit var model: VideoCompressionViewModel
        val assets = VideoImageAssets(context)
        try {
            main { ProcessingJobs.get(context).awaitReady(); ProcessingJobs.get(context).forget(ProcessingKind.VIDEO); model = own() }
            await { !model.state.value.busy }; main { model.load(video("edit-first.mp4")) }; await { !model.state.value.busy }
            main { model.loadImage(image("overlay-logo.png")) }; await { !model.state.value.busy }
            val layer = model.state.value.imageLayer!!; val owned = assets.resolve(layer)
            main { model.setOptions(maxEdge = 640); assertTrue(model.awaitSession()) }
            await { model.state.value.outputSupport.isNotEmpty() }; main { model.preview() }; await { !model.state.value.busy }
            val result = model.state.value.result!!
            // Invalid replacement leaves the current image, completed output and saved draft intact.
            val before = storage.read()
            main { model.loadImage(image("wrong-type.jpg")) }; await { !model.state.value.busy }
            assertEquals(VideoMessage.IMAGE_LOAD_ERROR, model.state.value.message)
            assertEquals(layer, model.state.value.imageLayer); assertEquals(result.file, model.state.value.result!!.file)
            assertEquals(before, storage.read())
            // Aged owned sources remain protected from cache cleanup on a fresh model.
            owned.setLastModified(System.currentTimeMillis() - 48 * 3600000L)
            lateinit var restored: VideoCompressionViewModel
            main { restored = own() }; await { !restored.state.value.busy && restored.state.value.result != null }
            assertTrue(owned.exists()); assertEquals(layer, restored.state.value.imageLayer)
            assertEquals(result.file, restored.state.value.result!!.file); assertNotNull(restored.state.value.imagePreview)
            failing.fail = true
            main { restored.loadImage(image("alpha.webp")) }; await { !restored.state.value.busy }
            main { assertFalse(restored.awaitSession()) }
            val replacement = assets.resolve(restored.state.value.imageLayer!!)
            assertTrue(owned.exists()); assertEquals(before, storage.read()); assertTrue(replacement.exists())
            failing.fail = false; main { restored.retrySession(); assertTrue(restored.awaitSession()) }
            assertFalse(owned.exists()); assertTrue(replacement.exists())
            val replaced = storage.read(); failing.fail = true
            main { restored.setImageLayer(null); assertFalse(restored.awaitSession()) }
            assertTrue(replacement.exists()); assertEquals(replaced, storage.read())
            failing.fail = false; main { restored.retrySession(); assertTrue(restored.awaitSession()) }
            assertFalse(replacement.exists()); assertNull(MediaSessionCodec.video(storage.read()!!).imageLayer)
        } finally { failing.fail = false; main { models.lastOrNull()?.reset(); models.lastOrNull()?.awaitSession(); store.clear() } }
    }
}

package com.example.contentstudio

import android.app.Application
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.contentstudio.media.*
import com.example.contentstudio.audio.*
import com.example.contentstudio.processing.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import kotlin.math.abs

class VideoEditDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun uri(name: String) = Uri.parse("content://com.example.contentstudio.test.videos/$name")
    private suspend fun main(block: suspend () -> Unit) = withContext(Dispatchers.Main) { block() }
    private suspend fun await(block: () -> Boolean) { withTimeout(120000) { while (!block()) delay(25) } }
    private fun colorAt(result: CompressedVideo, timeUs: Long, expected: Int) {
        val reader = MediaMetadataRetriever()
        try {
            reader.setDataSource(result.file.path)
            val frame = reader.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)!!
            try {
                val color = frame.getPixel(frame.width / 2, frame.height / 2)
                assertTrue("red $color", abs(Color.red(expected) - Color.red(color)) < 30)
                assertTrue("green $color", abs(Color.green(expected) - Color.green(color)) < 30)
                assertTrue("blue $color", abs(Color.blue(expected) - Color.blue(color)) < 30)
            } finally { frame.recycle() }
        } finally { reader.release() }
    }
    @Test fun allThreeTargetsPreserveActualTrimmedColorsOrderDimensionsAndMixedAudio() = runBlocking<Unit>(Dispatchers.IO) {
        val engine = VideoCompressor(context)
        val first = engine.importVideo(uri("edit-first.mp4")); val second = engine.importVideo(uri("edit-second.mp4"))
        val originals = listOf(first.file.readBytes(), second.file.readBytes())
        val folder = File(context.getExternalFilesDir(null), "qa/video-edits").apply { mkdirs() }
        try {
            for (reverse in listOf(false, true)) for (target in VideoOutputFormat.entries) {
                val clips = listOf(VideoClip(first, VideoTrim(1100, 2100)), VideoClip(second, VideoTrim(1750, 2750)))
                    .let { if (reverse) it.reversed() else it }
                val edit = VideoEdit(clips)
                assertNull(engine.outputSupport(edit, VideoQuality.BALANCED, 640)[target])
                val result = engine.compress(edit, VideoQuality.BALANCED, 640, target) {}
                try {
                    assertEquals(48000, result.info.audioSampleRate); assertEquals(2, result.info.audioChannels)
                    assertTrue(abs(result.info.durationMs - 2000) < 120)
                    assertEquals(if (reverse) 180 else 320, result.info.width)
                    assertEquals(if (reverse) 320 else 180, result.info.height)
                    colorAt(result, 250000, if (reverse) Color.YELLOW else Color.GREEN)
                    colorAt(result, 1250000, if (reverse) Color.GREEN else Color.YELLOW)
                    result.file.copyTo(File(folder, "${if (reverse) "reverse" else "forward"}-${target.name}.${target.extension}"), overwrite = true)
                } finally { result.file.delete(); result.info.poster?.recycle() }
            }
            assertArrayEquals(originals[0], first.file.readBytes()); assertArrayEquals(originals[1], second.file.readBytes())
        } finally { first.file.delete(); second.file.delete(); first.info.poster?.recycle(); second.info.poster?.recycle() }
    }
    @Test fun silenceBeforeAndAfterAudioKeepsTimingAndSilentPairsHaveNoAudio() = runBlocking<Unit>(Dispatchers.IO) {
        val engine = VideoCompressor(context); val audio = AudioEngine(context)
        val silent = engine.importVideo(uri("silent.mp4")); val sound = engine.importVideo(uri("edit-first.mp4"))
        try {
            for (reverse in listOf(false, true)) for (target in VideoOutputFormat.entries) {
                val edit = VideoEdit(listOf(VideoClip(silent, VideoTrim(0, 1000)), VideoClip(sound, VideoTrim(0, 1000)))
                    .let { if (reverse) it.reversed() else it })
                val result = engine.compress(edit, VideoQuality.BALANCED, 640, target) {}
                try {
                    assertTrue(result.info.hasAudio); assertEquals(1, result.info.audioChannels)
                    val source = audio.importAudio(Uri.fromFile(result.file))
                    val pcm = audio.convert(source, AudioTarget.WAV, 128000) {}
                    try {
                        val data = pcm.file.readBytes()
                        fun peak(startMs: Int) = (startMs * 48 until (startMs + 200) * 48).maxOf { frame ->
                            val offset = 44 + frame * 2
                            abs(((data[offset].toInt() and 255) or (data[offset+1].toInt() shl 8)).toShort().toInt())
                        }
                        assertTrue("missing tone", peak(if (reverse) 200 else 1200) > 100)
                        assertTrue("silent range changed", peak(if (reverse) 1200 else 200) < 50)
                    } finally { source.file.delete(); pcm.file.delete() }
                } finally { result.file.delete(); result.info.poster?.recycle() }
            }
            val secondSilent = engine.importVideo(uri("silent.mp4"))
            try {
                val result = engine.compress(VideoEdit(listOf(VideoClip(silent, VideoTrim(0, 1000)), VideoClip(secondSilent, VideoTrim(0, 1000)))),
                    VideoQuality.BALANCED, 640, VideoOutputFormat.MP4_AVC) {}
                try { assertFalse(result.info.hasAudio); assertTrue(abs(result.info.durationMs - 2000) < 120) }
                finally { result.file.delete(); result.info.poster?.recycle() }
            } finally { secondSilent.file.delete(); secondSilent.info.poster?.recycle() }
        } finally { silent.file.delete(); sound.file.delete(); silent.info.poster?.recycle(); sound.info.poster?.recycle() }
    }
    @Test fun cancellationRemovesWorkingOutputsButKeepsBothSources() = runBlocking<Unit>(Dispatchers.IO) {
        val engine = VideoCompressor(context)
        val first = engine.importVideo(uri("cancel.mp4")); val second = engine.importVideo(uri("cancel.mp4"))
        try {
            val before = first.file.parentFile!!.listFiles()!!.toSet()
            val began = CompletableDeferred<Unit>()
            val job = launch { engine.compress(VideoEdit(listOf(VideoClip(first), VideoClip(second))), VideoQuality.HIGH, 1280,
                VideoOutputFormat.MP4_AVC) { began.complete(Unit) } }
            withTimeout(30000) { began.await() }; delay(150); job.cancelAndJoin()
            assertEquals(before, first.file.parentFile!!.listFiles()!!.toSet())
            assertTrue(first.file.exists() && second.file.exists())
        } finally { first.file.delete(); second.file.delete(); first.info.poster?.recycle(); second.info.poster?.recycle() }
    }
    @Test fun aggregateSizeGuardAndBothOriginalUrisAreProtected() = runBlocking<Unit>(Dispatchers.IO) {
        val engine = VideoCompressor(context)
        val first = engine.importVideo(uri("edit-first.mp4")); val second = engine.importVideo(uri("edit-second.mp4"))
        val edit = VideoEdit(listOf(VideoClip(first), VideoClip(second)))
        val result = engine.compress(edit, VideoQuality.BALANCED, 640, VideoOutputFormat.MP4_AVC) {}
        val large = result.copy(bytes = edit.sourceBytes)
        try {
            suspend fun rejected(block: suspend () -> Unit) { try { block(); fail("Expected rejection") } catch (_: IllegalArgumentException) {} }
            rejected { engine.export(edit, large, uri("export.mp4")) }
            for (source in listOf(first, second)) rejected { engine.export(edit, result, source.originalUri, true) }
            engine.export(edit, large, uri("export.mp4"), true)
            assertArrayEquals(result.file.readBytes(), context.contentResolver.openInputStream(uri("export.mp4"))!!.use { it.readBytes() })
        } finally { first.file.delete(); second.file.delete(); result.file.delete(); listOf(first.info,second.info,result.info).forEach { it.poster?.recycle() } }
    }
    private class Failing(private val storage: MediaSessionStorage): MediaSessionStorage {
        var fail = false
        override fun read() = storage.read()
        override fun write(json: String?) { if (fail) throw IOException("Synthetic disk error"); storage.write(json) }
    }
    @Test fun joinedWorkRestoresCompletedOutputAndAtomicRetirementSurvivesWriteFailure() = runBlocking<Unit> {
        val application = context.applicationContext as Application
        val storage = AtomicMediaSession(File(context.noBackupFilesDir, "video-compression")); storage.write(null)
        val failing = Failing(storage); val store = ViewModelStore()
        val models = mutableListOf<VideoCompressionViewModel>()
        fun own(model: VideoCompressionViewModel): VideoCompressionViewModel {
            ViewModelProvider(store, object: ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST") override fun <T: androidx.lifecycle.ViewModel> create(type: Class<T>): T = model as T
            })["model-${models.size}", VideoCompressionViewModel::class.java]; models += model; return model
        }
        lateinit var model: VideoCompressionViewModel
        try {
            main { ProcessingJobs.get(context).awaitReady(); ProcessingJobs.get(context).forget(ProcessingKind.VIDEO); model = own(VideoCompressionViewModel(application, failing)) }
            await { !model.state.value.busy }
            main { model.load(uri("edit-first.mp4")) }; await { !model.state.value.busy }
            main { model.addSource(uri("edit-second.mp4")) }; await { !model.state.value.busy }
            main { model.setTrim(1100, 2100); model.setClipTrim(model.state.value.videoClips.last().source.file, 1750, 2750); model.swapSources(); model.setOptions(maxEdge = 640, canvas = VideoCanvas.SQUARE, fit = VideoFit.CENTER_CROP, speed = 0.75f); assertTrue(model.awaitSession()) }
            await { model.state.value.outputSupport.isNotEmpty() }
            val clips = model.state.value.videoClips
            main { model.preview() }; await { !model.state.value.busy }
            assertNotNull(model.state.value.result)
            lateinit var restored: VideoCompressionViewModel
            main { restored = own(VideoCompressionViewModel(application, failing)) }
            await { !restored.state.value.busy && restored.state.value.result != null }
            assertEquals(clips.map { it.source.file }, restored.state.value.videoClips.map { it.source.file })
            assertEquals(clips.map { it.trim }, restored.state.value.videoClips.map { it.trim })
            assertEquals(VideoCanvas.SQUARE, restored.state.value.canvas)
            assertEquals(VideoFit.CENTER_CROP, restored.state.value.fit)
            assertEquals(0.75f, restored.state.value.speed)
            assertEquals(model.state.value.result!!.file, restored.state.value.result!!.file)
            failing.fail = true; val previous = storage.read(); val removed = restored.state.value.videoClips.last().source.file
            main { restored.removeSource(removed); assertFalse(restored.awaitSession()) }
            assertEquals(previous, storage.read()); assertTrue(removed.exists())
            failing.fail = false
            main { restored.retrySession(); assertTrue(restored.awaitSession()) }
            assertFalse(removed.exists()); assertEquals(1, MediaSessionCodec.video(storage.read()!!).clips.size)
            val originals = clips.map { it.source.originalUri.toString() }
            main { restored.reset(); restored.awaitSession(); originals.forEach { restored.export(Uri.parse(it), "stale-output", pickerOriginals = originals) } }
            assertTrue(context.contentResolver.openInputStream(uri("edit-first.mp4"))!!.use { it.read() } >= 0)
            assertTrue(context.contentResolver.openInputStream(uri("edit-second.mp4"))!!.use { it.read() } >= 0)
        } finally { failing.fail = false; main { models.lastOrNull()?.reset(); models.lastOrNull()?.awaitSession(); store.clear() } }
    }
}

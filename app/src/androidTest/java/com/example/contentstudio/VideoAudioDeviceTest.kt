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
import com.example.contentstudio.audio.AudioEngine
import com.example.contentstudio.audio.AudioTarget
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException

@androidx.annotation.OptIn(UnstableApi::class)
class VideoAudioDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun video(name: String) = Uri.parse("content://com.example.contentstudio.test.videos/$name")
    private fun audio(name: String) = Uri.parse("content://com.example.contentstudio.test.audio/$name")
    private suspend fun main(block: suspend () -> Unit) = withContext(Dispatchers.Main) { block() }
    private suspend fun await(block: () -> Boolean) = withTimeout(120000) { while (!block()) delay(50) }

    @Test fun sixMixesAndTwoAudioReplacementOutputsKeepTimingPitchLevelsAndOriginals() = runBlocking<Unit>(Dispatchers.IO) {
        val engine = VideoCompressor(context); val assets = VideoAudioAssets(context)
        val first = engine.importVideo(video("edit-first.mp4")); val second = engine.importVideo(video("edit-second.mp4"))
        val layer = assets.importAudio(audio("mix.wav"))
        val originals = listOf(first.file.readBytes(), second.file.readBytes(), assets.resolve(layer).readBytes())
        assertArrayEquals(originals[2], context.contentResolver.openInputStream(audio("mix.wav"))!!.use { it.readBytes() })
        val folder = File(context.getExternalFilesDir(null), "qa/video-audio").apply { mkdirs() }
        suspend fun export(edit: VideoEdit, format: VideoOutputFormat, name: String) {
            assertNull(engine.outputSupport(edit, VideoQuality.BALANCED, 640)[format])
            val result = engine.compress(edit, VideoQuality.BALANCED, 640, format) {}
            try {
                engine.validateEditOutput(edit, result.info, VideoQuality.BALANCED, 640, format)
                if (edit.audioLayer != null && edit.clips.none { it.source.info.hasAudio }) {
                    val audio = AudioEngine(context)
                    val sound = audio.importAudio(Uri.fromFile(result.file))
                    try {
                        val pcm = audio.convert(sound, AudioTarget.WAV, 128000) {}
                        try { assertTrue("Silent tail was dropped", kotlin.math.abs(pcm.info.durationMs - edit.durationMs) < 180) }
                        finally { pcm.file.delete() }
                    } finally { sound.file.delete() }
                }
                result.file.copyTo(File(folder, "$name.${format.extension}"), overwrite = true)
                for (uri in edit.clips.map { it.source.originalUri } + audio("mix.wav")) {
                    var denied = false
                    try { engine.export(edit, result, uri, true) } catch (_: IllegalArgumentException) { denied = true }
                    assertTrue("Overwrite allowed for selected original: $uri", denied)
                }
                engine.export(edit, result, video("export.mp4"), true)
                assertArrayEquals(result.file.readBytes(), context.contentResolver.openInputStream(video("export.mp4"))!!.use { it.readBytes() })
            } finally { result.file.delete(); result.info.poster?.recycle() }
        }
        try {
            for (speed in listOf(.5f, 2f)) for (format in VideoOutputFormat.entries) {
                val slow = speed == .5f
                val edit = VideoEdit(listOf(VideoClip(first, VideoTrim(1100, 2100)), VideoClip(second, VideoTrim(1700, 2700))),
                    VideoCanvas.PORTRAIT_9_16, VideoFit.CENTER_CROP, speed, textLayer = VideoTextLayer("Birlevo"),
                    audioLayer = layer.copy(sourceStartMs = 500, sourceEndMs = if (slow) 1500 else 900, startMs = if (slow) 1500 else 300))
                export(edit, format, "$speed-${format.name}")
            }
            export(VideoEdit(listOf(VideoClip(first, VideoTrim(0, 2000))), audioLayer = layer.copy(
                startMs = 500, sourceEndMs = 1000, originalVolume = 0f, volume = .75f)), VideoOutputFormat.MP4_AVC, "replace")
            val silent = engine.importVideo(video("silent.mp4"))
            try { export(VideoEdit(listOf(VideoClip(silent)), audioLayer = layer.copy(startMs = 500, sourceEndMs = 1000,
                originalVolume = 0f, volume = .75f)), VideoOutputFormat.WEBM_VP8, "silent") }
            finally { silent.file.delete(); silent.info.poster?.recycle() }
            assertArrayEquals(originals[0], first.file.readBytes()); assertArrayEquals(originals[1], second.file.readBytes())
            assertArrayEquals(originals[2], assets.resolve(layer).readBytes())
        } finally { first.file.delete(); second.file.delete(); assets.resolve(layer).delete()
            first.info.poster?.recycle(); second.info.poster?.recycle() }
    }
    @Test fun invalidTimingIsUnavailableAndCancellationPreservesOwnedInputs() = runBlocking<Unit>(Dispatchers.IO) {
        val assets = VideoAudioAssets(context); val engine = VideoCompressor(context)
        val source = engine.importVideo(video("cancel.mp4")); val layer = assets.importAudio(audio("mix.wav"))
        try {
            val invalid = VideoEdit(listOf(VideoClip(source)), audioLayer = layer.copy(startMs = source.info.durationMs))
            assertTrue(engine.outputSupport(invalid, VideoQuality.HIGH, 1280).values.all { it == VideoOutputIssue.AUDIO_TIMING })
            val surround = source.copy(info = source.info.copy(audioChannels = 6))
            val second = surround.copy(file = File(source.file.parentFile, "source-unopened.mp4"))
            val muted = VideoEdit(listOf(VideoClip(surround, VideoTrim(0, 1000)), VideoClip(second, VideoTrim(0, 1000))),
                audioLayer = layer.copy(originalVolume = 0f))
            assertEquals(1, muted.channels)
            assertNull(engine.outputSupport(muted, VideoQuality.BALANCED, 640)[VideoOutputFormat.MP4_AVC])
            assertEquals(VideoOutputIssue.AUDIO_ENCODER, engine.outputSupport(muted.copy(audioLayer = layer),
                VideoQuality.BALANCED, 640)[VideoOutputFormat.MP4_AVC])
            val before = source.file.parentFile!!.listFiles()!!.toSet(); val started = CompletableDeferred<Unit>()
            val job = launch { engine.compress(VideoEdit(listOf(VideoClip(source)), audioLayer = layer),
                VideoQuality.HIGH, 1280, VideoOutputFormat.MP4_AVC) { started.complete(Unit) } }
            withTimeout(30000) { started.await() }; delay(150); job.cancelAndJoin()
            assertEquals(before, source.file.parentFile!!.listFiles()!!.toSet())
        } finally { source.file.delete(); source.info.poster?.recycle(); assets.resolve(layer).delete() }
    }

    private class Failing(private val storage: MediaSessionStorage): MediaSessionStorage {
        var fail = false
        override fun read() = storage.read()
        override fun write(json: String?) { if (fail) throw IOException("Synthetic disk error"); storage.write(json) }
    }
    @Test fun freshRestoreAndFailedAtomicReplacementRemovalProtectOwnedSoundAndImage() = runBlocking<Unit> {
        val storage = AtomicMediaSession(File(context.noBackupFilesDir, "video-compression")); storage.write(null)
        val failing = Failing(storage); val store = ViewModelStore(); val models = mutableListOf<VideoCompressionViewModel>()
        fun own(): VideoCompressionViewModel {
            val model = VideoCompressionViewModel(context.applicationContext as Application, failing)
            ViewModelProvider(store, object: ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST") override fun <T: androidx.lifecycle.ViewModel> create(type: Class<T>): T = model as T
            })["model-${models.size}", VideoCompressionViewModel::class.java]; models += model; return model
        }
        lateinit var model: VideoCompressionViewModel
        val assets = VideoAudioAssets(context)
        try {
            main { ProcessingJobs.get(context).awaitReady(); ProcessingJobs.get(context).forget(ProcessingKind.VIDEO); model = own() }
            await { !model.state.value.busy }; main { model.load(video("edit-first.mp4")) }; await { !model.state.value.busy }
            main { model.loadAudio(audio("mix.wav")) }; await { !model.state.value.busy }
            val layer = model.state.value.audioLayer!!; val owned = assets.resolve(layer)
            main { model.loadImage(Uri.parse("content://com.example.contentstudio.test.fixtures/overlay-logo.png")) }; await { !model.state.value.busy }
            val imageLayer = model.state.value.imageLayer!!; val imageFile = VideoImageAssets(context).resolve(imageLayer)
            main { model.setOptions(maxEdge = 640); assertTrue(model.awaitSession()) }
            await { model.state.value.outputSupport.isNotEmpty() }; main { model.preview() }; await { !model.state.value.busy }
            val result = model.state.value.result!!
            // Invalid replacement leaves the current audio, completed output and saved draft intact.
            val before = storage.read()
            main { model.loadAudio(audio("invalid.mp3")) }; await { !model.state.value.busy }
            assertEquals(VideoMessage.AUDIO_LOAD_ERROR, model.state.value.message)
            assertEquals(layer, model.state.value.audioLayer); assertEquals(result.file, model.state.value.result!!.file)
            assertEquals(before, storage.read())
            // Aged owned sources remain protected from cache cleanup on a fresh model.
            owned.setLastModified(System.currentTimeMillis() - 48 * 3600000L)
            lateinit var restored: VideoCompressionViewModel
            main { restored = own() }; await { !restored.state.value.busy && restored.state.value.result != null }
            assertTrue(owned.exists()); assertEquals(layer, restored.state.value.audioLayer)
            assertEquals(result.file, restored.state.value.result!!.file); assertNotNull(restored.state.value.audioLayer)
            failing.fail = true
            main { restored.loadAudio(audio("tone.mp3")) }; await { !restored.state.value.busy }
            main { assertFalse(restored.awaitSession()) }
            val replacement = assets.resolve(restored.state.value.audioLayer!!)
            assertTrue(owned.exists()); assertEquals(before, storage.read()); assertTrue(replacement.exists())
            failing.fail = false; main { restored.retrySession(); assertTrue(restored.awaitSession()) }
            assertFalse(owned.exists()); assertTrue(replacement.exists())
            val replaced = storage.read(); failing.fail = true
            main { restored.setAudioLayer(null); assertFalse(restored.awaitSession()) }
            assertTrue(replacement.exists()); assertEquals(replaced, storage.read())
            failing.fail = false; main { restored.retrySession(); assertTrue(restored.awaitSession()) }
            assertTrue(imageFile.exists()); assertEquals(imageLayer, restored.state.value.imageLayer)
            assertFalse(replacement.exists()); assertNull(MediaSessionCodec.video(storage.read()!!).audioLayer)
        } finally { failing.fail = false; main { models.lastOrNull()?.reset(); models.lastOrNull()?.awaitSession(); store.clear() } }
    }
}

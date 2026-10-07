package com.example.contentstudio

import android.app.Activity
import android.app.Application
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.contentstudio.audio.*
import com.example.contentstudio.media.MediaOutputs
import com.example.contentstudio.media.VideoCompressionViewModel
import com.example.contentstudio.media.VideoQuality
import com.example.contentstudio.processing.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(AndroidJUnit4::class)
class AudioToolDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val engine get() = AudioEngine(context)
    private fun uri(name: String) = Uri.parse("content://com.example.contentstudio.test.audio/$name")
    private fun video(name: String) = Uri.parse("content://com.example.contentstudio.test.videos/$name")
    private fun audioNode(tag: String): SemanticsNodeInteraction {
        compose.onNodeWithTag("audio-list").performScrollToNode(hasTestTag(tag))
        return compose.onNodeWithTag(tag).performScrollTo()
    }

    @Test fun actualAudioContainersConvertToPlayableWavAndAacWithPreservedRateChannelsDuration() = runBlocking(Dispatchers.IO) {
        for (name in listOf("tone.wav", "tone.mp3", "tone.m4a", "tone.aac", "tone.ogg", "tone.opus", "tone.flac")) {
            android.util.Log.i("AudioQA", "Import $name")
            val source = engine.importAudio(uri(name))
            try { for (target in AudioTarget.entries) {
                android.util.Log.i("AudioQA", "$name to $target")
                val output = engine.convert(source, target, 128000) { }
                try {
                    assertFalse(output.info.fromVideo); assertEquals(48000, output.info.sampleRate); assertEquals(2, output.info.channels)
                    assertTrue("Duration $name: ${output.info.durationMs}", kotlin.math.abs(output.info.durationMs - 3000) < 300)
                    if (target == AudioTarget.FLAC) assertTrue(output.info.mime in setOf("audio/flac", "audio/raw"))
                    else assertEquals(target.codec, output.info.mime)
                    if (target == AudioTarget.WAV) {
                        val header = output.file.inputStream().use { it.readNBytes(44) }
                        assertEquals(output.bytes - 44, ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).getInt(40).toLong())
                        assertTrue(output.file.inputStream().use { it.skip(44); it.readBytes().any { byte -> byte != 0.toByte() } })
                    }
                    val shared = MediaOutputs(context).stage(output.file, target.mime, "tone.${target.extension}")
                    assertEquals(output.bytes, context.contentResolver.openFileDescriptor(shared.uri, "r")!!.use { it.statSize })
                } finally { output.file.delete() }
            } } finally { source.file.delete() }
        }
    }
    @Test fun videoExtractionIgnoresVideoAndRejectsSilentOrMultipleAudioInsteadOfDroppingTracks() = runBlocking(Dispatchers.IO) {
        val source = engine.importAudio(video("audio.mp4"))
        try {
            assertTrue(source.info.fromVideo)
            for (target in AudioTarget.entries) {
                val result = engine.convert(source, target, 128000) { }
                try { assertFalse(result.info.fromVideo); assertTrue(kotlin.math.abs(result.info.durationMs - 6000) < 300) }
                finally { result.file.delete() }
            }
        } finally { source.file.delete() }
        for (bad in listOf(video("silent.mp4"), video("tracks.mp4"), uri("invalid.mp3"))) {
            try { engine.importAudio(bad); fail("Invalid selection accepted") } catch (_: Exception) { }
        }
    }
    @Test fun largerCopiesNeedExactConsentAndOriginalBytesRemainUnchanged() = runBlocking(Dispatchers.IO) {
        val original = context.contentResolver.openInputStream(uri("tone.mp3"))!!.use { it.readBytes() }
        val source = engine.importAudio(uri("tone.mp3")); val result = engine.convert(source, AudioTarget.WAV, 128000) { }
        try {
            assertTrue(result.bytes > source.bytes)
            try { engine.export(source, result, uri("export.wav")); fail("Unapproved copy") } catch (_: IllegalArgumentException) { }
            try { MediaOutputs(context).stage(result.file, "audio/wav", "x.wav", source.bytes); fail("Unapproved share") } catch (_: IllegalArgumentException) { }
            engine.export(source, result, uri("export.wav"), true)
            assertArrayEquals(result.file.readBytes(), context.contentResolver.openInputStream(uri("export.wav"))!!.use { it.readBytes() })
            try { engine.export(source, result, source.originalUri, true); fail("Overwrite accepted") } catch (_: IllegalArgumentException) { }
            assertArrayEquals(original, context.contentResolver.openInputStream(uri("tone.mp3"))!!.use { it.readBytes() })
        } finally { source.file.delete(); result.file.delete(); context.contentResolver.delete(uri("export.wav"), null, null) }
    }
    @Test fun selectedAacBitrateChangesRealOutputAndCancelledDecodeRemovesItsPartialFile() = runBlocking(Dispatchers.IO) {
        val source = engine.importAudio(uri("tone.m4a"))
        try {
            val small = engine.convert(source, AudioTarget.M4A, 64000) { }
            val large = engine.convert(source, AudioTarget.M4A, 256000) { }
            try { assertTrue("AAC passthrough ignored bitrate: ${small.bytes} / ${large.bytes}", large.bytes > small.bytes * 2) }
            finally { small.file.delete(); large.file.delete() }
            val folder = File(context.noBackupFilesDir, "audio-conversion")
            val before = folder.listFiles()!!.map { it.name }.toSet()
            val task = launch { engine.convert(source, AudioTarget.WAV, 128000) { if ((it ?: 0) >= 5) cancel() } }
            task.join(); assertTrue(task.isCancelled)
            assertEquals(before, folder.listFiles()!!.map { it.name }.toSet())
        } finally { source.file.delete() }
    }
    @Test fun pickerOutputPlaybackRecreationAndDurableResultWorkThroughHomeEntry() {
        Intents.init()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        var model: AudioToolViewModel? = null
        try {
            compose.waitUntil(15000) { compose.onAllNodesWithTag("compact-home").fetchSemanticsNodes().isNotEmpty() || compose.onAllNodesWithTag("wide-home-tools").fetchSemanticsNodes().isNotEmpty() }
            val homeTag = if (compose.onAllNodesWithTag("compact-home").fetchSemanticsNodes().isNotEmpty()) "compact-home" else "wide-home-tools"
            compose.onNodeWithTag(homeTag).performScrollToNode(hasTestTag("home-audio"))
            compose.onNodeWithTag("home-audio").performScrollTo().performClick()
            scenario.onActivity { model = ViewModelProvider(it)[AudioToolViewModel::class.java] }
            compose.waitUntil(15000) { !model!!.state.value.busy }
            Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri("tone.mp3"))))
            compose.onNodeWithTag("choose-audio").performScrollTo().performClick()
            compose.waitUntil(20000) { !model!!.state.value.busy && model!!.state.value.source != null }
            audioNode("audio-create").assertIsEnabled().performSemanticsAction(SemanticsActions.OnClick) { it() }
            compose.waitUntil(60000) { !model!!.state.value.busy && (model!!.state.value.result != null || model!!.state.value.message != null) }
            assertNotNull("Audio service output failed", model!!.state.value.result)
            val result = model!!.state.value.result!!
            scenario.recreate(); assertEquals(result.file, model!!.state.value.result!!.file)
            audioNode("play-output-audio").performClick()
            compose.onNodeWithTag("audio-player").assertExists()
            var player: Player? = null
            onView(isAssignableFrom(PlayerView::class.java)).inRoot(isDialog()).check { view, error ->
                if (error != null) throw error
                player = (view as PlayerView).player
            }
            compose.waitUntil(15000) {
                var ready = false
                instrumentation.runOnMainSync { ready = player!!.playbackState == Player.STATE_READY }
                ready
            }
            compose.onNodeWithTag("audio-close-player").performClick()
            audioNode("audio-result").assertIsDisplayed()
            // Native dialog dismissal animates on wall time, outside Compose's test clock.
            instrumentation.waitForIdleSync()
            Thread.sleep(300)
            val screenshot = instrumentation.uiAutomation.takeScreenshot()
            try {
                File(context.getExternalFilesDir("qa"), "audio-screen.png").outputStream().use { screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            } finally { screenshot.recycle() }
            // A fresh owner restores source/settings plus completed output from the atomic journals.
            val store = ViewModelStore(); var restored: AudioToolViewModel? = null
            instrumentation.runOnMainSync { restored = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(context.applicationContext as Application))[AudioToolViewModel::class.java] }
            try {
                compose.waitUntil(20000) { restored!!.state.value.result != null && !restored!!.state.value.busy }
                assertEquals(result.file, restored!!.state.value.result!!.file)
            } finally { instrumentation.runOnMainSync { store.clear() } }
            compose.onNodeWithTag("audio-back").performClick()
            compose.onNodeWithTag(if (homeTag == "compact-home") "compact-home" else "wide-home").assertExists()
        } finally {
            model?.let { value -> instrumentation.runOnMainSync { value.reset() }; runBlocking { withContext(Dispatchers.Main) { value.awaitSession() } } }
            scenario.close(); Intents.release()
        }
    }

    @Test fun realAudioDecodeContinuesAfterActivityClosesAndScreenTurnsOff() = runBlocking<Unit> {
        suspend fun main(block: suspend () -> Unit) = withContext(Dispatchers.Main) { block() }
        suspend fun waitFor(block: () -> Boolean) { withTimeout(120000) { while (!block()) delay(25) } }
        fun shell(command: String) = instrumentation.uiAutomation.executeShellCommand(command).use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText()
        }
        var activity: ActivityScenario<MainActivity>? = ActivityScenario.launch(MainActivity::class.java)
        lateinit var first: AudioToolViewModel
        var restored: AudioToolViewModel? = null
        val jobs = ProcessingJobs.get(context)
        try {
            activity!!.onActivity { first = ViewModelProvider(it)[AudioToolViewModel::class.java] }
            waitFor { !first.state.value.busy }
            main { first.load(uri("long.m4a")) }; waitFor { !first.state.value.busy }
            assertNotNull(first.state.value.source)
            main { first.setOptions(target = AudioTarget.WAV); first.preview() }
            waitFor { jobs.records.value[ProcessingKind.AUDIO]?.status == ProcessingStatus.RUNNING }
            activity!!.close(); activity = null
            shell("input keyevent KEYCODE_SLEEP")
            assertFalse(context.getSystemService(android.os.PowerManager::class.java).isInteractive)
            waitFor { jobs.records.value[ProcessingKind.AUDIO]?.status?.active == false }
            assertEquals(ProcessingStatus.SUCCEEDED, jobs.records.value[ProcessingKind.AUDIO]!!.status)
            shell("input keyevent KEYCODE_WAKEUP"); shell("wm dismiss-keyguard")
            activity = ActivityScenario.launch(MainActivity::class.java)
            activity!!.onActivity { restored = ViewModelProvider(it)[AudioToolViewModel::class.java] }
            waitFor { restored!!.state.value.result != null && !restored!!.state.value.busy }
            assertEquals(AudioTarget.WAV, restored!!.state.value.target)
            assertTrue(restored!!.state.value.result!!.bytes > 50_000_000)
        } finally {
            shell("input keyevent KEYCODE_WAKEUP"); shell("wm dismiss-keyguard")
            restored?.let { value -> main { value.reset(); value.awaitSession() } }
            if (restored == null) main { jobs.forget(ProcessingKind.AUDIO) }
            activity?.close()
        }
    }

    @Test fun editedVideoTransfersItsActualTrimmedBytesIntoAudioToolWithoutReencodingTheHandoff() {
        Intents.init()
        val activity = ActivityScenario.launch(MainActivity::class.java)
        var videoModel: VideoCompressionViewModel? = null
        var audioModel: AudioToolViewModel? = null
        try {
            compose.waitUntil(15000) { compose.onAllNodesWithText("Compress video").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Compress video").performScrollTo().performClick()
            activity.onActivity { videoModel = ViewModelProvider(it)[VideoCompressionViewModel::class.java] }
            compose.waitUntil(15000) { !videoModel!!.state.value.busy }
            Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(video("audio.mp4"))))
            compose.onNodeWithTag("choose-compression-video").performScrollTo().performClick()
            compose.waitUntil(20000) { videoModel!!.state.value.source != null && !videoModel!!.state.value.busy }
            instrumentation.runOnMainSync { videoModel!!.setOptions(VideoQuality.SMALL, 640); videoModel!!.setTrim(2000, 4000) }
            compose.onNodeWithTag("process-video").performScrollTo().performClick()
            compose.waitUntil(120000) { videoModel!!.state.value.result != null && !videoModel!!.state.value.busy }
            val output = videoModel!!.state.value.result!!.file.readBytes()
            compose.onNodeWithTag("add-output-to-audio").performScrollTo().performClick()
            compose.waitUntil(20000) { compose.onAllNodesWithTag("audio-list").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("audio-list").performScrollToNode(hasTestTag("audio-source"))
            activity.onActivity { audioModel = ViewModelProvider(it)[AudioToolViewModel::class.java] }
            val input = audioModel!!.state.value.source!!
            assertArrayEquals(output, input.file.readBytes())
            assertTrue(input.info.fromVideo); assertTrue(kotlin.math.abs(input.info.durationMs - 2000) < 300)
            instrumentation.runOnMainSync { videoModel!!.reset() }
            runBlocking { withContext(Dispatchers.Main) { videoModel!!.awaitSession() } }
            assertArrayEquals(output, input.file.readBytes())
            audioNode("audio-target-WAV").performClick()
            audioNode("audio-create").performClick()
            compose.waitUntil(30000) { audioModel!!.state.value.result != null && !audioModel!!.state.value.busy }
            assertTrue(kotlin.math.abs(audioModel!!.state.value.result!!.info.durationMs - 2000) < 300)
        } finally {
            instrumentation.runOnMainSync { videoModel?.reset(); audioModel?.reset() }
            runBlocking { withContext(Dispatchers.Main) { videoModel?.awaitSession(); audioModel?.awaitSession() } }
            activity.close(); Intents.release()
        }
    }
}

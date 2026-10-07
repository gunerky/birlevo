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
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.*
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.platform.app.InstrumentationRegistry
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.example.contentstudio.audio.*
import com.example.contentstudio.media.*
import kotlinx.coroutines.*
import org.hamcrest.Matchers.allOf
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.security.MessageDigest

class AudioTargetsDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val engine get() = AudioEngine(context)
    private fun uri(name: String) = Uri.parse("content://com.example.contentstudio.test.audio/$name")
    private fun bytes(uri: Uri) = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
    private fun node(tag: String): SemanticsNodeInteraction {
        compose.onNodeWithTag("audio-list").performScrollToNode(hasTestTag(tag))
        return compose.onNodeWithTag(tag).performScrollTo()
    }
    @Test fun flacRoundTripPreservesActualPcmAndContainsExactDurationAndDigest() = runBlocking<Unit>(Dispatchers.IO) {
        for (name in listOf("tone.wav", "mono44100.wav", "tone.mp3", "tone.flac")) {
            val source = engine.importAudio(uri(name))
            val expected = engine.convert(source, AudioTarget.WAV, 128_000) { }
            var flac: AudioResult? = null
            var decoded: AudioResult? = null
            try {
                flac = engine.convert(source, AudioTarget.FLAC, 128_000) { }
                val reimported = AudioSource(flac.file, Uri.fromFile(flac.file), name, flac.bytes, flac.info)
                decoded = engine.convert(reimported, AudioTarget.WAV, 128_000) { }
                assertArrayEquals("PCM mismatch for $name", expected.file.readBytes(), decoded.file.readBytes())
                val pcm = expected.file.readBytes().copyOfRange(44, expected.bytes.toInt())
                val header = flac.file.inputStream().use { it.readNBytes(42) }
                assertEquals("fLaC", String(header, 0, 4))
                assertArrayEquals(MessageDigest.getInstance("MD5").digest(pcm), header.copyOfRange(26, 42))
                assertEquals(source.info.sampleRate, flac.info.sampleRate)
                assertEquals(source.info.channels, flac.info.channels)
                val report = File(context.getExternalFilesDir("qa"), "audio-targets").apply { mkdirs() }
                flac.file.copyTo(File(report, "$name.flac"), true)
            } finally { expected.file.delete(); decoded?.file?.delete(); flac?.file?.delete(); source.file.delete() }
        }
    }
    @Test fun opusChangesRealBitrateEvenForOpusInputAndExplicitlyResamplesOnlyOgg() = runBlocking<Unit>(Dispatchers.IO) {
        for (name in listOf("tone.opus", "mono44100.wav")) {
            val source = engine.importAudio(uri(name))
            var small: AudioResult? = null; var high: AudioResult? = null
            try {
                small = engine.convert(source, AudioTarget.OGG, 64_000) { }
                high = engine.convert(source, AudioTarget.OGG, 256_000) { }
                assertEquals("audio/opus", high.info.mime); assertEquals(48_000, high.info.sampleRate)
                assertEquals(source.info.channels, high.info.channels)
                assertTrue("Opus bitrate ignored for $name: ${small.bytes}/${high.bytes}", high.bytes > small.bytes)
                assertEquals("OggS", high.file.inputStream().use { String(it.readNBytes(4)) })
                val report = File(context.getExternalFilesDir("qa"), "audio-targets").apply { mkdirs() }
                high.file.copyTo(File(report, "$name.ogg"), true)
            } finally { small?.file?.delete(); high?.file?.delete(); source.file.delete() }
        }
    }
    @Test fun absentEncoderHasExplicitReasonWithoutDisablingWavOrSilentlyChangingRate() = runBlocking<Unit>(Dispatchers.IO) {
        val source = engine.importAudio(uri("mono44100.wav"))
        try {
            val missing = AudioOutputSupport { emptyList() }
            assertNull(missing.issue(source.info, AudioTarget.WAV, 128_000))
            for (target in listOf(AudioTarget.M4A, AudioTarget.OGG, AudioTarget.FLAC))
                assertEquals(AudioOutputIssue.ENCODER, missing.issue(source.info, target, 128_000))
            val incompatible = source.copy(info = source.info.copy(sampleRate = 192_000))
            assertEquals(AudioOutputIssue.ENCODER, AudioOutputSupport().issue(incompatible.info, AudioTarget.M4A, 128_000))
            val before = source.file.parentFile!!.listFiles()!!.map { it.name }.toSet()
            try { engine.convert(incompatible, AudioTarget.M4A, 128_000) {}; fail("Silent rate fallback") }
            catch (_: IllegalArgumentException) { }
            assertEquals(before, source.file.parentFile!!.listFiles()!!.map { it.name }.toSet())
            val falseFlac = File.createTempFile("output-", ".flac", source.file.parentFile)
            try {
                source.file.copyTo(falseFlac, true)
                assertThrows(IllegalArgumentException::class.java) { engine.restoreOutput(falseFlac, falseFlac.length(), 0) }
            } finally { falseFlac.delete() }
        } finally { source.file.delete() }
    }
    @Test fun cancelBothNewEncodersDeletesPartialAndWorkingFilesAndKeepsOriginal() = runBlocking<Unit>(Dispatchers.IO) {
        val source = engine.importAudio(uri("long.m4a"))
        val original = source.file.readBytes()
        try {
            for (target in listOf(AudioTarget.FLAC, AudioTarget.OGG)) {
                val before = source.file.parentFile!!.listFiles()!!.map { it.name }.toSet()
                val task = launch { engine.convert(source, target, 128_000) { if (it != null) cancel() } }
                task.join(); assertTrue("No cancellation for $target", task.isCancelled)
                assertEquals(before, source.file.parentFile!!.listFiles()!!.map { it.name }.toSet())
                assertArrayEquals(original, source.file.readBytes())
            }
        } finally { source.file.delete() }
    }
    @Test fun newFormatsStillNeedConsentForNonSmallerSaveOpenAndShare() = runBlocking<Unit>(Dispatchers.IO) {
        val source = engine.importAudio(uri("tone.mp3"))
        val original = bytes(source.originalUri)
        try {
            for (target in listOf(AudioTarget.FLAC, AudioTarget.OGG)) {
                val result = engine.convert(source, target, 256_000) { }
                try {
                    val equal = source.copy(bytes = result.bytes)
                    assertThrows(IllegalArgumentException::class.java) { runBlocking { engine.export(equal, result, uri("export.${target.extension}")) } }
                    val outputs = MediaOutputs(context)
                    assertThrows(IllegalArgumentException::class.java) { runBlocking { outputs.stage(result.file, target.mime, "out.${target.extension}", result.bytes) } }
                    val shared = outputs.stage(result.file, target.mime, "out.${target.extension}", result.bytes, true)
                    assertEquals(target.mime, shared.shareIntent().type); assertEquals(target.mime, shared.viewIntent().type)
                    assertArrayEquals(result.file.readBytes(), bytes(shared.uri))
                    engine.export(equal, result, uri("export.${target.extension}"), true)
                    assertArrayEquals(result.file.readBytes(), bytes(uri("export.${target.extension}")))
                    assertArrayEquals(original, bytes(source.originalUri))
                } finally { result.file.delete(); context.contentResolver.delete(uri("export.${target.extension}"), null, null) }
            }
        } finally { source.file.delete() }
    }
    @Test fun oggUiPlaybackSaveSettingsInvalidateConsentAndRestoreDurableResult() = ui(AudioTarget.OGG)
    @Test fun flacUiPlaybackSaveSettingsInvalidateConsentAndRestoreDurableResult() = ui(AudioTarget.FLAC)

    private fun ui(target: AudioTarget) {
        Intents.init()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        var model: AudioToolViewModel? = null
        try {
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("compact-home").fetchSemanticsNodes().isNotEmpty() || compose.onAllNodesWithTag("wide-home-tools").fetchSemanticsNodes().isNotEmpty() }
            val homeTag = if (compose.onAllNodesWithTag("compact-home").fetchSemanticsNodes().isNotEmpty()) "compact-home" else "wide-home-tools"
            compose.onNodeWithTag(homeTag).performScrollToNode(hasTestTag("home-audio"))
            compose.onNodeWithTag("home-audio").performScrollTo().performClick()
            scenario.onActivity { model = ViewModelProvider(it)[AudioToolViewModel::class.java] }
            compose.waitUntil(15_000) { !model!!.state.value.busy }
            Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri("tone.mp3"))))
            node("choose-audio").performClick()
            compose.waitUntil(20_000) { !model!!.state.value.busy && model!!.state.value.source != null }
            node("audio-target-${target.name}").performClick()
            compose.waitUntil(20_000) { !model!!.state.value.busy }
            node("audio-target-${target.name}").assertIsSelected()
            node("audio-create").assertIsEnabled()
            screenshot("audio-options-${target.name}")
            node("audio-create").assertIsEnabled().performSemanticsAction(SemanticsActions.OnClick) { it() }
            compose.waitUntil(60_000) { !model!!.state.value.busy && (model!!.state.value.result != null || model!!.state.value.message != null) }
            val result = checkNotNull(model!!.state.value.result) { "Output failed: ${model!!.state.value.message}" }
            if (target == AudioTarget.FLAC) assertTrue(result.info.mime in setOf("audio/flac", "audio/raw"))
            else assertEquals(target.codec, result.info.mime)
            scenario.recreate()
            assertEquals(result.file, model!!.state.value.result!!.file)
            node("play-output-audio").performClick()
            var player: Player? = null
            onView(isAssignableFrom(PlayerView::class.java)).inRoot(isDialog()).check { view, error ->
                if (error != null) throw error; player = (view as PlayerView).player
            }
            compose.waitUntil(15_000) { var ready = false; instrumentation.runOnMainSync { ready = player!!.playbackState == Player.STATE_READY && player!!.playerError == null }; ready }
            instrumentation.runOnMainSync { player!!.play() }
            compose.waitUntil(15_000) { var ended = false; instrumentation.runOnMainSync { ended = player!!.playbackState == Player.STATE_ENDED }; ended }
            compose.onNodeWithTag("audio-close-player").performClick()
            Intents.intending(hasAction(Intent.ACTION_CREATE_DOCUMENT)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri("export.${target.extension}"))))
            if (compressionBenefit(model!!.state.value.source!!.bytes, result.bytes) == CompressionBenefit.SMALLER) node("audio-save").performClick()
            else { node("audio-save-anyway").performClick(); compose.onNodeWithTag("audio-save-confirm").performClick() }
            compose.waitUntil(15_000) { model!!.state.value.message == AudioMessage.EXPORTED }
            Intents.intended(allOf(hasAction(Intent.ACTION_CREATE_DOCUMENT), hasType(target.mime), hasExtra(Intent.EXTRA_TITLE, "tone.${target.extension}")))
            assertArrayEquals(result.file.readBytes(), bytes(uri("export.${target.extension}")))
            screenshot("audio-result-${target.name}")
            val store = ViewModelStore()
            var restored: AudioToolViewModel? = null
            instrumentation.runOnMainSync { restored = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(context.applicationContext as Application))[AudioToolViewModel::class.java] }
            try {
                compose.waitUntil(20_000) { restored!!.state.value.result != null && !restored!!.state.value.busy }
                assertEquals(target, restored!!.state.value.target); assertEquals(result.file, restored!!.state.value.result!!.file)
            } finally { instrumentation.runOnMainSync { store.clear() } }
            instrumentation.runOnMainSync { model!!.confirmNonSmallerCopy(result.file); model!!.setOptions(target = AudioTarget.WAV) }
            compose.waitUntil(15_000) { !model!!.state.value.busy }
            assertNull(model!!.state.value.result)
            assertFalse(model!!.confirmNonSmallerCopy(result.file))
        } finally {
            model?.let { value -> instrumentation.runOnMainSync { value.reset() }; runBlocking { withContext(Dispatchers.Main) { value.awaitSession() } } }
            scenario.close(); Intents.release()
            context.contentResolver.delete(uri("export.${target.extension}"), null, null)
        }
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        // Native surfaces and dialog dismissal render outside Compose's virtual clock.
        instrumentation.waitForIdleSync()
        Thread.sleep(350)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try { File(context.getExternalFilesDir("qa"), "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }
}

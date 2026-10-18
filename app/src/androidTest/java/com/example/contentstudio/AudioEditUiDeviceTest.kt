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
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import androidx.test.platform.app.InstrumentationRegistry
import com.example.contentstudio.audio.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class AudioEditUiDeviceTest {
    @get:Rule(order = 0) val compose = createEmptyComposeRule()
    @get:Rule(order = 1) val residentAdvertising = ResidentAdvertisingRule(compose)
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun uri(name: String) = Uri.parse("content://com.example.contentstudio.test.audio/$name")
    private fun node(tag: String): SemanticsNodeInteraction = compose.workspaceNode(tag).performScrollTo()
    private fun shot(name: String) {
        compose.waitForIdle(); instrumentation.waitForIdleSync()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try { File(context.getExternalFilesDir("qa"), "audio-edit-$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }
    @Test fun rangeOrderSaveAndDurableMultiSourceRestoreInvalidateOldResultConsent() {
        Intents.init()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        var model: AudioToolViewModel? = null
        try {
            compose.waitUntil(15000) { compose.onAllNodesWithTag("compact-home").fetchSemanticsNodes().isNotEmpty() || compose.onAllNodesWithTag("wide-home-tools").fetchSemanticsNodes().isNotEmpty() }
            val home = if (compose.onAllNodesWithTag("compact-home").fetchSemanticsNodes().isNotEmpty()) "compact-home" else "wide-home-tools"
            compose.workspaceNode(home).performScrollToNode(hasTestTag("home-audio")); compose.workspaceNode("home-audio").performClick()
            scenario.onActivity { model = ViewModelProvider(it)[AudioToolViewModel::class.java] }
            compose.waitUntil(15000) { !model!!.state.value.busy }
            instrumentation.runOnMainSync { model!!.reset() }
            compose.waitUntil(15000) { !model!!.state.value.busy }
            Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri("tone.mp3"))))
            node("choose-audio").performClick()
            compose.waitUntil(20000) { !model!!.state.value.busy && model!!.state.value.source != null }
            val first = model!!.state.value.source!!
            instrumentation.runOnMainSync { model!!.addSource(uri("invalid.mp3")) }
            compose.waitUntil(15000) { !model!!.state.value.busy }
            assertEquals(first, model!!.state.value.source); assertEquals(1, model!!.state.value.audioClips.size)
            Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri("mono44100.wav"))))
            node("audio-add").performClick()
            compose.waitUntil(20000) { !model!!.state.value.busy && model!!.state.value.audioClips.size == 2 }
            val second = model!!.state.value.audioClips[1].source
            node("audio-trim-0").performClick()
            compose.workspaceNode("audio-trim-start").performTextReplacement("1.5")
            compose.workspaceNode("audio-trim-end").performTextReplacement("0.5")
            compose.workspaceNode("audio-trim-apply").assertIsNotEnabled()
            compose.workspaceNode("audio-trim-start").performTextReplacement("0.5")
            compose.workspaceNode("audio-trim-end").performTextReplacement("1.5")
            shot("range")
            compose.workspaceNode("audio-trim-apply").assertIsEnabled().performClick()
            assertEquals(AudioRange(500, 1500), model!!.state.value.audioClips.first().range)
            node("play-source-audio").performClick()
            var player: Player? = null
            onView(isAssignableFrom(PlayerView::class.java)).inRoot(isDialog()).check { view, error ->
                if (error != null) throw error; player = (view as PlayerView).player
            }
            compose.waitUntil(15000) { var ready = false; instrumentation.runOnMainSync { ready = player!!.playbackState == Player.STATE_READY }; ready }
            instrumentation.runOnMainSync { assertEquals(1000L, player!!.duration); player!!.play() }
            compose.waitUntil(15000) { var ended = false; instrumentation.runOnMainSync { ended = player!!.playbackState == Player.STATE_ENDED }; ended }
            compose.workspaceNode("audio-close-player").performClick()
            node("audio-swap").performClick(); assertEquals(second, model!!.state.value.source)
            node("audio-source").assertTextContains("mono44100.wav", substring = true)
            shot("sources")
            node("audio-target-WAV").performClick()
            node("audio-create").performSemanticsAction(SemanticsActions.OnClick) { it() }
            compose.waitUntil(60000) { !model!!.state.value.busy && (model!!.state.value.result != null || model!!.state.value.message != null) }
            val result = checkNotNull(model!!.state.value.result) { "Failed: ${model!!.state.value.message}" }
            assertEquals(4000L, result.info.durationMs)
            val snapshot = model!!.state.value.audioClips
            scenario.recreate(); assertEquals(snapshot, model!!.state.value.audioClips)
            val store = ViewModelStore()
            var restored: AudioToolViewModel? = null
            instrumentation.runOnMainSync { restored = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(context.applicationContext as Application))[AudioToolViewModel::class.java] }
            try {
                compose.waitUntil(20000) { !restored!!.state.value.busy && restored!!.state.value.result != null }
                assertEquals(snapshot, restored!!.state.value.audioClips); assertEquals(result.file, restored!!.state.value.result!!.file)
                assertEquals(48000, restored!!.state.value.rate); assertEquals(2, restored!!.state.value.channels)
            } finally { instrumentation.runOnMainSync { store.clear() } }
            Intents.intending(hasAction(Intent.ACTION_CREATE_DOCUMENT)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri("export.wav"))))
            node("audio-save-anyway").performClick(); compose.workspaceNode("audio-save-confirm").performClick()
            compose.waitUntil(20000) { model!!.state.value.message == AudioMessage.EXPORTED }
            assertArrayEquals(result.file.readBytes(), context.contentResolver.openInputStream(uri("export.wav"))!!.use { it.readBytes() })
            node("audio-result"); shot("result")
            val originals = snapshot.map { it.source.originalUri.toString() }
            instrumentation.runOnMainSync { model!!.confirmNonSmallerCopy(result.file) }
            node("audio-remove-0").performClick()
            assertEquals(1, model!!.state.value.audioClips.size); assertNull(model!!.state.value.result)
            assertFalse(model!!.confirmNonSmallerCopy(result.file))
            runBlocking { withContext(Dispatchers.Main) { assertTrue(model!!.awaitSession()) } }
            assertFalse(second.file.exists()); assertTrue(first.file.exists())
            // A stale picker returning an old, removed original must never truncate/delete it.
            val original = context.contentResolver.openInputStream(second.originalUri)!!.use { it.readBytes() }
            instrumentation.runOnMainSync { model!!.export(second.originalUri, result.file.name, pickerOriginals = originals) }
            assertEquals(AudioMessage.EXPORT_ERROR, model!!.state.value.message)
            assertArrayEquals(original, context.contentResolver.openInputStream(second.originalUri)!!.use { it.readBytes() })
        } finally {
            model?.let { value -> instrumentation.runOnMainSync { value.reset() }; runBlocking { withContext(Dispatchers.Main) { value.awaitSession() } } }
            scenario.close(); Intents.release(); context.contentResolver.delete(uri("export.wav"), null, null)
        }
    }
}

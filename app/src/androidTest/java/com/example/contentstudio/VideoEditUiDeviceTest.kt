package com.example.contentstudio

import android.app.Instrumentation
import android.app.LocaleManager
import android.content.Intent
import android.net.Uri
import android.os.LocaleList
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.media3.ui.PlayerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.platform.app.InstrumentationRegistry
import com.example.contentstudio.media.*
import com.example.contentstudio.processing.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import java.io.File

class VideoEditUiDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private var scenario: ActivityScenario<MainActivity>? = null
    private var model: VideoCompressionViewModel? = null
    private fun uri(name: String) = Uri.parse("content://com.example.contentstudio.test.videos/$name")
    @Before fun before() = runBlocking<Unit> {
        context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags("en-US")
        withContext(Dispatchers.Main) { ProcessingJobs.get(context).awaitReady(); ProcessingJobs.get(context).forget(ProcessingKind.VIDEO) }
        AtomicMediaSession(File(context.noBackupFilesDir,"video-compression")).write(null)
        Intents.init()
    }
    @After fun after() = runBlocking<Unit> {
        withContext(Dispatchers.Main) { model?.reset(); model?.awaitSession() }
        scenario?.close(); Intents.release()
    }
    private fun choose(tag: String, name: String) {
        val previous = model!!.state.value.videoClips.map { it.source.file }.toSet()
        Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(Instrumentation.ActivityResult(
            android.app.Activity.RESULT_OK, Intent().setData(uri(name))))
        compose.onNodeWithTag(tag).performScrollTo().performClick()
        compose.waitUntil(30000) { val state = model!!.state.value
            !state.busy && if (name == "invalid.mp4") state.message == VideoMessage.LOAD_ERROR
                else state.videoClips.any { it.source.name == name && it.source.file !in previous }
        }
    }
    private fun shot(name: String) {
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try { val folder = File(context.getExternalFilesDir(null), "qa").apply { mkdirs() }
            File(folder,"video-edit-$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        } finally { bitmap.recycle() }
    }
    @Test fun bothClipControlsPlaybackOrderRemovalAndResultStayUsable() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(15000) { compose.onAllNodesWithTag("compact-home").fetchSemanticsNodes().isNotEmpty() ||
            compose.onAllNodesWithTag("wide-home-tools").fetchSemanticsNodes().isNotEmpty() }
        val home = if (compose.onAllNodesWithTag("wide-home-tools").fetchSemanticsNodes().isNotEmpty())
            compose.onNodeWithTag("wide-home-tools") else compose.onNodeWithTag("compact-home")
        home.performScrollToNode(hasText("Edit video"))
        compose.onNodeWithText("Edit video").performScrollTo().performClick()
        scenario!!.onActivity { model = ViewModelProvider(it)[VideoCompressionViewModel::class.java] }
        compose.waitUntil(15000) { model?.state?.value?.busy == false }
        // The main navigation control must clear the system status bar.
        var inset = 0
        scenario!!.onActivity { activity -> inset = activity.window.decorView.rootWindowInsets.getInsets(android.view.WindowInsets.Type.statusBars()).top }
        val back = compose.onNodeWithTag("video-compression-back").fetchSemanticsNode().boundsInWindow
        assertTrue("Back overlaps status bar", back.top >= inset)
        choose("choose-compression-video", "edit-first.mp4")
        choose("video-add-source", "invalid.mp4")
        assertEquals(1, model!!.state.value.videoClips.size)
        choose("video-add-source", "edit-second.mp4")
        assertEquals(2, model!!.state.value.videoClips.size)
        compose.onNodeWithTag("video-trim-start").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(1100f) }
        compose.onNodeWithTag("video-trim-end").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(2100f) }
        compose.onNodeWithTag("video-trim-start-second").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(1700f) }
        compose.onNodeWithTag("video-trim-end-second").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(2700f) }
        assertEquals(2000L, model!!.state.value.durationMs)
        compose.onNodeWithTag("preview-video-trim-second").performScrollTo().performClick()
        onView(isAssignableFrom(PlayerView::class.java)).inRoot(isDialog()).check { view,_ ->
            val player = (view as PlayerView).player!!
            assertEquals(1, player.mediaItemCount)
            assertEquals(1700L, player.currentMediaItem!!.clippingConfiguration.startPositionMs)
            assertEquals(2700L, player.currentMediaItem!!.clippingConfiguration.endPositionMs)
        }
        compose.onNodeWithTag("close-video-player").assertIsDisplayed(); shot("player")
        compose.onNodeWithTag("close-video-player").performClick()
        compose.onNodeWithTag("video-swap").performScrollTo().performClick()
        assertEquals("edit-second.mp4", model!!.state.value.source!!.name)
        compose.onNodeWithTag("video-remove-1").performScrollTo().performClick()
        assertEquals(1, model!!.state.value.videoClips.size)
        choose("video-add-source", "edit-first.mp4")
        scenario!!.onActivity { model!!.setClipTrim(model!!.state.value.videoClips.last().source.file,1100,2100); model!!.setOptions(maxEdge = 640) }
        compose.waitUntil(30000) { model!!.state.value.outputSupport.isNotEmpty() }
        compose.onNodeWithTag("video-total-duration").performScrollTo().assertIsDisplayed(); shot("sources")
        compose.onNodeWithTag("video-canvas-PORTRAIT_9_16").performScrollTo().performClick()
        compose.onNodeWithTag("video-fit-CENTER_CROP").performScrollTo().performClick()
        compose.onNodeWithTag("video-speed-0.5").performScrollTo().performClick()
        assertEquals(VideoCanvas.PORTRAIT_9_16, model!!.state.value.canvas)
        assertEquals(VideoFit.CENTER_CROP, model!!.state.value.fit)
        assertEquals(4000L, model!!.state.value.outputDurationMs)
        compose.onNodeWithTag("video-output-duration").performScrollTo().assertTextContains("0:04.0", substring = true)
        compose.waitUntil(30000) { model!!.state.value.outputSupport.isNotEmpty() }
        compose.onNodeWithTag("process-video").performScrollTo().assertIsEnabled().performClick()
        compose.waitUntil(120000) { !model!!.state.value.busy }
        assertNotNull(model!!.state.value.result)
        compose.onNodeWithTag("video-output-canvas").performScrollTo().assertIsDisplayed(); shot("options")
        compose.onNodeWithTag("play-output-video").performScrollTo().performClick()
        compose.onNodeWithTag("close-video-player").assertIsDisplayed(); shot("result")
        compose.onNodeWithTag("close-video-player").performClick()
        runBlocking { withContext(Dispatchers.Main) { assertTrue(model!!.awaitSession()) } }
        scenario!!.recreate()
        compose.waitUntil(30000) { compose.onAllNodesWithTag("process-video").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(2, model!!.state.value.videoClips.size)
        compose.onNodeWithTag("video-compression-back").assertIsDisplayed()
    }
}

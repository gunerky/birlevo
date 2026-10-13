package com.example.contentstudio

import android.app.Instrumentation
import android.app.LocaleManager
import android.content.Intent
import android.net.Uri
import android.os.LocaleList
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
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

class VideoTextUiDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private var scenario: ActivityScenario<MainActivity>? = null
    private var model: VideoCompressionViewModel? = null
    private val extraStore = ViewModelStore()
    @Before fun setup() = runBlocking<Unit> {
        context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags("en-US")
        withContext(Dispatchers.Main) { ProcessingJobs.get(context).awaitReady(); ProcessingJobs.get(context).forget(ProcessingKind.VIDEO) }
        AtomicMediaSession(File(context.noBackupFilesDir, "video-compression")).write(null)
        Intents.init()
    }
    @After fun cleanup() = runBlocking<Unit> {
        withContext(Dispatchers.Main) { extraStore.clear(); model?.reset(); model?.awaitSession() }
        scenario?.close(); Intents.release()
    }
    private fun shot(name: String) {
        val file = File(context.getExternalFilesDir(null), "qa/video-text-ui-$name.png")
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try { file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }
    @Test fun controlsGeneratedPreviewAndFreshModelRestoreUseTheSameLayer() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(15000) { compose.onAllNodes(hasTestTag("compact-home") or hasTestTag("wide-home-tools")).fetchSemanticsNodes().isNotEmpty() }
        compose.homeNode("Edit video").performClick()
        scenario!!.onActivity { model = ViewModelProvider(it)[VideoCompressionViewModel::class.java] }
        compose.waitUntil(15000) { model?.state?.value?.busy == false }
        Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(Instrumentation.ActivityResult(
            android.app.Activity.RESULT_OK, Intent().setData(Uri.parse("content://com.example.contentstudio.test.videos/edit-first.mp4"))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)))
        compose.workspaceNode("choose-compression-video").performScrollTo().performClick()
        compose.waitUntil(30000) { model?.state?.value?.source != null && model?.state?.value?.busy == false }
        compose.workspaceNode("video-text-enable").performScrollTo().performClick()
        compose.workspaceNode("video-text-input").performScrollTo().performTextInput("Birlevo İğ\nHello")
        // Hide keyboard before scrolling, including on narrow displays.
        instrumentation.uiAutomation.executeShellCommand("input keyevent 111").close()
        compose.workspaceNode("video-text-position-TOP_RIGHT").performScrollTo().performClick()
        compose.workspaceNode("video-text-color-CORAL").performScrollTo().performClick()
        compose.workspaceNode("video-text-size").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(.08f) }
        compose.workspaceNode("video-text-start").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(500f) }
        compose.workspaceNode("video-text-end").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(2500f) }
        val layer = model!!.state.value.textLayer!!
        assertEquals("Birlevo İğ\nHello", layer.text); assertEquals(VideoTextPosition.TOP_RIGHT, layer.position)
        assertEquals(VideoTextColor.CORAL, layer.color); assertEquals(500L, layer.startMs); assertEquals(2500L, layer.endMs)
        compose.workspaceNode("video-text-color-CORAL").performScrollTo().assertIsDisplayed(); shot("controls")
        scenario!!.onActivity { model!!.setOptions(maxEdge = 640) }
        compose.waitUntil(30000) { model!!.state.value.outputSupport.isNotEmpty() }
        compose.workspaceNode("process-video").performScrollTo().assertIsEnabled().performClick()
        compose.waitUntil(120000) { !model!!.state.value.busy }
        val result = model!!.state.value.result!!
        compose.workspaceNode("play-output-video").performScrollTo().performClick()
        onView(isAssignableFrom(PlayerView::class.java)).inRoot(isDialog()).check { view, _ ->
            assertEquals(Uri.fromFile(result.file), (view as PlayerView).player!!.currentMediaItem!!.localConfiguration!!.uri)
        }
        shot("result")
        compose.workspaceNode("close-video-player").performClick()
        runBlocking { withContext(Dispatchers.Main) { assertTrue(model!!.awaitSession()) } }
        lateinit var restored: VideoCompressionViewModel
        scenario!!.onActivity { restored = ViewModelProvider(extraStore, ViewModelProvider.AndroidViewModelFactory(it.application))[VideoCompressionViewModel::class.java] }
        compose.waitUntil(30000) { !restored.state.value.busy && restored.state.value.result != null }
        assertEquals(layer, restored.state.value.textLayer); assertEquals(result.file, restored.state.value.result!!.file)
        // Changing text retires the previous output and its per-result copy approval.
        compose.workspaceNode("video-text-input").performScrollTo().performTextReplacement("Changed")
        assertNull(model!!.state.value.result)
        compose.workspaceNode("video-text-enable").performScrollTo().performClick()
        assertNull(model!!.state.value.textLayer)
    }
}

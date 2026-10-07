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

class VideoImageUiDeviceTest {
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
        val file = File(context.getExternalFilesDir(null), "qa/video-image-ui-$name.png")
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try { file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }
    @Test fun controlsGeneratedPreviewAndFreshModelRestoreUseTheSameLayer() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(15000) { compose.onAllNodes(hasTestTag("compact-home") or hasTestTag("wide-home-tools")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasTestTag("compact-home") or hasTestTag("wide-home-tools"))
            .performScrollToNode(hasText("Edit video"))
        compose.onNodeWithText("Edit video").performClick()
        scenario!!.onActivity { model = ViewModelProvider(it)[VideoCompressionViewModel::class.java] }
        compose.waitUntil(15000) { model?.state?.value?.busy == false }
        Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(Instrumentation.ActivityResult(
            android.app.Activity.RESULT_OK, Intent().setData(Uri.parse("content://com.example.contentstudio.test.videos/edit-first.mp4"))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)))
        compose.onNodeWithTag("choose-compression-video").performScrollTo().performClick()
        compose.waitUntil(30000) { model?.state?.value?.source != null && model?.state?.value?.busy == false }
        Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(Instrumentation.ActivityResult(
            android.app.Activity.RESULT_OK, Intent().setData(Uri.parse("content://com.example.contentstudio.test.fixtures/overlay-logo.png"))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)))
        compose.onNodeWithTag("video-image-pick").performScrollTo().performClick()
        compose.waitUntil(30000) { !model!!.state.value.busy && model!!.state.value.imageLayer != null }
        compose.onNodeWithTag("video-image-preview").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("video-image-position-BOTTOM_RIGHT").performScrollTo().performClick()
        compose.onNodeWithTag("video-image-size").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(.4f) }
        compose.onNodeWithTag("video-image-opacity").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(.5f) }
        compose.onNodeWithTag("video-image-start").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(500f) }
        compose.onNodeWithTag("video-image-end").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(2500f) }
        val layer = model!!.state.value.imageLayer!!
        assertEquals(VideoTextPosition.BOTTOM_RIGHT, layer.position); assertEquals(.4f, layer.size)
        assertEquals(.5f, layer.opacity); assertEquals(500L, layer.startMs); assertEquals(2500L, layer.endMs)
        compose.onNodeWithTag("video-image-opacity").performScrollTo().assertIsDisplayed(); shot("controls")
        scenario!!.onActivity { model!!.setOptions(maxEdge = 640) }
        compose.waitUntil(30000) { model!!.state.value.outputSupport.isNotEmpty() }
        compose.onNodeWithTag("process-video").performScrollTo().assertIsEnabled().performClick()
        compose.waitUntil(120000) { !model!!.state.value.busy }
        val result = model!!.state.value.result!!
        compose.onNodeWithTag("play-output-video").performScrollTo().performClick()
        onView(isAssignableFrom(PlayerView::class.java)).inRoot(isDialog()).check { view, _ ->
            assertEquals(Uri.fromFile(result.file), (view as PlayerView).player!!.currentMediaItem!!.localConfiguration!!.uri)
        }
        shot("result")
        compose.onNodeWithTag("close-video-player").performClick()
        runBlocking { withContext(Dispatchers.Main) { assertTrue(model!!.awaitSession()) } }
        lateinit var restored: VideoCompressionViewModel
        scenario!!.onActivity { restored = ViewModelProvider(extraStore, ViewModelProvider.AndroidViewModelFactory(it.application))[VideoCompressionViewModel::class.java] }
        compose.waitUntil(30000) { !restored.state.value.busy && restored.state.value.result != null }
        assertEquals(layer, restored.state.value.imageLayer); assertNotNull(restored.state.value.imagePreview); assertEquals(result.file, restored.state.value.result!!.file)
        // Changing the image settings retires the previous output and its copy approval.
        compose.onNodeWithTag("video-image-opacity").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(.75f) }
        assertNull(model!!.state.value.result)
        compose.onNodeWithTag("video-image-remove").performScrollTo().performClick()
        assertNull(model!!.state.value.imageLayer); assertNull(model!!.state.value.imagePreview)
    }
}

package com.example.contentstudio

import android.app.Instrumentation
import android.app.LocaleManager
import android.content.Intent
import android.net.Uri
import android.os.LocaleList
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.platform.app.InstrumentationRegistry
import com.example.contentstudio.media.*
import com.example.contentstudio.processing.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import java.io.File

class VideoFrameUiDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private var scenario: ActivityScenario<MainActivity>? = null
    private var video: VideoCompressionViewModel? = null
    private var image: ImageCompressionViewModel? = null
    private var frame: VideoFrameViewModel? = null
    @Before fun setup() = runBlocking<Unit> {
        context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags("en-US")
        withContext(Dispatchers.Main) { ProcessingJobs.get(context).awaitReady(); ProcessingJobs.get(context).forget(ProcessingKind.VIDEO) }
        AtomicMediaSession(File(context.noBackupFilesDir, "video-compression")).write(null)
        Intents.init()
    }
    @After fun cleanup() = runBlocking<Unit> {
        withContext(Dispatchers.Main) { frame?.clear(); image?.reset(); video?.reset(); video?.awaitSession() }
        scenario?.close(); Intents.release()
    }
    private fun shot(name: String) {
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try { File(context.getExternalFilesDir(null), "qa/video-frame-$name.png").apply { parentFile!!.mkdirs() }
            .outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }
    @Test fun frameDialogRecreationActualPreviewAndOwnedImageNavigationWork() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(20000) { compose.onAllNodes(hasTestTag("compact-home") or hasTestTag("wide-home-tools")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasTestTag("compact-home") or hasTestTag("wide-home-tools")).performScrollToNode(hasText("Edit video"))
        compose.onNodeWithText("Edit video").performClick()
        scenario!!.onActivity {
            video = ViewModelProvider(it)[VideoCompressionViewModel::class.java]
            image = ViewModelProvider(it)[ImageCompressionViewModel::class.java]
            frame = ViewModelProvider(it)[VideoFrameViewModel::class.java]
        }
        compose.waitUntil(20000) { video?.state?.value?.busy == false }
        Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(Instrumentation.ActivityResult(
            android.app.Activity.RESULT_OK, Intent().setData(Uri.parse("content://com.example.contentstudio.test.videos/edit-first.mp4"))))
        compose.onNodeWithTag("choose-compression-video").performScrollTo().performClick()
        compose.waitUntil(30000) { video!!.state.value.source != null && !video!!.state.value.busy }
        scenario!!.onActivity { video!!.addSource(Uri.parse("content://com.example.contentstudio.test.videos/edit-second.mp4")) }
        compose.waitUntil(30000) { video!!.state.value.videoClips.size == 2 && !video!!.state.value.busy }
        scenario!!.onActivity { video!!.setTextLayer(VideoTextLayer("Birlevo")); video!!.setOptions(maxEdge = 640) }
        compose.waitUntil(30000) { video!!.state.value.outputSupport.isNotEmpty() }
        compose.onNodeWithTag("process-video").performScrollTo().performClick()
        compose.waitUntil(120000) { !video!!.state.value.busy && video!!.state.value.result != null }
        val result = video!!.state.value.result!!
        compose.onNodeWithTag("video-frame-open").performScrollTo().performClick()
        compose.onNodeWithTag("video-frame-target-2").performScrollTo().performClick()
        compose.onNodeWithTag("video-frame-time").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(3500f) }
        compose.onNodeWithTag("video-frame-preview").performScrollTo().performClick()
        compose.waitUntil(30000) { !frame!!.state.value.busy && frame!!.state.value.frame != null }
        assertTrue(frame!!.state.value.frame!!.timeUs >= 3500000)
        compose.onNodeWithTag("video-frame-image").performScrollTo().assertIsDisplayed(); shot("preview")
        scenario!!.recreate()
        compose.waitUntil(20000) { compose.onAllNodesWithTag("video-frame-image").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("video-frame-image").assertExists()
        assertEquals(3500L, frame!!.state.value.timeMs)
        compose.onNodeWithTag("video-frame-transfer").performScrollTo().assertIsEnabled().performClick()
        compose.waitUntil(30000) { image!!.state.value.source != null && !image!!.state.value.busy }
        compose.onNodeWithTag("image-preview").performScrollTo().assertExists()
        val source = image!!.state.value.source!!
        assertEquals(result.info.width, source.width); assertEquals(result.info.height, source.height)
        assertTrue(source.file.isFile); assertTrue(video!!.state.value.videoClips.all { it.source.originalUri in source.protectedUris })
        assertTrue(File(context.cacheDir, "video-frames").listFiles().orEmpty().none { it.name.startsWith("frame-") })
        compose.onNodeWithTag("image-preview").performClick()
        compose.waitUntil(30000) { !image!!.state.value.busy && image!!.state.value.result != null }; shot("image-tool")
        compose.onNodeWithTag("compression-back").performClick()
        compose.waitUntil(15000) { compose.onAllNodes(hasTestTag("compact-home") or hasTestTag("wide-home-tools")).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(result.file, video!!.state.value.result!!.file)
    }
}

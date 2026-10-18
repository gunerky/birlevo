package com.example.contentstudio

import android.app.LocaleManager
import android.os.LocaleList
import android.os.Build
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.example.contentstudio.data.*
import com.example.contentstudio.media.VideoCompressionViewModel
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class UiAuditDeviceTest {
    @get:Rule(order = 0) val compose = createEmptyComposeRule()
    @get:Rule(order = 1) val residentAdvertising = ResidentAdvertisingRule(compose)
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private var scenario: ActivityScenario<MainActivity>? = null
    @Before fun setup() {
        if (android.os.Build.VERSION.SDK_INT >= 33) context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(
            InstrumentationRegistry.getArguments().getString("audit-locale", "en-US"))
        ProjectRepository(context).save(emptyList())
    }
    @After fun close() { scenario?.close() }
    private fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(20_000) { compose.onAllNodes(hasTestTag("compact-home") or hasTestTag("wide-home-tools")).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun tool(tag: String): SemanticsNodeInteraction = compose.homeTool(tag).performScrollTo()
    private fun capture(name: String) {
        compose.waitForIdle()
        // Locale recreation can have ready semantics before its first window frame is drawn.
        instrumentation.waitForIdleSync()
        instrumentation.uiAutomation.waitForIdle(300, 5_000)
        if (Build.VERSION.SDK_INT >= 29) {
            val frame = CountDownLatch(1)
            scenario!!.onActivity { activity ->
                val view = activity.window.decorView
                view.viewTreeObserver.registerFrameCommitCallback { frame.countDown() }
                view.invalidate()
            }
            assertTrue("The audit window did not commit a rendered frame", frame.await(10, TimeUnit.SECONDS))
        }
        val label = InstrumentationRegistry.getArguments().getString("audit-label", "phone")!!.replace(Regex("[^a-zA-Z0-9-]"), "")
        val folder = File(context.getExternalFilesDir(null), "qa").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(folder, "audit-$label-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    @Test fun allHomeToolCardsOpenTheirActualTools() {
        launch()
        tool("compress-image-action").assertHasClickAction().performClick()
        compose.workspaceNode("choose-image").assertIsDisplayed()
        compose.workspaceNode("compression-back").performClick()
        tool("home-edit-video").assertHasClickAction().performClick()
        compose.workspaceNode("choose-compression-video").performScrollTo().assertIsDisplayed()
        compose.workspaceNode("video-compression-back").performClick()
        tool("home-photo-pdf").assertHasClickAction().performClick()
        compose.workspaceNode("choose-photo-pdf").assertIsDisplayed()
        compose.workspaceNode("photo-pdf-back").performClick()
        tool("home-read-pdf").assertHasClickAction().performClick()
        compose.workspaceNode("reader-back").assertIsDisplayed().performClick()
        tool("home-pdf-pages").assertHasClickAction().performClick()
        compose.workspaceNode("pages-add").assertIsDisplayed()
        compose.workspaceNode("pages-back").performClick()
        tool("home-read-documents").assertHasClickAction().performClick()
        compose.workspaceNode("choose-document").assertIsDisplayed()
        compose.workspaceNode("document-back").performClick()
        tool("home-new-presentation").assertHasClickAction().performClick()
        compose.editorNode("slide-heading-0").assertExists()
    }
    @Test fun fileFirstHomeShowsFiveCategoriesWithoutDuplicateShortcuts() {
        launch()
        tool("home-open-file").assertIsDisplayed().assertHasClickAction()
        listOf("compress-image-action", "home-edit-video", "home-audio", "home-documents", "home-presentation").forEach {
            tool(it).assertHasClickAction().assertIsDisplayed()
        }
        compose.onNodeWithTag("hero-images").assertDoesNotExist()
        compose.onNodeWithTag("home-compress-video").assertDoesNotExist()
    }
    @Test fun legacyVideoDraftOpensWorkingEditorWithoutChangingStoredSource() {
        val uri = Uri.parse("content://com.example.contentstudio.test.videos/audio.mp4")
        ProjectRepository(context).save(listOf(StudioProject(id = "legacy-ui", kind = ProjectKind.VIDEO,
            title = "Legacy video", updatedAt = 1, sourceUri = uri.toString(), sourceName = "sample.mp4")))
        launch(); compose.projectNode("Legacy video").performScrollTo().performClick()
        compose.editorNode("draft-edit-video").performScrollTo().performClick()
        var model: VideoCompressionViewModel? = null
        scenario!!.onActivity { model = ViewModelProvider(it)[VideoCompressionViewModel::class.java] }
        compose.waitUntil(30_000) { model?.state?.value?.let { !it.busy && it.source != null } == true }
        assertNull(model!!.state.value.issue)
        compose.workspaceNode("video-compression-back").performClick()
        compose.editorNode("draft-edit-video").assertExists()
        assertEquals(uri.toString(), ProjectRepository(context).load().single().sourceUri)
    }
    @Test fun visualAuditHomeAndImportAndPdfToolsStayReachable() {
        launch(); compose.workspaceNode("home-open-file").assertIsDisplayed(); capture("home")
        tool("compress-image-action").performClick(); compose.workspaceNode("choose-image").assertIsDisplayed(); capture("image-import")
        compose.workspaceNode("compression-back").performClick()
        compose.homeNode(context.getString(R.string.pdf_pages)).performScrollTo().performClick()
        compose.workspaceNode("pages-add").assertIsDisplayed(); capture("pdf-tools")
    }
    @Test fun plainTextLanguageControlStillOpensLanguageSelection() {
        launch()
        compose.workspaceNode("choose-language").assertIsDisplayed().assertHasClickAction().performClick()
        compose.onNodeWithText("Türkçe").assertIsDisplayed()
        compose.onNodeWithText("English").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.close)).performClick()
        compose.workspaceNode("choose-language").assertIsDisplayed()
    }
    @Test fun modernImageLossNoticeIsVisibleBeforeGeneratingOutput() {
        launch(); tool("compress-image-action").performClick()
        var model: com.example.contentstudio.media.ImageCompressionViewModel? = null
        scenario!!.onActivity {
            model = ViewModelProvider(it)[com.example.contentstudio.media.ImageCompressionViewModel::class.java]
            model!!.load(Uri.parse("content://com.example.contentstudio.test.fixtures/modern.heic"))
        }
        compose.waitUntil(30_000) { model!!.state.value.let { !it.busy && it.source != null } }
        compose.workspaceNode("modern-image-notice").performScrollTo().assertIsDisplayed()
            .assertTextEquals(context.getString(R.string.files_modern_images))
        assertNull(model!!.state.value.result)
        compose.workspaceNode("compression-back").performClick()
    }
}

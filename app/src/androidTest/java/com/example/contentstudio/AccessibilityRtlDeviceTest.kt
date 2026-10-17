package com.example.contentstudio

import android.app.LocaleManager
import android.app.UiAutomation
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.LocaleList
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.example.contentstudio.data.*
import com.example.contentstudio.media.*
import com.example.contentstudio.audio.AudioToolViewModel
import com.example.contentstudio.documents.TextDocumentViewModel
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import java.io.File

/** Real Android accessibility nodes/actions as well as keyboard, RTL and state persistence. */
class AccessibilityRtlDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val args get() = InstrumentationRegistry.getArguments()
    private val talkBack get() = args.getString("audit-talkback") == "true"
    private var activity: ActivityScenario<MainActivity>? = null
    private var image: ImageCompressionViewModel? = null
    private var video: VideoCompressionViewModel? = null
    private var photos: PhotoPdfViewModel? = null
    private var pages: PdfPagesViewModel? = null
    private var slideshow: SlideshowViewModel? = null
    private var audio: AudioToolViewModel? = null
    private var reader: PdfReaderViewModel? = null
    private var document: TextDocumentViewModel? = null
    private val project = StudioProject("accessibility-project", ProjectKind.PRESENTATION,
        "report_مرحبا_2026.pptx", 1, listOf(Slide("عنوان أول", "Original body"), Slide("عنوان ثان", "Second body")))

    @Before fun setup() {
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        if (talkBack) compose.waitUntil(20_000) {
            context.getSystemService(AccessibilityManager::class.java)
                .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { it.resolveInfo.serviceInfo.packageName == "com.google.android.marvin.talkback" }
        }
        context.getSystemService(LocaleManager::class.java).applicationLocales =
            LocaleList.forLanguageTags(args.getString("audit-locale", "ar"))
        ProjectRepository(context).save(listOf(project))
        listOf("video-compression", "audio-conversion", "photo-pdf", SlideshowEngine.FOLDER).forEach {
            AtomicMediaSession(File(context.noBackupFilesDir, it)).write(null)
        }
    }
    @After fun cleanup() = runBlocking<Unit> {
        withContext(Dispatchers.Main) {
            image?.reset(); video?.reset(); photos?.reset(); pages?.reset(); slideshow?.reset()
            audio?.reset(); reader?.reset(); document?.reset()
            video?.awaitSession(); photos?.awaitSession(); slideshow?.awaitSession()
            audio?.awaitSession()
        }
        activity?.close()
        ProjectRepository(context).save(emptyList())
    }
    private fun launch() {
        activity = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(20_000) {
            try { compose.onAllNodesWithTag("home-open-file").fetchSemanticsNodes().isNotEmpty() }
            catch (error: IllegalStateException) {
                // LocaleManager may recreate the just-launched Activity between roots.
                if (error.message?.contains("No compose hierarchies") == true) false else throw error
            }
        }
    }
    private fun label(id: Int): String { var value = ""; activity!!.onActivity { value = it.getString(id) }; return value }
    private fun nativeNodes(): List<AccessibilityNodeInfo> {
        val api = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        fun walk(node: AccessibilityNodeInfo?) {
            if (node == null || node.packageName?.toString() != context.packageName) return
            nodes += node
            for (i in 0 until node.childCount) walk(node.getChild(i))
        }
        val roots = api.windows.mapNotNull { it.root }
        if (roots.isEmpty()) walk(api.rootInActiveWindow) else roots.forEach(::walk)
        return nodes
    }
    private fun native(label: String, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        var found: AccessibilityNodeInfo? = null
        try { compose.waitUntil(20_000) {
            found = nativeNodes().firstOrNull { it.isVisibleToUser && predicate(it) &&
                (it.text?.toString() == label || it.contentDescription?.toString() == label) }
            found != null
        } } catch (failure: androidx.compose.ui.test.ComposeTimeoutException) {
            val folder = File(context.getExternalFilesDir(null), "qa/accessibility-rtl").apply { mkdirs() }
            File(folder, "missing-native-node.txt").writeText(nativeNodes().joinToString("\n") {
                "class=${it.className} text=${it.text} description=${it.contentDescription} " +
                    "state=${it.stateDescription} range=${it.rangeInfo} visible=${it.isVisibleToUser} " +
                    "actions=${it.actionList}"
            })
            throw failure
        }
        return checkNotNull(found)
    }
    private fun nativeClick(text: String) {
        val child = native(text) { it.isEnabled }
        var action = child
        while (!action.isClickable) action = checkNotNull(action.parent)
        val bounds = Rect().also(action::getBoundsInScreen)
        assertTrue("Touch target $text", bounds.height() / context.resources.displayMetrics.density >= 47f)
        if (talkBack) assertTrue(action.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS))
        assertTrue(action.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        compose.waitForIdle()
    }
    private fun nativeProgress(text: String, value: Float) {
        // Compose can expose the label as a child of its actionable range node,
        // as it does for clickable controls. Require that exact ancestor, not an
        // unrelated slider elsewhere in the window, and exercise its real action.
        fun rangeAncestor(label: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            var candidate: AccessibilityNodeInfo? = label
            while (candidate != null && candidate.packageName?.toString() == context.packageName) {
                if (candidate.rangeInfo != null && candidate.isEnabled && candidate.isVisibleToUser)
                    return candidate
                candidate = candidate.parent
            }
            return null
        }
        // A dialog heading can have the same text as its slider. Only accept a
        // label owned by an actual range control, not the first text match.
        val named = native(text) { rangeAncestor(it) != null }
        val action = checkNotNull(rangeAncestor(named))
        if (talkBack) assertTrue(action.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS))
        assertTrue(action.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id,
            Bundle().apply { putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, value) }))
        compose.waitForIdle()
    }
    private fun nativeSelection(text: String): String {
        var value = native(text) { true }
        while (!value.isClickable) value = checkNotNull(value.parent)
        value.refresh()
        // Android represents selection differently for buttons and tabs. Verify the actual
        // native state changes, without assuming every role maps to isSelected alone.
        return "${value.isSelected}|${value.isChecked}|${value.stateDescription}"
    }
    private fun shot(name: String) {
        compose.waitForIdle()
        val folder = File(context.getExternalFilesDir(null), "qa/accessibility-rtl").apply { mkdirs() }
        val mode = args.getString("audit-layout", "phone") + "-" + args.getString("audit-locale", "ar")
        val bitmap = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).takeScreenshot()
        try { File(folder, "$mode-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }
    private fun openProject() {
        compose.projectNode(project.title).assertExists()
        if (talkBack) nativeClick(project.title) else compose.projectNode(project.title).performClick()
    }
    private fun backToHome(tag: String) {
        compose.workspaceNode(tag).assertIsDisplayed().performClick()
        // On a wide inspector, Back first closes the active panel as designed.
        if (compose.onAllNodes(hasTestTag("compact-home") or hasTestTag("wide-home-tools")).fetchSemanticsNodes().isEmpty())
            compose.onNodeWithTag(tag).performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("home-open-file").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun mixedFilenameAndProjectMenuRemainReachableWithNativeAccessibilityActions() {
        launch()
        val language = compose.onNodeWithTag("choose-language").fetchSemanticsNode().boundsInRoot
        val brand = compose.onNodeWithText("Birlevo").fetchSemanticsNode().boundsInRoot
        val rtl = args.getString("audit-locale", "ar") in listOf("ar", "fa")
        assertEquals(rtl, language.center.x < brand.center.x)
        compose.projectNode(project.title).assertTextContains(project.title)
        var actions = ""; activity!!.onActivity { actions = it.getString(R.string.work_actions, project.title) }
        nativeClick(actions)
        nativeClick(label(R.string.delete_project))
        compose.onNodeWithTag("work-delete-confirm").assertIsDisplayed()
        shot("delete-confirmation")
        nativeClick(label(R.string.cancel))
        assertEquals(project, ProjectRepository(context).load().single())
    }

    @Test fun headingBodyNotesAndTitleCanBeEditedWithLargeTextAndSavedAcrossRecreation() {
        launch(); openProject()
        compose.editorNode("slide-heading-0").performScrollTo().performClick().performTextReplacement("مرحبا — report 2026")
        compose.onNodeWithTag("slide-body-0").performScrollTo().performTextReplacement("العربية\nفارسی\nEnglish 123")
        if (compose.onAllNodesWithTag("project-panel-close").fetchSemanticsNodes().isNotEmpty())
            compose.onNodeWithTag("project-panel-close").performClick()
        compose.onNodeWithTag("project-editor-list").performScrollToNode(hasTestTag("project-slides"))
        compose.onNodeWithTag("project-slides").performScrollToNode(hasTestTag("select-slide-1"))
        compose.onNodeWithTag("select-slide-1").performClick().assertIsSelected()
        compose.onNodeWithTag("select-slide-0").assertIsNotSelected()
        compose.onNodeWithTag("project-panel-notes").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithTag("slide-notes-1").performScrollTo().performTextReplacement("ملاحظات — یادداشت — notes")
        compose.editorNode("project-title").performScrollTo().performTextReplacement("تم — done 2026")
        compose.workspaceNode("save-project").performClick()
        compose.waitUntil(20_000) { ProjectRepository(context).load().single().title == "تم — done 2026" }
        activity!!.recreate()
        val saved = ProjectRepository(context).load().single()
        assertEquals("مرحبا — report 2026", saved.slides[0].heading)
        assertEquals("العربية\nفارسی\nEnglish 123", saved.slides[0].body)
        assertEquals("ملاحظات — یادداشت — notes", saved.slides[1].notes)
        shot("edited-project-saved")
    }

    @Test fun slideSelectionPanelNameAndQualityReachAndroidAccessibilityNodes() {
        launch(); openProject()
        compose.onNodeWithTag("project-editor-list").performScrollToNode(hasTestTag("project-slides"))
        compose.onNodeWithTag("project-slides").performScrollToNode(hasTestTag("select-slide-1"))
        val before = nativeSelection("عنوان ثان")
        if (talkBack) nativeClick("عنوان ثان") else compose.onNodeWithTag("select-slide-1").performClick()
        compose.onNodeWithTag("select-slide-1").assertIsSelected()
        compose.waitUntil(20_000) { nativeSelection("عنوان ثان") != before }
        var wide = false
        activity!!.onActivity { wide = it.window.decorView.width / it.resources.displayMetrics.density >= 840 && it.resources.configuration.fontScale <= 1.3f }
        if (!wide) {
            val pane = label(R.string.presentation) + " · " + label(R.string.present_notes)
            compose.onNodeWithTag("project-panel-notes").performScrollTo()
            if (talkBack) nativeClick(label(R.string.present_notes)) else compose.onNodeWithTag("project-panel-notes").performClick()
            compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, pane), useUnmergedTree = true).assertCountEquals(1)
            val heading = native(label(R.string.present_notes)) { it.isHeading }
            if (talkBack) assertTrue(heading.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS))
            compose.onNodeWithTag("project-panel-notes").assertIsSelected()
            if (talkBack) nativeClick(label(R.string.close)) else compose.onNodeWithTag("project-panel-close").performClick()
        } else compose.onNodeWithTag("project-panel-notes").performScrollTo().performClick().assertIsSelected()
        if (talkBack) {
            nativeClick(label(R.string.other_actions))
            nativeClick(label(R.string.present_images))
            nativeClick(label(R.string.workspace_output))
            nativeClick("JPEG")
        } else {
            compose.onNodeWithTag("presentation-more").performClick()
            compose.onNodeWithTag("presentation-images").performClick()
            compose.onNodeWithTag("slide-image-panel-output").performScrollTo().performClick()
            compose.onNodeWithTag("slide-image-format-JPEG").performScrollTo().performClick()
        }
        compose.onNodeWithTag("slide-image-quality").performScrollTo()
        nativeProgress(label(R.string.image_quality), 72f)
        lateinit var slideImages: PresentationImageViewModel
        activity!!.onActivity { slideImages = ViewModelProvider(it)[PresentationImageViewModel::class.java] }
        compose.waitUntil(20_000) { slideImages.state.value.options.quality == 72 }
        compose.waitUntil(20_000) { nativeNodes().any { node ->
            node.rangeInfo != null && node.refresh() && node.stateDescription?.toString() == "72"
        } }
        shot("slide-image-quality")
    }

    @Test fun pdfSelectionAcceptsRtlDigitsAndSelectedPageIsExposed() {
        launch(); compose.homeTool("home-pdf-pages").performClick()
        activity!!.onActivity { pages = ViewModelProvider(it)[PdfPagesViewModel::class.java]; pages!!.add(listOf(Uri.parse("content://com.example.contentstudio.test.pdf/reader.pdf"))) }
        compose.waitUntil(30_000) { pages!!.state.value.let { !it.busy && it.pages.size >= 2 && it.previewId == it.pages.first().id && it.pagePreview != null } }
        compose.onNodeWithTag("pages-list").performScrollToNode(hasTestTag("pages-item-1"))
        try { compose.waitUntil(20_000) { compose.onAllNodes(hasTestTag("pages-item-1") and isEnabled()).fetchSemanticsNodes().isNotEmpty() } }
        catch (error: Throwable) {
            val value = pages!!.state.value
            throw AssertionError("PDF card unavailable: busy=${value.busy} issue=${value.issue} preview=${value.previewId} failed=${value.previewFailureId}\n" +
                compose.onAllNodes(isRoot(), useUnmergedTree = true).printToString(maxDepth = 25), error)
        }
        compose.onNodeWithTag("pages-item-1").performClick().assertIsSelected()
        compose.waitUntil(30_000) { pages!!.state.value.let { !it.busy && it.previewId == it.pages[1].id } }
        compose.workspaceNode("pages-range").performScrollTo()
        try { compose.waitUntil(20_000) { compose.onAllNodes(hasTestTag("pages-range") and isEnabled()).fetchSemanticsNodes().isNotEmpty() } }
        catch (error: Throwable) {
            val value = pages!!.state.value
            throw AssertionError("PDF field unavailable: busy=${value.busy} issue=${value.issue} preview=${value.previewId} failed=${value.previewFailureId}\n" +
                compose.onAllNodes(isRoot(), useUnmergedTree = true).printToString(maxDepth = 25), error)
        }
        compose.onNodeWithTag("pages-range").performTextReplacement("٢،١")
        compose.onNodeWithTag("pages-select").performScrollTo().assertIsDisplayed()
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        assertEquals(listOf(1, 0), pages!!.state.value.pages.map { it.page })
        shot("pdf-range")
    }

    @Test fun failedPdfPreviewStopsRetryingAndKeepsControlsAccessible() {
        launch(); compose.homeTool("home-pdf-pages").performClick()
        activity!!.onActivity { pages = ViewModelProvider(it)[PdfPagesViewModel::class.java]; pages!!.add(listOf(Uri.parse("content://com.example.contentstudio.test.pdf/reader.pdf"))) }
        compose.waitUntil(30_000) { pages!!.state.value.let { !it.busy && it.pages.size >= 2 && it.pagePreview != null } }
        val owned = pages!!.state.value.sources.single().file
        assertTrue(owned.delete()) // Only the synthetic private copy, never its original URI.
        compose.onNodeWithTag("pages-list").performScrollToNode(hasTestTag("pages-item-1"))
        compose.onNodeWithTag("pages-item-1").performClick()
        compose.waitUntil(30_000) { pages!!.state.value.let { !it.busy && it.issue != null } }
        val stableUntil = android.os.SystemClock.uptimeMillis() + 1500
        compose.waitUntil(3000) {
            assertFalse("Failed preview must not restart automatically", pages!!.state.value.busy)
            android.os.SystemClock.uptimeMillis() >= stableUntil
        }
        compose.waitForIdle()
        assertFalse(pages!!.state.value.busy)
        assertNull(pages!!.state.value.pagePreview)
        assertEquals(pages!!.state.value.pages[1].id, pages!!.state.value.previewFailureId)
        compose.onNodeWithTag("pages-list").performScrollToNode(hasTestTag("pages-preview-retry"))
        compose.onNodeWithTag("pages-preview-retry").assertIsEnabled().performClick()
        compose.waitUntil(30_000) { pages!!.state.value.let { !it.busy && it.issue != null } }
        compose.workspaceNode("pages-range").performScrollTo().assertIsEnabled()
        shot("pdf-preview-error")
    }

    @Test fun photoAndSlideshowSelectionsRemainLogicalInRtl() = runBlocking<Unit> {
        launch(); compose.homeTool("home-photo-pdf").performClick()
        activity!!.onActivity { photos = ViewModelProvider(it)[PhotoPdfViewModel::class.java] }
        compose.waitUntil(20_000) { !photos!!.state.value.busy }
        val uris = listOf("orientation-6.jpg", "alpha.webp").map { Uri.parse("content://com.example.contentstudio.test.fixtures/$it") }
        withContext(Dispatchers.Main) { photos!!.add(uris) }
        compose.waitUntil(30_000) { !photos!!.state.value.busy && photos!!.state.value.pages.size == 2 }
        val chosen = photos!!.state.value.pages[1]
        compose.onNodeWithTag("photo-pdf-list").performScrollToNode(hasTestTag("photo-page-${chosen.id}"))
        compose.onNodeWithTag("photo-page-${chosen.id}").performClick().assertIsSelected()
        compose.onNodeWithTag("photo-pdf-back").performClick()
        compose.homeTool("home-new").performClick()
        compose.onNodeWithTag("home-slideshow").performClick()
        activity!!.onActivity { slideshow = ViewModelProvider(it)[SlideshowViewModel::class.java] }
        compose.waitUntil(20_000) { !slideshow!!.state.value.busy }
        withContext(Dispatchers.Main) { slideshow!!.add(uris) }
        compose.waitUntil(30_000) { !slideshow!!.state.value.busy && slideshow!!.state.value.timeline.frames.size == 2 }
        compose.onNodeWithTag("slideshow-select-1").performScrollTo().performClick().assertIsSelected()
        assertEquals(slideshow!!.state.value.timeline.frames[1].id, slideshow!!.state.value.selectedId)
        shot("slideshow-selection")
    }

    @Test fun videoTimeAndImageQualityExposeActualValuesAndNativeAdjustment() {
        launch(); compose.homeTool("home-edit-video").assertExists()
        if (talkBack) nativeClick(label(R.string.video)) else compose.homeTool("home-edit-video").performClick()
        activity!!.onActivity { video = ViewModelProvider(it)[VideoCompressionViewModel::class.java] }
        compose.waitUntil(20_000) { !video!!.state.value.busy }
        activity!!.onActivity { video!!.load(Uri.parse("content://com.example.contentstudio.test.videos/audio.mp4")) }
        compose.waitUntil(30_000) { !video!!.state.value.busy && video!!.state.value.source != null }
        if (talkBack) nativeClick(label(R.string.workspace_clips))
        compose.workspaceNode("video-trim-start").performScrollTo()
        nativeProgress(label(R.string.video_trim_start), 1000f)
        compose.waitUntil(20_000) { video!!.state.value.videoClips[0].trim.startMs == 1000L }
        if (talkBack) {
            nativeClick(label(R.string.close))
            nativeClick(label(R.string.video_frame))
        } else compose.workspaceNode("video-frame-open").performScrollTo().assertIsDisplayed().performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("video-frame-time").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("video-frame-time").performScrollTo()
        nativeProgress(label(R.string.video_frame), 1200f)
        compose.onNodeWithTag("video-frame-preview").performScrollTo()
        if (talkBack) nativeClick(label(R.string.image_preview)) else compose.onNodeWithTag("video-frame-preview").performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("video-frame-image").fetchSemanticsNodes().isNotEmpty() }
        shot("video-frame")
    }

    @Test fun audioReaderAndTextSettingsExposeHeadingsSelectionAndPreserveRtlText() {
        launch(); compose.homeTool("home-audio").performClick()
        activity!!.onActivity { audio = ViewModelProvider(it)[AudioToolViewModel::class.java] }
        compose.waitUntil(20_000) { !audio!!.state.value.busy }
        activity!!.onActivity { audio!!.load(Uri.parse("content://com.example.contentstudio.test.audio/tone.wav")) }
        compose.waitUntil(30_000) { !audio!!.state.value.busy && audio!!.state.value.source != null }
        compose.onNodeWithTag("audio-panel-output").performScrollTo().performClick().assertIsSelected()
        assertTrue(nativeNodes().any { it.isHeading && it.isVisibleToUser })
        backToHome("audio-back")
        compose.homeTool("home-read-pdf").performClick()
        activity!!.onActivity { reader = ViewModelProvider(it)[PdfReaderViewModel::class.java]; reader!!.load(Uri.parse("content://com.example.contentstudio.test.pdf/reader.pdf")) }
        compose.waitUntil(30_000) { !reader!!.state.value.busy && reader!!.state.value.source != null }
        compose.workspaceNode("reader-format-JPEG").performScrollTo().performClick()
        compose.onNodeWithTag("reader-panel-image").assertIsSelected()
        compose.onNodeWithTag("reader-quality").performScrollTo()
        nativeProgress(label(R.string.image_quality), 74f)
        compose.waitUntil(20_000) { reader!!.state.value.quality == 74 }
        backToHome("reader-back")
        compose.homeTool("home-read-documents").performClick()
        activity!!.onActivity { document = ViewModelProvider(it)[TextDocumentViewModel::class.java]; document!!.load(Uri.parse("content://com.example.contentstudio.test.documents/rtl.txt")) }
        compose.waitUntil(30_000) { !document!!.state.value.busy && document!!.state.value.content != null }
        assertTrue(document!!.state.value.content!!.text!!.contains("العربية"))
        compose.onNodeWithTag("document-panel-input").performScrollTo().performClick().assertIsSelected()
        assertTrue(nativeNodes().any { it.isHeading && it.isVisibleToUser })
        shot("document-input")
    }
}

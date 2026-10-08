package com.example.contentstudio

import android.app.Activity
import android.app.Instrumentation
import android.app.LocaleManager
import android.app.UiAutomation
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.graphics.Rect
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.LocaleList
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.platform.app.InstrumentationRegistry
import com.example.contentstudio.data.*
import com.example.contentstudio.media.*
import com.example.contentstudio.audio.AudioToolViewModel
import com.example.contentstudio.ui.FileAction
import org.junit.*
import org.junit.Assert.*
import java.io.File

/** Navigation through actual pickers, inspector state and exact-result export safeguards. */
class WorkspaceUiDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private var activity: ActivityScenario<MainActivity>? = null
    private var image: ImageCompressionViewModel? = null

    @Before fun setup() {
        context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(
            InstrumentationRegistry.getArguments().getString("audit-locale", "en-US"))
        ProjectRepository(context).save(emptyList())
        Intents.init()
    }
    @After fun cleanup() {
        instrumentation.runOnMainSync { image?.reset() }
        activity?.close()
        Intents.release()
    }
    private fun launch() {
        activity = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("home-open-file").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun pick(uri: String) {
        Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(Instrumentation.ActivityResult(
            Activity.RESULT_OK, Intent().setData(Uri.parse(uri)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)))
        compose.homeTool("home-open-file").performScrollTo().performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("file-actions-back").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun shot(name: String) {
        compose.waitForIdle()
        instrumentation.waitForIdleSync()
        // Semantics can settle before the emulator's RenderThread presents its buffer.
        android.os.SystemClock.sleep(300)
        val args = InstrumentationRegistry.getArguments()
        val prefix = args.getString("audit-layout", "phone") + "-" + args.getString("audit-locale", "en-US")
        val folder = File(context.getExternalFilesDir(null), "qa").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try { File(folder, "workspace-$prefix-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }
    @Test fun generalPickerOffersOnlyOperationsForItsFileAndRestoresSelection() {
        launch()
        val examples = listOf(
            "content://com.example.contentstudio.test.fixtures/orientation-6.jpg" to setOf(FileAction.IMAGE, FileAction.PHOTO_PDF, FileAction.PRESENTATION_IMAGE),
            "content://com.example.contentstudio.test.videos/audio.mp4" to setOf(FileAction.VIDEO, FileAction.AUDIO, FileAction.FRAME),
            "content://com.example.contentstudio.test.audio/tone.wav" to setOf(FileAction.AUDIO),
            "content://com.example.contentstudio.test.pdf/reader.pdf" to setOf(FileAction.PDF_READER, FileAction.PDF_PAGES, FileAction.PDF_IMAGE),
            "content://com.example.contentstudio.test.documents/records.csv" to setOf(FileAction.DOCUMENTS))
        for ((uri, actions) in examples) {
            pick(uri)
            compose.waitUntil(20_000) { actions.all { compose.onAllNodesWithTag("file-action-${it.name}").fetchSemanticsNodes().isNotEmpty() } }
            for (action in FileAction.entries) {
                if (action in actions) compose.onNodeWithTag("file-action-${action.name}").assertHasClickAction()
                else compose.onNodeWithTag("file-action-${action.name}").assertDoesNotExist()
            }
            activity!!.recreate()
            compose.waitUntil(20_000) { compose.onAllNodesWithTag("file-action-${actions.first().name}").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("file-actions-back").performClick()
        }
    }
    @Test fun imageOutputInspectorBackAndCopyGuardsKeepTheSourceAndInvalidateOldResult() {
        launch()
        pick("content://com.example.contentstudio.test.fixtures/orientation-6.jpg")
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("file-action-IMAGE").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("file-action-IMAGE").performClick()
        activity!!.onActivity { image = ViewModelProvider(it)[ImageCompressionViewModel::class.java] }
        compose.waitUntil(30_000) { image!!.state.value.let { !it.busy && it.source != null } }
        val source = image!!.state.value.source!!.file
        compose.onNodeWithTag("image-format-BMP").assertDoesNotExist()
        compose.onNodeWithTag("compression-panel-output").performClick()
        compose.onNodeWithTag("image-format-BMP").performScrollTo().performClick()
        activity!!.recreate()
        compose.onNodeWithTag("image-format-BMP").assertIsSelected()
        pressBack()
        compose.onNodeWithTag("image-format-BMP").assertDoesNotExist()
        assertEquals(ImageOutputFormat.BMP, image!!.state.value.format)
        compose.onNodeWithTag("image-preview").assertIsDisplayed().performClick()
        compose.waitUntil(30_000) { image!!.state.value.let { !it.busy && it.result != null } }
        val output = image!!.state.value.result!!
        assertTrue(output.bytes >= image!!.state.value.source!!.bytes)
        compose.onNodeWithTag("export-image").assertDoesNotExist()
        compose.onNodeWithTag("export-image-anyway").assertIsDisplayed().performClick()
        compose.onNodeWithTag("export-image-keep-original").performClick()
        compose.onNodeWithTag("output-more").performClick()
        compose.onNodeWithTag("share-output").performScrollTo().performClick()
        compose.onNodeWithTag("confirm-output-use").assertIsDisplayed()
        compose.onNodeWithTag("cancel-output-use").performClick()
        compose.onNodeWithTag("compression-panel-output").performClick()
        compose.onNodeWithTag("image-format-JPEG").performScrollTo().performClick()
        assertNull(image!!.state.value.result)
        pressBack()
        assertEquals(source, image!!.state.value.source!!.file)
        assertTrue(source.isFile)
        shot("image")
    }
    @Test fun firstPickedMediaActionWaitsForSessionAndOpensWithOneTap() {
        launch()
        pick("content://com.example.contentstudio.test.videos/audio.mp4")
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("file-action-VIDEO").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("file-action-VIDEO").performClick()
        lateinit var video: VideoCompressionViewModel
        activity!!.onActivity { video = ViewModelProvider(it)[VideoCompressionViewModel::class.java] }
        compose.waitUntil(30_000) { video.state.value.let { !it.busy && it.source?.originalUri.toString().endsWith("audio.mp4") } }
        compose.onNodeWithTag("video-compression-back").performClick()
        compose.onNodeWithTag("file-actions-back").performClick()
        pick("content://com.example.contentstudio.test.audio/tone.wav")
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("file-action-AUDIO").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("file-action-AUDIO").performClick()
        lateinit var audio: AudioToolViewModel
        activity!!.onActivity { audio = ViewModelProvider(it)[AudioToolViewModel::class.java] }
        compose.waitUntil(30_000) { audio.state.value.let { !it.busy && it.source?.originalUri.toString().endsWith("tone.wav") } }
        compose.onNodeWithTag("audio-back").performClick()
        compose.onNodeWithTag("file-actions-back").performClick()
        pick("content://com.example.contentstudio.test.fixtures/orientation-6.jpg")
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("file-action-PHOTO_PDF").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("file-action-PHOTO_PDF").performClick()
        lateinit var photos: PhotoPdfViewModel
        activity!!.onActivity { photos = ViewModelProvider(it)[PhotoPdfViewModel::class.java] }
        compose.waitUntil(30_000) { photos.state.value.let { !it.busy && it.pages.any { page -> page.sourceUri.toString().endsWith("orientation-6.jpg") } } }
        compose.onNodeWithTag("photo-pdf-back").assertIsDisplayed()
    }
    @Test fun presentationEditsOneSelectedSlideAndSavesWhenReturningToTheFile() {
        launch()
        pick("content://com.example.contentstudio.test.fixtures/orientation-6.jpg")
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("file-action-PRESENTATION_IMAGE").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("file-action-PRESENTATION_IMAGE").performClick()
        compose.onNodeWithTag("output-new-presentation").performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("project-panel-edit").fetchSemanticsNodes().isNotEmpty() }
        compose.editorNode("slide-heading-0").performScrollTo().performTextReplacement("First")
        compose.workspaceNode("add-slide").performClick()
        compose.editorNode("slide-heading-1").performScrollTo().performTextReplacement("Second")
        compose.onNodeWithTag("slide-heading-0").assertDoesNotExist()
        pressBack()
        compose.onNodeWithTag("project-slides").performScrollToNode(hasTestTag("select-slide-0"))
        compose.onNodeWithTag("select-slide-0").performClick()
        compose.editorNode("slide-heading-0").assertTextContains("First")
        activity!!.recreate()
        compose.editorNode("slide-heading-0").assertTextContains("First")
        pressBack()
        shot("presentation")
        compose.workspaceNode("save-project").performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("file-actions-back").fetchSemanticsNodes().isNotEmpty() }
        val saved = ProjectRepository(context).load().single()
        assertEquals(listOf("First", "Second"), saved.slides.map { it.heading })
        assertNotNull(saved.slides.first().imageUri)
        compose.onNodeWithTag("file-actions-back").performClick()
        compose.projectNode(saved.title).assertExists()
    }
    @Test fun adaptiveHomeAndWorkspaceKeepActionsVisibleAndRespectFontScale() {
        launch(); shot("home")
        compose.homeTool("compress-image-action").performClick()
        activity!!.onActivity { image = ViewModelProvider(it)[ImageCompressionViewModel::class.java]; image!!.load(Uri.parse("content://com.example.contentstudio.test.fixtures/orientation-6.jpg")) }
        compose.waitUntil(30_000) { image!!.state.value.let { !it.busy && it.source != null } }
        var wide = false
        activity!!.onActivity { wide = it.window.decorView.width / it.resources.displayMetrics.density >= 840 && it.resources.configuration.fontScale <= 1.3f }
        compose.onNodeWithTag(if (wide) "compression-workspace-wide" else "compression-workspace-compact").assertExists()
        compose.onNodeWithTag("image-preview").assertIsDisplayed()
        compose.onNodeWithTag("compression-panel-output").performClick()
        compose.onNodeWithTag("image-format-JPEG").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("image-preview").assertIsDisplayed()
        shot("inspector")
        pressBack()
        compose.onNodeWithTag("image-preview").assertIsDisplayed()
        assertNotNull(image!!.state.value.source)
        shot("image")
    }
    @Test fun talkBackFocusAndNativeAccessibilityActionsReachFileConversion() {
        Assume.assumeTrue("Run with workspace-qa.ps1 -Suite accessibility", InstrumentationRegistry.getArguments().getString("audit-talkback") == "true")
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val manager = context.getSystemService(AccessibilityManager::class.java)
        assertTrue("QA runner must enable TalkBack", manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.packageName == "com.google.android.marvin.talkback" })
        launch()
        fun focusAndClick(label: String) {
            var node: AccessibilityNodeInfo? = null
            fun find(value: AccessibilityNodeInfo?, clickableParent: AccessibilityNodeInfo? = null): AccessibilityNodeInfo? {
                if (value == null) return null
                val action = if (value.isClickable && value.isEnabled) value else clickableParent
                if (value.isVisibleToUser && (value.text?.toString() == label || value.contentDescription?.toString() == label)) return action
                for (index in 0 until value.childCount) find(value.getChild(index), action)?.let { return it }
                return null
            }
            try {
                compose.waitUntil(20_000) {
                    // Compose exposes virtual descendants; the framework text-search API
                    // does not reliably search them on this emulator. Walk the real tree.
                    node = find(instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).rootInActiveWindow)
                    node != null
                }
            } catch (failure: Throwable) {
                val native = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
                val root = native.rootInActiveWindow
                val dump = StringBuilder("Missing action: $label\n")
                fun visit(value: AccessibilityNodeInfo?, depth: Int) {
                    if (value == null || depth > 12) return
                    dump.append("  ".repeat(depth)).append("${value.packageName} ${value.text} ${value.contentDescription} clickable=${value.isClickable} enabled=${value.isEnabled}\n")
                    for (index in 0 until value.childCount) visit(value.getChild(index), depth + 1)
                }
                visit(root, 0)
                val folder = File(context.getExternalFilesDir(null), "qa").apply { mkdirs() }
                File(folder, "talkback-tree.txt").writeText(dump.toString())
                native.takeScreenshot()?.let { bitmap ->
                    try { File(folder, "talkback-failure.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
                    finally { bitmap.recycle() }
                }
                throw AssertionError("Native action missing: $label; active package: ${root?.packageName}", failure)
            }
            val action = checkNotNull(node)
            val bounds = Rect().also(action::getBoundsInScreen)
            val density = context.resources.displayMetrics.density
            assertTrue("Accessible touch height: $label", bounds.height() / density >= 48)
            assertTrue(action.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS))
            assertTrue(action.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        }
        Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK,
            Intent().setData(Uri.parse("content://com.example.contentstudio.test.fixtures/orientation-6.jpg"))))
        focusAndClick(context.getString(R.string.open_file))
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("file-action-IMAGE").fetchSemanticsNodes().isNotEmpty() }
        focusAndClick(context.getString(R.string.compress_image))
        activity!!.onActivity { image = ViewModelProvider(it)[ImageCompressionViewModel::class.java] }
        compose.waitUntil(30_000) { image!!.state.value.let { !it.busy && it.source != null } }
        focusAndClick(context.getString(R.string.create_result))
        compose.waitUntil(30_000) { image!!.state.value.let { !it.busy && it.result != null } }
        shot("talkback-result")
    }
}

package com.example.contentstudio

import android.app.Activity
import android.app.Application
import android.app.Instrumentation
import android.app.LocaleManager
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.LocaleList
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.platform.app.InstrumentationRegistry
import com.example.contentstudio.media.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import java.io.File

class PdfReaderDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val application get() = context.applicationContext as Application
    private fun fixture(name: String) = Uri.parse("content://com.example.contentstudio.test.pdf/$name")
    private fun bytes(uri: Uri) = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
    private fun files() = File(context.noBackupFilesDir, "pdf-reader").listFiles()?.map { it.name }?.toSet().orEmpty()
    private var scenario: ActivityScenario<MainActivity>? = null
    private var model: PdfReaderViewModel? = null
    @Before fun setup() { context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags("en-US") }
    @After fun close() { instrumentation.runOnMainSync { model?.reset() }; scenario?.close() }
    private fun waitReader(predicate: (PdfReaderState) -> Boolean) = compose.waitUntil(30_000) { model!!.state.value.let { !it.busy && predicate(it) } }
    private fun openReader() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(20_000) { compose.onAllNodes(hasTestTag("compact-home") or hasTestTag("wide-home-tools")).fetchSemanticsNodes().isNotEmpty() }
        val title = context.getString(R.string.read_pdf)
        compose.homeNode(title).performScrollTo().performClick()
        scenario!!.onActivity { model = ViewModelProvider(it)[PdfReaderViewModel::class.java] }
        compose.workspaceNode("choose-pdf").performScrollTo().performClick()
        waitReader { it.source != null && it.preview != null }
        assertNull(model!!.state.value.issue)
    }
    private fun readerNode(tag: String): SemanticsNodeInteraction = compose.workspaceNode(tag)
    private fun action(tag: String, group: String): SemanticsNodeInteraction {
        if (group == "reader-copy-actions" && compose.onAllNodesWithTag(group).fetchSemanticsNodes().isEmpty()) compose.onNodeWithTag("reader-panel-copy").performClick()
        compose.onNode(hasTestTag("output-more") and hasAnyAncestor(hasTestTag(group))).performScrollTo().performClick()
        return compose.onNodeWithTag(tag)
    }
    private fun imageAction(tag: String) = action(tag, "reader-image-actions")
    private fun copyAction(tag: String) = action(tag, "reader-copy-actions")

    @Test fun realPagesKeepOrientationAndFullOutputResolutionWhileCopyIsByteExact() = runBlocking<Unit> {
        val original = bytes(fixture("reader.pdf"))
        val engine = PdfReader(context)
        val imported = withContext(Dispatchers.IO) { engine.importPdf(fixture("reader.pdf")) }
        try {
            assertEquals(3, imported.source.pages)
            assertEquals(1280, imported.preview.width); assertEquals(640, imported.preview.height)
            assertEquals(Color.BLUE, imported.preview.getPixel(1000, 500))
            val portrait = withContext(Dispatchers.IO) { engine.preview(imported.source, 1) }
            try { assertEquals(640, portrait.width); assertEquals(1280, portrait.height); assertEquals(Color.RED, portrait.getPixel(500, 1000)) }
            finally { portrait.recycle() }
            for (edge in PdfReaderPolicy.outputEdges) {
                val image = withContext(Dispatchers.IO) { engine.pageImage(imported.source, 0, edge) }
                try {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(image.file.path, bounds)
                    assertEquals(edge, bounds.outWidth); assertEquals(edge / 2, bounds.outHeight)
                    assertEquals("image/png", bounds.outMimeType)
                } finally { image.file.delete() }
            }
            withContext(Dispatchers.IO) { engine.export(imported.source.file, imported.source.originalUri, fixture("export.pdf")) }
            assertArrayEquals(original, bytes(fixture("export.pdf")))
            assertArrayEquals(original, bytes(fixture("reader.pdf")))
            try { engine.export(imported.source.file, imported.source.originalUri, imported.source.originalUri); fail("Source overwrite must fail") }
            catch (_: IllegalArgumentException) { }
            assertArrayEquals(original, bytes(fixture("reader.pdf")))
        } finally { imported.preview.recycle(); imported.source.file.delete() }
    }

    @Test fun malformedProtectedAndOversizeDocumentsHaveDistinctErrorsWithoutPartialCopies() = runBlocking<Unit> {
        val before = files()
        for ((name, expected) in listOf("fake.pdf" to PdfReadIssue.INVALID, "protected.pdf" to PdfReadIssue.PASSWORD, "oversize.pdf" to PdfReadIssue.LIMIT)) {
            val issue = try {
                val result = withContext(Dispatchers.IO) { PdfReader(context).importPdf(fixture(name)) }
                result.preview.recycle(); result.source.file.delete(); null
            } catch (e: PdfReadException) { e.issue }
            assertEquals(name, expected, issue)
            assertEquals(name, before, files())
        }
    }

    @Test fun cancelledProviderReadRemovesPartialSnapshot() = runBlocking<Unit> {
        val before = files()
        coroutineScope {
            val job = launch(Dispatchers.IO) { PdfReader(context).importPdf(fixture("slow.pdf")) }
            delay(100); job.cancelAndJoin()
        }
        assertEquals(before, files())
    }

    @Test fun savedSessionRestoresPrivateSourceAndFailedReplacementKeepsValidDocument() {
        val handle = SavedStateHandle()
        lateinit var first: PdfReaderViewModel
        lateinit var restored: PdfReaderViewModel
        instrumentation.runOnMainSync { first = PdfReaderViewModel(application, handle); first.load(fixture("reader.pdf")) }
        compose.waitUntil(30_000) { !first.state.value.busy && first.state.value.source != null }
        instrumentation.runOnMainSync { first.showPage(1) }
        compose.waitUntil(30_000) { !first.state.value.busy && first.state.value.pageIndex == 1 }
        val owned = first.state.value.source!!.file
        val checkpoint = Bundle(handle.get<Bundle>("pdf-reader-session")!!)
        // A new ViewModel uses only its private snapshot, even without an accessible original URI.
        checkpoint.putString("uri", "content://unavailable.original/document")
        instrumentation.runOnMainSync { restored = PdfReaderViewModel(application, SavedStateHandle(mapOf("pdf-reader-session" to checkpoint))) }
        try {
            compose.waitUntil(30_000) { !restored.state.value.busy && restored.state.value.preview != null }
            assertEquals(owned, restored.state.value.source!!.file); assertEquals(1, restored.state.value.pageIndex)
            instrumentation.runOnMainSync { first.load(fixture("fake.pdf")) }
            compose.waitUntil(30_000) { !first.state.value.busy && first.state.value.issue == PdfReadIssue.INVALID }
            assertEquals(owned, first.state.value.source!!.file); assertTrue(owned.isFile)
            instrumentation.runOnMainSync { first.export(fixture("reader.pdf"), true) }
            assertEquals(PdfReadIssue.EXPORT, first.state.value.issue)
            assertTrue(bytes(fixture("reader.pdf")).isNotEmpty())
            instrumentation.runOnMainSync { first.export(fixture("fail.pdf"), false) }
            compose.waitUntil(30_000) { !first.state.value.busy && first.state.value.issue == PdfReadIssue.EXPORT }
            assertTrue(owned.isFile)
        } finally { instrumentation.runOnMainSync { restored.reset(); first.reset() } }
    }

    @Test fun pickerNavigationZoomRecreationCopySharingAndPngToPhotoPdfWorkTogether() {
        Intents.init()
        var photo: PhotoPdfViewModel? = null
        try {
            Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(fixture("reader.pdf"))))
            Intents.intending(hasAction(Intent.ACTION_CREATE_DOCUMENT)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(fixture("export.pdf"))))
            Intents.intending(hasAction(Intent.ACTION_CHOOSER)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null))
            openReader()
            readerNode("reader-zoom-in").performScrollTo().performClick()
            readerNode("reader-zoom-out").assertIsEnabled()
            readerNode("reader-zoom-reset").performClick()
            readerNode("reader-zoom-out").assertIsNotEnabled()
            readerNode("reader-next").performScrollTo().performClick(); waitReader { it.pageIndex == 1 }
            readerNode("reader-page-input").performScrollTo().performTextReplacement("3")
            readerNode("reader-go").performScrollTo().performClick(); waitReader { it.pageIndex == 2 }
            val source = model!!.state.value.source!!.file
            scenario!!.recreate()
            scenario!!.onActivity { model = ViewModelProvider(it)[PdfReaderViewModel::class.java]; photo = ViewModelProvider(it)[PhotoPdfViewModel::class.java] }
            assertEquals(source, model!!.state.value.source!!.file); assertEquals(2, model!!.state.value.pageIndex)
            readerNode("reader-preview").performScrollTo()
            val previewBounds = compose.workspaceNode("reader-preview").fetchSemanticsNode().boundsInRoot
            assertTrue("Preview must fit a bounded viewport", previewBounds.height <= 700 * context.resources.displayMetrics.density + 1)
            val screenshot = instrumentation.uiAutomation.takeScreenshot()
            try {
                val pageLabels = compose.onAllNodesWithTag("reader-page-number").fetchSemanticsNodes()
                if (pageLabels.isNotEmpty()) {
                    val labelBounds = pageLabels.single().boundsInRoot
                    val x = (labelBounds.left + 4).toInt().coerceIn(0, screenshot.width - 1)
                    val y = (labelBounds.top - 4).toInt().coerceIn(0, screenshot.height - 1)
                    assertNotEquals("PDF pixels must not paint behind page controls", Color.GREEN, screenshot.getPixel(x, y))
                }
                val label = InstrumentationRegistry.getArguments().getString("layout", "phone")
                val output = File(context.getExternalFilesDir(null), "qa/pdf-reader-$label.png").apply { parentFile!!.mkdirs() }
                output.outputStream().use { screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            } finally { screenshot.recycle() }
            readerNode("reader-save-copy").performScrollTo().performClick(); waitReader { it.saved }
            assertArrayEquals(bytes(fixture("reader.pdf")), bytes(fixture("export.pdf")))
            copyAction("share-output").performScrollTo().performClick()
            var chooser: Intent? = null
            compose.waitUntil(15_000) {
                // Espresso's recorder can be updating its list while the chooser is launched.
                chooser = runCatching { Intents.getIntents().lastOrNull { it.action == Intent.ACTION_CHOOSER } }.getOrNull()
                chooser != null
            }
            val send = checkNotNull(chooser).getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
            assertEquals("application/pdf", send.type)
            assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertFalse(send.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0)
            assertArrayEquals(bytes(fixture("reader.pdf")), bytes(send.getParcelableExtra(Intent.EXTRA_STREAM)!!))
            readerNode("reader-create-image").performScrollTo().performClick(); waitReader { it.image != null }
            val png = model!!.state.value.image!!.file.readBytes()
            Intents.intending(hasAction(Intent.ACTION_CREATE_DOCUMENT)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(fixture("export.png"))))
            readerNode("reader-save-image").performScrollTo().performClick(); waitReader { it.saved }
            assertArrayEquals(png, bytes(fixture("export.png")))
            imageAction("add-output-to-photo-pdf").performScrollTo().performClick()
            compose.waitUntil(20_000) { photo!!.state.value.pages.size == 1 && !photo!!.state.value.busy && model!!.state.value.source != null }
            File(context.cacheDir, "shared-outputs").deleteRecursively()
            assertArrayEquals(png, photo!!.state.value.pages.single().file.readBytes())
            compose.photoNode("generate-photo-pdf").performScrollTo().performClick()
            compose.waitUntil(20_000) { photo!!.state.value.result != null && !photo!!.state.value.busy }
            assertEquals(1, photo!!.state.value.result!!.pages)
        } finally { instrumentation.runOnMainSync { photo?.reset() }; Intents.release() }
    }

    @Test fun extractedPageAddsToPresentationAsAnOwnedImage() {
        Intents.init()
        try {
            Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(fixture("reader.pdf"))))
            openReader()
            readerNode("reader-create-image").performScrollTo().performClick(); waitReader { it.image != null }
            val png = model!!.state.value.image!!.file.readBytes()
            imageAction("add-output-to-presentation").performScrollTo().performClick()
            compose.workspaceNode("output-new-presentation").performClick()
            compose.waitUntil(20_000) { model!!.state.value.source != null && compose.onAllNodesWithTag("project-panel-edit").fetchSemanticsNodes().isNotEmpty() }
            File(context.cacheDir, "shared-outputs").deleteRecursively()
            val privateImages = File(context.filesDir, "project-images").listFiles().orEmpty()
            assertTrue("Transferred bytes belong to a private project image", privateImages.any { it.readBytes().contentEquals(png) })
        } finally { Intents.release() }
    }
}


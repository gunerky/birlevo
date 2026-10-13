package com.example.contentstudio

import android.app.Application
import android.app.Activity
import android.app.Instrumentation
import android.app.LocaleManager
import android.content.ClipData
import android.content.Intent
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
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.*
import com.tom_roush.pdfbox.pdmodel.*
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import java.io.File

class PdfPagesDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val application get() = context.applicationContext as Application
    private fun fixture(name: String) = Uri.parse("content://com.example.contentstudio.test.pdf/$name")
    private fun bytes(uri: Uri) = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
    private var scenario: ActivityScenario<MainActivity>? = null
    private var model: PdfPagesViewModel? = null
    @Before fun setup() {
        context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags("en-US")
        PDFBoxResourceLoader.init(context)
    }
    @After fun close() { instrumentation.runOnMainSync { model?.reset() }; scenario?.close() }
    private fun waitModel(predicate: (PdfPagesState) -> Boolean) = compose.waitUntil(60_000) { model!!.state.value.let { !it.busy && predicate(it) } }
    private fun node(tag: String): SemanticsNodeInteraction = compose.workspaceNode(tag).performScrollTo()
    private fun openPages() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(20_000) { compose.onAllNodes(hasTestTag("compact-home") or hasTestTag("wide-home-tools")).fetchSemanticsNodes().isNotEmpty() }
        val title = context.getString(R.string.pdf_pages)
        compose.homeNode(title).performScrollTo().performClick()
        scenario!!.onActivity { model = ViewModelProvider(it)[PdfPagesViewModel::class.java] }
    }

    @Test fun mergeReorderExtractAndRotatePreserveTextVectorAndOriginalBytes() = runBlocking<Unit> {
        val original = bytes(fixture("reader.pdf"))
        val imported = withContext(Dispatchers.IO) { PdfReader(context).importPdf(fixture("reader.pdf")) }
        val second = File(context.cacheDir, "second-pages.pdf").apply { writeBytes(original) }
        val target = File.createTempFile("pages-qa", ".pdf", context.cacheDir)
        try {
            val sources = listOf(imported.source, imported.source.copy(file = second))
            val plan = listOf(PdfPageRef("a", 1, 1, 90), PdfPageRef("b", 0, 0), PdfPageRef("c", 1, 0, 180))
            withContext(Dispatchers.IO) { PdfPageClient(context).create(sources, plan, target) }
            PDDocument.load(target).use { document ->
                assertEquals(3, document.numberOfPages)
                val stripper = PDFTextStripper()
                val text = stripper.getText(document)
                val normalized = text.filterNot { it.isWhitespace() }
                assertTrue("Extracted text: $text", normalized.contains("Readerfixturepage1")); assertTrue("Extracted text: $text", normalized.contains("Readerfixturepage2"))
                assertFalse(normalized.contains("Readerfixturepage3"))
                assertEquals(90, document.getPage(0).rotation); assertEquals(180, document.getPage(2).rotation)
                assertEquals(200f, document.getPage(0).mediaBox.width)
                assertEquals(400f, document.getPage(0).mediaBox.height)
                assertNotNull(document.getPage(0).resources.fontNames.firstOrNull())
                // A vector color fill stays in the content stream, not a raster image.
                assertFalse(document.getPage(0).resources.xObjectNames.iterator().hasNext())
            }
            val preview = withContext(Dispatchers.IO) { PdfReader(context).preview(PdfSource(target, Uri.EMPTY, "qa.pdf", target.length(), 3), 0) }
            try { assertEquals(1280, preview.width); assertEquals(640, preview.height)
                assertEquals(android.graphics.Color.RED, preview.getPixel(1000, 500))
            } finally { preview.recycle() }
            assertArrayEquals(original, bytes(fixture("reader.pdf"))); assertArrayEquals(original, imported.source.file.readBytes())
        } finally { imported.preview.recycle(); imported.source.file.delete(); second.delete(); target.delete() }
    }

    @Test fun formsAndPasswordDocumentsAreRejectedWithoutWritingAFalseSuccess() = runBlocking<Unit> {
        val forms = File.createTempFile("form-qa", ".pdf", context.cacheDir)
        val protected = File.createTempFile("protected-qa", ".pdf", context.cacheDir).apply { writeBytes(bytes(fixture("protected.pdf"))) }
        val output = File.createTempFile("result-qa", ".pdf", context.cacheDir)
        try {
            PDDocument().use { document -> document.addPage(PDPage()); document.documentCatalog.cosObject.setItem(COSName.ACRO_FORM, COSDictionary()); document.save(forms) }
            for ((file, issue) in listOf(forms to PdfPageIssue.COMPLEX, protected to PdfPageIssue.PROTECTED)) {
                try { withContext(Dispatchers.IO) { PdfPageClient(context).create(listOf(PdfSource(file, Uri.EMPTY, "qa.pdf", file.length(), 1)), listOf(PdfPageRef("a", 0, 0)), output) }; fail("Protected input was accepted") }
                catch (error: PdfPageException) { assertEquals(issue, error.issue) }
                assertEquals(0L, output.length())
            }
        } finally { forms.delete(); protected.delete(); output.delete() }
    }

    @Test fun workspaceRestoresSelectionRotationAndCannotOverwriteAnySource() {
        val checkpoint = SavedStateHandle()
        instrumentation.runOnMainSync { model = PdfPagesViewModel(application, checkpoint); model!!.add(listOf(fixture("reader.pdf"), fixture("reader.pdf"))) }
        waitModel { it.pages.size == 6 }
        instrumentation.runOnMainSync {
            model!!.select("5,1-2"); model!!.rotate(model!!.state.value.pages[0].id)
            model!!.move(model!!.state.value.pages[0].id, 1); model!!.undo()
        }
        assertEquals(90, model!!.state.value.pages[0].rotation)
        val saved = checkNotNull(checkpoint.get<Bundle>("pdf-pages-session"))
        val restored = instrumentation.runOnMainSyncResult { PdfPagesViewModel(application, SavedStateHandle(mapOf("pdf-pages-session" to saved))) }
        assertEquals(listOf(1, 0, 1), restored.state.value.pages.map { it.page })
        assertEquals(90, restored.state.value.pages[0].rotation)
        instrumentation.runOnMainSync { model!!.create() }
        waitModel { it.output != null }
        val original = bytes(fixture("reader.pdf"))
        instrumentation.runOnMainSync { model!!.export(fixture("reader.pdf")) }
        assertEquals(PdfPageIssue.EXPORT, model!!.state.value.issue)
        assertArrayEquals(original, bytes(fixture("reader.pdf")))
        instrumentation.runOnMainSync { model!!.export(fixture("export.pdf")) }
        waitModel { it.saved }
        assertArrayEquals(model!!.state.value.output!!.file.readBytes(), bytes(fixture("export.pdf")))
    }

    @Test fun realPickerPageControlsConfirmationExportAndReaderRoundTrip() {
        Intents.init()
        try {
            val clip = ClipData.newRawUri("PDF", fixture("reader.pdf")).apply { addItem(ClipData.Item(fixture("second.pdf"))) }
            Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().apply { clipData = clip }))
            Intents.intending(hasAction(Intent.ACTION_CREATE_DOCUMENT)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(fixture("export.pdf"))))
            openPages()
            node("pages-add").performClick(); waitModel { it.pages.size == 6 }
            node("pages-down-0").performClick(); node("pages-rotate-0").performClick()
            node("pages-item-0").performClick(); waitModel { it.pagePreview != null }
            node("pages-page-preview-0").assertIsDisplayed()
            node("pages-range").performTextInput("1-2"); node("pages-select").performClick()
            assertEquals(2, model!!.state.value.pages.size)
            node("pages-create").performClick(); compose.workspaceNode("pages-confirm").assertIsDisplayed().performClick()
            waitModel { it.output != null }; node("pages-preview").assertIsDisplayed()
            node("pages-save").performClick(); waitModel { it.saved }
            node("pages-read").performClick()
            lateinit var reader: PdfReaderViewModel
            scenario!!.onActivity { reader = ViewModelProvider(it)[PdfReaderViewModel::class.java] }
            compose.waitUntil(30_000) { !reader.state.value.busy && reader.state.value.preview != null }
            // A lazy preview may be below the fold at 200% font size. Scroll as a user would.
            compose.workspaceNode("reader-list").performScrollToNode(hasTestTag("reader-preview"))
            compose.workspaceNode("reader-preview").assertIsDisplayed()
            compose.workspaceNode("reader-back").performClick()
            node("pages-preview").assertIsDisplayed()
        } finally { Intents.release() }
    }

    @Test fun cancelledPickerAfterResetCannotDeleteAnOriginal() {
        instrumentation.runOnMainSync { model = PdfPagesViewModel(application, SavedStateHandle()); model!!.add(listOf(fixture("reader.pdf"))) }
        waitModel { it.sources.isNotEmpty() }
        val original = bytes(fixture("reader.pdf"))
        instrumentation.runOnMainSync { model!!.prepareExport(); model!!.reset(); model!!.export(fixture("reader.pdf")) }
        assertEquals(PdfPageIssue.EXPORT, model!!.state.value.issue)
        assertArrayEquals(original, bytes(fixture("reader.pdf")))
    }

    @Test fun failedBatchAndCancellationKeepWorkspaceAndCleanUndeliveredCopies() {
        instrumentation.runOnMainSync { model = PdfPagesViewModel(application, SavedStateHandle()); model!!.add(listOf(fixture("reader.pdf"))) }
        waitModel { it.sources.size == 1 }
        val before = model!!.state.value
        val folder = File(context.noBackupFilesDir, "pdf-pages")
        val files = folder.listFiles()!!.map { it.name }.toSet()
        instrumentation.runOnMainSync { model!!.add(listOf(fixture("reader.pdf"), fixture("fake.pdf"))) }
        waitModel { it.issue != null }
        assertEquals(before.sources, model!!.state.value.sources)
        assertEquals(before.pages, model!!.state.value.pages)
        assertEquals(files, folder.listFiles()!!.map { it.name }.toSet())
        instrumentation.runOnMainSync { model!!.add(listOf(fixture("slow.pdf"))); model!!.cancel() }
        waitModel { it.sources.size == 1 }
        assertEquals(before.pages, model!!.state.value.pages)
        assertEquals(files, folder.listFiles()!!.map { it.name }.toSet())
        assertTrue(before.sources.single().file.isFile)
    }

    private fun <T> Instrumentation.runOnMainSyncResult(action: () -> T): T { var result: T? = null; runOnMainSync { result = action() }; return checkNotNull(result) }
}

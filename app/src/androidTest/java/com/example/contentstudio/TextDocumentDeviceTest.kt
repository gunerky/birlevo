package com.example.contentstudio

import android.app.Activity
import android.app.Application
import android.app.Instrumentation
import android.app.LocaleManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.LocaleList
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.platform.app.InstrumentationRegistry
import com.example.contentstudio.documents.*
import com.example.contentstudio.data.*
import com.example.contentstudio.media.*
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.nio.charset.Charset

class TextDocumentDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val application get() = context.applicationContext as Application
    private fun uri(name: String) = Uri.parse("content://com.example.contentstudio.test.documents/$name")
    private fun bytes(uri: Uri) = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
    private fun files() = File(context.noBackupFilesDir, "text-documents").listFiles()?.map { it.name }?.toSet().orEmpty()
    private var scenario: ActivityScenario<MainActivity>? = null
    private var model: TextDocumentViewModel? = null
    @Before fun setup() {
        context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags("en-US")
        ProjectRepository(context).save(emptyList())
        PDFBoxResourceLoader.init(context)
    }
    @After fun close() { instrumentation.runOnMainSync { model?.reset() }; scenario?.close() }
    private fun waitState(predicate: (TextDocumentState) -> Boolean) = compose.waitUntil(30_000) { model!!.state.value.let { !it.busy && predicate(it) } }
    private fun node(tag: String): SemanticsNodeInteraction = compose.workspaceNode(tag)
    private fun launch(file: String? = null) {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(20_000) { compose.onAllNodes(hasTestTag("compact-home") or hasTestTag("wide-home-tools")).fetchSemanticsNodes().isNotEmpty() }
        compose.homeTool("home-read-documents").performScrollTo().performClick()
        scenario!!.onActivity { model = ViewModelProvider(it)[TextDocumentViewModel::class.java]; if (file != null) model!!.load(uri(file)) }
        if (file != null) waitState { it.content != null }
    }

    @Test fun quotedCsvAndTsvConversionsPreserveAllCellsAndOriginalBytes() = runBlocking<Unit> {
        val engine = TextDocumentEngine(context)
        val original = bytes(uri("records.csv"))
        val imported = withContext(Dispatchers.IO) { engine.import(uri("records.csv"), DocumentEncoding.AUTO) }
        try {
            assertEquals(DocumentKind.CSV, imported.content.kind)
            assertEquals("line 1\r\nline 2", imported.content.rows[1][1])
            assertEquals("00123", imported.content.rows[1][2])
            assertEquals("=SUM(1,2)", imported.content.rows[2][2])
            assertEquals(4, imported.content.rows.size)
            for (target in listOf(DocumentTarget.CSV, DocumentTarget.TSV, DocumentTarget.TXT)) {
                val result = withContext(Dispatchers.IO) { engine.create(imported.content, target) }
                try {
                    val text = result.file.readText(Charsets.UTF_8)
                    val kind = if (target == DocumentTarget.CSV) DocumentKind.CSV else DocumentKind.TSV
                    val parsed = TextDocumentCodec.parse(text.toByteArray(), kind, delimiter = if (kind == DocumentKind.CSV) ',' else null)
                    assertEquals(imported.content.rows, parsed.rows)
                    withContext(Dispatchers.IO) { engine.export(result, imported.source.originalUri, uri("export.${target.extension}")) }
                    assertArrayEquals(result.file.readBytes(), bytes(uri("export.${target.extension}")))
                    val output = withContext(Dispatchers.IO) { MediaOutputs(context).stage(result.file, target.mime, "converted.${target.extension}") }
                    assertArrayEquals(result.file.readBytes(), bytes(output.uri))
                    assertEquals(target.mime, output.mimeType)
                    assertTrue(output.shareIntent().flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
                    try { engine.export(result, imported.source.originalUri, imported.source.originalUri); fail() } catch (_: IllegalArgumentException) { }
                } finally { result.file.delete() }
            }
            assertArrayEquals(original, bytes(uri("records.csv")))
            val semicolon = withContext(Dispatchers.IO) { engine.import(uri("semicolon.csv"), DocumentEncoding.AUTO) }
            try { assertEquals(';', semicolon.content.delimiter); assertEquals("1,25", semicolon.content.rows[1][1]) }
            finally { semicolon.source.file.delete() }
        } finally { imported.source.file.delete() }
    }

    @Test fun unicodeAndExplicitLegacyEncodingProduceUtf8WithoutReplacementCharacters() = runBlocking<Unit> {
        val engine = TextDocumentEngine(context)
        for ((name, encoding) in listOf("utf16.txt" to DocumentEncoding.AUTO, "legacy.txt" to DocumentEncoding.WINDOWS1254, "rtl.txt" to DocumentEncoding.AUTO)) {
            val original = bytes(uri(name))
            val imported = withContext(Dispatchers.IO) { engine.import(uri(name), encoding) }
            val result = withContext(Dispatchers.IO) { engine.create(imported.content, DocumentTarget.TXT) }
            try {
                assertEquals(imported.content.text, result.file.readText(Charsets.UTF_8))
                assertFalse(imported.content.text.contains('\uFFFD'))
                assertArrayEquals(original, bytes(uri(name)))
            } finally { imported.source.file.delete(); result.file.delete() }
        }
    }

    @Test fun textAndTablePdfReportsKeepLastPageAndSelectableLatinText() = runBlocking<Unit> {
        val engine = TextDocumentEngine(context)
        for (name in listOf("long.txt", "records.csv", "rtl.txt")) {
            val imported = withContext(Dispatchers.IO) { engine.import(uri(name), DocumentEncoding.AUTO) }
            val result = withContext(Dispatchers.IO) { engine.create(imported.content, DocumentTarget.PDF) }
            try {
                PDDocument.load(result.file).use { pdf ->
                    assertEquals(result.pages, pdf.numberOfPages)
                    val text = PDFTextStripper().getText(pdf)
                    if (name == "long.txt") {
                        assertTrue(result.pages > 1)
                        assertTrue(text.contains("Line 1 all words")); assertTrue(text.contains("Line 2400 all words"))
                        for (line in listOf(15, 47, 48, 100, 1500, 2399)) assertTrue("Missing $line", text.contains("Line $line all words"))
                    }
                    if (name == "records.csv") { assertTrue(text.contains("00123")); assertTrue(text.contains("=SUM(1,2)")); assertTrue(text.contains("quote \"yes\"")) }
                }
                ParcelFileDescriptor.open(result.file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                    PdfRenderer(descriptor).use { renderer ->
                        assertEquals(result.pages, renderer.pageCount)
                        renderer.openPage(renderer.pageCount - 1).use { page ->
                            val bitmap = Bitmap.createBitmap(595, 842, Bitmap.Config.ARGB_8888)
                            try {
                                bitmap.eraseColor(android.graphics.Color.WHITE)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                assertTrue(bitmap.getPixel(0, 0) != android.graphics.Color.BLACK)
                                val qa = File(context.getExternalFilesDir(null), "qa").apply { mkdirs() }
                                File(qa, "document-pdf-${name.substringBeforeLast('.')}.png").outputStream().use {
                                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                                }
                            }
                            finally { bitmap.recycle() }
                        }
                    }
                }
            } finally { imported.source.file.delete(); result.file.delete() }
        }
    }

    @Test fun malformedOversizeAndCancelledImportsLeaveNoPartialCopies() = runBlocking<Unit> {
        val before = files()
        for ((name, issue) in listOf("invalid.csv" to DocumentIssue.INVALID, "binary.txt" to DocumentIssue.INVALID,
            "invalid.txt" to DocumentIssue.ENCODING, "legacy.txt" to DocumentIssue.ENCODING,
            "oversize.txt" to DocumentIssue.LIMIT, "large-cell.csv" to DocumentIssue.LIMIT)) {
            val actual = try { withContext(Dispatchers.IO) { TextDocumentEngine(context).import(uri(name), DocumentEncoding.AUTO) }; null }
                catch (error: DocumentException) { error.issue }
            assertEquals(name, issue, actual); assertEquals(before, files())
        }
        coroutineScope { val operation = launch(Dispatchers.IO) { TextDocumentEngine(context).import(uri("slow.txt"), DocumentEncoding.AUTO) }; delay(100); operation.cancelAndJoin() }
        assertEquals(before, files())
        val target = File(context.noBackupFilesDir, "text-documents/page-limit.pdf")
        val content = DocumentContent("x\n".repeat(16000), DocumentKind.TEXT, "UTF-8")
        val generated = try { withContext(Dispatchers.IO) { TextDocumentEngine(context).create(content, DocumentTarget.PDF) }; null }
            catch (error: DocumentException) { error.issue }
        assertEquals(DocumentIssue.PDF_LIMIT, generated); assertEquals(before, files()); assertFalse(target.exists())
    }

    @Test fun checkpointIsSmallAndRestoresSettingsWhileFailedReplacementKeepsSource() {
        val handle = SavedStateHandle()
        lateinit var first: TextDocumentViewModel
        lateinit var restored: TextDocumentViewModel
        instrumentation.runOnMainSync { first = TextDocumentViewModel(application, handle); first.load(uri("records.csv")) }
        compose.waitUntil(30_000) { first.state.value.content != null && !first.state.value.busy }
        instrumentation.runOnMainSync { first.setTarget(DocumentTarget.TSV); first.create() }
        compose.waitUntil(30_000) { first.state.value.result != null && !first.state.value.busy }
        val source = first.state.value.source!!.file
        val result = first.state.value.result!!.file
        instrumentation.runOnMainSync { first.load(uri("invalid.csv")) }
        compose.waitUntil(30_000) { first.state.value.issue == DocumentIssue.INVALID && !first.state.value.busy }
        assertEquals(source, first.state.value.source!!.file); assertEquals(result, first.state.value.result!!.file)
        val checkpoint = handle.get<Bundle>("text-document-session")!!
        assertTrue(checkpoint.keySet().size <= 8); assertFalse(checkpoint.containsKey("text"))
        instrumentation.runOnMainSync { restored = TextDocumentViewModel(application, SavedStateHandle(mapOf("text-document-session" to checkpoint))) }
        compose.waitUntil(30_000) { restored.state.value.content != null && !restored.state.value.busy }
        assertEquals(first.state.value.content!!.rows, restored.state.value.content!!.rows)
        assertEquals(DocumentTarget.TSV, restored.state.value.target)
        instrumentation.runOnMainSync { restored.reset(); first.reset() }
    }

    @Test fun stalePickerCannotOverwriteOrDeleteAnEarlierOriginalAfterReplacementOrReset() {
        val original = bytes(uri("records.csv"))
        val handle = SavedStateHandle()
        lateinit var reader: TextDocumentViewModel
        instrumentation.runOnMainSync { reader = TextDocumentViewModel(application, handle); reader.load(uri("records.csv")) }
        compose.waitUntil(30_000) { !reader.state.value.busy && reader.state.value.content != null }
        instrumentation.runOnMainSync { reader.setTarget(DocumentTarget.TSV); reader.create() }
        compose.waitUntil(30_000) { !reader.state.value.busy && reader.state.value.result != null }
        val oldResult = reader.state.value.result!!.file.name
        instrumentation.runOnMainSync { reader.load(uri("utf16.txt")) }
        compose.waitUntil(30_000) { !reader.state.value.busy && reader.state.value.source?.name == "utf16.txt" }
        instrumentation.runOnMainSync { reader.export(uri("records.csv"), oldResult, uri("records.csv").toString()) }
        assertEquals(DocumentIssue.EXPORT, reader.state.value.issue)
        assertArrayEquals(original, bytes(uri("records.csv")))
        instrumentation.runOnMainSync { reader.reset(); reader.export(uri("records.csv"), oldResult, uri("records.csv").toString()) }
        assertEquals(DocumentIssue.EXPORT, reader.state.value.issue)
        assertArrayEquals(original, bytes(uri("records.csv")))
    }

    @Test fun actualPickerTargetSaveAndPdfReaderHandoffWork() {
        Intents.init()
        try {
            Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri("records.csv"))))
            Intents.intending(hasAction(Intent.ACTION_CREATE_DOCUMENT)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri("export.tsv"))))
            launch()
            node("choose-document").performClick(); waitState { it.content != null }
            node("document-table-info").assertTextEquals(context.getString(R.string.document_table_info, 4, 3))
            node("document-target-TSV").performClick(); node("document-create").performClick(); waitState { it.result?.target == DocumentTarget.TSV }
            val expected = model!!.state.value.result!!.file.readBytes()
            node("document-save").performClick(); waitState { it.saved }
            assertArrayEquals(expected, bytes(uri("export.tsv")))
            node("document-target-PDF").performClick(); node("document-create").performClick(); waitState { it.result?.target == DocumentTarget.PDF }
            node("document-read-pdf").performClick()
            var pdf: PdfReaderViewModel? = null
            scenario!!.onActivity { pdf = ViewModelProvider(it)[PdfReaderViewModel::class.java] }
            compose.waitUntil(30_000) { pdf!!.state.value.let { !it.busy && it.source != null && it.preview != null } }
            assertNull(pdf!!.state.value.issue)
            compose.workspaceNode("reader-back").performClick()
        } finally { Intents.release() }
    }

    @Test fun fullRowDetailsAndSelectedTextTransferPreserveExistingProject() {
        val target = StudioProject("existing-text", ProjectKind.PRESENTATION, "Existing project", 1,
            listOf(Slide("Keep", "Original", "content://old-project-image")), theme = PresentationTheme.OCEAN)
        ProjectRepository(context).save(listOf(target))
        launch("wide.tsv")
        node("document-row-1").performClick()
        compose.onNodeWithText("last-cell").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.close)).performClick()
        node("document-add-presentation").performClick()
        compose.onNodeWithText("Existing project").performClick()
        var editor: ProjectEditorViewModel? = null
        scenario!!.onActivity { editor = ViewModelProvider(it)[ProjectEditorViewModel::class.java] }
        // AtomicFile readers may remove an in-progress .new file. Observe the single writer,
        // then independently read the finished snapshot instead of polling storage mid-write.
        compose.waitUntil(30_000) { editor!!.state.value.let {
            it.draft?.id == target.id && it.draft.slides.size > 1 && it.saveStatus == DraftSaveStatus.SAVED
        } }
        val updated = ProjectRepository(context).load().single()
        assertEquals(PresentationTheme.OCEAN, updated.theme)
        assertEquals(target.slides.first(), updated.slides.first())
        assertTrue(updated.slides.drop(1).joinToString("") { it.body }.contains("last-cell"))
        assertTrue(updated.slides.drop(1).all { it.imageUri == null })
        assertTrue(updated.slides.map { it.id }.distinct().size == updated.slides.size)
    }

    @Test fun nativeLayoutAndEncodingControlsRemainUsableAfterRecreation() {
        val locale = InstrumentationRegistry.getArguments().getString("audit-locale", "en-US")
        context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(locale)
        launch("records.csv")
        scenario!!.recreate(); waitState { it.content != null }
        node("choose-document").performScrollTo().assertIsDisplayed()
        node("document-target-TSV").performClick(); node("document-create").performClick(); waitState { it.result != null }
        node("document-result").assertIsDisplayed()
        node("document-encoding-AUTO").assertIsDisplayed()
        compose.waitForIdle()
        val frame = java.util.concurrent.CountDownLatch(1)
        scenario!!.onActivity { activity -> activity.window.decorView.viewTreeObserver.registerFrameCommitCallback { frame.countDown() }; activity.window.decorView.invalidate() }
        assertTrue(frame.await(10, java.util.concurrent.TimeUnit.SECONDS))
        val label = InstrumentationRegistry.getArguments().getString("audit-label", "phone")!!.replace(Regex("[^a-zA-Z0-9-]"), "")
        val folder = File(context.getExternalFilesDir(null), "qa").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(folder, "documents-$label.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        compose.workspaceNode("document-back").performClick()
    }
}

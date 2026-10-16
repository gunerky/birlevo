package com.example.contentstudio

import android.app.Application
import android.app.Activity
import android.app.Instrumentation
import android.app.LocaleManager
import android.net.Uri
import android.content.Intent
import android.os.Bundle
import android.os.LocaleList
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasType
import org.hamcrest.Matchers.allOf
import com.example.contentstudio.documents.*
import com.example.contentstudio.office.*
import com.example.contentstudio.media.MediaOutputs
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import java.io.File

/** Exercises the shipping engine, retained session and Compose workspace, not a parallel probe. */
class OfficeDocumentDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val application get() = context.applicationContext as Application
    private fun uri(name: String) = Uri.parse("content://com.example.contentstudio.test.documents/$name")
    private fun bytes(name: String) = context.contentResolver.openInputStream(uri(name))!!.use { it.readBytes() }
    private fun files() = File(context.noBackupFilesDir, "text-documents").listFiles()?.map { it.name }?.toSet().orEmpty()
    private var scenario: ActivityScenario<MainActivity>? = null
    private var model: TextDocumentViewModel? = null
    @Before fun setup() { context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags("en-US") }
    @After fun close() {
        instrumentation.runOnMainSync { model?.reset() }
        scenario?.close()
        context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags("en-US")
    }
    private fun waitState(check: (TextDocumentState) -> Boolean) = compose.waitUntil(30_000) {
        model!!.state.value.let { !it.busy && check(it) }
    }
    private fun launch(name: String) {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(20_000) { compose.onAllNodes(hasTestTag("compact-home") or hasTestTag("wide-home-tools")).fetchSemanticsNodes().isNotEmpty() }
        compose.homeTool("home-read-documents").performScrollTo().performClick()
        scenario!!.onActivity { model = ViewModelProvider(it)[TextDocumentViewModel::class.java]; model!!.load(uri(name)) }
        waitState { it.content != null }
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val image = instrumentation.uiAutomation.takeScreenshot() ?: error("Screenshot")
        val folder = File(context.getExternalFilesDir(null), "qa/office-integration").apply { mkdirs() }
        try { File(folder, "$name.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
        finally { image.recycle() }
    }

    @Test fun wordCopiesRequireConsentPreserveTextTablesAndOriginalAndCanBeShared() = runBlocking<Unit> {
        val engine = TextDocumentEngine(context)
        val before = bytes("rich.docx")
        val imported = withContext(Dispatchers.IO) { engine.import(uri("rich.docx"), DocumentEncoding.AUTO) }
        try {
            assertEquals(OfficeFormat.DOCX, imported.source.officeFormat)
            assertTrue(imported.content.text.contains("00123"))
            assertTrue(imported.content.text.contains("العربية"))
            try { engine.create(imported.content, DocumentTarget.DOCX); fail("Consent required") }
            catch (error: DocumentException) { assertEquals(DocumentIssue.CONSENT, error.issue) }
            for (target in listOf(DocumentTarget.DOCX, DocumentTarget.TXT, DocumentTarget.PDF)) {
                val result = withContext(Dispatchers.IO) { engine.create(imported.content, target, true) }
                try {
                    assertTrue(result.file.length() > 0)
                    if (target == DocumentTarget.DOCX) {
                        assertEquals(imported.content.office!!.word!!.blocks, OfficeCodec.readWord(result.file).blocks)
                        val staged = withContext(Dispatchers.IO) { MediaOutputs(context).stage(result.file, target.mime, "copy.docx") }
                        assertArrayEquals(result.file.readBytes(), context.contentResolver.openInputStream(staged.uri)!!.use { it.readBytes() })
                    }
                    try { engine.export(result, uri("rich.docx"), uri("rich.docx")); fail("Source overwrite") }
                    catch (_: IllegalArgumentException) { }
                } finally { result.file.delete() }
            }
            assertArrayEquals(before, bytes("rich.docx"))
        } finally { imported.source.file.delete() }
    }

    @Test fun workbookValuesFormulasMissingCacheAndDateMetadataStayDistinct() = runBlocking<Unit> {
        val engine = TextDocumentEngine(context)
        val before = bytes("shared-no-cache.xlsx")
        val imported = withContext(Dispatchers.IO) { engine.import(uri("shared-no-cache.xlsx"), DocumentEncoding.AUTO) }
        try {
            val office = imported.content.office!!
            assertEquals("00123", imported.content.rows[0][0])
            assertEquals("", imported.content.rows[0][2])
            assertEquals(listOf("C1"), office.missingResults)
            assertEquals("46309", imported.content.rows[0][3])
            assertFalse(office.workbook!!.date1904)
            val formula = office.copy(mode = OfficeCellMode.FORMULAS).content()
            assertEquals("=SUM(B1:B2)", formula.rows[0][2])
            for (content in listOf(imported.content, formula)) {
                val result = withContext(Dispatchers.IO) { engine.create(content, DocumentTarget.XLSX, true) }
                try {
                    val book = OfficeCodec.readSheet(result.file)
                    assertTrue(book.sheets.single().cells.all { it.formula == null })
                    assertEquals("00123", book.sheets.single().cells.first { it.address == "A1" }.value)
                    assertEquals(content.rows[0][2], book.sheets.single().cells.first { it.address == "C1" }.value)
                    val staged = withContext(Dispatchers.IO) { MediaOutputs(context).stage(result.file, DocumentTarget.XLSX.mime, "table.xlsx") }
                    assertArrayEquals(result.file.readBytes(), context.contentResolver.openInputStream(staged.uri)!!.use { it.readBytes() })
                } finally { result.file.delete() }
            }
            assertArrayEquals(before, bytes("shared-no-cache.xlsx"))
        } finally { imported.source.file.delete() }
    }

    @Test fun oldTextAndTableInputsExportOfficeWithoutChangingExistingLimits() = runBlocking<Unit> {
        val engine = TextDocumentEngine(context)
        for ((source, target) in listOf("records.csv" to DocumentTarget.XLSX, "rtl.txt" to DocumentTarget.DOCX)) {
            val imported = withContext(Dispatchers.IO) { engine.import(uri(source), DocumentEncoding.AUTO) }
            val result = withContext(Dispatchers.IO) { engine.create(imported.content, target) }
            try {
                if (target == DocumentTarget.XLSX) assertEquals(imported.content.rows, OfficeDocument(workbook = OfficeCodec.readSheet(result.file)).content().rows)
                else assertEquals(imported.content.text, OfficeDocument(word = OfficeCodec.readWord(result.file)).content().text)
            } finally { imported.source.file.delete(); result.file.delete() }
        }
        assertEquals(5000, DocumentPolicy.MAX_ROWS)
        assertEquals(50_000, DocumentPolicy.MAX_CELLS)
        val large = DocumentContent("", DocumentKind.CSV, "UTF-8", rows = List(501) { listOf("value") })
        try { engine.create(large, DocumentTarget.XLSX); fail("Office-specific limit") }
        catch (error: DocumentException) { assertEquals(DocumentIssue.OFFICE_LIMIT, error.issue) }
        val result = engine.create(large, DocumentTarget.CSV)
        try { assertEquals(501, result.file.readLines().size) } finally { result.file.delete() }
    }

    @Test fun invalidAndCancelledOfficeImportsAreTransactional() = runBlocking<Unit> {
        val before = files()
        try { withContext(Dispatchers.IO) { TextDocumentEngine(context).import(uri("invalid.docx"), DocumentEncoding.AUTO) }; fail("Invalid package") }
        catch (error: DocumentException) { assertEquals(DocumentIssue.OFFICE, error.issue) }
        assertEquals(before, files())
        coroutineScope {
            val operation = launch(Dispatchers.IO) { TextDocumentEngine(context).import(uri("slow.docx"), DocumentEncoding.AUTO) }
            delay(100); operation.cancelAndJoin()
        }
        assertEquals(before, files())
    }

    @Test fun checkpointRestoresSheetAndModeButNeverCarriesConversionConsent() {
        val handle = SavedStateHandle()
        lateinit var first: TextDocumentViewModel
        lateinit var restored: TextDocumentViewModel
        instrumentation.runOnMainSync { first = TextDocumentViewModel(application, handle); first.load(uri("rich.xlsx")) }
        compose.waitUntil(30_000) { !first.state.value.busy && first.state.value.content != null }
        instrumentation.runOnMainSync { first.selectOffice(sheet = 1, mode = OfficeCellMode.FORMULAS) }
        compose.waitUntil(30_000) { !first.state.value.busy && first.state.value.officeSheet == 1 }
        instrumentation.runOnMainSync { first.setOfficeConsent(true) }
        assertTrue(first.state.value.officeConsent)
        val bundle = handle.get<Bundle>("text-document-session")!!
        assertTrue(bundle.toString().length < 2000)
        instrumentation.runOnMainSync { restored = TextDocumentViewModel(application, SavedStateHandle(mapOf("text-document-session" to bundle))) }
        try {
            compose.waitUntil(30_000) { !restored.state.value.busy && restored.state.value.content != null }
            assertEquals(1, restored.state.value.officeSheet)
            assertEquals(OfficeCellMode.FORMULAS, restored.state.value.officeMode)
            assertEquals("العربية", restored.state.value.content!!.office!!.workbook!!.sheets[1].name)
            assertFalse(restored.state.value.officeConsent)
            assertNull(restored.state.value.result)
            val source = restored.state.value.source
            instrumentation.runOnMainSync { restored.load(uri("invalid.docx")) }
            compose.waitUntil(30_000) { !restored.state.value.busy && restored.state.value.issue != null }
            assertEquals(source, restored.state.value.source)
        } finally { instrumentation.runOnMainSync { restored.reset(); first.reset() } }
    }

    @Test fun workbookUiRequiresConsentInvalidatesOutputAndPreservesPickerSource() {
        launch("rich.xlsx")
        compose.workspaceNode("document-create").assertIsNotEnabled()
        compose.workspaceNode("office-loss-notice").performScrollTo().assertExists()
        compose.workspaceNode("office-consent").performScrollTo().performClick()
        compose.workspaceNode("document-create").performClick()
        waitState { it.result != null }
        val oldResult = model!!.state.value.result!!.file.name
        compose.workspaceNode("document-panel-input").performClick()
        compose.workspaceNode("office-sheet-1").performScrollTo().performClick()
        waitState { it.officeSheet == 1 }
        assertNull(model!!.state.value.result)
        assertFalse(model!!.state.value.officeConsent)
        compose.workspaceNode("office-mode-FORMULAS").performScrollTo().performClick()
        waitState { it.officeMode == OfficeCellMode.FORMULAS }
        capture("workbook-inspector")
        compose.onNodeWithTag("document-panel-close").performClick()
        compose.workspaceNode("office-selected-sheet").performScrollTo().assertTextContains("العربية", substring = true)
        capture("workbook-preview")
        val before = bytes("rich.xlsx")
        instrumentation.runOnMainSync { model!!.export(uri("rich.xlsx"), oldResult, uri("rich.xlsx").toString()) }
        assertEquals(DocumentIssue.EXPORT, model!!.state.value.issue)
        assertArrayEquals(before, bytes("rich.xlsx"))
    }

    @Test fun missingCacheUiAndTargetChangeDoNotReuseApproval() {
        launch("shared-no-cache.xlsx")
        compose.workspaceNode("office-missing-cache").performScrollTo().assertTextContains("1", substring = true)
        compose.workspaceNode("office-consent").performScrollTo().performClick()
        compose.workspaceNode("document-panel-output").performClick()
        compose.workspaceNode("document-target-XLSX").performScrollTo().performClick()
        assertFalse(model!!.state.value.officeConsent)
        compose.onNodeWithTag("document-panel-close").performClick()
        compose.workspaceNode("document-create").assertIsNotEnabled()
        compose.workspaceNode("office-consent").performScrollTo().performClick()
        compose.workspaceNode("document-create").performClick()
        waitState { it.result?.target == DocumentTarget.XLSX }
        compose.workspaceNode("document-save").assertIsEnabled()
        scenario!!.recreate()
        scenario!!.onActivity { model = ViewModelProvider(it)[TextDocumentViewModel::class.java] }
        waitState { it.result?.target == DocumentTarget.XLSX }
        assertEquals("", model!!.state.value.content!!.rows[0][2])
    }

    @Test fun arabicWordUiHasTranslatedConsentAndAccessibleActions() {
        context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags("ar")
        launch("rich.docx")
        compose.workspaceNode("office-loss-notice").performScrollTo().assertTextContains("نسخة", substring = true)
        capture("word-arabic")
        compose.workspaceNode("office-consent").performScrollTo().assertHasClickAction().performClick()
        compose.workspaceNode("document-create").assertIsEnabled().assertHasClickAction()
        compose.workspaceNode("document-add-presentation").performScrollTo().assertIsEnabled()
    }

    @Test fun officePickerAndDocxSaveUseCorrectMimeAndOriginalRemainsUnchanged() {
        Intents.init()
        try {
            Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri("rich.docx"))))
            Intents.intending(hasAction(Intent.ACTION_CREATE_DOCUMENT)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri("export.docx"))))
            launch("rtl.txt")
            val before = bytes("rich.docx")
            compose.workspaceNode("choose-document").performClick()
            waitState { it.source?.name == "rich.docx" }
            compose.workspaceNode("document-target-DOCX").performScrollTo().performClick()
            compose.onNodeWithTag("document-panel-close").performClick()
            compose.workspaceNode("office-consent").performScrollTo().performClick()
            compose.workspaceNode("document-create").performClick()
            waitState { it.result?.target == DocumentTarget.DOCX }
            val expected = model!!.state.value.result!!.file.readBytes()
            compose.workspaceNode("document-save").performClick()
            waitState { it.saved }
            Intents.intended(allOf(hasAction(Intent.ACTION_CREATE_DOCUMENT), hasType(OfficeFormat.DOCX.mime)))
            assertArrayEquals(expected, bytes("export.docx"))
            assertArrayEquals(before, bytes("rich.docx"))
        } finally { Intents.release() }
    }

    @Test fun persianWorkbookInspectorAndPlainPreviewWorkAtConfiguredWindowSize() {
        context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags("fa")
        launch("shared-no-cache.xlsx")
        compose.workspaceNode("office-loss-notice").performScrollTo().assertTextContains("نسخه", substring = true)
        compose.workspaceNode("document-panel-input").performClick()
        compose.workspaceNode("office-mode-FORMULAS").performScrollTo().performClick()
        waitState { it.officeMode == OfficeCellMode.FORMULAS }
        capture("workbook-persian-inspector")
        if (compose.onAllNodesWithTag("document-panel-close").fetchSemanticsNodes().isNotEmpty())
            compose.onNodeWithTag("document-panel-close").performClick()
        else compose.onNodeWithTag("document-panel-input").performClick()
        compose.workspaceNode("office-consent").performScrollTo().performClick()
        compose.workspaceNode("document-create").assertIsEnabled()
        capture("workbook-persian-preview")
        assertEquals("00123", model!!.state.value.content!!.rows[0][0])
        assertEquals("=SUM(B1:B2)", model!!.state.value.content!!.rows[0][2])
    }
}

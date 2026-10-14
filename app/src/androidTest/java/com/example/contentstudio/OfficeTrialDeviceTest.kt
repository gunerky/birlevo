package com.example.contentstudio

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.example.contentstudio.office.*
import com.example.contentstudio.documents.DocumentPolicy
import com.example.contentstudio.media.FormatSupport
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class OfficeTrialDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun work() = File(context.noBackupFilesDir, "office-trial/${System.nanoTime()}").apply { mkdirs() }
    private fun fixture(name: String, folder: File): File = File(folder, name).also { target ->
        instrumentation.context.assets.open("office/$name").use { input -> target.outputStream().use { output -> input.copyTo(output) } }
    }
    private fun hash(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
    private fun evidence() = File(context.getExternalFilesDir(null), "qa/office-trial").apply { mkdirs() }
    private fun rejects(block: () -> Unit) {
        try { block(); fail("Unverified/unsafe input must be rejected") }
        catch (_: TrialRejected) { }
    }
    private fun archive(folder: File, parts: Map<String, String>): File = File.createTempFile("malformed-", ".zip", folder).also {
        ZipOutputStream(it.outputStream()).use { zip ->
            for ((name, xml) in parts) { zip.putNextEntry(ZipEntry(name)); zip.write(xml.toByteArray()); zip.closeEntry() }
        }
    }

    @Test fun wordParagraphTableUnicodeAndLossesSurviveWithoutOpeningExternalContent() {
        val folder = work()
        val file = fixture("rich.docx", folder)
        val before = hash(file)
        val start = SystemClock.elapsedRealtime()
        val doc = OfficeTrial.readWord(file)
        val millis = SystemClock.elapsedRealtime() - start
        val paragraphs = doc.blocks.filterIsInstance<WordBlock.Paragraph>().map { it.text }
        assertTrue(paragraphs.contains("Birlevo Office compatibility sample"))
        assertTrue(paragraphs.contains("İstanbul café العربية فارسی & <text>"))
        assertTrue(paragraphs.contains("First\tsecond\nthird"))
        assertEquals(listOf(listOf("Code", "Text"), listOf("00123", "سلام‌دنیا")), doc.blocks.filterIsInstance<WordBlock.Table>().single().rows)
        assertTrue(doc.losses.containsAll(setOf("headers_footers", "images", "run_formatting", "hyperlinks", "external_links_not_opened")))
        rejects { OfficeTrial.writeWord(folder, doc) }
        assertEquals(before, hash(file))
        File(evidence(), "word-report.json").writeText(JSONObject().put("paragraphs", JSONArray(paragraphs))
            .put("losses", JSONArray(doc.losses.sorted())).put("expanded_bytes", doc.expanded).put("read_ms_emulator", millis)
            .put("general_office_compatibility", false).toString())
    }

    @Test fun workbookSheetsSparseCellsAndFormulaCachesAreDistinctFromCalculation() {
        val folder = work()
        val file = fixture("rich.xlsx", folder)
        val before = hash(file)
        val start = SystemClock.elapsedRealtime()
        val book = OfficeTrial.readSheet(file)
        val millis = SystemClock.elapsedRealtime() - start
        assertEquals(listOf("Data", "العربية"), book.sheets.map { it.name })
        val cells = book.sheets[0].cells.associateBy { it.address }
        assertEquals("00123", cells.getValue("A2").value)
        assertEquals("=2+2", cells.getValue("A3").value)
        assertNull(cells.getValue("A3").formula)
        assertEquals("B2*2", cells.getValue("C2").formula)
        assertEquals("20", cells.getValue("C2").value)
        assertEquals("1", cells.getValue("D2").value)
        assertEquals("b", cells.getValue("D2").kind)
        assertEquals("2026-10-14T00:00:00.000Z", cells.getValue("E2").value)
        assertEquals("d", cells.getValue("E2").kind)
        assertEquals("yyyy-mm-dd", cells.getValue("E2").numberFormat)
        assertFalse(book.date1904)
        assertEquals(listOf("A5:D5"), book.sheets[0].merges)
        assertTrue(book.losses.containsAll(setOf("styles_number_formats", "merged_cells", "formulas_not_calculated")))
        rejects { OfficeTrial.textGrid(book, 0, emptySet()) }
        val grid = OfficeTrial.textGrid(book, 0, book.losses)
        assertEquals("00123", grid[1][0])
        assertEquals("=B2*2", grid[1][2]) // Formula text, not an evaluated value.
        assertEquals(before, hash(file))
        val sheets = JSONArray(book.sheets.map { sheet -> JSONObject().put("name", sheet.name).put("merges", JSONArray(sheet.merges))
            .put("cells", JSONArray(sheet.cells.map { c -> JSONObject().put("address", c.address).put("kind", c.kind)
                .put("value", c.value ?: JSONObject.NULL).put("formula", c.formula ?: JSONObject.NULL).put("style", c.style)
                .put("number_format", c.numberFormat ?: JSONObject.NULL) })) })
        File(evidence(), "sheet-report.json").writeText(JSONObject().put("sheets", sheets).put("losses", JSONArray(book.losses.sorted()))
            .put("expanded_bytes", book.expanded).put("read_ms_emulator", millis).put("date1904", book.date1904)
            .put("formula_engine", false).toString())
    }

    @Test fun outputsRoundtripLocallyAndTextCannotBecomeExecutableFormula() {
        val folder = work()
        val blocks = listOf(WordBlock.Paragraph("Birlevo basic document"), WordBlock.Paragraph("İstanbul café العربية فارسی & <text>"),
            WordBlock.Paragraph("First\tsecond\nthird"), WordBlock.Table(listOf(listOf("Code", "Text"), listOf("00123", "سلام‌دنیا"))))
        val doc = OfficeTrial.writeWord(folder, WordSnapshot(blocks, emptySet(), 0))
        assertEquals(blocks, OfficeTrial.readWord(doc).blocks)
        val rows = listOf(listOf("Code", "Literal", "Text"), listOf("00123", "=2+2", "İstanbul & <text>"),
            listOf("00000", "=HYPERLINK(\"https://example.invalid\")", "سلام‌دنیا\nnext"), listOf("", "0", ""))
        val xlsx = OfficeTrial.writeTextGrid(folder, rows)
        val book = OfficeTrial.readSheet(xlsx)
        assertTrue(book.sheets.single().cells.all { it.formula == null && it.kind == "inlineStr" })
        assertEquals(rows, OfficeTrial.textGrid(book, 0, book.losses))
        doc.copyTo(File(evidence(), "native.docx"), overwrite = true)
        xlsx.copyTo(File(evidence(), "native.xlsx"), overwrite = true)
    }

    @Test fun missingFormulaCacheIsNotInventedAndSharedStringsPreserveLeadingZeros() {
        val file = fixture("shared-no-cache.xlsx", work())
        val book = OfficeTrial.readSheet(file)
        val cells = book.sheets.single().cells.associateBy { it.address }
        assertEquals("00123", cells.getValue("A1").value)
        assertEquals("SUM(B1:B2)", cells.getValue("C1").formula)
        assertNull(cells.getValue("C1").value)
        assertEquals("46309", cells.getValue("D1").value)
        assertEquals("built-in:14", cells.getValue("D1").numberFormat)
        assertEquals("=SUM(B1:B2)", OfficeTrial.textGrid(book, 0, book.losses)[0][2])
    }

    @Test fun xmlDtdDepthAndPackageTraversalAreRejected() {
        val folder = work()
        for (name in listOf("../escape.xml", "/absolute.xml", "word\\escape.xml", "word//alias.xml", "word/%2e%2e/escape.xml")) {
            rejects { TrialPackage(archive(folder, mapOf(name to "<root/>"))).close() }
        }
        for (xml in listOf("<!DOCTYPE root [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><root>&x;</root>",
            "<r>".repeat(TrialLimits.DEPTH+1) + "</r>".repeat(TrialLimits.DEPTH+1))) {
            rejects { TrialPackage(archive(folder, mapOf("part.xml" to xml))).use { it.xml("part.xml") } }
        }
        assertFalse(File(folder.parentFile, "escape.xml").exists())
    }

    @Test fun inflatedPayloadDuplicateEntriesMacrosAndEscapingRelationshipsAreRejected() {
        val folder = work()
        rejects { TrialPackage(archive(folder, mapOf("bomb.xml" to "x".repeat(TrialLimits.PART+1)))).close() }
        rejects { TrialPackage(fixture("duplicate.zip", folder)).close() }
        rejects { TrialPackage(archive(folder, mapOf("word/vbaProject.bin" to "payload"))).close() }
        val bad = archive(folder, mapOf("_rels/.rels" to
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"r\" Target=\"../outside.xml\"/></Relationships>"))
        rejects { TrialPackage(bad).use { it.resolve("", it.relationships("").single()) } }
        val external = archive(folder, mapOf("_rels/.rels" to
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"r\" TargetMode=\"External\" Target=\"https://example.invalid/data\"/></Relationships>"))
        rejects { TrialPackage(external).use { it.resolve("", it.relationships("").single()) } }
    }

    @Test fun cancellationAndInvalidOutputNeverLeavePartialFilesOrTouchSources() {
        val folder = work()
        val source = fixture("rich.docx", folder)
        val original = hash(source)
        val files = folder.listFiles()!!.map { it.name }.toSet()
        var calls = 0
        try { OfficeTrial.readWord(source) { if (++calls > 5) throw CancellationException("QA") }; fail("Cancellation") }
        catch (_: CancellationException) { }
        calls = 0
        try { OfficeTrial.writeTextGrid(folder, listOf(listOf("text"))) { if (++calls > 2) throw CancellationException("QA") }; fail("Cancellation") }
        catch (_: CancellationException) { }
        rejects { OfficeTrial.writeWord(folder, WordSnapshot(listOf(WordBlock.Paragraph("\uD800")), emptySet(), 0)) }
        assertEquals(files, folder.listFiles()!!.map { it.name }.toSet())
        assertEquals(original, hash(source))
    }

    @Test fun developmentProbeDoesNotAdvertiseNewApplicationFormats() {
        assertFalse(DocumentPolicy.inputMimes.any { it.contains("wordprocessingml") || it.contains("spreadsheetml") })
        assertFalse(FormatSupport.capabilities.flatMap { it.inputs }.any { it.contains("wordprocessingml") || it.contains("spreadsheetml") })
    }

    @Test fun hostEditedOutputsCanBeReopenedByTheAndroidProbe() {
        if (InstrumentationRegistry.getArguments().getString("office-roundtrip") != "true") return
        val folder = evidence()
        val doc = OfficeTrial.readWord(File(folder, "edited.docx"))
        assertEquals("Edited document", (doc.blocks.first() as WordBlock.Paragraph).text)
        val book = OfficeTrial.readSheet(File(folder, "edited.xlsx"))
        assertEquals("Edited value", book.sheets.single().cells.single { it.address == "A2" }.value)
        assertEquals("00000", book.sheets.single().cells.single { it.address == "A3" }.value)
        assertEquals("=2+2", book.sheets.single().cells.single { it.address == "B2" }.value)
        assertTrue(book.sheets.single().cells.all { it.formula == null })
    }
}

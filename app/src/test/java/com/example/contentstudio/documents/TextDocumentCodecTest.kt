package com.example.contentstudio.documents

import org.junit.Assert.*
import org.junit.Test
import java.io.StringWriter
import java.nio.charset.Charset
import kotlin.random.Random

class TextDocumentCodecTest {
    private fun parse(text: String, kind: DocumentKind = DocumentKind.CSV) = TextDocumentCodec.parse(text.toByteArray(), kind)
    private fun output(content: DocumentContent, target: DocumentTarget): String = StringWriter().also { TextDocumentCodec.write(content, target, it) }.toString()
    private fun issue(expected: DocumentIssue, block: () -> Unit) {
        try { block(); fail("Expected $expected") } catch (error: DocumentException) { assertEquals(expected, error.issue) }
    }
    @Test fun quotedNewlinesEscapedQuotesEmptyTrailingCellsAndSpacesArePreserved() {
        val content = parse("a,b,c\r\n\"x,y\",\"line 1\r\nline 2\",\"quote \"\"yes\"\"\"\r\n ,00123,\r\n")
        assertEquals(listOf(listOf("a", "b", "c"), listOf("x,y", "line 1\r\nline 2", "quote \"yes\""), listOf(" ", "00123", "")), content.rows)
        assertEquals(content.rows, parse(output(content, DocumentTarget.CSV)).rows)
        assertEquals(content.rows, parse(output(content, DocumentTarget.TSV), DocumentKind.TSV).rows)
        assertEquals(output(content, DocumentTarget.TSV), output(content, DocumentTarget.TXT))
    }
    @Test fun delimitersCanBeDetectedOrExplicitlyOverriddenWithoutDroppingRaggedCells() {
        val text = "name;value;note\nA;1,25;x\nB;2,50;y\n"
        val content = parse(text)
        assertEquals(';', content.delimiter)
        assertEquals("1,25", content.rows[1][1])
        assertEquals(listOf(listOf("a", "b"), listOf("c"), listOf("d", "e", "f")), parse("a,b\nc\nd,e,f").rows)
        assertEquals(listOf(listOf("a;b", "c")), TextDocumentCodec.parse("a;b,c".toByteArray(), DocumentKind.CSV, delimiter = ',').rows)
    }
    @Test fun bomEncodingsAndExplicitLegacyEncodingAreStrictAndNonDestructive() {
        val text = "İstanbul Türkçe العربية 北京 🙂\r\n"
        assertEquals(text, TextDocumentCodec.decode(byteArrayOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte()) + text.toByteArray(), DocumentEncoding.AUTO).first)
        for ((name, bom) in listOf("UTF-16LE" to byteArrayOf(0xff.toByte(), 0xfe.toByte()), "UTF-16BE" to byteArrayOf(0xfe.toByte(), 0xff.toByte()))) {
            val bytes = bom + text.toByteArray(Charset.forName(name))
            assertEquals(text, TextDocumentCodec.decode(bytes, DocumentEncoding.AUTO).first)
            issue(DocumentIssue.ENCODING) { TextDocumentCodec.decode(bytes, DocumentEncoding.UTF8) }
        }
        val legacy = "İstanbul,ığüşöç".toByteArray(Charset.forName("windows-1254"))
        issue(DocumentIssue.ENCODING) { TextDocumentCodec.decode(legacy, DocumentEncoding.AUTO) }
        assertEquals("İstanbul,ığüşöç", TextDocumentCodec.decode(legacy, DocumentEncoding.WINDOWS1254).first)
        issue(DocumentIssue.ENCODING) { TextDocumentCodec.decode(byteArrayOf(0xc3.toByte()), DocumentEncoding.AUTO) }
        issue(DocumentIssue.ENCODING) { TextDocumentCodec.decode(byteArrayOf(-1, -2, 0, 0, 65, 0, 0, 0), DocumentEncoding.AUTO) }
        issue(DocumentIssue.INVALID) { TextDocumentCodec.decode("abc\u0000xyz".toByteArray(), DocumentEncoding.AUTO) }
    }
    @Test fun malformedQuotesLimitsAndCancellationFailWithoutSilentTruncation() {
        for (text in listOf("\"unfinished", "a\"b,c", "\"a\" x,b")) issue(DocumentIssue.INVALID) { parse(text) }
        issue(DocumentIssue.LIMIT) { parse("x".repeat(DocumentPolicy.MAX_CELL_CHARS + 1)) }
        issue(DocumentIssue.LIMIT) { parse((1..101).joinToString(",") { "x" }) }
        issue(DocumentIssue.LIMIT) { parse("a\n".repeat(DocumentPolicy.MAX_ROWS + 1)) }
        issue(DocumentIssue.LIMIT) { parse((1..501).joinToString("\n") { (1..100).joinToString(",") { "x" } }) }
        issue(DocumentIssue.LIMIT) { TextDocumentCodec.decode(ByteArray(DocumentPolicy.MAX_BYTES.toInt() + 1), DocumentEncoding.AUTO) }
        var calls = 0
        try { TextDocumentCodec.parseRows("x,".repeat(2000), ',') { if (++calls == 1) throw java.util.concurrent.CancellationException() }; fail() }
        catch (_: java.util.concurrent.CancellationException) { }
    }
    @Test fun randomizedTableRoundTripsPreserveCellStringsAndFormulaText() {
        val random = Random(144)
        val examples = listOf("", " a ", "0012", "=SUM(A1:A2)", "İstanbul", "北京", "العربية", "x,y", "x\ty", "x;y", "say \"yes\"", "a\r\nb", "🙂")
        repeat(100) {
            val rows = List(random.nextInt(1, 12)) { List(random.nextInt(1, 8)) { examples[random.nextInt(examples.size)] } }
            val content = DocumentContent("", DocumentKind.CSV, "UTF-8", ',', rows)
            assertEquals(rows, TextDocumentCodec.parse(output(content, DocumentTarget.CSV).toByteArray(), DocumentKind.CSV, delimiter = ',').rows)
            assertEquals(rows, parse(output(content, DocumentTarget.TSV), DocumentKind.TSV).rows)
        }
    }
    @Test fun textExportPreservesLineEndingsAndSurrogatePairsAndDoesNotPretendItIsATable() {
        val text = "A\r\nB\rC\n" + "x".repeat(2047) + "🙂" + "z".repeat(4500)
        val content = parse(text, DocumentKind.TEXT)
        assertEquals(text, TextDocumentCodec.chunks(text).joinToString(""))
        TextDocumentCodec.chunks(text).forEach { assertFalse(it.last().isHighSurrogate()); assertFalse(it.first().isLowSurrogate()) }
        assertEquals(text, output(content, DocumentTarget.TXT))
        assertEquals(listOf(DocumentTarget.TXT, DocumentTarget.PDF), DocumentPolicy.targets(DocumentKind.TEXT))
        try { output(content, DocumentTarget.CSV); fail() } catch (_: IllegalArgumentException) { }
    }
}

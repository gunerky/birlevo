package com.example.contentstudio.documents

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.io.Writer

enum class DocumentKind { TEXT, CSV, TSV }
enum class DocumentEncoding(val charsetName: String?) {
    AUTO(null), UTF8("UTF-8"), UTF16LE("UTF-16LE"), UTF16BE("UTF-16BE"),
    WINDOWS1254("windows-1254"), WINDOWS1252("windows-1252")
}
enum class DocumentTarget(val extension: String, val mime: String) {
    TXT("txt", "text/plain"), CSV("csv", "text/csv"), TSV("tsv", "text/tab-separated-values"), PDF("pdf", "application/pdf"),
    DOCX("docx", OfficeFormat.DOCX.mime), XLSX("xlsx", OfficeFormat.XLSX.mime)
}
enum class DocumentIssue { ACCESS, ENCODING, INVALID, LIMIT, PDF_LIMIT, EXPORT, MEMORY, OFFICE, OFFICE_LIMIT, CONSENT }
class DocumentException(val issue: DocumentIssue) : IllegalArgumentException(issue.name)

object DocumentPolicy {
    const val MAX_BYTES = 4L * 1024 * 1024
    const val MAX_CHARS = 2_000_000
    const val MAX_ROWS = 5000
    const val MAX_COLUMNS = 100
    const val MAX_CELLS = 50_000
    const val MAX_CELL_CHARS = 32_768
    const val MAX_PDF_PAGES = 300
    val inputMimes = listOf("text/plain", "text/csv", "text/tab-separated-values", "text/markdown") + OfficeFormat.entries.map { it.mime }
    fun targets(kind: DocumentKind) = if (kind == DocumentKind.TEXT)
        listOf(DocumentTarget.TXT, DocumentTarget.PDF, DocumentTarget.DOCX) else
        listOf(DocumentTarget.TXT, DocumentTarget.CSV, DocumentTarget.TSV, DocumentTarget.PDF, DocumentTarget.XLSX)
}

data class DocumentContent(val text: String, val kind: DocumentKind, val encoding: String,
    val delimiter: Char? = null, val rows: List<List<String>> = emptyList(), val office: OfficeDocument? = null) {
    val columns get() = rows.maxOfOrNull { it.size } ?: 0
}

/** Bounded, strict text decoding and quoted delimited records. Never executes formulas or markup. */
object TextDocumentCodec {
    fun inferKind(name: String, mime: String?): DocumentKind = when {
        name.endsWith(".tsv", true) || mime == "text/tab-separated-values" -> DocumentKind.TSV
        name.endsWith(".csv", true) || mime == "text/csv" || mime == "application/csv" -> DocumentKind.CSV
        else -> DocumentKind.TEXT
    }

    fun decode(bytes: ByteArray, encoding: DocumentEncoding): Pair<String, String> {
        if (bytes.isEmpty()) throw DocumentException(DocumentIssue.INVALID)
        if (bytes.size > DocumentPolicy.MAX_BYTES) throw DocumentException(DocumentIssue.LIMIT)
        val bom = when {
            bytes.size >= 4 && ((bytes[0] == 0.toByte() && bytes[1] == 0.toByte() && bytes[2] == 0xfe.toByte() && bytes[3] == 0xff.toByte()) ||
                (bytes[0] == 0xff.toByte() && bytes[1] == 0xfe.toByte() && bytes[2] == 0.toByte() && bytes[3] == 0.toByte())) ->
                throw DocumentException(DocumentIssue.ENCODING)
            bytes.size >= 3 && bytes[0] == 0xef.toByte() && bytes[1] == 0xbb.toByte() && bytes[2] == 0xbf.toByte() -> "UTF-8" to 3
            bytes.size >= 2 && bytes[0] == 0xff.toByte() && bytes[1] == 0xfe.toByte() -> "UTF-16LE" to 2
            bytes.size >= 2 && bytes[0] == 0xfe.toByte() && bytes[1] == 0xff.toByte() -> "UTF-16BE" to 2
            else -> null
        }
        if (encoding != DocumentEncoding.AUTO && bom != null && bom.first != encoding.charsetName)
            throw DocumentException(DocumentIssue.ENCODING)
        val charset = bom?.first ?: encoding.charsetName ?: "UTF-8"
        val skip = bom?.second ?: 0
        val decoded = try {
            Charset.forName(charset).newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes, skip, bytes.size - skip)).toString()
        } catch (_: java.nio.charset.CharacterCodingException) { throw DocumentException(DocumentIssue.ENCODING) }
        if (decoded.length > DocumentPolicy.MAX_CHARS) throw DocumentException(DocumentIssue.LIMIT)
        if (decoded.isEmpty() || decoded.any { it < ' ' && it !in "\r\n\t\u000c" } ||
            decoded.startsWith("%PDF-") || decoded.startsWith("PK\u0003\u0004")) throw DocumentException(DocumentIssue.INVALID)
        return decoded to charset
    }

    fun parse(bytes: ByteArray, kind: DocumentKind, encoding: DocumentEncoding = DocumentEncoding.AUTO,
        delimiter: Char? = null, checkActive: () -> Unit = {}): DocumentContent {
        val (text, charset) = decode(bytes, encoding)
        if (kind == DocumentKind.TEXT) return DocumentContent(text, kind, charset)
        val separator = if (kind == DocumentKind.TSV) '\t' else delimiter ?: detectDelimiter(text)
        require(separator in listOf(',', ';', '\t'))
        return DocumentContent(text, kind, charset, separator, parseRows(text, separator, checkActive))
    }

    fun parseRows(text: String, separator: Char, checkActive: () -> Unit = {}): List<List<String>> {
        if (text.length > DocumentPolicy.MAX_CHARS) throw DocumentException(DocumentIssue.LIMIT)
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var state = 0 // 0: start, 1: unquoted, 2: quoted, 3: after closing quote
        var cells = 0
        var endedRecord = false
        fun finishCell() {
            if (row.size >= DocumentPolicy.MAX_COLUMNS || ++cells > DocumentPolicy.MAX_CELLS)
                throw DocumentException(DocumentIssue.LIMIT)
            row.add(cell.toString()); cell.setLength(0); state = 0
        }
        fun finishRow() {
            finishCell()
            if (rows.size >= DocumentPolicy.MAX_ROWS) throw DocumentException(DocumentIssue.LIMIT)
            rows.add(row); row = mutableListOf(); endedRecord = true
        }
        var i = 0
        while (i < text.length) {
            if (i % 4096 == 0) checkActive()
            val ch = text[i]
            endedRecord = false
            if (state == 2) {
                if (ch == '"') state = 3 else cell.append(ch)
            } else if (state == 3 && ch == '"') { cell.append('"'); state = 2 }
            else if (ch == separator) finishCell()
            else if (ch == '\r' || ch == '\n') {
                finishRow()
                if (ch == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
            } else if (state == 3 || (ch == '"' && state == 1)) throw DocumentException(DocumentIssue.INVALID)
            else if (ch == '"') state = 2
            else { cell.append(ch); state = 1 }
            if (cell.length > DocumentPolicy.MAX_CELL_CHARS) throw DocumentException(DocumentIssue.LIMIT)
            i++
        }
        checkActive()
        if (state == 2) throw DocumentException(DocumentIssue.INVALID)
        if (!endedRecord || rows.isEmpty()) finishRow()
        return rows
    }

    private fun detectDelimiter(text: String): Char {
        val separators = listOf(',', ';', '\t')
        val counts = separators.associateWith { mutableListOf<Int>() }
        val current = IntArray(3)
        var quoted = false
        var i = 0
        while (i < text.length && counts.getValue(',').size < 64) {
            val ch = text[i]
            if (ch == '"') {
                if (quoted && i + 1 < text.length && text[i + 1] == '"') i++ else quoted = !quoted
            } else if (!quoted) {
                val index = separators.indexOf(ch)
                if (index >= 0) current[index]++
                if (ch == '\n' || ch == '\r') {
                    separators.forEachIndexed { j, separator -> counts.getValue(separator).add(current[j]); current[j] = 0 }
                    if (ch == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                }
            }
            i++
        }
        if (i == text.length && text.lastOrNull() !in listOf('\n', '\r'))
            separators.forEachIndexed { j, separator -> counts.getValue(separator).add(current[j]) }
        return separators.maxByOrNull { separator ->
            val values = counts.getValue(separator)
            val most = values.filter { it > 0 }.groupingBy { it }.eachCount().maxByOrNull { it.value }
            if (most == null) 0 else most.value * 10000 / maxOf(1, values.size) + most.key
        } ?: ','
    }

    fun write(content: DocumentContent, target: DocumentTarget, writer: Writer, checkActive: () -> Unit = {}) {
        require(target in DocumentPolicy.targets(content.kind) && target !in listOf(DocumentTarget.PDF, DocumentTarget.DOCX, DocumentTarget.XLSX))
        if (content.kind == DocumentKind.TEXT) {
            chunks(content.text).forEach { checkActive(); writer.write(it) }
        } else {
            val separator = if (target == DocumentTarget.CSV) ',' else '\t'
            content.rows.forEach { row ->
                checkActive()
                row.forEachIndexed { index, value ->
                    if (index > 0) writer.write(separator.code)
                    val quote = value.isEmpty() || value.any { it == separator || it == '"' || it == '\n' || it == '\r' }
                    if (quote) writer.write('"'.code)
                    chunks(value).forEach { chunk -> checkActive(); writer.write(if (quote) chunk.replace("\"", "\"\"") else chunk) }
                    if (quote) writer.write('"'.code)
                }
                writer.write("\r\n")
            }
        }
    }

    /** Bounded layout chunks; surrogate pairs stay intact. Full exports use all chunks. */
    fun chunks(text: String, maxChars: Int = 2048): List<String> {
        require(maxChars >= 2)
        val result = ArrayList<String>()
        var start = 0
        while (start < text.length) {
            var end = minOf(start + maxChars, text.length)
            if (end < text.length && text[end - 1].isHighSurrogate() && text[end].isLowSurrogate()) end--
            result.add(text.substring(start, end)); start = end
        }
        return result
    }
}

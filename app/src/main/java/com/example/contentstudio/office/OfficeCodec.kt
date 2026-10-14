package com.example.contentstudio.office

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.Closeable
import java.io.File
import java.net.URI
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipException
import java.util.zip.ZipOutputStream

/** Local, bounded OPC reader/writer. No extraction, network resolver or formula evaluation. */
object OfficeLimits {
    const val COMPRESSED = 4L * 1024 * 1024
    const val PART = 1024 * 1024
    const val EXPANDED = 8L * 1024 * 1024
    const val ENTRIES = 256
    const val NODES = 30000
    const val DEPTH = 64
    const val CELLS = 5000
    const val ROWS = 500
    const val COLUMNS = 100
    const val TEXT = 32768
}

internal const val W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
internal const val S = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
internal const val R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
private const val REL = "http://schemas.openxmlformats.org/package/2006/relationships"
private const val CT = "http://schemas.openxmlformats.org/package/2006/content-types"

internal class OfficeRejected(message: String) : IllegalArgumentException(message)
private fun gate(ok: Boolean, reason: String) { if (!ok) throw OfficeRejected(reason) }

internal data class OfficeNode(val ns: String, val name: String,
    val attrs: Map<Pair<String, String>, String>, val children: MutableList<OfficeNode> = mutableListOf(),
    val text: StringBuilder = StringBuilder()) {
    fun attr(name: String, ns: String = "") = attrs[ns to name]
    fun child(ns: String, name: String) = children.firstOrNull { it.ns == ns && it.name == name }
    fun all(): Sequence<OfficeNode> = sequence { yield(this@OfficeNode); for (c in children) yieldAll(c.all()) }
}

/** No disk extraction or external resolver. Actual expanded bytes/CRC and XML nodes are bounded. */
internal class OfficePackage(file: File, private val active: () -> Unit = {}) : Closeable {
    private val zip: ZipFile
    val names: Set<String>
    val expandedBytes: Long
    private val nodes = mutableMapOf<String, OfficeNode>()
    private var parsedNodes = 0

    init {
        gate(file.length() in 1..OfficeLimits.COMPRESSED, "compressed limit or empty")
        zip = try { ZipFile(file) } catch (error: ZipException) {
            throw OfficeRejected("invalid ZIP package").apply { initCause(error) }
        }
        try {
            val entries = zip.entries().toList()
            gate(entries.size <= OfficeLimits.ENTRIES, "entry limit")
            val seen = mutableSetOf<String>()
            var expanded = 0L
            for (entry in entries) {
                active()
                val name = entry.name
                gate(name.isNotEmpty() && !name.startsWith('/') && '\\' !in name && ':' !in name && '%' !in name && "//" !in name &&
                    name.split('/').none { it == "." || it == ".." } && seen.add(name), "ambiguous package path")
                if (entry.isDirectory) continue
                gate(entry.method == ZipEntry.STORED || entry.method == ZipEntry.DEFLATED, "unsupported ZIP method")
                gate(entry.size in 0..OfficeLimits.PART.toLong(), "part limit")
                gate(!name.contains("vbaProject", true) && !name.startsWith("_xmlsignatures/"), "active or signed package")
                var bytes = 0L
                val crc = CRC32()
                zip.getInputStream(entry).use { input ->
                    val buffer = ByteArray(16384)
                    while (true) {
                        active()
                        val count = input.read(buffer)
                        if (count < 0) break
                        bytes += count; expanded += count
                        gate(bytes <= OfficeLimits.PART && expanded <= OfficeLimits.EXPANDED, "expanded limit")
                        crc.update(buffer, 0, count)
                    }
                }
                gate(bytes == entry.size && crc.value == entry.crc, "corrupt package part")
            }
            names = seen
            expandedBytes = expanded
        } catch (error: Throwable) { zip.close(); throw error }
    }

    fun xml(name: String): OfficeNode = nodes.getOrPut(name) {
        active()
        val entry = zip.getEntry(name) ?: throw OfficeRejected("missing $name")
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
        }
        zip.getInputStream(entry).use { input ->
            parser.setInput(input, null)
            val stack = mutableListOf<OfficeNode>()
            var root: OfficeNode? = null
            while (true) {
                active()
                when (parser.nextToken()) {
                    XmlPullParser.START_TAG -> {
                        gate(++parsedNodes <= OfficeLimits.NODES && stack.size < OfficeLimits.DEPTH, "XML complexity limit")
                        val attrs = (0 until parser.attributeCount).associate {
                            (parser.getAttributeNamespace(it).orEmpty() to parser.getAttributeName(it)) to parser.getAttributeValue(it)
                        }
                        val node = OfficeNode(parser.namespace.orEmpty(), parser.name, attrs)
                        if (stack.isEmpty()) { gate(root == null, "multiple roots"); root = node }
                        else stack.last().children.add(node)
                        stack.add(node)
                    }
                    XmlPullParser.END_TAG -> { gate(stack.isNotEmpty(), "invalid XML"); stack.removeAt(stack.lastIndex) }
                    XmlPullParser.TEXT, XmlPullParser.CDSECT, XmlPullParser.IGNORABLE_WHITESPACE -> {
                        stack.lastOrNull()?.text?.append(parser.text)
                    }
                    XmlPullParser.ENTITY_REF -> {
                        gate(parser.text != null && (parser.name in listOf("amp", "lt", "gt", "quot", "apos") || parser.name.startsWith('#')),
                            "unresolved entity")
                        stack.lastOrNull()?.text?.append(parser.text)
                    }
                    XmlPullParser.DOCDECL, XmlPullParser.PROCESSING_INSTRUCTION -> throw OfficeRejected("DTD or processing instruction")
                    XmlPullParser.END_DOCUMENT -> break
                }
            }
            gate(stack.isEmpty(), "unclosed XML")
            root ?: throw OfficeRejected("empty XML")
        }
    }

    fun release(name: String) { nodes.remove(name) }

    fun relationships(part: String): List<OfficeNode> {
        val relPath = if (part.isEmpty()) "_rels/.rels" else
            part.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" } +
                "_rels/" + part.substringAfterLast('/') + ".rels"
        if (relPath !in names) return emptyList()
        val root = xml(relPath)
        gate(root.ns == REL && root.name == "Relationships", "invalid relationships")
        val items = root.children.filter { it.ns == REL && it.name == "Relationship" }
        gate(items.map { it.attr("Id") }.distinct().size == items.size, "duplicate relationship")
        return items
    }

    fun resolve(part: String, rel: OfficeNode): String {
        gate(rel.attr("TargetMode") != "External", "external content")
        val uri = URI(rel.attr("Target") ?: throw OfficeRejected("missing target"))
        gate(!uri.isAbsolute && uri.rawAuthority == null && uri.query == null && uri.fragment == null &&
            '%' !in uri.rawPath && '\\' !in uri.rawPath, "invalid target")
        val combined = if (uri.path.startsWith('/')) uri.path.drop(1)
            else part.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" } + uri.path
        val path = mutableListOf<String>()
        for (segment in combined.split('/')) when (segment) {
            "", "." -> Unit
            ".." -> { gate(path.isNotEmpty(), "target outside package"); path.removeAt(path.lastIndex) }
            else -> path.add(segment)
        }
        return path.joinToString("/").also { gate(it in names, "missing target") }
    }

    fun main(contentType: String): String {
        val types = xml("[Content_Types].xml")
        gate(types.ns == CT && types.name == "Types", "invalid content types")
        gate(types.children.filter { it.name == "Override" }.map { it.attr("PartName") }.let { it.size == it.distinct().size },
            "duplicate content type")
        gate(types.children.filter { it.name == "Default" }.map { it.attr("Extension")?.lowercase() }
            .let { it.size == it.distinct().size }, "duplicate default content type")
        gate(types.children.none { it.attr("ContentType").orEmpty().contains("macroEnabled", true) }, "macro package")
        val main = relationships("").singleOrNull { it.attr("Type") == "$R/officeDocument" }
            ?: throw OfficeRejected("missing or ambiguous main part")
        val name = resolve("", main)
        val declared = types.children.singleOrNull { it.ns == CT && it.name == "Override" && it.attr("PartName") == "/$name" }
            ?: types.children.singleOrNull { it.ns == CT && it.name == "Default" &&
                it.attr("Extension").equals(name.substringAfterLast('.'), true) }
        gate(declared?.attr("ContentType") == contentType, "main content mismatch")
        return name
    }

    override fun close() = zip.close()
}

sealed interface WordBlock {
    data class Paragraph(val text: String) : WordBlock
    data class Table(val rows: List<List<String>>) : WordBlock
}
data class WordSnapshot(val blocks: List<WordBlock>, val losses: Set<String>, val expanded: Long)
data class OfficeCell(val address: String, val kind: String, val value: String?, val formula: String?, val style: Int,
    val numberFormat: String?)
data class OfficeSheet(val name: String, val cells: List<OfficeCell>, val merges: List<String>)
data class SheetSnapshot(val sheets: List<OfficeSheet>, val losses: Set<String>, val expanded: Long,
    val date1904: Boolean)

object OfficeCodec {
    private val CELL_ADDRESS = Regex("([A-Z]+)([1-9][0-9]*)")
    private const val DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"
    private const val XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"

    fun readWord(file: File, active: () -> Unit = {}): WordSnapshot = OfficePackage(file, active).use { pack ->
        val main = pack.main(DOCX)
        val root = pack.xml(main)
        gate(root.ns == W && root.name == "document", "wrong Word namespace")
        val body = root.child(W, "body") ?: throw OfficeRejected("no body")
        val losses = mutableSetOf<String>()
        if (pack.names.any { it.startsWith("word/header") || it.startsWith("word/footer") }) losses.add("headers_footers")
        if (pack.names.any { it.startsWith("word/media/") }) losses.add("images")
        if (pack.names.any { it.startsWith("word/footnotes") || it.startsWith("word/endnotes") }) losses.add("notes")
        if (pack.names.any { it.startsWith("word/styles") }) losses.add("style_inheritance")
        if (pack.relationships(main).any { it.attr("TargetMode") == "External" }) losses.add("external_links_not_opened")
        val flags = mapOf("rPr" to "run_formatting", "pPr" to "paragraph_formatting", "drawing" to "images",
            "hyperlink" to "hyperlinks", "numPr" to "numbering", "fldChar" to "fields", "instrText" to "fields",
            "ins" to "tracked_changes", "del" to "tracked_changes", "gridSpan" to "merged_table_cells",
            "vMerge" to "merged_table_cells", "sdt" to "content_controls", "altChunk" to "embedded_content")
        for (node in body.all()) {
            active()
            if (node.ns == W) flags[node.name]?.let(losses::add)
        }
        fun paragraph(p: OfficeNode): String = buildString {
            for (node in p.all()) if (node.ns == W) when (node.name) {
                "t" -> append(node.text)
                "tab" -> append('\t')
                "br", "cr" -> append('\n')
            }
        }.also { gate(it.length <= OfficeLimits.TEXT, "paragraph limit") }
        val blocks = body.children.mapNotNull { node ->
            active()
            when {
                node.ns == W && node.name == "p" -> WordBlock.Paragraph(paragraph(node))
                node.ns == W && node.name == "tbl" -> WordBlock.Table(node.children.filter { it.ns == W && it.name == "tr" }.map { row ->
                    row.children.filter { it.ns == W && it.name == "tc" }.map { cell ->
                        cell.children.filter { it.ns == W && it.name == "p" }.joinToString("\n") { paragraph(it) }
                    }
                })
                node.ns == W && node.name == "sectPr" -> { losses.add("page_layout"); null }
                else -> { losses.add("unsupported_body_block"); null }
            }
        }
        gate(blocks.size <= OfficeLimits.ROWS, "document block limit")
        var cells = 0
        var chars = 0L
        blocks.forEach { block ->
            active()
            when (block) {
                is WordBlock.Paragraph -> chars += block.text.length
                is WordBlock.Table -> {
                    gate(block.rows.size <= OfficeLimits.ROWS && block.rows.all { it.size <= OfficeLimits.COLUMNS }, "table limit")
                    block.rows.forEach { row -> row.forEach { value ->
                        gate(++cells <= OfficeLimits.CELLS && value.length <= OfficeLimits.TEXT, "table cell limit")
                        chars += value.length
                    } }
                }
            }
            gate(chars <= 2_000_000, "document text limit")
        }
        WordSnapshot(blocks, losses, pack.expandedBytes)
    }

    fun readSheet(file: File, active: () -> Unit = {}): SheetSnapshot = OfficePackage(file, active).use { pack ->
        val main = pack.main(XLSX)
        val root = pack.xml(main)
        gate(root.ns == S && root.name == "workbook", "wrong workbook namespace")
        val rels = pack.relationships(main)
        val losses = mutableSetOf<String>()
        if (pack.names.any { it.startsWith("xl/styles") }) losses.add("styles_number_formats")
        if (pack.names.any { it.startsWith("xl/drawings/") || it.startsWith("xl/charts/") }) losses.add("drawings_charts")
        if (pack.names.any { it.startsWith("xl/featurePropertyBag/") }) losses.add("unsupported_extensions")
        if (pack.names.any { it.startsWith("xl/externalLinks/") } || rels.any { it.attr("TargetMode") == "External" })
            losses.add("external_links_not_opened")
        val sharedRel = rels.singleOrNull { it.attr("Type") == "$R/sharedStrings" }
        val stylesRel = rels.singleOrNull { it.attr("Type") == "$R/styles" }
        val styles = stylesRel?.let { pack.xml(pack.resolve(main, it)) }
        gate(styles == null || styles.ns == S && styles.name == "styleSheet", "invalid styles part")
        val formats = styles?.child(S, "numFmts")?.children.orEmpty().associate {
            it.attr("numFmtId") to it.attr("formatCode")
        }
        val styleFormats = styles?.child(S, "cellXfs")?.children.orEmpty().map {
            val id = it.attr("numFmtId") ?: "0"
            formats[id] ?: when (id) { "0" -> "General"; "49" -> "@"; else -> "built-in:$id" }
        }
        val shared = sharedRel?.let {
            val xml = pack.xml(pack.resolve(main, it))
            gate(xml.ns == S && xml.name == "sst", "invalid shared strings part")
            xml.children.filter { it.ns == S && it.name == "si" }
            .map { si -> si.all().filter { it.ns == S && it.name == "t" }.joinToString("") { it.text.toString() } } }.orEmpty()
        gate(shared.size <= OfficeLimits.CELLS && shared.all { it.length <= OfficeLimits.TEXT }, "shared string limit")
        val definitions = root.child(S, "sheets")?.children?.filter { it.ns == S && it.name == "sheet" }.orEmpty()
        gate(definitions.size in 1..16, "sheet limit")
        gate(definitions.map { it.attr("name") }.distinct().size == definitions.size, "duplicate sheet name")
        gate(definitions.all { it.attr("name")?.let { name -> name.isNotEmpty() && name.length <= 31 && name.none(Char::isISOControl) } == true }, "invalid sheet name")
        var count = 0
        val sheets = definitions.map { sheet ->
            active()
            val rel = rels.singleOrNull { it.attr("Id") == sheet.attr("id", R) && it.attr("Type") == "$R/worksheet" }
                ?: throw OfficeRejected("missing worksheet")
            val xml = pack.xml(pack.resolve(main, rel))
            gate(xml.ns == S && xml.name == "worksheet", "wrong worksheet namespace")
            val seen = mutableSetOf<String>()
            val cells = xml.child(S, "sheetData")?.children.orEmpty().flatMap { row ->
                row.children.filter { it.ns == S && it.name == "c" }.map { cell ->
                    active()
                    gate(++count <= OfficeLimits.CELLS, "cell limit")
                    val address = cell.attr("r") ?: throw OfficeRejected("missing cell address")
                    coordinates(address)
                    gate(seen.add(address), "duplicate cell address")
                    val formula = cell.child(S, "f")?.text?.toString()
                    gate(formula == null || formula.length <= OfficeLimits.TEXT, "formula text limit")
                    // Shared/array/data-table formulas require expansion semantics, not a fabricated '=' string.
                    gate(cell.child(S, "f")?.attr("t") !in listOf("shared", "array", "dataTable"), "unsupported complex formula")
                    if (formula != null) losses.add("formulas_not_calculated")
                    if (cell.child(S, "f")?.attr("t") in listOf("shared", "array", "dataTable")) losses.add("complex_formulas")
                    val raw = cell.child(S, "v")?.text?.toString()?.takeUnless { it.isEmpty() }
                    val kind = cell.attr("t") ?: "n"
                    val value = when (kind) {
                        "inlineStr" -> cell.child(S, "is")?.all()?.filter { it.ns == S && it.name == "t" }?.joinToString("") { it.text.toString() }.orEmpty()
                        "s" -> shared.getOrNull(raw?.toIntOrNull() ?: -1) ?: throw OfficeRejected("bad shared string index")
                        "n", "b", "str", "e", "d" -> raw
                        else -> throw OfficeRejected("unknown cell type")
                    }
                    gate(value == null || value.length <= OfficeLimits.TEXT, "cell text limit")
                    val style = cell.attr("s")?.let { it.toIntOrNull() ?: throw OfficeRejected("invalid cell style") } ?: 0
                    gate(style >= 0 && (style == 0 && styleFormats.isEmpty() || style < styleFormats.size), "invalid cell style")
                    OfficeCell(address, kind, value, formula, style, styleFormats.getOrNull(style))
                }
            }
            val merges = xml.child(S, "mergeCells")?.children?.mapNotNull { it.attr("ref") }.orEmpty()
            if (merges.isNotEmpty()) losses.add("merged_cells")
            if (xml.all().any { it.ns == S && it.name in listOf("conditionalFormatting", "dataValidations", "autoFilter", "hyperlinks") })
                losses.add("sheet_features")
            if (xml.children.any { it.name !in listOf("sheetData", "mergeCells", "dimension") }) losses.add("sheet_layout_or_features")
            pack.release(pack.resolve(main, rel))
            OfficeSheet(sheet.attr("name") ?: throw OfficeRejected("missing sheet name"), cells, merges)
        }
        SheetSnapshot(sheets, losses, pack.expandedBytes, root.child(S, "workbookPr")?.attr("date1904") in listOf("1", "true"))
    }

    fun coordinates(address: String): Pair<Int, Int> {
        val match = CELL_ADDRESS.matchEntire(address) ?: throw OfficeRejected("invalid cell address")
        var column = 0
        for (letter in match.groupValues[1]) {
            column = column * 26 + (letter - 'A') + 1
            gate(column <= OfficeLimits.COLUMNS, "column limit")
        }
        val row = match.groupValues[2].toIntOrNull() ?: throw OfficeRejected("row limit")
        gate(row <= OfficeLimits.ROWS, "row limit")
        return row to column
    }

    fun textGrid(book: SheetSnapshot, sheet: Int, acknowledged: Set<String>): List<List<String>> {
        gate(acknowledged.containsAll(book.losses), "unacknowledged conversion losses")
        val cells = book.sheets[sheet].cells
        val coords = cells.associate { coordinates(it.address) to (it.formula?.let { f -> "=$f" } ?: it.value.orEmpty()) }
        val rows = coords.keys.maxOfOrNull { it.first } ?: 0
        val cols = coords.keys.maxOfOrNull { it.second } ?: 0
        gate(rows * cols <= OfficeLimits.CELLS, "dense grid limit")
        return (1..rows).map { r -> (1..cols).map { c -> coords[r to c].orEmpty() } }
    }

    fun writeWord(folder: File, snapshot: WordSnapshot, acknowledged: Set<String> = emptySet(), active: () -> Unit = {}): File {
        gate(acknowledged.containsAll(snapshot.losses), "unacknowledged conversion losses")
        gate(snapshot.blocks.size <= OfficeLimits.ROWS && snapshot.blocks.filterIsInstance<WordBlock.Table>().all {
            it.rows.size <= OfficeLimits.ROWS && it.rows.all { row -> row.size <= OfficeLimits.COLUMNS } &&
                it.rows.sumOf { row -> row.size } <= OfficeLimits.CELLS
        }, "document block limit")
        fun paragraph(text: String): String {
            gate(text.length <= OfficeLimits.TEXT, "paragraph limit")
            val pieces = text.split('\n').map { line -> line.split('\t').joinToString("<w:tab/>") { "<w:t xml:space=\"preserve\">${escape(it)}</w:t>" } }
            return "<w:p><w:r>${pieces.joinToString("<w:br/>")}</w:r></w:p>"
        }
        val body = snapshot.blocks.joinToString("") { block ->
            active()
            when (block) {
                is WordBlock.Paragraph -> paragraph(block.text)
                is WordBlock.Table -> "<w:tbl><w:tblPr/><w:tblGrid/>" + block.rows.joinToString("") { row ->
                    "<w:tr>" + row.joinToString("") { "<w:tc><w:tcPr/>${paragraph(it)}</w:tc>" } + "</w:tr>"
                } + "</w:tbl>"
            }
        }
        return write(folder, "docx", linkedMapOf(
            "[Content_Types].xml" to contentTypes("/word/document.xml", DOCX),
            "_rels/.rels" to relationships("word/document.xml"),
            "word/document.xml" to "<w:document xmlns:w=\"$W\"><w:body>$body</w:body></w:document>"), active)
    }

    /** Text values, including '=' and leading zeroes, always stay inline strings, never formulas. */
    fun writeTextGrid(folder: File, rows: List<List<String>>, active: () -> Unit = {}): File {
        gate(rows.size <= OfficeLimits.ROWS && rows.all { it.size <= OfficeLimits.COLUMNS } &&
            rows.sumOf { it.size } <= OfficeLimits.CELLS, "grid limit")
        fun letters(column: Int): String {
            var n = column; var text = ""
            while (n > 0) { n--; text = ('A' + n % 26) + text; n /= 26 }
            return text
        }
        val body = rows.mapIndexed { r, row ->
            active()
            "<row r=\"${r+1}\" ht=\"45\" customHeight=\"1\">" + row.mapIndexed { c, value ->
                gate(value.length <= OfficeLimits.TEXT, "cell text limit")
                "<c r=\"${letters(c+1)}${r+1}\" s=\"1\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${escape(value)}</t></is></c>"
            }.joinToString("") + "</row>"
        }.joinToString("")
        return write(folder, "xlsx", linkedMapOf(
            "[Content_Types].xml" to contentTypes("/xl/workbook.xml", XLSX,
                "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
                    "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>"),
            "_rels/.rels" to relationships("xl/workbook.xml"),
            "xl/workbook.xml" to "<workbook xmlns=\"$S\" xmlns:r=\"$R\"><sheets><sheet name=\"Data\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>",
            "xl/_rels/workbook.xml.rels" to relationships("worksheets/sheet1.xml", "worksheet").replace("</Relationships>",
                "<Relationship Id=\"rId2\" Type=\"$R/styles\" Target=\"styles.xml\"/></Relationships>"),
            "xl/styles.xml" to "<styleSheet xmlns=\"$S\"><numFmts count=\"1\"><numFmt numFmtId=\"164\" formatCode=\"@\"/></numFmts>" +
                "<fonts count=\"1\"><font><sz val=\"11\"/><name val=\"Arial\"/></font></fonts>" +
                "<fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill></fills>" +
                "<borders count=\"1\"><border/></borders><cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
                "<cellXfs count=\"2\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>" +
                "<xf numFmtId=\"164\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\" applyAlignment=\"1\">" +
                "<alignment horizontal=\"left\" vertical=\"top\" wrapText=\"1\"/></xf></cellXfs>" +
                "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles></styleSheet>",
            "xl/worksheets/sheet1.xml" to "<worksheet xmlns=\"$S\"><cols><col min=\"1\" max=\"${maxOf(1, rows.maxOfOrNull { it.size } ?: 1)}\" " +
                "width=\"26\" customWidth=\"1\"/></cols><sheetData>$body</sheetData></worksheet>"), active)
    }

    private fun write(folder: File, extension: String, parts: Map<String, String>, active: () -> Unit): File {
        folder.mkdirs()
        val partial = File.createTempFile("office-", ".part", folder)
        val result = File(partial.path.removeSuffix(".part") + ".$extension")
        try {
            partial.outputStream().use { disk ->
                ZipOutputStream(disk).use { zip ->
                    for ((name, value) in parts) {
                        active()
                        val bytes = ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + value).toByteArray()
                        gate(bytes.size <= OfficeLimits.PART, "output part limit")
                        zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
                    }
                    zip.finish(); zip.flush(); disk.fd.sync()
                }
            }
            active()
            gate(!result.exists() && partial.renameTo(result), "atomic output failed")
            return result
        } catch (error: Throwable) { partial.delete(); throw error }
    }

    private fun escape(value: String): String {
        gate(value.codePoints().allMatch { it == 9 || it == 10 || it == 13 || it in 32..0xD7FF ||
            it in 0xE000..0xFFFD || it in 0x10000..0x10FFFF }, "invalid XML character")
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\r", "&#13;")
    }
    private fun contentTypes(main: String, type: String, extra: String = "") =
        "<Types xmlns=\"$CT\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
            "<Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"$main\" ContentType=\"$type\"/>$extra</Types>"
    private fun relationships(target: String, type: String = "officeDocument") =
        "<Relationships xmlns=\"$REL\"><Relationship Id=\"rId1\" Type=\"$R/$type\" Target=\"$target\"/></Relationships>"
}

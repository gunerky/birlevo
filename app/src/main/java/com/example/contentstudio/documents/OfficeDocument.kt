package com.example.contentstudio.documents

import com.example.contentstudio.office.*
import java.io.File
import kotlinx.coroutines.CancellationException

enum class OfficeFormat(val mime: String) {
    DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    companion object {
        fun detect(name: String, mime: String? = null): OfficeFormat? {
            val named = entries.firstOrNull { name.endsWith(".${it.name}", true) }
            val typed = entries.firstOrNull { it.mime.equals(mime?.substringBefore(';'), true) }
            if (named != null && typed != null && named != typed) throw DocumentException(DocumentIssue.OFFICE)
            return named ?: typed
        }
    }
}

enum class OfficeCellMode { STORED_VALUES, FORMULAS }

/** Source snapshots retain sparse cells and metadata; output is an explicitly approved plain copy. */
data class OfficeDocument(val word: WordSnapshot? = null, val workbook: SheetSnapshot? = null,
    val sheet: Int = 0, val mode: OfficeCellMode = OfficeCellMode.STORED_VALUES) {
    private val selectedCells = workbook?.sheets?.getOrNull(sheet)?.cells.orEmpty()
        .associateBy { OfficeCodec.coordinates(it.address) }
    fun cellAt(row: Int, column: Int) = selectedCells[row to column]
    val missingResults = workbook?.sheets?.getOrNull(sheet)?.cells.orEmpty()
        .filter { it.formula != null && it.value == null }.map { it.address }

    fun content(checkActive: () -> Unit = {}): DocumentContent {
        word?.let { source ->
            val text = source.blocks.joinToString("\n") { block ->
                checkActive()
                when (block) {
                    is WordBlock.Paragraph -> block.text
                    is WordBlock.Table -> block.rows.joinToString("\n") { it.joinToString("\t") }
                }
            }
            if (text.length > DocumentPolicy.MAX_CHARS) throw DocumentException(DocumentIssue.OFFICE_LIMIT)
            return DocumentContent(text, DocumentKind.TEXT, "DOCX", office = this)
        }
        val book = checkNotNull(workbook)
        if (book.sheets.getOrNull(sheet) == null) throw DocumentException(DocumentIssue.OFFICE)
        val cells = selectedCells.mapValues { (_, cell) ->
            checkActive()
            if (mode == OfficeCellMode.FORMULAS && cell.formula != null) "=${cell.formula}" else cell.value.orEmpty()
        }
        val height = cells.keys.maxOfOrNull { it.first } ?: 0
        val width = cells.keys.maxOfOrNull { it.second } ?: 0
        if (height * width > DocumentPolicy.MAX_CELLS) throw DocumentException(DocumentIssue.OFFICE_LIMIT)
        val rows = (1..height).map { row ->
            checkActive()
            (1..width).map { col -> cells[row to col].orEmpty() }
        }
        return DocumentContent("", DocumentKind.CSV, "XLSX", rows = rows, office = this)
    }
}

internal object OfficeDocuments {
    fun <T> checked(block: () -> T): T = try { block() }
    catch (cancelled: CancellationException) { throw cancelled }
    catch (error: OfficeRejected) {
        throw DocumentException(if (error.message.orEmpty().contains("limit")) DocumentIssue.OFFICE_LIMIT else DocumentIssue.OFFICE)
    } catch (error: org.xmlpull.v1.XmlPullParserException) { throw DocumentException(DocumentIssue.OFFICE) }
    catch (error: java.net.URISyntaxException) { throw DocumentException(DocumentIssue.OFFICE) }
    catch (error: java.io.IOException) { throw DocumentException(DocumentIssue.OFFICE) }

    fun read(file: File, format: OfficeFormat, sheet: Int, mode: OfficeCellMode, active: () -> Unit): DocumentContent = checked {
        when (format) {
            OfficeFormat.DOCX -> OfficeDocument(word = OfficeCodec.readWord(file, active))
            OfficeFormat.XLSX -> OfficeDocument(workbook = OfficeCodec.readSheet(file, active), sheet = sheet, mode = mode)
        }.content(active)
    }

    fun write(folder: File, content: DocumentContent, target: DocumentTarget, active: () -> Unit): File = checked {
        when (target) {
            DocumentTarget.DOCX -> {
                val word = content.office?.word ?: WordSnapshot(
                    content.text.split('\n').map { WordBlock.Paragraph(it) }, emptySet(), 0)
                OfficeCodec.writeWord(folder, word, word.losses, active)
            }
            DocumentTarget.XLSX -> OfficeCodec.writeTextGrid(folder, content.rows, active)
            else -> error("Not an Office output")
        }
    }
}

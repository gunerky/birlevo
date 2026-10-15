package com.example.contentstudio.batch

import com.example.contentstudio.documents.*
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

enum class BatchDocumentIssue { TARGET, SHEET, CONSENT, ACCESS, ENCODING, INVALID, LIMIT, OUTPUT_LIMIT, PDF_LIMIT, EXPORT, MEMORY, OFFICE, OFFICE_LIMIT }
internal class BatchDocumentFailure(val issue: BatchDocumentIssue) : IllegalArgumentException(issue.name)

/** Bounded input facts, never text/cells or approval. Multi-sheet workbooks require a choice. */
data class BatchDocument(val kind: DocumentKind, val office: OfficeFormat? = null,
    val sheets: List<String> = emptyList(), val missing: List<Int> = emptyList(),
    val date1904: Boolean = false, val sheet: Int = 0, val mode: OfficeCellMode = OfficeCellMode.STORED_VALUES) {
    fun json(): JSONObject = JSONObject().put("kind", kind.name).put("office", office?.name ?: "")
        .put("sheets", JSONArray(sheets)).put("missing", JSONArray(missing)).put("date1904", date1904)
        .put("sheet", sheet).put("mode", mode.name)
    fun key(item: BatchItem, options: BatchOptions): String = MessageDigest.getInstance("SHA-256")
        .digest(listOf(item.id, item.source, item.bytes.toString(), json().toString(), options.documentTarget.name,
            options.documentEncoding.name, options.documentDelimiter?.code.toString()).joinToString("\n").toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    companion object {
        fun from(content: DocumentContent): BatchDocument {
            val book = content.office?.workbook
            return BatchDocument(content.kind, when { book != null -> OfficeFormat.XLSX; content.office != null -> OfficeFormat.DOCX; else -> null },
                book?.sheets?.map { it.name }.orEmpty(), book?.sheets?.map { s -> s.cells.count { it.formula != null && it.value == null } }.orEmpty(),
                book?.date1904 ?: false, if ((book?.sheets?.size ?: 0) > 1) -1 else 0)
        }
        fun decode(j: JSONObject): BatchDocument {
            val sheets = j.getJSONArray("sheets"); val missing = j.getJSONArray("missing")
            require(sheets.length() <= 16 && missing.length() == sheets.length())
            return BatchDocument(DocumentKind.valueOf(j.getString("kind")), j.getString("office").takeIf { it.isNotEmpty() }?.let(OfficeFormat::valueOf),
                (0 until sheets.length()).map { sheets.getString(it).also { n -> require(n.length in 1..256) } },
                (0 until missing.length()).map { missing.getInt(it).also { n -> require(n in 0..5000) } },
                j.getBoolean("date1904"), j.getInt("sheet"), OfficeCellMode.valueOf(j.getString("mode"))).also { value ->
                if (value.office == OfficeFormat.XLSX) require(value.kind == DocumentKind.CSV && value.sheets.isNotEmpty() && value.sheet in -1..value.sheets.lastIndex)
                else require(value.sheets.isEmpty() && value.sheet == 0 && !value.date1904 && value.mode == OfficeCellMode.STORED_VALUES &&
                    (value.office != OfficeFormat.DOCX || value.kind == DocumentKind.TEXT))
                require(value.sheets.distinct().size == value.sheets.size)
            }
        }
    }
}

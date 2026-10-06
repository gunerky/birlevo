package com.example.contentstudio.documents

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import androidx.core.graphics.withClip
import com.example.contentstudio.R
import java.io.File
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Text/record report. No bitmap rasterization or clipping of final document contents. */
internal class TextDocumentPdf(private val context: Context) {
    suspend fun write(content: DocumentContent, file: File): Int {
        val job = currentCoroutineContext()
        val pdf = PdfDocument()
        var page: PdfDocument.Page? = null
        var pages = 0
        var y = 48
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 12f; typeface = Typeface.DEFAULT }
        fun finishPage() {
            page?.let {
                it.canvas.drawText(pages.toString(), 530f, 819f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; textSize = 10f })
                pdf.finishPage(it)
            }
            page = null
        }
        fun startPage() {
            job.ensureActive()
            if (pages >= DocumentPolicy.MAX_PDF_PAGES) throw DocumentException(DocumentIssue.PDF_LIMIT)
            page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, ++pages).create())
            y = 48
        }
        fun append(text: String, bold: Boolean = false) {
            for (chunk in TextDocumentCodec.chunks(text.ifEmpty { " " })) {
                job.ensureActive()
                paint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                val layout = StaticLayout.Builder.obtain(chunk, 0, chunk.length, paint, 499)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL).setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
                    .setIncludePad(true).setLineSpacing(2f, 1f).build()
                var line = 0
                while (line < layout.lineCount) {
                    job.ensureActive()
                    if (page == null) startPage()
                    val top = layout.getLineTop(line)
                    var end = line
                    while (end < layout.lineCount && layout.getLineBottom(end) - top <= 794 - y) end++
                    if (end == line) { finishPage(); continue }
                    val height = layout.getLineBottom(end - 1) - top
                    val canvas = checkNotNull(page).canvas
                    canvas.withClip(48, y, 547, y + height) {
                        translate(48f, (y - top).toFloat())
                        layout.draw(this)
                    }
                    y += height
                    line = end
                    if (line < layout.lineCount) finishPage()
                }
            }
        }
        try {
            if (content.kind == DocumentKind.TEXT) append(content.text)
            else content.rows.forEachIndexed { row, cells ->
                job.ensureActive()
                append(context.getString(R.string.document_row, row + 1), true)
                cells.forEachIndexed { column, value ->
                    append(context.getString(R.string.document_column, column + 1) + ": " + value)
                }
                y += 12
            }
            if (page == null && pages == 0) startPage()
            finishPage()
            job.ensureActive()
            file.outputStream().use { pdf.writeTo(it) }
            job.ensureActive()
            return pages
        } finally { finishPage(); pdf.close() }
    }
}

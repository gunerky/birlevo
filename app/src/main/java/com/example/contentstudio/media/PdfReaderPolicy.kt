package com.example.contentstudio.media

import kotlin.math.max
import kotlin.math.roundToInt

enum class PdfReadIssue { INVALID, PASSWORD, LIMIT, ACCESS, MEMORY, TIMEOUT, PROCESS, EXPORT, STORAGE }
class PdfReadException(val issue: PdfReadIssue) : Exception(issue.name)

object PdfReaderPolicy {
    const val MAX_BYTES = 128L * 1024 * 1024
    const val MAX_PAGES = 2000
    const val PREVIEW_EDGE = 1280
    val outputEdges = listOf(1280, 2048, 4096)
    val imageFormats = listOf(ImageOutputFormat.PNG, ImageOutputFormat.JPEG, ImageOutputFormat.WEBP)

    fun rasterSize(width: Int, height: Int, edge: Int): Pair<Int, Int> {
        if (width <= 0 || height <= 0 || edge !in outputEdges) throw PdfReadException(PdfReadIssue.INVALID)
        val scale = edge.toDouble() / max(width, height)
        return max(1, (width * scale).roundToInt()) to max(1, (height * scale).roundToInt())
    }

    fun validatePageCount(pages: Int) {
        if (pages <= 0) throw PdfReadException(PdfReadIssue.INVALID)
        if (pages > MAX_PAGES) throw PdfReadException(PdfReadIssue.LIMIT)
    }
}

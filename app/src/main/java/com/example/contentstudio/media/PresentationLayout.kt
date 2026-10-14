package com.example.contentstudio.media

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.TextUtils
import com.example.contentstudio.data.SlideGeometry
import com.example.contentstudio.data.StudioProject
import com.example.contentstudio.data.slideGeometry
import java.util.Locale

internal enum class SlideParagraphDirection { LEFT_TO_RIGHT, RIGHT_TO_LEFT }

/** UTF-16 offsets in the original logical text; coordinates on the fixed 960 × 540 slide. */
internal data class MeasuredSlideLine(
    val start: Int, val end: Int, val visibleEnd: Int,
    val left: Float, val right: Float, val top: Float, val bottom: Float, val baseline: Float,
    val direction: SlideParagraphDirection,
)

/** Metrics of the requested Android default face, not a claim about per-glyph fallback fonts. */
internal data class SlideFontMeasurement(
    val size: Float, val bold: Boolean, val localeTag: String,
    val ascent: Float, val descent: Float, val top: Float, val bottom: Float,
)

internal data class MeasuredSlideFragment(val text: String, val left: Float, val top: Float,
    val width: Float, val height: Float, val rtl: Boolean, val word: Int)

internal class MeasuredSlideText(
    val text: String, val left: Float, val top: Float,
    private val native: StaticLayout, bold: Boolean,
) {
    val width = native.width
    val height = native.height
    val font = native.paint.fontMetrics.let {
        SlideFontMeasurement(native.paint.textSize, bold, native.paint.textLocale.toLanguageTag(),
            it.ascent, it.descent, it.top, it.bottom)
    }
    // Rendering needs only StaticLayout. Do not allocate line inventories for every video frame.
    val lines: List<MeasuredSlideLine> by lazy {
        (0 until native.lineCount).map { line ->
            MeasuredSlideLine(
                native.getLineStart(line), native.getLineEnd(line), native.getLineVisibleEnd(line),
                left + native.getLineLeft(line), left + native.getLineRight(line),
                top + native.getLineTop(line), top + native.getLineBottom(line),
                top + native.getLineBaseline(line),
                if (native.getParagraphDirection(line) == Layout.DIR_RIGHT_TO_LEFT)
                    SlideParagraphDirection.RIGHT_TO_LEFT else SlideParagraphDirection.LEFT_TO_RIGHT,
            )
        }
    }

    fun draw(canvas: Canvas) {
        val checkpoint = canvas.save()
        try { canvas.translate(left, top); native.draw(canvas) }
        finally { canvas.restoreToCount(checkpoint) }
    }

    /** Logical word order, physical positions from Android's actual bidi/shaping result. */
    fun wordFragments(): List<MeasuredSlideFragment> = buildList {
        var leading = ""
        val tokens = Regex("[^\\s\\p{Z}]+[\\s\\p{Z}]*|[\\s\\p{Z}]+")
        var wordId = 0
        for (line in 0 until native.lineCount) {
            val start = native.getLineStart(line)
            val lineText = text.substring(start, native.getLineEnd(line))
            val bidi = java.text.Bidi(lineText, if(native.getParagraphDirection(line) == Layout.DIR_RIGHT_TO_LEFT)
                java.text.Bidi.DIRECTION_RIGHT_TO_LEFT else java.text.Bidi.DIRECTION_LEFT_TO_RIGHT)
            val ranges = tokens.findAll(lineText).flatMap { token ->
                val word = wordId++
                val pieces = mutableListOf<IntRange>()
                var part = token.range.first
                for (index in token.range.first + 1..token.range.last) {
                    if (bidi.getLevelAt(index) != bidi.getLevelAt(index - 1)) {
                        pieces += part until index; part = index
                    }
                }
                pieces += part..token.range.last
                pieces.asSequence().map { it to word }
            }
            for ((range, word) in ranges) {
                val value = lineText.substring(range.first, range.last + 1)
                if (value.isBlank()) {
                    if (isEmpty()) leading += value
                    else {
                        val last = removeAt(lastIndex)
                        val advance = native.paint.measureText(value.trimEnd('\n', '\r'))
                        add(last.copy(text = last.text + value,
                            left = last.left - if(last.rtl) advance else 0f, width = last.width + advance))
                    }
                    continue
                }
                val from = start + range.first
                // Selection of wrap-ending whitespace can span the whole column. Measure only
                // visible glyphs, then add the trailing advance without selecting the next line.
                // The original fragment text (including all whitespace) is still exported.
                val visibleEnd = from + value.trimEnd { it.isWhitespace() || Character.isSpaceChar(it) }.length
                val trailingAdvance = native.paint.measureText(value.substring(visibleEnd - from).trimEnd('\n', '\r'))
                val rtl = bidi.getLevelAt(range.first) % 2 == 1
                val bounds = RectF()
                if (visibleEnd > from) {
                    val selection = Path()
                    native.getSelectionPath(if ('\n' !in leading && '\r' !in leading) from - leading.length else from,
                        visibleEnd, selection)
                    selection.computeBounds(bounds, true)
                }
                val firstLine = if ('\n' in leading) 0 else line
                add(MeasuredSlideFragment(leading + value, left + bounds.left - (if (rtl) trailingAdvance else 0f),
                    top + native.getLineTop(firstLine), maxOf(1f, bounds.width() + trailingAdvance),
                    (native.getLineBottom(line) - native.getLineTop(firstLine)).toFloat(),
                    rtl, word))
                leading = ""
            }
        }
    }
}

internal data class MeasuredSlide(
    val geometry: SlideGeometry,
    val caption: MeasuredSlideText,
    val heading: MeasuredSlideText?,
    val body: MeasuredSlideText?,
)

/** One shaping/fitting policy for previews, PDF, images, MP4 and appearance-preserving PPTX. */
internal object PresentationLayout {
    const val WIDTH = 960
    const val HEIGHT = 540

    fun measure(project: StudioProject, index: Int, hasImage: Boolean): MeasuredSlide {
        val slide = project.slides[index]
        val geometry = slideGeometry(slide.layout, hasImage)
        val color = project.theme.palette.text
        val heading = fit(slide.heading, geometry.headingStart, geometry.headingMinimum,
            geometry.textWidth, geometry.headingHeight, true, index + 1, color,
            geometry.textLeft, geometry.textTop.toFloat())
        val bodyTop = geometry.textTop + (heading?.height ?: 0) + if (heading != null) 24 else 0
        val body = fit(slide.body, geometry.bodyStart, geometry.bodyMinimum,
            geometry.textWidth, geometry.textBottom - bodyTop, false, index + 1, color,
            geometry.textLeft, bodyTop.toFloat())
        val captionPaint = paint(14, false, project.theme.palette.muted)
        // Only the repeated project label may be shortened. Slide content never is.
        val caption = TextUtils.ellipsize(project.title.replace('\n', ' ').replace('\r', ' '),
            captionPaint, 730f, TextUtils.TruncateAt.END).toString()
        return MeasuredSlide(geometry,
            MeasuredSlideText(caption, 64f, 70f, layout(caption, captionPaint, 828), false),
            heading, body)
    }

    private fun fit(text: String, start: Int, minimum: Int, width: Int, maxHeight: Int,
        bold: Boolean, page: Int, color: Int, left: Float, top: Float): MeasuredSlideText? {
        if (text.isBlank()) return null
        for (size in start downTo minimum step 2) {
            val result = layout(text, paint(size, bold, color), width)
            if (result.height <= maxHeight) return MeasuredSlideText(text, left, top, result, bold)
        }
        throw PdfTextOverflow(page)
    }

    private fun paint(size: Int, bold: Boolean, color: Int) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size.toFloat()
        this.color = color
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        // UI language changes must not change font fallback preferences in saved slide content.
        // Script shaping and each paragraph's first strong direction still come from the text.
        textLocale = Locale.ROOT
    }

    private fun layout(text: String, paint: TextPaint, width: Int) =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
            .setIncludePad(true)
            .setLineSpacing(4f, 1.05f)
            .build()
}

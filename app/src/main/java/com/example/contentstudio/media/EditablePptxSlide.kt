package com.example.contentstudio.media

import androidx.core.net.toUri
import android.content.Context
import android.graphics.Bitmap
import com.example.contentstudio.data.StudioProject
import kotlin.math.roundToInt

/** Deliberately excludes unverified scripts, emoji and bidi controls, never strips them. */
object EditablePptxPolicy {
    private val punctuation = setOf(0xA1, 0xA3, 0xA7, 0xAB, 0xB0, 0xB7, 0xBB, 0xBF,
        0x2013, 0x2014, 0x2018, 0x2019, 0x201C, 0x201D, 0x2022, 0x2026, 0x20AC)
    fun supports(project: StudioProject): Boolean = supportedText(project.title) &&
        project.slides.all { supportedText(it.heading) && supportedText(it.body) }

    fun usesRtl(project: StudioProject): Boolean =
        containsArabic(project.title) || project.slides.any { containsArabic(it.heading) || containsArabic(it.body) }

    internal fun containsArabic(text: String) = text.codePoints().anyMatch {
        Character.UnicodeScript.of(it) == Character.UnicodeScript.ARABIC
    }

    private fun supportedText(text: String) = text.codePoints().allMatch { cp ->
        cp == 9 || cp == 10 || cp == 13 || cp in 32..126 || cp == 0xA0 ||
            cp in 0x300..0x36F || Character.UnicodeScript.of(cp) == Character.UnicodeScript.LATIN ||
            // Modern Arabic/Persian letters, vowel marks and digits; ZWNJ keeps Persian joining.
            cp in punctuation || cp in 0x620..0x6D3 || cp in 0x6F0..0x6F9 ||
            cp == 0x60C || cp == 0x61B || cp == 0x61F || cp == 0x200C
    }
}

/** Native, separately editable text boxes and an oriented, contained image. No font embedding. */
internal object EditablePptxSlide {
    suspend fun write(context: Context, archive: PptxPackage, project: StudioProject, index: Int, options: PptxOptions) {
        val slide = project.slides[index]
        val bitmap = slide.imageUri?.let { SlideImages.decode(context, it.toUri(), options.edge) }
        try {
            val measured = PresentationLayout.measure(project, index, bitmap != null)
            val palette = project.theme.palette
            val shapes = StringBuilder()
            shapes.append(rectangle(2, "Accent", 64f, 56f, 40f, 5f, palette.accent))
            shapes.append(block(3, "Project title", measured.caption, 32f, palette.muted))
            measured.heading?.let { shapes.append(block(4, "Heading", it,
                measured.geometry.headingHeight.toFloat(), palette.text)) }
            measured.body?.let { shapes.append(block(5, "Body", it,
                measured.geometry.textBottom - it.top, palette.text)) }
            bitmap?.let {
                val box = requireNotNull(measured.geometry.image)
                val scale = minOf(box.width / it.width, box.height / it.height)
                val width = it.width * scale; val height = it.height * scale
                shapes.append(picture(box.left + (box.width - width) / 2,
                    box.top + (box.height - height) / 2, width, height))
            }
            shapes.append(text(7, "Page", "${index + 1} / ${project.slides.size}", 64f, 494f, 832f, 30f,
                14f, false, palette.muted, "r"))
            archive.editableSlide(slide.notes, shapes.toString(), palette.background, bitmap != null) { out ->
                check(requireNotNull(bitmap).compress(Bitmap.CompressFormat.PNG, 100, out))
            }
        } finally { bitmap?.recycle() }
    }

    private fun text(id: Int, name: String, value: String, x: Float, y: Float, width: Float,
        height: Float, size: Float, bold: Boolean, color: Int, align: String = "l",
        lineSpacing: Float = size * 1.05f + 4f, word: Boolean = false, rtl: Boolean = false): String {
        val style = "sz=\"${(size * 75).roundToInt()}\" b=\"${if(bold) 1 else 0}\" lang=\"en-US\""
        val font = "<a:solidFill><a:srgbClr val=\"${color(color)}\"/></a:solidFill><a:latin typeface=\"Arial\"/>" +
            if(word) "<a:cs typeface=\"Arial\"/><a:rtl val=\"${if(rtl) 1 else 0}\"/>" else ""
        val lineHeight = (lineSpacing * 75).roundToInt()
        val paragraphs = (if(word) listOf(value) else value.split('\n')).joinToString("") { line ->
            "<a:p><a:pPr algn=\"$align\" rtl=\"${if(rtl) 1 else 0}\"><a:lnSpc><a:spcPts val=\"$lineHeight\"/></a:lnSpc><a:spcBef><a:spcPts val=\"0\"/></a:spcBef><a:spcAft><a:spcPts val=\"0\"/></a:spcAft><a:buNone/></a:pPr><a:r><a:rPr $style>$font</a:rPr><a:t xml:space=\"preserve\">${PptxPackage.escape(line).replace("\r", "&#13;")}</a:t></a:r><a:endParaRPr $style/></a:p>"
        }
        return "<p:sp><p:nvSpPr><p:cNvPr id=\"$id\" name=\"$name\"/><p:cNvSpPr txBox=\"1\"/><p:nvPr/></p:nvSpPr><p:spPr>${transform(x,y,width,height)}<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom><a:noFill/><a:ln><a:noFill/></a:ln></p:spPr><p:txBody><a:bodyPr wrap=\"${if(word) "none" else "square"}\" lIns=\"0\" tIns=\"0\" rIns=\"0\" bIns=\"0\" anchor=\"t\" vertOverflow=\"overflow\"><a:noAutofit/></a:bodyPr><a:lstStyle/>$paragraphs</p:txBody></p:sp>"
    }

    private fun block(id: Int, name: String, measured: MeasuredSlideText, height: Float, color: Int): String {
        if (!containsRtl(measured.text)) return text(id, name, measured.text, measured.left, measured.top,
            measured.width.toFloat(), height, measured.font.size, measured.font.bold, color,
            lineSpacing = spacing(measured))
        val fragments = spacedWords(measured)
        val children = fragments.mapIndexed { index, fragment ->
            val rtl = fragment.rtl
            // Keep the measured centre, allowing font substitution room without reducing size.
            // Some readers ignore wrap="none"; a tight advance box can hide an entire word.
            val reserve = maxOf(measured.font.size * .5f, fragment.width * .125f)
            text(id * 10000 + index, "$name part $index", fragment.text, fragment.left - reserve, fragment.top,
                fragment.width + reserve * 2, fragment.height, measured.font.size, measured.font.bold, color,
                "ctr", lineSpacing=spacing(measured), word=true, rtl=rtl)
        }.joinToString("")
        val x = (measured.left * 9525).toLong(); val y = (measured.top * 9525).toLong()
        val w = measured.width * 9525L; val h = (height * 9525).toLong()
        return "<p:grpSp><p:nvGrpSpPr><p:cNvPr id=\"$id\" name=\"$name words\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr><a:xfrm><a:off x=\"$x\" y=\"$y\"/><a:ext cx=\"$w\" cy=\"$h\"/><a:chOff x=\"$x\" y=\"$y\"/><a:chExt cx=\"$w\" cy=\"$h\"/></a:xfrm></p:grpSpPr>$children</p:grpSp>"
    }

    private fun spacedWords(measured: MeasuredSlideText): List<MeasuredSlideFragment> {
        val parts = measured.wordFragments().toMutableList()
        for (row in parts.indices.groupBy { parts[it].top }.values) {
            val rtl = measured.lines.firstOrNull { it.top == parts[row.first()].top }?.direction ==
                SlideParagraphDirection.RIGHT_TO_LEFT
            val visual = if(rtl) row.sortedByDescending { parts[it].left } else row.sortedBy { parts[it].left }
            val gaps = visual.zipWithNext().count { (a,b) -> parts[a].word != parts[b].word }
            if(gaps == 0) continue
            val room = if(rtl) parts[row.minBy { parts[it].left }].left - measured.left
                else measured.left + measured.width - row.maxOf { parts[it].left + parts[it].width }
            val extra = minOf(measured.font.size * .25f, maxOf(0f, room) / gaps)
            var offset = 0f
            var previous: Int? = null
            for (index in visual) {
                if (previous != null && parts[previous].word != parts[index].word) {
                    offset += extra
                }
                parts[index] = parts[index].copy(left = parts[index].left + (if (rtl) -offset else offset))
                previous = index
            }
        }
        return parts
    }

    internal fun containsRtl(value: String) = EditablePptxPolicy.containsArabic(value)

    private fun spacing(block: MeasuredSlideText): Float =
        if (block.lines.size > 1) block.lines[1].baseline - block.lines[0].baseline
        else (block.font.descent - block.font.ascent) * 1.05f + 4f

    private fun rectangle(id: Int, name: String, x: Float, y: Float, width: Float, height: Float, color: Int) =
        "<p:sp><p:nvSpPr><p:cNvPr id=\"$id\" name=\"$name\"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr><p:spPr>${transform(x,y,width,height)}<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom><a:solidFill><a:srgbClr val=\"${color(color)}\"/></a:solidFill><a:ln><a:noFill/></a:ln></p:spPr></p:sp>"

    private fun picture(x: Float, y: Float, width: Float, height: Float) =
        "<p:pic><p:nvPicPr><p:cNvPr id=\"6\" name=\"Image\"/><p:cNvPicPr><a:picLocks noChangeAspect=\"1\"/></p:cNvPicPr><p:nvPr/></p:nvPicPr><p:blipFill><a:blip r:embed=\"Image\"/><a:stretch><a:fillRect/></a:stretch></p:blipFill><p:spPr>${transform(x,y,width,height)}<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></p:spPr></p:pic>"

    private fun transform(x: Float, y: Float, width: Float, height: Float) =
        "<a:xfrm><a:off x=\"${(x*9525).toLong()}\" y=\"${(y*9525).toLong()}\"/><a:ext cx=\"${(width*9525).toLong()}\" cy=\"${(height*9525).toLong()}\"/></a:xfrm>"
    private fun color(value: Int) = "%06X".format(java.util.Locale.ROOT, value and 0xFFFFFF)
}

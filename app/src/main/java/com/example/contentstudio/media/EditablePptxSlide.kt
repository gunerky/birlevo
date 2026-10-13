package com.example.contentstudio.media

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.example.contentstudio.data.StudioProject
import kotlin.math.roundToInt

/** Deliberately excludes unverified scripts, emoji and bidi controls, never strips them. */
object EditablePptxPolicy {
    private val punctuation = setOf(0xA1, 0xA3, 0xA7, 0xAB, 0xB0, 0xB7, 0xBB, 0xBF,
        0x2013, 0x2014, 0x2018, 0x2019, 0x201C, 0x201D, 0x2022, 0x2026, 0x20AC)
    fun supports(project: StudioProject): Boolean = supportedText(project.title) &&
        project.slides.all { supportedText(it.heading) && supportedText(it.body) }

    private fun supportedText(text: String) = text.codePoints().allMatch { cp ->
        cp == 9 || cp == 10 || cp == 13 || cp in 32..126 || cp == 0xA0 ||
            cp in 0x300..0x36F || Character.UnicodeScript.of(cp) == Character.UnicodeScript.LATIN ||
            cp in punctuation
    }
}

/** Native, separately editable text boxes and an oriented, contained image. No font embedding. */
internal object EditablePptxSlide {
    suspend fun write(context: Context, archive: PptxPackage, project: StudioProject, index: Int, options: PptxOptions) {
        val slide = project.slides[index]
        val bitmap = slide.imageUri?.let { SlideImages.decode(context, Uri.parse(it), options.edge) }
        try {
            val measured = PresentationLayout.measure(project, index, bitmap != null)
            val palette = project.theme.palette
            val shapes = StringBuilder()
            shapes.append(rectangle(2, "Accent", 64f, 56f, 40f, 5f, palette.accent))
            shapes.append(text(3, "Project title", measured.caption.text, 64f, 70f, 828f, 32f, 14f, false, palette.muted))
            measured.heading?.let { shapes.append(text(4, "Heading", it.text, it.left, it.top,
                it.width.toFloat(), measured.geometry.headingHeight.toFloat(), it.font.size, true, palette.text,
                lineSpacing = spacing(it))) }
            measured.body?.let { shapes.append(text(5, "Body", it.text, it.left, it.top,
                it.width.toFloat(), measured.geometry.textBottom - it.top, it.font.size, false, palette.text,
                lineSpacing = spacing(it))) }
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
        lineSpacing: Float = size * 1.05f + 4f): String {
        val style = "sz=\"${(size * 75).roundToInt()}\" b=\"${if(bold) 1 else 0}\" lang=\"en-US\""
        val font = "<a:solidFill><a:srgbClr val=\"${color(color)}\"/></a:solidFill><a:latin typeface=\"Arial\"/>"
        val lineHeight = (lineSpacing * 75).roundToInt()
        val paragraphs = value.split('\n').joinToString("") { line ->
            "<a:p><a:pPr algn=\"$align\" rtl=\"0\"><a:lnSpc><a:spcPts val=\"$lineHeight\"/></a:lnSpc><a:spcBef><a:spcPts val=\"0\"/></a:spcBef><a:spcAft><a:spcPts val=\"0\"/></a:spcAft><a:buNone/></a:pPr><a:r><a:rPr $style>$font</a:rPr><a:t xml:space=\"preserve\">${PptxPackage.escape(line)}</a:t></a:r><a:endParaRPr $style/></a:p>"
        }
        return "<p:sp><p:nvSpPr><p:cNvPr id=\"$id\" name=\"$name\"/><p:cNvSpPr txBox=\"1\"/><p:nvPr/></p:nvSpPr><p:spPr>${transform(x,y,width,height)}<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom><a:noFill/><a:ln><a:noFill/></a:ln></p:spPr><p:txBody><a:bodyPr wrap=\"square\" lIns=\"0\" tIns=\"0\" rIns=\"0\" bIns=\"0\" anchor=\"t\" vertOverflow=\"overflow\"><a:noAutofit/></a:bodyPr><a:lstStyle/>$paragraphs</p:txBody></p:sp>"
    }

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

package com.example.contentstudio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import com.example.contentstudio.data.*
import com.example.contentstudio.media.PdfTextOverflow
import com.example.contentstudio.media.PresentationPdf
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Experimental writer in androidTest ONLY. Not a supported product export or an Office renderer.
 */
internal class PptxTrialExporter(private val context: Context, private val testContext: Context) {
    enum class Mode {
        EDITABLE,
        SLIDE_IMAGE,
    }

    suspend fun export(
        project: StudioProject,
        mode: Mode,
        output: File,
        edge: Int = 2048,
        progress: (Int) -> Unit = {},
    ) {
        require(project.kind == ProjectKind.PRESENTATION && project.slides.size in 1..100)
        require(edge in listOf(1024, 2048, 4096))
        require(
            project.title.length <= 500 &&
                project.slides.sumOf { it.heading.length + it.body.length + it.notes.length } <=
                    200000
        )
        require(
            project.slides.all {
                it.heading.length <= 1000 && it.body.length <= 8000 && it.notes.length <= 8000
            }
        )
        val parts = linkedMapOf<String, ByteArray>()
        ZipInputStream(testContext.assets.open("pptx/blank-template.pptx")).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(entry.size <= 2 * 1024 * 1024)
                val bytes = zip.readBytes()
                require(bytes.size <= 2 * 1024 * 1024)
                if (
                    !entry.name.startsWith("ppt/slides/") &&
                        !entry.name.startsWith("ppt/notesSlides/")
                )
                    parts[entry.name] = bytes
            }
        }
        fun read(name: String) =
            parts.getValue(name).toString(Charsets.UTF_8).removePrefix("\uFEFF")
        fun put(name: String, text: String) {
            parts[name] = text.toByteArray(Charsets.UTF_8)
        }
        val slideIds =
            project.slides.indices.joinToString("") {
                "<p:sldId id=\"${256 + it}\" r:id=\"Slide${it + 1}\" xmlns:r=\"$R\"/>"
            }
        put(
            "ppt/presentation.xml",
            read("ppt/presentation.xml")
                .replace(
                    Regex("<p:sldIdLst>.*?</p:sldIdLst>"),
                    "<p:sldIdLst>$slideIds</p:sldIdLst>",
                ),
        )
        val baseRels =
            read("ppt/_rels/presentation.xml.rels")
                .replace(Regex("<Relationship\\b[^>]*Type=\"$R/slide\"[^>]*/>"), "")
        val newRels =
            project.slides.indices.joinToString("") {
                relation("Slide${it + 1}", "slide", "slides/slide${it + 1}.xml")
            }
        put(
            "ppt/_rels/presentation.xml.rels",
            baseRels.replace("</Relationships>", "$newRels</Relationships>"),
        )
        var types =
            read("[Content_Types].xml")
                .replace(
                    Regex("<Override\\b[^>]*PartName=\"/ppt/(?:slides|notesSlides)/[^>]*?/>"),
                    "",
                )
        if (!types.contains("Extension=\"png\""))
            types =
                types.replace(
                    "</Types>",
                    "<Default Extension=\"png\" ContentType=\"image/png\"/></Types>",
                )
        val overrides =
            project.slides.indices.joinToString("") { i ->
                "<Override PartName=\"/ppt/slides/slide${i + 1}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slide+xml\"/>" +
                    "<Override PartName=\"/ppt/notesSlides/notesSlide${i + 1}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.notesSlide+xml\"/>"
            }
        put("[Content_Types].xml", types.replace("</Types>", "$overrides</Types>"))
        put(
            "docProps/app.xml",
            read("docProps/app.xml")
                .replace(Regex("<Slides>.*?</Slides>"), "<Slides>${project.slides.size}</Slides>"),
        )
        try {
            ZipOutputStream(output.outputStream()).use { zip ->
                fun write(name: String, bytes: ByteArray) {
                    zip.putNextEntry(ZipEntry(name).apply { time = 0L })
                    zip.write(bytes)
                    zip.closeEntry()
                }
                parts.forEach { (name, bytes) -> write(name, bytes) }
                for ((index, slide) in project.slides.withIndex()) {
                    currentCoroutineContext().ensureActive()
                    val number = index + 1
                    val links =
                        mutableListOf(
                            relation("Layout", "slideLayout", "../slideLayouts/slideLayout6.xml"),
                            relation("Notes", "notesSlide", "../notesSlides/notesSlide$number.xml"),
                        )
                    val shapes = StringBuilder()
                    val palette = project.theme.palette
                    if (mode == Mode.SLIDE_IMAGE) {
                        val bitmap =
                            PresentationPdf(context)
                                .renderSlideBitmap(project, index, edge, edge * 9 / 16)
                        val bytes =
                            try {
                                ByteArrayOutputStream().use { buffer ->
                                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, buffer))
                                    buffer.toByteArray()
                                }
                            } finally {
                                bitmap.recycle()
                            }
                        write("ppt/media/image$number.png", bytes)
                        links += relation("Image", "image", "../media/image$number.png")
                        shapes.append(picture(2, 0f, 0f, 960f, 540f))
                    } else {
                        val geometry = slideGeometry(slide.layout, slide.imageUri != null)
                        val heading =
                            fit(
                                slide.heading,
                                geometry.headingStart,
                                geometry.headingMinimum,
                                geometry.textWidth,
                                geometry.headingHeight,
                                true,
                                number,
                            )
                        val bodyTop =
                            geometry.textTop +
                                heading.second +
                                if (slide.heading.isBlank()) 0 else 24
                        val body =
                            fit(
                                slide.body,
                                geometry.bodyStart,
                                geometry.bodyMinimum,
                                geometry.textWidth,
                                geometry.textBottom - bodyTop,
                                false,
                                number,
                            )
                        shapes.append(
                            textShape(
                                2,
                                "Heading",
                                slide.heading,
                                geometry.textLeft,
                                geometry.textTop.toFloat(),
                                geometry.textWidth.toFloat(),
                                geometry.headingHeight.toFloat(),
                                heading.first,
                                palette.text,
                                true,
                            )
                        )
                        shapes.append(
                            textShape(
                                3,
                                "Body",
                                slide.body,
                                geometry.textLeft,
                                bodyTop.toFloat(),
                                geometry.textWidth.toFloat(),
                                (geometry.textBottom - bodyTop).toFloat(),
                                body.first,
                                palette.text,
                            )
                        )
                        shapes.append(
                            "<p:sp><p:nvSpPr><p:cNvPr id=\"7\" name=\"Accent\"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr><p:spPr>${transform(64f, 56f, 40f, 5f)}<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom><a:solidFill><a:srgbClr val=\"${color(palette.accent)}\"/></a:solidFill><a:ln><a:noFill/></a:ln></p:spPr></p:sp>"
                        )
                        shapes.append(
                            textShape(
                                4,
                                "Project title",
                                project.title,
                                64f,
                                72f,
                                800f,
                                25f,
                                14,
                                palette.muted,
                            )
                        )
                        shapes.append(
                            textShape(
                                5,
                                "Page number",
                                "$number / ${project.slides.size}",
                                820f,
                                500f,
                                100f,
                                24f,
                                14,
                                palette.muted,
                            )
                        )
                        if (slide.imageUri != null) {
                            val source = File(android.net.Uri.parse(slide.imageUri).path!!)
                            val bytes = source.readBytes()
                            require(
                                bytes
                                    .take(8)
                                    .toByteArray()
                                    .contentEquals(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10))
                            )
                            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                            require(bounds.outWidth > 0 && bounds.outHeight > 0)
                            val box = geometry.image!!
                            val scale =
                                minOf(box.width / bounds.outWidth, box.height / bounds.outHeight)
                            val width = bounds.outWidth * scale
                            val height = bounds.outHeight * scale
                            shapes.append(
                                picture(
                                    6,
                                    box.left + (box.width - width) / 2,
                                    box.top + (box.height - height) / 2,
                                    width,
                                    height,
                                )
                            )
                            write("ppt/media/image$number.png", bytes)
                            links += relation("Image", "image", "../media/image$number.png")
                        }
                    }
                    val slideXml =
                        "<p:sld $NS><p:cSld><p:bg><p:bgPr><a:solidFill><a:srgbClr val=\"${color(palette.background)}\"/></a:solidFill><a:effectLst/></p:bgPr></p:bg><p:spTree>$GROUP$shapes</p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sld>"
                    write("ppt/slides/slide$number.xml", slideXml.toByteArray(Charsets.UTF_8))
                    write(
                        "ppt/slides/_rels/slide$number.xml.rels",
                        rels(links).toByteArray(Charsets.UTF_8),
                    )
                    val noteShape =
                        textShape(2, "Notes", slide.notes, 0f, 0f, 700f, 500f, 20, palette.text)
                            .replace(
                                "<p:nvPr/>",
                                "<p:nvPr><p:ph type=\"body\" idx=\"1\"/></p:nvPr>",
                            )
                    val notes =
                        "<p:notes $NS><p:cSld><p:spTree>$GROUP$noteShape</p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:notes>"
                    write(
                        "ppt/notesSlides/notesSlide$number.xml",
                        notes.toByteArray(Charsets.UTF_8),
                    )
                    write(
                        "ppt/notesSlides/_rels/notesSlide$number.xml.rels",
                        rels(
                                listOf(
                                    relation(
                                        "Master",
                                        "notesMaster",
                                        "../notesMasters/notesMaster1.xml",
                                    ),
                                    relation("Slide", "slide", "../slides/slide$number.xml"),
                                )
                            )
                            .toByteArray(Charsets.UTF_8),
                    )
                    progress(number)
                }
                currentCoroutineContext().ensureActive()
            }
        } catch (failure: Throwable) {
            output.delete()
            throw failure
        }
    }

    private fun textShape(
        id: Int,
        name: String,
        text: String,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        size: Int,
        color: Int,
        bold: Boolean = false,
    ): String {
        val rtl =
            text.any {
                Character.getDirectionality(it) in
                    listOf(
                        Character.DIRECTIONALITY_RIGHT_TO_LEFT,
                        Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC,
                    )
            }
        val paragraphs =
            text.split('\n').joinToString("") { line ->
                "<a:p><a:pPr rtl=\"${if (rtl) 1 else 0}\" algn=\"${if (rtl) "r" else "l"}\"/><a:r><a:rPr sz=\"${size * 75}\" b=\"${if (bold) 1 else 0}\"><a:solidFill><a:srgbClr val=\"${color(color)}\"/></a:solidFill><a:latin typeface=\"Arial\"/><a:ea typeface=\"Arial\"/><a:cs typeface=\"Arial\"/></a:rPr><a:t xml:space=\"preserve\">${xml(line)}</a:t></a:r></a:p>"
            }
        return "<p:sp><p:nvSpPr><p:cNvPr id=\"$id\" name=\"$name\"/><p:cNvSpPr txBox=\"1\"/><p:nvPr/></p:nvSpPr><p:spPr>${transform(x, y, width, height)}<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom><a:noFill/><a:ln><a:noFill/></a:ln></p:spPr><p:txBody><a:bodyPr lIns=\"0\" tIns=\"0\" rIns=\"0\" bIns=\"0\"/><a:lstStyle/>$paragraphs</p:txBody></p:sp>"
    }

    // Trial-only measurement mirrors the native renderer. Product implementation must share layout
    // metadata rather than maintain a second text-fitting algorithm. Office fonts may still reflow.
    private fun fit(
        text: String,
        start: Int,
        minimum: Int,
        width: Int,
        height: Int,
        bold: Boolean,
        slide: Int,
    ): Pair<Int, Int> {
        if (text.isBlank()) return start to 0
        for (size in start downTo minimum step 2) {
            val paint =
                TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = size.toFloat()
                    typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                }
            val layout =
                StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
                    .setIncludePad(true)
                    .setLineSpacing(4f, 1.05f)
                    .build()
            if (layout.height <= height) return size to layout.height
        }
        throw PdfTextOverflow(slide)
    }

    private fun picture(id: Int, x: Float, y: Float, width: Float, height: Float) =
        "<p:pic><p:nvPicPr><p:cNvPr id=\"$id\" name=\"Image\"/><p:cNvPicPr><a:picLocks noChangeAspect=\"1\"/></p:cNvPicPr><p:nvPr/></p:nvPicPr><p:blipFill><a:blip r:embed=\"Image\"/><a:stretch><a:fillRect/></a:stretch></p:blipFill><p:spPr>${transform(x, y, width, height)}<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></p:spPr></p:pic>"

    private fun transform(x: Float, y: Float, width: Float, height: Float) =
        "<a:xfrm><a:off x=\"${(x * 9525).toLong()}\" y=\"${(y * 9525).toLong()}\"/><a:ext cx=\"${(width * 9525).toLong()}\" cy=\"${(height * 9525).toLong()}\"/></a:xfrm>"

    private fun relation(id: String, type: String, target: String) =
        "<Relationship Id=\"$id\" Type=\"$R/$type\" Target=\"$target\"/>"

    private fun rels(links: List<String>) =
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">${links.joinToString("")}</Relationships>"

    private fun color(value: Int) = "%06X".format(value and 0xFFFFFF)

    private fun xml(value: String): String {
        require(
            value.codePoints().allMatch {
                it == 9 ||
                    it == 10 ||
                    it == 13 ||
                    it in 32..0xD7FF ||
                    it in 0xE000..0xFFFD ||
                    it in 0x10000..0x10FFFF
            }
        )
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }

    companion object {
        private const val P = "http://schemas.openxmlformats.org/presentationml/2006/main"
        private const val A = "http://schemas.openxmlformats.org/drawingml/2006/main"
        private const val R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
        private const val NS = "xmlns:p=\"$P\" xmlns:a=\"$A\" xmlns:r=\"$R\""
        private const val GROUP =
            "<p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"0\" cy=\"0\"/><a:chOff x=\"0\" y=\"0\"/><a:chExt cx=\"0\" cy=\"0\"/></a:xfrm></p:grpSpPr>"
    }
}

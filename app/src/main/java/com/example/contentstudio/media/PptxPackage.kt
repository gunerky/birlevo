package com.example.contentstudio.media

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Author-written OOXML package. No templates, Office SDK, font files or external relationships. */
internal class PptxPackage(output: OutputStream, val pages: Int, val includeNotes: Boolean) :
    java.io.Closeable {
    private val zip = ZipOutputStream(output).apply { setLevel(0) }
    private var next = 1

    init {
        require(pages in 1..100)
        xml(
            "[Content_Types].xml",
            "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Default Extension=\"png\" ContentType=\"image/png\"/>" +
                type("presentation", "presentation.main") +
                type("slideMasters/slideMaster1", "slideMaster") +
                type("slideLayouts/slideLayout1", "slideLayout") +
                "<Override PartName=\"/ppt/theme/theme1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.theme+xml\"/>" +
                (if (includeNotes) type("notesMasters/notesMaster1", "notesMaster") else "") +
                (1..pages).joinToString("") {
                    type("slides/slide$it", "slide") +
                        if (includeNotes) type("notesSlides/notesSlide$it", "notesSlide") else ""
                } +
                "</Types>",
        )
        relations("_rels/.rels", link("Presentation", "officeDocument", "ppt/presentation.xml"))
        xml(
            "ppt/presentation.xml",
            "<p:presentation $NS><p:sldMasterIdLst><p:sldMasterId id=\"2147483648\" r:id=\"Master\"/></p:sldMasterIdLst>" +
                (if (includeNotes)
                    "<p:notesMasterIdLst><p:notesMasterId r:id=\"NotesMaster\"/></p:notesMasterIdLst>"
                else "") +
                "<p:sldIdLst>" +
                (1..pages).joinToString("") { "<p:sldId id=\"${255 + it}\" r:id=\"Slide$it\"/>" } +
                "</p:sldIdLst><p:sldSz cx=\"9144000\" cy=\"5143500\" type=\"screen16x9\"/><p:notesSz cx=\"6858000\" cy=\"9144000\"/><p:defaultTextStyle/></p:presentation>",
        )
        relations(
            "ppt/_rels/presentation.xml.rels",
            link("Master", "slideMaster", "slideMasters/slideMaster1.xml") +
                (if (includeNotes)
                    link("NotesMaster", "notesMaster", "notesMasters/notesMaster1.xml")
                else "") +
                (1..pages).joinToString("") { link("Slide$it", "slide", "slides/slide$it.xml") },
        )
        xml(
            "ppt/slideMasters/slideMaster1.xml",
            "<p:sldMaster $NS><p:cSld><p:spTree>$GROUP</p:spTree></p:cSld>$COLOR_MAP<p:sldLayoutIdLst><p:sldLayoutId id=\"2147483649\" r:id=\"Layout\"/></p:sldLayoutIdLst><p:txStyles><p:titleStyle/><p:bodyStyle/><p:otherStyle/></p:txStyles></p:sldMaster>",
        )
        relations(
            "ppt/slideMasters/_rels/slideMaster1.xml.rels",
            link("Layout", "slideLayout", "../slideLayouts/slideLayout1.xml") +
                link("Theme", "theme", "../theme/theme1.xml"),
        )
        xml(
            "ppt/slideLayouts/slideLayout1.xml",
            "<p:sldLayout $NS type=\"blank\" preserve=\"1\"><p:cSld name=\"Blank\"><p:spTree>$GROUP</p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sldLayout>",
        )
        relations(
            "ppt/slideLayouts/_rels/slideLayout1.xml.rels",
            link("Master", "slideMaster", "../slideMasters/slideMaster1.xml"),
        )
        xml("ppt/theme/theme1.xml", theme())
        if (includeNotes) {
            xml(
                "ppt/notesMasters/notesMaster1.xml",
                "<p:notesMaster $NS><p:cSld><p:spTree>$GROUP</p:spTree></p:cSld>$COLOR_MAP<p:notesStyle/></p:notesMaster>",
            )
            relations(
                "ppt/notesMasters/_rels/notesMaster1.xml.rels",
                link("Theme", "theme", "../theme/theme1.xml"),
            )
        }
    }

    /** Encoded image is streamed, never accumulated as a second full-slide byte array. */
    fun slide(notes: String, encodePng: (OutputStream) -> Unit) {
        check(next <= pages)
        val number = next++
        val safeNotes = if (includeNotes) escape(notes) else ""
        xml(
            "ppt/slides/slide$number.xml",
            "<p:sld $NS><p:cSld><p:spTree>$GROUP<p:pic><p:nvPicPr><p:cNvPr id=\"2\" name=\"Slide image\"/><p:cNvPicPr><a:picLocks noChangeAspect=\"1\"/></p:cNvPicPr><p:nvPr/></p:nvPicPr><p:blipFill><a:blip r:embed=\"Image\"/><a:stretch><a:fillRect/></a:stretch></p:blipFill><p:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"9144000\" cy=\"5143500\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></p:spPr></p:pic></p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sld>",
        )
        relations(
            "ppt/slides/_rels/slide$number.xml.rels",
            link("Layout", "slideLayout", "../slideLayouts/slideLayout1.xml") +
                link("Image", "image", "../media/image$number.png") +
                if (includeNotes)
                    link("Notes", "notesSlide", "../notesSlides/notesSlide$number.xml")
                else "",
        )
        if (includeNotes) {
            val paragraphs =
                safeNotes.split('\n').joinToString("") {
                    "<a:p><a:r><a:rPr/><a:t xml:space=\"preserve\">$it</a:t></a:r><a:endParaRPr/></a:p>"
                }
            xml(
                "ppt/notesSlides/notesSlide$number.xml",
                "<p:notes $NS><p:cSld><p:spTree>$GROUP<p:sp><p:nvSpPr><p:cNvPr id=\"2\" name=\"Notes\"/><p:cNvSpPr/><p:nvPr><p:ph type=\"body\" idx=\"1\"/></p:nvPr></p:nvSpPr><p:spPr/><p:txBody><a:bodyPr/><a:lstStyle/>$paragraphs</p:txBody></p:sp></p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:notes>",
            )
            relations(
                "ppt/notesSlides/_rels/notesSlide$number.xml.rels",
                link("Master", "notesMaster", "../notesMasters/notesMaster1.xml") +
                    link("Slide", "slide", "../slides/slide$number.xml"),
            )
        }
        zip.putNextEntry(ZipEntry("ppt/media/image$number.png").apply { time = 0 })
        encodePng(zip)
        zip.closeEntry()
    }

    fun finish() {
        check(next == pages + 1)
        zip.finish()
        zip.flush()
    }

    override fun close() = zip.close()

    private fun xml(name: String, text: String) {
        zip.putNextEntry(ZipEntry(name).apply { time = 0 })
        zip.write(
            ("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" + text).toByteArray(
                Charsets.UTF_8
            )
        )
        zip.closeEntry()
    }

    private fun relations(name: String, links: String) =
        xml(
            name,
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">$links</Relationships>",
        )

    private fun type(part: String, subtype: String) =
        "<Override PartName=\"/ppt/$part.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.$subtype+xml\"/>"

    private fun link(id: String, type: String, target: String) =
        "<Relationship Id=\"$id\" Type=\"$R/$type\" Target=\"$target\"/>"

    private fun theme(): String {
        val colors =
            listOf(
                "dk1" to "000000",
                "lt1" to "FFFFFF",
                "dk2" to "17284D",
                "lt2" to "F5F7FB",
                "accent1" to "AC4025",
                "accent2" to "64DCC8",
                "accent3" to "5074AF",
                "accent4" to "C0563A",
                "accent5" to "746258",
                "accent6" to "BFCBDF",
                "hlink" to "0000FF",
                "folHlink" to "800080",
            )
        val fill = "<a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill>"
        val line =
            "<a:ln w=\"6350\" cap=\"flat\" cmpd=\"sng\" algn=\"ctr\">$fill<a:prstDash val=\"solid\"/><a:miter lim=\"800000\"/></a:ln>"
        return "<a:theme xmlns:a=\"$A\" name=\"Birlevo\"><a:themeElements><a:clrScheme name=\"Birlevo\">" +
            colors.joinToString("") { (name, hex) ->
                "<a:$name><a:srgbClr val=\"$hex\"/></a:$name>"
            } +
            "</a:clrScheme><a:fontScheme name=\"System\"><a:majorFont><a:latin typeface=\"Arial\"/><a:ea typeface=\"\"/><a:cs typeface=\"\"/></a:majorFont><a:minorFont><a:latin typeface=\"Arial\"/><a:ea typeface=\"\"/><a:cs typeface=\"\"/></a:minorFont></a:fontScheme><a:fmtScheme name=\"Simple\"><a:fillStyleLst>${fill.repeat(3)}</a:fillStyleLst><a:lnStyleLst>${line.repeat(3)}</a:lnStyleLst><a:effectStyleLst>${"<a:effectStyle><a:effectLst/></a:effectStyle>".repeat(3)}</a:effectStyleLst><a:bgFillStyleLst>${fill.repeat(3)}</a:bgFillStyleLst></a:fmtScheme></a:themeElements></a:theme>"
    }

    companion object {
        private const val P = "http://schemas.openxmlformats.org/presentationml/2006/main"
        private const val A = "http://schemas.openxmlformats.org/drawingml/2006/main"
        private const val R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
        private const val NS = "xmlns:p=\"$P\" xmlns:a=\"$A\" xmlns:r=\"$R\""
        private const val GROUP =
            "<p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"0\" cy=\"0\"/><a:chOff x=\"0\" y=\"0\"/><a:chExt cx=\"0\" cy=\"0\"/></a:xfrm></p:grpSpPr>"
        private const val COLOR_MAP =
            "<p:clrMap bg1=\"lt1\" tx1=\"dk1\" bg2=\"lt2\" tx2=\"dk2\" accent1=\"accent1\" accent2=\"accent2\" accent3=\"accent3\" accent4=\"accent4\" accent5=\"accent5\" accent6=\"accent6\" hlink=\"hlink\" folHlink=\"folHlink\"/>"

        internal fun escape(value: String): String {
            require(
                value.codePoints().allMatch {
                    it == 9 ||
                        it == 10 ||
                        it == 13 ||
                        it in 32..0xD7FF ||
                        it in 0xE000..0xFFFD ||
                        it in 0x10000..0x10FFFF
                }
            ) {
                "Invalid XML character"
            }
            return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("\r", "&#13;")
        }
    }
}

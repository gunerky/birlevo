package com.example.contentstudio.media

import com.tom_roush.pdfbox.cos.*
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFCloneUtility
import com.tom_roush.pdfbox.pdmodel.*
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import java.io.InputStream
import java.io.OutputStream
import java.io.FilterOutputStream

/** Invoked in an isolated worker. Fresh page tree: never clone /Parent, annotations,
 * forms, document actions or name trees, which can retain unselected pages. Content
 * streams and resources are copied as encoded objects, without rasterizing/re-encoding. */
internal object PdfPageEngine {
    fun create(inputs: List<InputStream>, pages: List<PdfPageRef>, output: OutputStream): Int {
        if (inputs.size !in 1..PdfPagePlan.MAX_SOURCES || pages.size !in 1..PdfPagePlan.MAX_PAGES)
            throw PdfPageException(PdfPageIssue.LIMIT)
        PDDocument(MemoryUsageSetting.setupMainMemoryOnly(64L * 1024 * 1024)).use { destination ->
            val clonedPages = arrayOfNulls<PDPage>(pages.size)
            for ((sourceIndex, input) in inputs.withIndex()) {
                if (pages.none { it.source == sourceIndex }) continue
                PDDocument.load(input, MemoryUsageSetting.setupMainMemoryOnly(64L * 1024 * 1024)).use { source ->
                    if (source.isEncrypted || source.signatureDictionaries.isNotEmpty()) throw PdfPageException(PdfPageIssue.PROTECTED)
                    val catalog = source.documentCatalog.cosObject
                    if (catalog.containsKey(COSName.ACRO_FORM) || catalog.containsKey(COSName.COLLECTION) ||
                        catalog.getCOSDictionary(COSName.NAMES)?.containsKey(COSName.EMBEDDED_FILES) == true)
                        throw PdfPageException(PdfPageIssue.COMPLEX)
                    if (source.numberOfPages !in 1..PdfPagePlan.MAX_PAGES) throw PdfPageException(PdfPageIssue.LIMIT)
                    destination.version = maxOf(destination.version, source.version)
                    val clone = PDFCloneUtility(destination)
                    pages.forEachIndexed { index, ref -> if (ref.source == sourceIndex) {
                        if (ref.page !in 0 until source.numberOfPages || ref.rotation !in listOf(0, 90, 180, 270))
                            throw PdfPageException(PdfPageIssue.INVALID)
                        val page = source.getPage(ref.page)
                        val dict = COSDictionary()
                        dict.setItem(COSName.TYPE, COSName.PAGE)
                        for (name in listOf(COSName.CONTENTS, COSName.GROUP)) {
                            page.cosObject.getDictionaryObject(name)?.let { dict.setItem(name, clone.cloneForNewDocument(it)) }
                        }
                        page.resources?.let { dict.setItem(COSName.RESOURCES, clone.cloneForNewDocument(it.cosObject)) }
                        val copied = PDPage(dict)
                        fun box(value: PDRectangle): PDRectangle {
                            if (!value.width.isFinite() || !value.height.isFinite() || value.width <= 0 || value.height <= 0)
                                throw PdfPageException(PdfPageIssue.INVALID)
                            return PDRectangle(clone.cloneForNewDocument(value.cosArray) as COSArray)
                        }
                        copied.mediaBox = box(page.mediaBox); copied.cropBox = box(page.cropBox)
                        copied.bleedBox = box(page.bleedBox); copied.trimBox = box(page.trimBox); copied.artBox = box(page.artBox)
                        copied.rotation = (page.rotation + ref.rotation) % 360
                        copied.userUnit = page.userUnit
                        clonedPages[index] = copied
                    } }
                }
            }
            clonedPages.forEach { destination.addPage(it ?: throw PdfPageException(PdfPageIssue.INVALID)) }
            destination.documentInformation.producer = "Birlevo PDF page tools"
            val bounded = object : FilterOutputStream(output) {
                var size = 0L
                override fun write(value: Int) { checkSize(1); out.write(value) }
                override fun write(buffer: ByteArray, offset: Int, count: Int) { checkSize(count); out.write(buffer, offset, count) }
                fun checkSize(count: Int) { size += count; if (size > PdfPagePlan.MAX_BYTES) throw PdfPageException(PdfPageIssue.LIMIT) }
            }
            destination.save(bounded)
            return destination.numberOfPages
        }
    }
}

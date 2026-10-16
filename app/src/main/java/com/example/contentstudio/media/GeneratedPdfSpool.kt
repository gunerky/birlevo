package com.example.contentstudio.media

import android.content.Context
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import com.example.contentstudio.storage.StorageCapacityException
import com.example.contentstudio.storage.StreamingStorage
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFCloneUtility
import com.tom_roush.pdfbox.multipdf.LayerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.OutputStream
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Accepts our own generated PDFs only. External PDF parsing remains in its isolated worker. */
internal class GeneratedPdfSpool(
    context: Context,
    private val folder: File,
    private val storage: StreamingStorage,
    vectorBase: File? = null,
) : Closeable {
    private val document: PDDocument

    init {
        storage.requireRoom(1024 * 1024)
        PDFBoxResourceLoader.init(context.applicationContext)
        if (vectorBase != null) require(
            vectorBase.canonicalFile.parentFile == folder.canonicalFile && vectorBase.name.startsWith("vectors-work-")
        )
        document = if (vectorBase == null) PDDocument(scratch()) else PDDocument.load(vectorBase, scratch())
    }

    private fun scratch(): MemoryUsageSetting {
        val available = storage.limit(Long.MAX_VALUE)
        // PDFBox interprets zero as unlimited storage.
        if (available == 0L) throw StorageCapacityException()
        return MemoryUsageSetting.setupTempFileOnly(available).setTempDir(folder)
    }

    suspend fun append(file: File) {
        require(file.canonicalFile.parentFile == folder.canonicalFile && file.name.startsWith("page-work-"))
        currentCoroutineContext().ensureActive()
        PDDocument.load(file, scratch()).use { source ->
            require(source.numberOfPages == 1)
            val dictionary = COSDictionary(source.getPage(0).cosObject)
            dictionary.removeItem(COSName.PARENT)
            // Clone encoded streams before the source closes; text/vectors and image bytes survive.
            val copied = PDFCloneUtility(document).cloneForNewDocument(dictionary) as COSDictionary
            document.addPage(PDPage(copied))
        }
        currentCoroutineContext().ensureActive()
    }

    suspend fun save(file: File) = writePdfFile(file, storage) { document.save(it) }

    /** Loaded native pages have finished font subsetting; copy encoded image streams as a form. */
    suspend fun overlay(pageIndex: Int, file: File) {
        require(file.canonicalFile.parentFile == folder.canonicalFile && file.name.startsWith("page-work-"))
        currentCoroutineContext().ensureActive()
        PDDocument.load(file, scratch()).use { source ->
            require(source.numberOfPages == 1)
            val form = LayerUtility(document).importPageAsForm(source, 0)
            PDPageContentStream(document, document.getPage(pageIndex),
                PDPageContentStream.AppendMode.APPEND, true, true).use { stream -> stream.drawForm(form) }
        }
        currentCoroutineContext().ensureActive()
    }

    override fun close() = document.close()
}

/** Both native PDF and PDFBox output use cancellation, live disk checks and descriptor sync. */
internal suspend fun writePdfFile(file: File, storage: StreamingStorage, write: (OutputStream) -> Unit) {
    val operation = currentCoroutineContext()
    FileOutputStream(file).use { stream ->
        val target = object : FilterOutputStream(storage.guard(stream)) {
            override fun write(value: Int) { operation.ensureActive(); out.write(value) }
            override fun write(bytes: ByteArray, offset: Int, count: Int) {
                operation.ensureActive(); out.write(bytes, offset, count)
            }
            // PDFBox closes its destination; retain the descriptor until sync.
            override fun close() = flush()
        }
        write(target)
        target.flush()
        operation.ensureActive()
        stream.fd.sync()
    }
}

internal suspend fun writeGeneratedPdfPage(
    file: File,
    width: Int,
    height: Int,
    number: Int,
    storage: StreamingStorage,
    draw: (Canvas) -> Unit,
) {
    val native = PdfDocument()
    try {
        val page = native.startPage(PdfDocument.PageInfo.Builder(width, height, number).create())
        try { draw(page.canvas) } finally { native.finishPage(page) }
        writePdfFile(file, storage) { native.writeTo(it) }
    } finally { native.close() }
}

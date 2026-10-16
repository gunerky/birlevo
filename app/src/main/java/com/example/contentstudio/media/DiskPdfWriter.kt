package com.example.contentstudio.media

import android.content.Context
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import com.example.contentstudio.storage.*
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFCloneUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import java.io.File
import java.io.FileOutputStream
import java.io.FilterOutputStream
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Trusted generated pages only. Untrusted external PDFs remain in their isolated worker. */
internal class DiskPdfWriter(private val context: Context, private val folder: File,
    private val capacity: (File) -> StreamingStorage) {
    suspend fun create(output: File, pages: Int, progress: (Int) -> Unit, batchSize: Int = 1,
        render: suspend (Int, Int, File, StreamingStorage) -> Unit): PresentationPdfResult {
        require(pages in 1..100 && output.canonicalFile.parentFile == folder.canonicalFile)
        require(batchSize in 1..8)
        val temporary = File(folder, "${output.name}.part")
        val operation = currentCoroutineContext()
        try {
            val storage = capacity(folder)
            storage.requireRoom(1024 * 1024)
            fun scratch(): MemoryUsageSetting {
                val available = storage.limit(Long.MAX_VALUE)
                // PDFBox interprets zero as unlimited, rather than no space.
                if (available == 0L) throw StorageCapacityException()
                return MemoryUsageSetting.setupTempFileOnly(available).setTempDir(folder)
            }
            PDFBoxResourceLoader.init(context.applicationContext)
            PDDocument(scratch()).use { document ->
                var first = 0
                while (first < pages) {
                    val count = minOf(batchSize, pages - first)
                    operation.ensureActive()
                    storage.requireRoom(1024 * 1024)
                    val page = File.createTempFile("page-work-", ".pdf", folder)
                    try {
                        render(first, count, page, storage)
                        operation.ensureActive()
                        PDDocument.load(page, scratch()).use { encoded ->
                            check(encoded.numberOfPages == count)
                            document.version = maxOf(document.version, encoded.version)
                            val clone = PDFCloneUtility(document)
                            repeat(count) { local ->
                                operation.ensureActive()
                                storage.requireRoom(1024 * 1024)
                                val dictionary = COSDictionary(encoded.getPage(local).cosObject)
                                dictionary.removeItem(COSName.PARENT)
                                // Copy encoded streams before closing their source. Text/vector/font
                                // resources survive; images are not rasterized or encoded again.
                                val copied = clone.cloneForNewDocument(dictionary) as COSDictionary
                                document.addPage(PDPage(copied))
                                progress((first + local + 1) * 100 / pages)
                            }
                        }
                    } finally { page.delete() }
                    first += count
                }
                operation.ensureActive()
                FileOutputStream(temporary).use { stream ->
                    // PDFBox closes its destination. Keep the descriptor open until sync.
                    val target = object : FilterOutputStream(storage.guard(stream)) {
                        override fun write(value: Int) { operation.ensureActive(); out.write(value) }
                        override fun write(bytes: ByteArray, offset: Int, count: Int) {
                            operation.ensureActive(); out.write(bytes, offset, count)
                        }
                        override fun close() = flush()
                    }
                    document.save(target)
                    stream.fd.sync()
                }
            }
            operation.ensureActive()
            check(temporary.renameTo(output))
            return PresentationPdfResult(output, pages, output.length())
        } catch (failure: Throwable) { output.delete(); throw failure }
        finally { temporary.delete() }
    }

    companion object {
        fun nativePage(target: File, info: PdfDocument.PageInfo, storage: StreamingStorage,
            draw: (Canvas) -> Unit) = nativePages(target, listOf(info), storage) { _, canvas -> draw(canvas) }

        fun nativePages(target: File, infos: List<PdfDocument.PageInfo>, storage: StreamingStorage,
            draw: (Int, Canvas) -> Unit) {
            val native = PdfDocument()
            try {
                infos.forEachIndexed { index, info ->
                    val page = native.startPage(info)
                    try { draw(index,page.canvas) } finally { native.finishPage(page) }
                }
                target.outputStream().use { native.writeTo(storage.guard(it)) }
            } finally { native.close() }
        }
    }
}

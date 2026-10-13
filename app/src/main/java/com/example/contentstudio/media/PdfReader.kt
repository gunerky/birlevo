package com.example.contentstudio.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.File
import kotlinx.coroutines.*

data class PdfSource(val file: File, val originalUri: Uri, val name: String, val bytes: Long, val pages: Int)
data class PdfPageImage(val file: File, val pageIndex: Int, val edge: Int,
    val format: ImageOutputFormat = ImageOutputFormat.PNG, val quality: Int = 80)
data class ImportedPdf(val source: PdfSource, val preview: Bitmap)

class PdfReader(private val context: Context) {
    private val client = PdfRenderClient(context)
    private val folder get() = File(context.noBackupFilesDir, "pdf-reader").apply { check(isDirectory || mkdirs()) }
    private fun temporary(prefix: String, extension: String) = File.createTempFile(prefix, extension, folder)

    suspend fun importPdf(uri: Uri): ImportedPdf {
        val source = temporary("source-", ".pdf")
        var preview: Bitmap? = null
        var committed = false
        try {
            val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }?.take(256) ?: "document.pdf"
            val input = context.contentResolver.openInputStream(uri) ?: throw PdfReadException(PdfReadIssue.ACCESS)
            input.use { stream -> source.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = stream.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > PdfReaderPolicy.MAX_BYTES) throw PdfReadException(PdfReadIssue.LIMIT)
                    output.write(buffer, 0, count)
                }
                if (total == 0L) throw PdfReadException(PdfReadIssue.INVALID)
            } }
            val raster = temporary("preview-", ".png")
            val pages: Int
            try {
                val info = client.render(source, raster, 0, PdfReaderPolicy.PREVIEW_EDGE)
                pages = info.pages
                preview = decodePreview(raster, info)
            } finally { raster.delete() }
            currentCoroutineContext().ensureActive()
            return ImportedPdf(PdfSource(source, uri, name, source.length(), pages), checkNotNull(preview)).also { committed = true }
        } catch (_: SecurityException) { throw PdfReadException(PdfReadIssue.ACCESS) }
        catch (_: java.io.IOException) { throw PdfReadException(PdfReadIssue.ACCESS) }
        finally { if (!committed) { source.delete(); preview?.recycle() } }
    }

    suspend fun preview(source: PdfSource, index: Int): Bitmap {
        require(index in 0 until source.pages)
        val output = temporary("preview-", ".png")
        try {
            val info = client.render(source.file, output, index, PdfReaderPolicy.PREVIEW_EDGE)
            if (info.pages != source.pages) throw PdfReadException(PdfReadIssue.INVALID)
            return decodePreview(output, info)
        } finally { output.delete() }
    }

    suspend fun pageImage(source: PdfSource, index: Int, edge: Int,
        format: ImageOutputFormat = ImageOutputFormat.PNG, quality: Int = 80): PdfPageImage {
        require(index in 0 until source.pages && edge in PdfReaderPolicy.outputEdges &&
            format in PdfReaderPolicy.imageFormats && quality in 40..95)
        val output = temporary("page-", ".${format.extension}")
        var committed = false
        try {
            val info = client.render(source.file, output, index, edge, format, quality)
            validateRaster(output, info, format)
            if (info.pages != source.pages) throw PdfReadException(PdfReadIssue.INVALID)
            currentCoroutineContext().ensureActive()
            return PdfPageImage(output, index, edge, format, quality).also { committed = true }
        } finally { if (!committed) output.delete() }
    }

    private fun validateRaster(file: File, info: PdfRasterInfo, format: ImageOutputFormat = ImageOutputFormat.PNG): BitmapFactory.Options {
        if (file.length() !in 1..PdfReaderPolicy.MAX_BYTES) throw PdfReadException(PdfReadIssue.LIMIT)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outMimeType != format.mime || bounds.outWidth != info.width || bounds.outHeight != info.height)
            throw PdfReadException(PdfReadIssue.INVALID)
        return bounds
    }

    private suspend fun decodePreview(file: File, info: PdfRasterInfo): Bitmap {
        validateRaster(file, info)
        val bitmap = BitmapFactory.decodeFile(file.path) ?: throw PdfReadException(PdfReadIssue.INVALID)
        try { currentCoroutineContext().ensureActive(); return bitmap }
        catch (error: Throwable) { bitmap.recycle(); throw error }
    }

    /** Explicit copy/image creation, never described as compression. No write access to the source. */
    suspend fun export(file: File, original: Uri, destination: Uri) {
        require(destination != original) { "Cannot overwrite source PDF" }
        try {
            context.contentResolver.openOutputStream(destination, "w")?.use { output -> file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
                output.flush()
                currentCoroutineContext().ensureActive()
            } } ?: throw PdfReadException(PdfReadIssue.ACCESS)
        } catch (error: Throwable) {
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, destination) }
            throw error
        }
    }
}

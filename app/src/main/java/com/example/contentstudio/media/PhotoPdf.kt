package com.example.contentstudio.media

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import androidx.core.net.toUri
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

data class PhotoPage(val id: String, val file: File, val sourceUri: Uri, val name: String, val bytes: Long) {
    val uri: Uri get() = Uri.fromFile(file)
}

/** One source snapshot and one decoded page at a time; call on Dispatchers.IO. */
class PhotoPdf(private val context: Context) {
    companion object { const val MAX_PAGES = 100; const val MAX_BATCH_BYTES = 512L * 1024 * 1024 }
    private val folder by lazy { File(context.noBackupFilesDir, "photo-pdf").apply { check(isDirectory || mkdirs()) } }
    private val pdf = PresentationPdf(context)

    suspend fun importPages(uris: List<Uri>, room: Int, remainingBytes: Long, progress: (Int) -> Unit): List<PhotoPage> {
        if (uris.isEmpty() || uris.size > room) throw MediaImportException(ImportIssue.PHOTO_LIMIT)
        val pages = mutableListOf<PhotoPage>()
        var current: File? = null
        var total = 0L
        try {
            uris.forEachIndexed { index, uri ->
                currentCoroutineContext().ensureActive()
                val bounds = SlideImages.validate(context, uri)
                val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0) else null
                }?.take(256) ?: "image.${ImageFormats.extension(bounds.outMimeType)}"
                val file = File.createTempFile("page-", ".${ImageFormats.extension(bounds.outMimeType)}", folder)
                current = file
                (context.contentResolver.openInputStream(uri) ?: throw MediaImportException(ImportIssue.FILE_ACCESS)).use { input ->
                    FileOutputStream(file).use { output ->
                        val buffer = ByteArray(65536)
                        var bytes = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val size = input.read(buffer)
                            if (size < 0) break
                            bytes += size
                            if (bytes > FormatSupport.MAX_IMAGE_BYTES) throw MediaImportException(ImportIssue.IMAGE_LIMIT)
                            if (total + bytes > remainingBytes) throw MediaImportException(ImportIssue.PHOTO_LIMIT)
                            output.write(buffer, 0, size)
                        }
                        if (bytes <= 0) throw MediaImportException(ImportIssue.INVALID_MEDIA)
                        output.fd.sync()
                    }
                }
                SlideImages.validate(context, Uri.fromFile(file))
                total += file.length()
                pages += PhotoPage(UUID.randomUUID().toString(), file, uri, name, file.length())
                current = null
                progress((index + 1) * 100 / uris.size)
            }
            currentCoroutineContext().ensureActive()
            return pages
        } catch (error: Throwable) { current?.delete(); pages.forEach { it.file.delete() }; throw error }
    }

    suspend fun create(pages: List<PhotoPage>, maxEdge: Int, progress: (Int) -> Unit): PresentationPdfResult {
        require(pages.size in 1..MAX_PAGES && maxEdge in listOf(2048, 4096))
        val output = File.createTempFile("output-", ".pdf", folder)
        val temporary = File(folder, "${output.name}.part")
        val document = PdfDocument()
        try {
            pages.forEachIndexed { index, source ->
                currentCoroutineContext().ensureActive()
                val bitmap = SlideImages.decode(context, source.uri, maxEdge)
                try {
                    val landscape = bitmap.width > bitmap.height
                    val width = if (landscape) 842 else 595
                    val height = if (landscape) 595 else 842
                    val page = document.startPage(PdfDocument.PageInfo.Builder(width, height, index + 1).create())
                    try {
                        page.canvas.drawColor(Color.WHITE)
                        val scale = minOf((width - 48f) / bitmap.width, (height - 48f) / bitmap.height)
                        val imageWidth = bitmap.width * scale
                        val imageHeight = bitmap.height * scale
                        val left = (width - imageWidth) / 2
                        val top = (height - imageHeight) / 2
                        page.canvas.drawBitmap(bitmap, null, RectF(left, top, left + imageWidth, top + imageHeight),
                            Paint(Paint.FILTER_BITMAP_FLAG))
                    } finally { document.finishPage(page) }
                } finally { bitmap.recycle() }
                progress((index + 1) * 100 / pages.size)
            }
            currentCoroutineContext().ensureActive()
            FileOutputStream(temporary).use { document.writeTo(it); it.fd.sync() }
            currentCoroutineContext().ensureActive()
            check(temporary.renameTo(output))
            return PresentationPdfResult(output, pages.size, output.length())
        } catch (error: Throwable) { output.delete(); throw error }
        finally { document.close(); temporary.delete() }
    }

    suspend fun render(result: PresentationPdfResult, index: Int): android.graphics.Bitmap {
        currentCoroutineContext().ensureActive()
        require(index in 0 until result.pages)
        PdfRenderer(ParcelFileDescriptor.open(result.file, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
            renderer.openPage(index).use { page ->
                val ratio = 1280f / maxOf(page.width, page.height)
                val bitmap = android.graphics.Bitmap.createBitmap(maxOf(1, (page.width * ratio).toInt()),
                    maxOf(1, (page.height * ratio).toInt()), android.graphics.Bitmap.Config.ARGB_8888)
                try {
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    currentCoroutineContext().ensureActive()
                    return bitmap
                } catch (error: Throwable) { bitmap.recycle(); throw error }
            }
        }
    }
    suspend fun export(result: PresentationPdfResult, pages: List<PhotoPage>, destination: Uri) {
        require(pages.none { it.sourceUri == destination }) { "Cannot overwrite original" }
        pdf.export(result, destination)
    }
    suspend fun restore(session: PhotoSession): List<PhotoPage> = session.assets.map { asset ->
        currentCoroutineContext().ensureActive()
        val file = asset.resolve(folder, "page-", FormatSupport.MAX_IMAGE_BYTES)
        SlideImages.validate(context, Uri.fromFile(file))
        PhotoPage(asset.id, file, asset.uri.toUri(), asset.name, file.length())
    }

    fun cleanOldCache(retained: Set<File> = emptySet()) {
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        folder.listFiles()?.filter { it !in retained && it.name != "session.json" && !it.name.startsWith("session.json.") && it.lastModified() < cutoff }?.forEach { it.delete() }
    }
}

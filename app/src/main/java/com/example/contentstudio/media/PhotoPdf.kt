package com.example.contentstudio.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import androidx.core.net.toUri
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import com.example.contentstudio.storage.*

data class PhotoPage(val id: String, val file: File, val sourceUri: Uri, val name: String, val bytes: Long) {
    val uri: Uri get() = Uri.fromFile(file)
}

/** One decoded/native page at a time; encoded pages spool to private disk. Call on Dispatchers.IO. */
class PhotoPdf(private val context: Context,
    private val capacity: (File) -> StreamingStorage = ::deviceStorage) {
    companion object { const val MAX_PAGES = 100; const val MAX_BATCH_BYTES = 512L * 1024 * 1024 }
    private val folder by lazy { File(context.noBackupFilesDir, "photo-pdf").apply { check(isDirectory || mkdirs()) } }
    private val pdf = PresentationPdf(context)

    suspend fun importPages(uris: List<Uri>, room: Int, remainingBytes: Long, progress: (Int) -> Unit) =
        ImageSnapshots(context, folder).importPages(uris, room, remainingBytes, progress)

    fun restoreOutput(file: File, bytes: Long, pages: Int): PresentationPdfResult {
        require(file.canonicalFile.parentFile == folder.canonicalFile && file.name.startsWith("output-") && file.length() == bytes)
        PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { require(it.pageCount == pages) }
        return PresentationPdfResult(file, pages, bytes)
    }
    suspend fun create(pages: List<PhotoPage>, maxEdge: Int, progress: (Int) -> Unit): PresentationPdfResult {
        require(pages.size in 1..MAX_PAGES && maxEdge in listOf(2048, 4096))
        val output = File.createTempFile("output-", ".pdf", folder)
        return DiskPdfWriter(context, folder, capacity).create(output, pages.size, progress) { index, _, target, storage ->
            val bitmap = SlideImages.decode(context, pages[index].uri, maxEdge)
            try {
                storage.requireRoom(MediaResourcePlan.rasterDisk(bitmap.width, bitmap.height) * 3)
                writePage(bitmap, index + 1, target, storage)
            } finally { bitmap.recycle() }
        }
    }

    private fun writePage(bitmap: Bitmap, number: Int, target: File, storage: StreamingStorage) {
        val landscape = bitmap.width > bitmap.height
        val width = if (landscape) 842 else 595
        val height = if (landscape) 595 else 842
        DiskPdfWriter.nativePage(target, PdfDocument.PageInfo.Builder(width, height, number).create(), storage) { canvas ->
                canvas.drawColor(Color.WHITE)
                val scale = minOf((width - 48f) / bitmap.width, (height - 48f) / bitmap.height)
                val imageWidth = bitmap.width * scale
                val imageHeight = bitmap.height * scale
                val left = (width - imageWidth) / 2
                val top = (height - imageHeight) / 2
                canvas.drawBitmap(bitmap, null, RectF(left, top, left + imageWidth, top + imageHeight),
                    Paint(Paint.FILTER_BITMAP_FLAG))
        }
    }

    suspend fun render(result: PresentationPdfResult, index: Int): android.graphics.Bitmap {
        currentCoroutineContext().ensureActive()
        require(index in 0 until result.pages)
        PdfRenderer(ParcelFileDescriptor.open(result.file, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
            renderer.openPage(index).use { page ->
                val ratio = 1280f / maxOf(page.width, page.height)
                deviceMemory(context).requireBytes(MediaResourcePlan.rasterMemory(
                    maxOf(1,(page.width*ratio).toInt()),maxOf(1,(page.height*ratio).toInt())))
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

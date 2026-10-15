package com.example.contentstudio.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import com.example.contentstudio.data.ProjectKind
import com.example.contentstudio.data.StudioProject
import java.io.File
import java.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

data class PresentationPdfResult(val file: File, val pages: Int, val bytes: Long)

class PdfTextOverflow(val slideNumber: Int) : IllegalArgumentException("Slide text does not fit")

class PdfEmptyPresentation : IllegalArgumentException("Presentation has no content")

/**
 * Fixed slide geometry independent of device density and UI font size. Native fonts shape all text.
 */
class PresentationPdf(private val context: Context) {
    private val folder by lazy {
        File(context.noBackupFilesDir, "presentation-pdf").apply {
            check(isDirectory || mkdirs()) { "Media cache unavailable" }
        }
    }

    suspend fun create(project: StudioProject, onProgress: (Int) -> Unit): PresentationPdfResult =
        createPages(project, project.slides.indices.toList(), allowEmpty = false, onProgress)

    suspend fun previewSlide(project: StudioProject, index: Int): Bitmap {
        require(index in project.slides.indices)
        val result = createPages(project, listOf(index), allowEmpty = true) {}
        try {
            return render(result, 0)
        } finally {
            result.file.delete()
        }
    }

    private suspend fun createPages(
        project: StudioProject,
        indices: List<Int>,
        allowEmpty: Boolean,
        onProgress: (Int) -> Unit,
    ): PresentationPdfResult {
        require(project.kind == ProjectKind.PRESENTATION && project.slides.size in 1..100)
        require(
            project.title.length <= 500 &&
                project.slides.sumOf { it.heading.length + it.body.length } <= 200_000
        )
        require(project.slides.all { it.heading.length <= 1000 && it.body.length <= 8000 })
        if (
            !allowEmpty &&
                project.slides.all {
                    it.heading.isBlank() && it.body.isBlank() && it.imageUri == null
                }
        )
            throw PdfEmptyPresentation()
        val file = File.createTempFile("slides-", ".pdf", folder)
        val document = PdfDocument()
        try {
            indices.forEachIndexed { pageIndex, index ->
                currentCoroutineContext().ensureActive()
                val page =
                    document.startPage(
                        PdfDocument.PageInfo.Builder(PresentationLayout.WIDTH, PresentationLayout.HEIGHT, pageIndex + 1).create()
                    )
                try {
                    drawSlide(page.canvas, project, index, 1024)
                } finally {
                    document.finishPage(page)
                }
                onProgress((pageIndex + 1) * 100 / indices.size)
            }
            currentCoroutineContext().ensureActive()
            file.outputStream().use { document.writeTo(it) }
            currentCoroutineContext().ensureActive()
            return PresentationPdfResult(file, indices.size, file.length())
        } catch (error: Throwable) {
            file.delete()
            throw error
        } finally {
            document.close()
        }
    }

    /** Undelivered bitmap owned by caller; no low-resolution PDF round-trip for video output. */
    suspend fun renderSlideBitmap(
        project: StudioProject,
        index: Int,
        width: Int,
        height: Int,
        crop: Boolean = false,
    ): Bitmap {
        require(index in project.slides.indices && width in 16..4096 && height in 16..4096)
        currentCoroutineContext().ensureActive()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = android.graphics.Canvas(bitmap)
            canvas.drawColor(Color.BLACK)
            val scale =
                if (crop) maxOf(width.toFloat() / PresentationLayout.WIDTH, height.toFloat() / PresentationLayout.HEIGHT)
                else minOf(width.toFloat() / PresentationLayout.WIDTH, height.toFloat() / PresentationLayout.HEIGHT)
            canvas.translate((width - PresentationLayout.WIDTH * scale) / 2, (height - PresentationLayout.HEIGHT * scale) / 2)
            canvas.scale(scale, scale)
            canvas.clipRect(0f, 0f, PresentationLayout.WIDTH.toFloat(), PresentationLayout.HEIGHT.toFloat())
            drawSlide(
                canvas,
                project,
                index,
                minOf(4096, kotlin.math.ceil(PresentationLayout.WIDTH * scale * 2).toInt()),
            )
            currentCoroutineContext().ensureActive()
            return bitmap
        } catch (failure: Throwable) {
            bitmap.recycle()
            throw failure
        }
    }

    private fun drawSlide(
        canvas: android.graphics.Canvas,
        project: StudioProject,
        index: Int,
        imageEdge: Int,
    ) {
        val slide = project.slides[index]
        val palette = project.theme.palette
        val image = slide.imageUri?.let { SlideImages.decode(context, Uri.parse(it), imageEdge) }
        try {
            val measured = PresentationLayout.measure(project, index, image != null)
            val geometry = measured.geometry
            canvas.drawColor(palette.background)
            val rule = android.graphics.Paint().apply { color = palette.accent }
            canvas.drawRect(64f, 56f, 104f, 61f, rule)
            measured.caption.draw(canvas)
            measured.heading?.draw(canvas)
            measured.body?.draw(canvas)
            image?.let { bitmap ->
                val box = requireNotNull(geometry.image)
                val scale = minOf(box.width / bitmap.width, box.height / bitmap.height)
                val width = bitmap.width * scale
                val height = bitmap.height * scale
                val left = box.left + (box.width - width) / 2f
                val top = box.top + (box.height - height) / 2f
                canvas.drawBitmap(
                    bitmap,
                    null,
                    RectF(left, top, left + width, top + height),
                    android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG),
                )
            }
            val footer =
                android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = palette.muted
                    textSize = 14f
                    textAlign = android.graphics.Paint.Align.RIGHT
                }
            canvas.drawText("${index + 1} / ${project.slides.size}", 896f, 510f, footer)
        } finally {
            image?.recycle()
        }
    }

    suspend fun render(result: PresentationPdfResult, pageIndex: Int): Bitmap {
        require(pageIndex in 0 until result.pages)
        currentCoroutineContext().ensureActive()
        val descriptor = ParcelFileDescriptor.open(result.file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer =
            try {
                PdfRenderer(descriptor)
            } catch (error: Throwable) {
                descriptor.close()
                throw error
            }
        try {
            require(renderer.pageCount == result.pages)
            val page = renderer.openPage(pageIndex)
            try {
                val bitmap = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888)
                try {
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    currentCoroutineContext().ensureActive()
                    return bitmap
                } catch (error: Throwable) {
                    bitmap.recycle()
                    throw error
                }
            } finally {
                page.close()
            }
        } finally {
            renderer.close()
        }
    }

    suspend fun export(result: PresentationPdfResult, destination: Uri) {
        try {
            context.contentResolver.openOutputStream(destination, "w")?.use { output ->
                result.file.inputStream().use { input ->
                    val buffer = ByteArray(65536)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                    output.flush()
                    currentCoroutineContext().ensureActive()
                }
            } ?: throw IOException("Destination unavailable")
        } catch (error: Throwable) {
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, destination) }
            throw error
        }
    }

    fun cleanOldCache() {
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        folder.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
    }
}

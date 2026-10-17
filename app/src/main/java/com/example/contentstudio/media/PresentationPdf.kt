package com.example.contentstudio.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import com.example.contentstudio.data.ProjectKind
import com.example.contentstudio.data.StudioProject
import com.example.contentstudio.data.SlideImageBox
import com.example.contentstudio.data.slideGeometry
import com.example.contentstudio.storage.*
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
class PresentationPdf(
    private val context: Context,
    private val capacity: (File) -> StreamingStorage = ::deviceStorage,
    private val memory: WorkingMemory = deviceMemory(context),
) {
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
        val temporary = File(folder, "${file.name}.part")
        try {
            val storage = capacity(folder)
            storage.requireRoom(1024 * 1024)
            suspend fun page(index: Int, number: Int, target: File) {
                currentCoroutineContext().ensureActive()
                storage.requireRoom(if (project.slides[index].imageUri != null)
                    MediaResourcePlan.rasterDisk(1024, 1024) * 3 else 1024 * 1024L)
                writeGeneratedPdfPage(target, PresentationLayout.WIDTH, PresentationLayout.HEIGHT, number, storage) {
                    drawSlide(it, project, index, 1024)
                }
            }
            if (indices.size == 1) {
                // Single-page previews need no PDFBox round-trip.
                page(indices.single(), 1, temporary)
                onProgress(100)
            } else writeMultipage(project, indices, temporary, storage, onProgress)
            currentCoroutineContext().ensureActive()
            check(temporary.renameTo(file))
            return PresentationPdfResult(file, indices.size, file.length())
        } catch (error: Throwable) {
            file.delete()
            throw error
        } finally {
            temporary.delete()
        }
    }

    private suspend fun writeMultipage(project: StudioProject, indices: List<Int>, target: File,
        storage: StreamingStorage, onProgress: (Int) -> Unit) {
        val vectors = File.createTempFile("vectors-work-", ".pdf", folder)
        try {
            // Share font subsets across all pages, without retaining any source bitmap in Skia.
            val native = PdfDocument()
            try {
                indices.forEachIndexed { pageIndex, index ->
                    currentCoroutineContext().ensureActive()
                    storage.requireRoom(1024 * 1024)
                    val page = native.startPage(PdfDocument.PageInfo.Builder(
                        PresentationLayout.WIDTH, PresentationLayout.HEIGHT, pageIndex + 1).create())
                    try { drawSlide(page.canvas, project, index, 1024, paintImage = false) }
                    finally { native.finishPage(page) }
                }
                writePdfFile(vectors, storage) { native.writeTo(it) }
            } finally { native.close() }
            GeneratedPdfSpool(context, folder, storage, vectors).use { document ->
                indices.forEachIndexed { pageIndex, index ->
                    currentCoroutineContext().ensureActive()
                    project.slides[index].imageUri?.let { uri ->
                        storage.requireRoom(MediaResourcePlan.rasterDisk(1024, 1024) * 3)
                        val singlePage = File.createTempFile("page-work-", ".pdf", folder)
                        try {
                            val image = SlideImages.decode(context, Uri.parse(uri), 1024)
                            try {
                                writeGeneratedPdfPage(singlePage, PresentationLayout.WIDTH,
                                    PresentationLayout.HEIGHT, pageIndex + 1, storage) { canvas ->
                                    drawImage(canvas, image, requireNotNull(slideGeometry(project.slides[index].layout, true).image))
                                }
                            } finally { image.recycle() }
                            document.overlay(pageIndex, singlePage)
                        } finally { singlePage.delete() }
                    }
                    onProgress((pageIndex + 1) * 100 / indices.size)
                }
                document.save(target)
            }
        } finally { vectors.delete() }
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
        memory.requireBytes(MediaResourcePlan.rasterMemory(width, height))
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
        paintImage: Boolean = true,
    ) {
        val slide = project.slides[index]
        val palette = project.theme.palette
        val image = if (paintImage) slide.imageUri?.let { SlideImages.decode(context, Uri.parse(it), imageEdge) } else null
        try {
            val measured = PresentationLayout.measure(project, index, slide.imageUri != null)
            val geometry = measured.geometry
            canvas.drawColor(palette.background)
            val rule = android.graphics.Paint().apply { color = palette.accent }
            canvas.drawRect(64f, 56f, 104f, 61f, rule)
            measured.caption.draw(canvas)
            measured.heading?.draw(canvas)
            measured.body?.draw(canvas)
            image?.let { drawImage(canvas, it, requireNotNull(geometry.image)) }
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

    private fun drawImage(canvas: android.graphics.Canvas, bitmap: Bitmap, box: SlideImageBox) {
        val scale = minOf(box.width / bitmap.width, box.height / bitmap.height)
        val width = bitmap.width * scale
        val height = bitmap.height * scale
        val left = box.left + (box.width - width) / 2f
        val top = box.top + (box.height - height) / 2f
        canvas.drawBitmap(bitmap, null, RectF(left, top, left + width, top + height),
            android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
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
                memory.requireBytes(MediaResourcePlan.rasterMemory(1280, 720))
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
        com.example.contentstudio.storage.OwnedFileCleanup.files(folder, Regex("(?:slides|vectors-work|page-work)-[0-9]+\\.pdf(?:\\.part)?"), emptySet())
    }
}

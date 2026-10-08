package com.example.contentstudio.media

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import com.example.contentstudio.data.*
import java.io.File
import java.io.IOException
import kotlinx.coroutines.*

data class SlideImageOptions(
    val format: ImageOutputFormat = ImageOutputFormat.PNG,
    val edge: Int = 2048,
    val quality: Int = 80,
) {
    init {
        require(edge in listOf(1024, 2048, 4096) && quality in 40..95)
    }

    val height
        get() = edge * 9 / 16
}

/** Creates a new raster of a slide, not a compression claim. Notes never enter output pixels. */
class PresentationImageEngine(private val context: Context) {
    private val folder by lazy {
        File(context.cacheDir, "presentation-images").apply { check(isDirectory || mkdirs()) }
    }
    private val painter = PresentationPdf(context)
    private val images = ImageCompressor(context)

    suspend fun create(
        project: StudioProject,
        slideId: String,
        options: SlideImageOptions,
    ): CompressedImage {
        val visible = project.visualSnapshot()
        val index = visible.slides.indexOfFirst { it.id == slideId }
        require(index >= 0 && visible.kind == ProjectKind.PRESENTATION)
        val file = File.createTempFile("slide-", ".${options.format.extension}", folder)
        var bitmap: android.graphics.Bitmap? = null
        var preview: android.graphics.Bitmap? = null
        var delivered = false
        val start = android.os.SystemClock.elapsedRealtime()
        try {
            bitmap = painter.renderSlideBitmap(visible, index, options.edge, options.height)
            // The renderer fills the entire canvas with opaque black before painting the slide.
            // Avoid a second full-size white-flattening allocation for JPEG.
            bitmap.setHasAlpha(false)
            val coroutine = currentCoroutineContext()
            file.outputStream().use { output ->
                ImageEncoder.write(bitmap, options.format, options.quality, output) {
                    coroutine.ensureActive()
                }
                output.fd.sync()
            }
            bitmap.recycle()
            bitmap = null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            require(
                bounds.outWidth == options.edge &&
                    bounds.outHeight == options.height &&
                    bounds.outMimeType == options.format.mime
            )
            require(file.length() in 1..FormatSupport.MAX_IMAGE_BYTES)
            preview = images.decode(file, 512)
            currentCoroutineContext().ensureActive()
            return CompressedImage(
                    file,
                    file.length(),
                    options.edge,
                    options.height,
                    preview,
                    android.os.SystemClock.elapsedRealtime() - start,
                    options.format,
                )
                .also { delivered = true }
        } finally {
            bitmap?.recycle()
            if (!delivered) {
                file.delete()
                preview?.recycle()
            }
        }
    }

    suspend fun export(project: StudioProject, result: CompressedImage, destination: Uri) {
        require(
            project.slides.none { it.imageUri == destination.toString() } &&
                project.sourceUri != destination.toString()
        )
        require(
            destination != Uri.fromFile(result.file) &&
                result.file.isFile &&
                result.file.length() == result.bytes &&
                result.bytes > 0
        )
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
        } catch (failure: Throwable) {
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, destination) }
            throw failure
        }
    }

    fun cleanOldCache() {
        val cutoff = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        folder
            .listFiles()
            ?.filter { it.isFile && it.name.startsWith("slide-") && it.lastModified() < cutoff }
            ?.forEach { it.delete() }
    }
}

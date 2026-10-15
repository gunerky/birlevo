package com.example.contentstudio.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

data class ImageSource(val file: File, val originalUri: Uri, val name: String,
    val bytes: Long, val width: Int, val height: Int, val preview: Bitmap, val mime: String = "image/jpeg",
    val protectedUris: Set<Uri> = setOf(originalUri))
data class CompressedImage(val file: File, val bytes: Long, val width: Int, val height: Int,
    val preview: Bitmap, val elapsedMs: Long, val format: ImageOutputFormat = ImageOutputFormat.JPEG)

/** Call on Dispatchers.IO. Never writes to the selected source. */
class ImageCompressor(private val context: Context, private val ownedFolder: File? = null) {
    private val folder by lazy { (ownedFolder ?: File(context.noBackupFilesDir, "image-compression")).apply { check(isDirectory || mkdirs()) { "Media cache unavailable" } } }

    suspend fun importImage(uri: Uri, protectedUris: Set<Uri> = emptySet()): ImageSource {
        val snapshot = File.createTempFile("source-", ".image", folder)
        try {
            val name = if (uri.scheme == "file") uri.lastPathSegment ?: "frame.png" else context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            } ?: "image"
            context.contentResolver.openInputStream(uri)?.use { input ->
                snapshot.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > FormatSupport.MAX_IMAGE_BYTES) throw MediaImportException(ImportIssue.IMAGE_LIMIT)
                        output.write(buffer, 0, count)
                    }
                }
            } ?: throw MediaImportException(ImportIssue.FILE_ACCESS)
            val bounds = bounds(snapshot)
            if (ModernImages.isModern(bounds.outMimeType) && android.os.Build.VERSION.SDK_INT >= 28) {
                val image = ModernImages.decode(snapshot, 512)
                try { currentCoroutineContext().ensureActive() } catch (error: Throwable) { image.bitmap.recycle(); throw error }
                return ImageSource(snapshot, uri, name, snapshot.length(), image.width, image.height, image.bitmap, bounds.outMimeType, protectedUris + uri)
            }
            val orientation = orientation(snapshot, bounds.outMimeType)
            val rotated = orientation in 5..8
            return ImageSource(snapshot, uri, name, snapshot.length(),
                if (rotated) bounds.outHeight else bounds.outWidth,
                if (rotated) bounds.outWidth else bounds.outHeight,
                decode(snapshot, 512), bounds.outMimeType, protectedUris + uri)
        } catch (error: Throwable) {
            snapshot.delete()
            throw error
        }
    }

    suspend fun compress(source: ImageSource, quality: Int, maxEdge: Int,
        format: ImageOutputFormat = ImageOutputFormat.JPEG): CompressedImage =
        compressFile(source.file, quality, maxEdge, format)

    /** Batch callers own the file and never deliver its temporary preview to the UI. */
    suspend fun compressFile(source: File, quality: Int, maxEdge: Int,
        format: ImageOutputFormat = ImageOutputFormat.JPEG): CompressedImage {
        require(quality in 40..95 && maxEdge in listOf(1280, 1920, 2560))
        currentCoroutineContext().ensureActive()
        val start = android.os.SystemClock.elapsedRealtime()
        val output = File.createTempFile("output-", ".${format.extension}", folder)
        var decoded: Bitmap? = null
        var preview: Bitmap? = null
        var committed = false
        try {
            decoded = decode(source, maxEdge)
            currentCoroutineContext().ensureActive()
            val coroutine = currentCoroutineContext()
            output.outputStream().use {
                ImageEncoder.write(decoded, format, quality, it) { coroutine.ensureActive() }
            }
            currentCoroutineContext().ensureActive()
            val width = decoded.width
            val height = decoded.height
            val encoded = bounds(output)
            if (encoded.outMimeType != format.mime || encoded.outWidth != width || encoded.outHeight != height ||
                output.length() !in 1..FormatSupport.MAX_IMAGE_BYTES) throw IOException("Invalid image output")
            // Encoding is complete; release large work buffers before decoding the UI preview.
            decoded.recycle()
            decoded = null
            preview = decode(output, 512)
            currentCoroutineContext().ensureActive()
            return CompressedImage(output, output.length(), width, height, preview,
                android.os.SystemClock.elapsedRealtime() - start, format).also { committed = true }
        } catch (error: Throwable) {
            output.delete()
            throw error
        } finally {
            decoded?.recycle()
            if (!committed) preview?.recycle()
        }
    }

    suspend fun export(source: ImageSource, result: CompressedImage, destination: Uri,
        allowNonSmallerCopy: Boolean = false) {
        require(destination !in source.protectedUris) { "Cannot overwrite original" }
        require(allowNonSmallerCopy || compressionBenefit(source.bytes, result.bytes) == CompressionBenefit.SMALLER) {
            "Output did not save space; explicit copy confirmation required"
        }
        try {
            context.contentResolver.openOutputStream(destination, "w")?.use { output ->
                result.file.inputStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
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
            // ACTION_CREATE_DOCUMENT returned a new document: remove partial exports if supported.
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, destination) }
            throw error
        }
    }

    fun cleanOldCache() {
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        folder.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
    }

    private fun bounds(file: File): BitmapFactory.Options = ImageInput.bounds { file.inputStream() }

    internal fun orientation(file: File, mime: String): Int = if (mime == "image/bmp")
        ExifInterface.ORIENTATION_NORMAL else ExifInterface(file).getAttributeInt(
            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)

    internal fun decode(file: File, maxEdge: Int): Bitmap {
        val dimensions = bounds(file)
        if (ModernImages.isModern(dimensions.outMimeType) && android.os.Build.VERSION.SDK_INT >= 28)
            return ModernImages.decode(file, maxEdge).bitmap
        var sample = 1
        // Bound intermediate allocations to approximately 4 MP; never upscale sampled pixels.
        while (dimensions.outWidth.toLong() * dimensions.outHeight / sample / sample > 4_000_000L ||
            max(dimensions.outWidth, dimensions.outHeight) / sample > maxEdge * 2) sample *= 2
        var bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }) ?: throw IOException("Image decode failed")
        try {
            val matrix = Matrix().apply {
                when (orientation(file, dimensions.outMimeType)) {
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                    ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(270f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
                }
            }
            if (!matrix.isIdentity) {
                val oriented = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (oriented !== bitmap) bitmap.recycle()
                bitmap = oriented
            }
            val ratio = minOf(1f, maxEdge.toFloat() / max(bitmap.width, bitmap.height))
            if (ratio < 1f) {
                val resized = Bitmap.createScaledBitmap(bitmap, max(1, (bitmap.width * ratio).roundToInt()),
                    max(1, (bitmap.height * ratio).roundToInt()), true)
                if (resized !== bitmap) bitmap.recycle()
                bitmap = resized
            }
            return bitmap
        } catch (error: Throwable) {
            bitmap.recycle()
            throw error
        }
    }
}

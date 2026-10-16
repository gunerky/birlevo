package com.example.contentstudio.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import java.io.File
import java.io.InputStream
import kotlin.math.roundToInt
import com.example.contentstudio.storage.*

/** Shared byte-level validation; no full-size bitmap is needed to inspect dimensions. */
internal object ImageInput {
    fun bounds(open: () -> InputStream?): BitmapFactory.Options {
        val modern = (open() ?: throw MediaImportException(ImportIssue.FILE_ACCESS)).use(ImageFormats::modernMime)
        if (modern != null) ModernImages.requireAvailable(modern)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        (open() ?: throw MediaImportException(ImportIssue.FILE_ACCESS)).use { BitmapFactory.decodeStream(it, null, bounds) }
        FormatSupport.validateImage(modern ?: bounds.outMimeType, bounds.outWidth, bounds.outHeight)
        if (modern != null) bounds.outMimeType = modern
        if (bounds.outMimeType == "image/webp")
            (open() ?: throw MediaImportException(ImportIssue.FILE_ACCESS)).use(ImageFormats::requireStillWebp)
        return bounds
    }
}

internal data class DecodedImage(val bitmap: Bitmap, val width: Int, val height: Int)

/** Platform still-image decoder. Display orientation is already applied: never apply EXIF twice. */
internal object ModernImages {
    fun isModern(mime: String?) = mime in listOf("image/heic", "image/heif", "image/avif")

    fun requireAvailable(mime: String) {
        if (Build.VERSION.SDK_INT < 28 || (mime == "image/avif" && Build.VERSION.SDK_INT < 31) ||
            (Build.VERSION.SDK_INT >= 29 && !ImageDecoder.isMimeTypeSupported(mime))) throw MediaImportException(ImportIssue.IMAGE_DEVICE)
    }

    @RequiresApi(28)
    fun decode(file: File, maxEdge: Int, memory: WorkingMemory = deviceMemory()): DecodedImage =
        decode(ImageDecoder.createSource(file), maxEdge, memory)

    @RequiresApi(28)
    fun decode(context: Context, uri: Uri, maxEdge: Int): DecodedImage =
        decode(ImageDecoder.createSource(context.contentResolver, uri), maxEdge, deviceMemory(context))

    @RequiresApi(28)
    private fun decode(source: ImageDecoder.Source, maxEdge: Int, memory: WorkingMemory): DecodedImage {
        require(maxEdge in 1..4096)
        var width = 0
        var height = 0
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            width = info.size.width
            height = info.size.height
            FormatSupport.validateImage(info.mimeType, width, height)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
            decoder.setOnPartialImageListener { false }
            val ratio = minOf(1.0, maxEdge.toDouble() / maxOf(width, height))
            val targetWidth = maxOf(1,(width*ratio).roundToInt())
            val targetHeight = maxOf(1,(height*ratio).roundToInt())
            memory.requireBytes(MediaResourcePlan.rasterMemory(targetWidth,targetHeight))
            decoder.setTargetSize(targetWidth,targetHeight)
        }
        return DecodedImage(bitmap, width, height)
    }
}

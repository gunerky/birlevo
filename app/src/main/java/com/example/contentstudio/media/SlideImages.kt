package com.example.contentstudio.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import java.io.IOException
import com.example.contentstudio.storage.*
import kotlin.math.roundToInt

/** Reads a persisted document URI without loading the full-resolution image into memory. */
object SlideImages {
    fun validate(context: Context, uri: Uri): BitmapFactory.Options {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) {
                if (cursor.getLong(0) > FormatSupport.MAX_IMAGE_BYTES) throw MediaImportException(ImportIssue.IMAGE_LIMIT)
                if (cursor.getLong(0) <= 0) throw MediaImportException(ImportIssue.INVALID_MEDIA)
            }
        }
        return ImageInput.bounds { context.contentResolver.openInputStream(uri) }
    }

    fun decode(context: Context, uri: Uri, maxEdge: Int): Bitmap {
        require(maxEdge in 1..4096)
        val bounds = validate(context, uri)
        if (android.os.Build.VERSION.SDK_INT >= 28 && (ModernImages.isModern(bounds.outMimeType) ||
                (android.os.Build.VERSION.SDK_INT >= 29 && android.graphics.ImageDecoder.isMimeTypeSupported(bounds.outMimeType))))
            return ModernImages.decode(context, uri, maxEdge).bitmap
        val plan = MediaResourcePlan.decode(bounds.outWidth, bounds.outHeight, maxEdge)
        deviceMemory(context).requireBytes(plan.memory)
        val options = BitmapFactory.Options().apply {
            inSampleSize = plan.sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        var bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: throw IOException("Image unavailable")
        try {
            val orientation = if (bounds.outMimeType == "image/bmp") ExifInterface.ORIENTATION_NORMAL else context.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
            val matrix = Matrix().apply {
                when (orientation) {
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
            val ratio = minOf(1.0, maxEdge.toDouble() / maxOf(bitmap.width, bitmap.height))
            if (ratio < 1.0) {
                val resized = Bitmap.createScaledBitmap(bitmap, maxOf(1, (bitmap.width * ratio).roundToInt()),
                    maxOf(1, (bitmap.height * ratio).roundToInt()), true)
                if (resized !== bitmap) bitmap.recycle()
                bitmap = resized
            }
            return bitmap
        } catch (error: Throwable) { bitmap.recycle(); throw error }
    }
}

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
        if (ModernImages.isModern(bounds.outMimeType) && android.os.Build.VERSION.SDK_INT >= 28)
            return ModernImages.decode(context, uri, maxEdge).bitmap
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxEdge) sample *= 2
        deviceMemory(context).requireBytes(MediaResourcePlan.rasterMemory(
            ((bounds.outWidth.toLong()+sample-1)/sample).toInt(),((bounds.outHeight.toLong()+sample-1)/sample).toInt()))
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
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
            if (matrix.isIdentity) return bitmap
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
                if (it !== bitmap) bitmap.recycle()
            }
        } catch (error: Throwable) { bitmap.recycle(); throw error }
    }
}

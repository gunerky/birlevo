package com.example.contentstudio.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import java.io.IOException
import java.io.OutputStream
import com.example.contentstudio.storage.*

/** Shared by image conversion and the isolated PDF worker. Never recycles its input. */
internal object ImageEncoder {
    fun write(bitmap: Bitmap, format: ImageOutputFormat, quality: Int, output: OutputStream,
        memory: WorkingMemory = deviceMemory(),
        checkActive: () -> Unit = {}) {
        require(quality in 40..95)
        checkActive()
        if (format == ImageOutputFormat.BMP) {
            BmpWriter.write(bitmap.width, bitmap.height, output,
                { y, row -> bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1) }, checkActive)
            return
        }
        val flattened = if (format == ImageOutputFormat.JPEG && bitmap.hasAlpha()) {
            memory.requireBytes(MediaResourcePlan.pixels(bitmap.width,bitmap.height)+2L*1024*1024)
            Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888).also {
                Canvas(it).apply { drawColor(Color.WHITE); drawBitmap(bitmap, 0f, 0f, null) }
            }
        } else bitmap
        try {
            val codec = when (format) {
                ImageOutputFormat.JPEG -> Bitmap.CompressFormat.JPEG
                ImageOutputFormat.PNG -> Bitmap.CompressFormat.PNG
                ImageOutputFormat.WEBP -> if (android.os.Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY
                    else @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
                ImageOutputFormat.BMP -> error("BMP handled above")
            }
            if (!flattened.compress(codec, quality, output)) throw IOException("Image encode failed")
            checkActive()
        } finally { if (flattened !== bitmap) flattened.recycle() }
    }
}

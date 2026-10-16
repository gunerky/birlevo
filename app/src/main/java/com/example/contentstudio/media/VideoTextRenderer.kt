package com.example.contentstudio.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.common.OverlaySettings
import androidx.media3.effect.StaticOverlaySettings
import kotlin.math.ceil

/** System font shaping/wrapping, reused by export. No frame-by-frame bitmap allocation. */
object VideoTextRenderer {
    fun layout(layer: VideoTextLayer, width: Int, height: Int): StaticLayout {
        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            color = layer.color.argb
            textSize = minOf(width, height) * layer.size
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            // Contrasting shadow keeps pale and dark colors legible on footage.
            val pale = Color.red(color) + Color.green(color) + Color.blue(color) > 382
            setShadowLayer(maxOf(1f, textSize * .08f), 0f, 0f, if (pale) Color.BLACK else Color.WHITE)
        }
        return StaticLayout.Builder.obtain(layer.text, 0, layer.text.length, paint, maxOf(1, minOf((width * .88f).toInt(), ceil(layer.text.lines().maxOf { paint.measureText(it) }.toDouble()).toInt())))
            .setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(true).build()
    }
    fun fits(layer: VideoTextLayer, width: Int, height: Int): Boolean = !layer.enabled ||
        layout(layer, width, height).height + padding(layer, width, height) * 2 <= height * .9f
    private fun padding(layer: VideoTextLayer, width: Int, height: Int) =
        ceil(maxOf(1f, minOf(width, height) * layer.size * .15f)).toInt()
    fun bitmap(layer: VideoTextLayer, width: Int, height: Int): Bitmap {
        val layout = layout(layer, width, height)
        val pad = padding(layer, width, height)
        require(layout.height + pad * 2 <= height * .9f) { "Text does not fit; shorten it or choose a smaller text size" }
        val bitmap = Bitmap.createBitmap(layout.width + pad * 2, layout.height + pad * 2, Bitmap.Config.ARGB_8888)
        try { val canvas = Canvas(bitmap); canvas.translate(pad.toFloat(), pad.toFloat()); layout.draw(canvas); return bitmap }
        catch (error: Throwable) { bitmap.recycle(); throw error }
    }
}

/** Applied to the composition after per-clip framing/speed; its timestamp is global output time. */
@androidx.annotation.OptIn(UnstableApi::class)
class VideoTextOverlay(private val layer: VideoTextLayer, private val durationMs: Long) : BitmapOverlay() {
    private var bitmap: Bitmap? = null
    private var visible = StaticOverlaySettings.Builder().build()
    private var hidden = StaticOverlaySettings.Builder().setAlphaScale(0f).build()
    override fun configure(videoSize: Size) {
        super.configure(videoSize)
        bitmap?.recycle() // Export-private, never handed to UI; configure occurs on the GL thread.
        bitmap = VideoTextRenderer.bitmap(layer, videoSize.width, videoSize.height)
        val anchorY = 1f - layer.position.row
        val anchorX = layer.position.column - 1f
        visible = StaticOverlaySettings.Builder().setBackgroundFrameAnchor(anchorX * .9f, anchorY * .9f)
            .setOverlayFrameAnchor(anchorX, anchorY).build()
        hidden = StaticOverlaySettings.Builder().setBackgroundFrameAnchor(anchorX * .9f, anchorY * .9f)
            .setOverlayFrameAnchor(anchorX, anchorY).setAlphaScale(0f).build()
    }
    override fun getBitmap(presentationTimeUs: Long): Bitmap = checkNotNull(bitmap)
    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings =
        if (layer.visible(presentationTimeUs, durationMs)) visible else hidden
    override fun release() { try { super.release() } finally { bitmap?.recycle(); bitmap = null } }
}

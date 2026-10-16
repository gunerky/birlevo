package com.example.contentstudio.media

import android.graphics.Bitmap
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.common.OverlaySettings
import androidx.media3.effect.StaticOverlaySettings
import kotlin.math.ceil

/** Cached export-private bitmap; frame evaluation only selects visible/hidden settings. */
@androidx.annotation.OptIn(UnstableApi::class)
class VideoImageOverlay(private val assets: VideoImageAssets, private val layer: VideoImageLayer,
    private val durationMs: Long) : BitmapOverlay() {
    private var bitmap: Bitmap? = null
    private var visible = StaticOverlaySettings.Builder().build()
    private var hidden = StaticOverlaySettings.Builder().setAlphaScale(0f).build()
    override fun configure(videoSize: Size) {
        super.configure(videoSize)
        val (width, height) = layer.dimensions(videoSize.width, videoSize.height)
        bitmap?.recycle(); bitmap = null
        val decoded = assets.decode(layer, ceil(maxOf(width, height).toDouble()).toInt().coerceAtLeast(1))
        bitmap = decoded
        fun settings(alpha: Float) = StaticOverlaySettings.Builder()
            .setBackgroundFrameAnchor((layer.position.column - 1f) * .9f, (1f - layer.position.row) * .9f)
            .setOverlayFrameAnchor(layer.position.column - 1f, 1f - layer.position.row)
            .setScale(width / decoded.width, height / decoded.height).setAlphaScale(alpha).build()
        visible = settings(layer.opacity); hidden = settings(0f)
    }
    override fun getBitmap(presentationTimeUs: Long) = checkNotNull(bitmap)
    override fun getOverlaySettings(presentationTimeUs: Long) =
        if (layer.visible(presentationTimeUs, durationMs)) visible else hidden
    override fun release() { try { super.release() } finally { bitmap?.recycle(); bitmap = null } }
}

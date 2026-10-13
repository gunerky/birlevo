package com.example.contentstudio.probes

import android.content.Context
import com.example.contentstudio.media.*
import java.io.File

/** Keep the original fixture matrix, now exercising the shared production pipeline. */
internal object VideoTargetProbe {
    enum class Target(val format: VideoOutputFormat) {
        HEVC(VideoOutputFormat.MP4_HEVC), WEBM(VideoOutputFormat.WEBM_VP8);
        val videoMime get() = format.videoMime
        val audioMime get() = format.audioMime
        val extension get() = format.extension
    }

    suspend fun encode(context: Context, source: VideoSource, trim: VideoTrim, target: Target,
        quality: VideoQuality = VideoQuality.BALANCED, edge: Int = 640,
        onStarted: () -> Unit = {}): File {
        var notified = false
        val result = VideoCompressor(context).compress(source, quality, edge, trim, target.format) {
            if (!notified) { notified = true; onStarted() }
        }
        // This test-owned poster is never delivered to a UI drawing layer.
        result.info.poster?.recycle()
        return result.file
    }
}

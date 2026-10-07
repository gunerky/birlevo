package com.example.contentstudio.probes

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.*
import com.example.contentstudio.media.*
import kotlinx.coroutines.*
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Deliberately independent of the production pipeline until the trial matrix is accepted. */
@androidx.annotation.OptIn(UnstableApi::class)
internal object VideoTargetProbe {
    enum class Target(val videoMime: String, val audioMime: String, val extension: String) {
        HEVC(MimeTypes.VIDEO_H265, MimeTypes.AUDIO_AAC, "mp4"),
        WEBM(MimeTypes.VIDEO_VP8, MimeTypes.AUDIO_OPUS, "webm")
    }

    suspend fun encode(context: Context, source: VideoSource, trim: VideoTrim, target: Target,
        quality: VideoQuality = VideoQuality.BALANCED, edge: Int = 640,
        onStarted: () -> Unit = {}): File {
        val settings = videoTarget(source.info.width, source.info.height, edge, quality)
        val folder = File(context.cacheDir, "video-target-probe").apply { check(isDirectory || mkdirs()) }
        val working = File.createTempFile("working-", ".${target.extension}", folder)
        val retained = File.createTempFile("result-", ".${target.extension}", folder)
        check(working.delete()); check(retained.delete())
        var transformer: Transformer? = null
        var completed = false
        try {
            withTimeout(90_000) {
                withContext(Dispatchers.Main.immediate) {
                    try {
                        suspendCancellableCoroutine<Unit> { continuation ->
                            val encoder = DefaultEncoderFactory.Builder(context)
                                .setEnableFallback(false)
                                .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder()
                                    .setBitrate(settings.bitrate).build()).build()
                            val factory = object : Codec.EncoderFactory by encoder {
                                // Trimmed Opus passthrough carries negative preroll timestamps. The
                                // platform WebM writer rejects these; re-encode instead of shifting
                                // one track independently and silently changing A/V synchronization.
                                override fun audioNeedsEncoding() = target == Target.WEBM
                            }
                            val builder = Transformer.Builder(context).setVideoMimeType(target.videoMime)
                                .setAudioMimeType(target.audioMime).setEncoderFactory(factory)
                                .addListener(object : Transformer.Listener {
                                    override fun onCompleted(composition: Composition, result: ExportResult) {
                                        completed = true
                                        if (continuation.isActive) continuation.resume(Unit)
                                    }
                                    override fun onError(composition: Composition, result: ExportResult, error: ExportException) {
                                        if (continuation.isActive) continuation.resumeWithException(error)
                                    }
                                })
                            if (target == Target.WEBM) builder.setMuxerFactory(ProbeWebmMuxer.Factory())
                                .setPortraitEncodingEnabled(true)
                            val active = builder.build(); transformer = active
                            val item = MediaItem.Builder().setUri(Uri.fromFile(source.file))
                                .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder()
                                    .setStartPositionMs(trim.startMs).setEndPositionMs(trim.endMs).build()).build()
                            active.start(EditedMediaItem.Builder(item).setEffects(Effects(emptyList(),
                                listOf(Presentation.createForWidthAndHeight(settings.width, settings.height,
                                    Presentation.LAYOUT_SCALE_TO_FIT)))).build(), working.path)
                            onStarted()
                        }
                    } finally {
                        try { if (completed) check(working.renameTo(retained)) }
                        finally { transformer?.cancel() }
                    }
                }
            }
            currentCoroutineContext().ensureActive()
            check(retained.length() > 0)
            return retained
        } catch (error: Throwable) {
            retained.delete(); throw error
        } finally { working.delete() }
    }
}

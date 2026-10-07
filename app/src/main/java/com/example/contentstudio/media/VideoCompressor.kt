package com.example.contentstudio.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Format
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.*
import java.io.File
import java.io.IOException
import kotlin.math.abs
import kotlin.math.max
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.*
import androidx.core.net.toUri

data class VideoInfo(val width: Int, val height: Int, val durationMs: Long, val hasAudio: Boolean,
    val poster: Bitmap?, val videoMime: String = "video/avc", val audioMime: String? = null,
    val audioSampleRate: Int = 48000, val audioChannels: Int = 2,
    val frameRate: Float? = null, val rotationDegrees: Int = 0)
data class VideoSource(val file: File, val originalUri: Uri, val name: String, val bytes: Long, val info: VideoInfo)
data class CompressedVideo(val file: File, val bytes: Long, val info: VideoInfo, val elapsedMs: Long,
    val format: VideoOutputFormat = VideoOutputFormat.MP4_AVC)
data class VideoTrim(val startMs: Long, val endMs: Long) {
    init { require(startMs >= 0 && endMs > startMs) }
    val durationMs: Long get() = endMs - startMs
}

@androidx.annotation.OptIn(UnstableApi::class)
class VideoCompressor(private val context: Context) {
    private val support = VideoOutputSupport()
    private val folder by lazy { File(context.noBackupFilesDir, "video-compression").apply { check(isDirectory || mkdirs()) { "Media cache unavailable" } } }

    fun outputSupport(info: VideoInfo, quality: VideoQuality, edge: Int) = support.all(info, videoTarget(info.width, info.height, edge, quality))

    suspend fun restoreOutput(file: File, bytes: Long, elapsedMs: Long,
        format: VideoOutputFormat = VideoOutputFormat.MP4_AVC): CompressedVideo {
        require(file.canonicalFile.parentFile == folder.canonicalFile && file.name.startsWith("output-") && file.length() == bytes)
        require(file.extension == format.extension)
        var info: VideoInfo? = null
        var delivered = false
        try {
            info = inspect(file); currentCoroutineContext().ensureActive()
            validateCodecs(info, format)
            return CompressedVideo(file, bytes, info, elapsedMs, format).also { delivered = true }
        } finally { if (!delivered) info?.poster?.recycle() }
    }
    private fun tempFile(prefix: String, extension: String = "mp4"): File {
        check(folder.isDirectory || folder.mkdirs()) { "Video cache unavailable" }
        return File.createTempFile(prefix, ".$extension", folder)
    }

    /** IO work: snapshot the selected file; its original URI is never opened for writing. */
    suspend fun importVideo(uri: Uri): VideoSource {
        val file = tempFile("source-")
        var info: VideoInfo? = null
        var committed = false
        try {
            val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }?.take(256) ?: "video.mp4"
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val size = input.read(buffer)
                        if (size < 0) break
                        total += size
                        if (total > FormatSupport.MAX_VIDEO_BYTES) throw MediaImportException(ImportIssue.VIDEO_LIMIT)
                        output.write(buffer, 0, size)
                    }
                    output.fd.sync()
                }
            } ?: throw MediaImportException(ImportIssue.FILE_ACCESS)
            currentCoroutineContext().ensureActive()
            info = inspect(file)
            currentCoroutineContext().ensureActive()
            return VideoSource(file, uri, name, file.length(), info).also { committed = true }
        } catch (error: Throwable) { file.delete(); throw error }
        finally { if (!committed) info?.poster?.recycle() }
    }

    suspend fun compress(source: VideoSource, quality: VideoQuality, maxEdge: Int, trim: VideoTrim,
        onProgress: (Int?) -> Unit): CompressedVideo = compress(source, quality, maxEdge, trim, VideoOutputFormat.MP4_AVC, onProgress)

    suspend fun compress(source: VideoSource, quality: VideoQuality, maxEdge: Int, trim: VideoTrim,
        format: VideoOutputFormat, onProgress: (Int?) -> Unit): CompressedVideo {
        require(trim.endMs <= source.info.durationMs) { "Trim exceeds source duration" }
        val target = videoTarget(source.info.width, source.info.height, maxEdge, quality)
        support.issue(source.info, target, format)?.let { throw VideoOutputUnavailable(it) }
        val output = tempFile("output-", format.extension)
        val working = try { tempFile("working-", format.extension) } catch (error: Throwable) { output.delete(); throw error }
        // Transformer expects a new path, not an existing file.
        check(output.delete())
        check(working.delete())
        val start = android.os.SystemClock.elapsedRealtime()
        var info: VideoInfo? = null
        var committed = false
        try {
            withTimeout(20 * 60 * 1000L) {
                withContext(Dispatchers.Main.immediate) {
                    var transformer: Transformer? = null
                    val handler = Handler(Looper.getMainLooper())
                    var poll: Runnable? = null
                    var completed = false
                    try {
                        suspendCancellableCoroutine<Unit> { continuation ->
                            val listener = object : Transformer.Listener {
                                override fun onCompleted(composition: Composition, result: ExportResult) {
                                    completed = true
                                    if (continuation.isActive) continuation.resume(Unit)
                                }
                                override fun onError(composition: Composition, result: ExportResult, exception: ExportException) {
                                    if (continuation.isActive) continuation.resumeWithException(exception)
                                }
                            }
                            val settings = VideoEncoderSettings.Builder().setBitrate(target.bitrate).apply {
                                if (format == VideoOutputFormat.MP4_HEVC) {
                                    val codec = support.selector(source.info, target, format).selectEncoderInfos(format.videoMime).first()
                                    val level = codec.getCapabilitiesForType(format.videoMime).profileLevels
                                        .filter { it.profile == android.media.MediaCodecInfo.CodecProfileLevel.HEVCProfileMain }.maxOf { it.level }
                                    setEncodingProfileLevel(android.media.MediaCodecInfo.CodecProfileLevel.HEVCProfileMain, level)
                                }
                            }.build()
                            val encoder = DefaultEncoderFactory.Builder(context)
                                .setVideoEncoderSelector(support.selector(source.info, target, format))
                                .setRequestedVideoEncoderSettings(settings)
                                .setEnableFallback(false).build()
                            val factory = object : Codec.EncoderFactory by encoder {
                                // Explicitly forward the Java default method so quality/bitrate
                                // settings cannot turn into passthrough when dimensions match.
                                override fun videoNeedsEncoding() = encoder.videoNeedsEncoding()
                                override fun audioNeedsEncoding() = format == VideoOutputFormat.WEBM_VP8
                                override fun createForAudioEncoding(request: Format): Codec {
                                    require(request.sampleMimeType == format.audioMime && request.channelCount == source.info.audioChannels)
                                    require(request.sampleRate == if (format == VideoOutputFormat.WEBM_VP8) 48000 else source.info.audioSampleRate)
                                    return encoder.createForAudioEncoding(request)
                                }
                            }
                            val builder = Transformer.Builder(context).setVideoMimeType(format.videoMime)
                                .setAudioMimeType(format.audioMime).setEncoderFactory(factory).addListener(listener)
                            if (format == VideoOutputFormat.WEBM_VP8) builder.setMuxerFactory(WebmMuxer.Factory()).setPortraitEncodingEnabled(true)
                            val active = builder.build()
                            transformer = active
                            val presentation = Presentation.createForWidthAndHeight(target.width, target.height,
                                Presentation.LAYOUT_SCALE_TO_FIT)
                            val mediaItem = MediaItem.Builder().setUri(Uri.fromFile(source.file))
                                .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder()
                                    .setStartPositionMs(trim.startMs).setEndPositionMs(trim.endMs).build())
                                .build()
                            val item = EditedMediaItem.Builder(mediaItem)
                                .setEffects(Effects(if (format == VideoOutputFormat.WEBM_VP8)
                                    listOf(SonicAudioProcessor().apply { setOutputSampleRateHz(48000) }) else emptyList(), listOf(presentation))).build()
                            poll = object : Runnable {
                                override fun run() {
                                    if (!continuation.isActive) return
                                    val holder = ProgressHolder()
                                    onProgress(if (active.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE)
                                        holder.progress else null)
                                    handler.postDelayed(this, 250)
                                }
                            }
                            active.start(item, working.path)
                            handler.post(poll)
                        }
                    } finally {
                        poll?.let(handler::removeCallbacks)
                        try {
                            // Keep the completed video before releasing Transformer; cancel may remove its old path.
                            if (completed) check(working.renameTo(output)) { "Could not retain video output" }
                        } finally {
                            transformer?.cancel()
                        }
                    }
                }
            }
            currentCoroutineContext().ensureActive()
            info = inspect(output)
            validateCodecs(info, format)
            require(info.hasAudio == source.info.hasAudio) { "Audio track was lost" }
            require(abs(info.durationMs - trim.durationMs) <= max(300L, trim.durationMs / 100)) {
                "Output duration changed"
            }
            require(info.width == target.width && info.height == target.height) {
                "Unexpected output dimensions"
            }
            if (info.hasAudio) require(info.audioChannels == source.info.audioChannels && info.audioSampleRate ==
                if (format == VideoOutputFormat.WEBM_VP8) 48000 else source.info.audioSampleRate) { "Unexpected audio configuration" }
            currentCoroutineContext().ensureActive()
            return CompressedVideo(output, output.length(), info, android.os.SystemClock.elapsedRealtime() - start, format)
                .also { committed = true }
        } catch (timeout: TimeoutCancellationException) {
            working.delete(); output.delete()
            throw IOException("Video processing timed out", timeout)
        } catch (error: Throwable) { working.delete(); output.delete(); throw error }
        finally { if (!committed) info?.poster?.recycle() }
    }

    private fun validateCodecs(info: VideoInfo, format: VideoOutputFormat) {
        require(info.videoMime == format.videoMime && (!info.hasAudio || info.audioMime == format.audioMime)) { "Unexpected output codec" }
    }

    suspend fun export(source: VideoSource, result: CompressedVideo, destination: Uri,
        allowNonSmallerCopy: Boolean = false) {
        require(destination != source.originalUri) { "Cannot overwrite original" }
        require(allowNonSmallerCopy || compressionBenefit(source.bytes, result.bytes) == CompressionBenefit.SMALLER) {
            "Output did not save space; explicit copy confirmation required"
        }
        try {
            context.contentResolver.openOutputStream(destination, "w")?.use { output ->
                result.file.inputStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val size = input.read(buffer)
                        if (size < 0) break
                        output.write(buffer, 0, size)
                    }
                    output.flush()
                    currentCoroutineContext().ensureActive()
                }
            } ?: throw IOException("Destination unavailable")
        } catch (error: Throwable) {
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, destination) }
            throw error
        }
    }

    suspend fun restore(asset: SessionAsset): VideoSource {
        val file = asset.resolve(folder, "source-", FormatSupport.MAX_VIDEO_BYTES)
        require(file.extension == "mp4")
        var info: VideoInfo? = null
        var delivered = false
        try {
            currentCoroutineContext().ensureActive()
            info = inspect(file)
            currentCoroutineContext().ensureActive()
            return VideoSource(file, asset.uri.toUri(), asset.name, file.length(), info).also { delivered = true }
        } finally { if (!delivered) info?.poster?.recycle() }
    }

    fun cleanOldCache(retained: Set<File> = emptySet()) {
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        folder.listFiles()?.filter { it !in retained && it.name != "session.json" && !it.name.startsWith("session.json.") && it.lastModified() < cutoff }?.forEach { it.delete() }
    }

    private fun inspect(file: File): VideoInfo {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.path)
            val tracks = mutableListOf<VideoTrackSupport>()
            val formats = mutableListOf<MediaFormat>()
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME)
                val transfer = when (mime) {
                    "video/avc" -> avcColorTransfer(format)
                    "video/hevc" -> hevcColorTransfer(format)
                    else -> if (format.containsKey(MediaFormat.KEY_COLOR_TRANSFER)) format.getInteger(MediaFormat.KEY_COLOR_TRANSFER) else 0
                }
                val rate = if (mime?.startsWith("video/") == true && format.containsKey(MediaFormat.KEY_FRAME_RATE))
                    runCatching { format.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat() }.getOrElse { format.getFloat(MediaFormat.KEY_FRAME_RATE) }
                    else null
                tracks += VideoTrackSupport(mime, transfer, rate)
                formats += format
            }
            FormatSupport.validateVideoTracks(tracks)
            val decoders = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            if (formats.any { decoders.findDecoderForFormat(it) == null })
                throw MediaImportException(ImportIssue.VIDEO_CODEC)
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.path)
                if (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) !in FormatSupport.videoMimes)
                    throw MediaImportException(ImportIssue.VIDEO_CONTAINER)
                val rawWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)!!.toInt()
                val rawHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)!!.toInt()
                val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
                val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toInt() ?: 0
                if (rawWidth < 16 || rawHeight < 16 || max(rawWidth, rawHeight) > 4096 ||
                    rawWidth.toLong() * rawHeight > 8_847_360 || duration !in 1..FormatSupport.MAX_VIDEO_DURATION_MS)
                    throw MediaImportException(ImportIssue.VIDEO_LIMIT)
                val swapped = rotation == 90 || rotation == 270
                val video = formats.single { it.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true }
                val audio = formats.singleOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                val audioRate = audio?.getInteger(MediaFormat.KEY_SAMPLE_RATE) ?: 48000
                val audioChannels = audio?.getInteger(MediaFormat.KEY_CHANNEL_COUNT) ?: 2
                val poster = runCatching {
                    if (Build.VERSION.SDK_INT >= 27) retriever.getScaledFrameAtTime(0,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 512, 512)
                    else retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { full ->
                        var retained = false
                        try {
                            val ratio = minOf(1f, 512f / max(full.width, full.height))
                            Bitmap.createScaledBitmap(full, max(1, (full.width * ratio).toInt()),
                                max(1, (full.height * ratio).toInt()), true).also { retained = it === full }
                        } finally { if (!retained) full.recycle() }
                    }
                }.getOrNull()
                return VideoInfo(if (swapped) rawHeight else rawWidth, if (swapped) rawWidth else rawHeight,
                    duration, audio != null, poster, requireNotNull(video.getString(MediaFormat.KEY_MIME)),
                    audio?.getString(MediaFormat.KEY_MIME), audioRate, audioChannels,
                    tracks.single { it.mime?.startsWith("video/") == true }.frameRate, rotation)
            } finally { retriever.release() }
        } finally { extractor.release() }
    }
}

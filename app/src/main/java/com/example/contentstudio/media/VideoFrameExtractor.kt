package com.example.contentstudio.media

import androidx.core.graphics.createBitmap
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.SystemClock
import java.io.File
import java.io.FileOutputStream
import com.example.contentstudio.storage.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.roundToInt

data class VideoFrameTarget(val file: File, val durationMs: Long, val name: String)
data class VideoFrame(val bitmap: Bitmap, val timeUs: Long)

/** IO-only, one decoder and one selected bitmap. Does not use sync-frame-only thumbnails. */
class VideoFrameExtractor(private val context: Context,
    private val memory: WorkingMemory = deviceMemory(context)) {
    suspend fun read(target: VideoFrameTarget, timeMs: Long, preview: Boolean): VideoFrame {
        require(target.file.isFile && target.durationMs > 0 && timeMs in 0 until target.durationMs)
        var extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var pending: Bitmap? = null
        var delivered = false
        val deadline = SystemClock.elapsedRealtime() + 120_000
        suspend fun active() {
            currentCoroutineContext().ensureActive()
            check(SystemClock.elapsedRealtime() < deadline) { "Frame decoder timed out" }
        }
        try {
            extractor.setDataSource(target.file.path)
            val track = (0 until extractor.trackCount).single {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
            }
            extractor.selectTrack(track)
            var format = extractor.getTrackFormat(track)
            val mime = checkNotNull(format.getString(MediaFormat.KEY_MIME))
            require(mime in FormatSupport.videoCodecs)
            require(format.intOr(MediaFormat.KEY_COLOR_TRANSFER, 0) !in listOf(6, 7)) { "HDR frame conversion is unavailable" }
            if (mime == "video/x-vnd.on2.vp9") requireVp9Profile0(extractor)
            val rotation = format.intOr(MediaFormat.KEY_ROTATION, 0)
            require(rotation in listOf(0, 90, 180, 270))
            // Locate a real sample first. At the end, choose the final sample rather than an arbitrary poster.
            extractor.seekTo(timeMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            var selected = extractor.sampleTime
            require(selected >= 0)
            var foundFollowing = false
            while (extractor.sampleTime >= 0) {
                active()
                selected = maxOf(selected, extractor.sampleTime)
                if (extractor.sampleTime >= timeMs * 1000) { foundFollowing = true; break }
                if (!extractor.advance()) break
            }
            // Packet order can differ from presentation order (B frames). Decode from the original
            // seek point and choose by presentation timestamp, not the first later packet's PTS.
            if (foundFollowing) selected = timeMs * 1000
            // A repeated seek on the legacy extractor can lose the sync sample and feed
            // undecodable delta frames (green/empty output). Probe and decode use separate
            // extractor lifetimes so decoding always starts with a fresh sync sample.
            extractor.release()
            extractor = MediaExtractor()
            extractor.setDataSource(target.file.path)
            extractor.selectTrack(track)
            extractor.seekTo(minOf(selected, timeMs * 1000), MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            format = extractor.getTrackFormat(track)
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            // Output Image planes are unrotated; apply rotation once while writing the selected bitmap.
            format.setInteger(MediaFormat.KEY_ROTATION, 0)
            val decoder = MediaCodec.createDecoderByType(mime).also { codec = it }
            decoder.configure(format, null, null, 0); decoder.start()
            val info = MediaCodec.BufferInfo()
            var inputEnd = false
            var outputFormat = format
            while (true) {
                active()
                if (!inputEnd) {
                    val slot = decoder.dequeueInputBuffer(10_000)
                    if (slot >= 0) {
                        val buffer = checkNotNull(decoder.getInputBuffer(slot)); buffer.clear()
                        val pts = maxOf(0, extractor.sampleTime)
                        val size = extractor.readSampleData(buffer, 0)
                        if (size >= 0) require(extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED == 0)
                        inputEnd = size < 0
                        decoder.queueInputBuffer(slot, 0, maxOf(0, size), pts,
                            if (inputEnd) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0)
                        if (!inputEnd) extractor.advance()
                    }
                }
                val slot = decoder.dequeueOutputBuffer(info, 10_000)
                if (slot == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) outputFormat = decoder.outputFormat
                else if (slot >= 0) {
                    try {
                        if (info.size > 0 && info.presentationTimeUs >= selected && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                            val image = checkNotNull(decoder.getOutputImage(slot)) { "Decoder does not expose image planes" }
                            image.use { pending = bitmap(it, rotation, if (preview) 512 else null, outputFormat) }
                            active()
                            return VideoFrame(checkNotNull(pending), info.presentationTimeUs).also { delivered = true }
                        }
                        check(info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM == 0) { "Selected frame unavailable" }
                    } finally { decoder.releaseOutputBuffer(slot, false) }
                }
            }
        } finally {
            if (!delivered) pending?.recycle()
            try { codec?.release() } finally { extractor.release() }
        }
    }

    /** Lossless PNG is a new still image, not a video compression result. */
    suspend fun write(target: VideoFrameTarget, timeMs: Long): File {
        val root = File(context.cacheDir, "video-frames").apply { check(isDirectory || mkdirs()) }
        val file = File.createTempFile("frame-", ".png", root)
        var frame: VideoFrame? = null
        var complete = false
        try {
            frame = read(target, timeMs, preview = false)
            currentCoroutineContext().ensureActive()
            FileOutputStream(file).use { output ->
                check(frame.bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)); output.fd.sync()
            }
            require(file.length() in 1..FormatSupport.MAX_IMAGE_BYTES)
            currentCoroutineContext().ensureActive()
            complete = true
            return file
        } finally { frame?.bitmap?.recycle(); if (!complete) file.delete() }
    }

    private suspend fun bitmap(image: Image, rotation: Int, maxEdge: Int?, format: MediaFormat): Bitmap {
        require(image.format == ImageFormat.YUV_420_888 && image.planes.size == 3)
        val crop = image.cropRect
        val width = if (rotation % 180 == 0) crop.width() else crop.height()
        val height = if (rotation % 180 == 0) crop.height() else crop.width()
        require(width > 0 && height > 0 && width.toLong() * height <= FormatSupport.MAX_IMAGE_PIXELS)
        val scale = if (maxEdge == null) 1.0 else minOf(1.0, maxEdge.toDouble() / maxOf(width, height))
        val w = maxOf(1, (width * scale).roundToInt()); val h = maxOf(1, (height * scale).roundToInt())
        // Preview sampling never allocates a full-size ARGB bitmap. Final still export keeps every pixel.
        memory.requireBytes(MediaResourcePlan.rasterMemory(w, h))
        val result = createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setHasAlpha(false)
        var complete = false
        try {
            val planes = image.planes
            val buffers = planes.map { it.buffer.duplicate() }
            val bases = buffers.map { it.position() }
            fun sample(plane: Int, x: Int, y: Int): Int = buffers[plane].get(bases[plane] +
                y * planes[plane].rowStride + x * planes[plane].pixelStride).toInt() and 255
            val row = IntArray(w)
            val matrix = VideoFrameColor.matrix(format.intOr(MediaFormat.KEY_COLOR_STANDARD, 2))
            val full = format.intOr(MediaFormat.KEY_COLOR_RANGE, 2) == 1
            for (y in 0 until h) {
                currentCoroutineContext().ensureActive()
                val ry = (y.toLong() * height / h).toInt()
                for (x in 0 until w) {
                    val rx = (x.toLong() * width / w).toInt()
                    val sx = crop.left + when (rotation) { 90 -> ry; 180 -> crop.width() - 1 - rx; 270 -> crop.width() - 1 - ry; else -> rx }
                    val sy = crop.top + when (rotation) { 90 -> crop.height() - 1 - rx; 180 -> crop.height() - 1 - ry; 270 -> rx; else -> ry }
                    row[x] = VideoFrameColor.convert(sample(0, sx, sy), sample(1, sx / 2, sy / 2), sample(2, sx / 2, sy / 2), matrix, full)
                }
                result.setPixels(row, 0, w, 0, y, w, 1)
            }
            complete = true
            return result
        } finally { if (!complete) result.recycle() }
    }
}

private fun MediaFormat.intOr(key: String, fallback: Int) = if (containsKey(key)) getInteger(key) else fallback

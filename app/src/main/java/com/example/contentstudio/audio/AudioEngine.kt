package com.example.contentstudio.audio

import android.content.Context
import android.media.*
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.*
import com.example.contentstudio.media.SessionAsset
import com.example.contentstudio.media.compressionBenefit
import com.example.contentstudio.media.CompressionBenefit
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.*

data class AudioInfo(val mime: String, val sampleRate: Int, val channels: Int, val durationMs: Long, val fromVideo: Boolean)
data class AudioSource(val file: File, val originalUri: Uri, val name: String, val bytes: Long, val info: AudioInfo)
data class AudioResult(val file: File, val bytes: Long, val info: AudioInfo, val elapsedMs: Long)

@androidx.annotation.OptIn(UnstableApi::class)
class AudioEngine(private val context: Context) {
    private val folder get() = File(context.noBackupFilesDir, "audio-conversion").apply { check(isDirectory || mkdirs()) }
    private fun temporary(prefix: String, extension: String) = File.createTempFile(prefix, ".$extension", folder)

    suspend fun importAudio(uri: Uri): AudioSource {
        val file = temporary("source-", "media")
        try {
            val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }?.take(256) ?: "audio"
            context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { output ->
                val buffer = ByteArray(65536); var size = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer); if (count < 0) break
                    size += count; require(size <= AudioPolicy.MAX_BYTES); output.write(buffer, 0, count)
                }
                output.fd.sync()
            } } ?: throw IOException("Source unavailable")
            val info = inspect(file); currentCoroutineContext().ensureActive()
            return AudioSource(file, uri, name, file.length(), info)
        } catch (failure: Throwable) { file.delete(); throw failure }
    }
    fun restore(asset: SessionAsset): AudioSource {
        val file = asset.resolve(folder, "source-", AudioPolicy.MAX_BYTES)
        return AudioSource(file, Uri.parse(asset.uri), asset.name, file.length(), inspect(file))
    }
    fun restoreOutput(file: File, bytes: Long, elapsedMs: Long): AudioResult {
        require(file.canonicalFile.parentFile == folder.canonicalFile && file.name.startsWith("output-") && file.length() == bytes)
        val info = inspect(file, output = true); require(!info.fromVideo)
        require((file.extension == "wav" && info.mime == "audio/raw") || (file.extension == "m4a" && info.mime == "audio/mp4a-latm"))
        return AudioResult(file, bytes, info, elapsedMs)
    }
    fun inspect(file: File, output: Boolean = false): AudioInfo {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.path)
            val tracks = (0 until extractor.trackCount).map { extractor.getTrackFormat(it) }
            val audio = tracks.filter { it.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
            require(audio.size == 1) { "Exactly one audio track is required" }
            val format = audio.single()
            val mime = checkNotNull(format.getString(MediaFormat.KEY_MIME))
            val rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE); val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val duration = format.getLong(MediaFormat.KEY_DURATION) / 1000
            AudioPolicy.validate(mime, rate, channels, duration, output)
            require(!format.containsKey("crypto-key"))
            if (mime == "audio/raw") require(!format.containsKey(MediaFormat.KEY_PCM_ENCODING) || format.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormatEncoding.PCM16)
            else require(MediaCodecList(MediaCodecList.REGULAR_CODECS).findDecoderForFormat(format) != null)
            return AudioInfo(mime, rate, channels, duration, tracks.any { it.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true })
        } finally { extractor.release() }
    }
    suspend fun convert(source: AudioSource, target: AudioTarget, bitrate: Int, progress: (Int?) -> Unit): AudioResult {
        require(bitrate in AudioPolicy.bitrates)
        val output = temporary("output-", target.extension)
        val start = android.os.SystemClock.elapsedRealtime()
        try {
            withTimeout(20 * 60 * 1000L) {
                if (target == AudioTarget.WAV) decodeWav(source, output, progress)
                else encodeM4a(source, output, bitrate, progress)
            }
            currentCoroutineContext().ensureActive()
            require(output.length() in 1..AudioPolicy.MAX_BYTES)
            val result = restoreOutput(output, output.length(), android.os.SystemClock.elapsedRealtime() - start)
            require(result.info.sampleRate == source.info.sampleRate && result.info.channels == source.info.channels)
            require(kotlin.math.abs(result.info.durationMs - source.info.durationMs) <= maxOf(300, source.info.durationMs / 100))
            return result
        } catch (timeout: TimeoutCancellationException) { output.delete(); throw IOException("Audio processing timed out", timeout) }
        catch (failure: Throwable) { output.delete(); throw failure }
    }
    private suspend fun decodeWav(source: AudioSource, output: File, progress: (Int?) -> Unit) {
        val extractor = MediaExtractor(); var codec: MediaCodec? = null
        try {
            extractor.setDataSource(source.file.path)
            val index = (0 until extractor.trackCount).single { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
            val format = extractor.getTrackFormat(index); extractor.selectTrack(index)
            var rate = source.info.sampleRate; var channels = source.info.channels
            var dataBytes = 0L
            RandomAccessFile(output, "rw").use { writer ->
                writer.setLength(0); writer.write(AudioPolicy.wavHeader(rate, channels, 0))
                val block = ByteArray(65536)
                fun writePcm(buffer: ByteBuffer) {
                    require(dataBytes + buffer.remaining() <= AudioPolicy.MAX_BYTES - 44)
                    while (buffer.hasRemaining()) { val count = minOf(block.size, buffer.remaining()); buffer.get(block, 0, count); writer.write(block, 0, count); dataBytes += count }
                }
                if (source.info.mime == "audio/raw") {
                    val buffer = ByteBuffer.allocate(1024 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive(); buffer.clear()
                        val size = extractor.readSampleData(buffer, 0); if (size < 0) break
                        require(extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED == 0)
                        buffer.position(0); buffer.limit(size); writePcm(buffer)
                        progress((extractor.sampleTime / 10 / source.info.durationMs).toInt()); extractor.advance()
                    }
                } else {
                    format.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormatEncoding.PCM16)
                    val decoder = MediaCodec.createDecoderByType(source.info.mime); codec = decoder
                    decoder.configure(format, null, null, 0); decoder.start()
                    val info = MediaCodec.BufferInfo(); var inputEnd = false; var outputEnd = false
                    var lastActivity = android.os.SystemClock.elapsedRealtime()
                    while (!outputEnd) {
                        currentCoroutineContext().ensureActive()
                        require(android.os.SystemClock.elapsedRealtime() - lastActivity < 30_000) { "Audio decoder stalled" }
                        if (!inputEnd) {
                            val slot = decoder.dequeueInputBuffer(10_000)
                            if (slot >= 0) {
                                val buffer = checkNotNull(decoder.getInputBuffer(slot)); buffer.clear()
                                val time = maxOf(0, extractor.sampleTime)
                                val size = extractor.readSampleData(buffer, 0)
                                if (size >= 0) require(extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED == 0)
                                inputEnd = size < 0
                                decoder.queueInputBuffer(slot, 0, maxOf(0, size), time, if (inputEnd) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0)
                                if (!inputEnd) extractor.advance()
                                lastActivity = android.os.SystemClock.elapsedRealtime()
                            }
                        }
                        val slot = decoder.dequeueOutputBuffer(info, 10_000)
                        if (slot == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            val pcm = decoder.outputFormat
                            require(!pcm.containsKey(MediaFormat.KEY_PCM_ENCODING) || pcm.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormatEncoding.PCM16)
                            rate = pcm.getInteger(MediaFormat.KEY_SAMPLE_RATE); channels = pcm.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            require(rate == source.info.sampleRate && channels == source.info.channels)
                        } else if (slot >= 0) {
                            try {
                                if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                                    val buffer = checkNotNull(decoder.getOutputBuffer(slot)); buffer.position(info.offset); buffer.limit(info.offset + info.size)
                                    writePcm(buffer); progress((info.presentationTimeUs / 10 / source.info.durationMs).toInt().coerceIn(0, 100))
                                }
                                outputEnd = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                                lastActivity = android.os.SystemClock.elapsedRealtime()
                            } finally { decoder.releaseOutputBuffer(slot, false) }
                        }
                    }
                }
                require(dataBytes > 0)
                writer.seek(0); writer.write(AudioPolicy.wavHeader(rate, channels, dataBytes)); writer.fd.sync()
            }
        } finally { try { codec?.release() } finally { extractor.release() } }
    }
    private suspend fun encodeM4a(source: AudioSource, output: File, bitrate: Int, progress: (Int?) -> Unit) {
        val working = temporary("working-", "m4a"); check(working.delete()); check(output.delete())
        try { withContext(Dispatchers.Main.immediate) {
            var transformer: Transformer? = null; var completed = false
            val handler = Handler(Looper.getMainLooper()); var poll: Runnable? = null
            try {
                suspendCancellableCoroutine<Unit> { continuation ->
                    val encoder = DefaultEncoderFactory.Builder(context).setEnableFallback(false)
                        .setRequestedAudioEncoderSettings(AudioEncoderSettings.Builder().setBitrate(bitrate).build()).build()
                    val active = Transformer.Builder(context).setAudioMimeType("audio/mp4a-latm").setEncoderFactory(encoder)
                        .addListener(object : Transformer.Listener {
                            override fun onCompleted(composition: Composition, result: ExportResult) { completed = true; if (continuation.isActive) continuation.resume(Unit) }
                            override fun onError(composition: Composition, result: ExportResult, exception: ExportException) { if (continuation.isActive) continuation.resumeWithException(exception) }
                        }).build()
                    transformer = active
                    val item = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(source.file))).setRemoveVideo(true)
                        .setEffects(Effects(listOf(EncodingPassThrough()), emptyList())).build()
                    poll = object : Runnable { override fun run() {
                        if (!continuation.isActive) return
                        val holder = ProgressHolder(); progress(if (active.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) holder.progress else null)
                        handler.postDelayed(this, 250)
                    } }
                    active.start(item, working.path); handler.post(checkNotNull(poll))
                }
            } finally {
                poll?.let(handler::removeCallbacks)
                try { if (completed) check(working.renameTo(output)) } finally { transformer?.cancel() }
            }
        } } finally { working.delete() }
    }
    suspend fun export(source: AudioSource, result: AudioResult, destination: Uri, allowNonSmallerCopy: Boolean = false) {
        require(destination != source.originalUri)
        require(allowNonSmallerCopy || compressionBenefit(source.bytes, result.bytes) == CompressionBenefit.SMALLER)
        try {
            context.contentResolver.openOutputStream(destination, "w")?.use { output -> result.file.inputStream().use { input ->
                val buffer = ByteArray(65536)
                while (true) { currentCoroutineContext().ensureActive(); val size = input.read(buffer); if (size < 0) break; output.write(buffer, 0, size) }
                output.flush(); currentCoroutineContext().ensureActive()
            } } ?: throw IOException("Destination unavailable")
        } catch (failure: Throwable) { runCatching { DocumentsContract.deleteDocument(context.contentResolver, destination) }; throw failure }
    }
    fun cleanOldCache(retained: Set<File>) {
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        folder.listFiles()?.filter { it !in retained && it.name != "session.json" && !it.name.startsWith("session.json.") && it.lastModified() < cutoff }?.forEach { it.delete() }
    }
}

private object AudioFormatEncoding { const val PCM16 = android.media.AudioFormat.ENCODING_PCM_16BIT }

/** Forces the requested AAC bitrate to apply even when the source already contains AAC. */
@androidx.annotation.OptIn(UnstableApi::class)
private class EncodingPassThrough : BaseAudioProcessor() {
    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat = inputAudioFormat
    override fun queueInput(inputBuffer: ByteBuffer) {
        // The pipeline also queues its shared EMPTY_BUFFER; never copy that buffer into itself.
        if (!inputBuffer.hasRemaining()) return
        val output = replaceOutputBuffer(inputBuffer.remaining())
        output.put(inputBuffer); output.flip()
    }
}

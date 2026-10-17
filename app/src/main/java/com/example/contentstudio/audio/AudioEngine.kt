package com.example.contentstudio.audio

import android.content.Context
import android.media.*
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.media3.common.Format
import androidx.media3.common.audio.SonicAudioProcessor
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
class AudioEngine(private val context: Context, private val ownedFolder: File? = null) {
    private val support = AudioOutputSupport()
    fun outputSupport(info: AudioInfo, bitrate: Int) = support.all(info, bitrate)
    fun outputIssue(info: AudioInfo, target: AudioTarget, bitrate: Int) = support.issue(info, target, bitrate)
    private val folder get() = (ownedFolder ?: File(context.noBackupFilesDir, "audio-conversion")).apply { check(isDirectory || mkdirs()) }
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
        if (file.extension == AudioTarget.FLAC.extension) {
            // Some platform extractors decode FLAC themselves and expose an audio/raw track.
            // Validate the native container too; a renamed WAV must never be accepted as FLAC.
            require(info.mime == "audio/flac" || info.mime == "audio/raw")
            val header = ByteArray(42)
            java.io.DataInputStream(file.inputStream()).use { it.readFully(header) }
            FlacHeader.fromCodec(header, info.sampleRate, info.channels)
            require(header[4] == 0x80.toByte() && FlacHeader.samples(header) > 0)
            require(kotlin.math.abs(FlacHeader.samples(header) * 1000 / info.sampleRate - info.durationMs) <= 1)
        } else require(AudioTarget.entries.any { it.extension == file.extension && it.codec == info.mime }) {
            "Unexpected output: ${file.extension} / ${info.mime}"
        }
        if (file.extension == AudioTarget.OGG.extension) {
            val signature = ByteArray(5)
            java.io.DataInputStream(file.inputStream()).use { it.readFully(signature) }
            require(String(signature, 0, 4, Charsets.US_ASCII) == "OggS" && signature[4] == 0.toByte())
        }
        if (file.extension == AudioTarget.MP3.extension) {
            val frames = Mp3Frames.inspect(file)
            require(frames.rate == info.sampleRate && frames.channels == info.channels)
        }
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
        require(support.issue(source.info, target, bitrate) == null) { "Requested audio output is unavailable" }
        val output = temporary("output-", target.extension)
        val start = android.os.SystemClock.elapsedRealtime()
        try {
            withTimeout(20 * 60 * 1000L) {
                when (target) {
                    AudioTarget.WAV -> decodeWav(source, output, progress)
                    AudioTarget.FLAC -> FlacAudioEncoder.encode(source, output, progress)
                    AudioTarget.MP3 -> Mp3AudioEncoder.encode(source, output, bitrate, progress)
                    AudioTarget.M4A, AudioTarget.OGG -> encodeLossy(source, output, target, bitrate, progress)
                }
            }
            currentCoroutineContext().ensureActive()
            require(output.length() in 1..AudioPolicy.MAX_BYTES)
            val result = restoreOutput(output, output.length(), android.os.SystemClock.elapsedRealtime() - start)
            require(result.info.sampleRate == target.sampleRate(source.info.sampleRate) && result.info.channels == source.info.channels)
            if (target == AudioTarget.MP3) require(Mp3Frames.inspect(output).bitrate == bitrate)
            require(kotlin.math.abs(result.info.durationMs - source.info.durationMs) <= maxOf(300, source.info.durationMs / 100))
            return result
        } catch (timeout: TimeoutCancellationException) { output.delete(); throw IOException("Audio processing timed out", timeout) }
        catch (failure: Throwable) { output.delete(); throw failure }
    }
    suspend fun convert(edit: AudioEdit, target: AudioTarget, bitrate: Int, progress: (Int?) -> Unit): AudioResult {
        require(bitrate in AudioPolicy.bitrates)
        if (edit.unchanged) return convert(edit.clips.single().source, target, bitrate, progress)
        require(support.issue(edit.info, target, bitrate) == null)
        // WAV is already the selected PCM result: avoid a second disk copy / decode pass.
        val working = temporary(if (target == AudioTarget.WAV) "output-" else "working-", "wav")
        val start = android.os.SystemClock.elapsedRealtime()
        var result: AudioResult? = null
        var delivered = false
        try {
            withTimeout(20 * 60 * 1000L) {
                AudioEditPcm.write(edit, working) { progress(if (target == AudioTarget.WAV) it else it?.div(2)) }
                currentCoroutineContext().ensureActive()
                result = if (target == AudioTarget.WAV) restoreOutput(working, working.length(), 0)
                    else {
                        val source = AudioSource(working, Uri.fromFile(working), "edited.wav", working.length(), inspect(working))
                        convert(source, target, bitrate) { progress(it?.let { 50 + it / 2 }) }
                    }
                validateEditOutput(edit, checkNotNull(result), target)
            }
            currentCoroutineContext().ensureActive()
            return checkNotNull(result).copy(elapsedMs = android.os.SystemClock.elapsedRealtime() - start).also { delivered = true }
        } catch (timeout: TimeoutCancellationException) { throw IOException("Audio processing timed out", timeout) }
        finally { if (target != AudioTarget.WAV || !delivered) working.delete(); if (!delivered) result?.file?.delete() }
    }
    fun validateEditOutput(edit: AudioEdit, result: AudioResult, target: AudioTarget) {
        require(result.file.extension == target.extension && result.info.sampleRate == target.sampleRate(edit.rate) && result.info.channels == edit.channels)
        require(kotlin.math.abs(result.info.durationMs - edit.durationMs) <= maxOf(300, edit.durationMs / 100))
    }
    private suspend fun decodeWav(source: AudioSource, output: File, progress: (Int?) -> Unit) {
        RandomAccessFile(output, "rw").use { writer ->
            val rate = source.info.sampleRate; val channels = source.info.channels
            writer.setLength(0); writer.write(AudioPolicy.wavHeader(rate, channels, 0))
            val block = ByteArray(65536); var dataBytes = 0L
            PcmAudioDecoder.read(source, progress) { buffer ->
                require(dataBytes + buffer.remaining() <= AudioPolicy.MAX_BYTES - 44)
                while (buffer.hasRemaining()) {
                    val count = minOf(block.size, buffer.remaining())
                    buffer.get(block, 0, count); writer.write(block, 0, count); dataBytes += count
                }
            }
            require(dataBytes > 0)
            writer.seek(0); writer.write(AudioPolicy.wavHeader(rate, channels, dataBytes)); writer.fd.sync()
        }
    }
    private suspend fun encodeLossy(source: AudioSource, output: File, target: AudioTarget, bitrate: Int, progress: (Int?) -> Unit) {
        val working = temporary("working-", target.extension); check(working.delete()); check(output.delete())
        try { withContext(Dispatchers.Main.immediate) {
            var transformer: Transformer? = null; var completed = false
            val handler = Handler(Looper.getMainLooper()); var poll: Runnable? = null
            try {
                suspendCancellableCoroutine<Unit> { continuation ->
                    val encoder = DefaultEncoderFactory.Builder(context).setEnableFallback(false)
                        .setRequestedAudioEncoderSettings(AudioEncoderSettings.Builder().setBitrate(bitrate).build()).build()
                    val factory = object : Codec.EncoderFactory by encoder {
                        override fun audioNeedsEncoding() = true
                        override fun createForAudioEncoding(request: Format, logSessionId: android.media.metrics.LogSessionId?): Codec {
                            require(request.sampleMimeType == target.codec && request.channelCount == source.info.channels &&
                                request.sampleRate == target.sampleRate(source.info.sampleRate))
                            return encoder.createForAudioEncoding(request, logSessionId)
                        }
                    }
                    val builder = Transformer.Builder(context).setAudioMimeType(target.codec).setEncoderFactory(factory)
                        .addListener(object : Transformer.Listener {
                            override fun onCompleted(composition: Composition, result: ExportResult) { completed = true; if (continuation.isActive) continuation.resume(Unit) }
                            override fun onError(composition: Composition, result: ExportResult, exception: ExportException) { if (continuation.isActive) continuation.resumeWithException(exception) }
                        })
                    if (target == AudioTarget.OGG) builder.setMuxerFactory(OggAudioMuxer.Factory())
                    val active = builder.build()
                    transformer = active
                    val item = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(source.file))).setRemoveVideo(true)
                        .setEffects(Effects(if (target == AudioTarget.OGG)
                            listOf(SonicAudioProcessor().apply { setOutputSampleRateHz(48_000) }, EncodingPassThrough())
                            else listOf(EncodingPassThrough()), emptyList())).build()
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
        export(AudioEdit(listOf(AudioClip(source)), source.info.sampleRate, source.info.channels), result, destination, allowNonSmallerCopy)
    }
    suspend fun export(edit: AudioEdit, result: AudioResult, destination: Uri, allowNonSmallerCopy: Boolean = false) {
        require(edit.clips.none { destination == it.source.originalUri })
        require(allowNonSmallerCopy || compressionBenefit(edit.sourceBytes, result.bytes) == CompressionBenefit.SMALLER)
        try {
            context.contentResolver.openOutputStream(destination, "w")?.use { output -> result.file.inputStream().use { input ->
                val buffer = ByteArray(65536)
                while (true) { currentCoroutineContext().ensureActive(); val size = input.read(buffer); if (size < 0) break; output.write(buffer, 0, size) }
                output.flush(); currentCoroutineContext().ensureActive()
            } } ?: throw IOException("Destination unavailable")
        } catch (failure: Throwable) { runCatching { DocumentsContract.deleteDocument(context.contentResolver, destination) }; throw failure }
    }
    fun cleanOldCache(retained: Set<File>) {
        com.example.contentstudio.storage.OwnedFileCleanup.files(folder, Regex("(?:source-[0-9]+\\.media|(?:output|working)-[0-9]+\\.(?:wav|m4a|ogg|flac|mp3))"), retained)
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

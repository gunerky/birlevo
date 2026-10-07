package com.example.contentstudio.audio

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.SystemClock
import java.nio.ByteBuffer
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Streams PCM16 into an awaited consumer; decoder buffers never escape their ownership. */
internal object PcmAudioDecoder {
    suspend fun read(source: AudioSource, progress: (Int?) -> Unit, consume: suspend (ByteBuffer) -> Unit) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(source.file.path)
            val track = (0 until extractor.trackCount).single {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            }
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val frameBytes = source.info.channels * 2
            suspend fun deliver(buffer: ByteBuffer, timeUs: Long) {
                require(buffer.remaining() % frameBytes == 0) { "Unaligned PCM" }
                consume(buffer)
                progress((timeUs / 10 / source.info.durationMs).toInt().coerceIn(0, 100))
            }
            if (source.info.mime == "audio/raw") {
                val buffer = ByteBuffer.allocate(1024 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    buffer.clear()
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) break
                    require(size <= buffer.capacity() && extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED == 0)
                    buffer.position(0); buffer.limit(size)
                    deliver(buffer, extractor.sampleTime)
                    extractor.advance()
                }
            } else {
                format.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                val decoder = MediaCodec.createDecoderByType(source.info.mime)
                codec = decoder
                decoder.configure(format, null, null, 0); decoder.start()
                val info = MediaCodec.BufferInfo()
                var inputEnd = false; var outputEnd = false
                var lastActivity = SystemClock.elapsedRealtime()
                while (!outputEnd) {
                    currentCoroutineContext().ensureActive()
                    check(SystemClock.elapsedRealtime() - lastActivity < 30_000) { "Audio decoder stalled" }
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
                            lastActivity = SystemClock.elapsedRealtime()
                        }
                    }
                    val slot = decoder.dequeueOutputBuffer(info, 10_000)
                    if (slot == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val pcm = decoder.outputFormat
                        require(!pcm.containsKey(MediaFormat.KEY_PCM_ENCODING) || pcm.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_16BIT)
                        require(pcm.getInteger(MediaFormat.KEY_SAMPLE_RATE) == source.info.sampleRate && pcm.getInteger(MediaFormat.KEY_CHANNEL_COUNT) == source.info.channels)
                        lastActivity = SystemClock.elapsedRealtime()
                    } else if (slot >= 0) {
                        try {
                            if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                                val buffer = checkNotNull(decoder.getOutputBuffer(slot))
                                buffer.position(info.offset); buffer.limit(info.offset + info.size)
                                deliver(buffer, info.presentationTimeUs)
                            }
                            outputEnd = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                            lastActivity = SystemClock.elapsedRealtime()
                        } finally { decoder.releaseOutputBuffer(slot, false) }
                    }
                }
            }
        } finally { try { codec?.release() } finally { extractor.release() } }
    }
}

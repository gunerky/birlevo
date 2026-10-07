package com.example.contentstudio.audio

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteOrder
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** LAME consumes bounded PCM16 blocks. No whole-file buffer, resampling or bitrate fallback. */
internal object Mp3AudioEncoder {
    fun supported(rate: Int, channels: Int, bitrate: Int): Boolean {
        if (!Mp3Policy.supports(rate, channels, bitrate)) return false
        return try { LameEncoder(rate, channels, bitrate).use { true } }
        catch (_: LinkageError) { false } catch (_: Exception) { false }
    }
    suspend fun encode(source: AudioSource, output: File, bitrate: Int, progress: (Int?) -> Unit) {
        val rate = source.info.sampleRate; val channels = source.info.channels
        require(Mp3Policy.supports(rate, channels, bitrate))
        LameEncoder(rate, channels, bitrate).use { encoder ->
            RandomAccessFile(output, "rw").use { writer ->
                writer.setLength(0)
                val pcm = ShortArray(4096 * channels)
                val encoded = ByteArray(12800)
                var frames = 0L
                fun write(size: Int) {
                    require(size in 0..encoded.size && writer.length() + size <= AudioPolicy.MAX_BYTES)
                    writer.write(encoded, 0, size)
                }
                PcmAudioDecoder.read(source, progress) { buffer ->
                    buffer.order(ByteOrder.LITTLE_ENDIAN)
                    while (buffer.hasRemaining()) {
                        currentCoroutineContext().ensureActive()
                        val count = minOf(pcm.size, buffer.remaining() / 2)
                        require(count > 0 && count % channels == 0)
                        for (i in 0 until count) pcm[i] = buffer.short
                        write(encoder.encode(pcm, count / channels, encoded)); frames += count / channels
                    }
                }
                require(frames > 0)
                currentCoroutineContext().ensureActive()
                write(encoder.flush(encoded))
                val tagSize = encoder.tag(encoded)
                require(tagSize.toLong() <= writer.length())
                writer.seek(0); writer.write(encoded, 0, tagSize); writer.fd.sync()
            }
        }
    }
}

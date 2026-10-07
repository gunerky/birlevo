package com.example.contentstudio.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Bounded decode -> sample-accurate selection -> explicit channel/rate conversion -> disk. */
@androidx.annotation.OptIn(UnstableApi::class)
internal object AudioEditPcm {
    private class RangeComplete : RuntimeException(null, null, false, false)
    suspend fun write(edit: AudioEdit, output: File, progress: (Int?) -> Unit) {
        RandomAccessFile(output, "rw").use { writer ->
            writer.setLength(0); writer.write(AudioPolicy.wavHeader(edit.rate, edit.channels, 0))
            val block = ByteArray(65536)
            val channelBuffer = ByteBuffer.allocateDirect(65536).order(ByteOrder.nativeOrder())
            var bytes = 0L; var completedMs = 0L
            fun write(buffer: ByteBuffer) {
                require(bytes + buffer.remaining() <= AudioPolicy.MAX_BYTES - 44)
                while (buffer.hasRemaining()) {
                    val count = minOf(block.size, buffer.remaining())
                    buffer.get(block, 0, count); writer.write(block, 0, count); bytes += count
                }
            }
            for (clip in edit.clips) {
                currentCoroutineContext().ensureActive()
                val info = clip.source.info
                val first = clip.range.startMs * info.sampleRate / 1000
                val last = clip.range.end(info.durationMs) * info.sampleRate / 1000
                require(last > first)
                val sonic = SonicAudioProcessor().apply { setOutputSampleRateHz(edit.rate) }
                val resample = info.sampleRate != edit.rate
                var position = 0L; var selected = 0L
                try {
                    if (resample) {
                        sonic.configure(AudioFormat(info.sampleRate, edit.channels, C.ENCODING_PCM_16BIT)); sonic.flush()
                    }
                    fun submit(buffer: ByteBuffer) {
                        if (resample) { sonic.queueInput(buffer); write(sonic.output) } else write(buffer)
                    }
                    try { PcmAudioDecoder.read(clip.source, {}) { pcm ->
                        currentCoroutineContext().ensureActive()
                        val frameBytes = info.channels * 2
                        val frames = pcm.remaining() / frameBytes
                        val from = maxOf(first, position)
                        val to = minOf(last, position + frames)
                        if (to > from) {
                            val part = pcm.duplicate().order(ByteOrder.LITTLE_ENDIAN).apply {
                                limit(pcm.position() + ((to - position) * frameBytes).toInt())
                                position(pcm.position() + ((from - position) * frameBytes).toInt())
                            }
                            selected += to - from
                            // Sonic requires native-order direct PCM. Also bound its input blocks.
                            while (part.hasRemaining()) {
                                currentCoroutineContext().ensureActive(); channelBuffer.clear()
                                while (part.hasRemaining() && channelBuffer.remaining() >= edit.channels * 2) {
                                    val left = part.short
                                    val right = if (info.channels == 2) part.short else left
                                    if (edit.channels == 1) channelBuffer.putShort(((left.toInt() + right.toInt()) / 2).toShort())
                                    else { channelBuffer.putShort(left); channelBuffer.putShort(right) }
                                }
                                channelBuffer.flip(); submit(channelBuffer)
                            }
                        }
                        position += frames
                        val done = (position * 1000 / info.sampleRate - clip.range.startMs).coerceIn(0, clip.range.duration(info.durationMs))
                        progress(((completedMs + done) * 100 / edit.durationMs).toInt().coerceIn(0, 100))
                        if (position >= last) throw RangeComplete()
                    } } catch (_: RangeComplete) { /* Decoder ownership is released by its finally block. */ }
                    require(selected > 0)
                    // Encoded source durations may include end padding; reject missing interior ranges.
                    val missing = last - first - selected
                    require(missing <= if (clip.range.end(info.durationMs) == info.durationMs) info.sampleRate * 3L / 10 else 1)
                    if (resample) { sonic.queueEndOfStream(); write(sonic.output); check(sonic.isEnded) }
                } finally { sonic.reset() }
                completedMs += clip.range.duration(info.durationMs)
            }
            require(bytes > 0)
            writer.seek(0); writer.write(AudioPolicy.wavHeader(edit.rate, edit.channels, bytes)); writer.fd.sync()
        }
    }
}

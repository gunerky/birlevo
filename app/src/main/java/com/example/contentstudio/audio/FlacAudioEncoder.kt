package com.example.contentstudio.audio

import android.media.MediaCodec
import android.media.MediaCodecList
import android.os.SystemClock
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Lossless encoding of the decoded PCM16. No whole-file PCM intermediate or native dependency. */
internal object FlacAudioEncoder {
    suspend fun encode(source: AudioSource, output: File, progress: (Int?) -> Unit) {
        val format = AudioOutputSupport().encoderFormat(source.info, AudioTarget.FLAC, 128_000)
        val name = checkNotNull(MediaCodecList(MediaCodecList.REGULAR_CODECS).findEncoderForFormat(format))
        val encoder = MediaCodec.createByCodecName(name)
        try {
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE); encoder.start()
            RandomAccessFile(output, "rw").use { writer ->
                writer.setLength(0)
                val info = MediaCodec.BufferInfo()
                val scratch = ByteArray(65_536)
                val md5 = MessageDigest.getInstance("MD5")
                var samples = 0L
                var header: ByteArray? = null
                var finished = false
                var wroteFrames = false
                var lastActivity = SystemClock.elapsedRealtime()
                fun writeHeader(data: ByteBuffer) {
                    require(data.remaining() in 42..65_536)
                    val bytes = ByteArray(data.remaining()); data.get(bytes)
                    val checked = FlacHeader.fromCodec(bytes, source.info.sampleRate, source.info.channels)
                    if (header == null) { header = checked; writer.write(checked) }
                    else check(header!!.contentEquals(checked)) { "FLAC header changed" }
                }
                suspend fun drain(waitUs: Long) {
                    var wait = waitUs
                    while (!finished) {
                        currentCoroutineContext().ensureActive()
                        val slot = encoder.dequeueOutputBuffer(info, wait)
                        wait = 0
                        if (slot == MediaCodec.INFO_TRY_AGAIN_LATER) break
                        if (slot == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            encoder.outputFormat.getByteBuffer("csd-0")?.duplicate()?.let(::writeHeader)
                            lastActivity = SystemClock.elapsedRealtime()
                        } else if (slot >= 0) {
                            try {
                                if (info.size > 0) {
                                    val buffer = checkNotNull(encoder.getOutputBuffer(slot))
                                    buffer.position(info.offset); buffer.limit(info.offset + info.size)
                                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) writeHeader(buffer)
                                    else {
                                        check(header != null)
                                        require(writer.length() + buffer.remaining() <= AudioPolicy.MAX_BYTES)
                                        while (buffer.hasRemaining()) {
                                            val count = minOf(scratch.size, buffer.remaining())
                                            buffer.get(scratch, 0, count); writer.write(scratch, 0, count)
                                        }
                                        wroteFrames = true
                                    }
                                }
                                finished = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                                lastActivity = SystemClock.elapsedRealtime()
                            } finally { encoder.releaseOutputBuffer(slot, false) }
                        }
                    }
                    check(SystemClock.elapsedRealtime() - lastActivity < 30_000) { "FLAC encoder stalled" }
                }
                suspend fun inputSlot(): Int {
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val slot = encoder.dequeueInputBuffer(10_000)
                        if (slot >= 0) return slot
                        drain(10_000)
                        check(!finished)
                    }
                }
                PcmAudioDecoder.read(source, progress) { pcm ->
                    while (pcm.hasRemaining()) {
                        val slot = inputSlot()
                        val input = checkNotNull(encoder.getInputBuffer(slot)); input.clear()
                        val frameBytes = source.info.channels * 2
                        val size = minOf(input.remaining(), pcm.remaining()).let { it - it % frameBytes }
                        check(size > 0)
                        val part = pcm.duplicate().apply { limit(position() + size) }
                        md5.update(part.duplicate())
                        input.put(part); pcm.position(pcm.position() + size)
                        encoder.queueInputBuffer(slot, 0, size, samples * 1_000_000 / source.info.sampleRate, 0)
                        samples += size / frameBytes
                        lastActivity = SystemClock.elapsedRealtime()
                        drain(0)
                    }
                }
                val slot = inputSlot()
                encoder.queueInputBuffer(slot, 0, 0, samples * 1_000_000 / source.info.sampleRate, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                while (!finished) drain(10_000)
                check(wroteFrames && samples > 0)
                writer.seek(0); writer.write(FlacHeader.finish(checkNotNull(header), samples, md5.digest())); writer.fd.sync()
            }
        } finally { encoder.release() }
    }
}

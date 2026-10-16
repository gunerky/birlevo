package com.example.contentstudio.audio

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Metadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.muxer.Muxer
import androidx.media3.muxer.BufferInfo
import androidx.media3.muxer.MuxerException
import androidx.media3.transformer.TransformerUtil
import com.google.common.collect.ImmutableList
import java.nio.ByteBuffer

/** Ogg/Opus only. Platform muxing includes Opus pre-skip and the final granule position. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class OggAudioMuxer(path: String) : Muxer {
    private val native = if (Build.VERSION.SDK_INT >= 29) MediaMuxer(path, MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG)
        else throw IllegalArgumentException("Ogg requires Android 10")
    private var started = false
    private var closed = false
    private var track: Int? = null
    override fun addTrack(format: Format): Int = guarded("add track") {
        check(!closed && track == null && format.sampleMimeType == "audio/opus")
        require(format.sampleRate == 48_000 && format.channelCount in 1..2)
        val media = MediaFormat.createAudioFormat("audio/opus", format.sampleRate, format.channelCount)
        format.initializationData.forEachIndexed { index, data -> media.setByteBuffer("csd-$index", ByteBuffer.wrap(data)) }
        native.addTrack(media).also { track = it }
    }
    override fun writeSampleData(trackId: Int, data: ByteBuffer, info: BufferInfo) {
        guarded("write sample") {
            check(!closed && trackId == track)
            require(info.presentationTimeUs >= 0)
            require(info.size in 0..data.remaining())
            val platformInfo = MediaCodec.BufferInfo().apply {
                set(data.position(), info.size, info.presentationTimeUs, TransformerUtil.getMediaCodecFlags(info.flags))
            }
            if (!started) { native.start(); started = true }
            native.writeSampleData(trackId, data, platformInfo)
        }
    }
    override fun addMetadataEntry(metadataEntry: Metadata.Entry) = Unit
    override fun close() {
        if (closed) return
        closed = true
        guarded("close") { try { if (started) native.stop() } finally { native.release() } }
    }
    private inline fun <T> guarded(action: String, block: () -> T): T = try { block() }
        catch (error: Exception) { throw MuxerException("Ogg: $action", error) }
    class Factory : Muxer.Factory {
        override fun create(path: String): Muxer = try { OggAudioMuxer(path) }
            catch (error: Exception) { throw MuxerException("Ogg: open", error) }
        override fun getSupportedSampleMimeTypes(trackType: Int): ImmutableList<String> =
            if (Build.VERSION.SDK_INT >= 29 && trackType == C.TRACK_TYPE_AUDIO) ImmutableList.of("audio/opus") else ImmutableList.of()
    }
}

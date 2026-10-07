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
import com.google.common.collect.ImmutableList
import java.nio.ByteBuffer

/** Ogg/Opus only. Platform muxing includes Opus pre-skip and the final granule position. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class OggAudioMuxer(path: String) : Muxer {
    private val native = if (Build.VERSION.SDK_INT >= 29) MediaMuxer(path, MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG)
        else throw IllegalArgumentException("Ogg requires Android 10")
    private var started = false
    private var closed = false
    private var track: Token? = null
    private class Token(val index: Int) : Muxer.TrackToken
    override fun addTrack(format: Format): Muxer.TrackToken = guarded("add track") {
        check(!closed && track == null && format.sampleMimeType == "audio/opus")
        require(format.sampleRate == 48_000 && format.channelCount in 1..2)
        val media = MediaFormat.createAudioFormat("audio/opus", format.sampleRate, format.channelCount)
        format.initializationData.forEachIndexed { index, data -> media.setByteBuffer("csd-$index", ByteBuffer.wrap(data)) }
        Token(native.addTrack(media)).also { track = it }
    }
    override fun writeSampleData(token: Muxer.TrackToken, data: ByteBuffer, info: MediaCodec.BufferInfo) {
        guarded("write sample") {
            check(!closed && token === track)
            require(info.presentationTimeUs >= 0)
            if (!started) { native.start(); started = true }
            native.writeSampleData((token as Token).index, data, info)
        }
    }
    override fun addMetadataEntry(metadataEntry: Metadata.Entry) = Unit
    override fun close() {
        if (closed) return
        closed = true
        guarded("close") { try { if (started) native.stop() } finally { native.release() } }
    }
    private inline fun <T> guarded(action: String, block: () -> T): T = try { block() }
        catch (error: Exception) { throw Muxer.MuxerException("Ogg: $action", error) }
    class Factory : Muxer.Factory {
        override fun create(path: String): Muxer = try { OggAudioMuxer(path) }
            catch (error: Exception) { throw Muxer.MuxerException("Ogg: open", error) }
        override fun getSupportedSampleMimeTypes(trackType: Int): ImmutableList<String> =
            if (Build.VERSION.SDK_INT >= 29 && trackType == C.TRACK_TYPE_AUDIO) ImmutableList.of("audio/opus") else ImmutableList.of()
    }
}

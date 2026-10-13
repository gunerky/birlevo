package com.example.contentstudio.media

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Metadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.muxer.Muxer
import com.google.common.collect.ImmutableList
import java.nio.ByteBuffer

/** Platform WebM adapter. Encoded picture and sound only; metadata is deliberately not copied. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class WebmMuxer(path: String) : Muxer {
    private val native = MediaMuxer(path, MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM)
    private var started = false
    private var closed = false
    private val tokens = mutableSetOf<Token>()
    private class Token(val index: Int) : Muxer.TrackToken

    override fun addTrack(format: Format): Muxer.TrackToken = guarded("add track") {
        check(!started && !closed)
        val mime = requireNotNull(format.sampleMimeType)
        val media = when (mime) {
            MimeTypes.VIDEO_VP8 -> {
                // WebM cannot represent MP4's rotation hint. Refuse to lose orientation.
                require(format.rotationDegrees == 0)
                MediaFormat.createVideoFormat(mime, format.width, format.height)
            }
            MimeTypes.AUDIO_OPUS -> {
                require(Build.VERSION.SDK_INT >= 29)
                MediaFormat.createAudioFormat(mime, format.sampleRate, format.channelCount)
            }
            else -> error("Unsupported WebM track")
        }
        format.initializationData.forEachIndexed { index, data ->
            media.setByteBuffer("csd-$index", ByteBuffer.wrap(data))
        }
        Token(native.addTrack(media)).also(tokens::add)
    }

    override fun writeSampleData(token: Muxer.TrackToken, data: ByteBuffer, info: MediaCodec.BufferInfo) {
        guarded("write sample") {
            check(!closed && token in tokens)
            require(info.presentationTimeUs >= 0) { "Negative WebM timestamp" }
            if (!started) { native.start(); started = true }
            native.writeSampleData((token as Token).index, data, info)
        }
    }

    // Metadata copying needs its own policy; this adapter preserves only encoded picture and sound.
    override fun addMetadataEntry(metadataEntry: Metadata.Entry) = Unit
    override fun close() {
        if (closed) return
        closed = true
        guarded("close") { try { if (started) native.stop() } finally { native.release() } }
    }

    private inline fun <T> guarded(action: String, block: () -> T): T = try { block() }
        catch (error: Exception) { throw Muxer.MuxerException("WebM: $action", error) }

    class Factory : Muxer.Factory {
        override fun create(path: String): Muxer = try { WebmMuxer(path) }
            catch (error: Exception) { throw Muxer.MuxerException("WebM: open", error) }
        override fun getSupportedSampleMimeTypes(trackType: Int): ImmutableList<String> = when (trackType) {
            C.TRACK_TYPE_VIDEO -> ImmutableList.of(MimeTypes.VIDEO_VP8)
            C.TRACK_TYPE_AUDIO -> if (Build.VERSION.SDK_INT >= 29) ImmutableList.of(MimeTypes.AUDIO_OPUS) else ImmutableList.of()
            else -> ImmutableList.of()
        }
    }
}

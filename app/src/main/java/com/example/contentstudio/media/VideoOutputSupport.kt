package com.example.contentstudio.media

import android.media.MediaCodecInfo
import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.EncoderSelector
import com.google.common.collect.ImmutableList

/** Query on IO. Checks the exact requested shape/rate/bitrate; never substitutes output settings. */
@androidx.annotation.OptIn(UnstableApi::class)
class VideoOutputSupport(private val inventory: () -> List<MediaCodecInfo> = {
    MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.toList()
}) {
    private val codecs by lazy(inventory)
    private val configured = object : LinkedHashMap<String, Boolean>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?) = size > 64
    }
    fun issue(info: VideoInfo, target: VideoTarget, format: VideoOutputFormat, forceAudioEncoding: Boolean = false): VideoOutputIssue? {
        // Preserve the established AVC path; its actual result still undergoes full validation.
        if (format == VideoOutputFormat.MP4_AVC && !forceAudioEncoding) return null
        videoOutputConstraint(format, info.hasAudio, Build.VERSION.SDK_INT, info.rotationDegrees)?.let { return it }
        if (videoEncoders(info, target, format).isEmpty()) return VideoOutputIssue.VIDEO_ENCODER
        val video = videoMediaFormat(info, target, format, encoder = false)
        if (!decodable(video)) return VideoOutputIssue.PREVIEW_DECODER
        if (info.hasAudio) {
            val rate = if (format == VideoOutputFormat.WEBM_VP8) 48000 else info.audioSampleRate
            val audio = MediaFormat.createAudioFormat(format.audioMime, rate, info.audioChannels)
            if (info.audioChannels < 1 || rate < 1) return VideoOutputIssue.AUDIO_ENCODER
            // Existing AAC can be retained in MP4. WebM deliberately re-encodes trimmed Opus.
            if ((forceAudioEncoding || format == VideoOutputFormat.WEBM_VP8 || info.audioMime != format.audioMime) &&
                codecs.none { it.isEncoder && supports(it, audio) }) return VideoOutputIssue.AUDIO_ENCODER
            if (!decodable(audio)) return VideoOutputIssue.PREVIEW_DECODER
        }
        return null
    }

    fun all(info: VideoInfo, target: VideoTarget) = VideoOutputFormat.entries.associateWith { issue(info, target, it) }
    fun selector(info: VideoInfo, target: VideoTarget, format: VideoOutputFormat): EncoderSelector =
        if (format == VideoOutputFormat.MP4_AVC) EncoderSelector.DEFAULT
        else EncoderSelector { mime -> if (mime == format.videoMime)
            ImmutableList.copyOf(videoEncoders(info, target, format)) else ImmutableList.of() }

    private fun videoEncoders(info: VideoInfo, target: VideoTarget, format: VideoOutputFormat): List<MediaCodecInfo> {
        val requested = videoMediaFormat(info, target, format)
        return codecs.filter { codec -> codec.isEncoder && format.videoMime in codec.supportedTypes && runCatching {
            val caps = codec.getCapabilitiesForType(format.videoMime)
            caps.encoderCapabilities.isBitrateModeSupported(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR) &&
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface in caps.colorFormats &&
                (format != VideoOutputFormat.MP4_HEVC || caps.profileLevels.any {
                    it.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain }) &&
                (caps.isFormatSupported(requested) || canConfigure(codec, requested, format))
        }.getOrDefault(false) }.sortedBy { if (Build.VERSION.SDK_INT >= 29 && it.isHardwareAccelerated) 0 else 1 }
    }

    private fun canConfigure(info: MediaCodecInfo, requested: MediaFormat, format: VideoOutputFormat): Boolean {
        // Some codecs report incomplete ranges (the API 36 software HEVC encoder reports
        // 512px yet encodes 1280px). Probe exact settings; never pad/scale the requested output.
        val key = "${info.name}|$requested"
        synchronized(configured) { if (configured[key] == true) return true }
        var codec: MediaCodec? = null
        var surface: android.view.Surface? = null
        return try {
            val setup = videoMediaFormatForProbe(requested, info, format)
            codec = MediaCodec.createByCodecName(info.name)
            codec.configure(setup, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            surface = codec.createInputSurface()
            synchronized(configured) { configured[key] = true }
            true
        } catch (_: Exception) { false }
        finally { surface?.release(); runCatching { codec?.release() } }
    }

    private fun videoMediaFormatForProbe(requested: MediaFormat, codec: MediaCodecInfo, format: VideoOutputFormat): MediaFormat =
        MediaFormat.createVideoFormat(format.videoMime, requested.getInteger(MediaFormat.KEY_WIDTH), requested.getInteger(MediaFormat.KEY_HEIGHT)).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, requested.getInteger(MediaFormat.KEY_BIT_RATE))
            setInteger(MediaFormat.KEY_FRAME_RATE, kotlin.math.round(requested.getFloat(MediaFormat.KEY_FRAME_RATE)).toInt())
            setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            if (format == VideoOutputFormat.MP4_HEVC) {
                setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.HEVCProfileMain)
                setInteger(MediaFormat.KEY_LEVEL, codec.getCapabilitiesForType(format.videoMime).profileLevels
                    .filter { it.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain }.maxOf { it.level })
            }
        }

    private fun videoMediaFormat(info: VideoInfo, target: VideoTarget, format: VideoOutputFormat, encoder: Boolean = true): MediaFormat {
        val portrait = target.width < target.height && format != VideoOutputFormat.WEBM_VP8
        return MediaFormat.createVideoFormat(format.videoMime, if (portrait) target.height else target.width,
            if (portrait) target.width else target.height).apply {
            if (encoder) {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, target.bitrate)
            }
            setFloat(MediaFormat.KEY_FRAME_RATE, info.frameRate ?: 30f)
        }
    }
    private fun decodable(format: MediaFormat) = codecs.any { !it.isEncoder && supports(it, format) }
    private fun supports(codec: MediaCodecInfo, format: MediaFormat): Boolean = runCatching {
        val mime = requireNotNull(format.getString(MediaFormat.KEY_MIME))
        mime in codec.supportedTypes && codec.getCapabilitiesForType(mime).isFormatSupported(format)
    }.getOrDefault(false)
}

class VideoOutputUnavailable(val issue: VideoOutputIssue) : IllegalArgumentException(issue.name)

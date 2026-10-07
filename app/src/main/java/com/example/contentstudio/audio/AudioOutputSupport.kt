package com.example.contentstudio.audio

import android.media.MediaCodecList
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build

/** Exact output settings, queried off the UI thread. No codec, channel or rate fallback. */
class AudioOutputSupport(private val inventory: () -> List<MediaCodecInfo> = {
    MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.toList()
}) {
    fun issue(source: AudioInfo, target: AudioTarget, bitrate: Int): AudioOutputIssue? {
        audioOutputConstraint(target, Build.VERSION.SDK_INT)?.let { return it }
        if (target == AudioTarget.WAV) return null
        return try {
            val codecs = inventory()
            fun supported(codec: MediaCodecInfo, format: MediaFormat) = runCatching {
                target.codec in codec.supportedTypes && codec.getCapabilitiesForType(target.codec).isFormatSupported(format)
            }.getOrDefault(false)
            if (codecs.none { it.isEncoder && supported(it, encoderFormat(source, target, bitrate)) }) AudioOutputIssue.ENCODER
            else if (codecs.none { !it.isEncoder && supported(it, MediaFormat.createAudioFormat(target.codec,
                    target.sampleRate(source.sampleRate), source.channels)) }) AudioOutputIssue.DECODER
            else null
        } catch (_: Exception) { AudioOutputIssue.CHECK_FAILED }
    }
    fun all(source: AudioInfo, bitrate: Int) = AudioTarget.entries.associateWith { issue(source, it, bitrate) }
    fun encoderFormat(source: AudioInfo, target: AudioTarget, bitrate: Int): MediaFormat =
        MediaFormat.createAudioFormat(target.codec, target.sampleRate(source.sampleRate), source.channels).apply {
            setInteger(MediaFormat.KEY_PCM_ENCODING, android.media.AudioFormat.ENCODING_PCM_16BIT)
            if (target.lossy) setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            if (target == AudioTarget.M4A) setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            if (target == AudioTarget.FLAC) setInteger(MediaFormat.KEY_FLAC_COMPRESSION_LEVEL, 5)
        }
}

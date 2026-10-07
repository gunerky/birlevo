package com.example.contentstudio.media

import com.example.contentstudio.audio.AudioPolicy

/** Immutable owned audio, with source trim and independent final-video placement. */
data class VideoAudioLayer(val asset: SessionAsset, val durationMs: Long, val rate: Int, val channels: Int,
    val sourceStartMs: Long = 0, val sourceEndMs: Long = durationMs, val startMs: Long = 0,
    val volume: Float = .5f, val originalVolume: Float = .5f) {
    init {
        require(asset.file == java.io.File(asset.file).name && asset.file.startsWith("sound-") &&
            !asset.file.contains('/') && !asset.file.contains('\\') && asset.bytes in 1..AudioPolicy.MAX_BYTES)
        require(asset.id.isNotBlank() && asset.id.length <= 128 && asset.name.length <= 256 && asset.uri.length <= 8192)
        require(durationMs in 1..AudioPolicy.MAX_DURATION_MS && rate in 8000..192000 && channels in 1..2)
        require(sourceStartMs >= 0 && sourceStartMs < sourceEndMs && sourceEndMs <= durationMs)
        require(startMs in 0..FormatSupport.MAX_VIDEO_DURATION_MS * 2)
        require(volume.isFinite() && volume in 0f..1f && originalVolume.isFinite() && originalVolume in 0f..1f)
    }
    fun length(videoMs: Long) = minOf(sourceEndMs - sourceStartMs, videoMs - startMs).coerceAtLeast(0)
    fun validTime(videoMs: Long) = length(videoMs) > 0
}

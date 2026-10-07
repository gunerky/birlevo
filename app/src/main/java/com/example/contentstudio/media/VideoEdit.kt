package com.example.contentstudio.media

/** Ordered, non-overlapping clips; original source files are never modified. */
data class VideoClip(val source: VideoSource, val trim: VideoTrim = VideoTrim(0, source.info.durationMs)) {
    init { require(trim.endMs <= source.info.durationMs) }
}

data class VideoEdit(val clips: List<VideoClip>) {
    init {
        require(clips.size in 1..2 && clips.map { it.source.file }.distinct().size == clips.size)
        require(durationMs in 1..FormatSupport.MAX_VIDEO_DURATION_MS)
    }
    val durationMs get() = clips.sumOf { it.trim.durationMs }
    val sourceBytes get() = clips.sumOf { it.source.bytes }
    val joined get() = clips.size > 1
    val hasAudio get() = clips.any { it.source.info.hasAudio }
    val channels get() = clips.filter { it.source.info.hasAudio }.maxOfOrNull { it.source.info.audioChannels } ?: 2
    /** One visible canvas follows the first clip's orientation; other clips fit without cropping. */
    fun profile() = clips.first().source.info.copy(durationMs = durationMs, hasAudio = hasAudio,
        audioMime = clips.firstOrNull { it.source.info.hasAudio }?.source?.info?.audioMime,
        audioSampleRate = if (joined) 48000 else clips.first().source.info.audioSampleRate,
        audioChannels = channels,
        frameRate = clips.mapNotNull { it.source.info.frameRate }.maxOrNull())
}

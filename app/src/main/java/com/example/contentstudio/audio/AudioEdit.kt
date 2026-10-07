package com.example.contentstudio.audio

/** Millisecond ranges are half-open. Null end retains the source's full-duration semantics. */
data class AudioRange(val startMs: Long = 0, val endMs: Long? = null) {
    fun end(durationMs: Long) = endMs ?: durationMs
    fun duration(durationMs: Long): Long {
        val end = end(durationMs)
        require(startMs >= 0 && startMs < end && end <= durationMs)
        return end - startMs
    }
    companion object {
        fun seconds(ms: Long): String = java.math.BigDecimal.valueOf(ms, 3).stripTrailingZeros().toPlainString()
        fun parse(start: String, end: String, durationMs: Long): AudioRange? = runCatching {
            fun milliseconds(text: String): Long {
                require(text.length in 1..16)
                val normalized = text.map { char ->
                    val digit = Character.digit(char, 10)
                    if (digit >= 0) ('0'.code + digit).toChar() else if (char == ',' || char == '٫') '.' else char
                }.joinToString("")
                require(normalized.matches(Regex("[0-9]+(\\.[0-9]{1,3})?")))
                return normalized.toBigDecimal().movePointRight(3).longValueExact()
            }
            val first = milliseconds(start); val last = milliseconds(end)
            AudioRange(first, last.takeUnless { it == durationMs }).also { it.duration(durationMs) }
        }.getOrNull()
    }
}

data class AudioClip(val source: AudioSource, val range: AudioRange = AudioRange())

data class AudioEdit(val clips: List<AudioClip>, val rate: Int, val channels: Int) {
    init {
        require(clips.size in 1..2)
        require(clips.map { it.source.file }.distinct().size == clips.size)
        require(rate in 8_000..192_000 && channels in 1..2)
        require(durationMs in 1..AudioPolicy.MAX_DURATION_MS)
    }
    val durationMs get() = clips.sumOf { it.range.duration(it.source.info.durationMs) }
    val sourceBytes get() = clips.sumOf { it.source.bytes }
    val info get() = AudioInfo("audio/raw", rate, channels, durationMs, false)
    val unchanged get() = clips.size == 1 && clips.single().let {
        it.range == AudioRange() && rate == it.source.info.sampleRate && channels == it.source.info.channels
    }
}

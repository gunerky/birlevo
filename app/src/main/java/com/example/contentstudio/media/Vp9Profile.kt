package com.example.contentstudio.media

/** VP9 uncompressed header: MSB-first frame marker, then low/high profile bits. */
object Vp9Profile {
    const val MAX_SAMPLE_BYTES = 32 * 1024 * 1024
    fun profile(firstByte: Int): Int {
        require(firstByte in 0..255 && firstByte ushr 6 == 2) { "Invalid VP9 frame marker" }
        return ((firstByte ushr 5) and 1) or (((firstByte ushr 4) and 1) shl 1)
    }
    fun requireSupported(firstByte: Int) {
        val profile = try { profile(firstByte) } catch (_: IllegalArgumentException) {
            throw MediaImportException(ImportIssue.INVALID_MEDIA)
        }
        if (profile != 0) throw MediaImportException(ImportIssue.VIDEO_CODEC)
    }
}

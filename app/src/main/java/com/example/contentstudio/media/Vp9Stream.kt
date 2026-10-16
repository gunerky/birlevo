package com.example.contentstudio.media

import android.media.MediaExtractor
import android.os.Build
import java.nio.ByteBuffer

/** Selected track only, on IO. The bitstream profile is canonical, not a filename/container hint. */
internal fun requireVp9Profile0(extractor: MediaExtractor) {
    extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
    val size = if (Build.VERSION.SDK_INT >= 28) extractor.sampleSize else Vp9Profile.MAX_SAMPLE_BYTES.toLong()
    if (size <= 0) throw MediaImportException(ImportIssue.INVALID_MEDIA)
    if (size > Vp9Profile.MAX_SAMPLE_BYTES) throw MediaImportException(ImportIssue.VIDEO_LIMIT)
    val packet = ByteBuffer.allocate(size.toInt())
    if (extractor.readSampleData(packet, 0) <= 0) throw MediaImportException(ImportIssue.INVALID_MEDIA)
    Vp9Profile.requireSupported(packet.get(0).toInt() and 255)
}

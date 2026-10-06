package com.example.contentstudio.media

import android.media.MediaFormat
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.container.NalUnitUtil

/** Some Android extractors omit AVC VUI color metadata. Read the already supplied SPS
 * with Media3's parser, without decoding frames or changing the format's buffer position. */
@androidx.annotation.OptIn(UnstableApi::class)
internal fun avcColorTransfer(format: MediaFormat): Int {
    val declared = if (format.containsKey(MediaFormat.KEY_COLOR_TRANSFER))
        format.getInteger(MediaFormat.KEY_COLOR_TRANSFER) else 0
    if (declared == C.COLOR_TRANSFER_ST2084 || declared == C.COLOR_TRANSFER_HLG) return declared
    val buffer = format.getByteBuffer("csd-0")?.duplicate() ?: return declared
    // Codec initialization data is tiny; never allocate an unbounded provider-supplied buffer.
    if (buffer.remaining() !in 4..128 * 1024) return declared
    val bytes = ByteArray(buffer.remaining()).also(buffer::get)
    val headers = mutableListOf<Int>()
    var index = 0
    while (index + 3 < bytes.size) {
        if (bytes[index] == 0.toByte() && bytes[index + 1] == 0.toByte()) {
            val prefix = if (bytes[index + 2] == 1.toByte()) 3
                else if (bytes[index + 2] == 0.toByte() && bytes[index + 3] == 1.toByte()) 4 else 0
            if (prefix > 0) { headers += index + prefix; index += prefix; continue }
        }
        index++
    }
    for ((position, header) in headers.withIndex()) {
        if (header >= bytes.size || (bytes[header].toInt() and 31) != 7) continue
        val limit = headers.getOrNull(position + 1) ?: bytes.size
        val transfer = runCatching { NalUnitUtil.parseSpsNalUnit(bytes, header, limit).colorTransfer }.getOrNull()
        if (transfer == C.COLOR_TRANSFER_ST2084 || transfer == C.COLOR_TRANSFER_HLG) return transfer
    }
    return declared
}

/** Initially accept HEVC Main 8-bit SDR. HDR and higher bit depths need a separate color pipeline. */
@androidx.annotation.OptIn(UnstableApi::class)
internal fun hevcColorTransfer(format: MediaFormat): Int {
    val declared = if (format.containsKey(MediaFormat.KEY_COLOR_TRANSFER)) format.getInteger(MediaFormat.KEY_COLOR_TRANSFER) else 0
    if (declared == C.COLOR_TRANSFER_ST2084 || declared == C.COLOR_TRANSFER_HLG)
        throw MediaImportException(ImportIssue.VIDEO_HDR)
    val buffer = format.getByteBuffer("csd-0")?.duplicate() ?: throw MediaImportException(ImportIssue.VIDEO_CODEC)
    if (buffer.remaining() !in 6..128 * 1024) throw MediaImportException(ImportIssue.VIDEO_CODEC)
    val bytes = ByteArray(buffer.remaining()).also(buffer::get)
    val headers = mutableListOf<Int>()
    var index = 0
    while (index + 3 < bytes.size) {
        if (bytes[index] == 0.toByte() && bytes[index + 1] == 0.toByte()) {
            val prefix = if (bytes[index + 2] == 1.toByte()) 3
                else if (bytes[index + 2] == 0.toByte() && bytes[index + 3] == 1.toByte()) 4 else 0
            if (prefix > 0) { headers += index + prefix; index += prefix; continue }
        }
        index++
    }
    for ((position, header) in headers.withIndex()) {
        if (header + 1 >= bytes.size || ((bytes[header].toInt() and 126) shr 1) != 33) continue
        val limit = headers.getOrNull(position + 1) ?: bytes.size
        val sps = runCatching { NalUnitUtil.parseH265SpsNalUnit(bytes, header, limit, null) }.getOrNull() ?: continue
        if (sps.colorTransfer == C.COLOR_TRANSFER_ST2084 || sps.colorTransfer == C.COLOR_TRANSFER_HLG)
            throw MediaImportException(ImportIssue.VIDEO_HDR)
        if (sps.bitDepthLumaMinus8 != 0 || sps.bitDepthChromaMinus8 != 0 || sps.chromaFormatIdc != 1)
            throw MediaImportException(ImportIssue.VIDEO_CODEC)
        return if (sps.colorTransfer != -1) sps.colorTransfer else declared
    }
    throw MediaImportException(ImportIssue.VIDEO_CODEC)
}

package com.example.contentstudio.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Native FLAC STREAMINFO, RFC 9639 section 8.2. Encoder metadata is bounded and validated. */
internal object FlacHeader {
    private const val SAMPLE_MASK = (1L shl 36) - 1
    fun fromCodec(data: ByteArray, rate: Int, channels: Int): ByteArray {
        require(data.size in 42..65_536 && String(data, 0, 4, Charsets.US_ASCII) == "fLaC")
        require(data[4].toInt() and 0x7f == 0 && data[5] == 0.toByte() && data[6] == 0.toByte() && data[7] == 34.toByte())
        val header = data.copyOf(42)
        header[4] = 0x80.toByte() // Keep STREAMINFO only, and mark it the last metadata block.
        val bits = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN).getLong(18)
        require((bits ushr 44).toInt() == rate && ((bits ushr 41) and 7).toInt() + 1 == channels && ((bits ushr 36) and 31).toInt() + 1 == 16)
        val block = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN)
        val minimum = block.getShort(8).toInt() and 0xffff
        val maximum = block.getShort(10).toInt() and 0xffff
        require(minimum in 16..65_535 && maximum in minimum..65_535)
        // Streaming encoder cannot update frame-size bounds. Zero explicitly means unknown.
        header.fill(0, 12, 18)
        return header
    }
    fun finish(header: ByteArray, samples: Long, md5: ByteArray): ByteArray {
        require(header.size == 42 && samples in 1..SAMPLE_MASK && md5.size == 16)
        return header.copyOf().apply {
            val bytes = ByteBuffer.wrap(this).order(ByteOrder.BIG_ENDIAN)
            bytes.putLong(18, (bytes.getLong(18) and SAMPLE_MASK.inv()) or samples)
            md5.copyInto(this, 26)
        }
    }
    fun samples(header: ByteArray): Long {
        require(header.size == 42)
        return ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN).getLong(18) and SAMPLE_MASK
    }
}

package com.example.contentstudio.media

import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Windows BITMAPINFOHEADER, uncompressed 24-bit BGR, bottom-up padded rows.
 * Only one pixel row is retained. Alpha is explicitly composited on white. */
object BmpWriter {
    fun rowBytes(width: Int): Int {
        require(width in 1..100_000)
        return ((width.toLong() * 3 + 3) / 4 * 4).toInt()
    }
    fun header(width: Int, height: Int): ByteArray {
        require(height in 1..100_000)
        val bytes = rowBytes(width).toLong() * height
        require(bytes + 54 <= FormatSupport.MAX_IMAGE_BYTES)
        return ByteBuffer.allocate(54).order(ByteOrder.LITTLE_ENDIAN).apply {
            put('B'.code.toByte()); put('M'.code.toByte()); putInt((bytes + 54).toInt())
            putInt(0); putInt(54); putInt(40); putInt(width); putInt(height)
            putShort(1); putShort(24); putInt(0); putInt(bytes.toInt())
            putInt(0); putInt(0); putInt(0); putInt(0)
        }.array()
    }
    fun write(width: Int, height: Int, output: OutputStream,
        readRow: (Int, IntArray) -> Unit, checkActive: () -> Unit = {}) {
        checkActive()
        output.write(header(width, height))
        val pixels = IntArray(width)
        val row = ByteArray(rowBytes(width))
        for (y in height - 1 downTo 0) {
            checkActive(); readRow(y, pixels)
            for (x in pixels.indices) {
                val color = pixels[x]; val alpha = color ushr 24
                for (channel in 0..2) {
                    val value = (color ushr (channel * 8)) and 255
                    row[x * 3 + channel] = ((value * alpha + 255 * (255 - alpha) + 127) / 255).toByte()
                }
            }
            output.write(row)
        }
        checkActive()
    }
}

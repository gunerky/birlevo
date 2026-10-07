package com.example.contentstudio.media

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class BmpWriterTest {
    @Test fun rowAlignmentBottomUpColorsAndAlphaAreByteExact() {
        val output = ByteArrayOutputStream()
        val rows = arrayOf(intArrayOf(0xffff0000.toInt()), intArrayOf(0x800000ff.toInt()))
        BmpWriter.write(1, 2, output, { y, row -> rows[y].copyInto(row) })
        val bytes = output.toByteArray()
        val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(62, bytes.size); assertEquals(62, header.getInt(2))
        assertEquals(54, header.getInt(10)); assertEquals(40, header.getInt(14))
        assertEquals(1, header.getInt(18)); assertEquals(2, header.getInt(22))
        assertEquals(24, header.getShort(28).toInt()); assertEquals(0, header.getInt(30))
        assertArrayEquals(byteArrayOf(255.toByte(), 127, 127, 0, 0, 0, 255.toByte(), 0), bytes.copyOfRange(54, 62))
        assertEquals(4, BmpWriter.rowBytes(1)); assertEquals(8, BmpWriter.rowBytes(2))
        assertEquals(12, BmpWriter.rowBytes(4))
    }
    @Test fun invalidGeometryAndOverLimitSizeFailBeforeWriting() {
        for ((w, h) in listOf(0 to 1, 1 to 0, Int.MAX_VALUE to 1, 100_000 to 100_000)) {
            val output = ByteArrayOutputStream()
            assertThrows(IllegalArgumentException::class.java) { BmpWriter.write(w, h, output, { _, _ -> }) }
            assertEquals(0, output.size())
        }
    }
    @Test fun cancellationStopsBeforeReadingRemainingRows() {
        var rows = 0
        assertThrows(java.util.concurrent.CancellationException::class.java) {
            BmpWriter.write(3, 10, ByteArrayOutputStream(), { _, _ -> rows++ },
                { if (rows == 2) throw java.util.concurrent.CancellationException() })
        }
        assertEquals(2, rows)
    }
}

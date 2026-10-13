package com.example.contentstudio.media

import org.junit.Assert.*
import org.junit.Test

class VideoFrameColorTest {
    @Test fun limitedAndFullRangeKeepBlackWhiteAndNeutralGray() {
        assertEquals(0xff000000.toInt(), VideoFrameColor.argb(16, 128, 128, 1, false))
        assertEquals(0xffffffff.toInt(), VideoFrameColor.argb(235, 128, 128, 1, false))
        assertEquals(0xff000000.toInt(), VideoFrameColor.argb(0, 128, 128, 2, true))
        assertEquals(0xffffffff.toInt(), VideoFrameColor.argb(255, 128, 128, 2, true))
        assertEquals(0xff808080.toInt(), VideoFrameColor.argb(128, 128, 128, 6, true))
    }
    @Test fun knownRedVectorsAndRangeClampsDoNotSwapChromaPlanes() {
        for ((standard, yuv) in listOf(2 to listOf(81, 90, 240), 1 to listOf(63, 102, 240))) {
            val color = VideoFrameColor.argb(yuv[0], yuv[1], yuv[2], standard, false)
            assertTrue((color ushr 16 and 255) >= 253)
            assertTrue((color ushr 8 and 255) <= 2); assertTrue((color and 255) <= 2)
        }
        assertEquals(0xff000000.toInt(), VideoFrameColor.argb(0, 128, 128, 1, false))
        assertEquals(0xffffffff.toInt(), VideoFrameColor.argb(255, 128, 128, 1, false))
    }
    @Test fun matrixOnlyExtendedMetadataUsesDeclaredCoefficientsAndRejectsUnknownValues() {
        for (y in listOf(16, 63, 128, 235)) for (u in listOf(16, 102, 240)) {
            assertEquals(VideoFrameColor.argb(y, u, 240, 1, false), VideoFrameColor.argb(y, u, 240, 71, false))
            assertEquals(VideoFrameColor.argb(y, u, 240, 2, false), VideoFrameColor.argb(y, u, 240, 85, false))
        }
        for (standard in listOf(7, 106, 65536)) {
            var denied = false
            try { VideoFrameColor.matrix(standard) } catch (_: IllegalArgumentException) { denied = true }
            assertTrue(denied)
        }
    }
}

package com.example.contentstudio.media

import org.junit.Assert.*
import org.junit.Test

class CompressionBenefitTest {
    @Test fun onlyAnActuallySmallerFileSavesSpace() {
        assertEquals(CompressionBenefit.SMALLER, compressionBenefit(100, 99))
        assertEquals(CompressionBenefit.SAME_SIZE, compressionBenefit(100, 100))
        assertEquals(CompressionBenefit.LARGER, compressionBenefit(100, 101))
    }
    @Test fun sourceTotalBitrateWarnsConservativelyIncludingBoundary() {
        assertTrue(videoSizeIncreaseRisk(125_000, 1_000, 1_000_000))
        assertTrue(videoSizeIncreaseRisk(125_000, 1_000, 2_000_000))
        assertFalse(videoSizeIncreaseRisk(125_000, 1_000, 999_999))
        assertFalse(videoSizeIncreaseRisk(512L * 1024 * 1024, 600_000, 4_000_000))
    }
    @Test(expected = IllegalArgumentException::class) fun emptyOutputIsNeverACompressionSuccess() {
        compressionBenefit(100, 0)
    }
}

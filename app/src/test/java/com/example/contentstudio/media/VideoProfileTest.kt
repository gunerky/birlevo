package com.example.contentstudio.media

import org.junit.Test
import org.junit.Assert.*

class VideoProfileTest {
    @Test fun portraitAndLandscapePreserveAspectAndHaveEvenDimensions() {
        assertEquals(VideoTarget(640, 360, 250_000), videoTarget(1920, 1080, 640, VideoQuality.SMALL))
        assertEquals(VideoTarget(360, 640, 250_000), videoTarget(1080, 1920, 640, VideoQuality.SMALL))
    }
    @Test fun smallerVideosAreNotUpscaledAndQualityChangesBitrate() {
        val small = videoTarget(320, 240, 1920, VideoQuality.SMALL)
        val high = videoTarget(320, 240, 1920, VideoQuality.HIGH)
        assertEquals(320, high.width); assertEquals(240, high.height)
        assertTrue(high.bitrate > small.bitrate)
    }
    @Test(expected = IllegalArgumentException::class) fun invalidResolutionIsRejected() {
        videoTarget(1920, 1080, 1234, VideoQuality.BALANCED)
    }
}

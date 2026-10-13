package com.example.contentstudio.media

import org.junit.Test
import org.junit.Assert.*

class VideoOutputPolicyTest {
    @Test fun webmAudioAndRotationLimitsNeverBlockExistingMp4Targets() {
        assertEquals(VideoOutputIssue.PLATFORM, videoOutputConstraint(VideoOutputFormat.WEBM_VP8, true, 28, 0))
        assertNull(videoOutputConstraint(VideoOutputFormat.WEBM_VP8, false, 24, 0))
        assertNull(videoOutputConstraint(VideoOutputFormat.WEBM_VP8, true, 29, 90))
        assertEquals(VideoOutputIssue.ORIENTATION, videoOutputConstraint(VideoOutputFormat.WEBM_VP8, false, 36, 180))
        for (format in listOf(VideoOutputFormat.MP4_AVC, VideoOutputFormat.MP4_HEVC)) {
            for (rotation in listOf(0, 90, 180, 270)) assertNull(videoOutputConstraint(format, true, 24, rotation))
        }
    }
}

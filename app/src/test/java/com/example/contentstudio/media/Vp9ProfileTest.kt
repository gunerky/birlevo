package com.example.contentstudio.media

import org.junit.Test
import org.junit.Assert.*

class Vp9ProfileTest {
    @Test fun lowAndHighProfileBitsAreReadInSpecificationOrder() {
        assertEquals(0, Vp9Profile.profile(0x82))
        assertEquals(1, Vp9Profile.profile(0xa2))
        assertEquals(2, Vp9Profile.profile(0x92))
        assertEquals(3, Vp9Profile.profile(0xb2))
        Vp9Profile.requireSupported(0x82)
        for (header in listOf(0xa2, 0x92, 0xb2)) {
            try { Vp9Profile.requireSupported(header); fail("Unsupported chroma/bit depth") }
            catch (error: MediaImportException) { assertEquals(ImportIssue.VIDEO_CODEC, error.issue) }
        }
    }
    @Test fun invalidMarkersAreRejectedAndHdrStillHasNoImplicitToneMap() {
        for (header in listOf(0, 0x42, 0xc2)) {
            try { Vp9Profile.requireSupported(header); fail("Marker") }
            catch (error: MediaImportException) { assertEquals(ImportIssue.INVALID_MEDIA, error.issue) }
        }
        FormatSupport.validateVideoTracks(listOf(VideoTrackSupport("video/x-vnd.on2.vp9")))
        try { FormatSupport.validateVideoTracks(listOf(VideoTrackSupport("video/x-vnd.on2.vp9", 7))); fail("HDR") }
        catch (error: MediaImportException) { assertEquals(ImportIssue.VIDEO_HDR, error.issue) }
    }
    @Test fun bothWebmTargetsPreservePlatformAndOrientationRules() {
        for (format in listOf(VideoOutputFormat.WEBM_VP8, VideoOutputFormat.WEBM_VP9)) {
            assertEquals(VideoOutputIssue.PLATFORM, videoOutputConstraint(format, true, 28, 0))
            assertNull(videoOutputConstraint(format, false, 24, 0))
            assertEquals(VideoOutputIssue.ORIENTATION, videoOutputConstraint(format, false, 36, 180))
        }
    }
}

package com.example.contentstudio.media

import org.junit.Assert.*
import org.junit.Test

class FormatSupportTest {
    private fun reason(expected: ImportIssue, block: () -> Unit) {
        assertEquals(expected, assertThrows(MediaImportException::class.java, block).issue)
    }
    @Test fun extensionOrContainerAloneDoesNotAuthorizeCodec() {
        reason(ImportIssue.VIDEO_CODEC) { FormatSupport.validateVideoTracks(listOf(VideoTrackSupport("video/mp4v-es"))) }
        reason(ImportIssue.VIDEO_CODEC) { FormatSupport.validateVideoTracks(listOf(VideoTrackSupport("video/avc"), VideoTrackSupport("audio/ac3"))) }
        FormatSupport.validateVideoTracks(listOf(VideoTrackSupport("video/avc"), VideoTrackSupport("audio/mp4a-latm")))
    }
    @Test fun phoneInputsDoNotExpandOutputFormatsOrWeakenHdrAndTrackRules() {
        FormatSupport.validateImage("image/heif", 4000, 3000)
        FormatSupport.validateImage("image/avif", 4000, 3000)
        FormatSupport.validateVideoTracks(listOf(VideoTrackSupport("video/hevc"), VideoTrackSupport("audio/mp4a-latm")))
        FormatSupport.validateVideoTracks(listOf(VideoTrackSupport("video/x-vnd.on2.vp8"), VideoTrackSupport("audio/opus")))
        reason(ImportIssue.VIDEO_HDR) { FormatSupport.validateVideoTracks(listOf(VideoTrackSupport("video/hevc", 7))) }
        reason(ImportIssue.VIDEO_TRACKS) { FormatSupport.validateVideoTracks(listOf(VideoTrackSupport("video/hevc"), VideoTrackSupport("text/vtt"))) }
        assertEquals(listOf("video/mp4"), FormatSupport.capabilities.first { it.tool == SupportedTool.VIDEO }.outputs)
        assertFalse("image/heif" in FormatSupport.capabilities.first { it.tool == SupportedTool.IMAGE }.outputs)
    }
    @Test fun hdrExtraTracksAndInvalidRatesHaveDistinctReasons() {
        reason(ImportIssue.VIDEO_HDR) { FormatSupport.validateVideoTracks(listOf(VideoTrackSupport("video/avc", 6))) }
        reason(ImportIssue.VIDEO_TRACKS) { FormatSupport.validateVideoTracks(listOf(VideoTrackSupport("video/avc"), VideoTrackSupport("text/vtt"))) }
        reason(ImportIssue.VIDEO_TRACKS) { FormatSupport.validateVideoTracks(listOf(VideoTrackSupport("video/avc"), VideoTrackSupport("audio/mp4a-latm"), VideoTrackSupport("audio/mp4a-latm"))) }
        for (rate in listOf(61f, Float.NaN, Float.POSITIVE_INFINITY))
            reason(ImportIssue.VIDEO_LIMIT) { FormatSupport.validateVideoTracks(listOf(VideoTrackSupport("video/avc", frameRate = rate))) }
        FormatSupport.validateVideoTracks(listOf(VideoTrackSupport("video/avc", frameRate = 60f)))
    }
    @Test fun imageDetectionAndBoundsAreIndependentOfName() {
        reason(ImportIssue.IMAGE_FORMAT) { FormatSupport.validateImage("image/gif", 1, 1) }
        reason(ImportIssue.INVALID_MEDIA) { FormatSupport.validateImage(null, -1, -1) }
        reason(ImportIssue.IMAGE_LIMIT) { FormatSupport.validateImage("image/jpeg", 10_001, 10_000) }
        FormatSupport.validateImage("image/jpeg", 10_000, 10_000)
        FormatSupport.validateImage("image/png", 1, 1)
    }
    @Test fun emptyOrAudioOnlyVideoCannotBeImported() {
        reason(ImportIssue.INVALID_MEDIA) { FormatSupport.validateVideoTracks(emptyList()) }
        reason(ImportIssue.INVALID_MEDIA) { FormatSupport.validateVideoTracks(listOf(VideoTrackSupport("audio/mp4a-latm"))) }
    }
    @Test fun pdfReaderInputIsSeparateFromPdfCreationTools() {
        val pdfTools = FormatSupport.capabilities.filter { "application/pdf" in it.outputs }
        assertEquals(setOf(SupportedTool.PRESENTATION, SupportedTool.PHOTO_PDF, SupportedTool.PDF_READER, SupportedTool.PDF_PAGES, SupportedTool.TEXT_DOCUMENT), pdfTools.map { it.tool }.toSet())
        assertEquals(listOf(SupportedTool.PDF_READER, SupportedTool.PDF_PAGES), FormatSupport.capabilities.filter { "application/pdf" in it.inputs }.map { it.tool })
        assertEquals(ImageOutputFormat.entries.map { it.mime }.toSet(), FormatSupport.capabilities.first { it.tool == SupportedTool.IMAGE }.outputs.toSet())
    }
}

package com.example.contentstudio.media

/** Operation-level support, independent of UI and filenames. Adding a MIME alone is not decoder support. */
enum class SupportedTool { IMAGE, VIDEO, PRESENTATION, PHOTO_PDF, PDF_READER, PDF_PAGES, TEXT_DOCUMENT, AUDIO }
data class FormatCapability(val tool: SupportedTool, val inputs: List<String>, val outputs: List<String>)

object FormatSupport {
    val imageOutputMimes = ImageOutputFormat.entries.map { it.mime }
    val imageMimes = imageOutputMimes + listOf("image/heic", "image/heif", "image/avif")
    val videoMimes = listOf("video/mp4", "video/quicktime", "video/3gpp", "video/x-matroska", "video/webm")
    const val MAX_IMAGE_BYTES = 128L * 1024 * 1024
    const val MAX_IMAGE_PIXELS = 100_000_000L
    const val MAX_VIDEO_BYTES = 512L * 1024 * 1024
    const val MAX_VIDEO_DURATION_MS = 600_000L
    val capabilities = listOf(
        FormatCapability(SupportedTool.AUDIO, com.example.contentstudio.audio.AudioPolicy.inputMimes + videoMimes, com.example.contentstudio.audio.AudioTarget.entries.map { it.mime }),
        FormatCapability(SupportedTool.IMAGE, imageMimes, imageOutputMimes),
        FormatCapability(SupportedTool.VIDEO, videoMimes, listOf("video/mp4")),
        FormatCapability(SupportedTool.PRESENTATION, imageMimes, listOf("application/pdf")),
        FormatCapability(SupportedTool.PHOTO_PDF, imageMimes, listOf("application/pdf")),
        FormatCapability(SupportedTool.PDF_READER, listOf("application/pdf"), listOf("application/pdf") + PdfReaderPolicy.imageFormats.map { it.mime }),
        FormatCapability(SupportedTool.PDF_PAGES, listOf("application/pdf"), listOf("application/pdf")),
        FormatCapability(SupportedTool.TEXT_DOCUMENT, com.example.contentstudio.documents.DocumentPolicy.inputMimes,
            com.example.contentstudio.documents.DocumentTarget.entries.map { it.mime }),
    )

    fun validateImage(mime: String?, width: Int, height: Int) {
        if (mime == null || width <= 0 || height <= 0) throw MediaImportException(ImportIssue.INVALID_MEDIA)
        if (mime !in imageMimes) throw MediaImportException(ImportIssue.IMAGE_FORMAT)
        if (width.toLong() * height > MAX_IMAGE_PIXELS) throw MediaImportException(ImportIssue.IMAGE_LIMIT)
    }

    fun validateVideoTracks(tracks: List<VideoTrackSupport>) {
        var video = 0
        var audio = 0
        tracks.forEach { track ->
            when (track.mime) {
                "video/avc", "video/hevc", "video/x-vnd.on2.vp8" -> {
                    video++
                    if (track.colorTransfer == 6 || track.colorTransfer == 7) throw MediaImportException(ImportIssue.VIDEO_HDR)
                    if (track.frameRate != null && !(track.frameRate > 0f && track.frameRate <= 60f)) throw MediaImportException(ImportIssue.VIDEO_LIMIT)
                }
                "audio/mp4a-latm", "audio/opus", "audio/vorbis", "audio/mpeg" -> audio++
                else -> throw MediaImportException(if (track.mime?.startsWith("video/") == true ||
                    track.mime?.startsWith("audio/") == true) ImportIssue.VIDEO_CODEC else ImportIssue.VIDEO_TRACKS)
            }
        }
        if (video == 0) throw MediaImportException(ImportIssue.INVALID_MEDIA)
        if (video != 1 || audio > 1) throw MediaImportException(ImportIssue.VIDEO_TRACKS)
    }
}

data class VideoTrackSupport(val mime: String?, val colorTransfer: Int = 0, val frameRate: Float? = null)
enum class ImportIssue { IMAGE_FORMAT, IMAGE_ANIMATED, IMAGE_LIMIT, IMAGE_DEVICE, VIDEO_CONTAINER, VIDEO_CODEC,
    VIDEO_HDR, VIDEO_TRACKS, VIDEO_LIMIT, INVALID_MEDIA, FILE_ACCESS, MEMORY, PHOTO_LIMIT }
class MediaImportException(val issue: ImportIssue) : IllegalArgumentException(issue.name)

fun importIssue(error: Exception): ImportIssue? = when (error) {
    is MediaImportException -> error.issue
    is SecurityException, is java.io.FileNotFoundException -> ImportIssue.FILE_ACCESS
    else -> null
}

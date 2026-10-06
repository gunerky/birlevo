package com.example.contentstudio.media

import java.io.File
import org.junit.Test
import org.junit.Assert.*

class MediaSessionCodecTest {
    private val source = SessionAsset("id", "source-123.mp4", "content://test/original", "İstanbul 📽", 100)
    @Test fun videoKeepsAllQualityResolutionAndTrimOptions() {
        for (quality in VideoQuality.entries) for (edge in listOf(640, 1280, 1920)) {
            val value = VideoSession(source, quality, edge, VideoTrim(100, 2500))
            assertEquals(value, MediaSessionCodec.video(MediaSessionCodec.video(value)))
        }
    }
    @Test fun photoOrderIdsAndUnicodeNamesRoundTripWithoutMediaOrResultBytes() {
        val assets = (0 until 100).map { source.copy(id = "p$it", file = "page-$it.png", name = "北京 $it") }
        val value = PhotoSession(assets.reversed(), 4096)
        val encoded = MediaSessionCodec.photos(value)
        assertEquals(value, MediaSessionCodec.photos(encoded))
        assertTrue(encoded.toByteArray().size < 40_000)
        assertFalse(encoded.contains("poster")); assertFalse(encoded.contains("confirmedCopy"))
    }
    @Test fun foreignVersionsWrongKindInvalidEdgesAndDuplicateReferencesAreRejected() {
        val video = MediaSessionCodec.video(VideoSession(source, VideoQuality.HIGH, 1920, VideoTrim(0, 1000)))
        for (invalid in listOf(video.replace("\"version\":1", "\"version\":2"), video.replace("\"edge\":1920", "\"edge\":200"))) {
            assertThrows(IllegalArgumentException::class.java) { MediaSessionCodec.video(invalid) }
        }
        assertThrows(IllegalArgumentException::class.java) { MediaSessionCodec.video(MediaSessionCodec.photos(PhotoSession(emptyList(), 2048))) }
        assertThrows(IllegalArgumentException::class.java) { MediaSessionCodec.photos(MediaSessionCodec.photos(PhotoSession(listOf(source, source), 2048))) }
        assertThrows(IllegalArgumentException::class.java) { MediaSessionCodec.photos(MediaSessionCodec.photos(PhotoSession(List(101) { source.copy(id = "$it", file = "page-$it.png") }, 2048))) }
    }
    @Test fun fileReferencesRejectTraversalMissingAndSizeMismatches() {
        val folder = java.nio.file.Files.createTempDirectory("birlevo-session-test").toFile()
        val file = File(folder, source.file).apply { writeBytes(ByteArray(100)) }
        try {
            assertEquals(file, source.resolve(folder, "source-", 512))
            for (bad in listOf(source.copy(file = "../${source.file}"), source.copy(file = "C:\\outside.mp4"),
                source.copy(bytes = 99), source.copy(bytes = 513), source.copy(file = "source-missing.mp4"))) {
                assertThrows(IllegalArgumentException::class.java) { bad.resolve(folder, "source-", 512) }
            }
        } finally { file.delete(); folder.delete() }
    }
}

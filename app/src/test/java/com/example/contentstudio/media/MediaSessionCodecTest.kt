package com.example.contentstudio.media

import java.io.File
import org.junit.Test
import org.junit.Assert.*

class MediaSessionCodecTest {
    private val source = SessionAsset("id", "source-123.mp4", "content://test/original", "İstanbul 📽", 100)
    @Test fun joinedClipsRoundTripOrderRangesAndProduceDistinctRequests() {
        val clips = listOf(SavedVideoClip(source, VideoTrim(100, 1100)),
            SavedVideoClip(source.copy(id = "second", file = "source-456.mp4", uri = "content://test/second"), VideoTrim(500, 1750)))
        val saved = VideoSession(source, VideoQuality.HIGH, 1920, clips.first().trim, VideoOutputFormat.WEBM_VP8, clips)
        val json = MediaSessionCodec.video(saved)
        assertEquals(2, org.json.JSONObject(json).getInt("version"))
        assertEquals(saved, MediaSessionCodec.video(json))
        val reversed = saved.copy(asset = clips.last().asset, trim = clips.last().trim, clips = clips.reversed())
        assertNotEquals(com.example.contentstudio.processing.ProcessingCodec.fingerprint(json),
            com.example.contentstudio.processing.ProcessingCodec.fingerprint(MediaSessionCodec.video(reversed)))
        assertFalse(json.contains("confirmedCopy")); assertFalse(json.contains("poster"))
    }
    @Test fun joinedCorruptCountDuplicateAndMissingRangeNeverBecomeFullSource() {
        val clips = listOf(SavedVideoClip(source, VideoTrim(0, 1000)),
            SavedVideoClip(source.copy(file = "source-second.mp4"), VideoTrim(0, 1000)))
        val json = MediaSessionCodec.video(VideoSession(source, VideoQuality.BALANCED, 640, clips.first().trim, clips = clips))
        assertThrows(IllegalArgumentException::class.java) { MediaSessionCodec.video(VideoSession(source,
            VideoQuality.BALANCED, 640, clips.first().trim, clips = listOf(clips.first(), clips.first()))) }
        val missing = org.json.JSONObject(json).apply { getJSONArray("clips").getJSONObject(1).remove("end") }
        assertThrows(org.json.JSONException::class.java) { MediaSessionCodec.video(missing.toString()) }
        val count = org.json.JSONObject(json).apply { getJSONArray("clips").remove(1) }
        assertThrows(IllegalArgumentException::class.java) { MediaSessionCodec.video(count.toString()) }
    }
    @Test fun videoKeepsAllQualityResolutionAndTrimOptions() {
        for (quality in VideoQuality.entries) for (edge in listOf(640, 1280, 1920)) for (format in VideoOutputFormat.entries) {
            val value = VideoSession(source, quality, edge, VideoTrim(100, 2500), format)
            assertEquals(value, MediaSessionCodec.video(MediaSessionCodec.video(value)))
        }
    }
    @Test fun legacyAvcFingerprintIsPreservedAndTargetChangesRequestIdentity() {
        val legacy = org.json.JSONObject().put("version", 1).put("kind", "video")
            .put("source", org.json.JSONObject().put("id", source.id).put("file", source.file).put("uri", source.uri)
                .put("name", source.name).put("bytes", source.bytes))
            .put("quality", "BALANCED").put("edge", 1280).put("start", 0).put("end", 2500).toString()
        val decoded = MediaSessionCodec.video(legacy)
        assertEquals(VideoOutputFormat.MP4_AVC, decoded.format)
        assertEquals(legacy, MediaSessionCodec.video(decoded))
        val fingerprints = VideoOutputFormat.entries.map {
            com.example.contentstudio.processing.ProcessingCodec.fingerprint(MediaSessionCodec.video(decoded.copy(format = it)))
        }
        assertEquals(VideoOutputFormat.entries.size, fingerprints.distinct().size)
        assertThrows(IllegalArgumentException::class.java) {
            MediaSessionCodec.video(org.json.JSONObject(legacy).put("format", "UNKNOWN").toString())
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

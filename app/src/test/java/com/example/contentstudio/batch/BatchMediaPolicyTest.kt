package com.example.contentstudio.batch

import com.example.contentstudio.audio.*
import com.example.contentstudio.media.*
import java.util.UUID
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BatchMediaPolicyTest {
    private fun item(kind: BatchKind, extension: String) = BatchItem(UUID.randomUUID().toString(), "source-123.mp4",
        "content://synthetic/original", "media", 200, if (kind == BatchKind.VIDEO) 320 else 0,
        if (kind == BatchKind.VIDEO) 240 else 0, "output-456.$extension", 100, durationMs = 3000)
    @Test fun videoProfilesRoundTripAllFormatsQualitiesAndResolutions() {
        for (format in VideoOutputFormat.entries) for (quality in VideoQuality.entries) for (edge in listOf(640,1280,1920)) {
            val options = BatchOptions(kind = BatchKind.VIDEO, videoFormat = format, videoQuality = quality, videoEdge = edge)
            val queue = BatchQueue(options = options, items = listOf(item(BatchKind.VIDEO, format.extension)))
            assertEquals(queue, BatchPolicy.decode(BatchPolicy.encode(queue)))
            assertEquals(2, JSONObject(BatchPolicy.encode(queue)).getInt("version"))
        }
    }
    @Test fun audioTargetsAndBitratesRoundTripWithoutSerializingApprovals() {
        for (target in AudioTarget.entries) for (bitrate in AudioPolicy.bitrates) {
            val options = BatchOptions(kind = BatchKind.AUDIO, audioTarget = target, audioBitrate = bitrate)
            val queue = BatchQueue(options = options, items = listOf(item(BatchKind.AUDIO, target.extension)))
            val text = BatchPolicy.encode(queue)
            assertEquals(queue, BatchPolicy.decode(text)); assertFalse(text.contains("approved"))
        }
    }
    @Test fun legacyImageCheckpointRemainsVersionOneAndUnchanged() {
        val image = BatchItem(UUID.randomUUID().toString(), "source-123.image", "content://synthetic/image", "a.png", 200, 10, 10)
        val queue = BatchQueue(items = listOf(image))
        val json = BatchPolicy.encode(queue)
        assertEquals(1, JSONObject(json).getInt("version")); assertFalse(json.contains("videoFormat"))
        assertEquals(queue, BatchPolicy.decode(json))
    }
    @Test fun sourceAndOutputSizeCapsAllowLargeMediaWithoutReducingImageLimits() {
        val large = 256L * 1024 * 1024
        val queue = BatchQueue(options = BatchOptions(kind = BatchKind.VIDEO), items = listOf(item(BatchKind.VIDEO,"mp4").copy(bytes = large, outputBytes = large)))
        assertEquals(queue, BatchPolicy.decode(BatchPolicy.encode(queue)))
        assertThrows(Exception::class.java) { BatchPolicy.requireExportable(queue, emptySet()) }
        assertEquals(1, BatchPolicy.requireExportable(queue, setOf(queue.items.single().copyKey!!)).size)
        assertThrows(Exception::class.java) { BatchPolicy.decode(BatchPolicy.encode(queue.copy(items = listOf(queue.items.single().copy(bytes = BatchPolicy.MAX_BYTES + 1))))) }
    }
    @Test fun wrongMediaMetadataAndTargetsCannotRestoreAsValidResults() {
        val base = BatchPolicy.encode(BatchQueue(options = BatchOptions(kind = BatchKind.VIDEO), items = listOf(item(BatchKind.VIDEO, "mp4"))))
        val mutations: List<(JSONObject) -> Unit> = listOf(
            { it.put("kind", "IMAGE") }, { it.put("videoEdge", 4096) }, { it.put("audioBitrate", 9000) },
            { it.getJSONArray("items").getJSONObject(0).put("durationMs", 0) },
            { it.getJSONArray("items").getJSONObject(0).put("durationMs", 600001) },
            { it.getJSONArray("items").getJSONObject(0).put("audioIssue", "ENCODER") },
            { it.getJSONArray("items").getJSONObject(0).put("output", "output-wrong.wav") },
        )
        mutations.forEach { change -> assertThrows(Exception::class.java) { BatchPolicy.decode(JSONObject(base).also(change).toString()) } }
    }
    @Test fun exactDeviceFailureIsKeptPerRowAndDoesNotHideOtherCompletedResults() {
        val good = item(BatchKind.VIDEO, "mp4")
        val failure = good.copy(id = UUID.randomUUID().toString(), source = "source-234.mp4", output = null, outputBytes = 0,
            issue = ImportIssue.INVALID_MEDIA, videoIssue = VideoOutputIssue.ORIENTATION)
        val queue = BatchQueue(options = BatchOptions(kind = BatchKind.VIDEO), items = listOf(good, failure))
        assertEquals(queue, BatchPolicy.decode(BatchPolicy.encode(queue)))
        assertEquals(listOf(good), BatchPolicy.requireExportable(queue, emptySet()))
    }
}

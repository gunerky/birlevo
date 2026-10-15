package com.example.contentstudio.batch

import com.example.contentstudio.media.*
import java.io.File
import java.util.UUID
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BatchImagePolicyTest {
    private fun item(bytes: Long = 100, outputBytes: Long = 50) = BatchImage(UUID.randomUUID().toString(),
        "source-123.image", "content://original/one", "../photo.JPG", bytes, 10, 10, "output-456.jpg", outputBytes)
    @Test fun metadataRoundTripsEveryFormatWithoutSerializingApprovals() {
        for (format in ImageOutputFormat.entries) for (edge in listOf(1280, 1920, 2560)) {
            val batch = BatchImages(options = BatchOptions(format, 95, edge),
                items = listOf(item().copy(output = "output-456.${format.extension}")))
            val encoded = BatchImagePolicy.encode(batch)
            assertEquals(batch, BatchImagePolicy.decode(encoded))
            assertFalse(encoded.contains("approved")); assertFalse(encoded.contains("preview"))
        }
    }
    @Test fun equalAndLargerCopiesNeedTheirOwnActualResultApproval() {
        val smaller = item()
        val equal = item(100, 100).copy(source = "source-234.image", output = "output-567.jpg")
        val larger = item(100, 200).copy(source = "source-345.image", output = "output-678.jpg")
        val batch = BatchImages(items = listOf(smaller, equal, larger))
        assertThrows(IllegalArgumentException::class.java) { BatchImagePolicy.requireExportable(batch, emptySet()) }
        assertThrows(IllegalArgumentException::class.java) { BatchImagePolicy.requireExportable(batch, setOf(equal.copyKey!!)) }
        assertEquals(3, BatchImagePolicy.requireExportable(batch, setOf(equal.copyKey!!, larger.copyKey!!)).size)
        val stale = batch.copy(items = listOf(larger.copy(output = "output-new.jpg")))
        assertThrows(IllegalArgumentException::class.java) { BatchImagePolicy.requireExportable(stale, setOf(larger.copyKey!!)) }
    }
    @Test fun pendingItemsBlockPackagingAndFailedOrUncheckedRowsAreExplicitlyExcluded() {
        val ready = item()
        val pending = item().copy(id = UUID.randomUUID().toString(), source = "source-234.image", output = null, outputBytes = 0)
        val batch = BatchImages(items = listOf(ready, pending))
        assertThrows(IllegalArgumentException::class.java) { BatchImagePolicy.requireExportable(batch, emptySet()) }
        assertEquals(listOf(ready), BatchImagePolicy.requireExportable(batch.copy(items = listOf(ready, pending.copy(issue = ImportIssue.FILE_ACCESS))), emptySet()))
        assertThrows(IllegalArgumentException::class.java) { BatchImagePolicy.requireExportable(batch.copy(items = listOf(ready.copy(included = false))), emptySet()) }
    }
    @Test fun flatNamesAreBoundedUniqueAndCannotTraverseOrCollideOnCaseInsensitiveReaders() {
        val names = listOf("../../a.png", "..\\A.png", "a.png", "a.png", "\u0000:/CON.png", ".")
            .mapIndexed { index, name -> BatchImagePolicy.entryName(index, name, ImageOutputFormat.WEBP) }
        assertEquals(names.size, names.map { it.lowercase() }.distinct().size)
        names.forEach { assertFalse(it.contains('/')); assertFalse(it.contains('\\')); assertFalse(it.contains("..")); assertTrue(it.length < 100) }
        listOf("../output-escape.jpg", "output-evil/file.jpg", "output-evil\\file.jpg", "source-original.jpg").forEach {
            assertThrows(IllegalArgumentException::class.java) { BatchImagePolicy.owned(File("batch"), it, "output-") }
        }
    }
    @Test fun corruptForeignOrOversizedMetadataAndInvalidOptionsAreRejected() {
        val good = BatchImagePolicy.encode(BatchImages(items = listOf(item())))
        val mutations: List<(JSONObject) -> Unit> = listOf(
            { it.put("version", 2) }, { it.put("edge", 4096) }, { it.put("quality", 20) },
            { it.getJSONArray("items").getJSONObject(0).put("source", "../source-original.image") },
            { it.getJSONArray("items").getJSONObject(0).put("outputBytes", -1) },
            { it.getJSONArray("items").getJSONObject(0).put("width", 100000000) },
            { it.getJSONArray("items").put(it.getJSONArray("items").getJSONObject(0)) },
        )
        mutations.forEach { mutation -> assertThrows(Exception::class.java) {
            BatchImagePolicy.decode(JSONObject(good).also(mutation).toString()) } }
        assertThrows(Exception::class.java) { BatchImagePolicy.decode(" ".repeat(512 * 1024 + 1)) }
        assertThrows(IllegalArgumentException::class.java) { BatchOptions(quality = 96) }
    }
    @Test fun countSourceAndOutputCapsAreIndependentWithoutReducingSingleFileLimits() {
        fun many(count: Int, sourceBytes: Long, resultBytes: Long) = BatchImages(items = (0 until count).map { index ->
            item(sourceBytes, resultBytes).copy(source = "source-$index.image", output = "output-$index.jpg") })
        val full = many(20, 100, 50)
        assertEquals(20, BatchImagePolicy.decode(BatchImagePolicy.encode(full)).items.size)
        assertThrows(Exception::class.java) { BatchImagePolicy.decode(BatchImagePolicy.encode(many(21, 100, 50))) }
        val limit = FormatSupport.MAX_IMAGE_BYTES
        assertEquals(4, BatchImagePolicy.decode(BatchImagePolicy.encode(many(4, limit, limit))).items.size)
        assertThrows(Exception::class.java) { BatchImagePolicy.decode(BatchImagePolicy.encode(many(5, limit, 50))) }
        assertThrows(Exception::class.java) { BatchImagePolicy.decode(BatchImagePolicy.encode(many(5, 50, limit))) }
    }
}

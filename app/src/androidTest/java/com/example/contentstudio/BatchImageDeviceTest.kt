package com.example.contentstudio

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.contentstudio.batch.*
import com.example.contentstudio.media.*
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BatchImageDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun uri(name: String) = Uri.parse("content://com.example.contentstudio.test.fixtures/$name")
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun original(name: String) = context.contentResolver.openInputStream(uri(name))!!.use { it.readBytes() }
    private fun approved(batch: BatchImages) = batch.items.filter { it.needsConsent }.mapNotNull { it.copyKey }.toSet()
    private suspend fun assertRejected(work: suspend () -> Unit) {
        try { work(); fail("Expected rejection") } catch (_: IllegalArgumentException) { }
    }
    @Test fun allFourFormatsPackageExactResultsWithSafeDuplicateNamesAndUnchangedSources() = runBlocking(Dispatchers.IO) {
        val engine = BatchImageEngine(context); engine.clear()
        val before = listOf("noise.jpg", "tiny.png").associateWith { hash(original(it)) }
        val evidence = File(context.getExternalFilesDir(null), "qa/batch-images").apply { mkdirs() }
        val report = JSONArray()
        try {
            for ((index, format) in ImageOutputFormat.entries.withIndex()) {
                engine.clear()
                var batch = engine.add(listOf(uri("noise.jpg"), uri("tiny.png"), uri("tiny.png")))
                batch = engine.options(BatchOptions(format, if (index % 2 == 0) 40 else 95, listOf(1280, 1920, 2560)[index % 3]))
                engine.convert(batch.revision) { _, _ -> }
                batch = engine.read()
                assertTrue(batch.items.all { it.output != null && it.issue == null })
                if (batch.items.any { it.needsConsent }) assertRejected { engine.zip(batch.revision, emptySet()) }
                val archive = engine.zip(batch.revision, approved(batch)).resolve(engine.folder)
                val entries = JSONArray()
                ZipFile(archive).use { zip ->
                    assertEquals(3, zip.size())
                    val names = zip.entries().asSequence().map { it.name }.toList()
                    assertEquals(3, names.map { it.lowercase() }.distinct().size)
                    batch.items.forEachIndexed { ordinal, item ->
                        val name = BatchImagePolicy.entryName(ordinal, item.name, format)
                        val entry = zip.getEntry(name); assertNotNull(entry)
                        val bytes = zip.getInputStream(entry).use { it.readBytes() }
                        val actual = File(File(engine.folder, "assets"), item.output!!)
                        assertArrayEquals(actual.readBytes(), bytes); assertEquals(item.outputBytes, entry.size)
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                        assertEquals(format.mime, bounds.outMimeType)
                        assertEquals(if (ordinal == 0) minOf(2048, batch.options.edge) else 16, bounds.outWidth)
                        entries.put(JSONObject().put("name", name).put("sha256", hash(bytes)).put("width", bounds.outWidth).put("height", bounds.outHeight))
                    }
                }
                archive.copyTo(File(evidence, "${format.name.lowercase()}.zip"), overwrite = true)
                report.put(JSONObject().put("format", format.name).put("entries", entries))
            }
            File(evidence, "manifest.json").writeText(JSONObject().put("outputs", report).toString(2))
            before.forEach { (name, value) -> assertEquals(value, hash(original(name))) }
        } finally { engine.clear() }
    }
    @Test fun interruptedQueueRetainsFinishedResultAndRetriesOnlyRemainingImages() = runBlocking(Dispatchers.IO) {
        val engine = BatchImageEngine(context); engine.clear()
        try {
            val start = engine.add(listOf(uri("noise.jpg"), uri("tiny.png")))
            try { engine.convert(start.revision) { _, _ -> throw CancellationException("Synthetic interruption after first durable result") }; fail() }
            catch (_: CancellationException) { }
            val restored = BatchImageEngine(context).read()
            assertNotNull(restored.items[0].output); assertNull(restored.items[1].output)
            val first = File(File(engine.folder, "assets"), restored.items[0].output!!)
            val firstHash = hash(first.readBytes())
            engine.convert(restored.revision) { _, _ -> }
            val done = engine.read()
            assertEquals(restored.items[0], done.items[0]); assertEquals(firstHash, hash(first.readBytes()))
            assertTrue(done.items.all { it.output != null })
            assertFalse(engine.folder.listFiles()!!.any { it.extension == "part" })
        } finally { engine.clear() }
    }
    @Test fun atomicImportPerFileFailureExplicitSubsetAndMissingOutputRecovery() = runBlocking(Dispatchers.IO) {
        val engine = BatchImageEngine(context); engine.clear()
        try {
            var batch = engine.add(listOf(uri("noise.jpg"), uri("tiny.png")))
            val filesBefore = File(engine.folder, "assets").listFiles()!!.map { it.name }.toSet()
            try { engine.add(listOf(uri("noise.jpg"), uri("invalid.jpg"))); fail() } catch (_: Exception) { }
            assertEquals(batch, engine.read())
            assertEquals(filesBefore, File(engine.folder, "assets").listFiles()!!.map { it.name }.toSet())
            File(File(engine.folder, "assets"), batch.items[1].source).writeBytes(byteArrayOf(1))
            engine.convert(batch.revision) { _, _ -> }
            batch = engine.read()
            assertNotNull(batch.items[0].output); assertEquals(ImportIssue.FILE_ACCESS, batch.items[1].issue)
            val archive = engine.zip(batch.revision, approved(batch)).resolve(engine.folder)
            ZipFile(archive).use { assertEquals(1, it.size()) }
            batch = engine.remove(batch.items[1].id)
            File(File(engine.folder, "assets"), batch.items.single().output!!).delete()
            val recovered = BatchImageEngine(context).read()
            assertNull(recovered.items.single().output); assertEquals(ImportIssue.FILE_ACCESS, recovered.items.single().issue)
            engine.convert(recovered.revision) { _, _ -> }
            assertNotNull(engine.read().items.single().output)
        } finally { engine.clear() }
    }
    @Test fun staleOptionsOriginalUriAndUnapprovedCopiesCannotExportAndFailureKeepsArchive() = runBlocking(Dispatchers.IO) {
        val engine = BatchImageEngine(context); engine.clear()
        val sourceBytes = original("tiny.png")
        try {
            var batch = engine.add(listOf(uri("tiny.png")))
            batch = engine.options(BatchOptions(ImageOutputFormat.BMP))
            engine.convert(batch.revision) { _, _ -> }; batch = engine.read()
            val approvals = approved(batch); assertEquals(1, approvals.size)
            val archive = engine.zip(batch.revision, approvals)
            assertRejected { engine.export(archive, batch.revision, emptySet(), uri("export.jpg")) }
            assertRejected { engine.export(archive, batch.revision, approvals, uri("tiny.png")) }
            try { engine.export(archive, batch.revision, approvals, uri("fail.jpg")); fail() } catch (_: java.io.FileNotFoundException) { }
            assertTrue(archive.resolve(engine.folder).isFile)
            engine.export(archive, batch.revision, approvals, uri("export.jpg"))
            assertArrayEquals(archive.resolve(engine.folder).readBytes(), original("export.jpg"))
            engine.options(batch.options.copy(format = ImageOutputFormat.PNG))
            assertRejected { engine.export(archive, batch.revision, approvals, uri("export.jpg")) }
            assertArrayEquals(sourceBytes, original("tiny.png"))
        } finally { engine.clear() }
    }
    @Test fun cancelledSlowImportAndFileLimitDoNotChangeExistingWork() = runBlocking(Dispatchers.IO) {
        val engine = BatchImageEngine(context); engine.clear()
        try {
            val existing = engine.add(listOf(uri("tiny.png")))
            val task = launch { engine.add(listOf(uri("slow.jpg"))) }
            delay(200); task.cancelAndJoin()
            assertEquals(existing, engine.read())
            assertEquals(1, File(engine.folder, "assets").listFiles()!!.size)
            assertRejected { engine.add(List(20) { uri("tiny.png") }) }
            assertEquals(existing, engine.read())
            var batch = engine.add(listOf(uri("noise.jpg")))
            batch = engine.options(BatchOptions(ImageOutputFormat.BMP))
            engine.convert(batch.revision) { _, _ -> }; batch = engine.read()
            val progress = mutableListOf<Int>()
            try {
                engine.zip(batch.revision, approved(batch)) { percent ->
                    progress += percent
                    if (percent >= 1) throw CancellationException("Cancel after real ZIP bytes were written")
                }
                fail("ZIP should have been cancelled")
            } catch (_: CancellationException) { }
            assertTrue(progress.isNotEmpty())
            assertFalse(engine.folder.listFiles()!!.any { it.name.endsWith(".zip") || it.name.endsWith(".part") })
            assertEquals(batch, engine.read())
            ZipFile(engine.zip(batch.revision, approved(batch)).resolve(engine.folder)).use { assertEquals(2, it.size()) }
        } finally { engine.clear() }
    }
}

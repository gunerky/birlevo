package com.example.contentstudio

import android.net.Uri
import android.os.Process
import androidx.test.platform.app.InstrumentationRegistry
import com.example.contentstudio.archive.*
import com.example.contentstudio.ui.resumeWork
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ArchiveDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun uri(name: String) = Uri.parse("content://com.example.contentstudio.test.archives/$name")
    private val evidence get() = File(context.getExternalFilesDir(null), "qa/archive").apply { mkdirs() }
    private suspend fun rejected(action: suspend () -> Unit) {
        try { action(); fail("expected rejection") } catch (_: IllegalArgumentException) { }
    }
    @Test fun exactBytesAtomicReplacementCorruptionSourceAndStaleGuards() = runBlocking {
        val engine = ArchiveEngine(context); engine.clear()
        try {
            val source = context.contentResolver.openInputStream(uri("safe.zip"))!!.use { it.readBytes() }
            val session = engine.open(uri("safe.zip"))
            assertEquals(4, session.entries.size)
            assertEquals(session.revision, engine.read()!!.revision)
            for (entry in session.entries.filterNot { it.directory }) {
                engine.save(session.revision, entry.path, uri("export.bin"))
                val bytes = context.contentResolver.openInputStream(uri("export.bin"))!!.use { it.readBytes() }
                val expected = if (entry.path == "empty.bin") byteArrayOf() else "Exact original: ${entry.path}".toByteArray()
                assertArrayEquals(expected, bytes)
                File(evidence, when(entry.path) { "empty.bin" -> "empty.bin"; "日本.txt" -> "unicode.txt"; else -> "hello.txt" }).writeBytes(bytes)
            }
            rejected { engine.open(uri("unsafe.zip")) }
            rejected { engine.open(uri("broken.zip")) }
            assertEquals(session.revision, engine.read()!!.revision)
            rejected { engine.save(session.revision, "folder/hello.txt", uri("safe.zip")) }
            rejected { engine.save("stale", "folder/hello.txt", uri("export.bin")) }
            try { engine.save(session.revision, "folder/hello.txt", uri("fail.bin")); fail() } catch (_: Exception) { }
            assertArrayEquals(source, context.contentResolver.openInputStream(uri("safe.zip"))!!.use { it.readBytes() })
            assertEquals(session.revision, engine.read()!!.revision)
            assertFalse(engine.folder.listFiles()!!.any { it.name.endsWith(".part") })
            assertFalse(File(engine.folder.parentFile, "outside.txt").exists())
            assertEquals("safe.zip", resumeWork(context).single { it.kind == "zip-reader" }.name)
            File(evidence, "source.zip").writeBytes(source)
        } finally { engine.clear() }
        assertNull(engine.read())
    }
    @Test fun cancellationNeverPublishesPartialInputOrExtraction() = runBlocking {
        val engine = ArchiveEngine(context); engine.clear()
        val session = engine.open(uri("safe.zip"))
        val job = launch(Dispatchers.IO) { engine.open(uri("slow.zip")) }
        withTimeout(15_000) { while (engine.folder.listFiles()!!.none { it.name != session.file && it.name.endsWith(".zip") && it.length() > 0 }) delay(5) }
        job.cancelAndJoin()
        assertEquals(session.revision, engine.read()!!.revision)
        assertEquals(1, engine.folder.listFiles()!!.count { it.name.endsWith(".zip") })
        val file = File(engine.folder, session.file)
        val staged = File(evidence, "cancel.part")
        try {
            staged.outputStream().use { out ->
                var checks = 0
                try { ZipArchive.extract(file, session.entries.first { !it.directory }, out) {
                    if (++checks > 3) throw CancellationException("synthetic cancellation")
                }; fail() } catch (_: CancellationException) { }
            }
        } finally { staged.delete(); engine.clear() }
        assertNull(engine.read())
    }
    @Test fun prepareProcessRestore() = runBlocking {
        val engine = ArchiveEngine(context); engine.clear()
        val session = engine.open(uri("safe.zip"))
        File(evidence, "process.json").writeText(JSONObject().put("pid", Process.myPid()).put("revision", session.revision).toString())
    }
    @Test fun verifyProcessRestore() = runBlocking {
        val marker = JSONObject(File(evidence, "process.json").readText())
        assertNotEquals(marker.getInt("pid"), Process.myPid())
        val engine = ArchiveEngine(context)
        try {
            val session = engine.read()!!
            assertEquals(marker.getString("revision"), session.revision)
            engine.save(session.revision, "folder/hello.txt", uri("export.bin"))
            assertArrayEquals("Exact original: folder/hello.txt".toByteArray(), context.contentResolver.openInputStream(uri("export.bin"))!!.use { it.readBytes() })
        } finally { engine.clear() }
    }
    @Test fun readsExistingBatchZipWithoutReencoding() = runBlocking {
        val engine = com.example.contentstudio.batch.BatchEngine(context)
        engine.clear()
        try {
            var batch = engine.add(listOf(Uri.parse("content://com.example.contentstudio.test.fixtures/tiny.png")))
            engine.convert(batch.revision) { queue, _ -> batch = queue }
            val output = engine.zip(batch.revision, batch.items.filter { it.needsConsent }.mapNotNull { it.copyKey }.toSet()).resolve(engine.folder)
            val entries = ZipArchive.inspect(output)
            assertEquals(1, entries.size)
            val extracted = java.io.ByteArrayOutputStream()
            ZipArchive.extract(output, entries.single(), extracted)
            ZipFile(output).use { zip -> assertArrayEquals(zip.getInputStream(zip.entries().nextElement()).use { it.readBytes() }, extracted.toByteArray()) }
        } finally { engine.clear() }
    }
}

package com.example.contentstudio

import android.net.Uri
import android.os.Debug
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.example.contentstudio.media.*
import com.example.contentstudio.storage.*
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

/** Inputs are generated on the host and pushed only into the dedicated QA app's files. */
class ExternalPdfResourceDeviceTest {
    @get:org.junit.Rule val residentAdvertising = ResidentAdvertisingRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val phase get() = InstrumentationRegistry.getArguments().getString("profilePhase", "final")!!
    private val evidence get() = File(context.getExternalFilesDir(null), "qa/external-pdf").apply { mkdirs() }
    private val fixtures get() = File(context.getExternalFilesDir(null), "qa/external-pdf-input")
    private val folder get() = File(context.noBackupFilesDir, "pdf-pages").apply { mkdirs() }
    private fun sources() = listOf("heavy.pdf" to 28, "second.pdf" to 1).map { (name, pages) ->
        val file = File(fixtures, name)
        PdfSource(file, Uri.fromFile(file), name, file.length(), pages)
    }
    private fun output() = File.createTempFile("output-", ".pdf", folder)
    private fun storage(pressured: () -> Boolean) = StreamingStorage {
        StorageSnapshot(4L*1024*1024*1024, if (pressured()) 0 else 3L*1024*1024*1024)
    }
    private suspend fun waitForWork() = withTimeout(10_000) {
        while (folder.listFiles().orEmpty().none { it.name.startsWith("scratch-work-") && it.length() >= 1024*1024 }) delay(5)
    }

    private fun digest(file: File): String {
        val hash = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(65536)
            while (true) { val count = input.read(buffer); if (count < 0) break; hash.update(buffer, 0, count) }
        }
        return hash.digest().joinToString("") { "%02x".format(it) }
    }

    @Test fun descriptorScratchCopiesEncodedImageResources() = runBlocking<Unit>(Dispatchers.IO) {
        val input = File(fixtures, "second.pdf")
        val scratch = File.createTempFile("scratch-test-", ".bin", context.cacheDir)
        val output = File.createTempFile("scratch-result-", ".pdf", context.cacheDir)
        try {
            com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context)
            ParcelFileDescriptor.open(input, ParcelFileDescriptor.MODE_READ_ONLY).use { source ->
                ParcelFileDescriptor.open(scratch, ParcelFileDescriptor.MODE_READ_WRITE).use { work ->
                    PdfScratchArena(PdfDescriptorStore(work, descriptorStorage(work.fileDescriptor))).use { arena ->
                        output.outputStream().use { target ->
                            assertEquals(1, PdfPageEngine.create(listOf(PdfDescriptorStore(source)),
                                listOf(PdfPageRef("one", 0, 0)), target, arena))
                        }
                    }
                }
            }
            assertTrue(output.length() > 1000)
        } finally { scratch.delete(); output.delete() }
    }

    @Test fun descriptorModesAndLiveScratchDiskPressureAreEnforced() {
        val source = sources()[1].file
        val original = digest(source)
        ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            val store = PdfDescriptorStore(descriptor, storage { false })
            try { store.write(0, byteArrayOf(1), 0, 1); fail("Read-only descriptor accepted a write") }
            catch (_: java.io.IOException) { }
        }
        assertEquals(original, digest(source))
        val file = File.createTempFile("scratch-pressure-", ".bin", context.cacheDir)
        try {
            val pressure = AtomicBoolean(false)
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE).use { descriptor ->
                PdfScratchArena(PdfDescriptorStore(descriptor, storage(pressure::get))).use { arena ->
                    arena.scope().use { scope ->
                        scope.createBuffer().write(ByteArray(65536))
                        pressure.set(true)
                        try { scope.createBuffer().write(1); fail("Scratch write pressure ignored") }
                        catch (_: StorageCapacityException) { }
                    }
                }
            }
        } finally { file.delete() }
    }

    @Test fun mergeAndExtractKeepOnlySelectedEncodedResources() = runBlocking<Unit>(Dispatchers.IO) {
        val sources = sources()
        val originals = sources.map { digest(it.file) }
        val merged = output()
        val extracted = output()
        val ignored = File.createTempFile("unselected-", ".pdf", context.cacheDir).apply { writeText("Not a PDF") }
        try {
            val plan = (0 until 28).map { PdfPageRef("main-$it", 0, it) }.toMutableList()
            plan.add(14, PdfPageRef("second", 1, 0, 180))
            assertEquals(29, PdfPageClient(context).create(sources, plan, merged))
            merged.copyTo(File(evidence, "merged.pdf"), overwrite = true)
            val selected = listOf(27, 13, 0).map { PdfPageRef("$it", 0, it, 270) }
            val unselected = PdfSource(ignored, Uri.fromFile(ignored), "ignored.pdf", ignored.length(), 1)
            assertEquals(3, PdfPageClient(context).create(listOf(sources[0], unselected), selected, extracted))
            assertTrue("Unselected pages must not be copied", extracted.length() < sources[0].bytes/4)
            extracted.copyTo(File(evidence, "extracted.pdf"), overwrite = true)
            assertEquals(originals, sources.map { digest(it.file) })
        } finally { merged.delete(); extracted.delete(); ignored.delete() }
    }

    @Test fun largeImportAnd4096PageExportsPreserveSourcesAndPixels() = runBlocking<Unit>(Dispatchers.IO) {
        val source = sources()[0]
        val original = digest(source.file)
        val reader = PdfReader(context)
        val imported = reader.importPdf(source.originalUri)
        try {
            assertEquals(28, imported.source.pages)
            assertEquals(1280, imported.preview.width)
            val formats = listOf(ImageOutputFormat.PNG, ImageOutputFormat.JPEG, ImageOutputFormat.WEBP)
            for ((index, format) in formats.withIndex()) {
                val image = reader.pageImage(imported.source, index*13, 4096, format, 80)
                try { image.file.copyTo(File(evidence, "page-${index*13}.${format.extension}"), overwrite = true) }
                finally { image.file.delete() }
            }
            assertEquals(original, digest(imported.source.file))
            assertEquals(original, digest(source.file))
        } finally { imported.preview.recycle(); imported.source.file.delete() }
    }

    @Test fun lowDiskDuringImportKeepsPriorSourceAndRemovesPartialCopy() = runBlocking<Unit>(Dispatchers.IO) {
        val existing = PdfReader(context).importPdf(sources()[1].originalUri)
        val readerFolder = existing.source.file.parentFile!!
        val before = readerFolder.listFiles()!!.toSet()
        val previous = digest(existing.source.file)
        val original = digest(sources()[0].file)
        val limited = PdfReader(context, capacity = { storage {
            readerFolder.listFiles().orEmpty().any { it !in before && it.name.startsWith("source-") && it.length() > 0 }
        } })
        try {
            try { limited.importPdf(sources()[0].originalUri); fail("Import pressure ignored") }
            catch (error: PdfReadException) { assertEquals(PdfReadIssue.STORAGE, error.issue) }
            assertEquals(before, readerFolder.listFiles()!!.toSet())
            assertEquals(previous, digest(existing.source.file))
            assertEquals(original, digest(sources()[0].file))
        } finally { existing.preview.recycle(); existing.source.file.delete() }
    }

    @Test fun cancellationAndDiskPressureCleanOnlyTheirOwnScratchAndSupportRetry() = runBlocking<Unit>(Dispatchers.IO) {
        val sources = sources()
        val original = sources.map { digest(it.file) }
        val previous = output()
        val small = listOf(PdfPageRef("second", 1, 0))
        PdfPageClient(context).create(sources, small, previous)
        val saved = digest(previous)
        val before = folder.listFiles()!!.toSet()
        val plan = (0 until 28).map { PdfPageRef("$it", 0, it) }
        try {
            val cancelled = output()
            val job = async { PdfPageClient(context).create(sources, plan, cancelled) }
            waitForWork(); job.cancelAndJoin()
            assertFalse(cancelled.exists())
            assertEquals(before, folder.listFiles()!!.toSet())
            val pressured = output()
            val client = PdfPageClient(context, capacity = { storage {
                folder.listFiles().orEmpty().any { it.name.startsWith("scratch-work-") && it.length() > 0 }
            } })
            try { client.create(sources, plan, pressured); fail("Worker disk pressure ignored") }
            catch (error: PdfPageException) { assertEquals(PdfPageIssue.STORAGE, error.issue) }
            assertFalse(pressured.exists())
            assertEquals(before, folder.listFiles()!!.toSet())
            assertEquals(saved, digest(previous))
            assertEquals(original, sources.map { digest(it.file) })
            val retry = output()
            try { assertEquals(1, PdfPageClient(context).create(sources, small, retry)) }
            finally { retry.delete() }
        } finally { previous.delete() }
    }

    @Test fun isolatedWorkerCrashReturnsProcessFailureAndAllowsRetry() = runBlocking<Unit>(Dispatchers.IO) {
        val sources = sources()
        val original = sources.map { digest(it.file) }
        val before = folder.listFiles()!!.toSet()
        val target = output()
        try {
            supervisorScope {
                // Arm the host before binding: fast legacy jobs can finish between later polls.
                val request = File(folder, "worker-stop-request.txt")
                try {
                    request.writeText("ready")
                    val work = async { PdfPageClient(context).create(sources, (0 until 28).map { PdfPageRef("$it", 0, it) }, target) }
                    try { withTimeout(15_000) { work.await() }; fail("Worker death ignored") }
                    catch (error: PdfPageException) { assertEquals(PdfPageIssue.PROCESS, error.issue) }
                } finally { request.delete() }
            }
            assertEquals(before, folder.listFiles()!!.toSet())
            assertFalse(target.exists())
            assertEquals(original, sources.map { digest(it.file) })
            val retry = output()
            try { assertEquals(1, PdfPageClient(context).create(sources, listOf(PdfPageRef("retry", 1, 0)), retry)) }
            finally { retry.delete() }
        } finally { target.delete() }
    }

    @Test fun profileImageHeavyPageWorker() = runBlocking<Unit>(Dispatchers.IO) {
        val sourceFile = File(fixtures, "heavy.pdf")
        assertTrue(sourceFile.length() in 120L*1024*1024 until PdfPagePlan.MAX_BYTES)
        val original = digest(sourceFile)
        val source = PdfSource(sourceFile, Uri.fromFile(sourceFile), "heavy.pdf", sourceFile.length(), 28)
        val pages = (27 downTo 0).map { PdfPageRef("$it", 0, it, if (it % 2 == 0) 90 else 0) }
        val folder = File(context.noBackupFilesDir, "pdf-pages").apply { mkdirs() }
        val output = File.createTempFile("output-", ".pdf", folder)
        val samples = StringBuilder("elapsedMs,javaBytes,nativeBytes,pssKiB\n")
        val started = SystemClock.elapsedRealtime()
        val sampler = launch {
            while (isActive) {
                val runtime = Runtime.getRuntime()
                val info = Debug.MemoryInfo().also(Debug::getMemoryInfo)
                samples.append("${SystemClock.elapsedRealtime()-started},${runtime.totalMemory()-runtime.freeMemory()},${Debug.getNativeHeapAllocatedSize()},${info.totalPss}\n")
                delay(100)
            }
        }
        try {
            var issue: PdfPageIssue? = null
            try { assertEquals(28, PdfPageClient(context).create(listOf(source), pages, output)) }
            catch (error: PdfPageException) { issue = error.issue }
            File(evidence, "worker-$phase.txt").writeText("issue=$issue\nelapsedMs=${SystemClock.elapsedRealtime()-started}\nsourceBytes=${sourceFile.length()}\noutputBytes=${output.length()}\n")
            if (phase == "baseline") assertTrue("Expected the existing 64 MiB buffer failure", issue in listOf(PdfPageIssue.INVALID, PdfPageIssue.MEMORY))
            else {
                assertNull("Worker failed: $issue", issue)
                output.copyTo(File(evidence, "reordered.pdf"), overwrite = true)
            }
            assertEquals(original, digest(sourceFile))
        } finally {
            sampler.cancelAndJoin()
            File(evidence, "parent-$phase.csv").writeText(samples.toString())
            output.delete()
        }
    }
}

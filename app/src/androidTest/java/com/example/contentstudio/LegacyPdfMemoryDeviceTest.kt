package com.example.contentstudio

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Debug
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.example.contentstudio.media.*
import com.example.contentstudio.storage.*
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.*
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

/** API 24-compatible engine tests; no LocaleManager or API 33 UI setup. */
class LegacyPdfMemoryDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val evidence get() = File(context.getExternalFilesDir(null), "qa/legacy-pdf").apply { mkdirs() }
    private fun fixture(name: String) = Uri.parse("content://com.example.contentstudio.test.fixtures/$name")

    @Test fun slideDecodeKeepsRequestedPixelsAndExifOrientation() {
        for (edge in listOf(2048, 2560)) {
            val image = SlideImages.decode(context, fixture("large-square.png"), edge)
            try {
                assertEquals(edge, image.width); assertEquals(edge, image.height)
                assertTrue(Color.red(image.getPixel(100, 100)) > 240)
                assertTrue(Color.blue(image.getPixel(edge - 100, 100)) > 240)
            } finally { image.recycle() }
        }
        val rotated = SlideImages.decode(context, fixture("orientation-6.jpg"), 2048)
        try {
            assertEquals(80, rotated.width); assertEquals(120, rotated.height)
            assertTrue(Color.blue(rotated.getPixel(15, 15)) > Color.red(rotated.getPixel(15, 15)))
        } finally { rotated.recycle() }
    }

    @Test fun modernImageSupportUsesOnlyAvailablePlatformApis() {
        val sdk = android.os.Build.VERSION.SDK_INT
        for ((name, minimum) in listOf("modern.heic" to 28, "modern-irot.heic" to 28, "modern.avif" to 31)) {
            if (sdk < minimum) {
                try { SlideImages.validate(context, fixture(name)); fail("Unavailable format accepted") }
                catch (error: MediaImportException) { assertEquals(ImportIssue.IMAGE_DEVICE, error.issue) }
            } else {
                val bitmap = SlideImages.decode(context, fixture(name), 2048)
                try {
                    val rotated = name.contains("irot")
                    assertEquals(if (rotated) 80 else 120, bitmap.width)
                    assertEquals(if (rotated) 120 else 80, bitmap.height)
                    val corner = bitmap.getPixel(10, 10)
                    if (rotated) assertTrue(Color.blue(corner) > 180 && Color.red(corner) < 70)
                    else assertTrue(Color.red(corner) > 180 && Color.blue(corner) < 70)
                } finally { bitmap.recycle() }
            }
        }
    }

    @Test fun profileMultipagePhotoPdf() = runBlocking<Unit> {
        val phase = InstrumentationRegistry.getArguments().getString("profilePhase", "final")!!
        val count = InstrumentationRegistry.getArguments().getString("profilePages", "24")!!.toInt()
        val highResolution = InstrumentationRegistry.getArguments().getString("highResolution", "false")!!.toBoolean()
        val width = if (highResolution) 5000 else 1536
        val height = if (highResolution) 2500 else 1024
        val edge = if (highResolution) 4096 else 2048
        require(count in 1..100)
        val sources = mutableListOf<PhotoPage>()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            val paint = Paint()
            repeat(count) { index ->
                canvas.drawColor(Color.rgb(20 + index, 80, 180))
                paint.color = Color.YELLOW
                canvas.drawRect(20f, 20f, (80 + index * 5).toFloat(), 180f, paint)
                val file = File.createTempFile("profile-source-", ".png", context.cacheDir)
                file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                sources += PhotoPage("$index", file, Uri.fromFile(file), file.name, file.length())
            }
        } finally { bitmap.recycle() }
        val samples = StringBuilder("page,elapsedMs,javaBytes,nativeBytes,pssKiB\n")
        var completed = 0
        val nativeAtStart = Debug.getNativeHeapAllocatedSize()
        var retainedNative = nativeAtStart
        val started = SystemClock.elapsedRealtime()
        fun sample(page: Int) {
            val runtime = Runtime.getRuntime()
            val info = Debug.MemoryInfo().also(Debug::getMemoryInfo)
            val native = Debug.getNativeHeapAllocatedSize()
            retainedNative = maxOf(retainedNative, native)
            samples.append("$page,${SystemClock.elapsedRealtime()-started},${runtime.totalMemory()-runtime.freeMemory()},$native,${info.totalPss}\n")
        }
        var result: PresentationPdfResult? = null
        try {
            sample(0)
            result = PhotoPdf(context).create(sources, edge) { sample(++completed) }
            sample(count + 1)
            if (count >= 24) assertTrue("Completed pages retained ${retainedNative-nativeAtStart} native bytes",
                retainedNative - nativeAtStart < 64L * 1024 * 1024)
            // Retain the file before validation so failures can be independently diagnosed.
            result.file.copyTo(File(evidence, "photo-$phase-$count.pdf"), overwrite = true)
            File(evidence, "photo-$phase-$count.json").writeText(
                """{"sourceWidth":$width,"sourceHeight":$height,"edge":$edge,"imageWidth":${minOf(width, edge)},"imageHeight":${if(highResolution) 2048 else 1024}}""")
            PdfRenderer(ParcelFileDescriptor.open(result.file, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
                assertEquals(count, renderer.pageCount)
                for (index in listOf(0, count / 2, count - 1).distinct()) {
                    renderer.openPage(index).use { page ->
                        assertEquals(842, page.width); assertEquals(595, page.height)
                        val preview = Bitmap.createBitmap(842, 595, Bitmap.Config.ARGB_8888)
                        try {
                            page.render(preview, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            // Platform PDF display rasterization can quantize a flat color (API 36
                            // measured a four-level red difference). Host image decoding stays exact.
                            val color = preview.getPixel(400, 300)
                            assertTrue(kotlin.math.abs(Color.red(color) - (20 + index)) <= 6)
                            assertTrue(kotlin.math.abs(Color.green(color) - 80) <= 6)
                            assertTrue(kotlin.math.abs(Color.blue(color) - 180) <= 6)
                        } finally { preview.recycle() }
                    }
                }
            }
        } finally {
            File(evidence, "photo-$phase-$count.csv").writeText(samples.toString())
            result?.file?.delete(); sources.forEach { it.file.delete() }
        }
    }

    @Test fun photoPdfRetains4096PixelsAndCleansWorkOnCancellationAndDiskFailure() = runBlocking<Unit> {
        val engine = PhotoPdf(context)
        val pages = engine.importPages(listOf(fixture("large-landscape.png"), fixture("alpha.webp")), 100, PhotoPdf.MAX_BATCH_BYTES) {}
        val result = engine.create(pages, 4096) {}
        val expected = result.file.readBytes()
        val folder = result.file.parentFile!!
        val before = folder.listFiles()!!.map { it.name }.toSet()
        try {
            PDFBoxResourceLoader.init(context)
            PDDocument.load(result.file).use { document ->
                val resources = document.getPage(0).resources
                val image = resources.xObjectNames.map { resources.getXObject(it) }.filterIsInstance<PDImageXObject>().single()
                assertEquals(4096, image.width); assertEquals(2048, image.height)
            }
            result.file.copyTo(File(evidence, "photo-4096.pdf"), overwrite = true)
            try {
                engine.create(pages, 4096) { throw CancellationException("User cancelled") }
                fail("Cancellation must propagate")
            } catch (_: CancellationException) { }
            assertEquals(before, folder.listFiles()!!.map { it.name }.toSet())
            // First write succeeds; pressure appears after a completed page, while scratch exists.
            var pressure = false
            val limited = PhotoPdf(context, capacity = { StreamingStorage {
                StorageSnapshot(4L * 1024 * 1024 * 1024, if (pressure) 0 else 3L * 1024 * 1024 * 1024)
            } })
            try {
                limited.create(pages, 4096) { pressure = true }
                fail("Disk pressure must stop generation")
            } catch (_: StorageCapacityException) { }
            assertEquals(before, folder.listFiles()!!.map { it.name }.toSet())
            assertArrayEquals(expected, result.file.readBytes())
            assertTrue(pages.all { it.file.isFile })
            val retry = engine.create(pages, 2048) {}
            try { assertEquals(2, retry.pages) } finally { retry.file.delete() }
        } finally { result.file.delete(); pages.forEach { it.file.delete() } }
    }

    @Test fun isolatedPageWorkerHandles2000VectorPagesWithoutRasterizing() = runBlocking<Unit> {
        PDFBoxResourceLoader.init(context)
        val source = File.createTempFile("vector-source-", ".pdf", context.cacheDir)
        val target = File.createTempFile("vector-output-", ".pdf", context.cacheDir)
        try {
            PDDocument().use { document ->
                repeat(PdfPagePlan.MAX_PAGES) { index ->
                    val page = PDPage()
                    document.addPage(page)
                    PDPageContentStream(document, page).use { content ->
                        content.beginText(); content.setFont(PDType1Font.HELVETICA, 12f)
                        content.newLineAtOffset(30f, 600f); content.showText("Vector page ${index + 1}"); content.endText()
                    }
                }
                document.save(source)
            }
            val sourceBytes = source.readBytes()
            val plan = (0 until PdfPagePlan.MAX_PAGES).reversed().map { PdfPageRef("$it", 0, it, 90) }
            val started = SystemClock.elapsedRealtime()
            assertEquals(2000, withContext(Dispatchers.IO) { PdfPageClient(context).create(
                listOf(PdfSource(source, Uri.fromFile(source), "vector.pdf", source.length(), 2000)), plan, target) })
            File(evidence, "vector-timing.txt").writeText("elapsedMs=${SystemClock.elapsedRealtime()-started}\n")
            PDDocument.load(target).use { document ->
                assertEquals(2000, document.numberOfPages)
                for ((outputPage, original) in listOf(0 to 2000, 999 to 1001, 1999 to 1)) {
                    val text = PDFTextStripper().apply { startPage = outputPage + 1; endPage = outputPage + 1 }.getText(document)
                    assertEquals("Vectorpage$original", text.filterNot(Char::isWhitespace))
                    val page = document.getPage(outputPage)
                    assertEquals(90, page.rotation)
                    assertFalse(page.resources.xObjectNames.iterator().hasNext())
                }
            }
            assertArrayEquals(sourceBytes, source.readBytes())
            target.copyTo(File(evidence, "vector-2000.pdf"), overwrite = true)
        } finally { source.delete(); target.delete() }
    }
}

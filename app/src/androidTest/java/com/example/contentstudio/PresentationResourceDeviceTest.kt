package com.example.contentstudio

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Debug
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.example.contentstudio.data.*
import com.example.contentstudio.media.*
import com.example.contentstudio.storage.*
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

/** Retained-memory samples are not exhaustive allocation peaks or physical-device benchmarks. */
class PresentationResourceDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val args get() = InstrumentationRegistry.getArguments()
    private val phase get() = args.getString("profilePhase", "final")!!
    private val count get() = args.getString("profilePages", "24")!!.toInt().also { require(it in 1..100) }
    private val evidence get() = File(context.getExternalFilesDir(null), "qa/presentation-resources").apply { mkdirs() }

    private fun project(sources: MutableList<File>, pages: Int = count): StudioProject {
        val bitmap = Bitmap.createBitmap(1536, 1024, Bitmap.Config.ARGB_8888)
        val slides = try {
            val canvas = Canvas(bitmap)
            val marker = Paint().apply { color = Color.YELLOW }
            List(pages) { index ->
                canvas.drawColor(Color.rgb(20 + index, 80, 180))
                canvas.drawRect(20f, 20f, (80 + index * 5).toFloat(), 180f, marker)
                val file = File.createTempFile("presentation-source-", ".png", context.cacheDir)
                sources += file
                file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                Slide("Slide ${index + 1}", "Vector body ${index + 1}", Uri.fromFile(file).toString(),
                    id = "slide-$index", layout = SlideLayout.IMAGE_LEFT)
            }
        } finally { bitmap.recycle() }
        return StudioProject("resource-profile", ProjectKind.PRESENTATION, "Resource profile", 0,
            slides, theme = PresentationTheme.OCEAN)
    }

    private fun digest(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).toList()

    private class Samples {
        val csv = StringBuilder("step,elapsedMs,javaBytes,nativeBytes,pssKiB\n")
        private val started = SystemClock.elapsedRealtime()
        private var sampled = false
        var initialNative = 0L
        var largestNative = 0L
        @Synchronized fun sample(step: Int) {
            val runtime = Runtime.getRuntime()
            val native = Debug.getNativeHeapAllocatedSize()
            if (!sampled) { initialNative = native; sampled = true }
            largestNative = maxOf(largestNative, native)
            val info = Debug.MemoryInfo().also(Debug::getMemoryInfo)
            csv.append("$step,${SystemClock.elapsedRealtime()-started},${runtime.totalMemory()-runtime.freeMemory()},$native,${info.totalPss}\n")
        }
    }

    @Test fun profileMultipagePresentationPdf() = runBlocking<Unit>(Dispatchers.IO) {
        val sources = mutableListOf<File>()
        var result: PresentationPdfResult? = null
        val samples = Samples()
        try {
            val project = project(sources)
            val original = sources.map(::digest)
            System.gc(); System.runFinalization(); delay(200)
            samples.sample(0)
            var page = 0
            result = PresentationPdf(context).create(project) { samples.sample(++page) }
            samples.sample(count + 1)
            assertEquals(count, result.pages)
            result.file.copyTo(File(evidence, "presentation-$phase-$count.pdf"), overwrite = true)
            assertEquals(original, sources.map(::digest))
            if (phase != "baseline" && count >= 24) assertTrue(
                "Native completed-page retention grew by ${samples.largestNative-samples.initialNative}",
                samples.largestNative-samples.initialNative < 48L * 1024 * 1024)
        } finally {
            File(evidence, "presentation-$phase-$count.csv").writeText(samples.csv.toString())
            result?.file?.delete(); sources.forEach { it.delete() }
        }
    }

    @Test fun profilePresentationSlideshow() = runBlocking<Unit>(Dispatchers.IO) {
        val sources = mutableListOf<File>()
        val engine = SlideshowEngine(context)
        var timeline: SlideshowTimeline? = null
        var result: CompressedVideo? = null
        val samples = Samples()
        try {
            val project = project(sources)
            val original = sources.map(::digest)
            timeline = engine.importPresentation(project) {}.let { it.copy(edge = 640,
                frames = it.frames.map { frame -> frame.copy(durationMs = 500) }) }
            System.gc(); System.runFinalization(); delay(200)
            samples.sample(0)
            result = engine.create(timeline) { progress -> if (progress != null) samples.sample(progress) }
            samples.sample(101)
            assertEquals(640, result.info.width)
            assertEquals(360, result.info.height)
            assertTrue(kotlin.math.abs(count * 500L - result.info.durationMs) <= 100)
            result.file.copyTo(File(evidence, "slideshow-$phase-$count.mp4"), overwrite = true)
            assertEquals(original, sources.map(::digest))
            assertTrue(engine.folder.listFiles()!!.none { it.name.startsWith("render-") || it.name.startsWith("working-") })
        } finally {
            File(evidence, "slideshow-$phase-$count.csv").writeText(samples.csv.toString())
            result?.file?.delete(); result?.info?.poster?.recycle()
            timeline?.let { engine.files(it).forEach { file -> file.delete() } }
            sources.forEach { it.delete() }
        }
    }

    private fun storage(pressured: () -> Boolean = { false }) = StreamingStorage {
        StorageSnapshot(4L * 1024 * 1024 * 1024, if (pressured()) 0 else 3L * 1024 * 1024 * 1024)
    }

    private val noMemory get() = WorkingMemory { MemorySnapshot(16L * 1024 * 1024, 16L * 1024 * 1024) }

    /** Test-only legacy native multipage composition, keeping the production painter unchanged. */
    private fun nativeReference(engine: PresentationPdf, project: StudioProject): PresentationPdfResult {
        val file = File.createTempFile("native-reference-", ".pdf", context.cacheDir)
        val painter = PresentationPdf::class.java.getDeclaredMethod("drawSlide", Canvas::class.java,
            StudioProject::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType).apply { isAccessible = true }
        val native = PdfDocument()
        try {
            project.slides.indices.forEach { index ->
                val page = native.startPage(PdfDocument.PageInfo.Builder(960, 540, index + 1).create())
                try { painter.invoke(engine, page.canvas, project, index, 1024, true) }
                finally { native.finishPage(page) }
            }
            file.outputStream().use(native::writeTo)
            return PresentationPdfResult(file, project.slides.size, file.length())
        } catch (failure: Throwable) { file.delete(); throw failure }
        finally { native.close() }
    }

    @Test fun composedPagesMatchNativePreviewForEveryLayoutThemeAndAlpha() = runBlocking<Unit>(Dispatchers.IO) {
        val source = File.createTempFile("presentation-alpha-", ".png", context.cacheDir)
        val bitmap = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.TRANSPARENT)
            Canvas(bitmap).drawRect(30f, 20f, 270f, 180f, Paint().apply { color = 0x80ff4020.toInt() })
            source.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { bitmap.recycle() }
        try {
            for (theme in PresentationTheme.entries) {
                val project = StudioProject("appearance", ProjectKind.PRESENTATION, "العربية — Türkçe", 0,
                    SlideLayout.entries.map { layout -> Slide("Hello — مرحبًا", "Türkçe ı İ ğ ş — 日本語",
                        Uri.fromFile(source).toString(), layout = layout) } +
                        Slide("Text only", "Font sharing and no image"), theme = theme)
                val engine = PresentationPdf(context)
                val result = engine.create(project) {}
                val reference = nativeReference(engine, project)
                try {
                    for (index in project.slides.indices) {
                        val native = engine.render(reference, index)
                        try {
                            val composed = engine.render(result, index)
                            try {
                                if (!native.sameAs(composed)) {
                                    for ((name, image) in listOf("native" to native, "composed" to composed))
                                        File(evidence, "appearance-$theme-$index-$name.png").outputStream().use {
                                            image.compress(Bitmap.CompressFormat.PNG, 100, it)
                                        }
                                    result.file.copyTo(File(evidence, "appearance-$theme.pdf"), overwrite = true)
                                }
                                assertTrue("Appearance changed: $theme / $index", native.sameAs(composed))
                            }
                            finally { composed.recycle() }
                        } finally { native.recycle() }
                    }
                } finally { result.file.delete(); reference.file.delete() }
            }
        } finally { source.delete() }
    }

    @Test fun pdfCancellationAndDiskPressureKeepExistingResultAndVectorText() = runBlocking<Unit>(Dispatchers.IO) {
        val sources = mutableListOf<File>()
        val engine = PresentationPdf(context)
        var result: PresentationPdfResult? = null
        try {
            val project = project(sources, 3)
            val sourceBytes = sources.map(::digest)
            result = engine.create(project) {}
            val saved = digest(result.file)
            val folder = result.file.parentFile!!
            val before = folder.listFiles()!!.toSet()
            PDFBoxResourceLoader.init(context)
            PDDocument.load(result.file).use { document ->
                assertEquals(3, document.numberOfPages)
                repeat(3) { page ->
                    val text = PDFTextStripper().apply { startPage = page + 1; endPage = page + 1 }.getText(document)
                    assertTrue(text.replace(" ", "").contains("Slide${page + 1}"))
                    assertTrue(document.getPage(page).resources.fontNames.iterator().hasNext())
                }
            }
            try { engine.create(project) { throw CancellationException("QA cancel") }; fail("Cancellation ignored") }
            catch (_: CancellationException) { }
            assertEquals(before, folder.listFiles()!!.toSet())
            for (failAfter in listOf(1, 3)) {
                var completed = 0
                val limited = PresentationPdf(context, capacity = { storage { completed >= failAfter } })
                try { limited.create(project) { completed++ }; fail("Disk pressure ignored") }
                catch (_: StorageCapacityException) { }
                assertEquals(before, folder.listFiles()!!.toSet())
            }
            assertEquals(saved, digest(result.file))
            assertEquals(sourceBytes, sources.map(::digest))
            val retry = engine.create(project) {}
            try { assertEquals(3, retry.pages) } finally { retry.file.delete() }
        } finally { result?.file?.delete(); sources.forEach { it.delete() } }
    }

    @Test fun memoryAdmissionKeepsRequestedSizeAndDeliveredPreviews() = runBlocking<Unit>(Dispatchers.IO) {
        val sources = mutableListOf<File>()
        val engine = SlideshowEngine(context)
        var value: SlideshowTimeline? = null
        var preview: Bitmap? = null
        try {
            val project = project(sources, 2)
            preview = PresentationPdf(context).renderSlideBitmap(project, 0, 1920, 1080)
            assertEquals(1920, preview.width); assertEquals(1080, preview.height)
            try {
                PresentationPdf(context, memory = noMemory).renderSlideBitmap(project, 0, 1920, 1080)
                fail("Memory admission ignored")
            } catch (_: WorkingMemoryException) { }
            assertFalse(preview.isRecycled)
            value = engine.importPresentation(project) {}
            val before = engine.folder.listFiles()!!.toSet()
            try {
                SlideshowEngine(context, memory = noMemory).create(value.copy(edge = 1920)) {}
                fail("Memory admission ignored")
            } catch (_: WorkingMemoryException) { }
            assertEquals(before, engine.folder.listFiles()!!.toSet())
            assertTrue(engine.files(value).all { it.isFile })
            assertFalse(preview.isRecycled)
        } finally {
            preview?.recycle(); value?.let { engine.files(it).forEach { file -> file.delete() } }
            sources.forEach { it.delete() }
        }
    }

    @Test fun slideshowRenderPressureKeepsSourcesResultAndSupportsRetry() = runBlocking<Unit>(Dispatchers.IO) {
        val sources = mutableListOf<File>()
        val engine = SlideshowEngine(context)
        var value: SlideshowTimeline? = null
        var result: CompressedVideo? = null
        try {
            value = engine.importPresentation(project(sources, 2)) {}.let { it.copy(edge = 640,
                frames = it.frames.map { frame -> frame.copy(durationMs = 500) }) }
            result = engine.create(value) {}
            val saved = digest(result.file)
            val before = engine.folder.listFiles()!!.toSet()
            val pressure = AtomicBoolean(false)
            val limited = SlideshowEngine(context, capacity = { storage(pressure::get) })
            try {
                limited.create(value) { if (it != null && it > 0) pressure.set(true) }
                fail("Render disk pressure ignored")
            } catch (_: StorageCapacityException) { }
            assertEquals(before, engine.folder.listFiles()!!.toSet())
            assertEquals(saved, digest(result.file))
            assertTrue(engine.files(value).all { it.isFile })
            val retry = engine.create(value) {}
            try { assertEquals(640, retry.info.width) }
            finally { retry.file.delete(); retry.info.poster?.recycle() }
        } finally {
            result?.file?.delete(); result?.info?.poster?.recycle()
            value?.let { engine.files(it).forEach { file -> file.delete() } }; sources.forEach { it.delete() }
        }
    }

    @Test fun slideshowEncodingPressureCancelsTransformerAndRemovesOnlyItsJob() = runBlocking<Unit>(Dispatchers.IO) {
        val sources = mutableListOf<File>()
        val engine = SlideshowEngine(context)
        var value: SlideshowTimeline? = null
        try {
            value = engine.importPresentation(project(sources, 2)) {}.let { it.copy(edge = 640,
                frames = it.frames.map { frame -> frame.copy(durationMs = 60_000) }) }
            val before = engine.folder.listFiles()!!.toSet()
            val sawEncoding = AtomicBoolean(false)
            val limited = SlideshowEngine(context, capacity = { storage {
                engine.folder.listFiles()!!.any { it.name.startsWith("working-") && it.length() > 0 }.also {
                    if (it) sawEncoding.set(true)
                }
            } })
            try { withTimeout(30_000) { limited.create(value) {} }; fail("Encoder disk pressure ignored") }
            catch (_: StorageCapacityException) { }
            assertTrue("Pressure must occur after the native encoder starts", sawEncoding.get())
            assertEquals(before, engine.folder.listFiles()!!.toSet())
            assertTrue(engine.files(value).all { it.isFile })
        } finally {
            value?.let { engine.files(it).forEach { file -> file.delete() } }; sources.forEach { it.delete() }
        }
    }
}

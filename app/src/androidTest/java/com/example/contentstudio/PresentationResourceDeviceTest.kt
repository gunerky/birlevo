package com.example.contentstudio

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Debug
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.example.contentstudio.data.*
import com.example.contentstudio.media.*
import com.example.contentstudio.storage.*
import java.io.File
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

/** Synthetic inputs only; API 24-compatible engine profiling, separate from UI previews. */
class PresentationResourceDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val evidence get() = File(context.getExternalFilesDir(null), "qa/presentation-resources").apply { mkdirs() }
    private val phase get() = InstrumentationRegistry.getArguments().getString("phase", "final")!!
    private val noSpace get() = StreamingStorage { StorageSnapshot(4L*1024*1024*1024, 0) }
    private val noMemory get() = WorkingMemory { MemorySnapshot(8L*1024*1024,7L*1024*1024) }
    private suspend inline fun <reified T: Exception> rejected(crossinline work: suspend () -> Unit) {
        try { work(); fail("Expected ${T::class.java.simpleName}") }
        catch (failure: Exception) { if (failure !is T) throw failure }
    }
    private fun project(count: Int): Pair<StudioProject, List<File>> {
        val files = mutableListOf<File>()
        val bitmap = Bitmap.createBitmap(1024, 768, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        try {
            repeat(count) { index ->
                canvas.drawColor(Color.rgb(20 + index, 80, 180))
                val file = File.createTempFile("presentation-input-", ".png", context.cacheDir)
                file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                files += file
            }
        } finally { canvas.setBitmap(null); bitmap.recycle() }
        return StudioProject("resource-profile", ProjectKind.PRESENTATION, "Resource profile", 1,
            files.mapIndexed { index, file -> Slide("Slide ${index + 1}", "Vector text ${index + 1}", Uri.fromFile(file).toString()) }) to files
    }

    @Test fun profilePresentationPdfPages() = runBlocking<Unit> {
        val count = InstrumentationRegistry.getArguments().getString("pages", "24")!!.toInt()
        require(count in 1..100)
        val (project, inputs) = project(count)
        val samples = StringBuilder("page,elapsedMs,javaBytes,nativeBytes,pssKiB\n")
        val started = SystemClock.elapsedRealtime()
        val nativeAtStart = Debug.getNativeHeapAllocatedSize()
        val nativePeak = java.util.concurrent.atomic.AtomicLong(nativeAtStart)
        val sampler = launch(Dispatchers.Default) { while(isActive) {
            nativePeak.updateAndGet { maxOf(it,Debug.getNativeHeapAllocatedSize()) }; delay(10)
        } }
        var retained = nativeAtStart
        fun sample(page: Int) {
            val native = Debug.getNativeHeapAllocatedSize()
            retained = maxOf(retained, native)
            val runtime = Runtime.getRuntime()
            val info = Debug.MemoryInfo().also(Debug::getMemoryInfo)
            samples.append("$page,${SystemClock.elapsedRealtime()-started},${runtime.totalMemory()-runtime.freeMemory()},$native,${info.totalPss}\n")
        }
        var result: PresentationPdfResult? = null
        try {
            sample(0)
            var page = 0
            result = PresentationPdf(context).create(project) { sample(++page) }
            sample(count + 1)
            result.file.copyTo(File(evidence, "slides-$phase-$count.pdf"), overwrite = true)
            assertEquals(count, result.pages)
            if (phase != "baseline" && count >= 24) assertTrue("Native retention grew by ${retained-nativeAtStart}",
                retained-nativeAtStart < 64L*1024*1024)
            if (phase != "baseline" && count >= 24) assertTrue("Sampled native peak grew by ${nativePeak.get()-nativeAtStart}",
                nativePeak.get()-nativeAtStart < 64L*1024*1024)
        } finally {
            sampler.cancelAndJoin()
            File(evidence, "slides-$phase-$count.csv").writeText(samples.toString())
            File(evidence, "slides-$phase-$count-peak.txt").writeText("sampledNativeBytes=${nativePeak.get()}\n")
            result?.file?.delete(); inputs.forEach { it.delete() }
        }
    }

    @Test fun profileSlideshowRastersAnd1920Output() = runBlocking<Unit> {
        val (project, inputs) = project(20)
        val engine = SlideshowEngine(context)
        val timeline = engine.importPresentation(project) {}.let { value ->
            value.copy(frames = value.frames.map { it.copy(durationMs = 500) }, edge = 1920)
        }
        val before = ProjectCodec.encode(listOf(project))
        val samples = StringBuilder("progress,nativeBytes,javaBytes\n")
        var result: CompressedVideo? = null
        try {
            result = withContext(Dispatchers.IO) { engine.create(timeline) { progress ->
                val runtime = Runtime.getRuntime()
                samples.append("$progress,${Debug.getNativeHeapAllocatedSize()},${runtime.totalMemory()-runtime.freeMemory()}\n")
            } }
            assertEquals(1920, result.info.width); assertEquals(1080, result.info.height)
            assertEquals(10_000L, result.info.durationMs)
            assertEquals(before, ProjectCodec.encode(listOf(project)))
            result.file.copyTo(File(evidence, "slideshow-$phase-1920.mp4"), overwrite = true)
            assertFalse(engine.folder.listFiles().orEmpty().any { it.name.startsWith("render-") || it.name.startsWith("working-") })
        } finally {
            File(evidence, "slideshow-$phase-1920.csv").writeText(samples.toString())
            result?.info?.poster?.recycle(); result?.file?.delete()
            engine.files(timeline).forEach { it.delete() }; inputs.forEach { it.delete() }
        }
    }

    @Test fun presentationFailuresPreserveInputsAndExistingPdfAndRemoveScratch() = runBlocking<Unit> {
        val (project, inputs) = project(2)
        val sourceBytes = inputs.map { it.readBytes() }
        val engine = PresentationPdf(context)
        val result = engine.create(project) {}
        val bytes = result.file.readBytes()
        val folder = result.file.parentFile!!
        val before = folder.listFiles()!!.map { it.name }.toSet()
        try {
            rejected<WorkingMemoryException> { PresentationPdf(context,memory=noMemory).create(project) {} }
            rejected<WorkingMemoryException> { PresentationPdf(context,memory=noMemory).renderSlideBitmap(project,0,1920,1080) }
            rejected<StorageCapacityException> { PresentationPdf(context,capacity={ noSpace }).create(project) {} }
            rejected<CancellationException> { engine.create(project) { throw CancellationException("Cancel page") } }
            for (afterPage in listOf(1,2)) {
                var pressure = false
                val limited = PresentationPdf(context,capacity={ StreamingStorage {
                    StorageSnapshot(4L*1024*1024*1024, if(pressure) 0 else 3L*1024*1024*1024)
                } })
                var completed = 0
                rejected<StorageCapacityException> { limited.create(project) { if(++completed == afterPage) pressure = true } }
                assertEquals(before,folder.listFiles()!!.map { it.name }.toSet())
            }
            assertEquals(before,folder.listFiles()!!.map { it.name }.toSet())
            assertArrayEquals(bytes,result.file.readBytes())
            inputs.zip(sourceBytes).forEach { (file, original) -> assertArrayEquals(original,file.readBytes()) }
            val bitmap = engine.renderSlideBitmap(project,0,1920,1080)
            try { assertEquals(1920,bitmap.width); assertEquals(1080,bitmap.height) } finally { bitmap.recycle() }
        } finally { result.file.delete(); inputs.forEach { it.delete() } }
    }

    @Test fun slideshowAdmissionAndPressureDuringPngAndNativeEncodingAllowRetry() = runBlocking<Unit> {
        val (project, inputs) = project(3)
        val engine = SlideshowEngine(context)
        val value = engine.importPresentation(project) {}.let { it.copy(edge=640,frames=it.frames.map { frame -> frame.copy(durationMs=500) }) }
        val result = withContext(Dispatchers.IO) { engine.create(value) {} }
        val bytes = result.file.readBytes()
        val original = ProjectCodec.encode(listOf(project))
        val before = engine.folder.listFiles()!!.map { it.name }.toSet()
        try {
            rejected<WorkingMemoryException> { SlideshowEngine(context,memory=noMemory).create(value) {} }
            rejected<WorkingMemoryException> { SlideshowEngine(context,memory=noMemory).preview(value,0) }
            rejected<StorageCapacityException> { SlideshowEngine(context,capacity={ noSpace }).create(value) {} }
            rejected<CancellationException> { engine.create(value) { throw CancellationException("Cancel raster") } }
            var pressure = false
            val limited = SlideshowEngine(context,capacity={ StreamingStorage {
                StorageSnapshot(4L*1024*1024*1024,if(pressure) 0 else 3L*1024*1024*1024)
            } })
            rejected<StorageCapacityException> { limited.create(value) { pressure = true } }
            var rendered = false
            val queries = java.util.concurrent.atomic.AtomicInteger()
            val nativeLimited = SlideshowEngine(context,capacity={ StreamingStorage {
                val exhausted = rendered && queries.incrementAndGet() >= 3
                StorageSnapshot(4L*1024*1024*1024,if(exhausted) 0 else 3L*1024*1024*1024)
            } })
            val longValue = value.copy(frames=value.frames.map { it.copy(durationMs=30_000) })
            rejected<StorageCapacityException> { withContext(Dispatchers.IO) {
                nativeLimited.create(longValue) { if(it != null && it >= 20) rendered = true }
            } }
            assertTrue(queries.get() >= 3)
            assertEquals(before,engine.folder.listFiles()!!.map { it.name }.toSet())
            assertArrayEquals(bytes,result.file.readBytes())
            assertEquals(original,ProjectCodec.encode(listOf(project)))
            assertTrue(engine.files(value).all(File::isFile))
            val retry = withContext(Dispatchers.IO) { engine.create(value) {} }
            try { assertEquals(640,retry.info.width) } finally { retry.file.delete(); retry.info.poster?.recycle() }
        } finally {
            result.info.poster?.recycle(); result.file.delete()
            engine.files(value).forEach { it.delete() }; inputs.forEach { it.delete() }
        }
    }
}

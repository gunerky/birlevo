package com.example.contentstudio

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.net.Uri
import android.os.Debug
import android.os.Build
import android.os.SystemClock
import android.os.Process
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.example.contentstudio.media.*
import com.example.contentstudio.processing.*
import com.example.contentstudio.storage.*
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

/** Large original fixtures remain host-generated QA files, outside the APK. */
class VideoResourceDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val phase get() = InstrumentationRegistry.getArguments().getString("profilePhase", "final")!!
    private val evidence get() = File(context.getExternalFilesDir(null), "qa/video-resources").apply { mkdirs() }
    private val fixtures get() = File(context.getExternalFilesDir(null), "qa/video-resource-input")
    private fun fixture(name: String) = File(fixtures, name).also { check(it.isFile) }
    private val work get() = File(context.noBackupFilesDir, "qa-video-resources").apply { mkdirs() }
    private fun engine(capacity: (File) -> StreamingStorage = ::deviceStorage) =
        VideoCompressor(context, ownedFolder = work, capacity = capacity)
    private val shortName get() = when (Build.VERSION.SDK_INT) { 24 -> "legacy.mp4"; 28 -> "medium.mp4"; else -> "high.mp4" }
    private suspend fun waitFor(condition: () -> Boolean) = withTimeout(30_000) {
        while (!condition()) delay(10)
    }
    private suspend fun main(block: suspend () -> Unit) = withContext(Dispatchers.Main) { block() }
    private fun digest(file: File): String {
        val hash = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val bytes = ByteArray(65536)
            while (true) { val n = input.read(bytes); if (n < 0) break; hash.update(bytes, 0, n) } }
        return hash.digest().joinToString("") { "%02x".format(it) }
    }
    private fun save(bitmap: Bitmap, name: String) = File(evidence, name).outputStream().use {
        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
    }
    private fun assertMarkers(bitmap: Bitmap) {
        val colors = listOf(1 to 1, 15 to 1, 1 to 15, 15 to 15).map { (x,y) ->
            bitmap.getPixel(bitmap.width*x/16, bitmap.height*y/16)
        }
        for (i in colors.indices) for (j in i+1 until colors.size) {
            val a=colors[i]; val b=colors[j]
            assertTrue("Distinct orientation markers must survive; blank/green frames are invalid",
                maxOf(kotlin.math.abs(Color.red(a)-Color.red(b)), kotlin.math.abs(Color.green(a)-Color.green(b)),
                    kotlin.math.abs(Color.blue(a)-Color.blue(b))) > 100)
        }
    }

    @Test fun profileSourcePostersWithoutChangingDimensionsOrOriginals() = runBlocking<Unit>(Dispatchers.IO) {
        val rows = StringBuffer("source,ms,javaBytes,nativeBytes,pssKiB\n")
        val report = StringBuilder()
        val names = InstrumentationRegistry.getArguments().getString("posterInputs")?.split(',')
            ?: when (Build.VERSION.SDK_INT) {
                24 -> listOf("legacy.mp4", "legacy-rotated.mp4", "long.mp4")
                28 -> listOf("medium.mp4", "medium-rotated.mp4", "long.mp4")
                else -> listOf("high.mp4", "rotated.mp4", "long.mp4")
            }
        for (name in names) {
            val original = fixture(name); val before = digest(original)
            val started = SystemClock.elapsedRealtime()
            fun sample() { val runtime = Runtime.getRuntime()
                rows.append("$name,${SystemClock.elapsedRealtime()-started},${runtime.totalMemory()-runtime.freeMemory()},${Debug.getNativeHeapAllocatedSize()},${Debug.getPss()}\n") }
            val sampler = launch(Dispatchers.Default) { while (isActive) { sample(); delay(5) } }
            var source: VideoSource? = null
            try {
                source = VideoCompressor(context).importVideo(Uri.fromFile(original))
                sample()
                val poster = checkNotNull(source.info.poster) { "Poster unavailable for $name" }
                assertMarkers(poster)
                assertEquals(512, maxOf(poster.width, poster.height))
                assertTrue(poster.allocationByteCount <= 512*512*4)
                assertTrue(kotlin.math.abs(source.info.durationMs - if (name == "long.mp4") 599_000L else 8000L) < 50)
                val sourceWidth = if (name.startsWith("medium")) 1920 else if (name.startsWith("legacy") || name == "long.mp4") 1280 else 3840
                val sourceHeight = sourceWidth * 9 / 16
                val rotated = name.contains("rotated")
                assertEquals(if (rotated) sourceHeight else sourceWidth, source.info.width)
                assertEquals(if (rotated) sourceWidth else sourceHeight, source.info.height)
                assertTrue(source.info.hasAudio)
                save(poster, "${name.removeSuffix(".mp4")}-poster-$phase.png")
                report.append("$name: ${source.info.width}x${source.info.height}, ${source.info.durationMs}ms, poster=${poster.width}x${poster.height}\n")
                assertEquals(before, digest(original)); assertEquals(before, digest(source.file))
            } finally {
                sampler.cancelAndJoin(); source?.info?.poster?.recycle(); source?.file?.delete()
                File(evidence, "poster-$phase.csv").writeText(rows.toString())
                File(evidence, "poster-$phase.txt").writeText(report.toString())
            }
        }
    }

    @Test fun highResolutionCodecCapabilityIsExplicitAndPreservesSource() = runBlocking<Unit>(Dispatchers.IO) {
        val original = fixture("high.mp4"); val before = digest(original)
        val extractor = MediaExtractor(); val report = StringBuilder()
        var supported = true
        try {
            extractor.setDataSource(original.path)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val decoder = MediaCodecList(MediaCodecList.REGULAR_CODECS).findDecoderForFormat(format)
                if (decoder == null) supported = false
                report.append("$format\ndecoder=$decoder\n")
            }
        } finally { extractor.release() }
        val existing = work.listFiles().orEmpty().toSet()
        if (supported) {
            val source = engine().importVideo(Uri.fromFile(original))
            try { assertEquals(3840, source.info.width); assertEquals(before, digest(source.file)) }
            finally { source.info.poster?.recycle(); source.file.delete() }
        } else {
            try { engine().importVideo(Uri.fromFile(original)); fail("Unsupported codec must be rejected") }
            catch (failure: MediaImportException) { assertEquals(ImportIssue.VIDEO_CODEC, failure.issue) }
        }
        assertEquals(existing, work.listFiles().orEmpty().toSet()); assertEquals(before, digest(original))
        File(evidence, "codec-capabilities.txt").writeText("supported=$supported\n$report")
    }

    @Test fun selectedHighResolutionAndFullStillKeepPixelsAndSourceBytes() = runBlocking<Unit>(Dispatchers.IO) {
        val engine = engine(); val original = fixture(shortName); val before = digest(original)
        val source = engine.importVideo(Uri.fromFile(original))
        try {
            assertNull(engine.outputSupport(source.info, VideoQuality.HIGH, 1920)[VideoOutputFormat.MP4_AVC])
            val result = engine.compress(source, VideoQuality.HIGH, 1920, VideoTrim(0, 8000)) {}
            try {
                val target = videoTarget(source.info.width, source.info.height, 1920, VideoQuality.HIGH)
                assertEquals(target.width, result.info.width); assertEquals(target.height, result.info.height)
                assertTrue(kotlin.math.abs(result.info.durationMs - 8000) < 100)
                result.file.copyTo(File(evidence, "high-output.mp4"), true)
                val still = VideoFrameExtractor(context).write(VideoFrameTarget(source.file, source.info.durationMs, source.name), 4100)
                try {
                    val decoded = BitmapFactory.decodeFile(still.path)
                    try { assertMarkers(decoded) } finally { decoded.recycle() }
                    still.copyTo(File(evidence, "full-source-frame.png"), true)
                } finally { still.delete() }
                assertEquals(before, digest(original)); assertEquals(before, digest(source.file))
            } finally { result.info.poster?.recycle(); result.file.delete() }
        } finally { source.info.poster?.recycle(); source.file.delete() }
    }

    /** Can also run against the unchanged app: it uses no new constructor or preview API. */
    @Test fun recordLegacyColorReference() = runBlocking<Unit>(Dispatchers.IO) {
        val engine = engine(); val original = fixture("legacy.mp4"); val before = digest(original)
        val source = engine.importVideo(Uri.fromFile(original))
        try {
            val result = engine.compress(source, VideoQuality.SMALL, 640, VideoTrim(0, 8000)) {}
            try { result.file.copyTo(File(evidence, "color-$phase.mp4"), true) }
            finally { result.info.poster?.recycle(); result.file.delete() }
            assertEquals(before, digest(original)); assertEquals(before, digest(source.file))
        } finally { source.info.poster?.recycle(); source.file.delete() }
    }

    @Test fun previewMemoryAdmissionDoesNotLowerFullStillResolution() = runBlocking<Unit>(Dispatchers.IO) {
        val original = fixture(shortName); val before = digest(original)
        val source = engine().importVideo(Uri.fromFile(original))
        val limited = WorkingMemory { MemorySnapshot(16L*1024*1024, 0) }
        try {
            val bounded = VideoCompressor(context, ownedFolder = work, memory = limited).importVideo(Uri.fromFile(original))
            try { assertNotNull("Optional poster should fit independently of full still export", bounded.info.poster) }
            finally { bounded.info.poster?.recycle(); bounded.file.delete() }
            val reader = VideoFrameExtractor(context, limited)
            val target = VideoFrameTarget(source.file, source.info.durationMs, source.name)
            val preview = reader.read(target, 100, true)
            try { assertEquals(512, maxOf(preview.bitmap.width, preview.bitmap.height)) }
            finally { preview.bitmap.recycle() }
            try { reader.read(target, 100, false).bitmap.recycle(); fail("Full still must retain its requested pixels or report memory pressure") }
            catch (_: WorkingMemoryException) { }
            assertEquals(before, digest(original)); assertEquals(before, digest(source.file))
        } finally { source.info.poster?.recycle(); source.file.delete() }
    }

    @Test fun cancellationAndLiveDiskPressurePreserveSourcesAndPreviousResultThenRetry() = runBlocking<Unit>(Dispatchers.IO) {
        val engine = engine(); val source = engine.importVideo(Uri.fromFile(fixture("long.mp4")))
        val before = digest(source.file)
        val previous = engine.compress(source, VideoQuality.SMALL, 640, VideoTrim(595_000, 598_000)) {}
        val previousHash = digest(previous.file)
        val existing = work.listFiles().orEmpty().toSet()
        suspend fun dirty() = waitFor { work.listFiles().orEmpty().any { it.name.startsWith("working-") && it.length() > 1000 } }
        try {
            val cancelled = launch { engine.compress(source, VideoQuality.SMALL, 640, VideoTrim(0, 599_000)) {} }
            dirty(); cancelled.cancelAndJoin()
            assertEquals(existing, work.listFiles().orEmpty().toSet())
            val pressured = AtomicBoolean(false)
            val pressure = engine { StreamingStorage { StorageSnapshot(4L*1024*1024*1024,
                if (pressured.get()) 0 else 3L*1024*1024*1024) } }
            val failed = async {
                try { pressure.compress(source, VideoQuality.SMALL, 640, VideoTrim(0, 599_000)) {}.also {
                    it.info.poster?.recycle(); it.file.delete() }; false }
                catch (_: StorageCapacityException) { true }
            }
            dirty(); pressured.set(true); assertTrue(failed.await())
            assertEquals(existing, work.listFiles().orEmpty().toSet())
            assertEquals(before, digest(source.file)); assertEquals(previousHash, digest(previous.file))
            val retry = engine.compress(source, VideoQuality.SMALL, 640, VideoTrim(595_000, 598_000)) {}
            try { retry.file.copyTo(File(evidence, "retry-tail.mp4"), true) }
            finally { retry.info.poster?.recycle(); retry.file.delete() }
        } finally { previous.info.poster?.recycle(); previous.file.delete(); source.info.poster?.recycle(); source.file.delete() }
    }

    @Test fun completeLongVideoSurvivesBackgroundAndReattachesResult() = runBlocking<Unit>(Dispatchers.IO) {
        val jobs = ProcessingJobs.get(context)
        main { jobs.awaitReady(); jobs.forget(ProcessingKind.VIDEO) }
        AtomicMediaSession(File(context.noBackupFilesDir, "video-compression")).write(null)
        var activity: ActivityScenario<MainActivity>? = ActivityScenario.launch(MainActivity::class.java)
        lateinit var model: VideoCompressionViewModel
        activity!!.onActivity { model = ViewModelProvider(it)[VideoCompressionViewModel::class.java] }
        waitFor { !model.state.value.busy }
        main { model.load(Uri.fromFile(fixture("long.mp4"))) }
        waitFor { !model.state.value.busy }; assertNotNull(model.state.value.source)
        val original = model.state.value.source!!; val before = digest(original.file)
        main { model.setOptions(VideoQuality.SMALL, 640); model.setTrim(0, 599_000) }
        waitFor { model.state.value.outputSupport.containsKey(VideoOutputFormat.MP4_AVC) }
        assertNull(model.state.value.outputSupport[VideoOutputFormat.MP4_AVC])
        val started = SystemClock.elapsedRealtime()
        val samples = StringBuilder("ms,javaBytes,nativeBytes,pssKiB\n")
        val sampler = launch(Dispatchers.Default) { while (isActive) {
            val runtime = Runtime.getRuntime()
            samples.append("${SystemClock.elapsedRealtime()-started},${runtime.totalMemory()-runtime.freeMemory()},${Debug.getNativeHeapAllocatedSize()},${Debug.getPss()}\n")
            delay(250)
        } }
        try {
            main { model.preview() }
            waitFor { jobs.records.value[ProcessingKind.VIDEO]?.status == ProcessingStatus.RUNNING }
            activity!!.close(); activity = null
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            fun shell(command: String) = automation.executeShellCommand(command).use {
                android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText()
            }
            try {
                shell("input keyevent KEYCODE_SLEEP")
                withTimeout(20*60*1000L) { while (jobs.records.value[ProcessingKind.VIDEO]?.status?.active != false) delay(50) }
            } finally { shell("input keyevent KEYCODE_WAKEUP"); shell("wm dismiss-keyguard") }
            assertEquals(ProcessingStatus.SUCCEEDED, jobs.records.value[ProcessingKind.VIDEO]!!.status)
            activity = ActivityScenario.launch(MainActivity::class.java)
            activity!!.onActivity { model = ViewModelProvider(it)[VideoCompressionViewModel::class.java] }
            waitFor { !model.state.value.busy && model.state.value.result != null }
            assertEquals(original.file, model.state.value.source!!.file)
            assertEquals(VideoTrim(0, 599_000), model.state.value.trim)
            assertEquals(VideoQuality.SMALL, model.state.value.quality); assertEquals(640, model.state.value.maxEdge)
            val result = model.state.value.result!!
            assertEquals(640, result.info.width); assertEquals(360, result.info.height)
            assertTrue(kotlin.math.abs(result.info.durationMs - 599_000) < 100)
            assertEquals(before, digest(original.file)); assertEquals(before, digest(fixture("long.mp4")))
            result.file.copyTo(File(evidence, "long-output.mp4"), true)
            File(evidence, "long-time.txt").writeText("elapsedMs=${SystemClock.elapsedRealtime()-started}\nsourceDurationMs=${original.info.durationMs}\noutputDurationMs=${result.info.durationMs}\n")
        } finally {
            sampler.cancelAndJoin(); File(evidence, "long-memory.csv").writeText(samples.toString())
            main { model.reset(); model.awaitSession() }; activity?.close()
        }
    }

    @Test fun prepareLongSessionForExternalStop() = runBlocking<Unit>(Dispatchers.IO) {
        val jobs = ProcessingJobs.get(context)
        main { jobs.awaitReady(); jobs.forget(ProcessingKind.VIDEO) }
        AtomicMediaSession(File(context.noBackupFilesDir, "video-compression")).write(null)
        val activity = ActivityScenario.launch(MainActivity::class.java)
        lateinit var model: VideoCompressionViewModel
        activity.onActivity { model = ViewModelProvider(it)[VideoCompressionViewModel::class.java] }
        try {
            waitFor { !model.state.value.busy }
            main { model.load(Uri.fromFile(fixture("long.mp4"))) }
            waitFor { !model.state.value.busy }; assertNotNull(model.state.value.source)
            main { model.setOptions(VideoQuality.HIGH, 1920); model.setTrim(595_000, 598_000); assertTrue(model.awaitSession()) }
            val saved = AtomicMediaSession(File(context.noBackupFilesDir, "video-compression")).read()!!
            var entered = false
            main { jobs.start(ProcessingKind.VIDEO, saved) { entered = true; awaitCancellation() } }
            waitFor { entered }
            File(evidence, "prepared-session.txt").writeText(saved)
            File(evidence, "prepared-source.sha256").writeText(digest(model.state.value.source!!.file))
            File(evidence, "prepared-pid.txt").writeText(Process.myPid().toString())
        } finally { activity.close() }
    }

    @Test fun verifyLongSessionAfterExternalStopAndExplicitRetry() = runBlocking<Unit>(Dispatchers.IO) {
        assertNotEquals(File(evidence, "prepared-pid.txt").readText().toInt(), Process.myPid())
        val activity = ActivityScenario.launch(MainActivity::class.java)
        lateinit var model: VideoCompressionViewModel
        activity.onActivity { model = ViewModelProvider(it)[VideoCompressionViewModel::class.java] }
        try {
            waitFor { !model.state.value.busy && model.state.value.workStatus == ProcessingStatus.INTERRUPTED }
            assertNull(model.state.value.result)
            assertEquals(VideoQuality.HIGH, model.state.value.quality); assertEquals(1920, model.state.value.maxEdge)
            assertEquals(VideoTrim(595_000, 598_000), model.state.value.trim)
            assertEquals(File(evidence, "prepared-source.sha256").readText(), digest(model.state.value.source!!.file))
            main { model.setOptions(VideoQuality.SMALL, 640) }
            waitFor { model.state.value.outputSupport.containsKey(VideoOutputFormat.MP4_AVC) }
            main { model.preview() }
            withTimeout(120_000) { while (model.state.value.busy || model.state.value.result == null) delay(25) }
            model.state.value.result!!.file.copyTo(File(evidence, "restored-tail.mp4"), true)
            assertEquals(File(evidence, "prepared-source.sha256").readText(), digest(model.state.value.source!!.file))
            File(evidence, "verified-pid.txt").writeText(Process.myPid().toString())
        } finally { main { model.reset(); model.awaitSession() }; activity.close() }
    }
}

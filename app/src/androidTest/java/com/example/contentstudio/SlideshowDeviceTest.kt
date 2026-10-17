package com.example.contentstudio

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.example.contentstudio.data.*
import com.example.contentstudio.media.*
import com.example.contentstudio.processing.*
import java.io.File
import java.io.IOException
import kotlin.math.abs
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*

class SlideshowDeviceTest {
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val stores = mutableListOf<ViewModelStore>()
    private val inputs = mutableListOf<File>()
    private val engine
        get() = SlideshowEngine(context)

    private suspend fun main(block: suspend () -> Unit) = withContext(Dispatchers.Main) { block() }

    private suspend fun waitFor(block: () -> Boolean) {
        withTimeout(120_000) { while (!block()) delay(25) }
    }

    private fun own(model: SlideshowViewModel) =
        model.also {
            ViewModelStore().also { store ->
                store.put("qa", model)
                stores += store
            }
        }

    private fun image(name: String, color: Int): Uri {
        val file = File(context.cacheDir, "slideshow-$name.png")
        inputs += file
        val bitmap = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(color)
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally {
            bitmap.recycle()
        }
        return Uri.fromFile(file)
    }

    private suspend fun color(result: CompressedVideo, us: Long, expected: Int) {
        // Legacy retrievers can return a sync frame despite OPTION_CLOSEST. The
        // independently decoded QA MP4 confirmed the correct timed frame exists.
        val bitmap = if (android.os.Build.VERSION.SDK_INT < 27) {
            VideoFrameExtractor(context).read(
                VideoFrameTarget(result.file, result.info.durationMs, "slideshow-qa.mp4"),
                us / 1000, preview = false,
            ).bitmap
        } else {
            val reader = MediaMetadataRetriever()
            try {
                reader.setDataSource(result.file.path)
                checkNotNull(reader.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST))
            } finally { reader.release() }
        }
        try {
            val actual = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
            assertTrue(
                "Frame color $actual instead of $expected",
                abs(Color.red(actual) - Color.red(expected)) < 25 &&
                    abs(Color.green(actual) - Color.green(expected)) < 25 &&
                    abs(Color.blue(actual) - Color.blue(expected)) < 25,
            )
        } finally { bitmap.recycle() }
    }

    @Before
    fun before() =
        runBlocking<Unit> {
            main {
                ProcessingJobs.get(context).awaitReady()
                ProcessingJobs.get(context).forget(ProcessingKind.SLIDESHOW)
            }
            AtomicMediaSession(File(context.noBackupFilesDir, SlideshowEngine.FOLDER)).write(null)
        }

    @After
    fun after() =
        runBlocking<Unit> {
            main {
                stores.forEach { it.clear() }
                ProcessingJobs.get(context).forget(ProcessingKind.SLIDESHOW)
            }
            inputs.forEach { it.delete() }
        }

    @Test
    fun orphanCleanupPreservesSavedAssetsResultsAndFreshJobs() =
        runBlocking<Unit>(Dispatchers.IO) {
            val engine = engine
            val now = System.currentTimeMillis()
            val old = now - 8L * 24 * 60 * 60 * 1000
            val retained =
                File(engine.folder, "page-123456.png").apply {
                    writeText("saved")
                    setLastModified(old)
                }
            val result =
                File(engine.folder, "output-00000000-0000-0000-0000-000000000001.mp4").apply {
                    writeText("result")
                    setLastModified(old)
                }
            val orphan =
                File(engine.folder, "working-00000000-0000-0000-0000-000000000002.mp4").apply {
                    writeText("partial")
                    setLastModified(old)
                }
            val fresh = File(engine.folder, "working-00000000-0000-0000-0000-000000000003.mp4").apply { writeText("fresh") }
            val directory =
                File(engine.folder, "render-00000000-0000-0000-0000-000000000000").apply {
                    mkdirs()
                }
            File(directory, "0.png").apply { writeText("raster"); setLastModified(old) }
            directory.setLastModified(old)
            val unknown =
                File(engine.folder, "unrelated.txt").apply {
                    writeText("keep")
                    setLastModified(old)
                }
            try {
                engine.removeStaleFiles(setOf(retained, result), now)
                assertTrue(retained.isFile)
                assertTrue(result.isFile)
                assertTrue(fresh.isFile)
                assertTrue(unknown.isFile)
                assertFalse(orphan.exists())
                assertFalse(directory.exists())
            } finally {
                listOf(retained, result, orphan, fresh, unknown).forEach { it.delete() }
                directory.listFiles()?.forEach { it.delete() }
                directory.delete()
            }
        }

    @Test
    fun actualPhotoVideoPreservesDurationsOrderCanvasAndOriginalBytes() =
        runBlocking<Unit>(Dispatchers.IO) {
            val engine = engine
            val uris = listOf(image("red", Color.RED), image("blue", Color.BLUE))
            val originals = inputs.map { it.readBytes() }
            val imported = engine.importImages(uris, SlideshowTimeline()) {}
            val timed =
                imported.copy(
                    frames =
                        imported.frames.mapIndexed { i, frame ->
                            frame.copy(durationMs = if (i == 0) 1000 else 1500)
                        },
                    edge = 640,
                )
            try {
                for (canvas in
                    listOf(
                        VideoCanvas.LANDSCAPE_16_9,
                        VideoCanvas.PORTRAIT_9_16,
                        VideoCanvas.SQUARE,
                    )) {
                    val value = timed.copy(canvas = canvas)
                    assertNull(engine.issue(value))
                    val result = engine.create(value) {}
                    try {
                        assertFalse(result.info.hasAudio)
                        assertEquals("video/avc", result.info.videoMime)
                        assertTrue(abs(2500 - result.info.durationMs) <= 100)
                        assertEquals(value.target().width, result.info.width)
                        assertEquals(value.target().height, result.info.height)
                        val qa =
                            File(context.getExternalFilesDir(null), "qa/slideshow").apply {
                                mkdirs()
                            }
                        result.file.copyTo(File(qa, "$canvas.mp4"), overwrite = true)
                        // Keep the actual output for independent decoding if the platform
                        // retriever or encoded frame assertions fail on an older emulator.
                        color(result, 300_000, Color.RED)
                        color(result, 1_300_000, Color.BLUE)
                        val saved = File(context.cacheDir, "slideshow-saved-$canvas.mp4")
                        inputs += saved
                        engine.export(value, result, Uri.fromFile(saved))
                        assertArrayEquals(result.file.readBytes(), saved.readBytes())
                        assertThrows(IllegalArgumentException::class.java) {
                            runBlocking { engine.export(value, result, uris.first()) }
                        }
                    } finally {
                        result.file.delete()
                        result.info.poster?.recycle()
                    }
                }
                originals.forEachIndexed { i, bytes ->
                    assertArrayEquals(bytes, inputs[i].readBytes())
                }
            } finally {
                engine.files(imported).forEach { it.delete() }
            }
        }

    @Test
    fun presentationSnapshotKeepsTextThemeLayoutsAndOwnsCopiedImage() =
        runBlocking<Unit>(Dispatchers.IO) {
            val engine = engine
            val original = image("project", Color.GREEN)
            val project =
                StudioProject(
                    "project",
                    ProjectKind.PRESENTATION,
                    "İstanbul العربية 日本語",
                    0,
                    listOf(
                        Slide(
                            "Heading",
                            "Body",
                            original.toString(),
                            id = "a",
                            layout = SlideLayout.IMAGE_LEFT,
                        ),
                        Slide(
                            "Text only",
                            "Second frame",
                            id = "b",
                            layout = SlideLayout.TEXT_IMAGE,
                        ),
                    ),
                    theme = PresentationTheme.OCEAN,
                )
            val value = engine.importPresentation(project) {}.copy(edge = 640)
            // Decode the source snapshot from JSON after the original has gone away.
            inputs.first().delete()
            try {
                val restored = engine.restore(SlideshowCodec.decode(SlideshowCodec.encode(value)))
                assertEquals(
                    project.slides.map { it.heading },
                    restored.frames.map { it.slide!!.heading },
                )
                assertEquals(project.theme, restored.theme)
                assertEquals(SlideLayout.IMAGE_LEFT, restored.frames.first().slide!!.layout)
                val preview = engine.preview(restored, 0)
                try {
                    assertNotEquals(Color.BLACK, preview.getPixel(10, 10))
                } finally {
                    preview.recycle()
                }
                val result =
                    engine.create(
                        restored.copy(frames = restored.frames.map { it.copy(durationMs = 750) })
                    ) {}
                try {
                    assertTrue(abs(result.info.durationMs - 1500) <= 100)
                    assertEquals(640, result.info.width)
                } finally {
                    result.file.delete()
                    result.info.poster?.recycle()
                }
            } finally {
                engine.files(value).forEach { it.delete() }
            }
        }

    @Test
    fun cancelRemovesPartialPngsAndVideoButKeepsPrivateSources() =
        runBlocking<Unit>(Dispatchers.IO) {
            val engine = engine
            val value =
                engine.importImages(listOf(image("cancel", Color.CYAN)), SlideshowTimeline()) {}
            val before = engine.folder.listFiles()!!.toSet()
            val started = CompletableDeferred<Unit>()
            val job = launch {
                engine.create(
                    value.copy(frames = value.frames.map { it.copy(durationMs = 60_000) })
                ) {
                    if (it != null && it >= 20) started.complete(Unit)
                }
            }
            withTimeout(30_000) { started.await() }
            job.cancelAndJoin()
            assertTrue(engine.files(value).all { it.exists() })
            assertTrue((engine.folder.listFiles()!!.toSet() - before).isEmpty())
            engine.files(value).forEach { it.delete() }
        }

    @Test
    fun settingsAndFinishedResultRestoreAfterOriginalRemovalAndViewModelRecreation() =
        runBlocking<Unit> {
            lateinit var first: SlideshowViewModel
            main { first = own(SlideshowViewModel(context.applicationContext as Application)) }
            waitFor { !first.state.value.busy }
            val original = image("restore", Color.MAGENTA)
            main { first.add(listOf(original)) }
            waitFor { !first.state.value.busy }
            main {
                first.options(canvas = VideoCanvas.SQUARE, quality = VideoQuality.HIGH, edge = 640)
                first.duration(first.state.value.timeline.frames.first().id, 750)
                assertTrue(first.awaitSession())
            }
            waitFor { first.state.value.supportReady }
            main { first.generate() }
            waitFor { !first.state.value.busy }
            assertNotNull(first.state.value.result)
            val snapshot = first.state.value.timeline
            val output = first.state.value.result!!.file
            inputs.first().delete()
            main { stores.last().clear() }
            lateinit var restored: SlideshowViewModel
            main { restored = own(SlideshowViewModel(context.applicationContext as Application)) }
            waitFor { !restored.state.value.busy && restored.state.value.result != null }
            assertEquals(snapshot, restored.state.value.timeline)
            assertEquals(output, restored.state.value.result!!.file)
            main { restored.duration(snapshot.frames.first().id, 1000) }
            assertNull(restored.state.value.result)
            waitFor { !output.exists() }
            main {
                restored.reset()
                assertTrue(restored.awaitSession())
            }
            assertNull(AtomicMediaSession(engine.folder).read())
        }

    private class Failing(private val disk: MediaSessionStorage) : MediaSessionStorage {
        var fail = false

        override fun read() = disk.read()

        override fun write(json: String?) {
            if (fail) throw IOException("QA full disk")
            disk.write(json)
        }
    }

    @Test
    fun queuedCreationSurvivesViewModelDisposalAndRestoresTheDurableResult() =
        runBlocking<Unit> {
            val jobs = ProcessingJobs.get(context)
            val gate = CompletableDeferred<Unit>()
            lateinit var model: SlideshowViewModel
            main {
                jobs.forget(ProcessingKind.VIDEO)
                model = own(SlideshowViewModel(context.applicationContext as Application))
            }
            waitFor { !model.state.value.busy }
            main { model.add(listOf(image("queue", Color.GREEN))) }
            waitFor { !model.state.value.busy }
            main {
                model.duration(model.state.value.timeline.frames.first().id, 500)
                model.options(edge = 640)
                assertTrue(model.awaitSession())
            }
            waitFor { model.state.value.supportReady }
            try {
                main {
                    jobs.start(ProcessingKind.VIDEO, "slideshow-queue-test") {
                        gate.await()
                        val folder =
                            File(context.noBackupFilesDir, ProcessingKind.VIDEO.folder).apply {
                                mkdirs()
                            }
                        val output =
                            File.createTempFile("output-", ".mp4", folder).apply {
                                writeBytes(byteArrayOf(1))
                            }
                        ProcessingOutput(output.name, 1)
                    }
                    model.generate()
                }
                waitFor { model.state.value.workStatus == ProcessingStatus.QUEUED }
                main {
                    model.leave()
                    stores.last().clear()
                }
                assertEquals(
                    ProcessingStatus.QUEUED,
                    jobs.records.value[ProcessingKind.SLIDESHOW]!!.status,
                )
                gate.complete(Unit)
                waitFor {
                    jobs.records.value[ProcessingKind.SLIDESHOW]?.status ==
                        ProcessingStatus.SUCCEEDED
                }
                lateinit var restored: SlideshowViewModel
                main {
                    restored = own(SlideshowViewModel(context.applicationContext as Application))
                }
                waitFor { !restored.state.value.busy && restored.state.value.result != null }
                assertEquals(500L, restored.state.value.timeline.durationMs)
                assertEquals(640, restored.state.value.result!!.info.width)
                main {
                    restored.reset()
                    assertTrue(restored.awaitSession())
                }
            } finally {
                gate.complete(Unit)
                main {
                    jobs.records.value[ProcessingKind.VIDEO]?.output?.let {
                        runCatching {
                            it.resolve(File(context.noBackupFilesDir, ProcessingKind.VIDEO.folder))
                                .delete()
                        }
                    }
                    jobs.forget(ProcessingKind.VIDEO)
                }
            }
        }

    @Test
    fun fullHdPresentationKeepsTheRequestedResolutionAndRejectsTextOverflow() =
        runBlocking<Unit>(Dispatchers.IO) {
            val engine = engine
            val project =
                StudioProject(
                    "hd",
                    ProjectKind.PRESENTATION,
                    "Slides",
                    0,
                    listOf(Slide("İstanbul العربية 日本語", "A clear plan", id = "slide")),
                    theme = PresentationTheme.WARM,
                )
            val imported = engine.importPresentation(project) {}
            for (fit in VideoFit.entries) {
                val portrait = imported.copy(canvas = VideoCanvas.PORTRAIT_9_16, fit = fit)
                val bitmap = engine.preview(portrait, 0, 1920)
                try {
                    assertEquals(1080, bitmap.width)
                    assertEquals(1920, bitmap.height)
                    assertEquals(
                        if (fit == VideoFit.FIT) Color.BLACK else project.theme.palette.background,
                        bitmap.getPixel(2, 2),
                    )
                } finally {
                    bitmap.recycle()
                }
            }
            val value =
                imported.copy(
                    edge = 1920,
                    quality = VideoQuality.HIGH,
                    frames = imported.frames.map { it.copy(durationMs = 500) },
                )
            val result = engine.create(value) {}
            try {
                assertEquals(1920, result.info.width)
                assertEquals(1080, result.info.height)
            } finally {
                result.file.delete()
                result.info.poster?.recycle()
            }
            val overflow =
                value.copy(
                    frames =
                        value.frames.map {
                            it.copy(slide = it.slide!!.copy(body = "Long paragraph ".repeat(500)))
                        }
                )
            val before = engine.folder.listFiles()!!.toSet()
            try {
                engine.create(overflow) {}
                fail("Slide text must not be silently clipped")
            } catch (_: PdfTextOverflow) {}
            assertEquals(before, engine.folder.listFiles()!!.toSet())
        }

    @Test
    fun failedCheckpointBlocksEncodingAndRetirementUntilExplicitRetry() =
        runBlocking<Unit> {
            val storage = Failing(AtomicMediaSession(engine.folder))
            lateinit var model: SlideshowViewModel
            main {
                model = own(SlideshowViewModel(context.applicationContext as Application, storage))
            }
            waitFor { !model.state.value.busy }
            main { model.add(listOf(image("disk", Color.YELLOW))) }
            waitFor { !model.state.value.busy }
            main {
                assertTrue(model.awaitSession())
                storage.fail = true
                model.duration(model.state.value.timeline.frames.first().id, 750)
                model.options(edge = 640)
            }
            waitFor { model.state.value.supportReady }
            main { model.generate() }
            waitFor { !model.state.value.busy }
            assertNull(model.state.value.result)
            assertEquals(ProcessingStatus.FAILED, model.state.value.workStatus)
            val sources = engine.files(model.state.value.timeline)
            main {
                model.remove(model.state.value.timeline.frames.first().id)
                assertFalse(model.awaitSession())
            }
            assertTrue(sources.all { it.exists() })
            main {
                storage.fail = false
                model.retrySession()
                assertTrue(model.awaitSession())
            }
            assertTrue(sources.none { it.exists() })
        }
}

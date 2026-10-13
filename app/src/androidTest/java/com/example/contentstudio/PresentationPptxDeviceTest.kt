package com.example.contentstudio

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.*
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.example.contentstudio.data.*
import com.example.contentstudio.media.*
import com.example.contentstudio.processing.*
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*

class PresentationPptxDeviceTest {
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val app
        get() = context.applicationContext as Application

    private val engine
        get() = PresentationPptxEngine(context)

    private val source
        get() = File(context.noBackupFilesDir, "pptx-qa-source.png")

    private val project
        get() =
            StudioProject(
                "pptx-device",
                ProjectKind.PRESENTATION,
                "Unicode deck",
                1,
                listOf(
                    Slide(
                        "Türkçe & English",
                        "İstanbul <text> — 123.45",
                        imageUri = Uri.fromFile(source).toString(),
                        id = "tr",
                        notes = "Private: <secret> & \"quoted\"\nLine 2 😀",
                    ),
                    Slide(
                        "العربية / فارسی",
                        "مرحبا بالعالم\nآزمایش متن",
                        id = "rtl",
                        layout = SlideLayout.IMAGE_LEFT,
                        notes = "ملاحظات خاصة",
                    ),
                    Slide(
                        "日本語 / 中文 / 한국어",
                        "日本語の段落\n中文内容\n한국어 문장",
                        id = "cjk",
                        layout = SlideLayout.IMAGE_TOP,
                        notes = "秘密のメモ",
                    ),
                    Slide("हिन्दी", "यह एक परीक्षण है", id = "hi", notes = "निजी नोट्स"),
                ),
            )

    @Before
    fun before() = runBlocking {
        withContext(Dispatchers.Main) {
            ProcessingJobs.get(app).awaitReady()
            ProcessingJobs.get(app).forget(ProcessingKind.PRESENTATION_PPTX)
        }
        val marker = Bitmap.createBitmap(160, 100, Bitmap.Config.ARGB_8888)
        try {
            marker.eraseColor(android.graphics.Color.WHITE)
            val canvas = android.graphics.Canvas(marker)
            val paint = android.graphics.Paint()
            paint.color = 0xFFF15D58.toInt()
            canvas.drawRect(0f, 0f, 40f, 40f, paint)
            paint.color = 0xFF0B1C3F.toInt()
            canvas.drawRect(120f, 0f, 160f, 40f, paint)
            paint.color = 0xFF64DCC8.toInt()
            canvas.drawRect(0f, 60f, 40f, 100f, paint)
            source.outputStream().use { check(marker.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally {
            marker.recycle()
        }
    }

    @After
    fun after() =
        runBlocking<Unit> {
            withContext(Dispatchers.Main) {
                ProcessingJobs.get(app).forget(ProcessingKind.PRESENTATION_PPTX)
            }
            source.delete()
        }

    @Test
    fun productionPackagesPreservePixelsAtEveryResolutionAndNotesAreExplicit() = runBlocking {
        val evidence = File(context.getExternalFilesDir(null), "qa/pptx-export").apply { mkdirs() }
        for ((i, edge) in listOf(1024, 2048, 4096).withIndex()) for (notes in listOf(false, true)) {
            val actualProject = project.copy(theme = PresentationTheme.entries[i])
            val options = PptxOptions(edge, notes)
            val progress = mutableListOf<Int>()
            val result = engine.create(actualProject, options) { progress += it }
            try {
                assertEquals(listOf(25, 50, 75, 100), progress)
                assertEquals(result.file.length(), result.bytes)
                assertEquals(4, result.pages)
                ZipFile(result.file).use { zip ->
                    assertEquals(notes, zip.getEntry("ppt/notesSlides/notesSlide1.xml") != null)
                    for (index in 0..3) {
                        val bitmap =
                            zip.getInputStream(zip.getEntry("ppt/media/image${index + 1}.png"))
                                .use { BitmapFactory.decodeStream(it) }
                        val expected =
                            PresentationPdf(context)
                                .renderSlideBitmap(actualProject, index, edge, options.height)
                        try {
                            assertEquals(edge, bitmap.width)
                            assertEquals(options.height, bitmap.height)
                            assertTrue(expected.sameAs(bitmap))
                        } finally {
                            bitmap.recycle()
                            expected.recycle()
                        }
                    }
                }
                val preview = engine.preview(result, 1)
                try {
                    assertEquals(512, preview.width)
                    assertEquals(288, preview.height)
                } finally {
                    preview.recycle()
                }
                val target = File(evidence, "edge-$edge-notes-$notes.pptx")
                engine.export(actualProject, result, Uri.fromFile(target))
                assertEquals(result.hash, engine.digest(target))
                val shared =
                    MediaOutputs(context)
                        .stage(result.file, PresentationPptxEngine.MIME, "slides.pptx")
                assertEquals(PresentationPptxEngine.MIME, shared.mimeType)
                assertEquals(result.bytes, shared.bytes)
                assertTrue(shared.name.endsWith(".pptx"))
                assertEquals(PresentationPptxEngine.MIME, shared.viewIntent().type)
                assertEquals(PresentationPptxEngine.MIME, shared.shareIntent().type)
            } finally {
                engine.discard(result.file)
            }
        }
        File(evidence, "expected-project.json").writeText(ProjectCodec.encode(listOf(project)))
    }

    @Test
    fun cancellationOverflowCorruptionAndSourceOverwriteNeverSaveBadOutput() = runBlocking {
        val before = engine.folder.listFiles().orEmpty().map { it.name }.toSet()
        try {
            engine.create(project, PptxOptions(1024)) {
                throw CancellationException("Test cancellation")
            }
            fail("Cancellation expected")
        } catch (_: CancellationException) {}
        assertEquals(before, engine.folder.listFiles().orEmpty().map { it.name }.toSet())
        try {
            engine.create(
                project.copy(slides = listOf(Slide("Heading", "Long line\n".repeat(600)))),
                PptxOptions(),
            )
            fail("Overflow expected")
        } catch (_: PdfTextOverflow) {}
        assertEquals(before, engine.folder.listFiles().orEmpty().map { it.name }.toSet())
        val result = engine.create(project, PptxOptions(1024))
        val source = File(engine.folder, "source-preserved.txt").apply { writeText("original") }
        try {
            try {
                engine.export(
                    project.copy(sourceUri = Uri.fromFile(source).toString()),
                    result,
                    Uri.fromFile(source),
                )
                fail("Source overwrite expected")
            } catch (_: IllegalArgumentException) {}
            assertEquals("original", source.readText())
            java.io.RandomAccessFile(result.file, "rw").use {
                it.seek(20)
                it.write(0x71)
            }
            try {
                engine.restore(result.file, result.pages, result.bytes, PptxOptions(1024))
                fail("Digest mismatch expected")
            } catch (_: IllegalArgumentException) {}
            val destination = File(engine.folder, "must-not-exist.pptx")
            try {
                engine.export(project, result, Uri.fromFile(destination))
                fail("Corruption expected")
            } catch (_: IllegalArgumentException) {}
            assertFalse(destination.exists())
        } finally {
            engine.discard(result.file)
            source.delete()
        }
    }

    @Test
    fun foregroundResultRestoresWithoutViewModelAndEditsInvalidateOnlyRelevantContent() =
        runBlocking {
            var scenario = ActivityScenario.launch(MainActivity::class.java)
            var model: PresentationPptxViewModel? = null
            scenario.onActivity {
                model = ViewModelProvider(it)[PresentationPptxViewModel::class.java]
            }
            var value = requireNotNull(model)
            try {
                withContext(Dispatchers.Main) {
                    value.prepare(project)
                    value.options(PptxOptions(1024))
                    value.generate()
                }
                withTimeout(30000) { value.state.first { !it.busy && it.result != null } }
                val first = requireNotNull(value.state.value.result)
                withContext(Dispatchers.Main) {
                    value.prepare(
                        project.copy(
                            updatedAt = 999,
                            slides = project.slides.map { it.copy(notes = "Edited private notes") },
                        )
                    )
                }
                assertEquals(first.file, value.state.value.result?.file)
                withContext(Dispatchers.Main) { assertTrue(value.awaitSession()) }
                scenario.close()
                scenario = ActivityScenario.launch(MainActivity::class.java)
                scenario.onActivity {
                    model = ViewModelProvider(it)[PresentationPptxViewModel::class.java]
                }
                value = requireNotNull(model)
                withContext(Dispatchers.Main) { value.prepare(project) }
                withTimeout(30000) { value.state.first { !it.busy && it.result != null } }
                assertEquals(first.file, value.state.value.result?.file)
                assertEquals(PptxOptions(1024), value.state.value.options)
                withContext(Dispatchers.Main) { value.options(PptxOptions(1024, true)) }
                assertNull(value.state.value.result)
                withContext(Dispatchers.Main) { value.generate() }
                withTimeout(30000) { value.state.first { !it.busy && it.result != null } }
                withContext(Dispatchers.Main) {
                    value.prepare(
                        project.copy(
                            slides =
                                project.slides.map { it.copy(notes = "Changed included notes") }
                        )
                    )
                }
                assertNull(value.state.value.result)
                val destination = File(engine.folder, "stale-save.pptx")
                withContext(Dispatchers.Main) {
                    value.export(Uri.fromFile(destination), first.file.path)
                }
                assertFalse(destination.exists())
                assertEquals(SlideImageMessage.EXPORT_ERROR, value.state.value.message)
            } finally {
                scenario.close()
            }
        }

    @Test
    fun pptxForegroundJobSurvivesActivityDestructionAndUserCanCancelIt() = runBlocking {
        var scenario = ActivityScenario.launch(MainActivity::class.java)
        var model: PresentationPptxViewModel? = null
        scenario.onActivity { model = ViewModelProvider(it)[PresentationPptxViewModel::class.java] }
        var value = requireNotNull(model)
        val jobs = ProcessingJobs.get(app)
        try {
            withContext(Dispatchers.Main) {
                value.prepare(project)
                value.options(PptxOptions(4096))
                assertTrue(value.awaitSession())
                value.generate()
            }
            assertTrue(value.state.value.busy)
            scenario.close()
            withTimeout(30000) {
                jobs.records.first {
                    it[ProcessingKind.PRESENTATION_PPTX]?.status == ProcessingStatus.SUCCEEDED
                }
            }
            scenario = ActivityScenario.launch(MainActivity::class.java)
            scenario.onActivity {
                model = ViewModelProvider(it)[PresentationPptxViewModel::class.java]
            }
            value = requireNotNull(model)
            withContext(Dispatchers.Main) { value.prepare(project) }
            assertNotNull(value.state.value.result)
            withContext(Dispatchers.Main) {
                value.options(PptxOptions(2048))
                value.generate()
                value.cancel()
            }
            withTimeout(30000) {
                jobs.records.first {
                    it[ProcessingKind.PRESENTATION_PPTX]?.status == ProcessingStatus.CANCELLED
                }
            }
            withTimeout(10000) { value.state.first { !it.busy } }
            assertNull(value.state.value.result)
        } finally {
            scenario.close()
        }
    }
}

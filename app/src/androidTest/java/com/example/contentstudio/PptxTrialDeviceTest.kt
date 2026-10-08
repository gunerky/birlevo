package com.example.contentstudio

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.example.contentstudio.data.*
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PptxTrialDeviceTest {
    private val instrumentation
        get() = InstrumentationRegistry.getInstrumentation()

    private val context
        get() = instrumentation.targetContext

    private val folder
        get() = File(context.getExternalFilesDir(null), "qa/pptx-trial").apply { mkdirs() }

    private fun fixture(): StudioProject {
        val image = File(folder, "source.png")
        val bitmap = Bitmap.createBitmap(160, 100, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            val paint = Paint().apply { color = Color.rgb(241, 93, 88) }
            canvas.drawRect(0f, 0f, 40f, 40f, paint)
            paint.color = Color.rgb(11, 28, 63)
            canvas.drawRect(120f, 0f, 160f, 40f, paint)
            paint.color = Color.rgb(100, 220, 200)
            canvas.drawRect(0f, 60f, 40f, 100f, paint)
            image.outputStream().use {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally {
            bitmap.recycle()
        }
        val languages =
            listOf(
                "Türkçe & English" to "İstanbul, ölçü, dönüşüm <test>",
                "العربية / فارسی" to "مرحبا بالعالم\nآزمایش متن و تصویر",
                "日本語 / 中文 / 한국어" to "日本語の段落\n中文内容\n한국어 문장",
                "हिन्दी" to "नमस्ते दुनिया",
            )
        return StudioProject(
            "pptx-trial",
            ProjectKind.PRESENTATION,
            "Birlevo trial",
            1,
            languages.mapIndexed { i, (heading, body) ->
                Slide(
                    heading,
                    body,
                    Uri.fromFile(image).toString(),
                    "slide-$i",
                    SlideLayout.entries[i % 3],
                    "PRIVATE NOTES $i\n$heading",
                )
            },
        )
    }

    @Test
    fun nativeExportsKeepOrderUnicodeNotesImageBytesAndAllThemes() =
        runBlocking<Unit> {
            val project = fixture()
            val original = File(folder, "source.png").readBytes()
            val exporter = PptxTrialExporter(context, instrumentation.context)
            File(folder, "expected-project.json")
                .writeText(ProjectCodec.encode(listOf(project)), Charsets.UTF_8)
            val measurements = JSONArray()
            for (theme in PresentationTheme.entries) {
                for (mode in PptxTrialExporter.Mode.entries) {
                    val file =
                        File(folder, "${theme.name.lowercase()}-${mode.name.lowercase()}.pptx")
                    val start = android.os.SystemClock.elapsedRealtime()
                    exporter.export(project.copy(theme = theme), mode, file)
                    measurements.put(
                        JSONObject()
                            .put("file", file.name)
                            .put("milliseconds", android.os.SystemClock.elapsedRealtime() - start)
                            .put("bytes", file.length())
                    )
                    ZipFile(file).use { zip ->
                        for ((index, slide) in project.slides.withIndex()) {
                            val text =
                                zip.getInputStream(zip.getEntry("ppt/slides/slide${index + 1}.xml"))
                                    .reader(Charsets.UTF_8)
                                    .readText()
                            assertFalse(text.contains("PRIVATE NOTES"))
                            if (mode == PptxTrialExporter.Mode.EDITABLE) {
                                assertTrue(text.contains("<p:txBody>"))
                                assertArrayEquals(
                                    original,
                                    zip.getInputStream(
                                            zip.getEntry("ppt/media/image${index + 1}.png")
                                        )
                                        .readBytes(),
                                )
                            } else assertFalse(text.contains("<p:txBody>"))
                            val notes =
                                zip.getInputStream(
                                        zip.getEntry("ppt/notesSlides/notesSlide${index + 1}.xml")
                                    )
                                    .reader(Charsets.UTF_8)
                                    .readText()
                            assertTrue(notes.contains("PRIVATE NOTES $index"))
                        }
                    }
                }
            }
            File(folder, "measurements.json").writeText(measurements.toString(2), Charsets.UTF_8)
            assertArrayEquals(original, File(folder, "source.png").readBytes())
            assertEquals(
                project,
                ProjectCodec.decode(ProjectCodec.encode(listOf(project))).single(),
            )
        }

    @Test
    fun cancellationAndInvalidXmlNeverLeavePartialOutputs() =
        runBlocking<Unit> {
            val project = fixture()
            val exporter = PptxTrialExporter(context, instrumentation.context)
            val cancelled = File(folder, "cancelled.pptx")
            val job = launch {
                exporter.export(project, PptxTrialExporter.Mode.EDITABLE, cancelled) {
                    if (it == 1) cancel()
                }
            }
            job.join()
            assertTrue(job.isCancelled)
            assertFalse(cancelled.exists())
            val invalid = File(folder, "invalid.pptx")
            try {
                exporter.export(
                    project.copy(
                        slides = listOf(project.slides.first().copy(body = "bad\u0001xml"))
                    ),
                    PptxTrialExporter.Mode.EDITABLE,
                    invalid,
                )
                fail("Invalid XML must fail")
            } catch (_: IllegalArgumentException) {
                assertFalse(invalid.exists())
            }
        }
}

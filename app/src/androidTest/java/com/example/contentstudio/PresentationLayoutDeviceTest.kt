package com.example.contentstudio

import android.app.LocaleManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.LocaleList
import androidx.test.platform.app.InstrumentationRegistry
import com.example.contentstudio.data.*
import com.example.contentstudio.media.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class PresentationLayoutDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val args get() = InstrumentationRegistry.getArguments()
    private val evidence get() = File(context.getExternalFilesDir(null), "qa/presentation-layout").apply { mkdirs() }
    private val image = "content://com.example.contentstudio.test.fixtures/orientation-6.jpg"
    private fun project(slide: Slide, theme: PresentationTheme = PresentationTheme.CLASSIC) =
        StudioProject("layout-test", ProjectKind.PRESENTATION, "Birlevo · تقرير 2026", 1,
            listOf(slide), theme = theme)

    @Before fun locale() {
        context.getSystemService(LocaleManager::class.java).applicationLocales =
            LocaleList.forLanguageTags(args.getString("audit-locale") ?: "en-US")
    }

    private fun write(bitmap: Bitmap, name: String) {
        File(evidence, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun paragraphsKeepLogicalOffsetsAndIndividualRtlDirections() {
        val body = "العربية: مرحبًا بالعالم ١٢٣\nEnglish: report_2026.pdf\nفارسی: سلام دنیا\n123 / 456\nहिन्दी: नमस्ते दुनिया"
        val actual = requireNotNull(PresentationLayout.measure(project(Slide("عنوان التقرير", body)), 0, false).body)
        assertEquals(body, actual.text)
        assertEquals(body, actual.lines.joinToString("") { body.substring(it.start, it.end) })
        assertEquals(SlideParagraphDirection.RIGHT_TO_LEFT, actual.lines.first().direction)
        for ((offset, expected) in listOf(
            body.indexOf("English") to SlideParagraphDirection.LEFT_TO_RIGHT,
            body.indexOf("فارسی") to SlideParagraphDirection.RIGHT_TO_LEFT,
            body.indexOf("123 /") to SlideParagraphDirection.LEFT_TO_RIGHT,
            body.indexOf("हिन्दी") to SlideParagraphDirection.LEFT_TO_RIGHT,
        )) assertEquals(expected, actual.lines.first { offset in it.start until it.end }.direction)
        assertTrue(actual.lines.all { it.visibleEnd in it.start..it.end && it.baseline in it.top..it.bottom })
    }

    @Test fun twentyTwoScriptSamplesFitAndMeasurementsDoNotFollowUiScaleOrLanguage() {
        val samples = listOf(
            "Create together", "Birlikte üretelim — ı İ ğ ş ç ö ü", "Crear juntos", "Criar juntos",
            "Berkreasi bersama", "एक साथ बनाएँ", "لنُبدع معًا", "Создаём вместе", "一緒に作ろう",
            "Créer ensemble", "Gemeinsam gestalten", "Twórzmy razem", "함께 만들어요", "一起创作",
            "一起創作", "Creiamo insieme", "สร้างสรรค์ร่วมกัน", "Cùng sáng tạo", "با هم بسازیم",
            "Tuunde pamoja", "Mu ƙirƙira tare", "Să creăm împreună",
        )
        val report = JSONArray()
        for ((sampleIndex, sample) in samples.withIndex()) for (design in SlideLayout.entries) {
            val original = project(Slide(sample, "$sample\nreport_2026.pdf · 123", image, layout = design))
            val measured = PresentationLayout.measure(original, 0, true)
            val blocks = listOf(requireNotNull(measured.heading), requireNotNull(measured.body))
            val row = JSONObject().put("sample", sampleIndex).put("layout", design.name)
            val textRows = JSONArray()
            for (block in blocks) {
                assertEquals(block.text, block.lines.joinToString("") { block.text.substring(it.start, it.end) })
                assertTrue(block.top + block.height <= measured.geometry.textBottom)
                assertTrue(block.font.ascent < 0 && block.font.descent > 0)
                assertEquals("und", block.font.localeTag)
                assertTrue(block.lines.all { it.left >= block.left - 1f && it.right <= block.left + block.width + 1f })
                textRows.put(JSONObject().put("text", block.text).put("font", block.font.toString())
                    .put("left", block.left).put("top", block.top).put("width", block.width).put("height", block.height)
                    .put("lines", JSONArray(block.lines.map { it.toString() })))
            }
            row.put("text", textRows); report.put(row)
        }
        File(evidence, "measurements-${args.getString("audit-layout") ?: "phone"}.json").writeText(report.toString(2))
    }

    @Test fun mixedScriptPreviewPdfAndLosslessImagesUseTheSameLayout() = runBlocking(Dispatchers.IO) {
        val source = project(Slide("العربية / فارسی 2026", "مرحبًا بالعالم · سلام دنیا\n日本語 中文 한국어 · हिन्दी ไทย\nİstanbul — report.pdf", image))
        val painter = PresentationPdf(context)
        val images = PresentationImageEngine(context)
        for (theme in PresentationTheme.entries) for (design in SlideLayout.entries) {
            val current = source.copy(theme = theme, slides = source.slides.map { it.copy(layout = design) })
            val pdf = painter.create(current) {}
            try {
                val rendered = painter.render(pdf, 0)
                val preview = painter.previewSlide(current, 0)
                try { assertTrue(rendered.sameAs(preview)); write(rendered, "pdf-${theme.name}-${design.name}") }
                finally { rendered.recycle(); preview.recycle() }
            } finally { pdf.file.delete() }
            val options = SlideImageOptions(format = ImageOutputFormat.PNG, edge = 1024)
            val result = images.create(current, current.slides.single().id, options)
            try {
                val decoded = BitmapFactory.decodeFile(result.file.path)
                val expected = painter.renderSlideBitmap(current, 0, options.edge, options.height)
                try {
                    // PNG may decode with different opaque/alpha storage flags. Compare actual
                    // ARGB pixels, not Bitmap.sameAs's native storage representation.
                    assertEquals(expected.width, decoded.width); assertEquals(expected.height, decoded.height)
                    write(decoded, "image-${theme.name}-${design.name}")
                    write(expected, "reference-${theme.name}-${design.name}")
                    val actualRow = IntArray(decoded.width); val expectedRow = IntArray(expected.width)
                    for (y in 0 until expected.height) {
                        decoded.getPixels(actualRow, 0, decoded.width, 0, y, decoded.width, 1)
                        expected.getPixels(expectedRow, 0, expected.width, 0, y, expected.width, 1)
                        assertArrayEquals("PNG pixels: $theme / $design / row $y", expectedRow, actualRow)
                    }
                }
                finally { decoded.recycle(); expected.recycle() }
            } finally { result.preview.recycle(); result.file.delete() }
        }
        assertEquals(SlideLayout.TEXT_IMAGE, source.slides.single().layout)
    }

    @Test fun overflowingTextFailsWithoutChangingTheProjectOrLeavingPartialFiles() = runBlocking(Dispatchers.IO) {
        val source = project(Slide("عنوان", "نص طويل جدًا ".repeat(500), image, layout = SlideLayout.IMAGE_TOP))
        val snapshot = ProjectCodec.encode(listOf(source))
        val painter = PresentationPdf(context)
        val folder = File(context.noBackupFilesDir, "presentation-pdf")
        val before = folder.listFiles()?.map { it.name }?.toSet() ?: emptySet()
        try { painter.create(source) {}; fail("Overflow must not truncate or write a success") }
        catch (error: PdfTextOverflow) { assertEquals(1, error.slideNumber) }
        try { painter.renderSlideBitmap(source, 0, 2048, 1152); fail("Bitmap output must enforce the same fit") }
        catch (error: PdfTextOverflow) { assertEquals(1, error.slideNumber) }
        assertEquals(snapshot, ProjectCodec.encode(listOf(source)))
        assertEquals(before, folder.listFiles()?.map { it.name }?.toSet() ?: emptySet<String>())
    }
}

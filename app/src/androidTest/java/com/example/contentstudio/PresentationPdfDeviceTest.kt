package com.example.contentstudio

import android.app.Activity
import android.app.Instrumentation
import android.app.LocaleManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.LocaleList
import android.os.ParcelFileDescriptor
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.contentstudio.data.*
import com.example.contentstudio.media.*
import java.io.File
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PresentationPdfDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var model: PresentationPdfViewModel
    private fun uri(name: String) = Uri.parse("content://com.example.contentstudio.test.pdf/$name")
    private val example = StudioProject("pdf-test", ProjectKind.PRESENTATION, "Dünya dilleri", 1, listOf(
        Slide("Birlikte üretelim", "Çalışma planı\nTürkçe: ı İ ğ Ğ ş Ş ç Ç ö Ö ü Ü\nSunumunu hazırla, önizle ve paylaş."),
        Slide("العربية والفارسية", "العربية: مرحبًا بالعالم\nفارسی: سلام دنیا\n123 — PDF"),
        Slide("世界の言葉", "日本語: こんにちは世界\n中文: 你好世界\n한국어: 안녕하세요\nहिन्दी: नमस्ते दुनिया\nไทย: สวัสดีชาวโลก"),
        Slide("Разные языки", "Русский: Привет, мир\nTiếng Việt: Xin chào thế giới\nHausa: Sannu duniya\nSwahili: Habari dunia\nRomână: Salut, lume")))

    @Before fun setup() {
        if (Build.VERSION.SDK_INT >= 33) context.getSystemService(LocaleManager::class.java)
            .applicationLocales = LocaleList.forLanguageTags("en-US")
        else instrumentation.runOnMainSync { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en-US")) }
        ProjectRepository(context).save(emptyList())
        context.contentResolver.delete(uri("export.pdf"), null, null)
        Intents.init()
    }
    @After fun teardown() {
        if (::model.isInitialized) instrumentation.runOnMainSync { model.reset() }
        scenario?.close()
        ProjectRepository(context).save(emptyList())
        Intents.release()
    }
    private fun launch(project: StudioProject = example) {
        ProjectRepository(context).save(listOf(project))
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.projectNode(project.title).performScrollTo().performClick()
        scenario!!.onActivity { model = ViewModelProvider(it)[PresentationPdfViewModel::class.java] }
    }
    private fun preview() {
        // Text replacement can leave the IME entering while the button scrolls.
        // Dismiss it as a user would before navigating to the full PDF preview.
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.waitForIdle()
        compose.editorNode("preview-pdf").performScrollTo().performClick()
        compose.waitUntil(20_000) { !model.state.value.busy &&
            (model.state.value.result != null || model.state.value.message != null) }
    }
    private fun waitForPage(index: Int) {
        compose.waitUntil(10_000) { !model.state.value.busy && model.state.value.pageIndex == index }
    }

    @Test fun imageOnlySlideAppearsInEditorAndPdf() {
        val image = "content://com.example.contentstudio.test.fixtures/orientation-6.jpg"
        val project = StudioProject("photo-slide", ProjectKind.PRESENTATION, "Photo slide", 1,
            listOf(Slide(imageUri = image)))
        launch(project)
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("slide-image-0").fetchSemanticsNodes().isNotEmpty() }
        compose.editorNode("choose-slide-image-0").assertExists()
        preview()
        assertEquals(1, model.state.value.result!!.pages)
        val bitmap = model.state.value.preview!!
        val redArea = bitmap.getPixel(1000, 200)
        assertTrue("The photo must be oriented and rendered into the PDF", Color.red(redArea) > 160 &&
            Color.green(redArea) < 120 && Color.blue(redArea) < 120)
        compose.onNodeWithTag("pdf-back").performClick()
        compose.onNodeWithTag("save-project").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("save-project").fetchSemanticsNodes().isEmpty() }
        compose.projectNode("Photo slide").performScrollTo().performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("slide-image-0").fetchSemanticsNodes().isNotEmpty() }
        compose.editorNode("remove-slide-image-0").performScrollTo().performClick()
        compose.onNodeWithTag("slide-image-0").assertDoesNotExist()
        preview()
        assertEquals(PdfMessage.EMPTY, model.state.value.message)
    }

    @Test fun editPreviewNavigateRecreateAndSavePreservesProject() {
        launch()
        compose.editorNode("slide-heading-0").performScrollTo().performTextReplacement("Birlikte üretelim — güncel")
        preview()
        val result = model.state.value.result!!
        assertEquals(4, result.pages)
        assertTrue(result.bytes > 1000)
        var wide = false
        scenario!!.onActivity { wide = it.window.decorView.width / it.resources.displayMetrics.density >= 700 }
        compose.onNodeWithTag(if (wide) "pdf-wide" else "pdf-compact").assertExists()
        compose.onNodeWithTag("pdf-page-preview").assertExists()
        compose.onNodeWithTag("pdf-previous").assertIsNotEnabled()
        compose.onNodeWithTag("pdf-next").performScrollTo().performClick(); waitForPage(1)
        scenario!!.recreate()
        assertEquals(result.file, model.state.value.result!!.file)
        assertEquals(1, model.state.value.pageIndex)
        compose.onNodeWithTag("pdf-next").performScrollTo().performClick(); waitForPage(2)
        compose.onNodeWithTag("pdf-next").performScrollTo().performClick(); waitForPage(3)
        compose.onNodeWithTag("pdf-next").assertIsNotEnabled()
        compose.onNodeWithTag("pdf-previous").performScrollTo().performClick(); waitForPage(2)
        Intents.intending(hasAction(Intent.ACTION_CREATE_DOCUMENT)).respondWith(
            Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri("export.pdf"))))
        compose.onNodeWithTag("save-pdf").performScrollTo().performClick()
        compose.waitUntil(15_000) { model.state.value.message == PdfMessage.EXPORTED }
        val output = File(context.getExternalFilesDir(null), "qa/presentation.pdf").apply { parentFile!!.mkdirs() }
        context.contentResolver.openInputStream(uri("export.pdf"))!!.use { input -> output.outputStream().use { input.copyTo(it) } }
        assertEquals(result.file.readBytes().toList(), output.readBytes().toList())
        val renderer = PdfRenderer(ParcelFileDescriptor.open(output, ParcelFileDescriptor.MODE_READ_ONLY))
        try {
            assertEquals(4, renderer.pageCount)
            for (index in 0 until renderer.pageCount) {
                val page = renderer.openPage(index)
                assertEquals(960, page.width); assertEquals(540, page.height)
                page.close()
            }
        } finally { renderer.close() }
        runBlocking(Dispatchers.IO) {
            val engine = PresentationPdf(context)
            for (index in 0 until result.pages) {
                val bitmap = engine.render(result, index)
                try {
                    var ink = 0
                    for (y in 135 until 630 step 3) for (x in 80 until 1200 step 3) {
                        if (Color.red(bitmap.getPixel(x, y)) < 150) ink++
                    }
                    assertTrue("Slide ${index + 1} must have text", ink > 50)
                    File(output.parentFile, "pdf-page-${index + 1}.png").outputStream().use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                } finally { bitmap.recycle() }
            }
        }
        assertEquals("Birlikte üretelim — güncel", ProjectRepository(context).load().single().slides[0].heading)
        compose.onNodeWithTag("pdf-message").performScrollTo()
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        File(output.parentFile, "pdf-preview.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        screenshot.recycle()
        Intents.intended(hasAction(Intent.ACTION_CREATE_DOCUMENT))
        compose.onNodeWithTag("pdf-back").performClick()
        compose.editorNode("slide-heading-0").performScrollTo().assertTextContains("Birlikte üretelim — güncel")
        compose.editorNode("slide-heading-0").performTextReplacement("Yeni içerik")
        preview()
        assertNotEquals(result.file, model.state.value.result!!.file)
        assertEquals("Yeni içerik", model.state.value.project!!.slides[0].heading)
        android.util.Log.i("PresentationPdfMetrics", "pages=${result.pages} bytes=${result.bytes}")
    }

    @Test fun emptyPresentationShowsActionableMessage() {
        launch(example.copy(slides = listOf(Slide())))
        preview()
        assertEquals(PdfMessage.EMPTY, model.state.value.message)
        compose.onNodeWithTag("pdf-message").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("save-pdf").assertDoesNotExist()
        compose.onNodeWithTag("pdf-back").performClick()
        compose.editorNode("slide-heading-0").performScrollTo().performTextReplacement("İlk başlık")
        preview()
        assertNotNull(model.state.value.result)
    }

    @Test fun overflowingTextIsRejectedWithoutTruncationAndCanBeFixed() {
        launch(example.copy(slides = listOf(Slide("Başlık", "Uzun metin satırı\n".repeat(60)))))
        preview()
        assertEquals(PdfMessage.OVERFLOW, model.state.value.message)
        assertEquals(1, model.state.value.overflowSlide)
        assertNull(model.state.value.result)
        compose.onNodeWithTag("save-pdf").assertDoesNotExist()
        compose.onNodeWithTag("pdf-back").performClick()
        compose.editorNode("slide-body-0").performScrollTo().performTextReplacement("Kısa ve okunaklı metin.")
        preview()
        assertNotNull(model.state.value.result)
    }

    @Test fun failedDestinationCanBeRetried() {
        launch(); preview()
        instrumentation.runOnMainSync { model.export(uri("fail.pdf")) }
        compose.waitUntil(10_000) { !model.state.value.busy && model.state.value.message == PdfMessage.EXPORT_ERROR }
        instrumentation.runOnMainSync { model.export(uri("export.pdf")) }
        compose.waitUntil(10_000) { model.state.value.message == PdfMessage.EXPORTED }
        assertNotNull(model.state.value.preview)
    }

    @Test fun newDocumentReturningBeforeRegenerationIsRemoved() {
        launch(); preview()
        context.contentResolver.openOutputStream(uri("export.pdf"), "w")!!.close()
        instrumentation.runOnMainSync {
            model.reset()
            model.prepare(example)
            model.export(uri("export.pdf"))
        }
        compose.waitUntil(15_000) { !model.state.value.busy && model.state.value.message == PdfMessage.EXPORT_ERROR }
        assertNotNull(model.state.value.result)
        compose.waitUntil(10_000) {
            try { context.contentResolver.openInputStream(uri("export.pdf"))!!.close(); false }
            catch (_: java.io.FileNotFoundException) { true }
        }
    }

    @Test fun cancellationRemovesPartialDocumentAndEngineCanRetry() = runBlocking {
        val engine = PresentationPdf(context)
        val folder = File(context.noBackupFilesDir, "presentation-pdf")
        val before = folder.listFiles()!!.map { it.name }.toSet()
        var sawPage = false
        lateinit var encoding: Job
        encoding = launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
            engine.create(example.copy(slides = List(100) { example.slides.first() })) {
                sawPage = true
                encoding.cancel()
            }
        }
        encoding.start(); encoding.join()
        assertTrue(sawPage)
        assertEquals(before, folder.listFiles()!!.map { it.name }.toSet())
        withContext(Dispatchers.IO) {
            val result = engine.create(example) { }
            try { assertEquals(4, result.pages) } finally { result.file.delete() }
        }
    }

    @Test fun tooManySlidesAreRejectedWithoutOutput() {
        launch(example.copy(slides = List(101) { example.slides.first().copy(id = "overflow-$it") }))
        preview()
        assertEquals(PdfMessage.GENERATE_ERROR, model.state.value.message)
        assertNull(model.state.value.result)
    }
}

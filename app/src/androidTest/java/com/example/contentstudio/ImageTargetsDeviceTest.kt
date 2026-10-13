package com.example.contentstudio

import android.app.Activity
import android.app.Application
import android.app.Instrumentation
import android.app.LocaleManager
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.LocaleList
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.contentstudio.media.*
import java.io.File
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImageTargetsDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun image(name: String) = Uri.parse("content://com.example.contentstudio.test.fixtures/$name")
    private fun pdf(name: String) = Uri.parse("content://com.example.contentstudio.test.pdf/$name")
    private fun bytes(uri: Uri) = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
    private fun readerNode(tag: String): SemanticsNodeInteraction = compose.workspaceNode(tag).performScrollTo()

    @Test fun bmpIsOpaqueReimportableAndRetainedWithoutChangingSourceOrBypassingSizeGuard() = runBlocking(Dispatchers.IO) {
        val engine = ImageCompressor(context)
        val original = bytes(image("tiny.png"))
        val source = engine.importImage(image("tiny.png"))
        val output = engine.compress(source, 80, 1920, ImageOutputFormat.BMP)
        var retained: Uri? = null
        try {
            assertEquals(16, output.width); assertEquals(16, output.height)
            val decoded = BitmapFactory.decodeFile(output.file.path)!!
            try { assertEquals(Color.WHITE, decoded.getPixel(0, 0)); assertEquals(Color.GREEN, decoded.getPixel(8, 8)) }
            finally { decoded.recycle() }
            assertTrue(output.bytes > source.bytes)
            try { engine.export(source, output, image("export.bmp")); fail("Large conversion needs approval") }
            catch (_: IllegalArgumentException) { }
            engine.export(source, output, image("export.bmp"), true)
            assertArrayEquals(output.file.readBytes(), bytes(image("export.bmp")))
            val staged = MediaOutputs(context).stage(output.file, "image/bmp", "result.bmp", source.bytes, true)
            retained = MediaOutputs(context).retainImage(staged.uri)
            val imported = engine.importImage(staged.uri)
            try { assertEquals("image/bmp", imported.mime); assertEquals(16, imported.width) }
            finally { imported.file.delete(); imported.preview.recycle() }
            val pages = PhotoPdf(context).importPages(listOf(staged.uri), 1, PhotoPdf.MAX_BATCH_BYTES) { }
            try { assertArrayEquals(output.file.readBytes(), pages.single().file.readBytes()) }
            finally { pages.forEach { it.file.delete() } }
            output.file.delete()
            val slide = SlideImages.decode(context, retained, 128)
            try { assertEquals(Color.GREEN, slide.getPixel(8, 8)) } finally { slide.recycle() }
            assertArrayEquals(original, bytes(image("tiny.png")))
        } finally {
            retained?.let { MediaOutputs(context).discardUnattachedImage(it) }
            output.file.delete(); output.preview.recycle(); source.file.delete(); source.preview.recycle()
        }
    }

    @Test fun pdfImageTargetsPreserveFullResolutionOrientationColorsAndOwnedTransfer() = runBlocking(Dispatchers.IO) {
        val engine = PdfReader(context)
        val original = bytes(pdf("reader.pdf"))
        val source = engine.importPdf(pdf("reader.pdf"))
        try {
            for (format in PdfReaderPolicy.imageFormats) for (edge in PdfReaderPolicy.outputEdges) {
                val result = engine.pageImage(source.source, 1, edge, format, 95)
                var retained: Uri? = null
                try {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(result.file.path, bounds)
                    assertEquals(format.mime, bounds.outMimeType)
                    assertEquals(edge / 2, bounds.outWidth); assertEquals(edge, bounds.outHeight)
                    val decoded = BitmapFactory.decodeFile(result.file.path)!!
                    try { val color = decoded.getPixel(edge / 3, edge * 3 / 4)
                        assertTrue(Color.red(color) > 240 && Color.green(color) < 15 && Color.blue(color) < 15)
                    } finally { decoded.recycle() }
                    val staged = MediaOutputs(context).stage(result.file, format.mime, "page.${format.extension}")
                    assertEquals(format.mime, staged.shareIntent().type)
                    retained = MediaOutputs(context).retainImage(staged.uri)
                    assertArrayEquals(result.file.readBytes(), bytes(retained))
                    val pages = PhotoPdf(context).importPages(listOf(staged.uri), 1, PhotoPdf.MAX_BATCH_BYTES) { }
                    try { assertArrayEquals(result.file.readBytes(), pages.single().file.readBytes()) }
                    finally { pages.forEach { it.file.delete() } }
                } finally { retained?.let { MediaOutputs(context).discardUnattachedImage(it) }; result.file.delete() }
            }
            assertArrayEquals(original, bytes(pdf("reader.pdf")))
        } finally { source.source.file.delete(); source.preview.recycle() }
    }

    @Test fun pdfPickerMimeQualityInvalidationRecreationAndLateExportAreConsistent() {
        context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags("en-US")
        Intents.init()
        var scenario: ActivityScenario<MainActivity>? = null
        var model: PdfReaderViewModel? = null
        var restored: PdfReaderViewModel? = null
        var legacy: PdfReaderViewModel? = null
        val checkpoint = SavedStateHandle()
        try {
            Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(pdf("reader.pdf"))))
            Intents.intending(hasAction(Intent.ACTION_CREATE_DOCUMENT)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(pdf("export.png"))))
            scenario = ActivityScenario.launch(MainActivity::class.java)
            compose.waitUntil(20000) { compose.onAllNodes(hasTestTag("compact-home") or hasTestTag("wide-home-tools")).fetchSemanticsNodes().isNotEmpty() }
            val title = context.getString(R.string.read_pdf)
            compose.homeNode(title).performScrollTo().performClick()
            scenario.onActivity { model = ViewModelProvider(it)[PdfReaderViewModel::class.java] }
            readerNode("choose-pdf").performClick()
            compose.waitUntil(30000) { !model!!.state.value.busy && model!!.state.value.source != null }
            for (format in PdfReaderPolicy.imageFormats) {
                readerNode("reader-format-${format.name}").performClick()
                assertNull(model!!.state.value.image)
                if (format.usesQuality) readerNode("reader-quality").assertExists()
                readerNode("reader-create-image").performClick()
                compose.waitUntil(30000) { !model!!.state.value.busy && model!!.state.value.image != null }
                val result = model!!.state.value.image!!
                assertEquals(format, result.format)
                readerNode("reader-save-image").performClick()
                compose.waitUntil(30000) { !model!!.state.value.busy && model!!.state.value.saved }
                assertArrayEquals(result.file.readBytes(), bytes(pdf("export.png")))
                Intents.intended(org.hamcrest.Matchers.allOf(hasAction(Intent.ACTION_CREATE_DOCUMENT), hasType(format.mime)))
            }
            readerNode("reader-create-image").assertIsDisplayed()
            instrumentation.waitForIdleSync()
            val screen = instrumentation.uiAutomation.takeScreenshot()
            try {
                val label = InstrumentationRegistry.getArguments().getString("layout", "phone")
                val path = File(context.getExternalFilesDir(null), "qa/image-targets-$label.png").apply { parentFile!!.mkdirs() }
                path.outputStream().use { screen.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            } finally { screen.recycle() }
            scenario.recreate()
            scenario.onActivity { model = ViewModelProvider(it)[PdfReaderViewModel::class.java] }
            assertEquals(ImageOutputFormat.WEBP, model!!.state.value.imageFormat)
            val old = model!!.state.value.image!!.file.path
            val original = model!!.state.value.source!!.originalUri
            instrumentation.runOnMainSync { model!!.setImageOptions(quality = 40) }
            assertNull(model!!.state.value.image)
            instrumentation.runOnMainSync { model!!.export(pdf("export.png"), true, old, original.toString()) }
            assertEquals(PdfReadIssue.EXPORT, model!!.state.value.issue)
            instrumentation.runOnMainSync { model!!.export(original, true, old, original.toString()) }
            assertTrue(bytes(original).isNotEmpty())
            val source = model!!.state.value.source!!
            // Exercise a new owner using saved metadata, including old checkpoint default compatibility.
            checkpoint["pdf-reader-session"] = android.os.Bundle().apply {
                putString("file", source.file.name); putString("uri", original.toString()); putString("name", source.name)
                putLong("bytes", source.bytes); putInt("pages", source.pages); putInt("page", 0)
                putString("format", "WEBP"); putInt("quality", 40); putInt("edge", 4096)
            }
            instrumentation.runOnMainSync { restored = PdfReaderViewModel(context.applicationContext as Application, checkpoint) }
            compose.waitUntil(30000) { !restored!!.state.value.busy }
            assertEquals(ImageOutputFormat.WEBP, restored!!.state.value.imageFormat)
            assertEquals(40, restored!!.state.value.quality); assertEquals(4096, restored!!.state.value.outputEdge)
            val oldMetadata = android.os.Bundle(checkpoint.get<android.os.Bundle>("pdf-reader-session")!!).apply {
                remove("format"); remove("quality"); remove("edge")
            }
            instrumentation.runOnMainSync { legacy = PdfReaderViewModel(context.applicationContext as Application,
                SavedStateHandle(mapOf("pdf-reader-session" to oldMetadata))) }
            compose.waitUntil(30000) { !legacy!!.state.value.busy }
            assertEquals(ImageOutputFormat.PNG, legacy!!.state.value.imageFormat)
            assertEquals(80, legacy!!.state.value.quality); assertEquals(2048, legacy!!.state.value.outputEdge)
        } finally {
            instrumentation.runOnMainSync { legacy?.reset(); restored?.reset(); model?.reset() }
            scenario?.close(); Intents.release()
        }
    }
}

package com.example.contentstudio

import android.app.Activity
import android.app.Instrumentation
import android.app.LocaleManager
import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.LocaleList
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.contentstudio.media.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.Dispatchers
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhotoPdfDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun fixture(name: String) = Uri.parse("content://com.example.contentstudio.test.fixtures/$name")
    private var scenario: ActivityScenario<MainActivity>? = null
    @Before fun setup() { context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags("en-US") }
    @After fun close() {
        var model: PhotoPdfViewModel? = null
        scenario?.onActivity { model = ViewModelProvider(it)[PhotoPdfViewModel::class.java]; model!!.reset() }
        if (model != null) runBlocking { kotlinx.coroutines.withContext(Dispatchers.Main) { model!!.awaitSession() } }
        scenario?.close()
    }

    @Test fun snapshotsSurviveSourceRemovalAndPdfKeepsOrderOrientationAndAlphaBackground() = runBlocking<Unit> {
        val engine = PhotoPdf(context)
        val sourceFiles = listOf("orientation-6.jpg", "alpha.webp").map { name ->
            java.io.File(context.cacheDir, "photo-source-$name").also { file ->
                context.contentResolver.openInputStream(fixture(name))!!.use { input -> file.outputStream().use { input.copyTo(it) } }
            }
        }
        val sources = sourceFiles.map(Uri::fromFile)
        val imported = engine.importPages(sources, 100, PhotoPdf.MAX_BATCH_BYTES) {}
        var result: PresentationPdfResult? = null
        try {
            sourceFiles.forEach { assertTrue(it.delete()); assertFalse(it.exists()) }
            result = engine.create(imported, 2048) {}
            val preview = engine.render(result, 0)
            assertTrue(preview.height > preview.width); assertEquals(1280, preview.height); preview.recycle()
            PdfRenderer(ParcelFileDescriptor.open(result.file, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
                assertEquals(2, renderer.pageCount)
                renderer.openPage(0).use { page ->
                    assertEquals(595, page.width); assertEquals(842, page.height)
                    val bitmap = Bitmap.createBitmap(595, 842, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    // EXIF 6: original bottom-left blue rotates to top-left.
                    val color = bitmap.getPixel(160, 210)
                    assertTrue(Color.blue(color) > Color.red(color)); bitmap.recycle()
                }
                renderer.openPage(1).use { page ->
                    val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    assertEquals(Color.WHITE, bitmap.getPixel(60, 190)); bitmap.recycle()
                }
            }
            val reversed = engine.create(imported.reversed(), 4096) {}
            try { assertEquals(2, reversed.pages); assertTrue(reversed.bytes > 0) } finally { reversed.file.delete() }
        } finally { imported.forEach { it.file.delete() }; result?.file?.delete() }
    }
    @Test fun invalidBatchIsAtomicAndLimitsDoNotReplaceExistingPages() = runBlocking<Unit> {
        val engine = PhotoPdf(context)
        val folder = java.io.File(context.noBackupFilesDir, "photo-pdf")
        val before = folder.listFiles()?.map { it.name }?.toSet().orEmpty()
        try {
            engine.importPages(listOf(fixture("tiny.png"), fixture("invalid.jpg")), 100, PhotoPdf.MAX_BATCH_BYTES) {}
            fail("Invalid batch must fail")
        } catch (_: IllegalArgumentException) { }
        assertEquals(before, folder.listFiles()?.map { it.name }?.toSet().orEmpty())
        assertThrows(IllegalArgumentException::class.java) { runBlocking {
            engine.importPages(listOf(fixture("tiny.png")), 0, PhotoPdf.MAX_BATCH_BYTES) {} } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking {
            engine.importPages(listOf(fixture("tiny.png")), 100, 1) {} } }
        assertEquals(before, folder.listFiles()?.map { it.name }?.toSet().orEmpty())
    }
    @Test fun cancelledImportRemovesPartialSnapshots() = runBlocking<Unit> {
        val folder = java.io.File(context.noBackupFilesDir, "photo-pdf")
        val before = folder.listFiles()?.map { it.name }?.toSet().orEmpty()
        coroutineScope {
            val job = launch(Dispatchers.IO) { PhotoPdf(context).importPages(listOf(fixture("slow.jpg")), 100, PhotoPdf.MAX_BATCH_BYTES) {} }
            delay(100)
            job.cancelAndJoin()
        }
        assertEquals(before, folder.listFiles()?.map { it.name }?.toSet().orEmpty())
    }
    @Test fun biggerImageTransfersToPhotoPdfOnlyAfterApprovalAndSurvivesCacheCleanup() {
        Intents.init()
        var image: ImageCompressionViewModel? = null
        var photo: PhotoPdfViewModel? = null
        try {
            Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK,
                Intent().setData(fixture("tiny.png"))))
            scenario = ActivityScenario.launch(MainActivity::class.java)
            compose.waitUntil(20_000) { compose.onAllNodesWithTag("compress-image-action").fetchSemanticsNodes().isNotEmpty() }
            compose.workspaceNode("compress-image-action").performScrollTo().performClick()
            scenario!!.onActivity { image = ViewModelProvider(it)[ImageCompressionViewModel::class.java]; photo = ViewModelProvider(it)[PhotoPdfViewModel::class.java] }
            compose.workspaceNode("choose-image").performScrollTo().performClick()
            compose.waitUntil(15_000) { image!!.state.value.source != null && !image!!.state.value.busy }
            compose.workspaceNode("image-preview").performScrollTo().performClick()
            compose.waitUntil(15_000) { image!!.state.value.result != null && !image!!.state.value.busy }
            val result = image!!.state.value.result!!
            assertTrue(result.bytes >= image!!.state.value.source!!.bytes)
            val expected = result.file.readBytes()
            compose.workspaceNode("add-output-to-photo-pdf").performScrollTo().performClick()
            compose.workspaceNode("cancel-output-use").performClick()
            assertTrue(photo!!.state.value.pages.isEmpty())
            compose.workspaceNode("add-output-to-photo-pdf").performScrollTo().performClick()
            compose.workspaceNode("confirm-output-use").performClick()
            compose.waitUntil(15_000) { photo!!.state.value.pages.size == 1 && !photo!!.state.value.busy && image!!.state.value.result != null }
            java.io.File(context.cacheDir, "shared-outputs").deleteRecursively()
            assertArrayEquals(expected, photo!!.state.value.pages.single().file.readBytes())
            compose.photoNode("generate-photo-pdf").performClick()
            compose.waitUntil(15_000) { photo!!.state.value.result != null && !photo!!.state.value.busy }
            assertEquals(1, photo!!.state.value.result!!.pages)
        } finally {
            instrumentation.runOnMainSync { image?.reset(); photo?.reset() }
            runBlocking { kotlinx.coroutines.withContext(Dispatchers.Main) { photo?.awaitSession() } }
            scenario?.close(); scenario = null; Intents.release()
        }
    }
    @Test fun multiplePickerReorderPreviewSaveAndShareWorkTogether() {
        Intents.init()
        var model: PhotoPdfViewModel? = null
        try {
            val clip = ClipData.newRawUri("images", fixture("orientation-6.jpg"))
            clip.addItem(ClipData.Item(fixture("alpha.webp")))
            Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK,
                Intent().apply { clipData = clip; addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }))
            Intents.intending(hasAction(Intent.ACTION_CREATE_DOCUMENT)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK,
                Intent().setData(Uri.parse("content://com.example.contentstudio.test.pdf/export.pdf"))))
            Intents.intending(hasAction(Intent.ACTION_CHOOSER)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null))
            scenario = ActivityScenario.launch(MainActivity::class.java)
            compose.waitUntil(20_000) { compose.onAllNodesWithTag("home-new").fetchSemanticsNodes().isNotEmpty() }
            compose.homeNode("Photos to PDF").performScrollTo().performClick()
            scenario!!.onActivity { model = ViewModelProvider(it)[PhotoPdfViewModel::class.java] }
            compose.waitUntil(20_000) { !model!!.state.value.busy }
            compose.workspaceNode("choose-photo-pdf").assertIsEnabled().performScrollTo().performClick()
            compose.waitUntil(20_000) { !model!!.state.value.busy &&
                (model!!.state.value.pages.size == 2 || model!!.state.value.message != null) }
            assertEquals("Import state: ${model!!.state.value.message}", 2, model!!.state.value.pages.size)
            val original = model!!.state.value.pages.map { it.id }
            scenario!!.recreate()
            scenario!!.onActivity { model = ViewModelProvider(it)[PhotoPdfViewModel::class.java] }
            assertEquals(original, model!!.state.value.pages.map { it.id })
            compose.photoNode("photo-down-${original[0]}").performClick()
            assertEquals(original.reversed(), model!!.state.value.pages.map { it.id })
            compose.photoNode("generate-photo-pdf").performClick()
            compose.waitUntil(30_000) { model!!.state.value.result != null && !model!!.state.value.busy }
            assertEquals(2, model!!.state.value.result!!.pages)
            compose.photoNode("photo-pdf-next").performScrollTo().performClick()
            compose.waitUntil(10_000) { model!!.state.value.pageIndex == 1 && !model!!.state.value.busy }
            compose.photoNode("save-photo-pdf").performScrollTo().performClick()
            compose.waitUntil(10_000) { model!!.state.value.message == PhotoPdfMessage.EXPORTED && !model!!.state.value.busy }
            compose.photoNode("share-output").performScrollTo().performClick()
            compose.waitUntil(10_000) { Intents.getIntents().any { it.action == Intent.ACTION_CHOOSER } }
            compose.photoNode("photo-pdf-preview").performScrollTo()
            val image = instrumentation.uiAutomation.takeScreenshot()
            try {
                val file = java.io.File(context.getExternalFilesDir(null), "qa/photo-pdf.png").apply { parentFile!!.mkdirs() }
                file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } finally { image.recycle() }
            compose.photoNode("photo-remove-${original[0]}").performScrollTo().performClick()
            assertNull(model!!.state.value.result)
            assertEquals(1, model!!.state.value.pages.size)
        } finally {
            instrumentation.runOnMainSync { model?.reset() }
            runBlocking { kotlinx.coroutines.withContext(Dispatchers.Main) { model?.awaitSession() } }
            scenario?.close(); scenario = null
            Intents.release()
        }
    }
}

package com.example.contentstudio

import android.app.Activity
import android.app.Instrumentation
import android.app.LocaleManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.LocaleList
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.contentstudio.media.*
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImageCompressionDeviceTest {
    @get:Rule(order = 0) val compose = createEmptyComposeRule()
    @get:Rule(order = 1) val residentAdvertising = ResidentAdvertisingRule(compose)
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var model: ImageCompressionViewModel
    private fun uri(name: String) = Uri.parse("content://com.example.contentstudio.test.fixtures/$name")
    private fun hash(uri: Uri): List<Byte> = context.contentResolver.openInputStream(uri)!!.use {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(65536)
        while (true) { val size = it.read(buffer); if (size < 0) break; digest.update(buffer, 0, size) }
        digest.digest().toList()
    }

    @Before fun setup() {
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags("en-US")
        } else instrumentation.runOnMainSync {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en-US"))
        }
        context.contentResolver.delete(uri("export.jpg"), null, null)
        Intents.init()
    }
    @After fun teardown() {
        if (::model.isInitialized) instrumentation.runOnMainSync { model.reset() }
        scenario?.close()
        Intents.release()
    }

    private fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("compress-image-action").fetchSemanticsNodes().isNotEmpty() }
        compose.workspaceNode("compress-image-action").performScrollTo().performClick()
        scenario!!.onActivity { model = ViewModelProvider(it)[ImageCompressionViewModel::class.java] }
    }
    private fun choose(name: String) {
        Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(Instrumentation.ActivityResult(
            Activity.RESULT_OK, Intent().setData(uri(name)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)))
        compose.workspaceNode("choose-image").performScrollTo().performClick()
        compose.waitUntil(20_000) { model.state.value.source != null && !model.state.value.busy }
    }
    private fun preview() {
        compose.workspaceNode("image-preview").performScrollTo().performClick()
        compose.waitUntil(20_000) { model.state.value.result != null && !model.state.value.busy }
    }

    @Test fun selectAdjustPreviewRecreateAndExportPreservesOriginal() {
        val originalHash = hash(uri("noise.jpg"))
        launch()
        choose("noise.jpg")
        compose.workspaceNode("image-quality").performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(60f) }
        compose.workspaceNode("edge-1280").performScrollTo().performClick()
        preview()
        val result = model.state.value.result!!
        assertEquals(60, model.state.value.quality)
        assertEquals(1280, result.width)
        assertEquals(960, result.height)
        assertTrue(result.bytes < model.state.value.source!!.bytes)
        scenario!!.recreate()
        assertEquals(result.file, model.state.value.result!!.file)
        compose.workspaceNode("size-change").performScrollTo().assertIsDisplayed()
        Intents.intending(hasAction(Intent.ACTION_CREATE_DOCUMENT)).respondWith(
            Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri("export.jpg"))))
        compose.workspaceNode("export-image").performScrollTo().performClick()
        compose.waitUntil(10_000) { model.state.value.message == CompressionMessage.EXPORTED }
        context.contentResolver.openInputStream(uri("export.jpg"))!!.use {
            val bitmap = BitmapFactory.decodeStream(it)!!
            assertEquals(1280, bitmap.width); assertEquals(960, bitmap.height); bitmap.recycle()
        }
        assertEquals(originalHash, hash(uri("noise.jpg")))
        Intents.intended(hasAction(Intent.ACTION_OPEN_DOCUMENT))
        Intents.intended(hasAction(Intent.ACTION_CREATE_DOCUMENT))
        android.util.Log.i("CompressionMetrics", "source=${model.state.value.source!!.bytes} output=${result.bytes} encodeMs=${result.elapsedMs}")
        val memory = android.os.Debug.MemoryInfo()
        android.os.Debug.getMemoryInfo(memory)
        android.util.Log.i("CompressionMetrics", "postExportPssKb=${memory.totalPss}")
        compose.workspaceNode("compression-message").performScrollTo()
        val file = File(context.getExternalFilesDir(null), "qa/compression.png").apply { parentFile!!.mkdirs() }
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        file.outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }; screenshot.recycle()
        // Changing settings must invalidate the old output, rather than saving an outdated preview.
        compose.workspaceNode("edge-1920").performScrollTo().performClick()
        compose.workspaceNode("export-image").assertDoesNotExist()
        assertNull(model.state.value.result)
    }

    @Test fun transparentPngBecomesWhiteAndLargerOutputIsReported() {
        launch(); choose("tiny.png"); preview()
        val state = model.state.value
        assertTrue(state.result!!.bytes > state.source!!.bytes)
        val bitmap = BitmapFactory.decodeFile(state.result.file.path)!!
        assertTrue(Color.red(bitmap.getPixel(0, 0)) > 240)
        assertTrue(Color.green(bitmap.getPixel(0, 0)) > 240)
        assertTrue(Color.blue(bitmap.getPixel(0, 0)) > 240)
        bitmap.recycle()
        compose.onNodeWithText("This output is not smaller. Try lower quality or resolution.").performScrollTo().assertIsDisplayed()
        compose.workspaceNode("export-image").assertDoesNotExist()
        compose.workspaceNode("size-change").assertTextContains("Size increase", substring = true)
        compose.workspaceNode("export-image-anyway").performScrollTo().performClick()
        compose.workspaceNode("export-image-keep-original").performClick()
        compose.workspaceNode("export-image-confirm").assertDoesNotExist()
        instrumentation.runOnMainSync { model.export(uri("export.jpg")) }
        compose.waitUntil(10_000) { !model.state.value.busy && model.state.value.message == CompressionMessage.EXPORT_ERROR }
        assertThrows(java.io.FileNotFoundException::class.java) {
            context.contentResolver.openInputStream(uri("export.jpg"))?.close()
        }
        Intents.intending(hasAction(Intent.ACTION_CREATE_DOCUMENT)).respondWith(
            Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri("export.jpg"))))
        compose.workspaceNode("export-image-anyway").performScrollTo().performClick()
        scenario!!.recreate()
        compose.workspaceNode("export-image-confirm").performClick()
        compose.waitUntil(10_000) { model.state.value.message == CompressionMessage.EXPORTED }
        assertEquals(hash(Uri.fromFile(state.result.file)), hash(uri("export.jpg")))
    }

    @Test fun allExifOrientationsAreNormalizedAndPrivateMetadataRemoved() = runBlocking {
        val expected = listOf(
            listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW),
            listOf(Color.GREEN, Color.RED, Color.YELLOW, Color.BLUE),
            listOf(Color.YELLOW, Color.BLUE, Color.GREEN, Color.RED),
            listOf(Color.BLUE, Color.YELLOW, Color.RED, Color.GREEN),
            listOf(Color.RED, Color.BLUE, Color.GREEN, Color.YELLOW),
            listOf(Color.BLUE, Color.RED, Color.YELLOW, Color.GREEN),
            listOf(Color.YELLOW, Color.GREEN, Color.BLUE, Color.RED),
            listOf(Color.GREEN, Color.YELLOW, Color.RED, Color.BLUE))
        val engine = ImageCompressor(context)
        for (orientation in 1..8) {
            val inputUri = uri("orientation-$orientation.jpg")
            val original = hash(inputUri)
            val source = engine.importImage(inputUri)
            val result = engine.compress(source, 95, 1280)
            try {
                val bitmap = BitmapFactory.decodeFile(result.file.path)!!
                assertEquals(if (orientation < 5) 120 else 80, bitmap.width)
                assertEquals(if (orientation < 5) 80 else 120, bitmap.height)
                val colors = listOf(bitmap.getPixel(10, 10), bitmap.getPixel(bitmap.width - 11, 10),
                    bitmap.getPixel(10, bitmap.height - 11), bitmap.getPixel(bitmap.width - 11, bitmap.height - 11))
                colors.zip(expected[orientation - 1]).forEach { (actual, wanted) ->
                    assertTrue("orientation $orientation", kotlin.math.abs(Color.red(actual) - Color.red(wanted)) < 30 &&
                        kotlin.math.abs(Color.green(actual) - Color.green(wanted)) < 30 &&
                        kotlin.math.abs(Color.blue(actual) - Color.blue(wanted)) < 30)
                }
                bitmap.recycle()
                val exif = ExifInterface(result.file)
                assertNull(exif.getAttribute(ExifInterface.TAG_ARTIST))
                assertNull(exif.latLong)
                assertEquals(original, hash(inputUri))
            } finally { source.file.delete(); result.file.delete() }
        }
    }

    @Test fun invalidInputShowsErrorWithoutExport() {
        launch()
        Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(Instrumentation.ActivityResult(
            Activity.RESULT_OK, Intent().setData(uri("invalid.jpg"))))
        compose.workspaceNode("choose-image").performClick()
        compose.waitUntil(10_000) { model.state.value.message == CompressionMessage.IMAGE_ERROR }
        compose.workspaceNode("compression-message").performScrollTo().assertIsDisplayed()
        compose.workspaceNode("export-image").assertDoesNotExist()
    }

    @Test fun cancellingImportPreservesPreviousImageAndRemovesPartialFile() {
        launch(); choose("tiny.png"); preview()
        val previous = model.state.value
        val folder = File(context.noBackupFilesDir, "image-compression")
        val before = folder.listFiles()!!.map { it.name }.toSet()
        instrumentation.runOnMainSync { model.load(uri("slow.jpg")) }
        compose.waitUntil(10_000) {
            folder.listFiles()!!.any { it.name !in before && it.length() > 0 }
        }
        instrumentation.runOnMainSync { model.cancel() }
        compose.waitUntil(10_000) { !model.state.value.busy }
        assertEquals(previous.source, model.state.value.source)
        assertEquals(previous.result, model.state.value.result)
        assertEquals(before, folder.listFiles()!!.map { it.name }.toSet())
    }

    @Test fun originalDestinationIsRejectedAndSaveFailureCanBeRetried() {
        launch(); choose("tiny.png"); preview()
        val original = hash(uri("tiny.png"))
        instrumentation.runOnMainSync { model.export(uri("tiny.png")) }
        compose.waitUntil(10_000) { model.state.value.message == CompressionMessage.EXPORT_ERROR }
        assertEquals(original, hash(uri("tiny.png")))
        instrumentation.runOnMainSync { assertTrue(model.confirmNonSmallerCopy(model.state.value.result!!.file)) }
        instrumentation.runOnMainSync { model.export(uri("fail.jpg")) }
        compose.waitUntil(10_000) { !model.state.value.busy && model.state.value.message == CompressionMessage.EXPORT_ERROR }
        instrumentation.runOnMainSync { model.export(uri("export.jpg")) }
        compose.waitUntil(10_000) { model.state.value.message == CompressionMessage.EXPORTED }
        assertEquals(original, hash(uri("tiny.png")))
    }

    @Test fun cancelledFailedAndObsoleteDestinationsCannotSaveAnotherResult() {
        val original = hash(uri("noise.jpg"))
        launch(); choose("noise.jpg"); preview()
        val first = model.state.value.result!!
        Intents.intending(hasAction(Intent.ACTION_CREATE_DOCUMENT)).respondWith(
            Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null))
        compose.workspaceNode("export-image").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(first, model.state.value.result)
        assertThrows(java.io.FileNotFoundException::class.java) {
            context.contentResolver.openInputStream(uri("export.jpg"))?.close()
        }
        Intents.intending(hasAction(Intent.ACTION_CREATE_DOCUMENT)).respondWith(
            Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri("fail.jpg"))))
        compose.workspaceNode("export-image").performScrollTo().performClick()
        compose.waitUntil(10_000) { !model.state.value.busy && model.state.value.message == CompressionMessage.EXPORT_ERROR }
        assertEquals(first, model.state.value.result)
        instrumentation.runOnMainSync { model.setOptions(quality = 65); model.preview() }
        compose.waitUntil(20_000) { !model.state.value.busy && model.state.value.result != null }
        val second = model.state.value.result!!
        assertNotEquals(first.file, second.file)
        instrumentation.runOnMainSync { model.export(uri("export.jpg"), first.file) }
        compose.waitForIdle()
        assertEquals(CompressionMessage.EXPORT_ERROR, model.state.value.message)
        assertEquals(second, model.state.value.result)
        assertThrows(java.io.FileNotFoundException::class.java) {
            context.contentResolver.openInputStream(uri("export.jpg"))?.close()
        }
        // The current result remains usable after rejecting the stale destination.
        Intents.intending(hasAction(Intent.ACTION_CREATE_DOCUMENT)).respondWith(
            Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri("export.jpg"))))
        compose.workspaceNode("export-image").performScrollTo().performClick()
        compose.waitUntil(10_000) { !model.state.value.busy && model.state.value.message == CompressionMessage.EXPORTED }
        assertEquals(hash(Uri.fromFile(second.file)), hash(uri("export.jpg")))
        assertEquals(original, hash(uri("noise.jpg")))
    }

    @Test fun obsoleteAndRestoredPickersNeverDeleteTheirFormerOriginal() {
        val originalUri = Uri.parse("content://com.example.contentstudio.test.fixtures/document/tiny.png")
        val resolver = context.contentResolver
        fun resetDeletes() { resolver.call(originalUri, "reset-delete-count", null, null) }
        fun deletes() = resolver.call(originalUri, "get-delete-count", null, null)!!.getInt("count")
        resetDeletes()
        android.provider.DocumentsContract.deleteDocument(resolver, originalUri)
        assertEquals("The fixture must observe actual DocumentsContract cleanup", 1, deletes())
        resetDeletes()
        val original = hash(originalUri)
        launch()
        instrumentation.runOnMainSync { model.load(originalUri) }
        compose.waitUntil(20_000) { !model.state.value.busy && model.state.value.source != null }
        preview()
        val oldResult = model.state.value.result!!.file
        instrumentation.runOnMainSync { model.load(uri("noise.jpg")) }
        compose.waitUntil(20_000) { !model.state.value.busy && model.state.value.source?.originalUri == uri("noise.jpg") }
        preview()
        val current = model.state.value.result!!
        instrumentation.runOnMainSync { model.export(originalUri, oldResult, listOf(originalUri.toString())) }
        assertEquals(CompressionMessage.EXPORT_ERROR, model.state.value.message)
        assertEquals(current, model.state.value.result)
        val store = androidx.lifecycle.ViewModelStore()
        lateinit var restored: ImageCompressionViewModel
        instrumentation.runOnMainSync {
            restored = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(
                context.applicationContext as android.app.Application))[ImageCompressionViewModel::class.java]
            restored.export(originalUri, oldResult, listOf(originalUri.toString()))
        }
        try {
            assertEquals(CompressionMessage.EXPORT_ERROR, restored.state.value.message)
            repeat(50) { android.os.SystemClock.sleep(20); assertEquals(0, deletes()) }
            assertEquals(original, hash(originalUri))
            // An unrelated empty destination still takes the cleanup path.
            instrumentation.runOnMainSync { restored.export(uri("document/export.jpg"), oldResult, listOf(originalUri.toString())) }
            compose.waitUntil(10_000) { deletes() == 1 }
            assertEquals(original, hash(originalUri))
        } finally { instrumentation.runOnMainSync { store.clear() }; resetDeletes() }
    }
}

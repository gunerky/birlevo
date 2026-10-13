package com.example.contentstudio

import android.graphics.BitmapFactory
import android.graphics.Color
import android.app.Activity
import android.app.Instrumentation
import android.app.LocaleManager
import android.content.Intent
import android.os.LocaleList
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.contentstudio.media.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImageFormatsDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun fixture(name: String) = Uri.parse("content://com.example.contentstudio.test.fixtures/$name")

    @Test fun formatSelectionChangesPickerMimeAndInvalidatesOldOutput() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags("en-US")
        Intents.init()
        var scenario: ActivityScenario<MainActivity>? = null
        var model: ImageCompressionViewModel? = null
        try {
            Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK,
                Intent().setData(fixture("noise.jpg"))))
            for (format in listOf(ImageOutputFormat.PNG, ImageOutputFormat.WEBP, ImageOutputFormat.BMP)) {
                Intents.intending(org.hamcrest.Matchers.allOf(hasAction(Intent.ACTION_CREATE_DOCUMENT), hasType(format.mime)))
                    .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(fixture("export.${format.extension}"))))
            }
            scenario = ActivityScenario.launch(MainActivity::class.java)
            compose.waitUntil(20_000) { compose.onAllNodesWithTag("compress-image-action").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("compress-image-action").performScrollTo().performClick()
            scenario.onActivity { model = ViewModelProvider(it)[ImageCompressionViewModel::class.java] }
            compose.onNodeWithTag("choose-image").performScrollTo().performClick()
            compose.waitUntil(20_000) { model!!.state.value.source != null && !model!!.state.value.busy }
            compose.onNodeWithTag("edge-1280").performScrollTo().performClick()
            for (format in listOf(ImageOutputFormat.PNG, ImageOutputFormat.WEBP, ImageOutputFormat.BMP)) {
                compose.onNodeWithTag("image-format-${format.name}").performScrollTo().performClick()
                assertNull(model!!.state.value.result)
                if (!format.usesQuality) compose.onNodeWithTag("image-quality").assertDoesNotExist()
                else compose.onNodeWithTag("image-quality").assertExists()
                compose.onNodeWithTag("image-preview").performScrollTo().performClick()
                compose.waitUntil(30_000) { model!!.state.value.result != null && !model!!.state.value.busy }
                assertEquals(format, model!!.state.value.result!!.format)
                val benefit = compressionBenefit(model!!.state.value.source!!.bytes, model!!.state.value.result!!.bytes)
                if (benefit == CompressionBenefit.SMALLER) compose.onNodeWithTag("export-image").performScrollTo().performClick()
                else {
                    compose.onNodeWithTag("export-image-anyway").performScrollTo().performClick()
                    compose.onNodeWithTag("export-image-confirm").performClick()
                }
                compose.waitUntil(10_000) { model!!.state.value.message == CompressionMessage.EXPORTED && !model!!.state.value.busy }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(fixture("export.${format.extension}"))!!.use { BitmapFactory.decodeStream(it, null, bounds) }
                assertEquals(format.mime, bounds.outMimeType)
                Intents.intended(org.hamcrest.Matchers.allOf(hasAction(Intent.ACTION_CREATE_DOCUMENT), hasType(format.mime)))
            }
        } finally {
            instrumentation.runOnMainSync { model?.reset() }
            scenario?.close(); Intents.release()
        }
    }

    @Test fun pngAndWebpPreserveAlphaAndAreShareableOwnedSlideAssets() = runBlocking<Unit> {
        val engine = ImageCompressor(context)
        val source = engine.importImage(fixture("tiny.png"))
        try {
            for (format in listOf(ImageOutputFormat.PNG, ImageOutputFormat.WEBP)) {
                val result = engine.compress(source, 80, 1920, format)
                var owned: Uri? = null
                try {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(result.file.path, bounds)
                    assertEquals(format.mime, bounds.outMimeType)
                    assertTrue(result.file.name.endsWith(".${format.extension}"))
                    val decoded = BitmapFactory.decodeFile(result.file.path)!!
                    assertEquals(0, Color.alpha(decoded.getPixel(0, 0)))
                    assertTrue(Color.alpha(decoded.getPixel(8, 8)) > 240)
                    decoded.recycle()
                    val output = MediaOutputs(context).stage(result.file, format.mime, "converted.${format.extension}")
                    assertEquals(format.mime, output.shareIntent().type)
                    owned = MediaOutputs(context).retainImage(output.uri)
                    result.file.delete()
                    val slideBitmap = SlideImages.decode(context, owned, 128)
                    assertEquals(0, Color.alpha(slideBitmap.getPixel(0, 0))); slideBitmap.recycle()
                } finally {
                    owned?.let { MediaOutputs(context).discardUnattachedImage(it) }
                    result.file.delete()
                }
            }
        } finally { source.file.delete() }
    }
    @Test fun stillWebpImportsAndJpegRemainsWhiteWithSizeGuard() = runBlocking<Unit> {
        val engine = ImageCompressor(context)
        val source = engine.importImage(fixture("alpha.webp"))
        try {
            assertEquals(16, source.width)
            val jpeg = engine.compress(source, 80, 1920)
            try {
                val bitmap = BitmapFactory.decodeFile(jpeg.file.path)!!
                assertTrue(Color.red(bitmap.getPixel(0, 0)) > 240); bitmap.recycle()
                assertThrows(IllegalArgumentException::class.java) {
                    runBlocking { MediaOutputs(context).stage(jpeg.file, "image/jpeg", "copy.jpg", 1) }
                }
            } finally { jpeg.file.delete() }
        } finally { source.file.delete() }
    }
}

package com.example.contentstudio

import android.app.Activity
import android.app.Instrumentation
import android.app.LocaleManager
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.LocaleList
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Media workflow checks in the normal debug regression suite. The platform-only
 * ReleaseSmokeInstrumentation independently tests the exact minified release APK;
 * it does not depend on Compose/Kotlin test classes shared with the target app. */
@RunWith(AndroidJUnit4::class)
class ReleaseSmokeDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private lateinit var scenario: ActivityScenario<MainActivity>
    @Before fun setup() {
        Assume.assumeTrue("This exact-release smoke harness uses the API 33+ locale service", android.os.Build.VERSION.SDK_INT >= 33)
        context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags("en-US")
        // Dedicated QA emulator only. Clear its project database through Android's filesystem.
        java.io.File(context.filesDir, "projects.json").delete()
        Intents.init()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitTag("compress-image-action")
    }
    @After fun close() { if (::scenario.isInitialized) { scenario.close(); Intents.release() } }
    private fun waitTag(tag: String, timeout: Long = 20_000) {
        compose.waitUntil(timeout) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun choose(tag: String, uri: Uri) {
        Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(
            Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri)))
        compose.onNodeWithTag(tag).performScrollTo().performClick()
    }
    private fun save(tag: String, uri: Uri, success: String) {
        context.contentResolver.delete(uri, null, null)
        Intents.intending(hasAction(Intent.ACTION_CREATE_DOCUMENT)).respondWith(
            Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri)))
        compose.onNodeWithTag(tag).performScrollTo().performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithText(success).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun minifiedImageImportCompressAndExport() {
        compose.onNodeWithTag("compress-image-action").performScrollTo().performClick()
        choose("choose-image", Uri.parse("content://com.example.contentstudio.test.fixtures/noise.jpg"))
        waitTag("image-preview")
        compose.onNodeWithTag("edge-1280").performScrollTo().performClick()
        compose.onNodeWithTag("image-preview").performScrollTo().performClick()
        waitTag("export-image")
        val uri = Uri.parse("content://com.example.contentstudio.test.fixtures/export.jpg")
        save("export-image", uri, "New image saved")
        val bitmap = context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it)!! }
        try { assertEquals(1280, bitmap.width); assertEquals(960, bitmap.height) } finally { bitmap.recycle() }
    }

    @Test fun minifiedVideoImportProcessPlayAndExport() {
        compose.onNodeWithText("Edit video").performScrollTo().performClick()
        choose("choose-compression-video", Uri.parse("content://com.example.contentstudio.test.videos/audio.mp4"))
        waitTag("process-video")
        compose.onNodeWithTag("video-edge-640").performScrollTo().performClick()
        compose.onNodeWithTag("process-video").performScrollTo().performClick()
        waitTag("export-video", 120_000)
        compose.onNodeWithTag("play-output-video").performScrollTo().performClick()
        compose.onNodeWithTag("video-player").assertIsDisplayed()
        compose.onNodeWithTag("close-video-player").performClick()
        val uri = Uri.parse("content://com.example.contentstudio.test.videos/export.mp4")
        save("export-video", uri, "New video saved")
        val extractor = MediaExtractor()
        try {
            context.contentResolver.openFileDescriptor(uri, "r")!!.use { extractor.setDataSource(it.fileDescriptor) }
            val tracks = (0 until extractor.trackCount).map { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME) }
            assertTrue(tracks.contains("video/avc")); assertTrue(tracks.contains("audio/mp4a-latm"))
        } finally { extractor.release() }
    }

    @Test fun minifiedPresentationThemesAutosaveRecreateAndPdfExport() {
        compose.onNodeWithText("Create presentation").performScrollTo().performClick()
        compose.editorNode("project-title").performTextReplacement("Release presentation")
        compose.editorNode("slide-heading-0").performScrollTo().performTextReplacement("A clear plan")
        closeSoftKeyboard()
        compose.editorNode("presentation-theme").performScrollTo().performClick()
        compose.onNodeWithTag("theme-OCEAN").performClick()
        scenario.recreate()
        compose.editorNode("slide-heading-0").performScrollTo().assertTextContains("A clear plan")
        compose.editorNode("preview-pdf").performScrollTo().performClick()
        waitTag("save-pdf")
        val uri = Uri.parse("content://com.example.contentstudio.test.pdf/export.pdf")
        save("save-pdf", uri, "New PDF saved")
        val renderer = PdfRenderer(context.contentResolver.openFileDescriptor(uri, "r")!!)
        try { assertEquals(1, renderer.pageCount) } finally { renderer.close() }
    }
}

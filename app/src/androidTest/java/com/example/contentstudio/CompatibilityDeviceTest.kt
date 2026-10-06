package com.example.contentstudio

import android.app.Activity
import android.app.Instrumentation
import android.app.LocaleManager
import android.content.Intent
import android.net.Uri
import android.os.LocaleList
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.platform.app.InstrumentationRegistry
import com.example.contentstudio.media.*
import com.example.contentstudio.ui.appLocales
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CompatibilityDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun locale(tag: String) { context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tag) }
    private fun fixture(name: String) = Uri.parse("content://com.example.contentstudio.test.fixtures/$name")

    @Test fun supportedFilesDialogFollowsEverySelectedLanguage() {
        try {
            for (language in appLocales) {
                locale(language.tag)
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    scenario.recreate()
                    compose.waitUntil(20_000) { compose.onAllNodesWithTag("supported-files-all").fetchSemanticsNodes().isNotEmpty() }
                    var title = ""
                    var output = ""
                    var input = ""
                    scenario.onActivity {
                        assertEquals(language.tag, it.resources.configuration.locales[0].toLanguageTag())
                        title = it.getString(R.string.supported_files); output = it.getString(R.string.files_output)
                        input = it.getString(R.string.files_input)
                    }
                    compose.onNodeWithTag("supported-files-all").performScrollTo().assertTextContains(title).performClick()
                    compose.onNodeWithTag("support-IMAGE").assertExists()
                    compose.onNodeWithTag("supported-files-list").performScrollToNode(hasTestTag("support-PHOTO_PDF"))
                    compose.onNodeWithTag("support-PHOTO_PDF").onChildren().filter(hasText("$output: PDF")).assertCountEquals(1)
                    compose.onNodeWithTag("supported-files-list").performScrollToNode(hasTestTag("support-PDF_READER"))
                    compose.onNodeWithTag("support-PDF_READER").onChildren().filter(hasText("$input: PDF")).assertCountEquals(1)
                    compose.onNodeWithTag("support-PDF_READER").onChildren().filter(hasText("$output: PDF / PNG")).assertCountEquals(1)
                    compose.onNodeWithTag("support-close").performClick()
                    compose.onNodeWithTag("supported-files-list").assertDoesNotExist()
                }
            }
        } finally { locale("en-US") }
    }

    @Test fun wrongMimeGetsSpecificErrorAndRetainsExistingSource() {
        locale("en-US")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val original = context.contentResolver.openInputStream(fixture("noise.jpg"))!!.use { it.readBytes() }
        Intents.init()
        var model: ImageCompressionViewModel? = null
        try {
            Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK,
                Intent().setData(fixture("wrong-type.jpg"))))
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                compose.waitUntil(20_000) { compose.onAllNodesWithTag("compress-image-action").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("compress-image-action").performScrollTo().performClick()
                scenario.onActivity { model = ViewModelProvider(it)[ImageCompressionViewModel::class.java]; model!!.load(fixture("noise.jpg")) }
                compose.waitUntil(30_000) { model!!.state.value.source != null && !model!!.state.value.busy }
                val accepted = model!!.state.value.source!!.file
                compose.onNodeWithTag("choose-image").performScrollTo().performClick()
                compose.waitUntil(30_000) { model!!.state.value.issue == ImportIssue.IMAGE_FORMAT && !model!!.state.value.busy }
                compose.onNodeWithTag("compression-message").performScrollTo().assertTextContains(context.getString(R.string.error_image_format))
                assertEquals(accepted, model!!.state.value.source!!.file)
                assertTrue(accepted.exists())
                assertNull(model!!.state.value.result)
                assertArrayEquals(original, context.contentResolver.openInputStream(fixture("noise.jpg"))!!.use { it.readBytes() })
                scenario.onActivity { model!!.reset() }
            }
        } finally { instrumentation.runOnMainSync { model?.reset() }; Intents.release() }
    }

    @Test fun sharedImageValidationCoversSlidesAndPhotoPdf() = runBlocking {
        assertEquals(ImportIssue.IMAGE_FORMAT, assertThrows(MediaImportException::class.java) {
            SlideImages.validate(context, fixture("wrong-type.jpg"))
        }.issue)
        val folder = java.io.File(context.noBackupFilesDir, "photo-pdf")
        val before = folder.listFiles()?.map { it.name }?.toSet() ?: emptySet()
        val failure = try {
            PhotoPdf(context).importPages(listOf(fixture("noise.jpg"), fixture("wrong-type.jpg")), 100, PhotoPdf.MAX_BATCH_BYTES) {}
            null
        } catch (e: MediaImportException) { e }
        assertEquals(ImportIssue.IMAGE_FORMAT, failure?.issue)
        assertEquals(before, folder.listFiles()?.map { it.name }?.toSet() ?: emptySet<String>())
    }

    @Test fun actualVideoMetadataProducesDistinctRejectionsWithoutLeftoverCopies() = runBlocking {
        val folder = java.io.File(context.noBackupFilesDir, "video-compression")
        val before = folder.listFiles()?.map { it.name }?.toSet() ?: emptySet()
        for ((name, reason) in listOf("hdr.mp4" to ImportIssue.VIDEO_HDR, "tracks.mp4" to ImportIssue.VIDEO_TRACKS)) {
            val failure = try {
                val accepted = VideoCompressor(context).importVideo(Uri.parse("content://com.example.contentstudio.test.videos/$name"))
                accepted.file.delete(); accepted.info.poster?.recycle()
                null
            } catch (e: MediaImportException) { e }
            assertEquals(name, reason, failure?.issue)
            assertEquals(before, folder.listFiles()?.map { it.name }?.toSet() ?: emptySet<String>())
        }
    }
}

package com.example.contentstudio

import android.app.Activity
import android.app.Instrumentation
import android.app.LocaleManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.LocaleList
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
import java.security.MessageDigest
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OutputTransferDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var image: ImageCompressionViewModel
    private lateinit var editor: ProjectEditorViewModel
    private fun fixture(name: String) = Uri.parse("content://com.example.contentstudio.test.fixtures/$name")
    private fun hash(uri: Uri) = context.contentResolver.openInputStream(uri)!!.use { MessageDigest.getInstance("SHA-256").digest(it.readBytes()).toList() }

    @Before fun setup() {
        if (Build.VERSION.SDK_INT >= 33) context.getSystemService(LocaleManager::class.java)
            .applicationLocales = LocaleList.forLanguageTags("en-US")
        ProjectRepository(context).save(emptyList())
        Intents.init()
    }
    @After fun close() {
        scenario?.close()
        ProjectRepository(context).save(emptyList())
        Intents.release()
    }
    private fun launch(name: String, saved: List<StudioProject> = emptyList()) {
        ProjectRepository(context).save(saved)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("compress-image-action").fetchSemanticsNodes().isNotEmpty() }
        scenario!!.onActivity { editor = ViewModelProvider(it)[ProjectEditorViewModel::class.java] }
        compose.onNodeWithTag("compress-image-action").performScrollTo().performClick()
        scenario!!.onActivity { image = ViewModelProvider(it)[ImageCompressionViewModel::class.java] }
        Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK,
            Intent().setData(fixture(name)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)))
        compose.onNodeWithTag("choose-image").performScrollTo().performClick()
        compose.waitUntil(20_000) { image.state.value.source != null && !image.state.value.busy }
        compose.onNodeWithTag("image-preview").performScrollTo().performClick()
        compose.waitUntil(20_000) { image.state.value.result != null && !image.state.value.busy }
    }
    private fun waitForDraft() {
        compose.waitUntil(20_000) { editor.state.value.draft != null && !editor.state.value.importingImage &&
            editor.state.value.saveStatus == DraftSaveStatus.SAVED && image.state.value.result == null }
        compose.onNodeWithTag("project-title").assertExists()
    }

    @Test fun compressedImageCreatesDurablePresentationWithoutASecondPicker() {
        launch("noise.jpg")
        val result = image.state.value.result!!
        val expected = MessageDigest.getInstance("SHA-256").digest(result.file.readBytes()).toList()
        compose.onNodeWithTag("add-output-to-presentation").performScrollTo().performClick()
        compose.onNodeWithTag("output-new-presentation").performClick()
        waitForDraft()
        val draft = editor.state.value.draft!!
        assertEquals(1, draft.slides.size)
        val owned = Uri.parse(draft.slides.single().imageUri)
        assertEquals(expected, hash(owned))
        File(context.cacheDir, "shared-outputs").deleteRecursively()
        compose.onNodeWithTag("save-project").performClick()
        compose.waitUntil(15_000) { editor.state.value.draft == null }
        scenario!!.close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.projectNode(draft.title).performScrollTo().performClick()
        scenario!!.onActivity { editor = ViewModelProvider(it)[ProjectEditorViewModel::class.java] }
        assertEquals(expected, hash(Uri.parse(editor.state.value.draft!!.slides.single().imageUri)))
        val pdf = runBlocking { PresentationPdf(context).create(editor.state.value.draft!!) {} }
        assertTrue(pdf.bytes > 0); pdf.file.delete()
        Intents.intended(hasAction(Intent.ACTION_OPEN_DOCUMENT), androidx.test.espresso.intent.VerificationModes.times(1))
    }

    @Test fun transferAppendsWithoutReplacingExistingSlidesOrTheme() {
        val old = StudioProject("existing-target", ProjectKind.PRESENTATION, "Existing", 1,
            listOf(Slide("Keep heading", "Keep body", id = "kept")), theme = PresentationTheme.OCEAN)
        launch("noise.jpg", listOf(old))
        compose.onNodeWithTag("add-output-to-presentation").performScrollTo().performClick()
        compose.onNodeWithTag("output-target-${old.id}").performScrollTo().performClick()
        waitForDraft()
        val saved = ProjectRepository(context).load().single()
        assertEquals(old.id, saved.id); assertEquals(old.slides.single(), saved.slides.first())
        assertEquals(old.theme, saved.theme); assertEquals(2, saved.slides.size)
        assertNotNull(saved.slides.last().imageUri)
    }

    @Test fun biggerOutputRequiresSeparateApprovalForOpenShareAndTransfer() {
        launch("tiny.png")
        assertTrue(image.state.value.result!!.bytes > image.state.value.source!!.bytes)
        for (tag in listOf("open-output", "share-output", "add-output-to-presentation")) {
            compose.onNodeWithTag(tag).performScrollTo().performClick()
            compose.onNodeWithTag("confirm-output-use").assertIsDisplayed()
            compose.onNodeWithTag("cancel-output-use").performClick()
            assertNull(editor.state.value.draft)
        }
        compose.onNodeWithTag("add-output-to-presentation").performScrollTo().performClick()
        compose.onNodeWithTag("confirm-output-use").performClick()
        compose.onNodeWithTag("output-new-presentation").performClick()
        waitForDraft()
        assertNotNull(editor.state.value.draft!!.slides.single().imageUri)
    }

    @Test fun fullPresentationIsDisabledAndNewPresentationRemainsAvailable() {
        val full = StudioProject("full", ProjectKind.PRESENTATION, "Full presentation", 1,
            List(100) { Slide(heading = "Slide $it") })
        launch("noise.jpg", listOf(full))
        compose.onNodeWithTag("add-output-to-presentation").performScrollTo().performClick()
        compose.onNodeWithTag("output-target-full").assertIsNotEnabled()
        compose.onNodeWithTag("output-new-presentation").assertIsEnabled().performClick()
        waitForDraft()
        assertEquals(full, ProjectRepository(context).load().first { it.id == full.id })
    }

    @Test fun openAndShareUseReadableSnapshotsAndRecoverAfterCacheCleanup() {
        launch("noise.jpg")
        val expected = MessageDigest.getInstance("SHA-256").digest(image.state.value.result!!.file.readBytes()).toList()
        Intents.intending(hasAction(Intent.ACTION_CHOOSER)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null))
        Intents.intending(hasAction(Intent.ACTION_VIEW)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null))
        compose.onNodeWithTag("share-output").performScrollTo().performClick()
        compose.waitUntil(15_000) { Intents.getIntents().any { it.action == Intent.ACTION_CHOOSER } }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("output-busy").fetchSemanticsNodes().isEmpty() }
        val chooser = Intents.getIntents().last { it.action == Intent.ACTION_CHOOSER }
        val share = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals("image/jpeg", share.type)
        assertEquals(expected, hash(share.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!))
        File(context.cacheDir, "shared-outputs").deleteRecursively()
        compose.onNodeWithTag("open-output").performScrollTo().performClick()
        compose.waitUntil(15_000) { Intents.getIntents().any { it.action == Intent.ACTION_VIEW } }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("output-busy").fetchSemanticsNodes().isEmpty() }
        val open = Intents.getIntents().last { it.action == Intent.ACTION_VIEW }
        assertEquals("image/jpeg", open.type); assertEquals(expected, hash(open.data!!))
        assertEquals(open.data, open.clipData!!.getItemAt(0).uri)
        compose.onNodeWithTag("output-error").assertDoesNotExist()
    }
}

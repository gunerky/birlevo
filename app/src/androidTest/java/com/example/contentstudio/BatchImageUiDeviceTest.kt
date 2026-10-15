package com.example.contentstudio

import android.app.Activity
import android.app.Application
import android.app.Instrumentation
import android.app.LocaleManager
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.LocaleList
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.contentstudio.batch.*
import com.example.contentstudio.media.ImageOutputFormat
import com.example.contentstudio.processing.*
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BatchImageUiDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var model: BatchImageViewModel
    private fun uri(name: String) = Uri.parse("content://com.example.contentstudio.test.fixtures/$name")
    @Before fun setup() {
        context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(
            InstrumentationRegistry.getArguments().getString("audit-locale", "en-US"))
        runBlocking { withContext(Dispatchers.Main) {
            ProcessingJobs.get(context).awaitReady(); ProcessingJobs.get(context).forget(ProcessingKind.BATCH_IMAGE)
        }; withContext(Dispatchers.IO) { BatchEngine(context).clear() } }
        Intents.init()
    }
    @After fun cleanup() {
        if (::model.isInitialized) {
            instrumentation.runOnMainSync { model.cancel() }
            compose.waitUntil(20_000) { !model.state.value.busy }
        }
        scenario?.close(); Intents.release()
        runBlocking(Dispatchers.IO) { BatchEngine(context).clear() }
    }
    private fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("home-batch-tools").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("home-batch-tools").performScrollTo().performClick()
        compose.onNodeWithTag("home-batch-images").performScrollTo().performClick()
        scenario!!.onActivity { model = ViewModelProvider(it)[BatchImageViewModel::class.java] }
        scenario!!.onActivity {
            val expected = InstrumentationRegistry.getArguments().getString("audit-locale", "en-US")!!.substringBefore('-')
            assertEquals(expected, it.resources.configuration.locales[0].language)
            if (expected == "ar") assertEquals(android.view.View.LAYOUT_DIRECTION_RTL, it.resources.configuration.layoutDirection)
        }
        waitIdle()
    }
    private fun waitIdle() { compose.waitUntil(30_000) { model.state.value.ready && !model.state.value.busy } }
    private fun row(tag: String): SemanticsNodeInteraction {
        compose.onNodeWithTag("batch-list").performScrollToNode(hasTestTag(tag))
        return compose.onNodeWithTag(tag)
    }
    private fun add() {
        val clips = ClipData.newRawUri("images", uri("noise.jpg")).apply { addItem(ClipData.Item(uri("tiny.png"))) }
        Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(Instrumentation.ActivityResult(
            Activity.RESULT_OK, Intent().apply { clipData = clips; addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }))
        row("batch-add").performClick()
        compose.waitUntil(30_000) { model.state.value.batch.items.size == 2 && !model.state.value.busy }
    }
    @Test fun multiSelectOptionsPerResultConsentReviewZipSaveAndRecreation() {
        launch(); add()
        compose.onNodeWithTag("batch-panel-output").performClick()
        compose.onNodeWithTag("batch-format-BMP").performScrollTo().performClick(); waitIdle()
        compose.onNodeWithTag("batch-edge-2560").performScrollTo().performClick(); waitIdle()
        compose.onNodeWithTag("batch-panel-close").performClick()
        compose.onNodeWithTag("batch-convert").performClick()
        compose.waitUntil(40_000) { !model.state.value.busy && model.state.value.batch.items.all { it.output != null } }
        assertEquals(ImageOutputFormat.BMP, model.state.value.batch.options.format)
        assertEquals(2560, model.state.value.batch.options.edge)
        compose.onNodeWithTag("batch-zip").assertIsNotEnabled()
        for (index in 0..1) {
            assertTrue(model.state.value.batch.items[index].needsConsent)
            row("batch-allow-$index").performClick()
            compose.onNodeWithTag("batch-confirm-copy").performClick()
        }
        scenario!!.recreate(); waitIdle()
        assertEquals(2, model.state.value.approved.size)
        compose.onNodeWithTag("batch-zip").assertIsEnabled().performClick()
        compose.onNodeWithTag("batch-confirm-zip").performClick()
        compose.waitUntil(30_000) { !model.state.value.busy && model.state.value.archive != null }
        val ready = model.state.value.archive!!
        val export = uri("export.jpg")
        Intents.intending(hasAction(Intent.ACTION_CREATE_DOCUMENT)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(export)))
        compose.onNodeWithTag("batch-save").performClick()
        compose.waitUntil(20_000) { model.state.value.saved && !model.state.value.busy }
        assertEquals("application/zip", Intents.getIntents().last { it.action == Intent.ACTION_CREATE_DOCUMENT }.type)
        val saved = File(context.cacheDir, "batch-ui-saved.zip")
        try {
            context.contentResolver.openInputStream(export)!!.use { input -> saved.outputStream().use { input.copyTo(it) } }
            assertEquals(ready.bytes, saved.length())
            ZipFile(saved).use { assertEquals(2, it.size()); assertNotNull(it.getEntry("02_tiny.bmp")) }
        } finally { saved.delete() }
        scenario!!.recreate(); waitIdle()
        compose.onNodeWithTag("batch-save").assertIsDisplayed()
        row("batch-clear").performClick(); compose.onNodeWithTag("batch-confirm-clear").performClick()
        compose.waitUntil(10_000) { !model.state.value.busy && model.state.value.batch.items.isEmpty() }
    }
    @Test fun changingSettingsInvalidatesResultsAndUncheckedCopyDoesNotBlockRemainingZip() {
        launch(); add()
        instrumentation.runOnMainSync { model.options(BatchOptions(ImageOutputFormat.BMP)) }; waitIdle()
        compose.onNodeWithTag("batch-convert").performClick()
        compose.waitUntil(30_000) { !model.state.value.busy && model.state.value.batch.items.all { it.output != null } }
        row("batch-allow-0").performClick(); compose.onNodeWithTag("batch-confirm-copy").performClick()
        row("batch-include-1").performClick(); waitIdle()
        assertEquals(1, model.state.value.approved.size)
        compose.onNodeWithTag("batch-zip").assertIsEnabled().performClick()
        compose.onNodeWithTag("batch-confirm-zip").performClick()
        compose.waitUntil(30_000) { model.state.value.archive != null && !model.state.value.busy }
        val old = model.state.value.archive!!.resolve(BatchEngine(context).folder)
        instrumentation.runOnMainSync { model.options(BatchOptions(ImageOutputFormat.WEBP, 95, 1280)) }; waitIdle()
        assertTrue(model.state.value.batch.items.all { it.output == null })
        assertTrue(model.state.value.approved.isEmpty()); assertNull(model.state.value.archive); assertFalse(old.exists())
        compose.onNodeWithTag("batch-zip").assertIsNotEnabled()
        val case = InstrumentationRegistry.getArguments().getString("audit-case", "phone")!!.replace(Regex("[^a-zA-Z0-9-]"), "_")
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        try {
            val file = File(context.getExternalFilesDir(null), "qa/batch-images/$case.png").apply { parentFile!!.mkdirs() }
            file.outputStream().use { screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        } finally { screenshot.recycle() }
        row("batch-remove-1").performClick(); waitIdle()
        assertEquals(1, model.state.value.batch.items.size)
    }
}

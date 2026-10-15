package com.example.contentstudio

import android.app.LocaleManager
import android.net.Uri
import android.os.LocaleList
import android.os.Process
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.example.contentstudio.batch.*
import com.example.contentstudio.media.ImageOutputFormat
import com.example.contentstudio.processing.*
import com.example.contentstudio.ui.resumeWork
import java.io.File
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*

/** Execute prepare -> host force-stop -> verify as two independent instrumentation processes. */
class BatchImageProcessTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var model: BatchImageViewModel
    private val marker get() = File(context.getExternalFilesDir(null), "qa/batch-images/process.json").apply { parentFile!!.mkdirs() }
    private fun launch() {
        context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags("en-US")
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("home-batch-tools").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("home-batch-tools").performScrollTo().performClick()
        compose.onNodeWithTag("home-batch-images").performScrollTo().performClick()
        scenario!!.onActivity { model = ViewModelProvider(it)[BatchImageViewModel::class.java] }
        idle()
    }
    private fun idle() { compose.waitUntil(30_000) { model.state.value.ready && !model.state.value.busy } }
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    @After fun close() { scenario?.close() }
    @Test fun prepareApprovedArchive() {
        runBlocking {
            withContext(Dispatchers.Main) { ProcessingJobs.get(context).awaitReady(); ProcessingJobs.get(context).forget(ProcessingKind.BATCH_IMAGE) }
            withContext(Dispatchers.IO) { BatchEngine(context).clear() }
        }
        launch()
        main { model.add(listOf(Uri.parse("content://com.example.contentstudio.test.fixtures/tiny.png"))) }; idle()
        main { model.options(BatchOptions(ImageOutputFormat.BMP, 95, 2560)) }; idle()
        main { model.convert() }; idle()
        val result = model.state.value.batch.items.single()
        main { model.confirmCopy(result.copyKey!!); model.pack(model.state.value.batch.revision) }; idle()
        assertNotNull(model.state.value.archive)
        marker.writeText(JSONObject().put("pid", Process.myPid()).put("result", result.output).toString())
    }
    @Test fun verifyRestorationRequiresNewApprovalAndRetainsCompletedImages() {
        val previous = JSONObject(marker.readText())
        assertNotEquals(previous.getInt("pid"), Process.myPid())
        launch()
        val restored = model.state.value
        assertEquals(BatchOptions(ImageOutputFormat.BMP, 95, 2560), restored.batch.options)
        assertEquals(previous.getString("result"), restored.batch.items.single().output)
        assertTrue(restored.approved.isEmpty()); assertNull(restored.archive)
        main { model.pack(restored.batch.revision) }; idle()
        assertNull(model.state.value.archive)
        main { model.confirmCopy(restored.batch.items.single().copyKey!!); model.pack(restored.batch.revision) }; idle()
        assertNotNull(model.state.value.archive)
        compose.onNodeWithTag("batch-back").performClick()
        assertTrue(runBlocking { resumeWork(context).any { it.kind == "batch-images" } })
        val home = compose.onNode(hasTestTag("compact-home") or hasTestTag("wide-home-tools"))
        home.performScrollToNode(hasText(context.getString(R.string.resume_work)))
        if (compose.onAllNodesWithTag("home-all-work").fetchSemanticsNodes().isNotEmpty())
            compose.onNodeWithTag("home-all-work").performClick()
        compose.homeTool("resume-batch-images").performClick(); idle()
        assertEquals(previous.getString("result"), model.state.value.batch.items.single().output)
        compose.onNodeWithTag("batch-back").performClick()
        compose.homeTool("resume-batch-images-manage").performClick()
        compose.onNodeWithTag("work-delete-request").performClick()
        compose.onNodeWithTag("work-delete-confirm").performClick(); idle()
        compose.waitUntil(15_000) { model.state.value.batch.items.isEmpty() && !model.state.value.busy }
        assertTrue(model.state.value.batch.items.isEmpty())
        assertFalse(runBlocking { resumeWork(context).any { it.kind == "batch-images" } })
    }
}

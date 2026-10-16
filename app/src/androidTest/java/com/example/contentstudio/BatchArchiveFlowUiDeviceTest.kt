package com.example.contentstudio

import android.app.Activity
import android.app.Instrumentation
import android.app.LocaleManager
import android.content.ClipData
import android.content.Intent
import android.os.LocaleList
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.platform.app.InstrumentationRegistry
import com.example.contentstudio.archive.*
import com.example.contentstudio.batch.*
import com.example.contentstudio.processing.*
import java.io.File
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*

class BatchArchiveFlowUiDeviceTest {
    @get:Rule val compose=createEmptyComposeRule()
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private val f get()=BatchArchiveFlowSupport(context)
    private var scenario: ActivityScenario<MainActivity>?=null
    private lateinit var batch: BatchImageViewModel
    private lateinit var archive: ArchiveViewModel
    @Before fun setup() {
        val locale=InstrumentationRegistry.getArguments().getString("audit-locale","en-US")
        context.getSystemService(LocaleManager::class.java).applicationLocales=LocaleList.forLanguageTags(locale)
        runBlocking {
            withContext(Dispatchers.Main) { ProcessingJobs.get(context).awaitReady();ProcessingJobs.get(context).forget(ProcessingKind.BATCH_IMAGE) }
            withContext(Dispatchers.IO) { BatchEngine(context).clear();ArchiveEngine(context).clear();f.reset() }
        }
        Intents.init();scenario=ActivityScenario.launch(MainActivity::class.java)
        scenario!!.onActivity { batch=ViewModelProvider(it)[BatchImageViewModel::class.java];archive=ViewModelProvider(it)[ArchiveViewModel::class.java] }
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("home-batch-tools").fetchSemanticsNodes().isNotEmpty() }
    }
    @After fun cleanup() {
        if(::batch.isInitialized) instrumentation.runOnMainSync { batch.cancel();archive.cancel() }
        if(::batch.isInitialized) compose.waitUntil(20_000) { !batch.state.value.busy && !archive.state.value.busy }
        scenario?.close();Intents.release()
        runBlocking(Dispatchers.IO) { BatchEngine(context).clear();ArchiveEngine(context).clear() }
    }
    private fun batchRow(tag:String): SemanticsNodeInteraction {
        compose.onNodeWithTag("batch-list").performScrollToNode(hasTestTag(tag))
        return compose.onNodeWithTag(tag).performScrollTo()
    }
    private fun archiveRow(tag:String): SemanticsNodeInteraction {
        compose.onNodeWithTag("archive-list").performScrollToNode(hasTestTag(tag))
        return compose.onNodeWithTag(tag).performScrollTo()
    }
    @Test fun userConvertsApprovesSavesZipOpensFromHomeAndExtractsOnlySelectedResult() {
        val originals=f.hash(f.uri("fixtures","tiny.png"))
        compose.onNodeWithTag("home-batch-tools").performScrollTo().performClick()
        compose.onNodeWithTag("home-batch-images").performClick()
        compose.waitUntil(30_000) { batch.state.value.ready && !batch.state.value.busy }
        val input=f.uri("fixtures","tiny.png")
        val second=input.buildUpon().appendQueryParameter("copy","2").build()
        Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK,
            Intent().apply { clipData=ClipData.newRawUri("images",input).apply { addItem(ClipData.Item(second)) } }))
        batchRow("batch-add").performClick()
        compose.waitUntil(30_000) { batch.state.value.batch.items.size==2 && !batch.state.value.busy }
        compose.onNodeWithTag("batch-panel-output").performClick()
        compose.onNodeWithTag("batch-format-BMP").performScrollTo().performClick()
        compose.waitUntil(20_000) { !batch.state.value.busy }
        val panelClose=if(compose.onAllNodesWithTag("batch-panel-close").fetchSemanticsNodes().isNotEmpty()) "batch-panel-close" else "batch-panel-output"
        compose.onNodeWithTag(panelClose).performClick()
        compose.onNodeWithTag("batch-convert").performClick()
        compose.waitUntil(40_000) { !batch.state.value.busy && batch.state.value.batch.items.all { it.output!=null } }
        compose.onNodeWithTag("batch-zip").assertIsNotEnabled()
        for(index in 0..1) { batchRow("batch-allow-$index").performClick();compose.onNodeWithTag("batch-confirm-copy").performClick() }
        compose.onNodeWithTag("batch-zip").assertIsEnabled().performClick()
        compose.onNodeWithTag("batch-confirm-zip").performClick()
        compose.waitUntil(30_000) { !batch.state.value.busy && batch.state.value.archive!=null }
        val saved=f.createZip("ui-chain.zip")
        Intents.intending(hasAction(Intent.ACTION_CREATE_DOCUMENT)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK,Intent().setData(saved)))
        compose.onNodeWithTag("batch-save").performClick()
        compose.waitUntil(20_000) { batch.state.value.saved && !batch.state.value.busy }
        assertEquals("application/zip",Intents.getIntents().last { it.action==Intent.ACTION_CREATE_DOCUMENT }.type)
        val savedHash=f.hash(saved)
        val queue=batch.state.value.batch
        val expected=runBlocking(Dispatchers.IO) { f.expected(BatchEngine(context),queue) }
        compose.onNodeWithTag("batch-back").performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("home-open-file").fetchSemanticsNodes().isNotEmpty() }
        Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK,Intent().setData(saved)))
        compose.onNodeWithTag("home-open-file").performScrollTo().performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("file-action-ARCHIVE").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("file-action-ARCHIVE").performClick()
        compose.waitUntil(30_000) { archive.state.value.session?.name=="ui-chain.zip" && !archive.state.value.busy }
        assertEquals(2,archive.state.value.session!!.entries.size)
        val name=expected.getJSONObject(0).getString("name")
        archiveRow("archive-select-$name").performClick()
        scenario!!.recreate()
        compose.waitUntil(30_000) { !archive.state.value.busy && archive.state.value.selected==setOf(name) }
        val layout=mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithText(name,useUnmergedTree=true).performSemanticsAction(
            androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layout) }
        assertEquals("Ordinal/extension order must remain intact in RTL",androidx.compose.ui.text.style.ResolvedTextDirection.Ltr,
            layout.single().getParagraphDirection(0))
        Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT_TREE)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK,Intent().setData(f.tree)))
        compose.onNodeWithTag("archive-extract").performClick()
        compose.waitUntil(30_000) { !archive.state.value.busy && archive.state.value.extracted!=null }
        assertNull(archive.state.value.error)
        val files=f.children(archive.state.value.extracted!!)
        assertEquals(setOf(name),files.keys)
        assertEquals(expected.getJSONObject(0).getString("sha256"),f.hash(files.getValue(name)))
        assertEquals(savedHash,f.hash(saved));assertEquals(originals,f.hash(input));f.sentinel()
        archiveRow("archive-extracted").assertExists()
        compose.waitForIdle();instrumentation.uiAutomation.waitForIdle(500,5000)
        val bitmap=checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val label=InstrumentationRegistry.getArguments().getString("audit-case","phone")
        try { File(f.evidence,"ui-$label.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) } }
        finally { bitmap.recycle() }
    }
}

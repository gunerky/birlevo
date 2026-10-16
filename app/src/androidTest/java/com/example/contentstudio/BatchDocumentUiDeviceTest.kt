package com.example.contentstudio

import android.app.Activity
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
import androidx.test.platform.app.InstrumentationRegistry
import com.example.contentstudio.batch.*
import com.example.contentstudio.documents.*
import com.example.contentstudio.processing.*
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*

class BatchDocumentUiDeviceTest {
    @get:Rule val compose=createEmptyComposeRule()
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private lateinit var model:BatchDocumentViewModel
    private var scenario:ActivityScenario<MainActivity>?=null
    private fun uri(name:String)=Uri.parse("content://com.example.contentstudio.test.documents/$name")
    @Before fun setup(){
        context.getSystemService(LocaleManager::class.java).applicationLocales=LocaleList.forLanguageTags(
            InstrumentationRegistry.getArguments().getString("audit-locale","en-US"))
        runBlocking {withContext(Dispatchers.Main){ProcessingJobs.get(context).awaitReady();ProcessingKind.entries.forEach {ProcessingJobs.get(context).forget(it)}}
            withContext(Dispatchers.IO){BatchEngine(context,BatchKind.DOCUMENT).clear()}}
        Intents.init()
    }
    @After fun close(){
        runBlocking {withContext(Dispatchers.Main){ProcessingKind.entries.forEach {ProcessingJobs.get(context).forget(it)}}}
        scenario?.close();Intents.release()
        runBlocking(Dispatchers.IO){BatchEngine(context,BatchKind.DOCUMENT).clear()}
    }
    private fun main(work:()->Unit)=instrumentation.runOnMainSync(work)
    private fun idle(){compose.waitUntil(40_000){model.state.value.ready&&!model.state.value.busy};compose.waitForIdle()}
    private fun launch(){
        scenario=ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(30_000){compose.onAllNodesWithTag("home-batch-tools").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("home-batch-tools").performScrollTo().performClick()
        compose.onNodeWithTag("home-batch-documents").performScrollTo().performClick()
        scenario!!.onActivity {model=ViewModelProvider(it)[BatchDocumentViewModel::class.java]};idle()
    }
    private fun row(tag:String):SemanticsNodeInteraction{
        compose.onNodeWithTag("batch-list").performScrollToNode(hasTestTag(tag));return compose.onNodeWithTag(tag).performScrollTo()
    }
    private fun add(vararg names:String){
        val clips=ClipData.newRawUri("documents",uri(names.first()))
        names.drop(1).forEach {clips.addItem(ClipData.Item(uri(it)))}
        Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK,
            Intent().apply {clipData=clips;addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)}))
        row("batch-add").performClick()
        compose.waitUntil(30_000){model.state.value.batch.items.size==names.size&&!model.state.value.busy}
    }
    private fun closeOptions(){
        if(compose.onAllNodesWithTag("batch-panel-close").fetchSemanticsNodes().isNotEmpty())compose.onNodeWithTag("batch-panel-close").performClick()
        else compose.onNodeWithTag("batch-panel-output").performClick()
    }
    private fun allowCopies(){
        model.state.value.batch.items.forEachIndexed {index,item->if(item.needsConsent){
            row("batch-allow-$index").performClick();compose.onNodeWithTag("batch-confirm-copy").performClick()
        }}
    }
    private fun pack(){compose.onNodeWithTag("batch-zip").performClick();compose.onNodeWithTag("batch-confirm-zip").performClick()
        compose.waitUntil(30_000){!model.state.value.busy&&model.state.value.archive!=null}}
    @Test fun mixedPickerSheetLossCopyReviewZipSaveAndChangedTarget(){
        launch();add("records.csv","rich.docx","rich.xlsx")
        row("batch-document-review-1").performClick();row("batch-document-loss-1").assertExists()
        row("batch-document-consent-1").performClick()
        row("batch-document-review-2").performClick()
        row("batch-document-sheet-2-1").performClick();idle()
        row("batch-document-mode-2-FORMULAS").performClick();idle()
        row("batch-document-consent-2").performClick()
        assertEquals(2,model.state.value.documentApproved.size)
        compose.onNodeWithTag("batch-convert").performClick();idle()
        assertTrue(model.state.value.batch.items.all {it.output!=null})
        allowCopies();pack()
        val archive=model.state.value.archive!!;val file=archive.resolve(File(context.noBackupFilesDir,"batch-documents"))
        scenario!!.recreate();idle();assertEquals(archive,model.state.value.archive)
        Intents.intending(hasAction(Intent.ACTION_CREATE_DOCUMENT)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK,Intent().setData(uri("export.zip"))))
        compose.onNodeWithTag("batch-save").performClick()
        compose.waitUntil(30_000){model.state.value.saved&&!model.state.value.busy}
        assertArrayEquals(file.readBytes(),context.contentResolver.openInputStream(uri("export.zip"))!!.use {it.readBytes()})
        ZipFile(file).use {assertEquals(3,it.size())};shot()
        compose.onNodeWithTag("batch-panel-output").performClick()
        compose.onNodeWithTag("batch-document-target-DOCX").performScrollTo().performClick();idle()
        assertTrue(model.state.value.batch.items.all {it.output==null})
        assertTrue(model.state.value.approved.isEmpty());assertTrue(model.state.value.documentApproved.isEmpty());assertNull(model.state.value.archive)
        closeOptions()
    }
    @Test fun missingFormulaCacheIsVisibleAndRevokingConsentInvalidatesOnlyTheArchive(){
        launch();add("shared-no-cache.xlsx")
        row("batch-document-review-0").performClick()
        row("batch-document-missing-0").assertExists()
        row("batch-document-consent-0").performClick()
        compose.onNodeWithTag("batch-convert").performClick();idle();allowCopies();pack()
        val old=model.state.value;val output=old.batch.items.single().output
        row("batch-document-consent-0").performClick()
        assertNull(model.state.value.archive);assertEquals(output,model.state.value.batch.items.single().output)
        compose.onNodeWithTag("batch-zip").assertIsNotEnabled()
        row("batch-document-consent-0").performClick();pack()
        assertNotEquals(old.archive,model.state.value.archive)
        row("batch-document-mode-0-FORMULAS").performClick();idle()
        assertNull(model.state.value.batch.items.single().output);assertTrue(model.state.value.documentApproved.isEmpty())
    }
    @Test fun incompatiblePeerHasExactErrorAndReviewedPartialZip(){
        launch();add("rich.docx","records.csv")
        compose.onNodeWithTag("batch-panel-output").performClick()
        compose.onNodeWithTag("batch-document-target-CSV").performScrollTo().performClick();idle();closeOptions()
        compose.onNodeWithTag("batch-convert").performClick();idle()
        assertEquals(BatchDocumentIssue.TARGET,model.state.value.batch.items[0].documentIssue)
        row("batch-failed-0").assertTextEquals(context.getString(R.string.batch_document_target))
        allowCopies();pack()
        ZipFile(model.state.value.archive!!.resolve(File(context.noBackupFilesDir,"batch-documents"))).use {assertEquals(1,it.size())}
    }
    @Test fun queuedDocumentsFinishAfterLeavingTheActivity(){
        launch();add("records.csv")
        val jobs=ProcessingJobs.get(context);val gate=CompletableDeferred<Unit>()
        main {jobs.start(ProcessingKind.VIDEO,"document-queue-regression"){
            gate.await();val folder=File(context.noBackupFilesDir,ProcessingKind.VIDEO.folder).apply {mkdirs()}
            val file=File(folder,"output-${java.util.UUID.randomUUID()}.json").apply {writeText("{}")};ProcessingOutput(file.name,file.length())
        }}
        compose.waitUntil(15_000){jobs.records.value[ProcessingKind.VIDEO]?.status==ProcessingStatus.RUNNING}
        main {model.convert()}
        assertEquals(ProcessingStatus.QUEUED,jobs.records.value[ProcessingKind.BATCH_DOCUMENT]?.status)
        compose.onNodeWithTag("batch-back").performClick();scenario!!.close();scenario=null
        gate.complete(Unit)
        compose.waitUntil(30_000){jobs.records.value[ProcessingKind.BATCH_DOCUMENT]?.status?.active==false}
        assertEquals(ProcessingStatus.SUCCEEDED,jobs.records.value[ProcessingKind.BATCH_DOCUMENT]?.status)
        launch();assertNotNull(model.state.value.batch.items.single().output)
        assertTrue(model.state.value.approved.isEmpty())
    }
    @Test fun stalePickerAfterClearCannotDeleteAnEarlierOriginal(){
        launch();add("records.csv")
        compose.onNodeWithTag("batch-convert").performClick();idle();allowCopies();pack()
        val oldArchive=model.state.value.archive!!.name
        val source=context.contentResolver.openInputStream(uri("records.csv"))!!.use{it.readBytes()}
        val marker=uri("export.guard")
        // Positive control: prove the provider observes the exact platform deletion API.
        android.provider.DocumentsContract.deleteDocument(context.contentResolver,uri("records.csv"))
        assertEquals("records.csv",context.contentResolver.openInputStream(marker)!!.bufferedReader().use{it.readText()})
        context.contentResolver.openOutputStream(marker,"w")!!.use{it.write(byteArrayOf())}
        main{model.clear()};idle();assertTrue(model.state.value.batch.items.isEmpty())
        main{model.export(uri("records.csv"),oldArchive)}
        assertTrue(model.state.value.error)
        runBlocking{withTimeoutOrNull(1500){while(context.contentResolver.openInputStream(marker)!!.use{it.read()}<0)delay(20)}}
        assertEquals(-1,context.contentResolver.openInputStream(marker)!!.use{it.read()})
        assertArrayEquals(source,context.contentResolver.openInputStream(uri("records.csv"))!!.use{it.readBytes()})
    }
    private fun shot(){
        compose.waitForIdle();instrumentation.uiAutomation.waitForIdle(500,5000)
        val bitmap=instrumentation.uiAutomation.takeScreenshot()?:error("screenshot")
        val folder=File(context.getExternalFilesDir(null),"qa/batch-documents").apply {mkdirs()}
        val name=InstrumentationRegistry.getArguments().getString("audit-case","phone")
        try{File(folder,"$name.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}}finally{bitmap.recycle()}
    }
}

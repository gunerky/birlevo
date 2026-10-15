package com.example.contentstudio

import android.app.LocaleManager
import android.os.LocaleList
import android.os.Process
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.example.contentstudio.batch.*
import com.example.contentstudio.documents.*
import com.example.contentstudio.processing.*
import java.io.File
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*

class BatchDocumentProcessTest {
    @get:Rule val compose=createEmptyComposeRule()
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private val marker get()=File(context.getExternalFilesDir(null),"qa/batch-documents/process.json").apply {parentFile!!.mkdirs()}
    @Test fun prepareDocumentQueue()=runBlocking(Dispatchers.IO){
        withContext(Dispatchers.Main){ProcessingJobs.get(context).awaitReady();ProcessingJobs.get(context).forget(ProcessingKind.BATCH_DOCUMENT)}
        val engine=BatchEngine(context,BatchKind.DOCUMENT);engine.clear()
        var q=engine.add(listOf("rich.docx","shared-no-cache.xlsx","records.csv").map {Uri.parse("content://com.example.contentstudio.test.documents/$it")})
        q=engine.document(q.items[1].id,0,OfficeCellMode.FORMULAS)
        val proof=q.items.mapNotNull {item->item.document?.takeIf{it.office!=null}?.key(item,q.options)}.toSet()
        engine.convert(q.revision,documentApproved=proof){_,_->};q=engine.read()
        assertTrue(q.items.all{it.output!=null})
        marker.writeText(JSONObject().put("pid",Process.myPid()).put("outputs",JSONArray(q.items.map{it.output})).toString())
        assertFalse(File(engine.folder,"session.json").readText().contains("SUM(B1:B2)"))
    }
    @Test fun restoreNeedsFreshLossAndCopyApprovalWithoutReencoding(){
        val saved=JSONObject(marker.readText());assertNotEquals(saved.getInt("pid"),Process.myPid())
        context.getSystemService(LocaleManager::class.java).applicationLocales=LocaleList.forLanguageTags("en-US")
        ActivityScenario.launch(MainActivity::class.java).use {scenario->
            compose.waitUntil(30_000){compose.onAllNodesWithTag("home-batch-tools").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithTag("home-batch-tools").performScrollTo().performClick()
            compose.onNodeWithTag("home-batch-documents").performClick()
            lateinit var model:BatchDocumentViewModel
            scenario.onActivity{model=ViewModelProvider(it)[BatchDocumentViewModel::class.java]}
            compose.waitUntil(30_000){model.state.value.ready&&!model.state.value.busy}
            val q=model.state.value.batch
            assertEquals(OfficeCellMode.FORMULAS,q.items[1].document!!.mode)
            assertTrue(model.state.value.documentApproved.isEmpty());assertTrue(model.state.value.approved.isEmpty());assertNull(model.state.value.archive)
            q.items.forEachIndexed{index,item->assertEquals(saved.getJSONArray("outputs").getString(index),item.output)}
            instrumentation.runOnMainSync{model.pack(q.revision)}
            assertNull(model.state.value.archive)
            instrumentation.runOnMainSync{
                q.items.forEach{item->item.document?.takeIf{it.office!=null}?.let{model.approveDocument(it.key(item,q.options),true)}
                    if(item.needsConsent)model.confirmCopy(item.copyKey!!)}
                model.pack(q.revision)
            }
            compose.waitUntil(30_000){!model.state.value.busy&&model.state.value.archive!=null}
            assertEquals(q.items.map{it.output},model.state.value.batch.items.map{it.output})
            instrumentation.runOnMainSync{model.clear()}
            compose.waitUntil(30_000){!model.state.value.busy&&model.state.value.batch.items.isEmpty()}
        }
    }
}

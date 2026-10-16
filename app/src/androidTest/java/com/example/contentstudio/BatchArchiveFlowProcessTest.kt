package com.example.contentstudio

import androidx.test.platform.app.InstrumentationRegistry
import com.example.contentstudio.batch.*
import com.example.contentstudio.media.ImageOutputFormat
import java.io.File
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*

class BatchArchiveFlowProcessTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun prepareInterruptedConversion(): Unit = runBlocking(Dispatchers.IO) {
        val f=BatchArchiveFlowSupport(context);val engine=BatchEngine(context);engine.clear();f.reset()
        var q=engine.options(BatchOptions(format=ImageOutputFormat.BMP))
        q=engine.add(listOf(f.uri("fixtures","tiny.png"),f.uri("fixtures","tiny.png")))
        try { engine.convert(q.revision) { _,_ -> throw CancellationException("one durable conversion") };fail() }
        catch(_: CancellationException) { }
        q=engine.read();assertEquals(1,q.items.count { it.output!=null })
        File(f.evidence,"process-checkpoint.json").writeText(JSONObject().put("id",q.items.first().id)
            .put("output",q.items.first().output).put("sha256",f.hash(f.asset(engine,q.items.first().output!!))).toString())
    }
    @Test fun restoreConvertRemainingThenExportReopenAndExtract(): Unit = runBlocking(Dispatchers.IO) {
        val f=BatchArchiveFlowSupport(context);f.grant();val engine=BatchEngine(context)
        val before=JSONObject(File(f.evidence,"process-checkpoint.json").readText())
        try {
            var q=engine.read();assertEquals(1,q.items.count { it.output!=null })
            assertEquals(before.getString("id"),q.items.first().id)
            engine.convert(q.revision) { _,_ -> };q=engine.read()
            assertEquals(before.getString("output"),q.items.first().output)
            assertEquals(before.getString("sha256"),f.hash(f.asset(engine,q.items.first().output!!)))
            assertTrue(q.items.all { it.needsConsent })
            f.rejected { engine.zip(q.revision,emptySet()) }
            val report=f.roundTrip(engine,q,"process")
            File(f.evidence,"process-manifest.json").writeText(JSONObject().put("outputs",JSONArray().put(report)).toString(2))
        } finally { engine.clear() }
    }
}

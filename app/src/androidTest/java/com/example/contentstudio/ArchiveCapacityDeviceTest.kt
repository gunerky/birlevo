package com.example.contentstudio

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.example.contentstudio.archive.*
import com.example.contentstudio.audio.AudioTarget
import com.example.contentstudio.batch.*
import com.example.contentstudio.storage.*
import java.io.File
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Synthetic disk pressure only: never fill the user's disk to test a failure path. */
class ArchiveCapacityDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val support get() = BatchArchiveFlowSupport(context)
    private val safe get() = Uri.parse("content://com.example.contentstudio.test.archives/safe.zip")
    private val mib = 1024L * 1024
    private val emptyStorage get() = StreamingStorage { StorageSnapshot(1024*mib, 64*mib) }
    private suspend fun capacityFailure(work: suspend () -> Unit) {
        try { work(); fail("capacity rejection expected") } catch (_: StorageCapacityException) { }
    }
    private fun clean(engine: ArchiveEngine) = assertFalse(engine.folder.listFiles().orEmpty().any { it.name.endsWith(".part") })

    @Test fun lowSpaceKeepsPreviousSessionSelectionSourceAndCompletedBatchOutputs() = runBlocking {
        val real = ArchiveEngine(context); real.clear()
        val batch = BatchEngine(context); batch.clear()
        support.reset()
        try {
            val sourceHash = support.hash(safe)
            val session = real.open(safe)
            val low = ArchiveEngine(context) { emptyStorage }
            assertEquals(0L,low.inputLimit())
            capacityFailure { low.open(safe) }
            assertEquals(session.revision, real.read()!!.revision)
            val token = low.prepareSelection(session.revision,setOf("folder/hello.txt"))
            capacityFailure { low.extractSelection(token,support.tree) }
            assertEquals(setOf("sentinel.txt"),support.children(android.provider.DocumentsContract.buildDocumentUriUsingTree(support.tree,"root")).keys)
            assertEquals(sourceHash,support.hash(safe)); clean(real); support.sentinel()
            // A failed attempt does not consume the selection or lose the old snapshot.
            real.extractSelection(token,support.tree)
            var q = batch.add(listOf(support.uri("fixtures","tiny.png")))
            batch.convert(q.revision) { queue,_ -> q=queue }
            val result = support.asset(batch,checkNotNull(q.items.single().output))
            val resultHash = support.hash(result)
            val lowBatch = BatchEngine(context, capacity = { emptyStorage })
            capacityFailure { lowBatch.zip(q.revision,support.copies(q)) }
            assertEquals(resultHash,support.hash(result))
            assertEquals(q,batch.read())
            assertFalse(batch.folder.listFiles().orEmpty().any { it.name.endsWith(".part") || it.name.endsWith(".zip") })
        } finally { real.clear(); batch.clear(); support.reset() }
    }

    @Test fun pressureDuringDestinationWriteRollsBackOnlyNewFolderAndAllowsRetry() = runBlocking {
        support.reset()
        var low = false
        val engine = ArchiveEngine(context) { StreamingStorage { StorageSnapshot(1024*mib,if(low) 64*mib else 512*mib) } }
        engine.clear()
        try {
            val session = engine.open(safe)
            val paths = setOf("folder/hello.txt","日本.txt")
            val token = engine.prepareSelection(session.revision,paths)
            capacityFailure { engine.extractSelection(token,support.tree,{ done,_ -> if(done == 1) low=true }) }
            support.sentinel(); clean(engine)
            assertEquals(setOf("sentinel.txt"),support.children(android.provider.DocumentsContract.buildDocumentUriUsingTree(support.tree,"root")).keys)
            low=false
            assertEquals(session.revision,engine.read()!!.revision)
            val retry = engine.extractSelection(engine.prepareSelection(session.revision,paths),support.tree)
            assertEquals(2,retry.second); support.sentinel(); clean(engine)
        } finally { engine.clear(); support.reset() }
    }

    @Test fun realWavBeyond128MiBConvertsPackagesReopensAndExtractsWithoutQualityReduction() = runBlocking {
        val flow = support
        flow.reset()
        val engine = BatchEngine(context,BatchKind.AUDIO); engine.clear()
        try {
            var q = engine.options(BatchOptions(kind=BatchKind.AUDIO,audioTarget=AudioTarget.WAV))
            q = engine.add(listOf(Uri.parse("content://com.example.contentstudio.test.archives/large.wav")))
            assertTrue(q.items.single().bytes > 128*mib)
            val sourceHash = flow.hash(flow.asset(engine,q.items.single().source))
            engine.convert(q.revision) { queue,_ -> q=queue }
            assertNull(q.items.single().issue)
            assertTrue(q.items.single().outputBytes > 128*mib)
            assertEquals(sourceHash,flow.hash(flow.asset(engine,q.items.single().source)))
            val record = flow.roundTrip(engine,q,"capacity-large-wav")
            File(flow.evidence,"capacity-manifest.json").writeText(JSONObject().put("outputs",JSONArray().put(record)).toString())
            val info = com.example.contentstudio.audio.AudioEngine(context).inspect(flow.asset(engine,checkNotNull(q.items.single().output)))
            assertEquals(96000,info.sampleRate); assertEquals(2,info.channels)
        } finally { engine.clear() }
    }
}

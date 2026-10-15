package com.example.contentstudio

import androidx.test.platform.app.InstrumentationRegistry
import com.example.contentstudio.archive.*
import com.example.contentstudio.batch.*
import com.example.contentstudio.media.*
import com.example.contentstudio.audio.*
import com.example.contentstudio.documents.*
import java.io.File
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*

class BatchArchiveFlowDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val flow get() = BatchArchiveFlowSupport(context)
    private suspend fun matrix(kind: BatchKind, recipes: List<BatchOptions>, inputs: List<android.net.Uri>) {
        val f=flow; val engine=BatchEngine(context,kind);engine.clear();val rows=JSONArray()
        val originals=inputs.associateWith { f.hash(it) }
        try {
            for(options in recipes) {
                engine.clear();ArchiveEngine(context).clear();f.reset()
                var q=engine.options(options);q=engine.add(inputs)
                if(kind==BatchKind.DOCUMENT) q.items.filter { it.document?.office==OfficeFormat.XLSX }.forEach {
                    q=engine.document(it.id,1,OfficeCellMode.STORED_VALUES)
                }
                engine.convert(q.revision,documentApproved=f.losses(q)) { _,_ -> };q=engine.read()
                assertTrue(q.items.any { it.output != null })
                q.items.filter { it.output==null }.forEach { assertEquals(BatchDocumentIssue.TARGET,it.documentIssue) }
                if(q.items.any { it.needsConsent }) f.rejected { engine.zip(q.revision,emptySet(),f.losses(q)) }
                val target=when(kind) { BatchKind.IMAGE->options.format.name;BatchKind.VIDEO->options.videoFormat.name
                    BatchKind.AUDIO->options.audioTarget.name;BatchKind.DOCUMENT->options.documentTarget.name }
                rows.put(f.roundTrip(engine,q,"${kind.name.lowercase()}-${target.lowercase()}"))
                originals.forEach { (uri,hash) -> assertEquals(hash,f.hash(uri)) }
                q.items.forEach { assertEquals(originals[android.net.Uri.parse(it.uri)],f.hash(f.asset(engine,it.source))) }
            }
            File(f.evidence,"${kind.name.lowercase()}-manifest.json").writeText(JSONObject().put("outputs",rows).toString(2))
        } finally { engine.clear();ArchiveEngine(context).clear() }
    }
    @Test fun imageFormatsDuplicateNamesAndTransparencySurviveWholeChain(): Unit = runBlocking(Dispatchers.IO) {
        matrix(BatchKind.IMAGE,ImageOutputFormat.entries.map { BatchOptions(format=it,edge=2560) },
            listOf(flow.uri("fixtures","noise.jpg"),flow.uri("fixtures","tiny.png"),flow.uri("fixtures","tiny.png")))
    }
    @Test fun videoFormatsFullFramesAudioAndRequestedResolutionSurviveWholeChain(): Unit = runBlocking(Dispatchers.IO) {
        matrix(BatchKind.VIDEO,VideoOutputFormat.entries.mapIndexed { index,it -> BatchOptions(kind=BatchKind.VIDEO,
            videoFormat=it,videoQuality=VideoQuality.entries[index%3],videoEdge=listOf(640,1280,1920)[index%3]) },
            listOf(flow.uri("videos","audio.mp4"),flow.uri("videos","portrait.mp4")))
    }
    @Test fun audioFormatsMonoStereoAndFullDurationSurviveWholeChain(): Unit = runBlocking(Dispatchers.IO) {
        matrix(BatchKind.AUDIO,AudioTarget.entries.map { BatchOptions(kind=BatchKind.AUDIO,audioTarget=it) },
            listOf(flow.uri("audio","tone.wav"),flow.uri("audio","mono44100.wav")))
    }
    @Test fun documentTargetsSheetChoiceAndApprovedLossSurviveWholeChain(): Unit = runBlocking(Dispatchers.IO) {
        matrix(BatchKind.DOCUMENT,DocumentTarget.entries.map { BatchOptions(kind=BatchKind.DOCUMENT,documentTarget=it) },
            listOf("utf16.txt","records.csv","rich.docx","rich.xlsx").map { flow.uri("documents",it) })
    }
    @Test fun queueSelectionArchiveSelectionCancellationAndRetryNeverTouchSavedZip(): Unit = runBlocking(Dispatchers.IO) {
        val f=flow;f.reset();val engine=BatchEngine(context);engine.clear();val reader=ArchiveEngine(context);reader.clear()
        try {
            var q=engine.options(BatchOptions(format=ImageOutputFormat.BMP))
            q=engine.add(List(3) { f.uri("fixtures","tiny.png") });engine.convert(q.revision) { _,_ -> };q=engine.read()
            q=engine.include(q.items[1].id,false)
            assertEquals(3,q.items.size);assertEquals(2,q.items.count { it.included })
            f.rejected { engine.zip(q.revision,emptySet()) }
            val expected=f.expected(engine,q)
            val selected=setOf(expected.getJSONObject(1).getString("name"))
            val report=f.roundTrip(engine,q,"selection",selected)
            val saved=f.children(android.provider.DocumentsContract.buildDocumentUriUsingTree(f.tree,"root")).getValue("selection.zip")
            val savedHash=f.hash(saved);val archive=reader.open(saved)
            val token=reader.prepareSelection(archive.revision,archive.entries.map { it.path }.toSet())
            try { reader.extractSelection(token,f.tree,{ done,_ -> if(done==1) throw CancellationException("cancel real output") });fail() }
            catch(_: CancellationException) { }
            assertEquals(savedHash,f.hash(saved));assertNull(reader.lastExtraction());f.sentinel()
            val retried=reader.extractSelection(reader.prepareSelection(archive.revision,selected),f.tree)
            assertEquals(selected,f.children(retried.first).keys)
            assertEquals(savedHash,f.hash(saved))
            engine.clear();reader.clear()
            assertEquals(savedHash,f.hash(saved))
            assertEquals(expected.getJSONObject(1).getString("sha256"),f.hash(f.children(retried.first).values.single()))
            File(f.evidence,"selection-manifest.json").writeText(JSONObject().put("outputs",JSONArray().put(report)).toString(2))
        } finally { engine.clear();reader.clear() }
    }
    @Test fun officeLossAndCopyProofsExpireOnRecipeChangeWhileSavedZipRemainsReadable(): Unit = runBlocking(Dispatchers.IO) {
        val f=flow;f.reset();val engine=BatchEngine(context,BatchKind.DOCUMENT);engine.clear()
        try {
            var q=engine.options(BatchOptions(kind=BatchKind.DOCUMENT,documentTarget=DocumentTarget.PDF))
            q=engine.add(listOf(f.uri("documents","rich.docx"),f.uri("documents","utf16.txt")))
            engine.convert(q.revision) { _,_ -> };q=engine.read()
            assertEquals(BatchDocumentIssue.CONSENT,q.items.first().documentIssue)
            engine.convert(q.revision,documentApproved=f.losses(q)) { _,_ -> };q=engine.read()
            f.rejected { engine.zip(q.revision,f.copies(q)) }
            // The Office PDF may be smaller. The tiny text's PDF is actually larger.
            assertTrue(q.items.last().needsConsent)
            f.rejected { engine.zip(q.revision,emptySet(),f.losses(q)) }
            val old=q;val proof=f.losses(q);val approved=f.copies(q)
            val result=engine.zip(q.revision,approved,proof)
            val saved=f.createZip("consent.pdf.zip")
            engine.export(result,q.revision,approved,saved,proof)
            val hash=f.hash(saved)
            q=engine.options(q.options.copy(documentTarget=DocumentTarget.DOCX))
            f.rejected { engine.export(result,old.revision,approved,saved,proof) }
            assertEquals(hash,f.hash(saved))
            engine.convert(q.revision,documentApproved=proof) { _,_ -> };q=engine.read()
            assertEquals(BatchDocumentIssue.CONSENT,q.items.first().documentIssue)
            q=engine.options(q.options.copy(documentTarget=DocumentTarget.PDF))
            engine.convert(q.revision,documentApproved=f.losses(q)) { _,_ -> };q=engine.read()
            assertTrue(q.items.last().needsConsent)
            f.rejected { engine.zip(q.revision,approved,f.losses(q)) }
            engine.zip(q.revision,f.copies(q),f.losses(q))
            val reader=ArchiveEngine(context)
            try { val archive=reader.open(saved);val resultFolder=reader.extractSelection(
                reader.prepareSelection(archive.revision,archive.entries.map { it.path }.toSet()),f.tree)
                assertEquals(2,resultFolder.second);assertEquals(hash,f.hash(saved));f.sentinel()
            } finally { reader.clear() }
        } finally { engine.clear() }
    }
    @Test fun zeroByteTextDocumentSurvivesExportReopenAndFolderExtraction(): Unit = runBlocking(Dispatchers.IO) {
        val f=flow;f.reset();val engine=BatchEngine(context,BatchKind.DOCUMENT);engine.clear()
        val folder=File(context.cacheDir,"qa-flow-empty-word").apply { mkdirs() }
        val original=com.example.contentstudio.office.OfficeCodec.writeWord(folder,
            com.example.contentstudio.office.WordSnapshot(emptyList(),emptySet(),0))
        val staged=MediaOutputs(context).stage(original,OfficeFormat.DOCX.mime,"empty.docx")
        try {
            engine.options(BatchOptions(kind=BatchKind.DOCUMENT,documentTarget=DocumentTarget.TXT))
            var q=engine.add(listOf(staged.uri))
            engine.convert(q.revision,documentApproved=f.losses(q)) { _,_ -> };q=engine.read()
            assertEquals(0L,q.items.single().outputBytes);assertNotNull(q.items.single().output)
            val report=f.roundTrip(engine,q,"empty")
            File(f.evidence,"empty-manifest.json").writeText(JSONObject().put("outputs",JSONArray().put(report)).toString(2))
        } finally { engine.clear();original.delete();context.contentResolver.delete(staged.uri,null,null) }
    }
}

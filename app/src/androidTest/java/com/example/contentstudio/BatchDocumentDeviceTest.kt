package com.example.contentstudio

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import com.example.contentstudio.batch.*
import com.example.contentstudio.documents.*
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BatchDocumentDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val evidence get() = File(context.getExternalFilesDir(null), "qa/batch-documents").apply { mkdirs() }
    private fun uri(name: String) = Uri.parse("content://com.example.contentstudio.test.documents/$name")
    private fun copies(q: BatchQueue) = q.items.filter { it.needsConsent }.mapNotNull { it.copyKey }.toSet()
    private fun losses(q: BatchQueue) = q.items.mapNotNull { item -> item.document?.takeIf { it.office != null && it.sheet >= 0 }?.key(item,q.options) }.toSet()
    private suspend fun rejected(action: suspend ()->Unit) { try { action(); fail("should reject") } catch (_: IllegalArgumentException) { } }
    private fun hash(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
    @Test fun allSixTargetsPreserveTheirSupportedContentAndRejectIncompatiblePeers() = runBlocking(Dispatchers.IO) {
        val engine = BatchEngine(context,BatchKind.DOCUMENT); engine.clear()
        val report = JSONArray()
        try {
            for (target in DocumentTarget.entries) {
                engine.clear()
                var q = engine.options(BatchOptions(kind=BatchKind.DOCUMENT,documentTarget=target))
                q = engine.add(listOf(uri("utf16.txt"),uri("records.csv"),uri("rich.docx"),uri("rich.xlsx")))
                val workbook = q.items.last()
                assertEquals(-1,workbook.document!!.sheet)
                q = engine.document(workbook.id,1,OfficeCellMode.STORED_VALUES)
                val proof = losses(q)
                engine.convert(q.revision,documentApproved=proof) { _,_ -> }; q=engine.read()
                for(item in q.items) {
                    val meta=item.document!!
                    if (target !in DocumentPolicy.targets(meta.kind)) {
                        assertNull(item.output);assertEquals(BatchDocumentIssue.TARGET,item.documentIssue);continue
                    }
                    assertNotNull(item.output);assertNull(item.documentIssue)
                    val result=File(File(engine.folder,"assets"),item.output!!)
                    assertEquals(item.outputBytes,result.length())
                    if(target==DocumentTarget.PDF) PdfRenderer(ParcelFileDescriptor.open(result,ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
                        assertTrue(renderer.pageCount>0)
                        for(pageIndex in setOf(0,renderer.pageCount-1)) renderer.openPage(pageIndex).use { page ->
                            val bitmap=Bitmap.createBitmap(300,424,Bitmap.Config.ARGB_8888)
                            try { bitmap.eraseColor(android.graphics.Color.WHITE);page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                val pixels=IntArray(300*424);bitmap.getPixels(pixels,0,300,0,0,300,424)
                                assertTrue(pixels.any { it != android.graphics.Color.WHITE })
                            } finally {bitmap.recycle()}
                        }
                    }
                }
                val archive=engine.zip(q.revision,copies(q),proof).resolve(engine.folder)
                archive.copyTo(File(evidence,"${target.name.lowercase()}.zip"),overwrite=true)
                val entries=JSONArray()
                val reader=TextDocumentEngine(context,File(engine.folder,"assets"))
                q.items.filter {it.output!=null}.forEachIndexed { index,item ->
                    val meta=item.document!!
                    val content=reader.read(DocumentSource(File(File(engine.folder,"assets"),item.source),Uri.parse(item.uri),item.name,item.bytes,meta.office),
                        meta.kind,q.options.documentEncoding,q.options.documentDelimiter,meta.sheet,meta.mode)
                    entries.put(JSONObject().put("name",BatchPolicy.entryName(index,item.name,target.extension)).put("source",item.name)
                        .put("sha256",hash(File(File(engine.folder,"assets"),item.output!!))).put("text",content.text)
                        .put("rows",JSONArray(content.rows.map {JSONArray(it)})).put("kind",content.kind.name))
                }
                report.put(JSONObject().put("target",target.name).put("entries",entries))
            }
            File(evidence,"manifest.json").writeText(JSONObject().put("outputs",report).toString(2))
        } finally {engine.clear()}
    }
    @Test fun workbookChoiceMissingCacheAndTwoIndependentConsentsStayBoundToTheirResult() = runBlocking(Dispatchers.IO) {
        val engine=BatchEngine(context,BatchKind.DOCUMENT);engine.clear()
        try {
            var q=engine.add(listOf(uri("rich.docx"),uri("rich.xlsx"),uri("shared-no-cache.xlsx"),uri("records.csv")))
            engine.convert(q.revision) {_,_->};q=engine.read()
            assertEquals(BatchDocumentIssue.SHEET,q.items[1].documentIssue)
            assertEquals(BatchDocumentIssue.CONSENT,q.items[0].documentIssue)
            assertNotNull(q.items[3].output)
            assertEquals(listOf(1),q.items[2].document!!.missing)
            q=engine.document(q.items[1].id,1,OfficeCellMode.STORED_VALUES)
            val proof=losses(q)
            engine.convert(q.revision,documentApproved=proof) {_,_->};q=engine.read()
            assertTrue(q.items.all {it.output!=null})
            rejected {engine.zip(q.revision,copies(q))}
            rejected {engine.zip(q.revision,emptySet(),proof)}
            val zip=engine.zip(q.revision,copies(q),proof)
            rejected {engine.export(zip,q.revision,copies(q),uri("rich.docx"),proof)}
            val old=q
            q=engine.document(q.items[2].id,0,OfficeCellMode.FORMULAS)
            assertEquals(old.items[0].output,q.items[0].output);assertNull(q.items[2].output)
            engine.convert(q.revision,documentApproved=proof) {_,_->};q=engine.read()
            assertEquals(BatchDocumentIssue.CONSENT,q.items[2].documentIssue)
            val renewed=losses(q)
            engine.convert(q.revision,documentApproved=renewed) {_,_->};q=engine.read()
            assertNotNull(q.items[2].output)
            q=engine.options(q.options.copy(documentTarget=DocumentTarget.CSV))
            engine.convert(q.revision,documentApproved=losses(q)) {_,_->};q=engine.read()
            assertEquals(BatchDocumentIssue.TARGET,q.items[0].documentIssue)
            val file=File(File(engine.folder,"assets"),q.items[2].output!!)
            assertTrue(file.readText().contains("=SUM(B1:B2)"));assertTrue(file.readText().contains("00123"))
            engine.zip(q.revision,copies(q),losses(q)).resolve(engine.folder).copyTo(File(evidence,"formulas.zip"),true)
            q=engine.document(q.items[2].id,0,OfficeCellMode.STORED_VALUES)
            engine.convert(q.revision,documentApproved=losses(q)) {_,_->};q=engine.read()
            val values=TextDocumentCodec.parse(File(File(engine.folder,"assets"),q.items[2].output!!).readBytes(),DocumentKind.CSV)
            assertEquals("",values.rows[0][2]);assertEquals("46309",values.rows[0][3])
        } finally {engine.clear()}
    }
    @Test fun cancelResumeAtomicImportsEncodingAndOtherToolsStayIntact() = runBlocking(Dispatchers.IO) {
        val engine=BatchEngine(context,BatchKind.DOCUMENT);engine.clear()
        try {
            var q=engine.add(listOf(uri("records.csv"),uri("wide.tsv")))
            val sourceHash=hash(File(File(engine.folder,"assets"),q.items.first().source))
            rejected {engine.add(listOf(uri("utf16.txt"),uri("invalid.csv")))}
            assertEquals(q,engine.read())
            try {engine.convert(q.revision){_,_->throw CancellationException("after durable row")};fail()} catch (_:CancellationException) {}
            q=engine.read();assertNotNull(q.items[0].output);assertNull(q.items[1].output)
            val completed=q.items[0].output
            engine.convert(q.revision){_,_->};q=engine.read();assertEquals(completed,q.items[0].output)
            try {engine.zip(q.revision,copies(q)){throw CancellationException("ZIP write")};fail()}catch(_:CancellationException){}
            assertFalse(engine.folder.listFiles()!!.any {it.name.endsWith(".part")})
            assertEquals(sourceHash,hash(File(File(engine.folder,"assets"),q.items.first().source)))
            assertTrue(engine.read().items.all {it.output!=null})
            engine.clear()
            rejected {engine.add(listOf(uri("legacy.txt")))}
            q=engine.options(BatchOptions(kind=BatchKind.DOCUMENT,documentEncoding=DocumentEncoding.WINDOWS1254,documentTarget=DocumentTarget.TXT))
            q=engine.add(listOf(uri("legacy.txt")));engine.convert(q.revision){_,_->};q=engine.read()
            assertEquals("İstanbul ığüşöç",File(File(engine.folder,"assets"),q.items.single().output!!).readText())
            q=engine.options(q.options.copy(documentEncoding=DocumentEncoding.UTF8));engine.convert(q.revision){_,_->};q=engine.read()
            assertEquals(BatchDocumentIssue.ENCODING,q.items.single().documentIssue)
        } finally {engine.clear()}
    }
    @Test fun emptyWordBodyCanCreateAndArchiveAnEmptyTextFile()=runBlocking<Unit>(Dispatchers.IO){
        val folder=File(context.cacheDir,"qa-empty-word").apply{mkdirs()}
        val original=com.example.contentstudio.office.OfficeCodec.writeWord(folder,
            com.example.contentstudio.office.WordSnapshot(emptyList(),emptySet(),0))
        val staged=com.example.contentstudio.media.MediaOutputs(context).stage(original,OfficeFormat.DOCX.mime,"empty.docx")
        val engine=BatchEngine(context,BatchKind.DOCUMENT);engine.clear()
        try{
            engine.options(BatchOptions(kind=BatchKind.DOCUMENT,documentTarget=DocumentTarget.TXT))
            var q=engine.add(listOf(staged.uri));val proof=losses(q)
            engine.convert(q.revision,documentApproved=proof){_,_->};q=engine.read()
            assertNotNull(q.items.single().output);assertEquals(0L,q.items.single().outputBytes)
            assertFalse(q.items.single().needsConsent)
            val zip=engine.zip(q.revision,emptySet(),proof).resolve(engine.folder)
            ZipFile(zip).use{archive->val entry=archive.entries().nextElement();assertEquals(0L,entry.size)
                assertArrayEquals(byteArrayOf(),archive.getInputStream(entry).use{it.readBytes()})}
            zip.copyTo(File(evidence,"empty.zip"),true)
        }finally{engine.clear();original.delete();context.contentResolver.delete(staged.uri,null,null)}
    }
}

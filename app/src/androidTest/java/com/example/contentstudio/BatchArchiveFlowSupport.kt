package com.example.contentstudio

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract as Docs
import com.example.contentstudio.archive.*
import com.example.contentstudio.batch.*
import com.example.contentstudio.documents.*
import com.example.contentstudio.audio.sampleRate
import com.example.contentstudio.processing.ProcessingOutput
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*

/** Test-only cross-tool contract; no provider/storage/codecs are replaced in the production engines. */
internal class BatchArchiveFlowSupport(val context: Context) {
    val evidence = File(context.getExternalFilesDir(null), "qa/archive-flow").apply { mkdirs() }
    val tree: Uri = Docs.buildTreeDocumentUri("com.example.contentstudio.test.archive.tree", "root")
    private val fixture = Uri.parse("content://com.example.contentstudio.test.archives/safe.zip")
    fun reset() { context.contentResolver.call(fixture, "tree-reset", "normal", null) }
    fun grant() { context.contentResolver.call(fixture, "tree-grant", null, null) }
    fun uri(kind: String, name: String): Uri = Uri.parse("content://com.example.contentstudio.test.$kind/$name")
    fun asset(engine: BatchEngine, name: String) = File(File(engine.folder,"assets"),name)
    fun digest(input: InputStream): String = input.use {
        val hash = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(65536)
        while(true) { val n=it.read(buffer); if(n<0) break; hash.update(buffer,0,n) }
        hash.digest().joinToString("") { byte -> "%02x".format(byte) }
    }
    fun hash(file: File) = digest(file.inputStream())
    fun hash(uri: Uri) = digest(checkNotNull(context.contentResolver.openInputStream(uri)))
    fun copies(q: BatchQueue) = q.items.filter { it.needsConsent }.mapNotNull { it.copyKey }.toSet()
    fun losses(q: BatchQueue) = q.items.mapNotNull { item -> item.document?.takeIf { it.office != null && it.sheet >= 0 }?.key(item,q.options) }.toSet()
    suspend fun rejected(action: suspend () -> Unit) { try { action(); fail("Expected rejection") } catch(_: IllegalArgumentException) { } }
    fun createZip(name: String): Uri = checkNotNull(Docs.createDocument(context.contentResolver,
        Docs.buildDocumentUriUsingTree(tree,"root"),"application/zip",name))
    fun children(folder: Uri): Map<String,Uri> = context.contentResolver.query(
        Docs.buildChildDocumentsUriUsingTree(tree,Docs.getDocumentId(folder)),
        arrayOf(Docs.Document.COLUMN_DOCUMENT_ID, Docs.Document.COLUMN_DISPLAY_NAME),null,null,null)!!.use { c ->
        buildMap { while(c.moveToNext()) put(c.getString(1),Docs.buildDocumentUriUsingTree(tree,c.getString(0))) }
    }
    fun sentinel() = assertEquals("7, 8, 9", context.contentResolver.openInputStream(
        Docs.buildDocumentUriUsingTree(tree,"root/sentinel.txt"))!!.use { it.readBytes().joinToString() })

    /** Expectations are captured from converted files BEFORE packaging, not from the ZIP being checked. */
    suspend fun expected(engine: BatchEngine, q: BatchQueue): JSONArray {
        val rows = JSONArray()
        q.items.filter { it.included && it.output != null }.forEachIndexed { index,item ->
            val row = JSONObject().put("name",BatchPolicy.entryName(index,item.name,q.options.extension))
                .put("sha256",hash(asset(engine,item.output!!))).put("bytes",item.outputBytes)
                .put("source",item.name).put("sourceBytes",item.bytes)
                .put("sourceWidth",item.width).put("sourceHeight",item.height).put("durationMs",item.durationMs)
            if(q.options.kind == BatchKind.AUDIO) {
                val info = com.example.contentstudio.audio.AudioEngine(context).inspect(asset(engine,item.source))
                row.put("sampleRate",q.options.audioTarget.sampleRate(info.sampleRate)).put("channels",info.channels)
            }
            item.document?.let { meta ->
                val content = TextDocumentEngine(context,File(engine.folder,"assets")).read(
                    DocumentSource(asset(engine,item.source),Uri.parse(item.uri),item.name,item.bytes,meta.office),
                    meta.kind,q.options.documentEncoding,q.options.documentDelimiter,meta.sheet,meta.mode)
                row.put("documentKind",content.kind.name).put("text",content.text).put("rows",JSONArray(content.rows.map { JSONArray(it) }))
            }
            rows.put(row)
        }
        return rows
    }

    suspend fun roundTrip(engine: BatchEngine, q: BatchQueue, label: String,
        chosen: Set<String>? = null): JSONObject = withContext(Dispatchers.IO) {
        val expected = expected(engine,q)
        val result = engine.zip(q.revision,copies(q),losses(q))
        val saved = createZip("$label.zip")
        engine.export(result,q.revision,copies(q),saved,losses(q))
        verifySaved(engine,q,label,result,saved,expected,chosen)
    }

    suspend fun verifySaved(engine: BatchEngine,q: BatchQueue,label: String,result: ProcessingOutput,saved: Uri,
        expected: JSONArray, chosen: Set<String>? = null): JSONObject = withContext(Dispatchers.IO) {
        val zipHash = hash(result.resolve(engine.folder))
        assertEquals(zipHash,hash(saved))
        val reader = ArchiveEngine(context)
        try {
            val archive = reader.open(saved)
            assertEquals("$label.zip",archive.name)
            val names = (0 until expected.length()).map { expected.getJSONObject(it).getString("name") }
            assertEquals(names.toSet(),archive.entries.filterNot { it.directory }.map { it.path }.toSet())
            val selected = chosen ?: names.toSet()
            val output = reader.extractSelection(reader.prepareSelection(archive.revision,selected),tree)
            val files = children(output.first)
            assertEquals(selected,files.keys)
            val selectedRows = JSONArray()
            for(i in 0 until expected.length()) {
                val row=expected.getJSONObject(i);val name=row.getString("name")
                if(name !in selected) continue
                val file=File(evidence,"$label--$name")
                context.contentResolver.openInputStream(files.getValue(name))!!.use { input -> file.outputStream().use { input.copyTo(it,65536) } }
                assertEquals(row.getLong("bytes"),file.length());assertEquals(row.getString("sha256"),hash(file))
                row.put("extracted",file.name);selectedRows.put(row)
            }
            assertEquals(zipHash,hash(saved));sentinel()
            // Explicit clear must preserve both user's exported ZIP and extracted documents.
            reader.clear();assertEquals(zipHash,hash(saved));assertEquals(selected,children(output.first).keys)
            for(i in 0 until selectedRows.length()) {
                val row=selectedRows.getJSONObject(i)
                assertEquals(row.getString("sha256"),hash(files.getValue(row.getString("name"))))
            }
            context.contentResolver.openInputStream(saved)!!.use { input -> File(evidence,"$label.zip").outputStream().use { input.copyTo(it,65536) } }
            JSONObject().put("label",label).put("kind",q.options.kind.name).put("target",q.options.extension)
                .put("zipSha256",zipHash).put("entries",expected).put("extracted",selectedRows)
                .put("edge",if(q.options.kind==BatchKind.IMAGE) q.options.edge else q.options.videoEdge)
                .put("imageFormat",q.options.format.name).put("videoFormat",q.options.videoFormat.name).put("audioTarget",q.options.audioTarget.name)
                .put("documentTarget",q.options.documentTarget.name)
        } finally { reader.clear() }
    }
}

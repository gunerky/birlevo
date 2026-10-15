package com.example.contentstudio

import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.platform.app.InstrumentationRegistry
import com.example.contentstudio.archive.ArchiveEngine
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Test
import org.junit.Assert.*

class ArchiveFolderProcessTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val proof get() = File(context.getExternalFilesDir(null), "qa/archive-folders/pending.txt").apply { parentFile!!.mkdirs() }
    @Test fun prepare() = runBlocking {
        val engine = ArchiveEngine(context); engine.clear()
        val source = Uri.parse("content://com.example.contentstudio.test.archives/safe.zip")
        context.contentResolver.call(source, "tree-reset", "normal", null)
        val session = engine.open(source)
        proof.writeText(engine.prepareSelection(session.revision, setOf("日本.txt")))
    }
    @Test fun restore() = runBlocking {
        val engine = ArchiveEngine(context)
        assertNotNull(engine.read())
        // Represents the picker callback's new grant; force-stop revokes temporary grants.
        context.contentResolver.call(Uri.parse("content://com.example.contentstudio.test.archives/safe.zip"), "tree-grant", null, null)
        val result = engine.extractSelection(proof.readText(), DocumentsContract.buildTreeDocumentUri("com.example.contentstudio.test.archive.tree", "root"))
        assertEquals(1,result.second)
        engine.clear(); proof.delete(); Unit
    }
    @Test fun prepareInterruptedExtraction(): Unit = runBlocking {
        val engine = ArchiveEngine(context); engine.clear()
        val source = Uri.parse("content://com.example.contentstudio.test.archives/safe.zip")
        context.contentResolver.call(source, "tree-reset", "normal", null)
        val session = engine.open(source)
        val token = engine.prepareSelection(session.revision, session.entries.filterNot { it.directory }.map { it.path }.toSet())
        val ready = CountDownLatch(1)
        // Deliberately survives this test method; the QA script kills this dedicated app process.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            engine.extractSelection(token, DocumentsContract.buildTreeDocumentUri("com.example.contentstudio.test.archive.tree", "root"),
                { done,_ -> if(done == 1) { ready.countDown(); CountDownLatch(1).await() } })
        }
        assertTrue(ready.await(15, TimeUnit.SECONDS))
    }
    @Test fun restoreInterruptedExtractionShowsReceiptWithoutResumingOrDeleting(): Unit = runBlocking {
        val engine = ArchiveEngine(context)
        assertNotNull(engine.read())
        val status = checkNotNull(engine.lastExtraction())
        assertFalse(status.third)
        // Only the test inspector reacquires access; lastExtraction above does no provider I/O.
        context.contentResolver.call(Uri.parse("content://com.example.contentstudio.test.archives/safe.zip"), "tree-grant", null, null)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(status.first, DocumentsContract.getDocumentId(status.first))
        context.contentResolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null,null,null)!!.use {
            assertEquals(1,it.count)
        }
        // A second read leaves the same destination untouched.
        assertEquals(status,engine.lastExtraction())
        assertTrue(DocumentsContract.deleteDocument(context.contentResolver,status.first))
        engine.clear()
    }
}

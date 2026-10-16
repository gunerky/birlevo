package com.example.contentstudio

import android.net.Uri
import android.provider.DocumentsContract as Documents
import androidx.test.platform.app.InstrumentationRegistry
import com.example.contentstudio.archive.*
import java.io.File
import kotlinx.coroutines.*
import org.junit.Test
import org.junit.Assert.*

class ArchiveFolderDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val resolver get() = context.contentResolver
    private val source = Uri.parse("content://com.example.contentstudio.test.archives/safe.zip")
    private val tree = Documents.buildTreeDocumentUri("com.example.contentstudio.test.archive.tree", "root")
    private fun reset(mode: String = "normal") { resolver.call(source, "tree-reset", mode, null) }
    private fun children(uri: Uri): List<Uri> = resolver.query(
        Documents.buildChildDocumentsUriUsingTree(tree, Documents.getDocumentId(uri)),
        arrayOf(Documents.Document.COLUMN_DOCUMENT_ID), null, null, null)!!.use { c ->
        buildList { while(c.moveToNext()) add(Documents.buildDocumentUriUsingTree(tree, c.getString(0))) }
    }
    private fun root() = Documents.buildDocumentUriUsingTree(tree, "root")
    private fun bytes(uri: Uri) = resolver.openInputStream(uri)!!.use { it.readBytes() }
    private fun sentinel() = assertArrayEquals(byteArrayOf(7,8,9), bytes(Documents.buildDocumentUriUsingTree(tree, "root/sentinel.txt")))
    private fun noStages(engine: ArchiveEngine) = assertFalse(engine.folder.listFiles()!!.any { it.name.endsWith(".part") })

    @Test fun selectedNestedUnicodeEmptyFilesAndRepeatedExtractionKeepOriginals() = runBlocking {
        reset(); val engine = ArchiveEngine(context); engine.clear()
        try {
            val session = engine.open(source)
            val originals = bytes(source)
            val all = session.entries.filterNot { it.directory }.map { it.path }.toSet()
            val progress = mutableListOf<Pair<Int,Int>>()
            val first = engine.extractSelection(engine.prepareSelection(session.revision, all), tree, { a,b -> progress += a to b })
            assertEquals(3, first.second); assertEquals(3 to 3, progress.last())
            val files = children(first.first)
            val nested = files.single { Documents.getDocumentId(it).endsWith("/folder") }
            assertArrayEquals("Exact original: folder/hello.txt".toByteArray(), bytes(children(nested).single()))
            val unicode = files.single { Documents.getDocumentId(it).endsWith("/日本.txt") }
            assertArrayEquals("Exact original: 日本.txt".toByteArray(), bytes(unicode))
            assertEquals(0, bytes(files.single { Documents.getDocumentId(it).endsWith("/empty.bin") }).size)
            val second = engine.extractSelection(engine.prepareSelection(session.revision, setOf("folder/hello.txt")), tree)
            assertNotEquals(first.first, second.first); assertEquals(1, second.second)
            assertEquals(3, children(root()).size); sentinel(); assertArrayEquals(originals, bytes(source)); noStages(engine)
            val evidence = File(context.getExternalFilesDir(null), "qa/archive-folders").apply { mkdirs() }
            File(evidence,"hello.txt").writeBytes(bytes(children(nested).single()))
            File(evidence,"unicode.txt").writeBytes(bytes(unicode))
            File(evidence,"empty.bin").writeBytes(bytes(files.single { Documents.getDocumentId(it).endsWith("/empty.bin") }))
        } finally { engine.clear() }
    }

    @Test fun aliasRenameWriteAndDeletionFailuresNeverOverwriteExistingFiles() = runBlocking {
        val engine = ArchiveEngine(context); engine.clear()
        try {
            val session = engine.open(source)
            for(mode in listOf("alias", "root-alias", "child-alias", "rename", "write-fail", "write-delete-fail")) {
                reset(mode); var partial: Uri? = null
                try {
                    engine.extractSelection(engine.prepareSelection(session.revision, setOf("日本.txt")), tree, remaining = { partial = it })
                    fail("failure mode was accepted: $mode")
                } catch(_: IllegalArgumentException) { } catch(_: IllegalStateException) { } catch(_: java.io.IOException) { }
                sentinel(); noStages(engine)
                if(mode == "write-delete-fail") {
                    assertNotNull(partial); assertEquals(2, children(root()).size)
                    assertEquals(partial,engine.lastExtraction()!!.first); assertFalse(engine.lastExtraction()!!.third)
                }
                else { assertNull(partial); assertEquals(1, children(root()).size) }
            }
        } finally { engine.clear(); reset() }
    }

    @Test fun cancelAfterFirstFileRemovesOnlyNewFolderAndStalePlanDoesNothing() = runBlocking {
        reset(); val engine = ArchiveEngine(context); engine.clear()
        try {
            val session = engine.open(source)
            val token = engine.prepareSelection(session.revision, session.entries.filterNot { it.directory }.map { it.path }.toSet())
            try {
                engine.extractSelection(token, tree, { done,_ -> if(done == 1) throw CancellationException("cancel after a real file") })
                fail()
            } catch(_: CancellationException) { }
            assertEquals(1, children(root()).size); sentinel(); noStages(engine)
            val stale = engine.prepareSelection(session.revision, setOf("日本.txt"))
            engine.open(source)
            try { engine.extractSelection(stale, tree); fail() } catch(_: IllegalArgumentException) { }
            assertEquals(1, children(root()).size); sentinel()
            try { engine.prepareSelection(engine.read()!!.revision, setOf("../sentinel.txt")); fail() } catch(_: IllegalArgumentException) { }
            assertEquals(1, children(root()).size)
        } finally { engine.clear() }
    }

    @Test fun corruptSelectedContentCreatesNoDestination() = runBlocking {
        reset(); val engine = ArchiveEngine(context); engine.clear()
        try {
            val session = engine.open(source)
            val token = engine.prepareSelection(session.revision, setOf("folder/hello.txt"))
            val file = File(engine.folder, session.file)
            // First file's local compressed data, after the empty directory's local header.
            val raw = file.readBytes()
            fun short(at:Int) = (raw[at].toInt() and 255) or ((raw[at+1].toInt() and 255) shl 8)
            var at = 0
            while(at < raw.size - 30) {
                if(raw[at] == 0x50.toByte() && raw[at+1] == 0x4b.toByte() && raw[at+2] == 3.toByte() && raw[at+3] == 4.toByte()) {
                    val name = String(raw, at+30, short(at+26), Charsets.UTF_8)
                    if(name == "folder/hello.txt") { val data = at+30+short(at+26)+short(at+28); raw[data] = (raw[data].toInt() xor 0x40).toByte(); break }
                }; at++
            }
            file.writeBytes(raw)
            try { engine.extractSelection(token, tree); fail() } catch(_: ArchiveRejected) { }
            assertEquals(1, children(root()).size); sentinel(); noStages(engine)
        } finally { engine.clear() }
    }
}

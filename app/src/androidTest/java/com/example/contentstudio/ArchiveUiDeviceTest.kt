package com.example.contentstudio

import android.app.Activity
import android.app.Instrumentation
import android.app.LocaleManager
import android.content.Intent
import android.net.Uri
import android.os.LocaleList
import android.os.Bundle
import android.provider.DocumentsContract as Docs
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.platform.app.InstrumentationRegistry
import com.example.contentstudio.archive.*
import java.io.File
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*

class ArchiveUiDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var model: ArchiveViewModel
    private fun uri(name: String) = Uri.parse("content://com.example.contentstudio.test.archives/$name")
    @Before fun setup() {
        context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(
            InstrumentationRegistry.getArguments().getString("audit-locale", "en-US"))
        runBlocking { ArchiveEngine(context).clear() }
        Intents.init()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("home-documents").fetchSemanticsNodes().isNotEmpty() }
        scenario!!.onActivity { model = ViewModelProvider(it)[ArchiveViewModel::class.java] }
        idle()
    }
    @After fun cleanup() {
        if (::model.isInitialized) {
            instrumentation.runOnMainSync { model.cancel() }
            compose.waitUntil(15_000) { !model.state.value.busy }
        }
        scenario?.close(); Intents.release()
        runBlocking { ArchiveEngine(context).clear() }
    }
    private fun idle() { compose.waitUntil(30_000) { !model.state.value.busy }; compose.waitForIdle() }
    private fun pick(name: String) {
        Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(Instrumentation.ActivityResult(
            Activity.RESULT_OK, Intent().setData(uri(name)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)))
    }
    private fun row(tag: String): SemanticsNodeInteraction {
        compose.onNodeWithTag("archive-list").performScrollToNode(hasTestTag(tag))
        return compose.onNodeWithTag(tag)
    }
    @Test fun multiSelectionRecreationAndTreePickerExtractOnlySelectedFiles() {
        context.contentResolver.call(uri("safe.zip"), "tree-reset", "normal", null)
        pick("safe.zip")
        compose.onNodeWithTag("home-documents").performScrollTo().performClick()
        compose.onNodeWithTag("home-archive").performClick()
        compose.onNodeWithTag("archive-open").performClick()
        compose.waitUntil(30_000) { model.state.value.session != null && !model.state.value.busy }
        row("archive-select-all").performClick()
        assertEquals(3,model.state.value.selected.size)
        row("archive-select-日本.txt").performClick()
        assertEquals(2,model.state.value.selected.size)
        scenario!!.recreate(); idle()
        assertEquals(2,model.state.value.selected.size)
        val tree = android.provider.DocumentsContract.buildTreeDocumentUri("com.example.contentstudio.test.archive.tree", "root")
        Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT_TREE)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(tree)))
        compose.onNodeWithTag("archive-extract").performClick()
        compose.waitUntil(30_000) { model.state.value.extracted != null && !model.state.value.busy }
        assertEquals(2,model.state.value.completed)
        assertNull(model.state.value.error)
        row("archive-extracted").assertExists(); shot()
    }
    @Test fun realSystemTreePickerPreservesNestedPaths() {
        pick("safe.zip")
        compose.onNodeWithTag("home-documents").performScrollTo().performClick()
        compose.onNodeWithTag("home-archive").performClick()
        compose.onNodeWithTag("archive-open").performClick()
        compose.waitUntil(30_000) { model.state.value.session != null && !model.state.value.busy }
        row("archive-select-all").performClick()
        compose.onNodeWithTag("archive-extract").performClick()
        compose.waitUntil(15_000) { Intents.getIntents().any { it.action == Intent.ACTION_OPEN_DOCUMENT_TREE } }
        fun click(node: AccessibilityNodeInfo) {
            var target: AccessibilityNodeInfo? = node
            while(target != null && !target.isClickable) target = target.parent
            assertTrue(checkNotNull(target).performAction(AccessibilityNodeInfo.ACTION_CLICK))
        }
        // The real picker can restore its previously selected directory.
        val location = nativeNode {
            (it.text?.toString() == "Birlevo-folder-QA" &&
                (it.viewIdResourceName == "android:id/title" || it.viewIdResourceName?.endsWith(":id/breadcrumb_text") == true)) ||
                (it.text?.toString() == "Download" && it.viewIdResourceName == "android:id/title")
        }
        if(location.text?.toString() == "Download") {
            click(location)
            click(nativeNode { it.text?.toString() == "Birlevo-folder-QA" && it.viewIdResourceName == "android:id/title" })
        } else if(location.viewIdResourceName == "android:id/title") click(location)
        click(nativeNode { it.text?.toString()?.equals("Use this folder", ignoreCase = true) == true })
        click(nativeNode { it.text?.toString()?.equals("Allow", ignoreCase = true) == true })
        compose.waitUntil(30_000) { model.state.value.extracted != null && !model.state.value.busy }
        val result = model.state.value.extracted!!
        try {
            assertNull(model.state.value.error); assertEquals(3,model.state.value.completed)
            assertTrue(Docs.getDocumentId(result).contains("Download/Birlevo-folder-QA/"))
            fun children(parent: Uri): List<Uri> = context.contentResolver.query(
                Docs.buildChildDocumentsUriUsingTree(result, Docs.getDocumentId(parent)),
                arrayOf(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID), null,null,null)!!.use { c ->
                buildList { while(c.moveToNext()) add(Docs.buildDocumentUriUsingTree(result,c.getString(0))) }
            }
            val all = children(result)
            val nested = all.single { Docs.getDocumentId(it).endsWith("/folder") }
            assertArrayEquals("Exact original: folder/hello.txt".toByteArray(),
                context.contentResolver.openInputStream(children(nested).single())!!.use { it.readBytes() })
            assertEquals(3, all.size)
        } finally { android.provider.DocumentsContract.deleteDocument(context.contentResolver,result) }
    }
    @Test fun genericPickerRouteSaveRecreationAndClearKeepOriginal() {
        pick("safe.zip")
        compose.onNodeWithTag("home-open-file").performScrollTo().performClick()
        instrumentation.waitForIdleSync()
        instrumentation.uiAutomation.waitForIdle(500, 5000)
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("file-action-ARCHIVE").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("file-action-ARCHIVE").performClick()
        compose.waitUntil(30_000) { model.state.value.session != null && !model.state.value.busy }
        val revision = model.state.value.session!!.revision
        compose.onNodeWithText(context.getString(R.string.archive_hint)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.workspace_help)).performClick()
        compose.onNodeWithText(context.getString(R.string.archive_hint)).assertExists()
        compose.onNodeWithText(context.getString(R.string.workspace_help)).performClick()
        scenario!!.recreate(); idle()
        assertEquals(revision, model.state.value.session!!.revision)
        Intents.intending(hasAction(Intent.ACTION_CREATE_DOCUMENT)).respondWith(Instrumentation.ActivityResult(
            Activity.RESULT_OK, Intent().setData(uri("export.bin"))))
        row("archive-save-folder/hello.txt").performClick()
        compose.waitUntil(30_000) { model.state.value.saved != null && !model.state.value.busy }
        assertArrayEquals("Exact original: folder/hello.txt".toByteArray(), context.contentResolver.openInputStream(uri("export.bin"))!!.use { it.readBytes() })
        compose.onNodeWithTag("archive-saved").assertExists()
        shot()
        compose.onNodeWithTag("archive-clear").performClick()
        compose.onAllNodesWithText(context.getString(R.string.clear_session)).onLast().performClick()
        compose.waitUntil(15_000) { model.state.value.session == null && !model.state.value.busy }
        assertNotNull(context.contentResolver.openInputStream(uri("safe.zip"))!!.use { it.read() })
    }
    @Test fun documentMenuRejectsUnsafeZipAndRetainsPreviousArchive() {
        compose.onNodeWithTag("home-documents").performScrollTo().performClick()
        compose.onNodeWithTag("home-archive").performClick()
        pick("safe.zip"); compose.onNodeWithTag("archive-open").performClick()
        compose.waitUntil(30_000) { model.state.value.session != null && !model.state.value.busy }
        val revision = model.state.value.session!!.revision
        pick("unsafe.zip"); compose.onNodeWithTag("archive-open").performClick()
        compose.waitUntil(30_000) { model.state.value.error != null && !model.state.value.busy }
        assertEquals(ArchiveIssue.PATH, model.state.value.error)
        assertEquals(revision, model.state.value.session!!.revision)
        row("archive-error").assertExists()
        compose.onNodeWithTag("archive-back").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("home-documents").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("home-documents").performScrollTo().performClick()
        compose.onNodeWithTag("home-archive").performClick(); idle()
        assertEquals(revision, model.state.value.session!!.revision)
        assertNull(model.state.value.error)
    }
    @Test fun realSystemPickerSavesTwoCopiesWithoutOverwriting() {
        compose.onNodeWithTag("home-documents").performScrollTo().performClick()
        compose.onNodeWithTag("home-archive").performClick()
        pick("safe.zip"); compose.onNodeWithTag("archive-open").performClick()
        compose.waitUntil(30_000) { model.state.value.session != null && !model.state.value.busy }
        val name = "birlevo-zip-qa-${System.nanoTime()}.txt"
        val saved = mutableListOf<Uri>()
        try {
            repeat(2) {
                val previous = model.state.value.saved
                row("archive-save-folder/hello.txt").performClick()
                val field = nativeNode { it.className?.toString() == "android.widget.EditText" }
                assertTrue(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, name)
                }))
                val create = nativeNode { it.isClickable && (it.viewIdResourceName?.endsWith(":id/button1") == true ||
                    it.viewIdResourceName?.endsWith(":id/create") == true) }
                assertTrue(create.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                compose.waitUntil(30_000) { model.state.value.saved != null && model.state.value.saved != previous && !model.state.value.busy &&
                    compose.onAllNodesWithTag("archive-open").fetchSemanticsNodes().isNotEmpty() }
                saved += model.state.value.saved!!
            }
            assertNotEquals(saved[0], saved[1])
            for (uri in saved) assertArrayEquals("Exact original: folder/hello.txt".toByteArray(),
                context.contentResolver.openInputStream(uri)!!.use { it.readBytes() })
        } finally { saved.forEach { runCatching { android.provider.DocumentsContract.deleteDocument(context.contentResolver, it) } } }
    }
    @Test fun coldRestoreWaitsForPendingSaveAndCancellationDoesNotWrite() {
        val session = runBlocking { ArchiveEngine(context).open(uri("safe.zip")) }
        lateinit var cold: ArchiveViewModel
        instrumentation.runOnMainSync {
            cold = ArchiveViewModel(context.applicationContext as android.app.Application)
            cold.reload()
            cold.save(session.revision, "folder/hello.txt", uri("export.bin"))
        }
        compose.waitUntil(30_000) { cold.state.value.saved != null && !cold.state.value.busy }
        val original = context.contentResolver.openInputStream(uri("export.bin"))!!.use { it.readBytes() }
        lateinit var cancelled: ArchiveViewModel
        instrumentation.runOnMainSync {
            cancelled = ArchiveViewModel(context.applicationContext as android.app.Application)
            cancelled.reload()
            cancelled.save(session.revision, "日本.txt", uri("export.bin"))
            cancelled.cancel()
        }
        compose.waitUntil(15_000) { !cancelled.state.value.busy }
        assertArrayEquals(original, context.contentResolver.openInputStream(uri("export.bin"))!!.use { it.readBytes() })
        instrumentation.runOnMainSync { cold.cancel(); cancelled.cancel() }
    }
    @Test fun coldClearNeedsOnlyMetadataAndNeverStartsArchiveScan() = runBlocking {
        val engine = ArchiveEngine(context)
        val session = engine.open(uri("safe.zip"))
        // Same-length damage: a scan would fail; explicit clear must not need to inflate it.
        val file = File(engine.folder, session.file)
        val bytes = file.readBytes(); bytes[0] = 0; file.writeBytes(bytes)
        withContext(Dispatchers.Main) {
            val cold = ArchiveViewModel(context.applicationContext as android.app.Application)
            assertFalse(cold.state.value.busy)
            assertTrue(cold.clearSavedWork())
        }
        assertNull(engine.read())
    }
    private fun nativeNode(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        fun find(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (predicate(node)) return node
            for (i in 0 until node.childCount) node.getChild(i)?.let { find(it)?.let { result -> return result } }
            return null
        }
        val deadline = System.currentTimeMillis() + 15000
        while (System.currentTimeMillis() < deadline) {
            instrumentation.uiAutomation.rootInActiveWindow?.let { find(it)?.let { result -> return result } }
            Thread.sleep(50)
        }
        val root = instrumentation.uiAutomation.rootInActiveWindow
        val lines = mutableListOf<String>()
        fun dump(node: AccessibilityNodeInfo) {
            if (lines.size >= 300) return
            lines.add("${node.packageName} | ${node.text} | ${node.contentDescription} | ${node.viewIdResourceName}")
            for(i in 0 until node.childCount) node.getChild(i)?.let(::dump)
        }
        root?.let(::dump)
        File(context.getExternalFilesDir(null), "qa/archive/native-failure.txt").apply { parentFile!!.mkdirs(); writeText(lines.joinToString("\n")) }
        error("Native document control unavailable")
    }
    private fun shot() {
        compose.waitForIdle(); instrumentation.uiAutomation.waitForIdle(500, 5000)
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: error("screenshot unavailable")
        val name = InstrumentationRegistry.getArguments().getString("audit-case", "phone")
        val file = File(context.getExternalFilesDir(null), "qa/archive/$name.png").apply { parentFile!!.mkdirs() }
        try { file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }
}

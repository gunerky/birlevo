package com.example.contentstudio

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.example.contentstudio.data.*
import com.example.contentstudio.media.*
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaOutputsDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val store get() = MediaOutputs(context)
    private fun fixture(name: String) = Uri.parse("content://com.example.contentstudio.test.fixtures/$name")
    private fun hash(uri: Uri) = context.contentResolver.openInputStream(uri)!!.use {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(65536)
        while (true) { val size = it.read(buffer); if (size < 0) break; digest.update(buffer, 0, size) }
        digest.digest().toList()
    }

    @Test fun projectImageSurvivesDeletedSourceAndShareCacheAndRepositoryReload() = runBlocking<Unit> {
        val image = ImageCompressor(context).importImage(fixture("orientation-6.jpg"))
        val shared = withContext(Dispatchers.IO) { store.stage(image.file, "image/jpeg", "photo.jpg") }
        val original = hash(shared.uri)
        val owned = withContext(Dispatchers.IO) { store.retainImage(shared.uri) }
        val project = StudioProject("owned-test", ProjectKind.PRESENTATION, "Owned", 1, listOf(Slide(imageUri = owned.toString())))
        val repository = ProjectRepository(context)
        val previous = repository.load()
        try {
            repository.save(listOf(project))
            image.file.delete()
            File(context.cacheDir, "shared-outputs").deleteRecursively()
            assertEquals(original, hash(owned))
            assertEquals(project, ProjectRepository(context).load().single())
            val bitmap = SlideImages.decode(context, owned, 1024)
            assertEquals(80, bitmap.width); assertEquals(120, bitmap.height); bitmap.recycle()
            val pdf = PresentationPdf(context).create(repository.load().single()) {}
            assertEquals(1, pdf.pages)
            val page = PresentationPdf(context).render(pdf, 0)
            assertTrue(page.width > 0); page.recycle(); pdf.file.delete()
        } finally { repository.save(previous) }
    }

    @Test fun nonSavingSnapshotsRequireApprovalBeforeAnyFileIsCreated() = runBlocking<Unit> {
        val source = ImageCompressor(context).importImage(fixture("tiny.png"))
        val engine = ImageCompressor(context)
        val result = engine.compress(source, 80, 1280)
        try {
            assertTrue(result.bytes > source.bytes)
            for (originalBytes in listOf(source.bytes, result.bytes)) {
                try { store.stage(result.file, "image/jpeg", "bigger.jpg", originalBytes); fail("Approval required") }
                catch (_: IllegalArgumentException) { }
            }
            val copy = store.stage(result.file, "image/jpeg", "bigger.jpg", source.bytes, true)
            assertEquals(result.bytes, copy.bytes)
            assertEquals(result.file.readBytes().toList(), context.contentResolver.openInputStream(copy.uri)!!.use { it.readBytes().toList() })
        } finally { source.file.delete(); result.file.delete() }
    }

    @Test fun intentsCarryCorrectTypeContentUriClipDataAndReadOnlyGrant() = runBlocking<Unit> {
        val image = ImageCompressor(context).importImage(fixture("orientation-6.jpg"))
        try {
            val output = store.stage(image.file, "image/jpeg", "../../private.jpg")
            for (intent in listOf(output.viewIntent(), output.shareIntent())) {
                assertEquals("image/jpeg", intent.type)
                assertEquals("content", output.uri.scheme)
                assertEquals(output.uri, intent.clipData!!.getItemAt(0).uri)
                assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
                assertEquals(0, intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                assertEquals(0, intent.flags and Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
            }
            assertEquals(output.uri, output.viewIntent().data)
            assertEquals(output.uri, output.shareIntent().getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
            val provider = context.packageManager.resolveContentProvider("${context.packageName}.outputs", 0)!!
            assertFalse(provider.exported); assertTrue(provider.grantUriPermissions)
            try {
                androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.outputs",
                    File(context.filesDir, "projects.json"))
                fail("Private project metadata must not be shareable")
            } catch (_: IllegalArgumentException) { }
        } finally { image.file.delete() }
    }

    @Test fun anotherApplicationCanReadTheExplicitlyGrantedSnapshot() = runBlocking<Unit> {
        val image = ImageCompressor(context).importImage(fixture("noise.jpg"))
        try {
            val output = store.stage(image.file, "image/jpeg", "share.jpg")
            val expected = hash(output.uri)
            context.contentResolver.delete(fixture("export.jpg"), null, null)
            context.startActivity(output.shareIntent().setComponent(ComponentName(instrumentation.context.packageName,
                OutputReceiverActivity::class.java.name)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            withTimeout(15_000) {
                while (runCatching { hash(fixture("export.jpg")) }.getOrNull() != expected) delay(100)
            }
            assertEquals(expected, hash(fixture("export.jpg")))
        } finally { image.file.delete() }
    }

    @Test fun invalidProjectAssetIsRejectedWithoutCommittingFiles() = runBlocking<Unit> {
        val folder = File(context.filesDir, "project-images")
        val before = folder.list()?.toSet() ?: emptySet()
        try { store.retainImage(fixture("invalid.jpg")); fail("Invalid image accepted") }
        catch (_: IllegalArgumentException) { }
        assertEquals(before, folder.list()?.toSet() ?: emptySet<String>())
    }

    @Test fun failedProjectSaveKeepsOwnedImageAndCanRetryWithoutSource() = runBlocking<Unit> {
        var failWrites = true
        var saved = emptyList<StudioProject>()
        val storage = object : ProjectStorage {
            override fun load() = emptyList<StudioProject>()
            override fun save(projects: List<StudioProject>) {
                if (failWrites) throw java.io.IOException("Disk full")
                saved = projects
            }
        }
        val models = ViewModelStore()
        val editor = withContext(Dispatchers.Main) {
            ProjectEditorViewModel(storage, SavedStateHandle(), store).also { models.put("owned-editor", it) }
        }
        try {
            withTimeout(10_000) { while (editor.state.value.loading) delay(50) }
            val image = ImageCompressor(context).importImage(fixture("orientation-6.jpg"))
            val share = store.stage(image.file, "image/jpeg", "photo.jpg")
            val expected = hash(share.uri)
            assertTrue(withContext(Dispatchers.Main) { editor.addPresentationImage(share.uri, null, "Retry photo") })
            assertEquals(DraftSaveStatus.ERROR, editor.state.value.saveStatus)
            assertTrue(saved.isEmpty())
            val owned = Uri.parse(editor.state.value.draft!!.slides.single().imageUri)
            image.file.delete(); File(context.cacheDir, "shared-outputs").deleteRecursively()
            assertEquals(expected, hash(owned))
            failWrites = false
            withContext(Dispatchers.Main) { editor.flush() }
            withTimeout(10_000) { while (editor.state.value.saveStatus != DraftSaveStatus.SAVED) delay(50) }
            assertEquals(owned.toString(), saved.single().slides.single().imageUri)
            assertEquals(expected, hash(owned))
        } finally { withContext(Dispatchers.Main) { models.clear() } }
    }
}

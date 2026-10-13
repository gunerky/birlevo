package com.example.contentstudio

import android.app.LocaleManager
import android.net.Uri
import android.os.LocaleList
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.ViewModelProvider
import com.example.contentstudio.audio.AudioToolViewModel
import com.example.contentstudio.data.*
import com.example.contentstudio.media.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ProjectManagementDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private var scenario: ActivityScenario<MainActivity>? = null
    private val repository get() = ProjectRepository(context)
    private val first = StudioProject("delete-first", ProjectKind.PRESENTATION, "First project", 1,
        listOf(Slide("A")))
    private val second = first.copy(id = "delete-second", title = "Second project", updatedAt = 2)
    @Before fun setup() {
        context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags("en-US")
        for (kind in listOf("video-compression", "audio-conversion", "photo-pdf", SlideshowEngine.FOLDER))
            AtomicMediaSession(File(context.noBackupFilesDir, kind)).write(null)
        repository.save(listOf(first, second))
    }
    @After fun finish() { scenario?.close(); repository.save(emptyList()) }
    private fun launch() { scenario = ActivityScenario.launch(MainActivity::class.java) }
    private fun scrollTo(tag: String) {
        // Off-screen lazy rows do not exist in the semantics tree until scrolled into view.
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag(tag.removeSuffix("-manage")))
    }
    private fun awaitRemoval(check: () -> Boolean) {
        compose.waitForIdle()
        try { compose.waitUntil(10_000, check) }
        catch (error: Throwable) {
            throw AssertionError("Removal timed out; saved=${repository.load().map { it.id }}\n" +
                "sessions=" + listOf("audio-conversion", "photo-pdf", SlideshowEngine.FOLDER).associateWith {
                    AtomicMediaSession(File(context.noBackupFilesDir, it)).read()?.take(600)
                } + "\n" +
                compose.onAllNodes(isRoot(), useUnmergedTree = true).printToString(maxDepth = 25), error)
        }
    }
    private fun manage(id: String) {
        scrollTo("project-$id")
        compose.onNodeWithTag("project-$id-manage", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithTag("work-delete-request").performClick()
    }

    @Test fun cancelRotationAndConfirmedRemovalSurviveRelaunch() {
        launch(); manage(first.id)
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(2, repository.load().size)
        manage(first.id); scenario!!.recreate()
        compose.onNodeWithTag("work-delete-confirm").performClick()
        awaitRemoval { repository.load().none { it.id == first.id } }
        compose.onNodeWithTag("project-${first.id}").assertDoesNotExist()
        scenario!!.close(); launch()
        scrollTo("project-${second.id}")
        compose.onNodeWithTag("project-${second.id}").assertExists()
        compose.onNodeWithTag("project-${first.id}").assertDoesNotExist()
    }

    @Test fun deletionPreservesSharedProjectImageOriginalAndExportedCopy() = runBlocking<Unit> {
        val original = Uri.parse("content://com.example.contentstudio.test.fixtures/orientation-6.jpg")
        val originalBytes = context.contentResolver.openInputStream(original)!!.use { it.readBytes().toList() }
        val outputs = MediaOutputs(context)
        val owned = outputs.retainImage(original)
        val source = ImageCompressor(context).importImage(original)
        val exported = outputs.stage(source.file, "image/jpeg", "kept.jpg")
        val exportedBytes = context.contentResolver.openInputStream(exported.uri)!!.use { it.readBytes().toList() }
        val slides = listOf(Slide("Shared", imageUri = owned.toString()))
        repository.save(listOf(first.copy(slides = slides), second.copy(slides = slides)))
        launch(); manage(first.id)
        compose.onNodeWithTag("work-delete-confirm").performClick()
        awaitRemoval { repository.load().size == 1 }
        assertEquals(owned.toString(), repository.load().single().slides.single().imageUri)
        assertEquals(originalBytes, context.contentResolver.openInputStream(original)!!.use { it.readBytes().toList() })
        assertEquals(originalBytes, context.contentResolver.openInputStream(owned)!!.use { it.readBytes().toList() })
        assertEquals(exportedBytes, context.contentResolver.openInputStream(exported.uri)!!.use { it.readBytes().toList() })
        source.file.delete()
    }

    @Test fun clearPhotoWorkFromHomeKeepsProjectsAndDoesNotReturnAfterRelaunch() = runBlocking<Unit> {
        val engine = PhotoPdf(context)
        val original = Uri.parse("content://com.example.contentstudio.test.fixtures/orientation-6.jpg")
        val page = engine.importPages(listOf(original), PhotoPdf.MAX_PAGES, PhotoPdf.MAX_BATCH_BYTES) {}.single()
        val folder = File(context.noBackupFilesDir, "photo-pdf")
        AtomicMediaSession(folder).write(MediaSessionCodec.photos(PhotoSession(listOf(MediaSessionCodec.asset(page)), 2048)))
        launch()
        scrollTo("resume-photo-pdf")
        compose.onNodeWithTag("resume-photo-pdf-manage", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithTag("work-delete-request").performClick()
        compose.onNodeWithTag("work-delete-confirm").performClick()
        awaitRemoval { AtomicMediaSession(folder).read() == null }
        compose.onNodeWithTag("resume-photo-pdf").assertDoesNotExist()
        assertEquals(2, repository.load().size)
        assertTrue(context.contentResolver.openInputStream(original)!!.use { it.read() } >= 0)
        scenario!!.close(); launch()
        compose.onNodeWithTag("resume-photo-pdf").assertDoesNotExist()
    }

    private fun clearFromHome(kind: String) {
        scrollTo("resume-$kind")
        compose.onNodeWithTag("resume-$kind-manage", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithTag("work-delete-request").performClick()
        compose.onNodeWithTag("work-delete-confirm").performClick()
        awaitRemoval { AtomicMediaSession(File(context.noBackupFilesDir, kind)).read() == null }
        compose.onNodeWithTag("resume-$kind").assertDoesNotExist()
    }

    @Test fun clearAudioFromHomePreservesOriginalAndSavedProjects() = runBlocking<Unit> {
        launch()
        lateinit var audio: AudioToolViewModel
        scenario!!.onActivity { audio = ViewModelProvider(it)[AudioToolViewModel::class.java] }
        compose.waitUntil(10_000) { !audio.state.value.busy }
        val original = Uri.parse("content://com.example.contentstudio.test.audio/tone.wav")
        val bytes = context.contentResolver.openInputStream(original)!!.use { it.readBytes().toList() }
        withContext(Dispatchers.Main) { audio.load(original) }
        compose.waitUntil(10_000) { !audio.state.value.busy }
        assertNotNull(audio.state.value.source)
        val owned = audio.state.value.source!!.file
        assertTrue(withContext(Dispatchers.Main) { audio.awaitSession() })
        scenario!!.close(); launch(); clearFromHome("audio-conversion")
        assertFalse(owned.exists())
        assertEquals(2, repository.load().size)
        assertEquals(bytes, context.contentResolver.openInputStream(original)!!.use { it.readBytes().toList() })
    }

    @Test fun clearSlideshowFromHomePreservesOriginalAndSavedProjects() = runBlocking<Unit> {
        launch()
        lateinit var slideshow: SlideshowViewModel
        scenario!!.onActivity { slideshow = ViewModelProvider(it)[SlideshowViewModel::class.java] }
        compose.waitUntil(10_000) { !slideshow.state.value.busy }
        val original = Uri.parse("content://com.example.contentstudio.test.fixtures/orientation-6.jpg")
        val bytes = context.contentResolver.openInputStream(original)!!.use { it.readBytes().toList() }
        withContext(Dispatchers.Main) { slideshow.add(listOf(original)) }
        compose.waitUntil(10_000) { !slideshow.state.value.busy }
        assertEquals(1, slideshow.state.value.timeline.frames.size)
        assertTrue(withContext(Dispatchers.Main) { slideshow.awaitSession() })
        scenario!!.close(); launch(); clearFromHome(SlideshowEngine.FOLDER)
        assertEquals(2, repository.load().size)
        assertEquals(bytes, context.contentResolver.openInputStream(original)!!.use { it.readBytes().toList() })
    }
}

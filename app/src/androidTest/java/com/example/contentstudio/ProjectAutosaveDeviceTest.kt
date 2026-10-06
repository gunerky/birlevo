package com.example.contentstudio

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.contentstudio.data.*
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProjectAutosaveDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val store = ViewModelStore()
    private lateinit var model: ProjectEditorViewModel

    private class Storage : ProjectStorage {
        val saved = AtomicReference<List<StudioProject>>(emptyList())
        @Volatile var failWrite = false
        @Volatile var failRead = false
        @Volatile var blockWrite = false
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        override fun load(): List<StudioProject> {
            if (failRead) throw IOException("Unreadable projects")
            return saved.get()
        }
        override fun save(projects: List<StudioProject>) {
            if (blockWrite) {
                entered.countDown()
                check(release.await(10, TimeUnit.SECONDS))
            }
            if (failWrite) throw IOException("Disk full")
            saved.set(projects)
        }
    }

    private fun launch(storage: Storage, handle: SavedStateHandle = SavedStateHandle()) {
        instrumentation.runOnMainSync {
            model = ProjectEditorViewModel(storage, handle)
            store.put("test-editor", model)
        }
        compose.waitUntil(10_000) { !model.state.value.loading }
    }

    @After fun clear() { instrumentation.runOnMainSync { store.clear() } }

    @Test fun editsDuringSlowWritePersistLatestRevisionAndImageCallbackUsesLatestOrder() {
        val storage = Storage()
        launch(storage)
        val original = StudioProject("test", ProjectKind.PRESENTATION, "First", 1,
            listOf(Slide("A", id = "a"), Slide("B", id = "b")))
        storage.blockWrite = true
        instrumentation.runOnMainSync { model.edit(original); model.flush() }
        assertTrue(storage.entered.await(10, TimeUnit.SECONDS))
        val reordered = original.copy(title = "Latest", slides = original.slides.reversed())
        try {
            instrumentation.runOnMainSync {
                model.edit(reordered)
                model.setSlideImage("a", "content://images/42")
                model.flush()
            }
        } finally { storage.blockWrite = false; storage.release.countDown() }
        compose.waitUntil(10_000) {
            storage.saved.get().singleOrNull()?.let { it.title == "Latest" && it.slides[1].imageUri != null } == true &&
                model.state.value.saveStatus == DraftSaveStatus.SAVED
        }
        val saved = storage.saved.get().single()
        assertEquals(listOf("b", "a"), saved.slides.map { it.id })
        assertEquals("content://images/42", saved.slides[1].imageUri)
        assertEquals(null, saved.slides[0].imageUri)
        // A delayed picker result for a removed slide must not restore it or revert other edits.
        instrumentation.runOnMainSync {
            model.edit(model.state.value.draft!!.copy(slides = listOf(saved.slides[0])))
            model.setSlideImage("a", "content://images/stale")
            model.flush()
        }
        compose.waitUntil(10_000) { storage.saved.get().single().slides.size == 1 }
        assertEquals("b", storage.saved.get().single().slides.single().id)
    }

    @Test fun failedSaveKeepsDraftOpenAndPreviouslySavedDataUntilRetry() {
        val storage = Storage()
        val old = StudioProject("test", ProjectKind.PRESENTATION, "Old", 1, listOf(Slide("Old")))
        storage.saved.set(listOf(old))
        launch(storage)
        storage.failWrite = true
        instrumentation.runOnMainSync { model.edit(old.copy(title = "New")); model.saveAndClose("Presentation") }
        compose.waitUntil(10_000) { model.state.value.saveStatus == DraftSaveStatus.ERROR && !model.state.value.closing }
        assertEquals("New", model.state.value.draft!!.title)
        assertEquals(listOf(old), storage.saved.get())
        storage.failWrite = false
        instrumentation.runOnMainSync { model.saveAndClose("Presentation") }
        compose.waitUntil(10_000) { model.state.value.draft == null }
        assertEquals("New", storage.saved.get().single().title)
    }

    @Test fun restoredDraftWaitsForSuccessfulLoadBeforeWriting() {
        val storage = Storage().apply { failRead = true }
        val restored = StudioProject("restored", ProjectKind.PRESENTATION, "Restored", 1, listOf(Slide("A")))
        launch(storage, SavedStateHandle(mapOf("project-draft" to ProjectCodec.encode(listOf(restored)))))
        assertTrue(model.state.value.loadFailed)
        instrumentation.runOnMainSync { model.flush(); model.saveAndClose("Presentation") }
        assertTrue(storage.saved.get().isEmpty())
        assertEquals(restored, model.state.value.draft)
        storage.failRead = false
        instrumentation.runOnMainSync { model.load() }
        compose.waitUntil(10_000) { storage.saved.get().singleOrNull()?.id == restored.id }
        assertEquals(restored.slides, storage.saved.get().single().slides)
    }

    @Test fun stateCheckpointCapturesLatestTypingAndClosedDraftDoesNotReopen() {
        val storage = Storage()
        val handle = SavedStateHandle()
        launch(storage, handle)
        val latest = StudioProject("checkpoint", ProjectKind.PRESENTATION, "Latest", 1,
            (0 until 100).map { Slide("Heading $it", "Body $it", id = "slide-$it") })
        var restored: SavedStateHandle? = null
        instrumentation.runOnMainSync {
            model.edit(latest.copy(title = "Before"))
            model.edit(latest)
            // Android requests its checkpoint immediately, before the autosave typing delay.
            restored = SavedStateHandle.createHandle(handle.savedStateProvider().saveState(), null)
        }
        val recovered = (restored!!.get<android.os.Bundle>("project-draft")!!).getString("json")!!
        assertEquals(latest, ProjectCodec.decode(recovered).single())
        instrumentation.runOnMainSync { model.saveAndClose("Presentation") }
        compose.waitUntil(10_000) { model.state.value.draft == null }
        instrumentation.runOnMainSync {
            restored = SavedStateHandle.createHandle(handle.savedStateProvider().saveState(), null)
        }
        assertNull(restored!!.get<android.os.Bundle>("project-draft")!!.getString("json"))
    }
}

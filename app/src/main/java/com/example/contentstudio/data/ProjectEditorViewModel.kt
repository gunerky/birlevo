package com.example.contentstudio.data

import android.os.Bundle
import android.net.Uri
import com.example.contentstudio.media.MediaOutputs
import com.example.contentstudio.media.ImportIssue
import com.example.contentstudio.media.importIssue
import java.util.UUID
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

enum class DraftSaveStatus { PENDING, SAVING, SAVED, ERROR }

data class ProjectEditorState(
    val projects: List<StudioProject> = emptyList(),
    val draft: StudioProject? = null,
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val closing: Boolean = false,
    val importingImage: Boolean = false,
    val saveStatus: DraftSaveStatus = DraftSaveStatus.SAVED,
)

/** One writer survives Activity recreation. Edits never cancel an AtomicFile write in flight. */
class ProjectEditorViewModel(
    private val storage: ProjectStorage,
    private val savedState: SavedStateHandle,
    private val mediaOutputs: MediaOutputs? = null,
) : ViewModel() {
    // Accept the String checkpoint written by earlier versions as well as the lazy Bundle.
    private val restoredDraft = when (val checkpoint = savedState.get<Any>("project-draft")) {
        is String -> checkpoint
        is Bundle -> checkpoint.getString("json")
        else -> null
    }?.let { ProjectCodec.decode(it).single() }
    private val mutableState = MutableStateFlow(ProjectEditorState(
        draft = restoredDraft,
        saveStatus = if (restoredDraft == null) DraftSaveStatus.SAVED else DraftSaveStatus.PENDING,
    ))
    val state = mutableState.asStateFlow()
    private val writes = Mutex()
    private val requests = Channel<Unit>(Channel.CONFLATED)
    private var revision = 0L
    private var persistedRevision = -1L

    init {
        // Serialize at Android's state-save boundary, not for every character typed.
        // The latest in-memory draft is captured even if the autosave debounce has not elapsed.
        savedState.setSavedStateProvider("project-draft") {
            Bundle().apply {
                mutableState.value.draft?.let { putString("json", ProjectCodec.encode(listOf(it))) }
            }
        }
        load()
        viewModelScope.launch {
            for (request in requests) {
                // Wait for a quiet typing interval, without cancelling writes already started.
                while (withTimeoutOrNull(600L) { requests.receive(); true } == true) Unit
                persist()
            }
        }
    }

    fun load() {
        if (!mutableState.value.loading && !mutableState.value.loadFailed) return
        mutableState.value = mutableState.value.copy(loading = true)
        viewModelScope.launch {
            try {
                val projects = withContext(Dispatchers.IO) { storage.load().sortedByDescending { it.updatedAt } }
                mutableState.value = mutableState.value.copy(projects = projects, loading = false, loadFailed = false)
                if (mutableState.value.draft?.kind == ProjectKind.PRESENTATION) requests.trySend(Unit)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableState.value = mutableState.value.copy(loading = false, loadFailed = true) }
        }
    }

    fun edit(project: StudioProject) {
        val current = mutableState.value
        if (current.loading || current.loadFailed || current.closing || current.draft == project) return
        revision++
        mutableState.value = current.copy(draft = project, saveStatus = DraftSaveStatus.PENDING)
        if (project.kind == ProjectKind.PRESENTATION) requests.trySend(Unit)
    }

    fun setSlideImage(id: String, uri: String) {
        val project = mutableState.value.draft ?: return
        if (project.slides.none { it.id == id }) return
        edit(project.copy(slides = project.slides.map { if (it.id == id) it.copy(imageUri = uri) else it }))
    }

    /** The ViewModel owns the import, so rotation cannot strand an in-flight picker result. */
    fun importSlideImage(projectId: String, slideId: String, uri: Uri, onError: (ImportIssue?) -> Unit) {
        val current = state.value
        if (current.loading || current.loadFailed || current.importingImage || current.closing || current.draft?.id != projectId ||
            current.draft.slides.none { it.id == slideId }) return
        mutableState.value = current.copy(importingImage = true)
        viewModelScope.launch {
            var owned: Uri? = null
            var attached = false
            try {
                withContext(Dispatchers.IO) { owned = checkNotNull(mediaOutputs).retainImage(uri) }
                if (state.value.draft?.id == projectId) {
                    setSlideImage(slideId, checkNotNull(owned).toString())
                    attached = state.value.draft?.slides?.any { it.id == slideId && it.imageUri == owned.toString() } == true
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { onError(importIssue(error)) }
            catch (_: OutOfMemoryError) { onError(ImportIssue.MEMORY) }
            finally {
                if (!attached) withContext(NonCancellable + Dispatchers.IO) {
                    owned?.let { runCatching { mediaOutputs?.discardUnattachedImage(it) } }
                }
                mutableState.value = state.value.copy(importingImage = false)
            }
        }
    }

    /** Appends a fresh slide; existing text, images, ordering and theme are preserved. */
    suspend fun addPresentationImage(uri: Uri, targetId: String?, title: String): Boolean {
        val current = state.value
        if (current.loading || current.loadFailed || current.closing || current.importingImage || current.draft != null) return false
        val target = if (targetId == null) StudioProject(UUID.randomUUID().toString(),
            ProjectKind.PRESENTATION, title, System.currentTimeMillis())
        else current.projects.find { it.id == targetId && it.kind == ProjectKind.PRESENTATION } ?: return false
        if (target.slides.size >= SlideOperations.MAX_SLIDES) return false
        mutableState.value = current.copy(importingImage = true)
        var owned: Uri? = null
        var attached = false
        try {
            withContext(Dispatchers.IO) { owned = checkNotNull(mediaOutputs).retainImage(uri) }
            if (state.value.draft != null) return false
            edit(target.copy(slides = target.slides + Slide(imageUri = owned.toString())))
            attached = state.value.draft?.slides?.any { it.imageUri == owned.toString() } == true
            if (!attached) return false
            // A failed write leaves the complete draft open with its retry action.
            persist()
            return true
        } finally {
            if (!attached) withContext(NonCancellable + Dispatchers.IO) {
                owned?.let { runCatching { mediaOutputs?.discardUnattachedImage(it) } }
            }
            mutableState.value = state.value.copy(importingImage = false)
        }
    }

    /** Appends copied text, never an ephemeral reader URI. Existing project assets stay untouched. */
    suspend fun addPresentationText(slides: List<Slide>, targetId: String?, title: String): Boolean {
        val current = state.value
        if (current.loading || current.loadFailed || current.closing || current.importingImage || current.draft != null || slides.isEmpty()) return false
        val target = if (targetId == null) StudioProject(UUID.randomUUID().toString(), ProjectKind.PRESENTATION, title, System.currentTimeMillis())
            else current.projects.find { it.id == targetId && it.kind == ProjectKind.PRESENTATION } ?: return false
        if (target.slides.size + slides.size > SlideOperations.MAX_SLIDES) return false
        edit(target.copy(slides = target.slides + slides.map { it.copy(id = UUID.randomUUID().toString(), imageUri = null) }))
        if (state.value.draft?.id != target.id) return false
        persist()
        return true
    }

    fun setMedia(uri: String, name: String?) {
        mutableState.value.draft?.let { edit(it.copy(sourceUri = uri, sourceName = name)) }
    }

    fun flush() {
        if (mutableState.value.draft?.kind == ProjectKind.PRESENTATION) viewModelScope.launch { persist() }
    }

    fun saveAndClose(fallbackTitle: String) {
        val project = mutableState.value.draft ?: return
        if (mutableState.value.closing || mutableState.value.loading || mutableState.value.loadFailed || mutableState.value.importingImage) return
        edit(project.copy(title = project.title.trim().ifBlank { fallbackTitle }))
        mutableState.value = mutableState.value.copy(closing = true)
        viewModelScope.launch {
            if (persist()) {
                savedState["project-draft"] = null
                mutableState.value = mutableState.value.copy(draft = null, closing = false)
            } else mutableState.value = mutableState.value.copy(closing = false)
        }
    }

    private suspend fun persist(): Boolean = writes.withLock {
        val current = mutableState.value
        val project = current.draft ?: return@withLock true
        if (current.loading || current.loadFailed) return@withLock false
        if (persistedRevision == revision) return@withLock true
        val snapshotRevision = revision
        val updated = project.copy(updatedAt = System.currentTimeMillis())
        val next = (current.projects.filterNot { it.id == project.id } + updated).sortedByDescending { it.updatedAt }
        mutableState.value = current.copy(saveStatus = DraftSaveStatus.SAVING)
        try {
            withContext(Dispatchers.IO) { storage.save(next) }
            persistedRevision = snapshotRevision
            mutableState.value = mutableState.value.copy(projects = next,
                saveStatus = if (revision == snapshotRevision) DraftSaveStatus.SAVED else DraftSaveStatus.PENDING)
            true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            mutableState.value = mutableState.value.copy(saveStatus = DraftSaveStatus.ERROR)
            false
        }
    }
}

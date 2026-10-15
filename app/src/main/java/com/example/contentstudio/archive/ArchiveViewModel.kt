package com.example.contentstudio.archive

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ArchiveState(val session: ArchiveSession? = null, val busy: Boolean = false,
    val error: ArchiveIssue? = null, val saved: Uri? = null,
    val selected: Set<String> = emptySet(), val picker: String? = null,
    val completed: Int = 0, val total: Int = 0, val extracted: Uri? = null, val partial: Uri? = null)

class ArchiveViewModel(application: Application) : AndroidViewModel(application) {
    private val engine = ArchiveEngine(application)
    private val mutable = MutableStateFlow(ArchiveState())
    val state = mutable.asStateFlow()
    private var work: Job? = null
    private val operations = mutableSetOf<Job>()

    private fun run(action: suspend () -> Unit) {
        if (work?.isActive == true) return
        track(viewModelScope.launch {
            mutable.value = mutable.value.copy(busy = true, error = null, saved = null, extracted = null, partial = null, total = 0)
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { mutable.value = mutable.value.copy(error = (failure as? ArchiveRejected)?.issue ?: ArchiveIssue.ACCESS) }
            finally { mutable.value = mutable.value.copy(busy = false) }
        })
    }
    fun open(uri: Uri) = run { mutable.value = ArchiveState(engine.open(uri), busy = true) }
    fun reload() = run {
        val session = engine.read()
        mutable.value = if (session?.revision == mutable.value.session?.revision) mutable.value.copy(session = session)
            else ArchiveState(session, busy = true)
        engine.lastExtraction()?.let { (uri, count, complete) ->
            mutable.value = mutable.value.copy(extracted = if (complete) uri else null, partial = if (complete) null else uri,
                completed = count, total = count)
        }
    }
    fun select(path: String, checked: Boolean) {
        if (mutable.value.busy || mutable.value.session?.entries?.none { !it.directory && it.path == path } != false) return
        mutable.value = mutable.value.copy(selected = if (checked) mutable.value.selected + path else mutable.value.selected - path)
    }
    fun selectAll() {
        if (mutable.value.busy) return
        val all = mutable.value.session?.entries?.filterNot { it.directory }?.map { it.path }?.toSet().orEmpty()
        mutable.value = mutable.value.copy(selected = if (mutable.value.selected == all) emptySet() else all)
    }
    fun prepareFolder() = run {
        val state = mutable.value
        val token = engine.prepareSelection(checkNotNull(state.session).revision, state.selected)
        mutable.value = mutable.value.copy(picker = token)
    }
    fun pickerLaunched() { mutable.value = mutable.value.copy(picker = null) }
    fun extract(token: String, tree: Uri) {
        val previous = work
        track(viewModelScope.launch {
            previous?.join()
            mutable.value = mutable.value.copy(busy = true, error = null, saved = null, extracted = null, partial = null, completed = 0, total = 0)
            try {
                if (mutable.value.session == null) mutable.value = mutable.value.copy(session = engine.read())
                val result = engine.extractSelection(token, tree, { done, total ->
                    mutable.value = mutable.value.copy(completed = done, total = total)
                }, { uri -> mutable.value = mutable.value.copy(partial = uri) })
                mutable.value = mutable.value.copy(extracted = result.first, completed = result.second, total = result.second)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { mutable.value = mutable.value.copy(error = (failure as? ArchiveRejected)?.issue ?: ArchiveIssue.ACCESS) }
            finally { mutable.value = mutable.value.copy(busy = false) }
        })
    }
    fun save(revision: String, path: String, uri: Uri) {
        val previous = work
        track(viewModelScope.launch {
            previous?.join()
            mutable.value = mutable.value.copy(busy = true, error = null, saved = null)
            try {
                if (mutable.value.session == null) mutable.value = ArchiveState(engine.read(), busy = true)
                engine.save(revision, path, uri); mutable.value = mutable.value.copy(saved = uri)
            }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { mutable.value = mutable.value.copy(error = (failure as? ArchiveRejected)?.issue ?: ArchiveIssue.ACCESS) }
            finally { mutable.value = mutable.value.copy(busy = false) }
        })
    }
    fun clear() = run { engine.clear(); mutable.value = ArchiveState(busy = true) }
    private fun track(job: Job) {
        work = job; operations.add(job)
        job.invokeOnCompletion { operations.remove(job) }
    }
    fun cancel() { operations.toList().forEach { it.cancel() } }
    suspend fun clearSavedWork(): Boolean {
        if (work?.isActive == true) return false
        return try { engine.clear(); mutable.value = ArchiveState(); true } catch (_: Exception) { false }
    }
}

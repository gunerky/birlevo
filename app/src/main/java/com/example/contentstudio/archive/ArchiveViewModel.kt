package com.example.contentstudio.archive

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ArchiveState(val session: ArchiveSession? = null, val busy: Boolean = false,
    val error: ArchiveIssue? = null, val saved: Uri? = null)

class ArchiveViewModel(application: Application) : AndroidViewModel(application) {
    private val engine = ArchiveEngine(application)
    private val mutable = MutableStateFlow(ArchiveState())
    val state = mutable.asStateFlow()
    private var work: Job? = null
    private val operations = mutableSetOf<Job>()

    private fun run(action: suspend () -> Unit) {
        if (work?.isActive == true) return
        track(viewModelScope.launch {
            mutable.value = mutable.value.copy(busy = true, error = null, saved = null)
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { mutable.value = mutable.value.copy(error = (failure as? ArchiveRejected)?.issue ?: ArchiveIssue.ACCESS) }
            finally { mutable.value = mutable.value.copy(busy = false) }
        })
    }
    fun open(uri: Uri) = run { mutable.value = ArchiveState(engine.open(uri), busy = true) }
    fun reload() = run { mutable.value = ArchiveState(engine.read(), busy = true) }
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

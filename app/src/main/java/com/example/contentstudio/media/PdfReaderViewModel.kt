package com.example.contentstudio.media

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class PdfReaderState(val source: PdfSource? = null, val preview: Bitmap? = null,
    val pageIndex: Int = 0, val image: PdfPageImage? = null, val outputEdge: Int = 2048,
    val busy: Boolean = false, val issue: PdfReadIssue? = null, val saved: Boolean = false)

class PdfReaderViewModel(application: Application, private val savedState: SavedStateHandle) : AndroidViewModel(application) {
    private val engine = PdfReader(application)
    private val mutableState = MutableStateFlow(PdfReaderState())
    val state = mutableState.asStateFlow()
    private var operation: Job? = null
    private var generation = 0

    init {
        val checkpoint = savedState.get<Bundle>(CHECKPOINT)
        val restored = checkpoint?.let { data -> runCatching {
            val folder = File(application.noBackupFilesDir, "pdf-reader")
            val name = checkNotNull(data.getString("file"))
            require(name == File(name).name && name.startsWith("source-") && name.endsWith(".pdf"))
            val file = File(folder, name)
            require(file.isFile && file.length() in 1..PdfReaderPolicy.MAX_BYTES && file.length() == data.getLong("bytes"))
            PdfReaderPolicy.validatePageCount(data.getInt("pages"))
            PdfSource(file, Uri.parse(data.getString("uri")), data.getString("name") ?: "document.pdf", file.length(), data.getInt("pages"))
        }.getOrNull() }
        if (restored != null) {
            val index = checkNotNull(checkpoint).getInt("page").coerceIn(0, restored.pages - 1)
            mutableState.value = PdfReaderState(source = restored, pageIndex = index)
            renderPage(index, force = true)
        } else if (checkpoint != null) { savedState.remove<Bundle>(CHECKPOINT); mutableState.update { it.copy(issue = PdfReadIssue.ACCESS) } }
        viewModelScope.launch(Dispatchers.IO) {
            val cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000
            File(application.noBackupFilesDir, "pdf-reader").listFiles()?.filter {
                it != restored?.file && it.lastModified() < cutoff
            }?.forEach { it.delete() }
        }
    }

    fun load(uri: Uri) {
        if (state.value.busy) return
        runOperation {
            var imported: ImportedPdf? = null
            var committed = false
            val old = state.value
            try {
                withContext(Dispatchers.IO) { imported = engine.importPdf(uri) }
                val result = checkNotNull(imported)
                mutableState.value = PdfReaderState(source = result.source, preview = result.preview, busy = true)
                checkpoint()
                committed = true
                withContext(NonCancellable + Dispatchers.IO) { deleteFiles(old) }
            } finally { if (!committed) withContext(NonCancellable + Dispatchers.IO) {
                imported?.source?.file?.delete(); imported?.preview?.recycle()
            } }
        }
    }

    fun showPage(index: Int) = renderPage(index, force = false)
    private fun renderPage(index: Int, force: Boolean) {
        val source = state.value.source ?: return
        if (state.value.busy || index !in 0 until source.pages || (!force && index == state.value.pageIndex)) return
        runOperation {
            var bitmap: Bitmap? = null
            var committed = false
            try {
                withContext(Dispatchers.IO) { bitmap = engine.preview(source, index); state.value.image?.file?.delete() }
                mutableState.update { it.copy(preview = bitmap, pageIndex = index, image = null) }
                checkpoint()
                committed = true
            } finally { if (!committed) bitmap?.recycle() }
        }
    }

    fun setOutputEdge(edge: Int) {
        require(edge in PdfReaderPolicy.outputEdges)
        if (state.value.busy || edge == state.value.outputEdge) return
        val file = state.value.image?.file
        mutableState.update { it.copy(outputEdge = edge, image = null, issue = null, saved = false) }
        viewModelScope.launch(Dispatchers.IO) { file?.delete() }
    }

    fun createPageImage() {
        val snapshot = state.value
        val source = snapshot.source ?: return
        if (snapshot.busy) return
        runOperation {
            var image: PdfPageImage? = null
            var committed = false
            try {
                withContext(Dispatchers.IO) { image = engine.pageImage(source, snapshot.pageIndex, snapshot.outputEdge); snapshot.image?.file?.delete() }
                mutableState.update { it.copy(image = image) }
                committed = true
            } finally { if (!committed) image?.file?.delete() }
        }
    }

    fun export(uri: Uri, pageImage: Boolean) {
        val snapshot = state.value
        val source = snapshot.source
        val file = if (pageImage) snapshot.image?.file else source?.file
        // A late picker result must never make cleanup delete the original document.
        if (uri == source?.originalUri) {
            mutableState.update { it.copy(issue = PdfReadIssue.EXPORT) }; return
        }
        if (source == null || file == null || snapshot.busy) {
            viewModelScope.launch(Dispatchers.IO) { runCatching { DocumentsContract.deleteDocument(getApplication<Application>().contentResolver, uri) } }
            mutableState.update { it.copy(issue = PdfReadIssue.EXPORT) }; return
        }
        runOperation {
            try { withContext(Dispatchers.IO) { engine.export(file, source.originalUri, uri) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { throw PdfReadException(PdfReadIssue.EXPORT) }
            mutableState.update { it.copy(saved = true) }
        }
    }

    private fun checkpoint() {
        val source = state.value.source ?: return
        savedState[CHECKPOINT] = Bundle().apply {
            putString("file", source.file.name); putString("name", source.name.take(256))
            putString("uri", source.originalUri.toString()); putLong("bytes", source.bytes)
            putInt("pages", source.pages); putInt("page", state.value.pageIndex)
        }
    }
    fun cancel() { operation?.cancel() }
    fun reset() {
        generation++
        val previous = operation
        val old = state.value
        previous?.cancel()
        savedState.remove<Bundle>(CHECKPOINT)
        mutableState.value = PdfReaderState()
        viewModelScope.launch { previous?.join(); withContext(Dispatchers.IO) { deleteFiles(old) } }
    }
    private fun deleteFiles(snapshot: PdfReaderState) { snapshot.source?.file?.delete(); snapshot.image?.file?.delete() }
    private fun runOperation(block: suspend () -> Unit) {
        val token = ++generation
        val previous = operation
        mutableState.update { it.copy(busy = true, issue = null, saved = false) }
        operation = viewModelScope.launch {
            try { previous?.cancelAndJoin(); block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: PdfReadException) { if (token == generation) mutableState.update { it.copy(issue = error.issue) } }
            catch (_: OutOfMemoryError) { if (token == generation) mutableState.update { it.copy(issue = PdfReadIssue.MEMORY) } }
            catch (_: Exception) { if (token == generation) mutableState.update { it.copy(issue = PdfReadIssue.INVALID) } }
            finally { if (token == generation) mutableState.update { it.copy(busy = false) } }
        }
    }
    companion object { private const val CHECKPOINT = "pdf-reader-session" }
}

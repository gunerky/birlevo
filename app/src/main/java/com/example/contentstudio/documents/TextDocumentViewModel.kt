package com.example.contentstudio.documents

import android.app.Application
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.core.net.toUri
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

data class TextDocumentState(val source: DocumentSource? = null, val content: DocumentContent? = null,
    val encoding: DocumentEncoding = DocumentEncoding.AUTO, val kind: DocumentKind? = null, val delimiter: Char? = null,
    val target: DocumentTarget = DocumentTarget.PDF, val result: DocumentResult? = null,
    val previewChunks: List<String> = emptyList(), val busy: Boolean = false, val issue: DocumentIssue? = null,
    val saved: Boolean = false)

class TextDocumentViewModel(application: Application, private val savedState: SavedStateHandle) : AndroidViewModel(application) {
    private val engine = TextDocumentEngine(application)
    private val mutableState = MutableStateFlow(TextDocumentState())
    val state = mutableState.asStateFlow()
    private var operation: Job? = null
    private var generation = 0
    init {
        val data = savedState.get<Bundle>(CHECKPOINT)
        if (data != null) {
            val restored = runCatching {
                val name = checkNotNull(data.getString("file"))
                require(name == File(name).name && name.startsWith("source-") && name.endsWith(".bin"))
                val file = File(application.noBackupFilesDir, "text-documents/$name")
                require(file.isFile && file.length() in 1..DocumentPolicy.MAX_BYTES && file.length() == data.getLong("bytes"))
                val source = DocumentSource(file, checkNotNull(data.getString("uri")).toUri(), data.getString("name") ?: "document.txt", file.length())
                TextDocumentState(source = source, kind = DocumentKind.valueOf(checkNotNull(data.getString("kind"))),
                    encoding = DocumentEncoding.valueOf(checkNotNull(data.getString("encoding"))),
                    delimiter = data.getString("delimiter")?.singleOrNull(), target = DocumentTarget.valueOf(checkNotNull(data.getString("target"))))
            }.getOrNull()
            if (restored != null) { mutableState.value = restored; reparse(restored.kind!!, restored.encoding, restored.delimiter) }
            else { savedState.remove<Bundle>(CHECKPOINT); mutableState.update { it.copy(issue = DocumentIssue.ACCESS) } }
        }
        // Old snapshots are bounded by time. The active checkpoint's source is never removed.
        val active = mutableState.value.source?.file
        viewModelScope.launch(Dispatchers.IO) {
            val cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000
            File(application.noBackupFilesDir, "text-documents").listFiles()?.filter { it != active && it.lastModified() < cutoff }?.forEach { it.delete() }
        }
    }
    fun load(uri: Uri) {
        if (state.value.busy) return
        val old = state.value
        runOperation {
            var imported: ImportedDocument? = null
            var committed = false
            try {
                withContext(Dispatchers.IO) { imported = engine.import(uri, old.encoding) }
                val value = checkNotNull(imported)
                val chunks = withContext(Dispatchers.Default) { if (value.content.kind == DocumentKind.TEXT) TextDocumentCodec.chunks(value.content.text) else emptyList() }
                mutableState.value = TextDocumentState(source = value.source, content = value.content,
                    encoding = old.encoding, kind = value.content.kind, delimiter = value.content.delimiter,
                    previewChunks = chunks, busy = true)
                checkpoint(); committed = true
                withContext(NonCancellable + Dispatchers.IO) { deleteFiles(old) }
            } finally { if (!committed) withContext(NonCancellable + Dispatchers.IO) { imported?.source?.file?.delete() } }
        }
    }
    fun setEncoding(value: DocumentEncoding) {
        if (state.value.busy) return
        if (state.value.source == null) mutableState.update { it.copy(encoding = value, issue = null) }
        else reparse(state.value.kind ?: DocumentKind.TEXT, value, state.value.delimiter)
    }
    fun setKind(value: DocumentKind) { if (!state.value.busy && state.value.source != null) reparse(value, state.value.encoding, null) }
    fun setDelimiter(value: Char?) { if (!state.value.busy && state.value.kind == DocumentKind.CSV) reparse(DocumentKind.CSV, state.value.encoding, value) }
    private fun reparse(kind: DocumentKind, encoding: DocumentEncoding, delimiter: Char?) {
        val old = state.value
        val source = old.source ?: return
        runOperation {
            val content = withContext(Dispatchers.IO) { engine.read(source, kind, encoding, delimiter) }
            val chunks = withContext(Dispatchers.Default) { if (kind == DocumentKind.TEXT) TextDocumentCodec.chunks(content.text) else emptyList() }
            mutableState.update { it.copy(content = content, kind = kind, encoding = encoding, delimiter = content.delimiter,
                target = old.target.takeIf { target -> target in DocumentPolicy.targets(kind) } ?: DocumentTarget.PDF,
                previewChunks = chunks, result = null) }
            checkpoint()
            withContext(NonCancellable + Dispatchers.IO) { old.result?.file?.delete() }
        }
    }
    fun setTarget(target: DocumentTarget) {
        val old = state.value
        if (old.busy || target !in DocumentPolicy.targets(old.kind ?: DocumentKind.TEXT) || target == old.target) return
        mutableState.update { it.copy(target = target, result = null, saved = false, issue = null) }; checkpoint()
        viewModelScope.launch(Dispatchers.IO) { old.result?.file?.delete() }
    }
    fun create() {
        val old = state.value
        val content = old.content ?: return
        if (old.busy) return
        runOperation {
            var result: DocumentResult? = null
            var committed = false
            try {
                withContext(Dispatchers.IO) { result = engine.create(content, old.target) }
                mutableState.update { it.copy(result = result) }; committed = true
                withContext(NonCancellable + Dispatchers.IO) { old.result?.file?.delete() }
            } finally { if (!committed) withContext(NonCancellable + Dispatchers.IO) { result?.file?.delete() } }
        }
    }
    fun export(destination: Uri, expectedResult: String?, pickerOriginal: String? = null) {
        val old = state.value
        if (destination == old.source?.originalUri || destination.toString() == pickerOriginal) { mutableState.update { it.copy(issue = DocumentIssue.EXPORT) }; return }
        if (old.busy || old.result == null || old.source == null || old.result.file.name != expectedResult) {
            viewModelScope.launch(Dispatchers.IO) { runCatching { DocumentsContract.deleteDocument(getApplication<Application>().contentResolver, destination) } }
            mutableState.update { it.copy(issue = DocumentIssue.EXPORT) }; return
        }
        runOperation {
            try { withContext(Dispatchers.IO) { engine.export(old.result, old.source.originalUri, destination) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { throw DocumentException(DocumentIssue.EXPORT) }
            mutableState.update { it.copy(saved = true) }
        }
    }
    private fun checkpoint() {
        val value = state.value
        val source = value.source ?: return
        savedState[CHECKPOINT] = Bundle().apply {
            putString("file", source.file.name); putString("uri", source.originalUri.toString()); putString("name", source.name)
            putLong("bytes", source.bytes); putString("kind", value.kind?.name); putString("encoding", value.encoding.name)
            putString("delimiter", value.delimiter?.toString()); putString("target", value.target.name)
        }
    }
    fun cancel() { operation?.cancel() }
    fun reset() {
        generation++
        val previous = operation; val old = state.value
        previous?.cancel(); savedState.remove<Bundle>(CHECKPOINT); mutableState.value = TextDocumentState()
        viewModelScope.launch { previous?.join(); withContext(Dispatchers.IO) { deleteFiles(old) } }
    }
    private fun deleteFiles(value: TextDocumentState) { value.source?.file?.delete(); value.result?.file?.delete() }
    private fun runOperation(block: suspend () -> Unit) {
        val previous = operation; val token = ++generation
        mutableState.update { it.copy(busy = true, issue = null, saved = false) }
        operation = viewModelScope.launch {
            try { previous?.cancelAndJoin(); block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: DocumentException) { if (token == generation) mutableState.update { it.copy(issue = error.issue) } }
            catch (_: OutOfMemoryError) { if (token == generation) mutableState.update { it.copy(issue = DocumentIssue.MEMORY) } }
            catch (_: Exception) { if (token == generation) mutableState.update { it.copy(issue = DocumentIssue.ACCESS) } }
            finally { if (token == generation) mutableState.update { it.copy(busy = false) } }
        }
    }
    companion object { private const val CHECKPOINT = "text-document-session" }
}

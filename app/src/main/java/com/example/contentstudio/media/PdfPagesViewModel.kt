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
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import com.example.contentstudio.storage.*

data class PdfPagesState(val sources: List<PdfSource> = emptyList(), val pages: List<PdfPageRef> = emptyList(),
    val output: PdfSource? = null, val preview: Bitmap? = null, val busy: Boolean = false,
    val issue: PdfPageIssue? = null, val saved: Boolean = false,
    val pagePreview: Bitmap? = null, val previewId: String? = null, val canUndo: Boolean = false,
    val previewFailureId: String? = null)

class PdfPagesViewModel(application: Application, private val savedState: SavedStateHandle) : AndroidViewModel(application) {
    private val reader = PdfReader(application)
    private val client = PdfPageClient(application)
    private val mutableState = MutableStateFlow(PdfPagesState())
    val state = mutableState.asStateFlow()
    private val folder = File(application.noBackupFilesDir, "pdf-pages").apply { check(isDirectory || mkdirs()) }
    private var operation: Job? = null
    private var generation = 0
    private val history = ArrayDeque<List<PdfPageRef>>()

    init {
        val data = savedState.get<Bundle>(CHECKPOINT)
        if (data != null) runCatching {
            val files = checkNotNull(data.getStringArrayList("files"))
            val names = checkNotNull(data.getStringArrayList("names"))
            val uris = checkNotNull(data.getStringArrayList("uris"))
            val counts = checkNotNull(data.getIntArray("counts"))
            require(files.size in 1..PdfPagePlan.MAX_SOURCES && files.size == names.size && files.size == uris.size && files.size == counts.size)
            val sources = files.mapIndexed { i, name ->
                require(name == File(name).name && name.startsWith("source-") && name.endsWith(".pdf"))
                val file = File(folder, name)
                require(file.isFile && file.length() in 1..PdfPagePlan.MAX_BYTES)
                PdfSource(file, Uri.parse(uris[i]), names[i], file.length(), counts[i])
            }
            require(sources.sumOf { it.bytes } <= PdfPagePlan.MAX_BYTES)
            val plan = checkNotNull(data.getIntArray("plan"))
            require(plan.size % 3 == 0)
            val pages = plan.asList().chunked(3).map { PdfPageRef(UUID.randomUUID().toString(), it[0], it[1], it[2]) }
            PdfPagePlan.validate(pages, sources.map { it.pages })
            mutableState.value = PdfPagesState(sources, pages)
        }.onFailure { savedState.remove<Bundle>(CHECKPOINT); mutableState.value = PdfPagesState(issue = PdfPageIssue.ACCESS) }
        // Do not delete files of other saved sessions: references may survive process death.
    }

    fun add(uris: List<Uri>) {
        if (state.value.busy || uris.isEmpty()) return
        val snapshot = state.value
        if (snapshot.sources.size + uris.size > PdfPagePlan.MAX_SOURCES) { fail(PdfPageIssue.LIMIT); return }
        runOperation {
            val added = mutableListOf<PdfSource>()
            var committed = false
            try {
                withContext(Dispatchers.IO) {
                    for (uri in uris) {
                        val imported = reader.importPdf(uri)
                        // This bitmap was never delivered to Compose.
                        imported.preview.recycle()
                        var owned: File? = null
                        try {
                            val target = File.createTempFile("source-", ".pdf", folder)
                            owned = target
                            imported.source.file.inputStream().use { input -> target.outputStream().use { out ->
                            val guarded = deviceStorage(folder).guard(out)
                            val buffer = ByteArray(64 * 1024)
                            while (true) { currentCoroutineContext().ensureActive(); val count = input.read(buffer); if (count < 0) break; guarded.write(buffer, 0, count) }
                            guarded.flush(); out.fd.sync()
                        } }
                            currentCoroutineContext().ensureActive()
                            added.add(imported.source.copy(file = target))
                        } catch (error: Throwable) { owned?.delete(); throw error }
                        finally { imported.source.file.delete() }
                        if ((snapshot.sources + added).sumOf { it.bytes } > PdfPagePlan.MAX_BYTES ||
                            snapshot.pages.size + added.sumOf { it.pages } > PdfPagePlan.MAX_PAGES) throw PdfPageException(PdfPageIssue.LIMIT)
                    }
                }
                val pages = snapshot.pages + added.flatMapIndexed { i, source ->
                    (0 until source.pages).map { PdfPageRef(UUID.randomUUID().toString(), snapshot.sources.size + i, it) }
                }
                mutableState.value = snapshot.copy(sources = snapshot.sources + added, pages = pages, output = null, preview = null, busy = true)
                checkpoint(); committed = true
                withContext(Dispatchers.IO) { snapshot.output?.file?.delete() }
            } finally { if (!committed) withContext(NonCancellable + Dispatchers.IO) { added.forEach { it.file.delete() } } }
        }
    }

    fun move(id: String, offset: Int) = change { pages ->
        val index = pages.indexOfFirst { it.id == id }; val target = index + offset
        if (index >= 0 && target in pages.indices) pages.toMutableList().apply { add(target, removeAt(index)) } else pages
    }
    fun rotate(id: String) = change { list -> list.map { if (it.id == id) it.copy(rotation = (it.rotation + 90) % 360) else it } }
    fun remove(id: String) = change { if (it.size > 1) it.filterNot { page -> page.id == id } else it }
    fun select(range: String): Boolean {
        val selection = PdfPagePlan.selection(range, state.value.pages.size) ?: return false
        change { pages -> selection.map { pages[it] } }; return true
    }
    private fun change(transform: (List<PdfPageRef>) -> List<PdfPageRef>) {
        if (state.value.busy) return
        val before = state.value.pages
        val after = transform(before)
        if (before == after) return
        history.addLast(before); if (history.size > 20) history.removeFirst()
        val old = state.value.output
        mutableState.update { it.copy(pages = after, output = null, preview = null, pagePreview = null, previewId = null, canUndo = true, saved = false, issue = null) }
        checkpoint()
        viewModelScope.launch(Dispatchers.IO) { old?.file?.delete() }
    }
    fun undo() {
        if (state.value.busy || history.isEmpty()) return
        val old = state.value.output
        val pages = history.removeLast()
        mutableState.update { it.copy(pages = pages, output = null, preview = null, pagePreview = null, previewId = null, canUndo = history.isNotEmpty(), saved = false, issue = null) }
        checkpoint()
        viewModelScope.launch(Dispatchers.IO) { old?.file?.delete() }
    }
    fun previewPage(id: String) {
        val snapshot = state.value
        val ref = snapshot.pages.find { it.id == id } ?: return
        if (snapshot.busy) return
        if (snapshot.previewId == id) { mutableState.update { it.copy(pagePreview = null, previewId = null) }; return }
        runOperation(ref.id) {
            var bitmap: Bitmap? = null
            var committed = false
            try {
                mutableState.update { it.copy(pagePreview = null, previewId = null) }
                withContext(Dispatchers.IO) {
                    val loaded = reader.preview(snapshot.sources[ref.source], ref.page)
                    if (ref.rotation == 0) bitmap = loaded
                    else try { bitmap = Bitmap.createBitmap(loaded, 0, 0, loaded.width, loaded.height,
                        android.graphics.Matrix().apply { postRotate(ref.rotation.toFloat()) }, true) }
                    finally { loaded.recycle() }
                    currentCoroutineContext().ensureActive()
                }
                mutableState.update { it.copy(pagePreview = bitmap, previewId = id) }; committed = true
            } finally { if (!committed) bitmap?.recycle() }
        }
    }
    fun create() {
        val snapshot = state.value
        if (snapshot.busy || snapshot.pages.isEmpty()) return
        runOperation {
            val file = withContext(Dispatchers.IO) { File.createTempFile("output-", ".pdf", folder) }
            var bitmap: Bitmap? = null
            var committed = false
            try {
                withContext(Dispatchers.IO) {
                    client.create(snapshot.sources, snapshot.pages, file)
                    if (file.length() !in 1..PdfPagePlan.MAX_BYTES) throw PdfPageException(PdfPageIssue.LIMIT)
                    bitmap = reader.preview(PdfSource(file, Uri.EMPTY, "pages.pdf", file.length(), snapshot.pages.size), 0)
                }
                mutableState.update { it.copy(output = PdfSource(file, Uri.EMPTY, "pages.pdf", file.length(), snapshot.pages.size), preview = bitmap) }
                committed = true
                withContext(Dispatchers.IO) { snapshot.output?.file?.delete() }
            } finally { if (!committed) withContext(NonCancellable + Dispatchers.IO) { file.delete(); bitmap?.recycle() } }
        }
    }
    fun prepareExport() {
        savedState["pdf-pages-export-sources"] = ArrayList(state.value.sources.map { it.originalUri.toString() })
    }
    fun stageForReader(onReady: (Uri) -> Unit) {
        val snapshot = state.value
        val output = snapshot.output ?: return
        if (snapshot.busy) return
        runOperation {
            val staged = try { withContext(Dispatchers.IO) {
                MediaOutputs(getApplication()).stage(output.file, "application/pdf", "pages.pdf")
            } } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { throw PdfPageException(PdfPageIssue.EXPORT) }
            onReady(staged.uri)
        }
    }
    fun cancelExport() { savedState.remove<ArrayList<String>>("pdf-pages-export-sources") }
    fun export(uri: Uri) {
        val snapshot = state.value
        val originals = savedState.remove<ArrayList<String>>("pdf-pages-export-sources").orEmpty()
        if (uri.toString() in originals || snapshot.sources.any { it.originalUri == uri }) { fail(PdfPageIssue.EXPORT); return }
        val output = snapshot.output
        if (snapshot.busy || output == null) {
            viewModelScope.launch(Dispatchers.IO) { runCatching { DocumentsContract.deleteDocument(getApplication<Application>().contentResolver, uri) } }
            fail(PdfPageIssue.EXPORT); return
        }
        runOperation {
            try { withContext(Dispatchers.IO) { reader.export(output.file, Uri.EMPTY, uri) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { throw PdfPageException(PdfPageIssue.EXPORT) }
            mutableState.update { it.copy(saved = true) }
        }
    }
    private fun checkpoint() {
        val snapshot = state.value
        if (snapshot.sources.isEmpty()) { savedState.remove<Bundle>(CHECKPOINT); return }
        savedState[CHECKPOINT] = Bundle().apply {
            putStringArrayList("files", ArrayList(snapshot.sources.map { it.file.name }))
            putStringArrayList("names", ArrayList(snapshot.sources.map { it.name.take(256) }))
            putStringArrayList("uris", ArrayList(snapshot.sources.map { it.originalUri.toString() }))
            putIntArray("counts", snapshot.sources.map { it.pages }.toIntArray())
            putIntArray("plan", snapshot.pages.flatMap { listOf(it.source, it.page, it.rotation) }.toIntArray())
        }
    }
    private fun fail(issue: PdfPageIssue) { mutableState.update { it.copy(issue = issue) } }
    fun cancel() { operation?.cancel() }
    fun reset() {
        generation++; val oldJob = operation; oldJob?.cancel()
        val old = state.value; mutableState.value = PdfPagesState(); savedState.remove<Bundle>(CHECKPOINT)
        history.clear()
        viewModelScope.launch { oldJob?.join(); withContext(Dispatchers.IO) { old.sources.forEach { it.file.delete() }; old.output?.file?.delete() } }
    }
    private fun runOperation(previewRequest: String? = null, block: suspend () -> Unit) {
        val token = ++generation; val old = operation
        mutableState.update { it.copy(busy = true, issue = null, saved = false, previewFailureId = null) }
        operation = viewModelScope.launch {
            try { old?.cancelAndJoin(); block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: PdfPageException) { if (token == generation) fail(error.issue) }
            catch (error: PdfReadException) { if (token == generation) fail(when (error.issue) {
                PdfReadIssue.PASSWORD -> PdfPageIssue.PROTECTED; PdfReadIssue.LIMIT -> PdfPageIssue.LIMIT
                PdfReadIssue.MEMORY -> PdfPageIssue.MEMORY; PdfReadIssue.ACCESS -> PdfPageIssue.ACCESS
                PdfReadIssue.STORAGE -> PdfPageIssue.STORAGE
                PdfReadIssue.PROCESS, PdfReadIssue.TIMEOUT -> PdfPageIssue.PROCESS; else -> PdfPageIssue.INVALID
            }) }
            catch (_: StorageCapacityException) { if (token == generation) fail(PdfPageIssue.STORAGE) }
            catch (_: OutOfMemoryError) { if (token == generation) fail(PdfPageIssue.MEMORY) }
            catch (_: Exception) { if (token == generation) fail(PdfPageIssue.INVALID) }
            finally { if (token == generation) mutableState.update { it.copy(busy = false,
                previewFailureId = if (it.issue != null) previewRequest else null) } }
        }
    }
    companion object { private const val CHECKPOINT = "pdf-pages-session" }
}

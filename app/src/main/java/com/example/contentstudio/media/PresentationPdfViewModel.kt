package com.example.contentstudio.media

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.contentstudio.data.StudioProject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class PdfMessage { GENERATE_ERROR, EMPTY, OVERFLOW, EXPORT_ERROR, EXPORTED }
data class PresentationPdfState(val project: StudioProject? = null, val result: PresentationPdfResult? = null,
    val preview: Bitmap? = null, val pageIndex: Int = 0, val busy: Boolean = false, val progress: Int? = null,
    val message: PdfMessage? = null, val overflowSlide: Int = 0)

class PresentationPdfViewModel(application: Application) : AndroidViewModel(application) {
    private val engine = PresentationPdf(application)
    private val mutableState = MutableStateFlow(PresentationPdfState())
    val state = mutableState.asStateFlow()
    private var operation: Job? = null
    @Volatile private var generation = 0
    init { viewModelScope.launch(Dispatchers.IO) { engine.cleanOldCache() } }

    fun prepare(project: StudioProject) {
        if (state.value.project == project && (state.value.busy || state.value.result != null)) return
        val previous = operation
        val oldFile = state.value.result?.file
        val token = ++generation
        previous?.cancel()
        mutableState.value = PresentationPdfState(project = project, busy = true)
        operation = viewModelScope.launch {
            var output: PresentationPdfResult? = null
            var bitmap: Bitmap? = null
            var committed = false
            try {
                previous?.join()
                withContext(Dispatchers.IO) {
                    oldFile?.delete()
                    output = engine.create(project) { progress ->
                        if (generation == token) mutableState.update { it.copy(progress = progress) }
                    }
                    bitmap = engine.render(output, 0)
                }
                mutableState.update { it.copy(result = output, preview = bitmap) }
                committed = true
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: PdfEmptyPresentation) { if (token == generation) mutableState.update { it.copy(message = PdfMessage.EMPTY) } }
            catch (overflow: PdfTextOverflow) { if (token == generation) mutableState.update {
                it.copy(message = PdfMessage.OVERFLOW, overflowSlide = overflow.slideNumber) } }
            catch (_: Exception) { if (token == generation) mutableState.update { it.copy(message = PdfMessage.GENERATE_ERROR) } }
            catch (_: OutOfMemoryError) { if (token == generation) mutableState.update { it.copy(message = PdfMessage.GENERATE_ERROR) } }
            finally {
                if (!committed) withContext(NonCancellable + Dispatchers.IO) { output?.file?.delete(); bitmap?.recycle() }
                if (token == generation) mutableState.update { it.copy(busy = false, progress = null) }
            }
        }
    }

    fun showPage(index: Int) {
        val result = state.value.result ?: return
        if (state.value.busy || index == state.value.pageIndex || index !in 0 until result.pages) return
        runOperation(PdfMessage.GENERATE_ERROR) {
            var bitmap: Bitmap? = null
            var committed = false
            try {
                withContext(Dispatchers.IO) { bitmap = engine.render(result, index) }
                mutableState.update { it.copy(pageIndex = index, preview = bitmap) }
                committed = true
            } finally { if (!committed) bitmap?.recycle() }
        }
    }

    fun export(uri: Uri) {
        val result = state.value.result
        if (result == null) {
            viewModelScope.launch(Dispatchers.IO) { runCatching {
                DocumentsContract.deleteDocument(getApplication<Application>().contentResolver, uri) } }
            mutableState.update { it.copy(message = PdfMessage.EXPORT_ERROR) }
            return
        }
        if (state.value.busy) return
        runOperation(PdfMessage.EXPORT_ERROR) {
            withContext(Dispatchers.IO) { engine.export(result, uri) }
            mutableState.update { it.copy(message = PdfMessage.EXPORTED) }
        }
    }

    fun cancel() { operation?.cancel() }
    fun reset() {
        generation++
        val previous = operation
        val file = state.value.result?.file
        previous?.cancel()
        mutableState.value = PresentationPdfState()
        viewModelScope.launch { previous?.join(); withContext(Dispatchers.IO) { file?.delete() } }
    }
    private fun runOperation(errorMessage: PdfMessage, block: suspend () -> Unit) {
        val token = ++generation
        val previous = operation
        mutableState.update { it.copy(busy = true, message = null) }
        operation = viewModelScope.launch {
            try { previous?.cancelAndJoin(); block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (token == generation) mutableState.update { it.copy(message = errorMessage) } }
            catch (_: OutOfMemoryError) { if (token == generation) mutableState.update { it.copy(message = errorMessage) } }
            finally { if (token == generation) mutableState.update { it.copy(busy = false, progress = null) } }
        }
    }
}

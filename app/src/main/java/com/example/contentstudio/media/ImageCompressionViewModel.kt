package com.example.contentstudio.media

import android.app.Application
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class CompressionState(val source: ImageSource? = null, val result: CompressedImage? = null,
    val quality: Int = 80, val maxEdge: Int = 1920, val busy: Boolean = false,
    val message: CompressionMessage? = null, val format: ImageOutputFormat = ImageOutputFormat.JPEG,
    val issue: ImportIssue? = null)
enum class CompressionMessage { IMAGE_ERROR, EXPORT_ERROR, EXPORTED }

class ImageCompressionViewModel(application: Application) : AndroidViewModel(application) {
    private val engine = ImageCompressor(application)
    private val mutableState = MutableStateFlow(CompressionState())
    val state = mutableState.asStateFlow()
    private var operation: Job? = null
    private var generation = 0
    private var confirmedCopy: File? = null

    init { viewModelScope.launch(Dispatchers.IO) { engine.cleanOldCache() } }

    fun load(uri: Uri) {
        if (state.value.busy) return
        runOperation(CompressionMessage.IMAGE_ERROR) {
            var imported: ImageSource? = null
            var committed = false
            try {
                withContext(Dispatchers.IO) { imported = engine.importImage(uri) }
                removeFiles(state.value)
                mutableState.value = CompressionState(source = imported, busy = true)
                committed = true
            } finally {
                if (!committed) withContext(NonCancellable + Dispatchers.IO) {
                    imported?.file?.delete(); imported?.preview?.recycle()
                }
            }
        }
    }

    fun setOptions(quality: Int = state.value.quality, maxEdge: Int = state.value.maxEdge,
        format: ImageOutputFormat = state.value.format) {
        if (state.value.busy) return
        if (quality == state.value.quality && maxEdge == state.value.maxEdge && format == state.value.format) return
        require(quality in 40..95 && maxEdge in listOf(1280, 1920, 2560))
        state.value.result?.file?.let { removeFile(it) }
        confirmedCopy = null
        mutableState.update { it.copy(quality = quality, maxEdge = maxEdge, format = format, result = null, message = null) }
    }

    /** Fully import before leaving the video dialog; the extracted PNG may then be deleted safely. */
    suspend fun addFrame(file: File, originals: Set<Uri>): Boolean {
        if (state.value.busy) return false
        val root = File(getApplication<Application>().cacheDir, "video-frames").canonicalFile
        require(file.canonicalFile.parentFile == root && file.name.startsWith("frame-") && file.extension == "png")
        mutableState.update { it.copy(busy = true, message = null, issue = null) }
        var imported: ImageSource? = null
        var committed = false
        try {
            operation?.join()
            withContext(Dispatchers.IO) { imported = engine.importImage(Uri.fromFile(file), originals) }
            removeFiles(state.value)
            confirmedCopy = null
            mutableState.value = CompressionState(source = imported, busy = true)
            committed = true
            return true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { mutableState.update { it.copy(message = CompressionMessage.IMAGE_ERROR, issue = importIssue(error)) }; return false }
        catch (_: OutOfMemoryError) { mutableState.update { it.copy(message = CompressionMessage.IMAGE_ERROR, issue = ImportIssue.MEMORY) }; return false }
        finally {
            if (!committed) withContext(NonCancellable + Dispatchers.IO) { imported?.file?.delete(); imported?.preview?.recycle() }
            mutableState.update { it.copy(busy = false) }
        }
    }

    fun preview() {
        val snapshot = state.value
        val source = snapshot.source ?: return
        if (snapshot.busy) return
        confirmedCopy = null
        runOperation(CompressionMessage.IMAGE_ERROR) {
            var result: CompressedImage? = null
            var committed = false
            try {
                withContext(Dispatchers.IO) { result = engine.compress(source, snapshot.quality, snapshot.maxEdge, snapshot.format) }
                state.value.result?.file?.let { removeFile(it) }
                mutableState.update { it.copy(result = result) }
                committed = true
            } finally {
                if (!committed) withContext(NonCancellable + Dispatchers.IO) {
                    result?.file?.delete(); result?.preview?.recycle()
                }
            }
        }
    }

    fun confirmNonSmallerCopy(file: File): Boolean {
        if (state.value.busy || state.value.result?.file != file) return false
        confirmedCopy = file
        return true
    }

    fun export(uri: Uri, expectedResult: File? = state.value.result?.file,
        pickerOriginals: List<String> = emptyList()) {
        val snapshot = state.value
        if (snapshot.busy) return
        val source = snapshot.source
        val result = snapshot.result
        if (source == null || result == null || result.file != expectedResult) {
            // A destination belongs to the result that opened its picker, including after recreation.
            // Never clean up a source URI even when rejecting an obsolete callback.
            val originals = source?.protectedUris.orEmpty() + pickerOriginals.map(Uri::parse)
            if (uri !in originals) viewModelScope.launch(Dispatchers.IO) {
                runCatching { DocumentsContract.deleteDocument(getApplication<Application>().contentResolver, uri) }
            }
            mutableState.update { it.copy(message = CompressionMessage.EXPORT_ERROR) }
            return
        }
        runOperation(CompressionMessage.EXPORT_ERROR) {
            withContext(Dispatchers.IO) { engine.export(source, result, uri,
                allowNonSmallerCopy = confirmedCopy == result.file) }
            mutableState.update { it.copy(message = CompressionMessage.EXPORTED) }
        }
    }

    fun cancel() { operation?.cancel() }
    fun clearMessage() { mutableState.update { it.copy(message = null, issue = null) } }

    fun reset() {
        confirmedCopy = null
        generation++
        operation?.cancel()
        val old = state.value
        // File cleanup follows the cancelled operation, so an encoder never reads a deleted source.
        val previous = operation
        viewModelScope.launch {
            previous?.join()
            removeFiles(old)
        }
        mutableState.value = CompressionState()
        // Keep the job reference until the next operation joins it.
    }

    private fun runOperation(errorMessage: CompressionMessage, block: suspend () -> Unit) {
        val token = ++generation
        val previous = operation
        mutableState.update { it.copy(busy = true, message = null, issue = null) }
        operation = viewModelScope.launch {
            try { previous?.cancelAndJoin(); block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { if (token == generation) mutableState.update { it.copy(message = errorMessage, issue = importIssue(error)) } }
            catch (_: OutOfMemoryError) { if (token == generation) mutableState.update { it.copy(message = errorMessage, issue = ImportIssue.MEMORY) } }
            finally { if (token == generation) mutableState.update { it.copy(busy = false) } }
        }
    }

    private fun removeFile(file: File) { viewModelScope.launch(Dispatchers.IO) { file.delete() } }
    private fun removeFiles(value: CompressionState) {
        value.source?.file?.let { removeFile(it) }
        value.result?.file?.let { removeFile(it) }
    }
}

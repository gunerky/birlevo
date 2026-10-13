package com.example.contentstudio.audio

import com.example.contentstudio.media.*

import android.app.Application
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import com.example.contentstudio.processing.*

data class AudioToolState(val source: AudioSource? = null, val result: AudioResult? = null,
    val target: AudioTarget = AudioTarget.M4A, val bitrate: Int = 128_000, val busy: Boolean = false, val progress: Int? = null,
    val message: AudioMessage? = null, val issue: ImportIssue? = null, val workStatus: ProcessingStatus? = null,
    val outputSupport: Map<AudioTarget, AudioOutputIssue?> = emptyMap(),
    val clips: List<AudioClip> = emptyList(), val outputRate: Int? = null, val outputChannels: Int? = null) {
    val audioClips get() = clips.ifEmpty { listOfNotNull(source?.let { AudioClip(it) }) }
    val rate get() = outputRate ?: audioClips.maxOfOrNull { it.source.info.sampleRate } ?: 48_000
    val channels get() = outputChannels ?: audioClips.maxOfOrNull { it.source.info.channels } ?: 2
    val durationMs get() = audioClips.sumOf { it.range.duration(it.source.info.durationMs) }
    val sourceBytes get() = audioClips.sumOf { it.source.bytes }
    val profile get() = AudioInfo("audio/raw", rate, channels, durationMs, false)
    fun edit() = AudioEdit(audioClips, rate, channels)
}
enum class AudioMessage { LOAD_ERROR, PROCESS_ERROR, EXPORT_ERROR, EXPORTED }

class AudioToolViewModel @JvmOverloads constructor(application: Application,
    storage: MediaSessionStorage = AtomicMediaSession(File(application.noBackupFilesDir, "audio-conversion"))) : AndroidViewModel(application) {
    private val engine = AudioEngine(application)
    private val mutableState = MutableStateFlow(AudioToolState(busy = true))
    val state = mutableState.asStateFlow()
    private var operation: Job? = null
    private var generation = 0
    private var confirmedCopy: File? = null
    private val session = MediaSessionWriter(viewModelScope, storage)
    val sessionStatus = session.status
    private var restoreFailed = false
    private val processing = ProcessingJobs.get(application)
    private val restored = CompletableDeferred<Unit>()
    private var resetJob: Job? = null
    val sessionRestoreFailed get() = restoreFailed

    init { restoreSession(); observeProcessing() }
    private fun restoreSession() {
        runOperation(AudioMessage.LOAD_ERROR) {
            var source: AudioSource? = null
            try {
                val text = session.read()
                if (text != null) {
                    val saved = withContext(Dispatchers.IO) { AudioSessionCodec.decode(text) }
                    val clips = withContext(Dispatchers.IO) { saved.clips.map { AudioClip(engine.restore(it.asset), it.range).also { clip -> clip.range.duration(clip.source.info.durationMs) } } }
                    source = clips.first().source
                    val value = AudioToolState(source = source, clips = clips, target = saved.target, bitrate = saved.bitrate,
                        outputRate = saved.rate, outputChannels = saved.channels, busy = true)
                    val support = withContext(Dispatchers.IO) { engine.outputSupport(value.profile, saved.bitrate) }
                    mutableState.value = value.copy(outputSupport = support)
                }
                session.initialize(checkpoint())
                processing.awaitReady()
                if (processing.recoveryFailed && source != null) mutableState.update { it.copy(workStatus = ProcessingStatus.INTERRUPTED) }
                val request = checkpoint().json
                restoreFailed = false
                withContext(Dispatchers.IO) { runCatching { engine.cleanOldCache(state.value.audioClips.map { it.source.file }.toSet() + setOfNotNull(processing.retainedOutput(ProcessingKind.AUDIO, request))) } }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Throwable) { restoreFailed = true; throw failure }
            finally { restored.complete(Unit) }
        }
    }
    private fun checkpoint(): SessionSnapshot {
        val value = state.value
        if (value.source == null) return SessionSnapshot(null)
        val clips = value.audioClips.map { SavedAudioClip(AudioSessionCodec.asset(it.source), it.range) }
        return SessionSnapshot(AudioSessionCodec.encode(AudioSession(clips.first().asset, value.target, value.bitrate, clips,
            value.outputRate, value.outputChannels)), value.audioClips.map { it.source.file }.toSet())
    }
    fun flushSession() { viewModelScope.launch { session.flush() } }
    suspend fun awaitSession(): Boolean { resetJob?.join(); return session.flush() }
    fun retrySession() { if (restoreFailed && !state.value.busy) restoreSession() else session.retry() }

    fun load(uri: Uri) = importSource(uri, append = false)
    fun addSource(uri: Uri) = importSource(uri, append = true)
    private fun importSource(uri: Uri, append: Boolean) {
        if (state.value.busy || (append && state.value.audioClips.size >= 2)) return
        runOperation(AudioMessage.LOAD_ERROR) {
            var imported: AudioSource? = null
            var committed = false
            try {
                // Assign on IO before the cancellable dispatcher handoff, retaining cleanup ownership.
                withContext(Dispatchers.IO) { imported = engine.importAudio(uri) }
                val loaded = requireNotNull(imported)
                val old = state.value
                val next = if (append) old.copy(clips = old.audioClips + AudioClip(loaded), source = old.source ?: loaded,
                    result = null, busy = true, message = null, workStatus = null)
                    else AudioToolState(source = loaded, clips = listOf(AudioClip(loaded)), busy = true)
                val support = withContext(Dispatchers.IO) { engine.outputSupport(next.profile, next.bitrate) }
                processing.dropFinished(ProcessingKind.AUDIO)
                mutableState.value = next.copy(outputSupport = support)
                session.update(checkpoint(), if (append) emptySet() else old.audioClips.map { it.source.file }.toSet())
                restoreFailed = false; confirmedCopy = null
                old.result?.file?.let(::removeFile)
                committed = true
            } finally {
                if (!committed) withContext(NonCancellable + Dispatchers.IO) {
                    imported?.file?.delete()
                }
            }
        }
    }

    /** Copy an encoded result into audio-owned storage before the producing tool can retire it. */
    suspend fun addOutput(uri: Uri): Boolean {
        restored.await()
        // Main.immediate can resume this caller inside restored.complete(), before the
        // initialization operation's finally block clears busy. Wait for that operation too.
        operation?.join()
        if (state.value.busy) return false
        load(uri)
        operation?.join()
        return state.value.source?.originalUri == uri && state.value.message == null
    }

    fun setOptions(target: AudioTarget = state.value.target, bitrate: Int = state.value.bitrate) {
        val recheck = bitrate != state.value.bitrate || state.value.outputSupport[target] == AudioOutputIssue.CHECK_FAILED
        if (state.value.busy || (target == state.value.target && bitrate == state.value.bitrate && !recheck)) return
        require(bitrate in AudioPolicy.bitrates)
        processing.dropFinished(ProcessingKind.AUDIO)
        state.value.result?.file?.let(::removeFile)
        mutableState.update { it.copy(target = target, bitrate = bitrate, result = null, message = null) }
        confirmedCopy = null; session.update(checkpoint())
        if (!recheck) return
        if (state.value.source == null) return
        runOperation(AudioMessage.PROCESS_ERROR) {
            val support = withContext(Dispatchers.IO) { engine.outputSupport(state.value.profile, bitrate) }
            mutableState.update { it.copy(outputSupport = support) }
        }
    }

    fun setRange(file: File, range: AudioRange) {
        val value = state.value
        val clip = value.audioClips.firstOrNull { it.source.file == file } ?: return
        if (runCatching { range.duration(clip.source.info.durationMs) }.isFailure) return
        updateEdit(value.copy(clips = value.audioClips.map { if (it.source.file == file) it.copy(range = range) else it }))
    }
    fun swapSources() {
        val value = state.value
        if (value.audioClips.size != 2) return
        val clips = value.audioClips.reversed()
        updateEdit(value.copy(source = clips.first().source, clips = clips))
    }
    fun removeSource(file: File) {
        val value = state.value
        if (value.audioClips.size != 2) return
        val clips = value.audioClips.filterNot { it.source.file == file }
        if (clips.size != 1) return
        updateEdit(value.copy(source = clips.first().source, clips = clips), setOf(file))
    }
    fun setProfile(rate: Int = state.value.rate, channels: Int = state.value.channels) {
        require(rate in 8_000..192_000 && channels in 1..2)
        updateEdit(state.value.copy(outputRate = rate, outputChannels = channels))
    }
    private fun updateEdit(next: AudioToolState, retired: Set<File> = emptySet()) {
        if (state.value.busy || next == state.value) return
        val recheck = next.rate != state.value.rate || next.channels != state.value.channels
        processing.dropFinished(ProcessingKind.AUDIO)
        state.value.result?.file?.let(::removeFile)
        mutableState.value = next.copy(result = null, message = null, issue = null, workStatus = null)
        confirmedCopy = null; session.update(checkpoint(), retired)
        if (recheck) runOperation(AudioMessage.PROCESS_ERROR) {
            val support = withContext(Dispatchers.IO) { engine.outputSupport(next.profile, next.bitrate) }
            mutableState.update { it.copy(outputSupport = support) }
        }
    }

    fun preview() {
        val snapshot = state.value
        val edit = runCatching { snapshot.edit() }.getOrNull() ?: return
        if (snapshot.busy) return
        if (!snapshot.outputSupport.containsKey(snapshot.target) || snapshot.outputSupport[snapshot.target] != null) return
        confirmedCopy = null
        mutableState.update { it.copy(busy = true, message = null, issue = null, progress = null) }
        try { processing.start(ProcessingKind.AUDIO, checkNotNull(checkpoint().json)) { progress ->
            check(session.flush()) { "Source session could not be saved" }
            var output: ProcessingOutput? = null
            var delivered = false
            try {
                withContext(Dispatchers.IO) {
                    val result = engine.convert(edit, snapshot.target, snapshot.bitrate, progress)
                    output = ProcessingOutput(result.file.name, result.bytes, elapsedMs = result.elapsedMs)
                }
                checkNotNull(output).also { delivered = true }
            } finally { if (!delivered) withContext(NonCancellable + Dispatchers.IO) {
                output?.let { it.resolve(File(getApplication<Application>().noBackupFilesDir, "audio-conversion")).delete() }
            } }
        } } catch (_: Exception) { mutableState.update { it.copy(busy = false, message = AudioMessage.PROCESS_ERROR) } }
    }

    private fun observeProcessing() { viewModelScope.launch {
        restored.await(); processing.awaitReady()
        processing.records.map { it[ProcessingKind.AUDIO] }.distinctUntilChanged().collectLatest { record ->
            record ?: return@collectLatest
            if (record.request != ProcessingCodec.fingerprint(checkpoint().json)) return@collectLatest
            if (record.status.active) {
                mutableState.update { it.copy(busy = true, progress = record.progress, workStatus = record.status) }
            } else if (record.status == ProcessingStatus.SUCCEEDED && state.value.result?.file?.name != record.output?.name) {
                var result: AudioResult? = null
                mutableState.update { it.copy(busy = true, workStatus = record.status) }
                try {
                    withContext(Dispatchers.IO) { val output = checkNotNull(record.output)
                        result = engine.restoreOutput(output.resolve(File(getApplication<Application>().noBackupFilesDir, "audio-conversion")), output.bytes, output.elapsedMs)
                        engine.validateEditOutput(state.value.edit(), checkNotNull(result), state.value.target) }
                    if (record.request == ProcessingCodec.fingerprint(checkpoint().json)) {
                        val old = state.value.result?.file
                        mutableState.update { it.copy(result = result, busy = false, progress = null, message = null) }
                        confirmedCopy = null
                        if (old != result?.file) old?.let(::removeFile)
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { mutableState.update { it.copy(busy = false, message = AudioMessage.PROCESS_ERROR) } }
            } else if (record.status != ProcessingStatus.SUCCEEDED) {
                mutableState.update { it.copy(busy = false, progress = null, workStatus = record.status,
                    message = if (record.status == ProcessingStatus.FAILED) AudioMessage.PROCESS_ERROR else null) }
            }
        }
    } }

    fun confirmNonSmallerCopy(file: File): Boolean {
        if (state.value.busy || state.value.result?.file != file) return false
        confirmedCopy = file
        return true
    }

    fun export(uri: Uri, expectedResult: String? = state.value.result?.file?.name, pickerOriginal: String? = null, pickerOriginals: List<String> = emptyList()) {
        val snapshot = state.value
        val source = snapshot.source
        val result = snapshot.result
        if (snapshot.audioClips.any { uri == it.source.originalUri } || uri.toString() == pickerOriginal || uri.toString() in pickerOriginals) {
            mutableState.update { it.copy(message = AudioMessage.EXPORT_ERROR) }; return
        }
        if (snapshot.busy || source == null || result == null || result.file.name != expectedResult) {
            // A newly created document can return after process death; avoid leaving an empty output.
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { DocumentsContract.deleteDocument(getApplication<Application>().contentResolver, uri) }
            }
            mutableState.update { it.copy(message = AudioMessage.EXPORT_ERROR) }
            return
        }
        runOperation(AudioMessage.EXPORT_ERROR) {
            withContext(Dispatchers.IO) { engine.export(snapshot.edit(), result, uri,
                allowNonSmallerCopy = confirmedCopy == result.file) }
            mutableState.update { it.copy(message = AudioMessage.EXPORTED) }
        }
    }

    fun cancel() { processing.cancel(ProcessingKind.AUDIO); operation?.cancel() }
    fun leave() { if (state.value.workStatus?.active != true) operation?.cancel(); flushSession() }
    fun reset() {
        confirmedCopy = null
        generation++
        val previous = operation
        previous?.cancel()
        processing.cancel(ProcessingKind.AUDIO)
        val old = state.value
        mutableState.value = AudioToolState(busy = true)
        restoreFailed = false
        resetJob = viewModelScope.launch {
            previous?.join(); processing.forget(ProcessingKind.AUDIO)
            session.update(SessionSnapshot(null), old.audioClips.map { it.source.file }.toSet())
            old.result?.file?.let(::removeFile); session.flush()
            mutableState.update { it.copy(busy = false) }
        }
    }

    suspend fun clearSavedWork(): Boolean {
        restored.await()
        processing.awaitReady()
        val ready = state.first { !it.busy || it.workStatus?.active == true }
        if (ready.busy || ready.workStatus?.active == true || processing.records.value[ProcessingKind.AUDIO]?.status?.active == true) return false
        reset()
        resetJob?.join()
        return session.flush()
    }

    private fun runOperation(errorMessage: AudioMessage, block: suspend () -> Unit) {
        val token = ++generation
        val previous = operation
        mutableState.update { it.copy(busy = true, progress = null, message = null, issue = null) }
        operation = viewModelScope.launch {
            try { previous?.cancelAndJoin(); block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                android.util.Log.e("AudioTool", "Operation failed: $errorMessage", error)
                if (token == generation) mutableState.update { it.copy(message = errorMessage, issue = importIssue(error)) }
            }
            catch (_: OutOfMemoryError) { if (token == generation) mutableState.update { it.copy(message = errorMessage, issue = ImportIssue.MEMORY) } }
            finally { if (token == generation) mutableState.update { it.copy(busy = false, progress = null) } }
        }
    }

    private fun removeFile(file: File) { viewModelScope.launch(Dispatchers.IO) { file.delete() } }
}

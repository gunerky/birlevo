package com.example.contentstudio.processing

import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.contentstudio.media.AtomicMediaSession
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Application-owned jobs outlive Activities; one heavy encoder/PDF task runs at a time. */
class ProcessingJobs internal constructor(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val disk = AtomicMediaSession(File(context.noBackupFilesDir, "processing"))
    private val writer = Mutex()
    private val heavy = Mutex()
    private val initialized = CompletableDeferred<Unit>()
    private var serviceReady = CompletableDeferred<Unit>()
    private var promote: ((ProcessingKind) -> Unit)? = null
    private var serviceOwner: Any? = null
    private val tasks = mutableMapOf<ProcessingKind, Job>()
    private val interrupted = mutableSetOf<String>()
    private val mutable = MutableStateFlow<Map<ProcessingKind, ProcessingRecord>>(emptyMap())
    val records = mutable.asStateFlow()
    var recoveryFailed = false; private set
    init { scope.launch {
        try {
            val text = withContext(Dispatchers.IO) { disk.read() }
            if (text != null) mutable.value = withContext(Dispatchers.IO) { ProcessingCodec.decode(text) }
            if (mutable.value.values.any { it.status == ProcessingStatus.INTERRUPTED }) persist()
        } catch (_: Exception) { recoveryFailed = true }
        finally { initialized.complete(Unit) }
    } }
    suspend fun awaitReady() { initialized.await() }
    /** Null means cleanup cannot prove safety; retain completed outputs even for a different request. */
    fun cleanupRetention(kind: ProcessingKind): Set<File>? {
        if (!initialized.isCompleted || recoveryFailed) return null
        val record = mutable.value[kind] ?: return emptySet()
        if (record.status.active) return null
        return try {
            setOfNotNull(record.output?.resolve(File(context.noBackupFilesDir, kind.folder)))
        } catch (_: Exception) { null }
    }
    private suspend fun persist() = writer.withLock {
        val text = ProcessingCodec.encode(mutable.value.values)
        withContext(NonCancellable + Dispatchers.IO) { disk.write(text) }
    }
    private fun replace(record: ProcessingRecord) { mutable.update { it + (record.kind to record) } }
    fun serviceAttached(promotion: (ProcessingKind) -> Unit): Any {
        val owner = Any()
        serviceOwner = owner; promote = promotion; serviceReady.complete(Unit)
        return owner
    }
    /** Revoke readiness before requesting idle shutdown; a new job must await the next service. */
    fun serviceStopping(owner: Any?): Boolean {
        if (owner == null || serviceOwner !== owner || mutable.value.values.any { it.status.active }) return false
        serviceOwner = null; promote = null; serviceReady = CompletableDeferred()
        return true
    }
    fun serviceDetached(owner: Any?) {
        if (owner == null || serviceOwner !== owner) return
        serviceOwner = null; promote = null; serviceReady = CompletableDeferred(); cancelAll()
    }

    /** Called on the main thread in response to an explicit, visible user action. */
    fun start(kind: ProcessingKind, request: String, work: suspend ((Int?) -> Unit) -> ProcessingOutput) {
        check(initialized.isCompleted && tasks[kind]?.isCompleted != false)
        val record = ProcessingRecord(UUID.randomUUID().toString(), kind, checkNotNull(ProcessingCodec.fingerprint(request)), ProcessingStatus.QUEUED)
        replace(record)
        try {
            val intent = Intent(context, ProcessingService::class.java)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
        } catch (failure: Exception) {
            replace(record.copy(status = ProcessingStatus.FAILED))
            scope.launch { runCatching { persist() } }
            throw IOException("Could not start processing service", failure)
        }
        tasks[kind] = scope.launch(start = CoroutineStart.LAZY) {
            val identity = currentCoroutineContext()[Job]
            var produced: ProcessingOutput? = null
            var committed = false
            var terminal = ProcessingStatus.FAILED
            try {
                persist()
                withTimeout(5000) { serviceReady.await() }
                heavy.withLock {
                    ensureActive()
                    replace(record.copy(status = ProcessingStatus.RUNNING))
                    // The type must be promoted on Main before any local document/media work starts.
                    checkNotNull(promote).invoke(kind)
                    persist()
                    produced = work { percent ->
                        mutable.update { state ->
                            val current = state[kind]
                            if (current?.id == record.id && current.status.active) state + (kind to current.copy(progress = percent?.coerceIn(0, 100)))
                            else state
                        }
                    }
                    ensureActive()
                    val completed = record.copy(status = ProcessingStatus.SUCCEEDED, output = checkNotNull(produced))
                    // Publish success only when the durable result record has been written.
                    writer.withLock {
                        val snapshot = mutable.value + (kind to completed)
                        withContext(NonCancellable) {
                            withContext(Dispatchers.IO) { disk.write(ProcessingCodec.encode(snapshot.values)) }
                            replace(completed); committed = true
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                terminal = if (record.id in interrupted) ProcessingStatus.INTERRUPTED else ProcessingStatus.CANCELLED
            } catch (_: Throwable) { terminal = ProcessingStatus.FAILED }
            finally {
                if (!committed) {
                    withContext(NonCancellable) {
                        produced?.let { output -> withContext(Dispatchers.IO) {
                            runCatching { output.resolve(File(context.noBackupFilesDir, kind.folder)).delete() }
                        } }
                        val stopped = record.copy(status = terminal)
                        runCatching { writer.withLock {
                            val snapshot = mutable.value + (kind to stopped)
                            withContext(Dispatchers.IO) { disk.write(ProcessingCodec.encode(snapshot.values)) }
                        } }
                        replace(stopped)
                    }
                }
                interrupted.remove(record.id)
                if (tasks[kind] === identity) tasks.remove(kind)
            }
        }.also { it.start() }
    }
    fun cancel(kind: ProcessingKind) { tasks[kind]?.cancel() }
    fun cancelIds(ids: Set<String>) { mutable.value.values.filter { it.id in ids && it.status.active }.forEach { cancel(it.kind) } }
    fun cancelAll() {
        interrupted += mutable.value.values.filter { it.status.active }.map { it.id }
        tasks.values.toList().forEach { it.cancel() }
    }
    fun dropFinished(kind: ProcessingKind) {
        if (mutable.value[kind]?.status?.active == true) return
        mutable.update { it - kind }
        scope.launch { runCatching { persist() } }
    }
    suspend fun forget(kind: ProcessingKind) {
        val task = tasks[kind]; task?.cancelAndJoin()
        mutable.update { it - kind }
        runCatching { persist() }
    }
    fun retainedOutput(kind: ProcessingKind, request: String?): File? = mutable.value[kind]?.takeIf {
        it.request == ProcessingCodec.fingerprint(request) && it.status == ProcessingStatus.SUCCEEDED
    }?.output?.let { runCatching { it.resolve(File(context.noBackupFilesDir, kind.folder)) }.getOrNull() }
    companion object {
        fun get(context: Context): ProcessingJobs = (context.applicationContext as ProcessingApplication).processingJobs
    }
}

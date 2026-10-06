package com.example.contentstudio.media

import android.content.*
import android.os.*
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class PdfRasterInfo(val pages: Int, val width: Int, val height: Int)

/** One short-lived worker per requested page, bounded time and no renderer in the UI process. */
internal class PdfRenderClient(private val context: Context) {
    suspend fun render(source: File, output: File, page: Int, edge: Int): PdfRasterInfo {
        try {
            return withTimeout(30_000L) { suspendCancellableCoroutine { continuation ->
                val complete = AtomicBoolean(false)
                val lock = Any()
                var bound = false
                lateinit var connection: ServiceConnection
                fun detach() = synchronized(lock) {
                    if (bound) { bound = false; runCatching { context.unbindService(connection) } }
                }
                fun finish(info: PdfRasterInfo? = null, issue: PdfReadIssue? = null) {
                    if (!complete.compareAndSet(false, true)) return
                    detach()
                    if (continuation.isActive) {
                        if (info != null) continuation.resume(info)
                        else continuation.resumeWithException(PdfReadException(issue ?: PdfReadIssue.PROCESS))
                    }
                }
                val receiver = Messenger(object : Handler(Looper.getMainLooper()) {
                    override fun handleMessage(message: Message) {
                        val reply = message.data
                        val error = reply.getString("error")
                        if (error != null) { finish(issue = PdfReadIssue.entries.find { it.name == error } ?: PdfReadIssue.INVALID); return }
                        val info = PdfRasterInfo(reply.getInt("pages"), reply.getInt("width"), reply.getInt("height"))
                        val valid = info.pages in 1..PdfReaderPolicy.MAX_PAGES && info.width in 1..edge && info.height in 1..edge
                        if (valid) finish(info) else finish(issue = PdfReadIssue.INVALID)
                    }
                })
                connection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName, service: IBinder) {
                        if (complete.get()) return
                        try {
                            ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY).use { input ->
                                ParcelFileDescriptor.open(output, ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_TRUNCATE).use { target ->
                                    Messenger(service).send(Message.obtain(null, PdfRenderService.RENDER).apply {
                                        replyTo = receiver
                                        data = Bundle().apply {
                                            putParcelable("input", input); putParcelable("output", target)
                                            putInt("page", page); putInt("edge", edge)
                                        }
                                    })
                                }
                            }
                        } catch (_: Exception) { finish(issue = PdfReadIssue.PROCESS) }
                    }
                    override fun onServiceDisconnected(name: ComponentName) { finish(issue = PdfReadIssue.PROCESS) }
                    override fun onNullBinding(name: ComponentName) { finish(issue = PdfReadIssue.PROCESS) }
                    override fun onBindingDied(name: ComponentName) { finish(issue = PdfReadIssue.PROCESS) }
                }
                continuation.invokeOnCancellation { complete.set(true); detach() }
                synchronized(lock) {
                    if (!complete.get()) {
                        try {
                            bound = context.bindService(Intent(context, PdfRenderService::class.java), connection, Context.BIND_AUTO_CREATE)
                            if (!bound) finish(issue = PdfReadIssue.PROCESS)
                        } catch (_: Exception) { finish(issue = PdfReadIssue.PROCESS) }
                    }
                }
            } }
        } catch (_: TimeoutCancellationException) { throw PdfReadException(PdfReadIssue.TIMEOUT) }
    }
}

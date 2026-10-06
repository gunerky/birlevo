package com.example.contentstudio.media

import android.content.*
import android.os.*
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class PdfPageClient(private val context: Context) {
    suspend fun create(sources: List<PdfSource>, pages: List<PdfPageRef>, output: File): Int {
        PdfPagePlan.validate(pages, sources.map { it.pages })
        if (sources.sumOf { it.bytes } > PdfPagePlan.MAX_BYTES) throw PdfPageException(PdfPageIssue.LIMIT)
        try { return withTimeout(60_000L) { suspendCancellableCoroutine { continuation ->
            val complete = AtomicBoolean(false)
            val lock = Any()
            var bound = false
            lateinit var connection: ServiceConnection
            fun detach() = synchronized(lock) { if (bound) { bound = false; runCatching { context.unbindService(connection) } } }
            fun finish(count: Int? = null, issue: PdfPageIssue? = null) {
                if (!complete.compareAndSet(false, true)) return
                detach()
                if (continuation.isActive) {
                    if (count != null) continuation.resume(count)
                    else continuation.resumeWithException(PdfPageException(issue ?: PdfPageIssue.PROCESS))
                }
            }
            val receiver = Messenger(object : Handler(Looper.getMainLooper()) {
                override fun handleMessage(message: Message) {
                    val error = message.data.getString("error")
                    val count = message.data.getInt("pages")
                    if (error != null) finish(issue = PdfPageIssue.entries.find { it.name == error } ?: PdfPageIssue.INVALID)
                    else if (count == pages.size) finish(count) else finish(issue = PdfPageIssue.INVALID)
                }
            })
            connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, service: IBinder) {
                    if (complete.get()) return
                    val descriptors = mutableListOf<ParcelFileDescriptor>()
                    try {
                        val inputs = ArrayList(sources.map { ParcelFileDescriptor.open(it.file, ParcelFileDescriptor.MODE_READ_ONLY).also(descriptors::add) })
                        val target = ParcelFileDescriptor.open(output, ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_TRUNCATE).also(descriptors::add)
                        Messenger(service).send(Message.obtain(null, 1).apply {
                            replyTo = receiver
                            data = Bundle().apply {
                                putParcelableArrayList("inputs", inputs); putParcelable("output", target)
                                putIntArray("plan", pages.flatMap { listOf(it.source, it.page, it.rotation) }.toIntArray())
                            }
                        })
                    } catch (_: Exception) { finish(issue = PdfPageIssue.PROCESS) }
                    finally { descriptors.forEach { runCatching { it.close() } } }
                }
                override fun onServiceDisconnected(name: ComponentName) { finish(issue = PdfPageIssue.PROCESS) }
                override fun onNullBinding(name: ComponentName) { finish(issue = PdfPageIssue.PROCESS) }
                override fun onBindingDied(name: ComponentName) { finish(issue = PdfPageIssue.PROCESS) }
            }
            continuation.invokeOnCancellation { complete.set(true); detach() }
            synchronized(lock) { if (!complete.get()) {
                try {
                    bound = context.bindService(Intent(context, PdfPageService::class.java), connection, Context.BIND_AUTO_CREATE)
                    if (!bound) finish(issue = PdfPageIssue.PROCESS)
                } catch (_: Exception) { finish(issue = PdfPageIssue.PROCESS) }
            } }
        } } } catch (_: TimeoutCancellationException) { throw PdfPageException(PdfPageIssue.PROCESS) }
    }
}

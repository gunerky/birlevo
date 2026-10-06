package com.example.contentstudio.media

import android.app.Service
import android.content.Intent
import android.os.*
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException

/** No application files, network or URI permissions. Only read-only source FDs and
 * one disposable output FD; killed on completion, timeout and cancellation. */
class PdfPageService : Service() {
    private lateinit var messenger: Messenger
    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
        val thread = HandlerThread("pdf-pages").apply { start() }
        messenger = Messenger(object : Handler(thread.looper) {
            override fun handleMessage(message: Message) {
                val reply = Bundle()
                val descriptors = mutableListOf<ParcelFileDescriptor>()
                try {
                    val data = message.data.apply { classLoader = ParcelFileDescriptor::class.java.classLoader }
                    @Suppress("DEPRECATION")
                    val inputs = data.getParcelableArrayList<ParcelFileDescriptor>("inputs") ?: throw PdfPageException(PdfPageIssue.INVALID)
                    descriptors.addAll(inputs)
                    @Suppress("DEPRECATION")
                    val output = data.getParcelable<ParcelFileDescriptor>("output") ?: throw PdfPageException(PdfPageIssue.INVALID)
                    descriptors.add(output)
                    val plan = data.getIntArray("plan") ?: throw PdfPageException(PdfPageIssue.INVALID)
                    if (plan.size % 3 != 0 || plan.size / 3 !in 1..PdfPagePlan.MAX_PAGES) throw PdfPageException(PdfPageIssue.LIMIT)
                    if (inputs.size !in 1..PdfPagePlan.MAX_SOURCES || inputs.any { it.statSize !in 1..PdfPagePlan.MAX_BYTES } ||
                        inputs.sumOf { it.statSize } > PdfPagePlan.MAX_BYTES) throw PdfPageException(PdfPageIssue.LIMIT)
                    val pages = plan.asList().chunked(3).mapIndexed { index, ref -> PdfPageRef("$index", ref[0], ref[1], ref[2]) }
                    val streams = inputs.map { ParcelFileDescriptor.AutoCloseInputStream(it) }
                    try { ParcelFileDescriptor.AutoCloseOutputStream(output).use { target ->
                        reply.putInt("pages", PdfPageEngine.create(streams, pages, target))
                    } } finally { streams.forEach { runCatching { it.close() } } }
                } catch (error: PdfPageException) { reply.putString("error", error.issue.name) }
                catch (_: InvalidPasswordException) { reply.putString("error", PdfPageIssue.PROTECTED.name) }
                catch (_: OutOfMemoryError) { reply.putString("error", PdfPageIssue.MEMORY.name) }
                catch (_: Exception) { reply.putString("error", PdfPageIssue.INVALID.name) }
                finally { descriptors.forEach { runCatching { it.close() } } }
                try { message.replyTo?.send(Message.obtain(null, 1).apply { data = reply }) } catch (_: RemoteException) { }
            }
        })
    }
    override fun onBind(intent: Intent): IBinder = messenger.binder
    override fun onUnbind(intent: Intent): Boolean { Process.killProcess(Process.myPid()); return false }
}

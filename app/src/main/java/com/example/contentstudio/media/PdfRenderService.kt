package com.example.contentstudio.media

import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.*
import java.io.FilterOutputStream
import java.io.OutputStream

/** Isolated UID, no application storage/network access. Receives only a read-only PDF
 * descriptor and a disposable output descriptor; never executes embedded PDF actions. */
class PdfRenderService : Service() {
    private lateinit var thread: HandlerThread
    private lateinit var messenger: Messenger
    override fun onCreate() {
        super.onCreate()
        thread = HandlerThread("pdf-render").apply { start() }
        messenger = Messenger(object : Handler(thread.looper) {
            override fun handleMessage(message: Message) {
                if (message.what != RENDER) return
                val reply = Bundle()
                try {
                    val data = message.data.apply { classLoader = ParcelFileDescriptor::class.java.classLoader }
                    @Suppress("DEPRECATION")
                    val input = data.getParcelable<ParcelFileDescriptor>("input") ?: throw PdfReadException(PdfReadIssue.INVALID)
                    @Suppress("DEPRECATION")
                    val output = data.getParcelable<ParcelFileDescriptor>("output") ?: run { input.close(); throw PdfReadException(PdfReadIssue.INVALID) }
                    input.use { descriptor -> output.use { target ->
                        val renderer = try { PdfRenderer(descriptor) }
                            catch (_: SecurityException) { throw PdfReadException(PdfReadIssue.PASSWORD) }
                        renderer.use {
                            PdfReaderPolicy.validatePageCount(it.pageCount)
                            val index = data.getInt("page", -1)
                            if (index !in 0 until it.pageCount) throw PdfReadException(PdfReadIssue.INVALID)
                            it.openPage(index).use { page ->
                                val (width, height) = PdfReaderPolicy.rasterSize(page.width, page.height, data.getInt("edge"))
                                val format = ImageOutputFormat.valueOf(data.getString("format") ?: ImageOutputFormat.PNG.name)
                                val quality = data.getInt("quality", 80)
                                require(format in PdfReaderPolicy.imageFormats && quality in 40..95)
                                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                try {
                                    bitmap.eraseColor(Color.WHITE)
                                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    bitmap.setHasAlpha(false) // PDF pages are rendered onto an opaque white canvas.
                                    ParcelFileDescriptor.AutoCloseOutputStream(target).use { stream ->
                                        val bounded = object : FilterOutputStream(stream) {
                                            var bytes = 0L
                                            override fun write(value: Int) { checkSize(1); out.write(value) }
                                            override fun write(buffer: ByteArray, offset: Int, count: Int) { checkSize(count); out.write(buffer, offset, count) }
                                            fun checkSize(count: Int) {
                                                bytes += count
                                                if (bytes > PdfReaderPolicy.MAX_BYTES) throw PdfReadException(PdfReadIssue.LIMIT)
                                            }
                                        }
                                        ImageEncoder.write(bitmap, format, quality, bounded)
                                    }
                                    reply.putInt("pages", it.pageCount)
                                    reply.putInt("width", width); reply.putInt("height", height)
                                } finally { bitmap.recycle() }
                            }
                        }
                    } }
                } catch (error: PdfReadException) { reply.putString("error", error.issue.name) }
                catch (_: OutOfMemoryError) { reply.putString("error", PdfReadIssue.MEMORY.name) }
                catch (_: Exception) { reply.putString("error", PdfReadIssue.INVALID.name) }
                try { message.replyTo?.send(Message.obtain(null, RENDER).apply { data = reply }) }
                catch (_: RemoteException) { }
            }
        })
    }
    override fun onBind(intent: Intent): IBinder = messenger.binder
    override fun onUnbind(intent: Intent): Boolean {
        // Last client has finished or timed out. Even a blocked native parser cannot
        // retain an isolated process or its source descriptor after cancellation.
        Process.killProcess(Process.myPid())
        return false
    }
    companion object { const val RENDER = 1 }
}

package com.example.contentstudio.media

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** A shareable snapshot, independent of an encoder's working file. Save exports remain permanent. */
data class MediaOutput(val uri: Uri, val mimeType: String, val name: String, val bytes: Long) {
    fun viewIntent(): Intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mimeType).apply {
        clipData = ClipData.newRawUri(name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    fun shareIntent(): Intent = Intent(Intent.ACTION_SEND).setType(mimeType).apply {
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri(name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

class MediaOutputs(private val context: Context) {
    /** Only explicitly requested snapshots are staged. Old shares are temporary; projects are not. */
    suspend fun stage(file: File, mime: String, name: String, sourceBytes: Long? = null,
        allowNonSmallerCopy: Boolean = false): MediaOutput {
        val bytes = file.length()
        require(bytes > 0)
        require(sourceBytes == null || compressionBenefit(sourceBytes, bytes) == CompressionBenefit.SMALLER ||
            allowNonSmallerCopy) { "Non-saving output requires explicit approval" }
        val extension = when (mime) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/bmp" -> "bmp"
            "video/mp4" -> "mp4"
            "video/webm" -> "webm"
            "audio/mp4" -> "m4a"
            "audio/wav" -> "wav"
            "application/pdf" -> "pdf"
            "text/plain" -> "txt"
            "text/csv" -> "csv"
            "text/tab-separated-values" -> "tsv"
            else -> throw IllegalArgumentException("Unsupported output")
        }
        val root = File(context.cacheDir, "shared-outputs").apply { check(isDirectory || mkdirs()) }
        val cutoff = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        root.listFiles()?.filter { it.isDirectory && it.lastModified() < cutoff }?.forEach { it.deleteRecursively() }
        val folder = File(root, UUID.randomUUID().toString()).apply { check(mkdir()) }
        val base = name.substringBeforeLast('.').replace('/', '_').replace('\\', '_')
            .filterNot { it.isISOControl() }.take(64).ifBlank { "output" }
        val destination = File(folder, "$base.$extension")
        try {
            file.inputStream().use { copyAtomic(it, destination, bytes) }
            check(destination.length() == bytes) { "Incomplete output snapshot" }
            currentCoroutineContext().ensureActive()
            return MediaOutput(uri(destination), mime, destination.name, bytes)
        } catch (error: Throwable) { folder.deleteRecursively(); throw error }
    }

    /** Copies original encoded bytes, including orientation, without changing image quality. */
    suspend fun retainImage(source: Uri): Uri {
        val bounds = SlideImages.validate(context, source)
        val root = File(context.filesDir, "project-images").apply { check(isDirectory || mkdirs()) }
        val destination = File(root, "${UUID.randomUUID()}.${ImageFormats.extension(bounds.outMimeType)}")
        try {
            val input = context.contentResolver.openInputStream(source) ?: throw MediaImportException(ImportIssue.FILE_ACCESS)
            input.use { copyAtomic(it, destination, FormatSupport.MAX_IMAGE_BYTES, ImportIssue.IMAGE_LIMIT) }
            val owned = uri(destination)
            SlideImages.validate(context, owned)
            currentCoroutineContext().ensureActive()
            return owned
        } catch (error: Throwable) { destination.delete(); throw error }
    }

    private fun uri(file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.outputs", file)

    /** Used only for newly copied images that were never attached to a draft. */
    fun discardUnattachedImage(uri: Uri) {
        require(uri.authority == "${context.packageName}.outputs" && uri.pathSegments.firstOrNull() == "project-images")
        context.contentResolver.delete(uri, null, null)
    }

    private suspend fun copyAtomic(input: InputStream, destination: File, limit: Long, limitIssue: ImportIssue? = null) {
        val temporary = File(destination.parentFile, "${destination.name}.part")
        try {
            FileOutputStream(temporary).use { output ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > limit && limitIssue != null) throw MediaImportException(limitIssue)
                    require(total <= limit) { "Image or output exceeds size limit" }
                    output.write(buffer, 0, count)
                }
                require(total > 0)
                output.fd.sync()
            }
            currentCoroutineContext().ensureActive()
            check(temporary.renameTo(destination)) { "Cannot commit output" }
        } finally { temporary.delete() }
    }
}

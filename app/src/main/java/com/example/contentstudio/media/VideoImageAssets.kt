package com.example.contentstudio.media

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import java.io.File
import java.util.UUID
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

data class ImportedVideoImage(val layer: VideoImageLayer, val preview: Bitmap)

/** Byte-for-byte snapshot in the video's ownership domain, never a shared image-tool cache. */
class VideoImageAssets(private val context: Context) {
    private val folder get() = File(context.noBackupFilesDir, "video-compression").apply { check(isDirectory || mkdirs()) }
    private val decoder = ImageCompressor(context)
    fun resolve(layer: VideoImageLayer) = layer.asset.resolve(folder, "overlay-", FormatSupport.MAX_IMAGE_BYTES)
    suspend fun importImage(uri: Uri): ImportedVideoImage {
        val file = File.createTempFile("overlay-", ".image", folder)
        var preview: Bitmap? = null
        var delivered = false
        try {
            val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }?.take(256) ?: "image"
            context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { output ->
                val buffer = ByteArray(65536); var total = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer); if (count < 0) break
                    total += count
                    if (total > FormatSupport.MAX_IMAGE_BYTES) throw MediaImportException(ImportIssue.IMAGE_LIMIT)
                    output.write(buffer, 0, count)
                }
                output.fd.sync()
            } } ?: throw MediaImportException(ImportIssue.FILE_ACCESS)
            val info = inspect(file); preview = info.third
            currentCoroutineContext().ensureActive()
            return ImportedVideoImage(VideoImageLayer(SessionAsset(UUID.randomUUID().toString(), file.name,
                uri.toString(), name, file.length()), info.first, info.second), preview).also { delivered = true }
        } finally { if (!delivered) { preview?.recycle(); file.delete() } }
    }
    suspend fun restore(layer: VideoImageLayer): Bitmap {
        val info = inspect(resolve(layer))
        try {
            require(info.first == layer.width && info.second == layer.height)
            currentCoroutineContext().ensureActive()
            return info.third
        } catch (error: Throwable) { info.third.recycle(); throw error }
    }
    private fun inspect(file: File): Triple<Int, Int, Bitmap> {
        val bounds = ImageInput.bounds { file.inputStream() }
        if (ModernImages.isModern(bounds.outMimeType) && Build.VERSION.SDK_INT >= 28) {
            val decoded = ModernImages.decode(file, 512)
            return Triple(decoded.width, decoded.height, decoded.bitmap)
        }
        val rotated = decoder.orientation(file, bounds.outMimeType) in 5..8
        return Triple(if (rotated) bounds.outHeight else bounds.outWidth,
            if (rotated) bounds.outWidth else bounds.outHeight, decoder.decode(file, 512))
    }
    fun decode(layer: VideoImageLayer, edge: Int): Bitmap = decoder.decode(resolve(layer), edge)
}

package com.example.contentstudio.media

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Bounded, byte-exact private snapshots shared by photo PDF and still-video creation. */
internal class ImageSnapshots(private val context: Context, private val folder: File) {
    suspend fun importPages(
        uris: List<Uri>,
        room: Int,
        remainingBytes: Long,
        progress: (Int) -> Unit,
    ): List<PhotoPage> {
        if (uris.isEmpty() || uris.size > room) throw MediaImportException(ImportIssue.PHOTO_LIMIT)
        val pages = mutableListOf<PhotoPage>()
        var current: File? = null
        var total = 0L
        try {
            uris.forEachIndexed { index, uri ->
                currentCoroutineContext().ensureActive()
                val bounds = SlideImages.validate(context, uri)
                val name =
                    context.contentResolver
                        .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { if (it.moveToFirst()) it.getString(0) else null }
                        ?.take(256) ?: "image.${ImageFormats.extension(bounds.outMimeType)}"
                val file =
                    File.createTempFile(
                        "page-",
                        ".${ImageFormats.extension(bounds.outMimeType)}",
                        folder,
                    )
                current = file
                (context.contentResolver.openInputStream(uri)
                        ?: throw MediaImportException(ImportIssue.FILE_ACCESS))
                    .use { input ->
                        FileOutputStream(file).use { output ->
                            val buffer = ByteArray(65536)
                            var bytes = 0L
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val size = input.read(buffer)
                                if (size < 0) break
                                bytes += size
                                if (bytes > FormatSupport.MAX_IMAGE_BYTES)
                                    throw MediaImportException(ImportIssue.IMAGE_LIMIT)
                                if (total + bytes > remainingBytes)
                                    throw MediaImportException(ImportIssue.PHOTO_LIMIT)
                                output.write(buffer, 0, size)
                            }
                            if (bytes <= 0) throw MediaImportException(ImportIssue.INVALID_MEDIA)
                            output.fd.sync()
                        }
                    }
                SlideImages.validate(context, Uri.fromFile(file))
                total += file.length()
                pages += PhotoPage(UUID.randomUUID().toString(), file, uri, name, file.length())
                current = null
                progress((index + 1) * 100 / uris.size)
            }
            currentCoroutineContext().ensureActive()
            return pages
        } catch (error: Throwable) {
            current?.delete()
            pages.forEach { it.file.delete() }
            throw error
        }
    }
}

package com.example.contentstudio.audio

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Copies exact offline LGPL source bytes to a user-selected document, never app/project media. */
internal object Mp3SourceBundle {
    const val ASSET = "licenses/birlevo-mp3-sources.zip"
    suspend fun save(context: Context, destination: Uri) {
        try {
            context.contentResolver.openOutputStream(destination, "w")?.use { output ->
                context.assets.open(ASSET).use { input ->
                    val buffer = ByteArray(65536)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer); if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                }
                output.flush(); currentCoroutineContext().ensureActive()
            } ?: throw IOException("Source destination unavailable")
        } catch (failure: Throwable) {
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, destination) }
            throw failure
        }
    }
}

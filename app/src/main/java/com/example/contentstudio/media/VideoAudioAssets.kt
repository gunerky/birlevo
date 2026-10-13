package com.example.contentstudio.media

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.contentstudio.audio.AudioEngine
import com.example.contentstudio.audio.AudioPolicy
import java.io.File
import java.util.UUID
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Audio snapshots belong to the video session, never the audio tool's disposable cache. */
class VideoAudioAssets(private val context: Context) {
    private val folder get() = File(context.noBackupFilesDir, "video-compression").apply { check(isDirectory || mkdirs()) }
    private val inspector = AudioEngine(context)
    fun resolve(layer: VideoAudioLayer) = layer.asset.resolve(folder, "sound-", AudioPolicy.MAX_BYTES)
    suspend fun importAudio(uri: Uri): VideoAudioLayer {
        val file = File.createTempFile("sound-", ".media", folder)
        var delivered = false
        try {
            val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }?.take(256) ?: "audio"
            context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { output ->
                val buffer = ByteArray(65536); var total = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer); if (count < 0) break
                    total += count; require(total <= AudioPolicy.MAX_BYTES)
                    output.write(buffer, 0, count)
                }
                output.fd.sync()
            } } ?: throw java.io.IOException("Audio source unavailable")
            val info = inspector.inspect(file); currentCoroutineContext().ensureActive()
            return VideoAudioLayer(SessionAsset(UUID.randomUUID().toString(), file.name, uri.toString(), name, file.length()),
                info.durationMs, info.sampleRate, info.channels).also { delivered = true }
        } finally { if (!delivered) file.delete() }
    }
    fun restore(layer: VideoAudioLayer) {
        val info = inspector.inspect(resolve(layer))
        require(info.durationMs == layer.durationMs && info.sampleRate == layer.rate && info.channels == layer.channels)
    }
}

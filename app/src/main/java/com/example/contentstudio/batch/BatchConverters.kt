package com.example.contentstudio.batch

import android.content.Context
import android.net.Uri
import com.example.contentstudio.audio.*
import com.example.contentstudio.media.*
import java.io.File
import java.io.IOException

internal class BatchOutputFailure(val video: VideoOutputIssue? = null, val audio: AudioOutputIssue? = null) : IOException("Requested output unavailable")

/** Adapters own transient previews; only the queue's metadata ever reaches Compose. */
internal class BatchConverters(context: Context, private val assets: File, private val kind: BatchKind) {
    private val images by lazy { ImageCompressor(context, assets) }
    private val videos by lazy { VideoCompressor(context, ownedFolder = assets) }
    private val audio by lazy { AudioEngine(context, assets) }
    suspend fun import(uri: Uri): BatchItem = when (kind) {
        BatchKind.IMAGE -> images.importImage(uri).let { source ->
            try { item(source.file, uri, source.name, source.bytes, source.width, source.height) }
            finally { source.preview.recycle() }
        }
        BatchKind.VIDEO -> videos.importVideo(uri).let { source ->
            try { item(source.file, uri, source.name, source.bytes, source.info.width, source.info.height, source.info.durationMs) }
            finally { source.info.poster?.recycle() }
        }
        BatchKind.AUDIO -> audio.importAudio(uri).let { source ->
            item(source.file, uri, source.name, source.bytes, 0, 0, source.info.durationMs)
        }
    }
    private fun item(file: File, uri: Uri, name: String, bytes: Long, width: Int, height: Int, duration: Long = 0) =
        BatchItem(java.util.UUID.randomUUID().toString(), file.name, uri.toString(), name.take(256).ifBlank { "media" },
            bytes, width, height, durationMs = duration)
    suspend fun convert(item: BatchItem, options: BatchOptions, progress: (Int?) -> Unit): File {
        require(options.kind == kind)
        val file = BatchPolicy.owned(assets, item.source, "source-")
        val asset = SessionAsset(item.id, item.source, item.uri, item.name, item.bytes)
        return when (kind) {
            BatchKind.IMAGE -> images.compressFile(file, options.quality, options.edge, options.format).let {
                it.preview.recycle(); it.file
            }
            BatchKind.VIDEO -> {
                val source = videos.restore(asset)
                try {
                    val result = videos.compress(source, options.videoQuality, options.videoEdge,
                        VideoTrim(0, source.info.durationMs), options.videoFormat, progress)
                    result.info.poster?.recycle()
                    result.file
                } catch (failure: VideoOutputUnavailable) { throw BatchOutputFailure(video = failure.issue) }
                finally { source.info.poster?.recycle() }
            }
            BatchKind.AUDIO -> {
                val source = audio.restore(asset)
                audio.outputIssue(source.info, options.audioTarget, options.audioBitrate)?.let { throw BatchOutputFailure(audio = it) }
                audio.convert(source, options.audioTarget, options.audioBitrate, progress).file
            }
        }
    }
}

package com.example.contentstudio.batch

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.example.contentstudio.audio.*
import com.example.contentstudio.media.*
import com.example.contentstudio.documents.*
import java.io.File
import java.io.IOException
import kotlinx.coroutines.ensureActive

internal class BatchOutputFailure(val video: VideoOutputIssue? = null, val audio: AudioOutputIssue? = null) : IOException("Requested output unavailable")

/** Adapters own transient previews; only the queue's metadata ever reaches Compose. */
internal class BatchConverters(context: Context, private val assets: File, private val kind: BatchKind) {
    private val images by lazy { ImageCompressor(context, assets) }
    private val videos by lazy { VideoCompressor(context, ownedFolder = assets) }
    private val audio by lazy { AudioEngine(context, assets) }
    private val documents by lazy { TextDocumentEngine(context, assets) }
    suspend fun import(uri: Uri, options: BatchOptions): BatchItem = when (kind) {
        BatchKind.DOCUMENT -> documents.import(uri, options.documentEncoding).let { imported ->
            item(imported.source.file, uri, imported.source.name, imported.source.bytes, 0, 0)
                .copy(document = BatchDocument.from(imported.content))
        }
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
    suspend fun convert(item: BatchItem, options: BatchOptions, approvedDocuments: Set<String>, progress: (Int?) -> Unit): File {
        require(options.kind == kind)
        val file = BatchPolicy.owned(assets, item.source, "source-")
        val asset = SessionAsset(item.id, item.source, item.uri, item.name, item.bytes)
        return when (kind) {
            BatchKind.DOCUMENT -> convertDocument(item, file, options, approvedDocuments)
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

    private suspend fun convertDocument(item: BatchItem, file: File, options: BatchOptions, approved: Set<String>): File {
        val meta = checkNotNull(item.document)
        if (options.documentTarget !in DocumentPolicy.targets(meta.kind)) throw BatchDocumentFailure(BatchDocumentIssue.TARGET)
        if (meta.office == OfficeFormat.XLSX && meta.sheet < 0) throw BatchDocumentFailure(BatchDocumentIssue.SHEET)
        if (meta.office != null && meta.key(item, options) !in approved) throw BatchDocumentFailure(BatchDocumentIssue.CONSENT)
        try {
            val content = documents.read(DocumentSource(file, item.uri.toUri(), item.name, item.bytes, meta.office),
                meta.kind, options.documentEncoding, options.documentDelimiter, meta.sheet, meta.mode)
            if (BatchDocument.from(content).copy(sheet = meta.sheet, mode = meta.mode) != meta)
                throw BatchDocumentFailure(BatchDocumentIssue.INVALID)
            val result = documents.create(content, options.documentTarget, officeConsent = meta.office != null)
            val output = File(assets, "output-${java.util.UUID.randomUUID()}.${options.extension}")
            try {
                require(result.file.canonicalFile.parentFile == assets.canonicalFile)
                java.io.RandomAccessFile(result.file, "rw").use { it.fd.sync() }
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                check(result.file.renameTo(output))
                return output
            } finally { result.file.delete() }
        } catch (failure: DocumentException) { throw BatchDocumentFailure(BatchDocumentIssue.valueOf(failure.issue.name)) }
    }
}

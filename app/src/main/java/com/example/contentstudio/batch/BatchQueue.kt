package com.example.contentstudio.batch

import com.example.contentstudio.media.*
import com.example.contentstudio.audio.*
import com.example.contentstudio.documents.*
import com.example.contentstudio.processing.ProcessingKind
import java.io.File
import java.util.Locale
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

enum class BatchKind(val processing: ProcessingKind) {
    IMAGE(ProcessingKind.BATCH_IMAGE), VIDEO(ProcessingKind.BATCH_VIDEO), AUDIO(ProcessingKind.BATCH_AUDIO), DOCUMENT(ProcessingKind.BATCH_DOCUMENT)
}
data class BatchOptions(val format: ImageOutputFormat = ImageOutputFormat.JPEG,
    val quality: Int = 80, val edge: Int = 1920, val kind: BatchKind = BatchKind.IMAGE,
    val videoFormat: VideoOutputFormat = VideoOutputFormat.MP4_AVC,
    val videoQuality: VideoQuality = VideoQuality.BALANCED, val videoEdge: Int = 1280,
    val audioTarget: AudioTarget = AudioTarget.M4A, val audioBitrate: Int = 128_000,
    val documentTarget: DocumentTarget = DocumentTarget.PDF, val documentEncoding: DocumentEncoding = DocumentEncoding.AUTO,
    val documentDelimiter: Char? = null) {
    init { require(quality in 40..95 && edge in listOf(1280, 1920, 2560) &&
        videoEdge in listOf(640, 1280, 1920) && audioBitrate in AudioPolicy.bitrates && documentDelimiter in listOf(null, ',', ';', '\t')) }
    val extension get() = when(kind) { BatchKind.IMAGE -> format.extension; BatchKind.VIDEO -> videoFormat.extension; BatchKind.AUDIO -> audioTarget.extension; BatchKind.DOCUMENT -> documentTarget.extension }
}
data class BatchItem(val id: String, val source: String, val uri: String, val name: String,
    val bytes: Long, val width: Int, val height: Int, val output: String? = null,
    val outputBytes: Long = 0, val issue: ImportIssue? = null, val included: Boolean = true,
    val durationMs: Long = 0, val videoIssue: VideoOutputIssue? = null, val audioIssue: AudioOutputIssue? = null,
    val document: BatchDocument? = null, val documentIssue: BatchDocumentIssue? = null) {
    val copyKey get() = output?.let { "$id/$it/$outputBytes" }
    val needsConsent get() = output != null && outputBytes >= bytes
}
data class BatchQueue(val revision: String = UUID.randomUUID().toString(),
    val options: BatchOptions = BatchOptions(), val items: List<BatchItem> = emptyList()) {
    fun revised(items: List<BatchItem> = this.items, options: BatchOptions = this.options) =
        copy(revision = UUID.randomUUID().toString(), items = items, options = options)
}

/** Metadata only. Copy approvals are deliberately absent and never survive process death. */
object BatchPolicy {
    const val MAX_FILES = 20
    const val MAX_BYTES = 512L * 1024 * 1024
    fun owned(folder: File, name: String, prefix: String): File {
        require(name.startsWith(prefix) && name.length <= 160 &&
            name.matches(Regex("[a-zA-Z0-9.-]+")) && !name.contains(".."))
        return File(folder, name).also { require(it.canonicalFile.parentFile == folder.canonicalFile) }
    }
    fun requireExportable(batch: BatchQueue, approved: Set<String>, documentApproved: Set<String> = emptySet()): List<BatchItem> {
        require(batch.items.isNotEmpty() && batch.items.all { it.output != null || it.issue != null })
        val selected = batch.items.filter { it.included && it.output != null }
        require(selected.isNotEmpty())
        require(selected.all { !it.needsConsent || it.copyKey in approved })
        require(selected.all { item -> item.document?.let { it.office == null || it.key(item, batch.options) in documentApproved } != false })
        require(selected.sumOf { it.outputBytes } <= MAX_BYTES)
        return selected
    }
    /** A flat archive, no user-supplied paths. Ordinals also prevent case-insensitive collisions. */
    fun entryName(index: Int, name: String, format: ImageOutputFormat): String {
        return entryName(index, name, format.extension)
    }
    fun entryName(index: Int, name: String, extension: String): String {
        require(extension.matches(Regex("[a-z0-9]{1,8}")))
        val base = name.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.')
            .map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '_' }
            .joinToString("").take(72).ifBlank { "image" }
        return String.format(Locale.ROOT, "%02d_%s.%s", index + 1, base, extension)
    }
    fun encode(batch: BatchQueue): String = JSONObject().put("version", when(batch.options.kind) { BatchKind.IMAGE -> 1; BatchKind.DOCUMENT -> 3; else -> 2 }).put("revision", batch.revision)
        .put("format", batch.options.format.name).put("quality", batch.options.quality).put("edge", batch.options.edge)
        .apply { if (batch.options.kind != BatchKind.IMAGE) {
            put("kind", batch.options.kind.name).put("videoFormat", batch.options.videoFormat.name)
                .put("videoQuality", batch.options.videoQuality.name).put("videoEdge", batch.options.videoEdge)
                .put("audioTarget", batch.options.audioTarget.name).put("audioBitrate", batch.options.audioBitrate)
        } }
        .apply { if (batch.options.kind == BatchKind.DOCUMENT) put("documentTarget", batch.options.documentTarget.name)
            .put("documentEncoding", batch.options.documentEncoding.name).put("documentDelimiter", batch.options.documentDelimiter?.code ?: -1) }
        .put("items", JSONArray().also { array -> batch.items.forEach { item -> array.put(JSONObject()
            .put("id", item.id).put("source", item.source).put("uri", item.uri).put("name", item.name)
            .put("bytes", item.bytes).put("width", item.width).put("height", item.height)
            .put("included", item.included).apply {
                if (batch.options.kind != BatchKind.IMAGE) put("durationMs", item.durationMs)
                item.videoIssue?.let { put("videoIssue", it.name) }; item.audioIssue?.let { put("audioIssue", it.name) }
                item.document?.let { put("document", it.json()) }; item.documentIssue?.let { put("documentIssue", it.name) }
                item.output?.let { put("output", it).put("outputBytes", item.outputBytes) }
                item.issue?.let { put("issue", it.name) }
            }) } }).toString()
    fun decode(text: String): BatchQueue {
        require(text.toByteArray(Charsets.UTF_8).size <= AtomicMediaSession.MAX_CHECKPOINT_BYTES)
        val root = JSONObject(text); val version = root.getInt("version"); require(version in 1..3)
        val revision = root.getString("revision"); UUID.fromString(revision)
        val kind = if (version == 1) BatchKind.IMAGE else BatchKind.valueOf(root.getString("kind")).also { require(it != BatchKind.IMAGE) }
        require((version == 3) == (kind == BatchKind.DOCUMENT))
        val options = BatchOptions(ImageOutputFormat.valueOf(root.getString("format")), root.getInt("quality"), root.getInt("edge"), kind,
            if (version >= 2) VideoOutputFormat.valueOf(root.getString("videoFormat")) else VideoOutputFormat.MP4_AVC,
            if (version >= 2) VideoQuality.valueOf(root.getString("videoQuality")) else VideoQuality.BALANCED,
            if (version >= 2) root.getInt("videoEdge") else 1280,
            if (version >= 2) AudioTarget.valueOf(root.getString("audioTarget")) else AudioTarget.M4A,
            if (version >= 2) root.getInt("audioBitrate") else 128_000,
            if (version == 3) DocumentTarget.valueOf(root.getString("documentTarget")) else DocumentTarget.PDF,
            if (version == 3) DocumentEncoding.valueOf(root.getString("documentEncoding")) else DocumentEncoding.AUTO,
            if (version == 3) root.getInt("documentDelimiter").let { require(it in listOf(-1, 44, 59, 9)); if(it == -1) null else it.toChar() } else null)
        val values = root.getJSONArray("items"); require(values.length() <= MAX_FILES)
        val items = (0 until values.length()).map { index -> values.getJSONObject(index).let { value ->
            BatchItem(value.getString("id"), value.getString("source"), value.getString("uri"),
                value.getString("name"), value.getLong("bytes"), value.getInt("width"), value.getInt("height"),
                if (value.has("output")) value.getString("output") else null, value.optLong("outputBytes"),
                if (value.has("issue")) ImportIssue.valueOf(value.getString("issue")) else null,
                value.getBoolean("included"), if (version >= 2) value.getLong("durationMs") else value.optLong("durationMs"),
                if (value.has("videoIssue")) VideoOutputIssue.valueOf(value.getString("videoIssue")) else null,
                if (value.has("audioIssue")) AudioOutputIssue.valueOf(value.getString("audioIssue")) else null,
                if (value.has("document")) BatchDocument.decode(value.getJSONObject("document")) else null,
                if (value.has("documentIssue")) BatchDocumentIssue.valueOf(value.getString("documentIssue")) else null).also { item ->
                UUID.fromString(item.id)
                owned(File("batch"), item.source, "source-")
                require(item.uri.length <= 8192 && item.name.length in 1..256)
                val fileLimit = when(kind) { BatchKind.IMAGE -> FormatSupport.MAX_IMAGE_BYTES; BatchKind.DOCUMENT -> DocumentPolicy.MAX_BYTES; else -> MAX_BYTES }
                require(item.bytes in 1..fileLimit)
                if (kind == BatchKind.IMAGE) require(item.width > 0 && item.height > 0 && item.width.toLong() * item.height <= FormatSupport.MAX_IMAGE_PIXELS && item.durationMs == 0L)
                else if (kind == BatchKind.DOCUMENT) require(item.width == 0 && item.height == 0 && item.durationMs == 0L && item.document != null)
                else require(item.durationMs in 1..600_000 && if (kind == BatchKind.VIDEO) item.width >= 16 && item.height >= 16 else item.width == 0 && item.height == 0)
                require(item.videoIssue == null || kind == BatchKind.VIDEO)
                require(item.audioIssue == null || kind == BatchKind.AUDIO)
                require(item.document == null || kind == BatchKind.DOCUMENT)
                require(item.documentIssue == null || kind == BatchKind.DOCUMENT)
                if (item.output != null) {
                    owned(File("batch"), item.output, "output-")
                    require(item.output.endsWith(".${options.extension}") && item.outputBytes in (if (kind == BatchKind.DOCUMENT) 0L..MAX_BYTES else 1L..fileLimit) && item.issue == null && item.videoIssue == null && item.audioIssue == null && item.documentIssue == null)
                    item.document?.let { require(options.documentTarget in DocumentPolicy.targets(it.kind) && (it.office != OfficeFormat.XLSX || it.sheet >= 0)) }
                } else require(item.outputBytes == 0L)
            }
        } }
        require(items.map { it.id }.distinct().size == items.size && items.map { it.source }.distinct().size == items.size)
        require(items.mapNotNull { it.output }.distinct().size == items.count { it.output != null })
        require(items.sumOf { it.bytes } <= MAX_BYTES && items.sumOf { it.outputBytes } <= MAX_BYTES)
        return BatchQueue(revision, options, items)
    }
}

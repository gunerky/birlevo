package com.example.contentstudio.batch

import com.example.contentstudio.media.*
import java.io.File
import java.util.Locale
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

data class BatchOptions(val format: ImageOutputFormat = ImageOutputFormat.JPEG,
    val quality: Int = 80, val edge: Int = 1920) {
    init { require(quality in 40..95 && edge in listOf(1280, 1920, 2560)) }
}
data class BatchImage(val id: String, val source: String, val uri: String, val name: String,
    val bytes: Long, val width: Int, val height: Int, val output: String? = null,
    val outputBytes: Long = 0, val issue: ImportIssue? = null, val included: Boolean = true) {
    val copyKey get() = output?.let { "$id/$it/$outputBytes" }
    val needsConsent get() = output != null && outputBytes >= bytes
}
data class BatchImages(val revision: String = UUID.randomUUID().toString(),
    val options: BatchOptions = BatchOptions(), val items: List<BatchImage> = emptyList()) {
    fun revised(items: List<BatchImage> = this.items, options: BatchOptions = this.options) =
        copy(revision = UUID.randomUUID().toString(), items = items, options = options)
}

/** Metadata only. Copy approvals are deliberately absent and never survive process death. */
object BatchImagePolicy {
    const val MAX_FILES = 20
    const val MAX_BYTES = 512L * 1024 * 1024
    fun owned(folder: File, name: String, prefix: String): File {
        require(name.startsWith(prefix) && name.length <= 160 &&
            name.matches(Regex("[a-zA-Z0-9.-]+")) && !name.contains(".."))
        return File(folder, name).also { require(it.canonicalFile.parentFile == folder.canonicalFile) }
    }
    fun requireExportable(batch: BatchImages, approved: Set<String>): List<BatchImage> {
        require(batch.items.isNotEmpty() && batch.items.all { it.output != null || it.issue != null })
        val selected = batch.items.filter { it.included && it.output != null }
        require(selected.isNotEmpty())
        require(selected.all { !it.needsConsent || it.copyKey in approved })
        require(selected.sumOf { it.outputBytes } <= MAX_BYTES)
        return selected
    }
    /** A flat archive, no user-supplied paths. Ordinals also prevent case-insensitive collisions. */
    fun entryName(index: Int, name: String, format: ImageOutputFormat): String {
        val base = name.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.')
            .map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '_' }
            .joinToString("").take(72).ifBlank { "image" }
        return String.format(Locale.ROOT, "%02d_%s.%s", index + 1, base, format.extension)
    }
    fun encode(batch: BatchImages): String = JSONObject().put("version", 1).put("revision", batch.revision)
        .put("format", batch.options.format.name).put("quality", batch.options.quality).put("edge", batch.options.edge)
        .put("items", JSONArray().also { array -> batch.items.forEach { item -> array.put(JSONObject()
            .put("id", item.id).put("source", item.source).put("uri", item.uri).put("name", item.name)
            .put("bytes", item.bytes).put("width", item.width).put("height", item.height)
            .put("included", item.included).apply {
                item.output?.let { put("output", it).put("outputBytes", item.outputBytes) }
                item.issue?.let { put("issue", it.name) }
            }) } }).toString()
    fun decode(text: String): BatchImages {
        require(text.toByteArray(Charsets.UTF_8).size <= AtomicMediaSession.MAX_CHECKPOINT_BYTES)
        val root = JSONObject(text); require(root.getInt("version") == 1)
        val revision = root.getString("revision"); UUID.fromString(revision)
        val options = BatchOptions(ImageOutputFormat.valueOf(root.getString("format")), root.getInt("quality"), root.getInt("edge"))
        val values = root.getJSONArray("items"); require(values.length() <= MAX_FILES)
        val items = (0 until values.length()).map { index -> values.getJSONObject(index).let { value ->
            BatchImage(value.getString("id"), value.getString("source"), value.getString("uri"),
                value.getString("name"), value.getLong("bytes"), value.getInt("width"), value.getInt("height"),
                if (value.has("output")) value.getString("output") else null, value.optLong("outputBytes"),
                if (value.has("issue")) ImportIssue.valueOf(value.getString("issue")) else null,
                value.getBoolean("included")).also { item ->
                UUID.fromString(item.id)
                owned(File("batch"), item.source, "source-")
                require(item.uri.length <= 8192 && item.name.length in 1..256)
                require(item.bytes in 1..FormatSupport.MAX_IMAGE_BYTES && item.width > 0 && item.height > 0 &&
                    item.width.toLong() * item.height <= FormatSupport.MAX_IMAGE_PIXELS)
                if (item.output != null) {
                    owned(File("batch"), item.output, "output-")
                    require(item.output.endsWith(".${options.format.extension}") && item.outputBytes in 1..FormatSupport.MAX_IMAGE_BYTES && item.issue == null)
                } else require(item.outputBytes == 0L)
            }
        } }
        require(items.map { it.id }.distinct().size == items.size && items.map { it.source }.distinct().size == items.size)
        require(items.mapNotNull { it.output }.distinct().size == items.count { it.output != null })
        require(items.sumOf { it.bytes } <= MAX_BYTES && items.sumOf { it.outputBytes } <= MAX_BYTES)
        return BatchImages(revision, options, items)
    }
}

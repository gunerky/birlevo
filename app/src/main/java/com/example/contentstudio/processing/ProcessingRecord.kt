package com.example.contentstudio.processing

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

enum class ProcessingKind(val folder: String) { VIDEO("video-compression"), PHOTO_PDF("photo-pdf"), AUDIO("audio-conversion"), SLIDESHOW("slideshow-video"), PRESENTATION_PPTX("presentation-pptx"), BATCH_IMAGE("batch-images"), BATCH_VIDEO("batch-video"), BATCH_AUDIO("batch-audio"), BATCH_DOCUMENT("batch-documents") }
enum class ProcessingStatus { QUEUED, RUNNING, SUCCEEDED, FAILED, CANCELLED, INTERRUPTED;
    val active get() = this == QUEUED || this == RUNNING
}
data class ProcessingOutput(val name: String, val bytes: Long, val pages: Int = 0, val elapsedMs: Long = 0) {
    fun resolve(folder: File): File {
        require(name == File(name).name && name.startsWith("output-") && !name.contains('/') && !name.contains('\\'))
        return File(folder, name).also { require(it.canonicalFile.parentFile == folder.canonicalFile && it.isFile && bytes > 0 && it.length() == bytes) }
    }
}
data class ProcessingRecord(val id: String, val kind: ProcessingKind, val request: String,
    val status: ProcessingStatus, val progress: Int? = null, val output: ProcessingOutput? = null)

object ProcessingCodec {
    fun fingerprint(request: String?): String? = request?.let {
        java.security.MessageDigest.getInstance("SHA-256").digest(it.toByteArray(Charsets.UTF_8)).joinToString("") { byte -> "%02x".format(byte) }
    }
    fun encode(records: Collection<ProcessingRecord>): String = JSONObject().put("version", 1)
        .put("jobs", JSONArray().also { jobs -> records.forEach { value ->
            jobs.put(JSONObject().put("id", value.id).put("kind", value.kind.name).put("request", value.request)
                .put("status", value.status.name).apply { value.output?.let {
                    put("output", JSONObject().put("name", it.name).put("bytes", it.bytes).put("pages", it.pages).put("elapsed", it.elapsedMs))
                } })
        } }).toString()
    fun decode(text: String): Map<ProcessingKind, ProcessingRecord> {
        require(text.toByteArray(Charsets.UTF_8).size <= 512 * 1024)
        val root = JSONObject(text); require(root.getInt("version") == 1)
        val jobs = root.getJSONArray("jobs"); require(jobs.length() <= ProcessingKind.entries.size)
        val records = (0 until jobs.length()).map { index -> jobs.getJSONObject(index).let {
            val id = it.getString("id"); require(id.length in 1..128)
            val request = it.getString("request"); require(request.matches(Regex("[a-f0-9]{64}")))
            val status = ProcessingStatus.valueOf(it.getString("status"))
            val output = it.optJSONObject("output")?.let { item -> ProcessingOutput(item.getString("name"),
                item.getLong("bytes"), item.getInt("pages"), item.getLong("elapsed")).also { value ->
                require(value.bytes > 0 && value.pages in 0..100 && value.elapsedMs >= 0)
                require(value.name.startsWith("output-") && value.name == File(value.name).name && !value.name.contains('/') && !value.name.contains('\\'))
            } }
            require((status == ProcessingStatus.SUCCEEDED) == (output != null))
            ProcessingRecord(id, ProcessingKind.valueOf(it.getString("kind")), request,
                if (status.active) ProcessingStatus.INTERRUPTED else status, output = output)
        } }
        require(records.map { it.kind }.distinct().size == records.size)
        return records.associateBy { it.kind }
    }
}

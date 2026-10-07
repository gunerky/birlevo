package com.example.contentstudio.media

import android.util.AtomicFile
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

enum class SessionSaveStatus { PENDING, SAVING, SAVED, ERROR }
data class SessionSnapshot(val json: String?, val files: Set<File> = emptySet())
interface MediaSessionStorage { fun read(): String?; fun write(json: String?) }

/** Only metadata is stored. A null tombstone is atomic too; reset cannot resurrect old inputs. */
class AtomicMediaSession(folder: File) : MediaSessionStorage {
    private val storage = AtomicFile(File(folder, "session.json"))
    override fun read(): String? {
        if (!storage.baseFile.exists() && !File(storage.baseFile.path + ".bak").exists()) return null
        val text = storage.openRead().use { input ->
            val bytes = input.readBytesLimited()
            bytes.toString(Charsets.UTF_8)
        }
        return text.takeUnless { it == "null" }
    }
    override fun write(json: String?) {
        val bytes = (json ?: "null").toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_CHECKPOINT_BYTES)
        check(storage.baseFile.parentFile!!.let { it.isDirectory || it.mkdirs() })
        val output = storage.startWrite()
        try { output.write(bytes); storage.finishWrite(output) }
        catch (failure: Throwable) { storage.failWrite(output); throw failure }
    }
    private fun java.io.InputStream.readBytesLimited(): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            require(output.size() + count <= MAX_CHECKPOINT_BYTES)
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
    companion object { const val MAX_CHECKPOINT_BYTES = 512 * 1024 }
}

/** Main-thread revisions, one atomic IO writer. Retired sources survive failed saves. */
class MediaSessionWriter(scope: CoroutineScope, private val storage: MediaSessionStorage) {
    private val requests = Channel<Unit>(Channel.CONFLATED)
    private val lock = Mutex()
    private var latest = SessionSnapshot(null)
    private val retired = mutableSetOf<File>()
    private var revision = 0L
    private var persisted = 0L
    private val mutable = MutableStateFlow(SessionSaveStatus.SAVED)
    val status = mutable.asStateFlow()
    init { scope.launch { for (ignored in requests) {
        while (withTimeoutOrNull(150L) { requests.receive(); true } == true) Unit
        flush()
    } } }
    fun initialize(snapshot: SessionSnapshot) { latest = snapshot; persisted = revision }
    fun update(snapshot: SessionSnapshot, retire: Set<File> = emptySet()) {
        latest = snapshot; retired += retire; revision++
        mutable.value = SessionSaveStatus.PENDING
        requests.trySend(Unit)
    }
    fun retry() { requests.trySend(Unit) }
    suspend fun read(): String? = withContext(Dispatchers.IO) { storage.read() }
    suspend fun flush(): Boolean = lock.withLock {
        if (persisted == revision) return@withLock true
        val snapshot = latest
        val version = revision
        mutable.value = SessionSaveStatus.SAVING
        try {
            // A lifecycle cancellation cannot interrupt an atomic commit / its safe retirement.
            withContext(NonCancellable + Dispatchers.IO) { storage.write(snapshot.json) }
            persisted = version
            val remove = retired - snapshot.files - latest.files
            withContext(NonCancellable + Dispatchers.IO) { remove.forEach { it.delete() } }
            retired.removeAll(remove)
            mutable.value = if (revision == version) SessionSaveStatus.SAVED else SessionSaveStatus.PENDING
            true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { mutable.value = SessionSaveStatus.ERROR; false }
    }
}

data class SessionAsset(val id: String, val file: String, val uri: String, val name: String, val bytes: Long) {
    fun resolve(folder: File, prefix: String, maxBytes: Long): File {
        require(file == File(file).name && file.startsWith(prefix) && !file.contains('/') && !file.contains('\\'))
        require(id.isNotBlank() && id.length <= 128 && name.length <= 256 && uri.length <= 8192)
        require(bytes in 1..maxBytes)
        val source = File(folder, file)
        require(source.canonicalFile.parentFile == folder.canonicalFile && source.isFile && source.length() == bytes)
        return source
    }
}
data class VideoSession(val asset: SessionAsset, val quality: VideoQuality, val edge: Int, val trim: VideoTrim,
    val format: VideoOutputFormat = VideoOutputFormat.MP4_AVC,
    val clips: List<SavedVideoClip> = listOf(SavedVideoClip(asset, trim)),
    val canvas: VideoCanvas = VideoCanvas.FIRST_CLIP, val fit: VideoFit = VideoFit.FIT, val speed: Float = 1f, val textLayer: VideoTextLayer? = null)
data class SavedVideoClip(val asset: SessionAsset, val trim: VideoTrim)
data class PhotoSession(val assets: List<SessionAsset>, val edge: Int)

/** Versioned, bounded references. No bitmaps, media bytes, output files or copy approvals. */
object MediaSessionCodec {
    private fun asset(value: SessionAsset) = JSONObject().put("id", value.id).put("file", value.file)
        .put("uri", value.uri).put("name", value.name.take(256)).put("bytes", value.bytes)
    private fun asset(value: JSONObject) = SessionAsset(value.getString("id"), value.getString("file"),
        value.getString("uri"), value.getString("name"), value.getLong("bytes"))
    private fun base(kind: String) = JSONObject().put("version", 1).put("kind", kind)
    private fun read(text: String, kind: String): JSONObject {
        require(text.toByteArray(Charsets.UTF_8).size <= AtomicMediaSession.MAX_CHECKPOINT_BYTES)
        return JSONObject(text).also { require(it.getInt("version") == 1 && it.getString("kind") == kind) }
    }
    fun video(value: VideoSession): String {
        require(value.clips.size in 1..2 && value.clips.first() == SavedVideoClip(value.asset, value.trim))
        require(value.clips.map { it.asset.file }.distinct().size == value.clips.size)
        require(value.speed in videoSpeeds)
        val layered = value.textLayer != null
        val transformed = value.canvas != VideoCanvas.FIRST_CLIP || value.fit != VideoFit.FIT || value.speed != 1f
        if (value.clips.size == 1 && !transformed && !layered) return base("video").put("source", asset(value.asset))
            .put("quality", value.quality.name).put("edge", value.edge).put("start", value.trim.startMs).put("end", value.trim.endMs)
            // Keep legacy request bytes exactly stable for old completed jobs.
            .also { if (value.format != VideoOutputFormat.MP4_AVC) it.put("format", value.format.name) }.toString()
        return base("video").put("version", if (layered) 4 else if (transformed) 3 else 2).put("quality", value.quality.name).put("edge", value.edge)
            .put("format", value.format.name).put("clips", JSONArray().also { array -> value.clips.forEach { clip ->
                array.put(JSONObject().put("source", asset(clip.asset)).put("start", clip.trim.startMs).put("end", clip.trim.endMs))
            } }).also { if (transformed || layered) it.put("canvas", value.canvas.name).put("fit", value.fit.name).put("speed", value.speed)
                value.textLayer?.let { layer -> it.put("textLayer", JSONObject().put("text", layer.text)
                    .put("position", layer.position.name).put("size", layer.size).put("color", layer.color.name)
                    .put("start", layer.startMs).also { json -> layer.endMs?.let { json.put("end", it) } }) } }.toString()
    }
    fun video(text: String): VideoSession {
        require(text.toByteArray(Charsets.UTF_8).size <= AtomicMediaSession.MAX_CHECKPOINT_BYTES)
        val data = JSONObject(text)
        require(data.getString("kind") == "video" && data.getInt("version") in 1..4)
        require(data.getInt("version") == 1 || data.has("clips"))
        val edge = data.getInt("edge"); require(edge in listOf(640, 1280, 1920))
        val clips = if (data.getInt("version") == 1) listOf(SavedVideoClip(asset(data.getJSONObject("source")),
            VideoTrim(data.getLong("start"), data.getLong("end")))) else {
            val entries = data.getJSONArray("clips"); require(if (data.getInt("version") == 2) entries.length() == 2 else entries.length() in 1..2)
            (0 until entries.length()).map { index -> entries.getJSONObject(index).let {
                SavedVideoClip(asset(it.getJSONObject("source")), VideoTrim(it.getLong("start"), it.getLong("end")))
            } }
        }
        require(clips.map { it.asset.file }.distinct().size == clips.size && clips.sumOf { it.trim.durationMs } <= FormatSupport.MAX_VIDEO_DURATION_MS * 2)
        val canvas = if (data.getInt("version") >= 3) VideoCanvas.valueOf(data.getString("canvas")) else VideoCanvas.FIRST_CLIP
        val fit = if (data.getInt("version") >= 3) VideoFit.valueOf(data.getString("fit")) else VideoFit.FIT
        val speed = if (data.getInt("version") >= 3) data.getDouble("speed").toFloat().also { require(it in videoSpeeds) } else 1f
        return VideoSession(clips.first().asset, VideoQuality.valueOf(data.getString("quality")), edge, clips.first().trim,
            VideoOutputFormat.valueOf(data.optString("format", "MP4_AVC")), clips, canvas, fit, speed, if (data.getInt("version") == 4) data.getJSONObject("textLayer").let {
                VideoTextLayer(it.getString("text"), VideoTextPosition.valueOf(it.getString("position")),
                    it.getDouble("size").toFloat(), VideoTextColor.valueOf(it.getString("color")), it.getLong("start"),
                    if (it.has("end")) it.getLong("end") else null)
            } else null)
    }
    fun photos(value: PhotoSession): String = base("photos").put("edge", value.edge)
        .put("pages", JSONArray().also { list -> value.assets.forEach { list.put(asset(it)) } }).toString()
    fun photos(text: String): PhotoSession = read(text, "photos").let { data ->
        val edge = data.getInt("edge"); require(edge in listOf(2048, 4096))
        val entries = data.getJSONArray("pages"); require(entries.length() <= PhotoPdf.MAX_PAGES)
        val assets = (0 until entries.length()).map { asset(entries.getJSONObject(it)) }
        require(assets.map { it.id }.distinct().size == assets.size && assets.map { it.file }.distinct().size == assets.size)
        require(assets.all { it.bytes in 1..FormatSupport.MAX_IMAGE_BYTES } && assets.sumOf { it.bytes } <= PhotoPdf.MAX_BATCH_BYTES)
        PhotoSession(assets, edge)
    }
    fun asset(source: VideoSource) = SessionAsset("video", source.file.name, source.originalUri.toString(), source.name.take(256), source.bytes)
    fun asset(page: PhotoPage) = SessionAsset(page.id, page.file.name, page.sourceUri.toString(), page.name.take(256), page.bytes)
}

package com.example.contentstudio.audio

import com.example.contentstudio.media.AtomicMediaSession
import com.example.contentstudio.media.SessionAsset
import org.json.JSONObject
import org.json.JSONArray

data class SavedAudioClip(val asset: SessionAsset, val range: AudioRange = AudioRange())
data class AudioSession(val asset: SessionAsset, val target: AudioTarget, val bitrate: Int,
    val clips: List<SavedAudioClip> = listOf(SavedAudioClip(asset)), val rate: Int? = null, val channels: Int? = null)
object AudioSessionCodec {
    fun encode(value: AudioSession): String {
        validate(value)
        // Keep legacy, unedited request bytes stable for durable completed-job lookup.
        if (value.clips == listOf(SavedAudioClip(value.asset)) && value.rate == null && value.channels == null) return legacy(value)
        return JSONObject().put("version", 2).put("kind", "audio")
            .put("clips", JSONArray().apply { value.clips.forEach { clip -> put(JSONObject()
                .put("source", assetJson(clip.asset)).put("start", clip.range.startMs).put("end", clip.range.endMs ?: JSONObject.NULL)) } })
            .put("rate", value.rate ?: JSONObject.NULL).put("channels", value.channels ?: JSONObject.NULL)
            .put("target", value.target.name).put("bitrate", value.bitrate).toString()
    }
    private fun assetJson(asset: SessionAsset) = JSONObject().put("id", asset.id).put("file", asset.file).put("uri", asset.uri)
        .put("name", asset.name).put("bytes", asset.bytes)
    private fun readAsset(source: JSONObject) = SessionAsset(source.getString("id"), source.getString("file"), source.getString("uri"),
        source.getString("name"), source.getLong("bytes"))
    private fun validate(value: AudioSession) {
        require(value.bitrate in AudioPolicy.bitrates && value.clips.size in 1..2 && value.asset == value.clips.first().asset)
        require(value.clips.map { it.asset.file }.distinct().size == value.clips.size)
        require(value.rate == null || value.rate in 8_000..192_000)
        require(value.channels == null || value.channels in 1..2)
        value.clips.forEach { require(it.range.startMs in 0 until AudioPolicy.MAX_DURATION_MS)
            require(it.range.endMs == null || it.range.endMs in (it.range.startMs + 1)..AudioPolicy.MAX_DURATION_MS) }
    }
    private fun legacy(value: AudioSession): String = JSONObject().put("version", 1).put("kind", "audio")
        .put("source", JSONObject().put("id", value.asset.id).put("file", value.asset.file).put("uri", value.asset.uri)
            .put("name", value.asset.name).put("bytes", value.asset.bytes))
        .put("target", value.target.name).put("bitrate", value.bitrate).toString()
    fun decode(text: String): AudioSession {
        require(text.toByteArray(Charsets.UTF_8).size <= AtomicMediaSession.MAX_CHECKPOINT_BYTES)
        val root = JSONObject(text); require(root.getString("kind") == "audio")
        val bitrate = root.getInt("bitrate"); require(bitrate in AudioPolicy.bitrates)
        val target = AudioTarget.valueOf(root.getString("target"))
        return when (root.getInt("version")) {
            1 -> AudioSession(readAsset(root.getJSONObject("source")), target, bitrate)
            2 -> {
                require(root.has("rate") && root.has("channels"))
                val list = root.getJSONArray("clips"); require(list.length() in 1..2)
                val clips = (0 until list.length()).map { list.getJSONObject(it).let { clip ->
                    require(clip.has("end"))
                    SavedAudioClip(readAsset(clip.getJSONObject("source")), AudioRange(clip.getLong("start"),
                        if (clip.isNull("end")) null else clip.getLong("end"))) } }
                AudioSession(clips.first().asset, target, bitrate, clips,
                    if (root.isNull("rate")) null else root.getInt("rate"), if (root.isNull("channels")) null else root.getInt("channels"))
            }
            else -> throw IllegalArgumentException("Unknown audio session version")
        }.also(::validate)
    }
    fun asset(source: AudioSource) = SessionAsset("audio", source.file.name, source.originalUri.toString(), source.name, source.bytes)
}

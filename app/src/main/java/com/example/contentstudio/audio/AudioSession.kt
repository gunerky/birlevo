package com.example.contentstudio.audio

import com.example.contentstudio.media.AtomicMediaSession
import com.example.contentstudio.media.SessionAsset
import org.json.JSONObject

data class AudioSession(val asset: SessionAsset, val target: AudioTarget, val bitrate: Int)
object AudioSessionCodec {
    fun encode(value: AudioSession): String = JSONObject().put("version", 1).put("kind", "audio")
        .put("source", JSONObject().put("id", value.asset.id).put("file", value.asset.file).put("uri", value.asset.uri)
            .put("name", value.asset.name).put("bytes", value.asset.bytes))
        .put("target", value.target.name).put("bitrate", value.bitrate).toString()
    fun decode(text: String): AudioSession {
        require(text.toByteArray(Charsets.UTF_8).size <= AtomicMediaSession.MAX_CHECKPOINT_BYTES)
        val root = JSONObject(text); require(root.getInt("version") == 1 && root.getString("kind") == "audio")
        val source = root.getJSONObject("source")
        val bitrate = root.getInt("bitrate"); require(bitrate in AudioPolicy.bitrates)
        return AudioSession(SessionAsset(source.getString("id"), source.getString("file"), source.getString("uri"),
            source.getString("name"), source.getLong("bytes")), AudioTarget.valueOf(root.getString("target")), bitrate)
    }
    fun asset(source: AudioSource) = SessionAsset("audio", source.file.name, source.originalUri.toString(), source.name, source.bytes)
}

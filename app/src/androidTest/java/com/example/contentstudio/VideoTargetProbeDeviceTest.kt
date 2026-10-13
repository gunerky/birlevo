package com.example.contentstudio

import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.example.contentstudio.media.*
import com.example.contentstudio.audio.AudioEngine
import com.example.contentstudio.audio.AudioTarget
import com.example.contentstudio.probes.VideoTargetProbe
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/** Encoder inventory is evidence, not an assertion that a conversion works. Real files are checked below. */
@androidx.annotation.OptIn(UnstableApi::class)
class VideoTargetProbeDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val qa get() = File(context.getExternalFilesDir("qa"), "video-targets").apply { mkdirs() }
    private fun uri(name: String) = Uri.parse("content://com.example.contentstudio.test.videos/$name")
    private fun encoders(mime: String) = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
        .filter { it.isEncoder && mime in it.supportedTypes }.map { it.name }

    @Test fun inventoryRecordsEncodingAndDecodingSeparately() {
        val records = JSONArray()
        for (mime in listOf("video/avc", "video/hevc", "video/x-vnd.on2.vp8", "video/x-vnd.on2.vp9", "video/av01", "audio/opus")) {
            val codecs = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { mime in it.supportedTypes }
            records.put(JSONObject().put("mime", mime).put("encoders", JSONArray(codecs.filter { it.isEncoder }.map { it.name }))
                .put("decoders", JSONArray(codecs.filter { !it.isEncoder }.map { it.name })))
        }
        File(qa, "inventory.json").writeText(JSONObject().put("api", Build.VERSION.SDK_INT)
            .put("device", Build.MODEL).put("codecs", records).toString(2))
        assertTrue(encoders("video/avc").isNotEmpty())
    }

    @Test fun webmVp8OpusProducesPlayableTrimmedFiles() = matrix(VideoTargetProbe.Target.WEBM,
        listOf("audio.mp4", "silent.mp4", "portrait.mp4", "hevc.mp4", "phone.webm", "phone-vorbis.webm"))

    @Test fun hevcMp4RequiresAnEncoderAndProducesPlayableFiles() = matrix(VideoTargetProbe.Target.HEVC,
        listOf("audio.mp4", "silent.mp4", "portrait.mp4"))

    @Test fun smallAndHighChoicesKeepRequestedDimensionsAndCodecs() {
        for (target in VideoTargetProbe.Target.entries) {
            matrix(target, listOf("audio.mp4"), VideoQuality.SMALL, 640)
            matrix(target, listOf("audio.mp4"), VideoQuality.HIGH, 1280)
        }
    }

    @Test fun cancellationDeletesPartialFilesAndKeepsSources() = runBlocking<Unit> {
        val engine = VideoCompressor(context)
        val source = engine.importVideo(uri("cancel.mp4"))
        val original = source.file.readBytes()
        val folder = File(context.noBackupFilesDir, "video-compression")
        try {
            for (target in VideoTargetProbe.Target.entries) {
                if (encoders(target.videoMime).isEmpty()) continue
                val before = folder.listFiles()?.map { it.name }?.toSet().orEmpty()
                val started = CompletableDeferred<Unit>()
                val operation = launch(Dispatchers.IO) {
                    VideoTargetProbe.encode(context, source, VideoTrim(0, source.info.durationMs), target,
                        onStarted = { started.complete(Unit) }).delete()
                }
                withTimeout(15_000) { started.await() }
                operation.cancelAndJoin()
                assertEquals(before, folder.listFiles()?.map { it.name }?.toSet().orEmpty())
                assertArrayEquals(original, source.file.readBytes())
            }
        } finally { source.file.delete(); source.info.poster?.recycle() }
    }

    private fun matrix(target: VideoTargetProbe.Target, names: List<String>,
        quality: VideoQuality = VideoQuality.BALANCED, edge: Int = 640) = runBlocking<Unit> {
        val report = JSONObject().put("target", target.name).put("api", Build.VERSION.SDK_INT)
            .put("encoders", JSONArray(encoders(target.videoMime))).put("productionSupport", true)
            .put("quality", quality.name).put("maxEdge", edge)
        val rows = JSONArray(); report.put("cases", rows)
        try {
            val available = encoders(target.videoMime).isNotEmpty() &&
                (target != VideoTargetProbe.Target.WEBM || (Build.VERSION.SDK_INT >= 29 && encoders(target.audioMime).isNotEmpty()))
            report.put("status", if (available) "running" else "unavailable_encoder")
            assumeTrue("No required encoder: ${target.name}; recorded separately, not conversion success", available)
            for (name in names) {
                val row = JSONObject().put("source", name); rows.put(row)
                val engine = VideoCompressor(context)
                val original = context.contentResolver.openInputStream(uri(name))!!.use { it.readBytes() }
                val source = engine.importVideo(uri(name))
                var output: File? = null
                var validated: VideoSource? = null
                try {
                    val trim = VideoTrim(250, minOf(1750, source.info.durationMs))
                    val start = android.os.SystemClock.elapsedRealtime()
                    output = VideoTargetProbe.encode(context, source, trim, target, quality, edge)
                    val generated = output
                    val mimes = trackMimes(generated)
                    assertEquals(1, mimes.count { it == target.videoMime })
                    assertEquals(if (source.info.hasAudio) 1 else 0, mimes.count { it == target.audioMime })
                    assertEquals(if (source.info.hasAudio) 2 else 1, mimes.size)
                    // Re-open through the actual app importer: container, decoder, SDR, dimensions and limits.
                    validated = engine.importVideo(Uri.fromFile(generated))
                    val expected = videoTarget(source.info.width, source.info.height, edge, quality)
                    assertEquals(expected.width, validated.info.width); assertEquals(expected.height, validated.info.height)
                    assertTrue("Trim duration drift", abs(validated.info.durationMs - trim.durationMs) <= 300)
                    play(generated)
                    if (name == "audio.mp4") {
                        val audio = AudioEngine(context)
                        val extracted = audio.importAudio(Uri.fromFile(generated))
                        try {
                            assertTrue(extracted.info.fromVideo)
                            assertArrayEquals(generated.readBytes(), extracted.file.readBytes())
                            for (audioTarget in AudioTarget.entries) {
                                val converted = audio.convert(extracted, audioTarget, 128_000) {}
                                try {
                                    assertEquals(extracted.info.channels, converted.info.channels)
                                    assertEquals(extracted.info.sampleRate, converted.info.sampleRate)
                                    assertTrue(abs(converted.info.durationMs - trim.durationMs) <= 300)
                                } finally { converted.file.delete() }
                            }
                            row.put("audioHandoff", "WAV_M4A_passed").put("audioSampleRate", extracted.info.sampleRate)
                        } finally { extracted.file.delete() }
                    }
                    val result = CompressedVideo(generated, generated.length(), validated.info, 0)
                    // A deliberately equal source size exercises the production compression guard for this codec.
                    assertThrows(IllegalArgumentException::class.java) {
                        runBlocking { engine.export(source.copy(bytes = result.bytes), result, uri("export.mp4")) }
                    }
                    engine.export(source, result, uri("export.mp4"), allowNonSmallerCopy = true)
                    assertArrayEquals(generated.readBytes(), context.contentResolver.openInputStream(uri("export.mp4"))!!.use { it.readBytes() })
                    assertArrayEquals(original, source.file.readBytes())
                    assertArrayEquals(original, context.contentResolver.openInputStream(uri(name))!!.use { it.readBytes() })
                    row.put("status", "passed").put("bytes", generated.length()).put("sourceBytes", source.bytes)
                        .put("width", validated.info.width).put("height", validated.info.height)
                        .put("durationMs", validated.info.durationMs).put("mimes", JSONArray(mimes))
                        .put("elapsedMs", android.os.SystemClock.elapsedRealtime() - start)
                } catch (error: Throwable) {
                    row.put("status", "failed").put("error", error.javaClass.simpleName)
                    report.put("status", "failed"); throw error
                } finally {
                    context.contentResolver.delete(uri("export.mp4"), null, null)
                    output?.delete(); validated?.file?.delete(); validated?.info?.poster?.recycle()
                    source.file.delete(); source.info.poster?.recycle()
                }
            }
            report.put("status", "passed")
        } finally { File(qa, "${target.name.lowercase()}-${quality.name.lowercase()}-$edge.json").writeText(report.toString(2)) }
    }

    private fun trackMimes(file: File): List<String?> = MediaExtractor().let { extractor ->
        try { extractor.setDataSource(file.path)
            (0 until extractor.trackCount).map { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME) }
        } finally { extractor.release() }
    }

    private suspend fun play(file: File) {
        var player: ExoPlayer? = null
        var firstFrame = false
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            scenario.onActivity { activity ->
                val active = ExoPlayer.Builder(activity).build(); player = active
                active.addListener(object : Player.Listener {
                    override fun onRenderedFirstFrame() { firstFrame = true }
                })
                activity.setContentView(PlayerView(activity).apply { this.player = active })
                active.setMediaItem(MediaItem.fromUri(Uri.fromFile(file))); active.prepare(); active.play()
            }
            withTimeout(15_000) {
                while (!withContext(Dispatchers.Main.immediate) {
                    player?.playerError?.let { throw it }
                    firstFrame && player?.playbackState == Player.STATE_ENDED
                }) delay(50)
            }
        } finally {
            instrumentation.runOnMainSync { player?.release() }; scenario.close()
        }
    }
}

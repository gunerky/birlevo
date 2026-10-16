package com.example.contentstudio

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaCodec
import android.media.MediaMuxer
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.muxer.BufferInfo
import androidx.media3.muxer.Muxer
import androidx.media3.muxer.MuxerException
import androidx.test.platform.app.InstrumentationRegistry
import com.example.contentstudio.audio.AudioEngine
import com.example.contentstudio.audio.AudioTarget
import com.example.contentstudio.audio.OggAudioMuxer
import com.example.contentstudio.media.WebmMuxer
import com.example.contentstudio.media.VideoCompressor
import com.example.contentstudio.media.VideoEdit
import com.example.contentstudio.media.VideoClip
import com.example.contentstudio.media.VideoTrim
import com.example.contentstudio.media.VideoQuality
import com.example.contentstudio.media.VideoOutputFormat
import java.io.File
import java.nio.ByteBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Real encoded packets exercise nonzero buffer positions at the Media3/platform boundary. */
@androidx.annotation.OptIn(UnstableApi::class)
class Media3MuxerDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val evidence get() = File(context.getExternalFilesDir(null), "qa/media3-migration").apply { mkdirs() }
    private data class Packet(val bytes: ByteArray, val timeUs: Long, val flags: Int)

    @Test fun webmPreservesEncodedPacketsTimestampsAndKeyframesAtNonzeroOffsets() {
        roundTrip(Uri.parse("content://com.example.contentstudio.test.videos/phone.webm"),
            "video/", WebmMuxer.Factory())
    }

    @Test fun oggMatchesPlatformPacketsAndTimingAndRejectsInvalidSamples() = runBlocking<Unit>(Dispatchers.IO) {
        val engine = AudioEngine(context)
        val source = engine.importAudio(Uri.parse("content://com.example.contentstudio.test.audio/mono44100.wav"))
        try {
            val result = engine.convert(source, AudioTarget.OGG, 64_000) {}
            try { roundTrip(Uri.fromFile(result.file), "audio/", OggAudioMuxer.Factory()) }
            finally { result.file.delete() }
        } finally { source.file.delete() }
    }

    @Test fun aacEncodingStillUsesRequestedBitrateAndProfile() = runBlocking<Unit>(Dispatchers.IO) {
        val engine = AudioEngine(context)
        val source = engine.importAudio(Uri.parse("content://com.example.contentstudio.test.audio/mono44100.wav"))
        try {
            val small = engine.convert(source, AudioTarget.M4A, 64_000) {}
            try {
                val high = engine.convert(source, AudioTarget.M4A, 256_000) {}
                try {
                    assertEquals(source.info.sampleRate, high.info.sampleRate)
                    assertEquals(source.info.channels, high.info.channels)
                    assertTrue(high.bytes > small.bytes)
                    high.file.copyTo(File(evidence, "requested-aac.m4a"), true)
                } finally { high.file.delete() }
            } finally { small.file.delete() }
        } finally { source.file.delete() }
    }

    @Test fun joinedSpeedAdjustedAvcStillEncodesItsSoundOnLegacyAndroid() = runBlocking<Unit>(Dispatchers.IO) {
        val engine = VideoCompressor(context)
        // The API 24 emulator advertises AVC Baseline, not the newer fixtures' High profile.
        val legacy = android.os.Build.VERSION.SDK_INT < 26
        val sourceName = if(legacy) "audio-baseline.mp4" else "audio.mp4"
        val secondName = if(legacy) "audio-baseline.mp4" else "edit-second.mp4"
        val source = engine.importVideo(Uri.parse("content://com.example.contentstudio.test.videos/$sourceName"))
        val second = engine.importVideo(Uri.parse("content://com.example.contentstudio.test.videos/$secondName"))
        val original = source.file.readBytes()
        try {
            val edit = VideoEdit(listOf(VideoClip(source, VideoTrim(0, 1000)),
                VideoClip(second, VideoTrim(1000, 2000))), speed = if(legacy) .5f else 2f)
            val result = engine.compress(edit, VideoQuality.BALANCED, 640, VideoOutputFormat.MP4_AVC) {}
            try {
                engine.validateEditOutput(edit, result.info, VideoQuality.BALANCED, 640, VideoOutputFormat.MP4_AVC)
                assertTrue(result.info.hasAudio)
                assertArrayEquals(original, source.file.readBytes())
                result.file.copyTo(File(evidence, "joined-avc.mp4"), true)
            } finally { result.file.delete() }
        } finally { source.file.delete(); source.info.poster?.recycle(); second.file.delete(); second.info.poster?.recycle() }
    }

    private fun roundTrip(uri: Uri, type: String, factory: Muxer.Factory) {
        val output = File.createTempFile("muxer-migration-", ".bin", context.cacheDir)
        val input = MediaExtractor()
        try {
            input.setDataSource(context, uri, null)
            val index = (0 until input.trackCount).first { input.getTrackFormat(it).getString(MediaFormat.KEY_MIME)!!.startsWith(type) }
            val media = input.getTrackFormat(index)
            val mime = media.getString(MediaFormat.KEY_MIME)!!
            val format = Format.Builder().setSampleMimeType(mime).apply {
                if (type == "video/") { setWidth(media.getInteger(MediaFormat.KEY_WIDTH)); setHeight(media.getInteger(MediaFormat.KEY_HEIGHT)) }
                else { setSampleRate(media.getInteger(MediaFormat.KEY_SAMPLE_RATE)); setChannelCount(media.getInteger(MediaFormat.KEY_CHANNEL_COUNT)) }
                setInitializationData((0..2).mapNotNull { csd -> media.getByteBuffer("csd-$csd")?.duplicate()?.let { b -> ByteArray(b.remaining()).also(b::get) } })
            }.build()
            input.selectTrack(index)
            val before = mutableListOf<Packet>()
            val data = ByteBuffer.allocateDirect(2 * 1024 * 1024)
            factory.create(output.path).use { muxer ->
                val track = muxer.addTrack(format)
                // Rejection must occur before the platform starts writing a valid track.
                rejects { muxer.writeSampleData(track + 999, data, BufferInfo(0, 0, 0)) }
                rejects { muxer.writeSampleData(track, data, BufferInfo(-1, 0, 0)) }
                rejects { muxer.writeSampleData(track, data, BufferInfo(0, data.capacity() + 1, 0)) }
                while (true) {
                    data.clear()
                    val size = input.readSampleData(data, 13)
                    if (size < 0) break
                    data.position(13); data.limit(13 + size)
                    val flags = if (input.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) C.BUFFER_FLAG_KEY_FRAME else 0
                    val bytes = ByteArray(size).also { data.duplicate().get(it) }
                    before += Packet(bytes, input.sampleTime, flags)
                    muxer.writeSampleData(track, data, BufferInfo(input.sampleTime, size, flags))
                    input.advance()
                }
            }
            assertTrue(before.isNotEmpty())
            val platformTimes = if(type == "audio/") platformOggTimes(format, before) else null
            output.copyTo(File(evidence, if(type == "video/") "offset.webm" else "offset.ogg"),true)
            val decoded = MediaExtractor()
            try {
                decoded.setDataSource(output.path); decoded.selectTrack(0)
                for ((index,packet) in before.withIndex()) {
                    data.clear()
                    val size = decoded.readSampleData(data, 0)
                    assertEquals(packet.bytes.size, size)
                    data.position(0); data.limit(size)
                    assertArrayEquals(packet.bytes, ByteArray(size).also(data::get))
                    // Android reconstructs Ogg granule timestamps. Compare against a direct
                    // platform reference fed the same packets, not extractor-input timestamps.
                    val expectedTime = platformTimes?.get(index) ?: packet.timeUs
                    assertTrue("Packet time $expectedTime became ${decoded.sampleTime}",
                        kotlin.math.abs(expectedTime - decoded.sampleTime) <= 1000)
                    assertEquals(packet.flags != 0, decoded.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0)
                    decoded.advance()
                }
                data.clear()
                assertEquals(-1, decoded.readSampleData(data, 0))
            } finally { decoded.release() }
        } finally { input.release(); output.delete() }
    }

    private fun platformOggTimes(format: Format, packets: List<Packet>): List<Long> {
        val reference = File.createTempFile("ogg-reference-", ".ogg", context.cacheDir)
        try {
            val native = MediaMuxer(reference.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG)
            try {
                val media = MediaFormat.createAudioFormat(format.sampleMimeType!!,format.sampleRate,format.channelCount)
                format.initializationData.forEachIndexed { index, bytes -> media.setByteBuffer("csd-$index",ByteBuffer.wrap(bytes)) }
                val track = native.addTrack(media)
                native.start()
                for (packet in packets) {
                    val info = MediaCodec.BufferInfo().apply { set(0,packet.bytes.size,packet.timeUs,
                        if(packet.flags != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0) }
                    native.writeSampleData(track,ByteBuffer.wrap(packet.bytes),info)
                }
                native.stop()
            } finally { native.release() }
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(reference.path); extractor.selectTrack(0)
                val times = mutableListOf<Long>()
                while(extractor.sampleTime >= 0) { times += extractor.sampleTime; extractor.advance() }
                assertEquals(packets.size,times.size)
                return times
            } finally { extractor.release() }
        } finally { reference.delete() }
    }

    private fun rejects(write: () -> Unit) {
        try { write(); fail("Invalid sample accepted") } catch (_: MuxerException) { }
    }
}

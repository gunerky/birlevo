package com.example.contentstudio.audio

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.example.contentstudio.media.MediaOutputs
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipInputStream

class Mp3DeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val engine get() = AudioEngine(context)
    private fun uri(name: String) = Uri.parse("content://com.example.contentstudio.test.audio/$name")
    @Test fun monoAndStereoApplyEveryRealCbrRateAndDecodeToNonSilentPcm() = runBlocking<Unit>(Dispatchers.IO) {
        assertEquals("4.0", LameEncoder.version())
        for (name in listOf("tone.wav", "mono44100.wav", "tone.mp3")) {
            val source = engine.importAudio(uri(name)); val sourceBytes = source.file.readBytes()
            var previous = 0L
            try {
                for (bitrate in AudioPolicy.bitrates) {
                    val result = engine.convert(source, AudioTarget.MP3, bitrate) { }
                    try {
                        val stream = Mp3Frames.inspect(result.file)
                        assertEquals(bitrate, stream.bitrate); assertEquals(source.info.sampleRate, stream.rate)
                        assertEquals(source.info.channels, stream.channels); assertTrue(result.bytes > previous)
                        previous = result.bytes
                        val decoded = engine.convert(AudioSource(result.file, Uri.fromFile(result.file), "test.mp3", result.bytes,
                            result.info), AudioTarget.WAV, 128000) { }
                        try { assertTrue(decoded.file.readBytes().drop(44).any { it != 0.toByte() }) }
                        finally { decoded.file.delete() }
                        val report = File(context.getExternalFilesDir("qa"), "mp3").apply { mkdirs() }
                        result.file.copyTo(File(report, "$name-$bitrate.mp3"), true)
                    } finally { result.file.delete() }
                }
                assertArrayEquals(sourceBytes, source.file.readBytes())
            } finally { source.file.delete() }
        }
    }
    @Test fun allMpegRatesArePreservedAndUnsupportedCombinationsCreateNoOutput() = runBlocking<Unit>(Dispatchers.IO) {
        val seed = engine.importAudio(uri("tone.wav"))
        try {
            for (rate in listOf(8000, 11025, 12000, 16000, 22050, 24000, 32000, 44100, 48000)) {
                val file = File.createTempFile("source-", ".media", seed.file.parentFile)
                val pcm = ByteBuffer.allocate(rate * 2).order(ByteOrder.LITTLE_ENDIAN)
                repeat(rate) { pcm.putShort((kotlin.math.sin(it * 2 * Math.PI * 440 / rate) * 12000).toInt().toShort()) }
                file.writeBytes(AudioPolicy.wavHeader(rate, 1, pcm.capacity().toLong()) + pcm.array())
                val source = AudioSource(file, Uri.fromFile(file), "synthetic.wav", file.length(), engine.inspect(file))
                try {
                    for (bitrate in AudioPolicy.bitrates) {
                        if (Mp3Policy.supports(rate, 1, bitrate)) {
                            assertNull("$rate/$bitrate", AudioOutputSupport().issue(source.info, AudioTarget.MP3, bitrate))
                            val result = engine.convert(source, AudioTarget.MP3, bitrate) { }
                            try { assertEquals(rate, result.info.sampleRate); assertEquals(bitrate, Mp3Frames.inspect(result.file).bitrate) }
                            finally { result.file.delete() }
                        } else {
                            assertEquals(AudioOutputIssue.ENCODER, AudioOutputSupport().issue(source.info, AudioTarget.MP3, bitrate))
                            val before = file.parentFile!!.listFiles()!!.map { it.name }.toSet()
                            assertThrows(IllegalArgumentException::class.java) { runBlocking { engine.convert(source, AudioTarget.MP3, bitrate) { } } }
                            assertEquals(before, file.parentFile!!.listFiles()!!.map { it.name }.toSet())
                        }
                    }
                } finally { file.delete() }
            }
            val bad = seed.copy(info = seed.info.copy(sampleRate = 96000))
            assertEquals(AudioOutputIssue.ENCODER, AudioOutputSupport().issue(bad.info, AudioTarget.MP3, 128000))
        } finally { seed.file.delete() }
    }
    @Test fun cancellingNativeEncodingClosesHandleAndDeletesPartialWithoutChangingSource() = runBlocking<Unit>(Dispatchers.IO) {
        val source = engine.importAudio(uri("long.m4a")); val original = source.file.readBytes()
        try {
            val before = source.file.parentFile!!.listFiles()!!.map { it.name }.toSet()
            val task = launch { engine.convert(source, AudioTarget.MP3, 256000) { if (it != null) cancel() } }
            task.join(); assertTrue(task.isCancelled)
            assertEquals(before, source.file.parentFile!!.listFiles()!!.map { it.name }.toSet())
            assertArrayEquals(original, source.file.readBytes())
            val after = engine.convert(source, AudioTarget.MP3, 64000) { }
            after.file.delete()
        } finally { source.file.delete() }
    }
    @Test fun largerCopiesStillRequireConsentAndUseRealMp3MimeAndBytes() = runBlocking<Unit>(Dispatchers.IO) {
        val source = engine.importAudio(uri("tone.mp3"))
        val result = engine.convert(source, AudioTarget.MP3, 256000) { }
        try {
            assertTrue(result.bytes >= source.bytes)
            assertThrows(IllegalArgumentException::class.java) { runBlocking { engine.export(source, result, uri("export.mp3")) } }
            assertThrows(IllegalArgumentException::class.java) { runBlocking { MediaOutputs(context).stage(result.file,
                "audio/mpeg", "test.mp3", source.bytes) } }
            engine.export(source, result, uri("export.mp3"), true)
            assertArrayEquals(result.file.readBytes(), context.contentResolver.openInputStream(uri("export.mp3"))!!.use { it.readBytes() })
            val shared = MediaOutputs(context).stage(result.file, "audio/mpeg", "test.mp3", source.bytes, true)
            assertEquals("audio/mpeg", shared.shareIntent().type); assertEquals("audio/mpeg", shared.viewIntent().type)
            assertTrue(shared.name.endsWith(".mp3"))
            assertArrayEquals(result.file.readBytes(), context.contentResolver.openInputStream(shared.uri)!!.use { it.readBytes() })
        } finally { source.file.delete(); result.file.delete(); context.contentResolver.delete(uri("export.mp3"), null, null) }
    }
    @Test fun nativeBoundsFlushAndIdempotentCloseAreEnforced() {
        val encoder = LameEncoder(48000, 2, 128000)
        val output = ByteArray(12800)
        assertThrows(java.io.IOException::class.java) { encoder.encode(ShortArray(2), 4097, output) }
        assertThrows(java.io.IOException::class.java) { encoder.encode(ShortArray(2), 2, output) }
        assertThrows(java.io.IOException::class.java) { encoder.encode(ShortArray(2), 1, ByteArray(5)) }
        encoder.encode(ShortArray(4096 * 2), 4096, output); encoder.flush(output)
        assertTrue(encoder.tag(output) > 0)
        assertThrows(java.io.IOException::class.java) { encoder.encode(ShortArray(2), 1, output) }
        encoder.close(); encoder.close()
        assertThrows(IllegalStateException::class.java) { encoder.flush(output) }
    }
    @Test fun exactCorrespondingSourceAndLicenseAreAvailableOfflineAndCanBeExported() = runBlocking<Unit>(Dispatchers.IO) {
        val source = context.assets.open(Mp3SourceBundle.ASSET).use { it.readBytes() }
        val entries = mutableSetOf<String>()
        ZipInputStream(source.inputStream()).use { zip ->
            while (true) { val entry = zip.nextEntry ?: break; entries.add(entry.name); zip.closeEntry() }
        }
        assertTrue(entries.containsAll(listOf("third_party/lame/lame-4.0.tar.gz", "third_party/lame/COPYING",
            "third_party/lame/LICENSE-2.1.txt", "third_party/lame/README.md", "app/src/main/cpp/CMakeLists.txt",
            "app/src/main/cpp/config.h.in", "app/src/main/cpp/lame_bridge.c", "interface/LameEncoder.java")))
        try {
            Mp3SourceBundle.save(context, uri("export.mp3")) // Test provider holds arbitrary export bytes.
            assertArrayEquals(source, context.contentResolver.openInputStream(uri("export.mp3"))!!.use { it.readBytes() })
        } finally { context.contentResolver.delete(uri("export.mp3"), null, null) }
    }
}

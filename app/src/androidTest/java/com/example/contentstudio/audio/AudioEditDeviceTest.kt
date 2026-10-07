package com.example.contentstudio.audio

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioEditDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val engine get() = AudioEngine(context)
    private fun uri(name: String) = Uri.parse("content://com.example.contentstudio.test.audio/$name")
    private suspend fun pcm(result: AudioResult): ByteArray {
        val decoded = engine.convert(AudioSource(result.file, Uri.fromFile(result.file), "out", result.bytes, result.info), AudioTarget.WAV, 128000) {}
        return try { decoded.file.readBytes().copyOfRange(44, decoded.bytes.toInt()) } finally { decoded.file.delete() }
    }
    @Test fun rangesAndOrderPreserveEveryPcmSampleAndFlacIsIdentical() = runBlocking<Unit>(Dispatchers.IO) {
        val seed = engine.importAudio(uri("tone.wav"))
        val sources = (0..1).map { index ->
            val file = File.createTempFile("source-", ".media", seed.file.parentFile)
            val pcm = ByteBuffer.allocate(48000 * 2 * 2 * 2).order(ByteOrder.LITTLE_ENDIAN)
            repeat(96000) { frame -> pcm.putShort((frame % 20000 + index * 10000).toShort()); pcm.putShort((-frame % 20000 - index * 10000).toShort()) }
            file.writeBytes(AudioPolicy.wavHeader(48000, 2, pcm.capacity().toLong()) + pcm.array())
            AudioSource(file, Uri.fromFile(file), "sample-$index.wav", file.length(), engine.inspect(file))
        }
        try {
            val clips = listOf(AudioClip(sources[0], AudioRange(500, 1500)), AudioClip(sources[1], AudioRange(250, 1000)))
            for (ordered in listOf(clips, clips.reversed())) {
                val edit = AudioEdit(ordered, 48000, 2)
                val expected = ordered.map { clip -> clip.source.file.readBytes().copyOfRange(
                    44 + (clip.range.startMs * 48000 * 4 / 1000).toInt(), 44 + (clip.range.end(2000) * 48000 * 4 / 1000).toInt()) }.reduce { a, b -> a + b }
                for (target in listOf(AudioTarget.WAV, AudioTarget.FLAC)) {
                    val result = engine.convert(edit, target, 128000) {}
                    try { assertEquals(1750L, result.info.durationMs); assertArrayEquals(expected, pcm(result)) }
                    finally { result.file.delete() }
                }
            }
        } finally { sources.forEach { it.file.delete() }; seed.file.delete() }
    }
    @Test fun allInputContainersTrimToExactDecodedSamples() = runBlocking<Unit>(Dispatchers.IO) {
        for (name in listOf("tone.wav", "tone.mp3", "tone.m4a", "tone.aac", "tone.ogg", "tone.opus", "tone.flac")) {
            val source = engine.importAudio(uri(name))
            val full = engine.convert(source, AudioTarget.WAV, 128000) {}
            val edit = AudioEdit(listOf(AudioClip(source, AudioRange(200, 1200))), 48000, 2)
            val result = engine.convert(edit, AudioTarget.WAV, 128000) {}
            try {
                assertEquals(1000L, result.info.durationMs)
                val expected = full.file.readBytes().copyOfRange(44 + 38400, 44 + 230400)
                assertArrayEquals(name, expected, result.file.readBytes().copyOfRange(44, result.bytes.toInt()))
            } finally { source.file.delete(); full.file.delete(); result.file.delete() }
        }
    }
    @Test fun mixedRatesAndChannelsUseVisibleProfileForAllFiveFormats() = runBlocking<Unit>(Dispatchers.IO) {
        val stereo = engine.importAudio(uri("tone.wav")); val mono = engine.importAudio(uri("mono44100.wav"))
        try {
            val clips = listOf(AudioClip(stereo, AudioRange(500, 1500)), AudioClip(mono, AudioRange(250, 1500)))
            val folder = File(context.getExternalFilesDir("qa"), "audio-edits").apply { mkdirs() }
            for ((rate, channels) in listOf(48000 to 2, 44100 to 1)) for (target in AudioTarget.entries) {
                val edit = AudioEdit(clips, rate, channels)
                val result = engine.convert(edit, target, 128000) {}
                try {
                    assertEquals(target.sampleRate(rate), result.info.sampleRate); assertEquals(channels, result.info.channels)
                    assertTrue("$target: ${result.info.durationMs}", kotlin.math.abs(result.info.durationMs - 2250) < 100)
                    val decoded = pcm(result); assertTrue(decoded.any { it != 0.toByte() })
                    if (target == AudioTarget.WAV && channels == 2) {
                        val values = ByteBuffer.wrap(decoded).order(ByteOrder.LITTLE_ENDIAN)
                        values.position(rate * 4)
                        while (values.hasRemaining()) assertEquals(values.short, values.short)
                    }
                    result.file.copyTo(File(folder, "$rate-$channels.${target.extension}"), overwrite = true)
                } finally { result.file.delete() }
            }
        } finally { stereo.file.delete(); mono.file.delete() }
    }
    @Test fun cancellationDuringSelectionAndEncodingRetiresEveryTemporaryFile() = runBlocking<Unit>(Dispatchers.IO) {
        val source = engine.importAudio(uri("long.m4a")); val original = source.file.readBytes()
        try {
            val edit = AudioEdit(listOf(AudioClip(source, AudioRange(100, 5000))), source.info.sampleRate, source.info.channels)
            for (target in AudioTarget.entries) for (phase in listOf(0, 50)) {
                val before = source.file.parentFile!!.listFiles()!!.map { it.name }.toSet()
                val job = launch { engine.convert(edit, target, 128000) { if (it != null && it >= phase) cancel() } }
                job.join(); assertTrue("$target phase $phase", job.isCancelled)
                assertEquals(before, source.file.parentFile!!.listFiles()!!.map { it.name }.toSet())
                assertArrayEquals(original, source.file.readBytes())
            }
        } finally { source.file.delete() }
    }
    @Test fun consentUsesBothOriginalSizesAndProtectsBothOriginalUris() = runBlocking<Unit>(Dispatchers.IO) {
        val one = engine.importAudio(uri("tone.mp3")); val two = engine.importAudio(uri("tone.m4a"))
        val edit = AudioEdit(listOf(AudioClip(one), AudioClip(two)), 48000, 2)
        val result = engine.convert(edit, AudioTarget.WAV, 128000) {}
        try {
            assertTrue(result.bytes >= edit.sourceBytes)
            val originals = edit.clips.map { context.contentResolver.openInputStream(it.source.originalUri)!!.use { input -> input.readBytes() } }
            assertThrows(IllegalArgumentException::class.java) { runBlocking { engine.export(edit, result, uri("export.wav")) } }
            for (clip in edit.clips) assertThrows(IllegalArgumentException::class.java) { runBlocking { engine.export(edit, result, clip.source.originalUri, true) } }
            engine.export(edit, result, uri("export.wav"), true)
            assertArrayEquals(result.file.readBytes(), context.contentResolver.openInputStream(uri("export.wav"))!!.use { it.readBytes() })
            edit.clips.forEachIndexed { i, clip -> assertArrayEquals(originals[i], context.contentResolver.openInputStream(clip.source.originalUri)!!.use { it.readBytes() }) }
        } finally { one.file.delete(); two.file.delete(); result.file.delete(); context.contentResolver.delete(uri("export.wav"), null, null) }
    }
    @Test fun invalidTotalsAndIntervalsNeverProduceFilesOrReduceSettings() = runBlocking<Unit>(Dispatchers.IO) {
        val source = engine.importAudio(uri("long.m4a"))
        val second = engine.importAudio(uri("tone.wav"))
        try {
            assertThrows(IllegalArgumentException::class.java) { AudioEdit(listOf(AudioClip(source, AudioRange(500, 100))), 48000, 2) }
            assertThrows(IllegalArgumentException::class.java) { AudioEdit(List(3) { AudioClip(source) }, 48000, 2) }
            val fake = source.copy(info = source.info.copy(durationMs = 600000))
            assertThrows(IllegalArgumentException::class.java) { AudioEdit(listOf(AudioClip(fake), AudioClip(second)), 48000, 2) }
            val edit = AudioEdit(listOf(AudioClip(second, AudioRange(200, 1200))), 96000, 2)
            val before = source.file.parentFile!!.listFiles()!!.map { it.name }.toSet()
            assertThrows(IllegalArgumentException::class.java) { runBlocking { engine.convert(edit, AudioTarget.WAV, 123) {} } }
            assertThrows(IllegalArgumentException::class.java) { runBlocking { engine.convert(edit, AudioTarget.MP3, 128000) {} } }
            assertEquals(before, source.file.parentFile!!.listFiles()!!.map { it.name }.toSet())
        } finally { source.file.delete(); second.file.delete() }
    }
}

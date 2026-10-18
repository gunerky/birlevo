package com.example.contentstudio

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.example.contentstudio.audio.*
import com.example.contentstudio.media.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Cross-engine output assertions; the opt-in rule supplies a resident advertising SDK. */
class AdResidentBasicMediaDeviceTest {
    @get:Rule val residentAdvertising = ResidentAdvertisingRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val evidence get() = File(context.getExternalFilesDir(null), "qa/resident-ad-media").apply { mkdirs() }

    @Test fun fullImageAudioRoundTripsAndCopyConsentPreserveSources() = runBlocking<Unit>(Dispatchers.IO) {
        val resident = InstrumentationRegistry.getArguments().getString("resident-ad-network") == "true"
        if (!resident) assertFalse("Normal engine tests must not initialize advertising",
            com.google.android.libraries.ads.mobile.sdk.MobileAds.isInitialized)
        val image = ImageCompressor(context)
        val original = File.createTempFile("resident-image-", ".png", context.cacheDir)
        val bitmap = Bitmap.createBitmap(3840, 2160, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.RED)
            canvas.drawRect(0f, 0f, 1920f, 1080f, Paint().apply { color = Color.GREEN })
            original.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { bitmap.recycle() }
        val originalBytes = original.readBytes()
        val source = image.importImage(Uri.fromFile(original))
        try {
            val result = image.compress(source, 95, 2560, ImageOutputFormat.PNG)
            try {
                assertEquals(2560, result.width); assertEquals(1440, result.height)
                val pixels = BitmapFactory.decodeFile(result.file.path)
                try {
                    assertEquals(Color.GREEN, pixels.getPixel(100, 100))
                    assertEquals(Color.RED, pixels.getPixel(2400, 1300))
                } finally { pixels.recycle() }
                result.file.copyTo(File(evidence, "image-2560.png"), true)
            } finally { result.preview.recycle(); result.file.delete() }
            assertArrayEquals(originalBytes, source.file.readBytes())
            assertArrayEquals(originalBytes, original.readBytes())
        } finally { source.preview.recycle(); source.file.delete(); original.delete() }

        val tiny = image.importImage(Uri.parse("content://com.example.contentstudio.test.fixtures/tiny.png"))
        val copy = image.compress(tiny, 95, 2560, ImageOutputFormat.BMP)
        try {
            assertTrue(copy.bytes > tiny.bytes)
            val target = Uri.parse("content://com.example.contentstudio.test.fixtures/export.bmp")
            try { image.export(tiny, copy, target); fail("Larger copy needs explicit approval with resident SDK") }
            catch (_: IllegalArgumentException) { }
            image.export(tiny, copy, target, true)
            assertArrayEquals(copy.file.readBytes(), context.contentResolver.openInputStream(target)!!.use { it.readBytes() })
        } finally { copy.preview.recycle(); copy.file.delete(); tiny.preview.recycle(); tiny.file.delete() }

        val audio = AudioEngine(context)
        val input = audio.importAudio(Uri.parse("content://com.example.contentstudio.test.audio/tone.wav"))
        val sourceBytes = input.file.readBytes()
        try {
            val wav = audio.convert(input, AudioTarget.WAV, 128_000) { }
            try {
                wav.file.copyTo(File(evidence, "audio.wav"), true)
                val issue = AudioOutputSupport().issue(input.info, AudioTarget.FLAC, 128_000)
                val baseline = File(evidence, "baseline-flac.txt")
                if (resident) {
                    assertTrue("The resident profile must capture codec support before SDK startup", baseline.isFile)
                    assertEquals("SDK initialization must not remove FLAC support",
                        baseline.readText(), issue?.name ?: "available")
                }
                File(evidence, "flac-support.txt").writeText(issue?.name ?: "available")
                if (issue == null) {
                    val flac = audio.convert(input, AudioTarget.FLAC, 128_000) { }
                    try {
                        val decoded = audio.convert(AudioSource(flac.file, Uri.fromFile(flac.file), "resident.flac", flac.bytes,
                            flac.info), AudioTarget.WAV, 128_000) { }
                        try { assertArrayEquals(wav.file.readBytes(), decoded.file.readBytes()) }
                        finally { decoded.file.delete() }
                        flac.file.copyTo(File(evidence, "audio.flac"), true)
                    } finally { flac.file.delete() }
                } else {
                    assertTrue("Known legacy codec absence must remain explicit",
                        issue in setOf(AudioOutputIssue.ENCODER, AudioOutputIssue.DECODER))
                    assertEquals(24, android.os.Build.VERSION.SDK_INT)
                    val before = input.file.parentFile!!.listFiles()!!.toSet()
                    try { audio.convert(input, AudioTarget.FLAC, 128_000) { }; fail("Unavailable FLAC must fail explicitly") }
                    catch (_: IllegalArgumentException) { }
                    assertEquals(before, input.file.parentFile!!.listFiles()!!.toSet())
                }
            } finally { wav.file.delete() }
            val mp3 = audio.convert(input, AudioTarget.MP3, 128_000) { }
            try {
                assertEquals(128_000, Mp3Frames.inspect(mp3.file).bitrate)
                val decoded = audio.convert(AudioSource(mp3.file, Uri.fromFile(mp3.file), "resident.mp3", mp3.bytes,
                    mp3.info), AudioTarget.WAV, 128_000) { }
                try {
                    assertEquals(input.info.sampleRate, decoded.info.sampleRate)
                    assertEquals(input.info.channels, decoded.info.channels)
                    assertTrue(decoded.file.readBytes().drop(44).any { it != 0.toByte() })
                } finally { decoded.file.delete() }
                mp3.file.copyTo(File(evidence, "audio.mp3"), true)
            } finally { mp3.file.delete() }
            val before = input.file.parentFile!!.listFiles()!!.toSet()
            val cancelled = launch { audio.convert(input, AudioTarget.MP3, 256_000) { cancel() } }
            cancelled.join(); assertTrue(cancelled.isCancelled)
            assertEquals(before, input.file.parentFile!!.listFiles()!!.toSet())
            assertArrayEquals(sourceBytes, input.file.readBytes())
        } finally { input.file.delete() }
        if (!resident) assertFalse("The opt-in rule must remain inactive in normal engine tests",
            com.google.android.libraries.ads.mobile.sdk.MobileAds.isInitialized)
    }
}

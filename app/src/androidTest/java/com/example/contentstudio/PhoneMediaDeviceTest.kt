package com.example.contentstudio

import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import com.example.contentstudio.data.*
import com.example.contentstudio.media.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Real synthetic files are decoded and encoded. Providers deliberately advertise inaccurate MIME. */
class PhoneMediaDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun image(name: String) = Uri.parse("content://com.example.contentstudio.test.fixtures/$name")
    private fun video(name: String) = Uri.parse("content://com.example.contentstudio.test.videos/$name")
    private fun bytes(uri: Uri) = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }

    @Test fun modernStillImagesKeepOrientationAndExistingOutputChoices() = runBlocking<Unit> {
        val engine = ImageCompressor(context)
        for (name in listOf("modern.heic", "modern.avif", "modern-irot.heic")) {
            val original = bytes(image(name))
            val source = engine.importImage(image(name))
            try {
                val rotated = name.contains("irot")
                assertEquals(name, if (rotated) 80 else 120, source.width)
                assertEquals(name, if (rotated) 120 else 80, source.height)
                val corner = source.preview.getPixel(10, 10)
                if (rotated) assertTrue(name, Color.blue(corner) > 180 && Color.red(corner) < 70)
                else assertTrue(name, Color.red(corner) > 180 && Color.blue(corner) < 70)
                for (format in ImageOutputFormat.entries) {
                    val result = engine.compress(source, 80, 1920, format)
                    try {
                        assertEquals(source.width, result.width); assertEquals(source.height, result.height)
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(result.file.path, bounds)
                        assertEquals(format.mime, bounds.outMimeType)
                        assertEquals(result.file.length(), result.bytes)
                        if (compressionBenefit(source.bytes, result.bytes) != CompressionBenefit.SMALLER) {
                            assertThrows(IllegalArgumentException::class.java) {
                                runBlocking { engine.export(source, result, image("export.${format.extension}")) }
                            }
                        }
                    } finally { result.file.delete() }
                }
                assertArrayEquals(original, bytes(source.originalUri))
                assertArrayEquals(original, source.file.readBytes())
            } finally { source.file.delete() }
        }
    }

    @Test fun modernImagesAreCopiedWithoutReencodingAndUsedInBothPdfTools() = runBlocking<Unit> {
        val outputs = MediaOutputs(context)
        val pdf = PhotoPdf(context)
        val names = listOf("modern.heic", "modern.avif", "modern-irot.heic")
        for (name in names) {
            val original = bytes(image(name))
            val owned = outputs.retainImage(image(name))
            try {
                assertArrayEquals(original, bytes(owned))
                val project = StudioProject(id = "phone-image", kind = ProjectKind.PRESENTATION,
                    title = "Phone image", updatedAt = 1, slides = listOf(Slide(imageUri = owned.toString())))
                val result = PresentationPdf(context).create(project) {}
                try { assertTrue(result.bytes > 0); assertEquals(1, result.pages) } finally { result.file.delete() }
            } finally { outputs.discardUnattachedImage(owned) }
        }
        val pages = pdf.importPages(names.map(::image), 100, PhotoPdf.MAX_BATCH_BYTES) {}
        try {
            pages.zip(names).forEach { (page, name) -> assertArrayEquals(bytes(image(name)), page.file.readBytes()) }
            val result = pdf.create(pages, 4096) {}
            try {
                PdfRenderer(ParcelFileDescriptor.open(result.file, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
                    assertEquals(3, renderer.pageCount)
                    renderer.openPage(0).use { assertTrue(it.width > it.height) }
                    renderer.openPage(2).use { assertTrue(it.height > it.width) }
                }
            } finally { result.file.delete() }
        } finally { pages.forEach { it.file.delete() } }
    }

    @Test fun phoneContainersAndCodecsTranscodeToAvcAacMp4WithoutChangingSources() = runBlocking<Unit> {
        val engine = VideoCompressor(context)
        for (name in listOf("hevc.mp4", "container.mkv", "phone.mov", "phone.3gp", "phone.webm", "phone-vorbis.webm", "phone-mp3.mkv")) {
            val original = bytes(video(name))
            val source = engine.importVideo(video(name))
            try {
                assertTrue(name, source.info.durationMs > 0)
                val result = engine.compress(source, VideoQuality.BALANCED, 640, VideoTrim(0, minOf(1500, source.info.durationMs))) {}
                try {
                    assertTrue(name, result.bytes > 0)
                    assertEquals(name, source.info.hasAudio, result.info.hasAudio)
                    val extractor = MediaExtractor()
                    try {
                        extractor.setDataSource(result.file.path)
                        val mimes = (0 until extractor.trackCount).map { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME) }
                        assertEquals(name, 1, mimes.count { it == "video/avc" })
                        assertEquals(name, if (source.info.hasAudio) 1 else 0, mimes.count { it == "audio/mp4a-latm" })
                    } finally { extractor.release() }
                    if (compressionBenefit(source.bytes, result.bytes) != CompressionBenefit.SMALLER) {
                        assertThrows(IllegalArgumentException::class.java) {
                            runBlocking { engine.export(source, result, video("export.mp4")) }
                        }
                    }
                    assertArrayEquals(name, original, bytes(source.originalUri))
                } finally { result.file.delete() }
            } finally { source.file.delete() }
        }
    }

    @Test fun sequencesUnsupportedCodecsAndTenBitVideoAreRejectedWithoutPartialImports() = runBlocking<Unit> {
        val imageFolder = File(context.noBackupFilesDir, "image-compression")
        val imageBefore = imageFolder.listFiles()?.map { it.name }?.toSet().orEmpty()
        val animation = try { ImageCompressor(context).importImage(image("modern-sequence.avif")); null }
            catch (e: MediaImportException) { e }
        assertEquals(ImportIssue.IMAGE_ANIMATED, animation?.issue)
        assertEquals(imageBefore, imageFolder.listFiles()?.map { it.name }?.toSet().orEmpty())
        val folder = File(context.noBackupFilesDir, "video-compression")
        val before = folder.listFiles()?.map { it.name }?.toSet().orEmpty()
        for (name in listOf("phone-10bit.mp4", "phone-mpeg4.mp4")) {
            val error = try { VideoCompressor(context).importVideo(video(name)); null } catch (e: MediaImportException) { e }
            assertEquals(name, ImportIssue.VIDEO_CODEC, error?.issue)
            assertEquals(before, folder.listFiles()?.map { it.name }?.toSet().orEmpty())
        }
    }
}

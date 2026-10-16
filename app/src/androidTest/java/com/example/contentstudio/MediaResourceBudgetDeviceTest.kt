package com.example.contentstudio

import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.example.contentstudio.media.*
import com.example.contentstudio.storage.*
import java.io.File
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class MediaResourceBudgetDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val mib = 1024L*1024
    private val lowMemory get() = WorkingMemory { MemorySnapshot(8*mib,7*mib) }
    private val noSpace get() = StreamingStorage { StorageSnapshot(1024*mib,64*mib) }
    private val evidence get() = File(context.getExternalFilesDir(null),"qa/media-budgets").apply { mkdirs() }
    private fun fixture(group: String,name: String) = Uri.parse("content://com.example.contentstudio.test.$group/$name")
    private suspend inline fun <reified T: Exception> rejected(crossinline work: suspend () -> Unit) {
        try { work(); fail("Expected ${T::class.java.simpleName}") } catch (failure: Exception) { if(failure !is T) throw failure }
    }
    @Test fun imageOutputsKeepSelected2560ResolutionAcrossAllFourFormats() = runBlocking {
        val engine = ImageCompressor(context)
        val source = engine.importImage(fixture("fixtures","large-square.png"))
        try {
            assertEquals(4000,source.width); assertEquals(4000,source.height)
            for(format in ImageOutputFormat.entries) {
                val result = engine.compress(source,80,2560,format)
                try {
                    assertEquals(2560,result.width); assertEquals(2560,result.height)
                    val bitmap = BitmapFactory.decodeFile(result.file.path)
                    try {
                        assertEquals(2560,bitmap.width);assertEquals(2560,bitmap.height)
                        assertTrue(Color.red(bitmap.getPixel(200,200)) > 240)
                        assertTrue(Color.blue(bitmap.getPixel(2300,200)) > 240)
                    } finally { bitmap.recycle() }
                    result.file.copyTo(File(evidence,"image-2560.${format.extension}"),overwrite=true)
                } finally { result.preview.recycle(); result.file.delete() }
            }
        } finally { source.preview.recycle();source.file.delete() }
    }
    @Test fun imageAdmissionKeepsOriginalAndExistingOutputOnMemoryAndSpaceFailures() = runBlocking {
        val engine = ImageCompressor(context)
        val source = engine.importImage(fixture("fixtures","tiny.png"))
        val result = engine.compress(source,80,2560,ImageOutputFormat.PNG)
        val bytes = source.file.readBytes(); val originalResult = result.file.readBytes()
        try {
            rejected<WorkingMemoryException> { ImageCompressor(context,memory=lowMemory).compress(source,80,2560,ImageOutputFormat.PNG) }
            rejected<StorageCapacityException> { ImageCompressor(context,capacity={ noSpace }).compress(source,80,2560,ImageOutputFormat.PNG) }
            assertArrayEquals(bytes,source.file.readBytes());assertArrayEquals(originalResult,result.file.readBytes())
            assertEquals(ImportIssue.MEMORY,importIssue(WorkingMemoryException()))
            assertEquals(ImportIssue.STORAGE,importIssue(StorageCapacityException()))
        } finally { result.preview.recycle();result.file.delete();source.preview.recycle();source.file.delete() }
    }
    @Test fun videoAdmissionAndNativeWriteMonitorCleanUpAndAllowRetry() = runBlocking {
        val engine = VideoCompressor(context)
        val source = engine.importVideo(fixture("videos","audio.mp4"))
        val before = source.file.readBytes()
        try {
            rejected<WorkingMemoryException> { VideoCompressor(context,memory=lowMemory).compress(source,VideoQuality.BALANCED,640,VideoTrim(0,source.info.durationMs)) {} }
            rejected<StorageCapacityException> { VideoCompressor(context,capacity={ noSpace }).compress(source,VideoQuality.BALANCED,640,VideoTrim(0,source.info.durationMs)) {} }
            var queries = 0
            val pressure = VideoCompressor(context,capacity={ StreamingStorage { StorageSnapshot(1024*mib,if(++queries == 1) 512*mib else 64*mib) } })
            rejected<StorageCapacityException> { pressure.compress(source,VideoQuality.BALANCED,640,VideoTrim(0,source.info.durationMs)) {} }
            assertTrue(queries >= 2)
            assertArrayEquals(before,source.file.readBytes())
            val result = engine.compress(source,VideoQuality.BALANCED,640,VideoTrim(0,source.info.durationMs)) {}
            try {
                val expected = videoTarget(source.info.width,source.info.height,640,VideoQuality.BALANCED)
                assertEquals(expected.width,result.info.width);assertEquals(expected.height,result.info.height)
                result.file.copyTo(File(evidence,"video-retry.mp4"),overwrite=true)
            } finally { result.info.poster?.recycle();result.file.delete() }
            assertFalse(source.file.parentFile!!.listFiles().orEmpty().any { it.name.startsWith("working-") })
        } finally { source.info.poster?.recycle();source.file.delete() }
    }
    @Test fun isolatedPdfWorkerKeeps4096EdgeAndDescriptorStorageAccess() = runBlocking {
        val reader = PdfReader(context)
        val imported = reader.importPdf(fixture("pdf","reader.pdf"))
        try {
            for(format in PdfReaderPolicy.imageFormats) {
                val output = reader.pageImage(imported.source,0,4096,format)
                try {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds=true }
                    BitmapFactory.decodeFile(output.file.path,bounds)
                    assertEquals(4096,maxOf(bounds.outWidth,bounds.outHeight))
                    output.file.copyTo(File(evidence,"pdf-4096.${format.extension}"),overwrite=true)
                } finally { output.file.delete() }
            }
        } finally { imported.preview.recycle();imported.source.file.delete() }
    }
}

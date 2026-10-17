package com.example.contentstudio

import android.net.Uri
import android.os.Process
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.example.contentstudio.media.*
import com.example.contentstudio.processing.*
import java.io.File
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*

/** Execute prepare, external force-stop, verify as separate runner invocations. */
class BackgroundProcessingProcessTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val jobs get() = ProcessingJobs.get(context)
    private var activity: ActivityScenario<MainActivity>? = null
    private lateinit var video: VideoCompressionViewModel
    private lateinit var photo: PhotoPdfViewModel
    private val qa get() = File(context.getExternalFilesDir(null),"qa").apply { mkdirs() }
    private suspend fun main(block: suspend () -> Unit) = withContext(Dispatchers.Main) { block() }
    private suspend fun waitFor(block: () -> Boolean) { withTimeout(60_000) { while(!block()) delay(25) } }
    private suspend fun launch() {
        activity=ActivityScenario.launch(MainActivity::class.java)
        activity!!.onActivity { video=ViewModelProvider(it)[VideoCompressionViewModel::class.java]; photo=ViewModelProvider(it)[PhotoPdfViewModel::class.java] }
        waitFor { video.state.value.source != null || !video.state.value.busy }
    }
    @After fun close() { activity?.close() }
    @Test fun prepareCompletedPdfAndHeldVideoJob() = runBlocking<Unit> {
        main { jobs.awaitReady(); ProcessingKind.entries.forEach { jobs.forget(it) } }
        AtomicMediaSession(File(context.noBackupFilesDir,"video-compression")).write(null)
        AtomicMediaSession(File(context.noBackupFilesDir,"photo-pdf")).write(null)
        launch(); waitFor { !photo.state.value.busy && !video.state.value.busy }
        main { video.load(Uri.parse("content://com.example.contentstudio.test.videos/audio.mp4"))
            photo.add(listOf(Uri.parse("content://com.example.contentstudio.test.fixtures/orientation-6.jpg"),Uri.parse("content://com.example.contentstudio.test.fixtures/alpha.webp"))) }
        waitFor { !video.state.value.busy && !photo.state.value.busy }
        main { video.setOptions(VideoQuality.HIGH,1920); video.setTrim(1000,4500); video.awaitSession()
            photo.setMaxEdge(4096); photo.generate() }
        waitFor { photo.state.value.result != null && !photo.state.value.busy }
        val result=photo.state.value.result!!
        File(qa,"background-pdf-name.txt").writeText(result.file.name)
        File(qa,"background-pdf-bytes.txt").writeText(result.bytes.toString())
        result.file.setLastModified(1)
        val value=video.state.value
        val request=MediaSessionCodec.video(VideoSession(MediaSessionCodec.asset(value.source!!),value.quality,value.maxEdge,value.trim!!))
        var entered=false
        main { jobs.start(ProcessingKind.VIDEO,request) { entered=true; awaitCancellation() } }
        waitFor { entered } // The deterministic hold is after the RUNNING journal commit.
        assertEquals(ProcessingStatus.RUNNING,jobs.records.value[ProcessingKind.VIDEO]!!.status)
        File(qa,"background-prepared-pid.txt").writeText(Process.myPid().toString())
    }
    @Test fun verifyInterruptedJobCompletedPdfAndExplicitVideoRetry() = runBlocking<Unit> {
        assertNotEquals(File(qa,"background-prepared-pid.txt").readText().toInt(),Process.myPid())
        launch(); waitFor { !video.state.value.busy && video.state.value.workStatus==ProcessingStatus.INTERRUPTED }
        assertNull(video.state.value.result); assertEquals(VideoTrim(1000,4500),video.state.value.trim)
        waitFor { !photo.state.value.busy && photo.state.value.result != null }
        assertEquals(File(qa,"background-pdf-name.txt").readText(),photo.state.value.result!!.file.name)
        assertEquals(File(qa,"background-pdf-bytes.txt").readText().toLong(),photo.state.value.result!!.bytes)
        assertEquals(2,photo.state.value.result!!.pages); assertNotNull(photo.state.value.preview)
        main { video.setOptions(VideoQuality.SMALL,640) }
        // Recipe changes asynchronously recheck encoder support before the UI enables creation.
        waitFor { video.state.value.outputSupport.containsKey(video.state.value.format) }
        assertNull(video.state.value.outputSupport[video.state.value.format])
        main { video.preview() }
        waitFor { !video.state.value.busy && video.state.value.result != null }
        assertTrue(kotlin.math.abs(video.state.value.result!!.info.durationMs-3500)<300)
        main { video.reset(); photo.reset(); video.awaitSession(); photo.awaitSession() }
        File(qa,"background-verified-pid.txt").writeText(Process.myPid().toString())
    }
}

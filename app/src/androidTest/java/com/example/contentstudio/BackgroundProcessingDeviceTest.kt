package com.example.contentstudio

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.example.contentstudio.media.*
import com.example.contentstudio.processing.*
import java.io.File
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*

class BackgroundProcessingDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val jobs get() = ProcessingJobs.get(context)
    private var activity: ActivityScenario<MainActivity>? = null
    private val files = mutableListOf<File>()
    private suspend fun main(block: suspend () -> Unit) = withContext(Dispatchers.Main) { block() }
    private suspend fun waitFor(block: () -> Boolean) { withTimeout(120_000) { while (!block()) delay(25) } }
    private fun launch() { activity=ActivityScenario.launch(MainActivity::class.java) }
    private fun shell(command: String) = instrumentation.uiAutomation.executeShellCommand(command).use {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText().trim()
    }
    @Before fun clean() = runBlocking<Unit> {
        main { jobs.awaitReady(); ProcessingKind.entries.forEach { jobs.forget(it) } }
        AtomicMediaSession(File(context.noBackupFilesDir,"video-compression")).write(null)
        AtomicMediaSession(File(context.noBackupFilesDir,"photo-pdf")).write(null)
        shell("pm grant studio.birlevo.android android.permission.POST_NOTIFICATIONS")
    }
    @After fun close() = runBlocking<Unit> {
        main { ProcessingKind.entries.forEach { jobs.forget(it) } }
        activity?.close(); files.forEach { it.delete() }
    }
    private fun synthetic(kind: ProcessingKind): ProcessingOutput {
        val folder=File(context.noBackupFilesDir,kind.folder).apply { mkdirs() }
        val file=File.createTempFile("output-qa-", ".bin",folder).also { files+=it }
        file.outputStream().use { it.write(ByteArray(100)); it.fd.sync() }
        return ProcessingOutput(file.name,file.length())
    }
    @Test fun videoContinuesAfterActivityDestroyedAndReattachesCompletedOutput() = runBlocking<Unit> {
        launch(); lateinit var first: VideoCompressionViewModel
        activity!!.onActivity { first=ViewModelProvider(it)[VideoCompressionViewModel::class.java] }
        waitFor { !first.state.value.busy }
        main { first.load(Uri.parse("content://com.example.contentstudio.test.videos/cancel.mp4")) }
        waitFor { !first.state.value.busy }; assertNotNull(first.state.value.source)
        main { first.setOptions(VideoQuality.SMALL,640); first.preview() }
        waitFor { jobs.records.value[ProcessingKind.VIDEO]?.status == ProcessingStatus.RUNNING }
        val original = first.state.value.source!!
        activity!!.close(); activity=null
        try {
            shell("input keyevent KEYCODE_SLEEP")
            assertFalse(context.getSystemService(android.os.PowerManager::class.java).isInteractive)
            waitFor { jobs.records.value[ProcessingKind.VIDEO]?.status?.active == false }
        } finally { shell("input keyevent KEYCODE_WAKEUP"); shell("wm dismiss-keyguard") }
        assertEquals(ProcessingStatus.SUCCEEDED,jobs.records.value[ProcessingKind.VIDEO]!!.status)
        launch(); lateinit var restored: VideoCompressionViewModel
        activity!!.onActivity { restored=ViewModelProvider(it)[VideoCompressionViewModel::class.java] }
        waitFor { !restored.state.value.busy && restored.state.value.result != null }
        assertEquals(original.file,restored.state.value.source!!.file)
        assertEquals(640,restored.state.value.maxEdge); assertTrue(restored.state.value.result!!.bytes>0)
        main { restored.reset(); restored.awaitSession() }
    }
    @Test fun heavyTasksQueueAndCancellingQueuedWorkNeverRunsItsEngine() = runBlocking<Unit> {
        launch(); val gate=CompletableDeferred<Unit>(); var entered=false
        main { jobs.start(ProcessingKind.VIDEO,"queue-a") { gate.await(); synthetic(ProcessingKind.VIDEO) }
            jobs.start(ProcessingKind.PHOTO_PDF,"queue-b") { entered=true; synthetic(ProcessingKind.PHOTO_PDF) } }
        waitFor { jobs.records.value[ProcessingKind.VIDEO]?.status==ProcessingStatus.RUNNING }
        assertEquals(ProcessingStatus.QUEUED,jobs.records.value[ProcessingKind.PHOTO_PDF]!!.status)
        main { jobs.cancel(ProcessingKind.PHOTO_PDF) }
        waitFor { jobs.records.value[ProcessingKind.PHOTO_PDF]?.status==ProcessingStatus.CANCELLED }
        assertFalse(entered)
        main { gate.complete(Unit) }
        waitFor { jobs.records.value[ProcessingKind.VIDEO]?.status==ProcessingStatus.SUCCEEDED }
    }
    @Test fun notificationCancelStopsOnlyCapturedJobAndAllowsImmediateRetry() = runBlocking<Unit> {
        launch(); val firstGate=CompletableDeferred<Unit>(); val nextGate=CompletableDeferred<Unit>()
        main { jobs.start(ProcessingKind.VIDEO,"notification-a") { firstGate.await(); synthetic(ProcessingKind.VIDEO) } }
        waitFor { jobs.records.value[ProcessingKind.VIDEO]?.status==ProcessingStatus.RUNNING }
        val notifications=context.getSystemService(NotificationManager::class.java)
        waitFor { notifications.activeNotifications.any { it.id==ProcessingService.NOTIFICATION && it.notification.actions?.isNotEmpty()==true } }
        delay(700)
        val cancel=notifications.activeNotifications.first { it.id==ProcessingService.NOTIFICATION }.notification.actions[0].actionIntent
        cancel.send()
        waitFor { jobs.records.value[ProcessingKind.VIDEO]?.status==ProcessingStatus.CANCELLED }
        main { jobs.start(ProcessingKind.VIDEO,"notification-b") { nextGate.await(); synthetic(ProcessingKind.VIDEO) } }
        waitFor { jobs.records.value[ProcessingKind.VIDEO]?.status==ProcessingStatus.RUNNING }
        cancel.send(); delay(250)
        assertEquals(ProcessingStatus.RUNNING,jobs.records.value[ProcessingKind.VIDEO]!!.status)
        main { nextGate.complete(Unit) }
        waitFor { jobs.records.value[ProcessingKind.VIDEO]?.status==ProcessingStatus.SUCCEEDED }
    }
    @Test fun mediaProcessingTimeoutStopsServiceAndMarksInterrupted() = runBlocking<Unit> {
        val previous=shell("device_config get activity_manager media_processing_fgs_timeout_duration")
        try {
            shell("device_config put activity_manager media_processing_fgs_timeout_duration 1200")
            launch(); val gate=CompletableDeferred<Unit>()
            main { jobs.start(ProcessingKind.VIDEO,"timeout-job") { gate.await(); synthetic(ProcessingKind.VIDEO) } }
            waitFor { jobs.records.value[ProcessingKind.VIDEO]?.status==ProcessingStatus.RUNNING }
            context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            withTimeout(20_000) { while (jobs.records.value[ProcessingKind.VIDEO]?.status?.active==true) delay(50) }
            assertEquals(ProcessingStatus.INTERRUPTED,jobs.records.value[ProcessingKind.VIDEO]!!.status)
            waitFor { context.getSystemService(NotificationManager::class.java).activeNotifications.none { it.id==ProcessingService.NOTIFICATION } }
        } finally {
            if (previous.matches(Regex("[0-9]+"))) shell("device_config put activity_manager media_processing_fgs_timeout_duration $previous")
            else shell("device_config delete activity_manager media_processing_fgs_timeout_duration")
        }
    }
}

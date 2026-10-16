package com.example.contentstudio

import android.content.pm.ServiceInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.example.contentstudio.processing.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class DocumentServiceTypeDeviceTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private fun type():Int {
        val descriptor=instrumentation.uiAutomation.executeShellCommand("dumpsys activity services studio.birlevo.android")
        val text=android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use{it.readText()}
        val match=Regex("(?:foregroundServiceType|types)=0x([0-9a-fA-F]+)").find(text)?:error(text)
        return match.groupValues[1].toInt(16)
    }
    @Test fun documentUsesDataSyncAndNextMediaTaskPromotesBeforeEngineEntry()=runBlocking {
        val jobs=ProcessingJobs.get(context)
        withContext(Dispatchers.Main){jobs.awaitReady();ProcessingKind.entries.forEach{jobs.forget(it)}}
        ActivityScenario.launch(MainActivity::class.java).use {
            val docGate=CompletableDeferred<Unit>();val mediaGate=CompletableDeferred<Unit>()
            val docEntered=CompletableDeferred<Unit>();val mediaEntered=CompletableDeferred<Unit>()
            fun result(kind:ProcessingKind):ProcessingOutput{
                val folder=File(context.noBackupFilesDir,kind.folder).apply{mkdirs()}
                val file=File(folder,"output-${UUID.randomUUID()}.json").apply{writeText("{}")}
                return ProcessingOutput(file.name,file.length())
            }
            try {
                withContext(Dispatchers.Main){jobs.start(ProcessingKind.BATCH_DOCUMENT,"fgs-document"){
                    docEntered.complete(Unit);docGate.await();result(ProcessingKind.BATCH_DOCUMENT)}
                    jobs.start(ProcessingKind.VIDEO,"fgs-media-after-document"){
                        mediaEntered.complete(Unit);mediaGate.await();result(ProcessingKind.VIDEO)}}
                withTimeout(15_000){docEntered.await()}
                assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,type())
                assertEquals(ProcessingStatus.QUEUED,jobs.records.value[ProcessingKind.VIDEO]?.status)
                docGate.complete(Unit)
                withTimeout(15_000){mediaEntered.await()}
                assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING,type())
                mediaGate.complete(Unit)
                withTimeout(15_000){while(jobs.records.value.values.any{it.status.active})delay(20)}
                assertEquals(ProcessingStatus.SUCCEEDED,jobs.records.value[ProcessingKind.BATCH_DOCUMENT]?.status)
                assertEquals(ProcessingStatus.SUCCEEDED,jobs.records.value[ProcessingKind.VIDEO]?.status)
            } finally {withContext(Dispatchers.Main){ProcessingKind.entries.forEach{jobs.forget(it)}}}
        }
    }
}

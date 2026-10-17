package com.example.contentstudio

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.system.Os
import androidx.test.platform.app.InstrumentationRegistry
import com.example.contentstudio.media.*
import com.example.contentstudio.audio.AudioEngine
import com.example.contentstudio.processing.*
import com.example.contentstudio.storage.OwnedFileCleanup
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*

class StorageCleanupDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val created = mutableListOf<File>()
    private val old = System.currentTimeMillis() - 10 * OwnedFileCleanup.DAY
    private fun file(folder: File, name: String, age: Boolean = true) = File(folder, name).apply {
        parentFile!!.mkdirs(); writeText("cleanup:$name"); created += this
        if (age) check(setLastModified(old))
    }
    @After fun clear() { created.asReversed().forEach { it.delete() } }

    @Test fun persistentMediaCleanersRemoveOwnedOrphansButKeepAssetsAndUnknownFiles() {
        val cases = listOf(
            Triple("video-compression", "source-123456.mp4", "working-123456.webm"),
            Triple("audio-conversion", "source-123456.media", "working-123456.wav"),
            Triple("photo-pdf", "page-123456.png", "output-123456.pdf.part"),
        )
        for ((name, sourceName, staleName) in cases) {
            val folder = File(context.noBackupFilesDir, name)
            val source = file(folder, sourceName)
            val bytes = source.readBytes()
            val orphan = file(folder, staleName)
            val unknown = file(folder, "future-cleanup-format.dat")
            when (name) {
                "video-compression" -> VideoCompressor(context).cleanOldCache(setOf(source))
                "audio-conversion" -> AudioEngine(context).cleanOldCache(setOf(source))
                else -> PhotoPdf(context).cleanOldCache(setOf(source))
            }
            assertFalse(orphan.exists()); assertArrayEquals(bytes, source.readBytes()); assertTrue(unknown.exists())
        }
    }

    @Test fun cleanupDoesNotFollowFileOrDirectorySymlinks() {
        val root = File(context.cacheDir, "cleanup-links-${UUID.randomUUID()}").apply { mkdir() }
        created += root
        val outside = file(context.cacheDir, "cleanup-outside-${UUID.randomUUID()}.mp4")
        val bytes = outside.readBytes()
        val link = File(root, "output-1.mp4").also { created += it }
        Os.symlink(outside.path, link.path)
        val sameRoot = file(root, "output-2.mp4")
        val alias = File(root, "output-3.mp4").also { created += it }
        Os.symlink(sameRoot.path, alias.path)
        val cycle = File(root, "output-4.mp4").also { created += it }
        Os.symlink(cycle.path, cycle.path)
        OwnedFileCleanup.files(root, Regex("output-[0-9]+\\.mp4"), setOf(alias))
        assertTrue(link.exists()); assertTrue(alias.exists()); assertTrue(sameRoot.exists())
        assertNotNull(Os.lstat(cycle.path))
        assertArrayEquals(bytes, outside.readBytes())
        val directory = File(root, UUID.randomUUID().toString()).also { created += it }
        Os.symlink(context.cacheDir.path, directory.path)
        OwnedFileCleanup.directories(root, Regex("[0-9a-f-]{36}"), Regex(".*"))
        assertTrue(directory.exists()); assertArrayEquals(bytes, outside.readBytes())
        val rootLink = File(context.cacheDir, "cleanup-root-link-${UUID.randomUUID()}").also { created += it }
        Os.symlink(root.path, rootLink.path)
        OwnedFileCleanup.files(rootLink, Regex("output-[0-9]+\\.mp4"))
        assertTrue(sameRoot.exists()); assertTrue(rootLink.isDirectory)
    }

    @Test fun restagingAnAgedSharePreservesSourceUriAndRetiresOnlyOtherKnownSnapshots() = runBlocking<Unit> {
        val outputs = MediaOutputs(context)
        val source = file(context.cacheDir, "cleanup-input-${UUID.randomUUID()}.jpg", false)
        val first = outputs.stage(source, "image/jpeg", "first.jpg")
        val root = File(context.cacheDir, "shared-outputs")
        val firstDirectory = File(root, first.uri.pathSegments[1])
        val firstFile = File(firstDirectory, first.name)
        created += firstDirectory; created += firstFile
        assertTrue(firstFile.setLastModified(old)); assertTrue(firstDirectory.setLastModified(old))
        val staleDirectory = File(root, UUID.randomUUID().toString()).apply { mkdir() }
        created += staleDirectory
        val stale = file(staleDirectory, "expired.jpg")
        staleDirectory.setLastModified(old)
        val unknownDirectory = File(root, UUID.randomUUID().toString()).apply { mkdir() }
        created += unknownDirectory
        val unknown = file(unknownDirectory, "future.asset")
        val knownBesideUnknown = file(unknownDirectory, "keep.jpg")
        unknownDirectory.setLastModified(old)
        val next = outputs.stage(firstFile, "image/jpeg", "next.jpg")
        val nextDirectory = File(root, next.uri.pathSegments[1])
        created += nextDirectory; created += File(nextDirectory, next.name)
        assertArrayEquals(source.readBytes(), context.contentResolver.openInputStream(first.uri)!!.use { it.readBytes() })
        assertArrayEquals(source.readBytes(), context.contentResolver.openInputStream(next.uri)!!.use { it.readBytes() })
        assertFalse(stale.exists()); assertFalse(staleDirectory.exists())
        assertTrue(unknown.exists()); assertTrue(knownBesideUnknown.exists())
    }

    private fun isolated(root: File) = object : ContextWrapper(context) {
        override fun getNoBackupFilesDir() = root
        override fun startService(service: Intent) = ComponentName(packageName, "CleanupTestService")
        override fun startForegroundService(service: Intent) = ComponentName(packageName, "CleanupTestService")
    }

    @Test fun corruptProcessingIndexDisablesCleanupAndPreservesPreviousOutput() = runBlocking<Unit> {
        val root = File(context.cacheDir, "cleanup-index-${UUID.randomUUID()}").apply { mkdir() }
        created += root
        val processing = File(root, "processing").apply { mkdir() }; created += processing
        val metadata = file(processing, "session.json").apply { writeText("corrupt checkpoint") }
        val folder = File(root, ProcessingKind.VIDEO.folder).apply { mkdir() }; created += folder
        val output = file(folder, "output-123456.mp4")
        val bytes = output.readBytes()
        val jobs = withContext(Dispatchers.Main) { ProcessingJobs(isolated(root)) }
        jobs.awaitReady()
        assertTrue(jobs.recoveryFailed); assertNull(jobs.cleanupRetention(ProcessingKind.VIDEO))
        assertArrayEquals(bytes, output.readBytes()); assertEquals("corrupt checkpoint", metadata.readText())
    }

    @Test fun activeJobDefersCleanupAndFinishedOutputSurvivesDifferentRequest() = runBlocking<Unit> {
        val root = File(context.cacheDir, "cleanup-jobs-${UUID.randomUUID()}").apply { mkdir() }
        created += root
        val jobs = withContext(Dispatchers.Main) { ProcessingJobs(isolated(root)) }
        jobs.awaitReady()
        val folder = File(root, ProcessingKind.VIDEO.folder).apply { mkdir() }; created += folder
        val output = file(folder, "output-123456.mp4")
        val started = CompletableDeferred<Unit>(); val finish = CompletableDeferred<Unit>()
        try {
            withContext(Dispatchers.Main) {
                jobs.serviceAttached { }
                jobs.start(ProcessingKind.VIDEO, "old settings") {
                    started.complete(Unit); finish.await(); ProcessingOutput(output.name, output.length())
                }
            }
            withTimeout(10_000) { started.await() }
            assertNull(jobs.cleanupRetention(ProcessingKind.VIDEO))
            finish.complete(Unit)
            withTimeout(10_000) { while (jobs.records.value[ProcessingKind.VIDEO]?.status != ProcessingStatus.SUCCEEDED) delay(20) }
            assertNull(jobs.retainedOutput(ProcessingKind.VIDEO, "new settings"))
            val retained = jobs.cleanupRetention(ProcessingKind.VIDEO)!!
            OwnedFileCleanup.files(folder, Regex("output-[0-9]+\\.mp4"), retained)
            assertTrue(output.exists()); assertEquals(setOf(output), retained)
            val restarted = withContext(Dispatchers.Main) { ProcessingJobs(isolated(root)) }
            restarted.awaitReady()
            assertEquals(retained, restarted.cleanupRetention(ProcessingKind.VIDEO))
        } finally {
            finish.complete(Unit)
            withContext(Dispatchers.Main) { jobs.forget(ProcessingKind.VIDEO) }
            File(root, "processing/session.json").delete(); File(root, "processing").delete()
        }
    }

    @Test fun staleIdleServiceDestructionCannotCancelNewQueuedOrRunningWork() = runBlocking<Unit> {
        val root = File(context.cacheDir, "cleanup-service-${UUID.randomUUID()}").apply { mkdir() }
        created += root
        val jobs = withContext(Dispatchers.Main) { ProcessingJobs(isolated(root)) }
        jobs.awaitReady()
        val folder = File(root, ProcessingKind.VIDEO.folder).apply { mkdir() }; created += folder
        val output = file(folder, "output-123456.mp4")
        val started = CompletableDeferred<Unit>(); val finish = CompletableDeferred<Unit>()
        try {
            withContext(Dispatchers.Main) {
                val old = jobs.serviceAttached { fail("A stopping service cannot promote new work") }
                assertTrue(jobs.serviceStopping(old))
                jobs.start(ProcessingKind.VIDEO, "next job") {
                    started.complete(Unit); finish.await(); ProcessingOutput(output.name, output.length())
                }
                jobs.serviceDetached(old)
                assertTrue(jobs.records.value[ProcessingKind.VIDEO]!!.status.active)
                val current = jobs.serviceAttached { }
                assertFalse(jobs.serviceStopping(current))
                jobs.serviceDetached(old)
            }
            withTimeout(10_000) { started.await() }
            assertTrue(jobs.records.value[ProcessingKind.VIDEO]!!.status.active)
            finish.complete(Unit)
            withTimeout(10_000) { while (jobs.records.value[ProcessingKind.VIDEO]?.status != ProcessingStatus.SUCCEEDED) delay(20) }
            assertTrue(output.exists())
        } finally {
            finish.complete(Unit)
            withContext(Dispatchers.Main) { jobs.forget(ProcessingKind.VIDEO) }
            File(root, "processing/session.json").delete(); File(root, "processing").delete()
        }
    }
}

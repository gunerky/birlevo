package com.example.contentstudio

import android.app.Application
import android.net.Uri
import android.util.AtomicFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.example.contentstudio.media.*
import java.io.File
import java.io.IOException
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*

class MediaSessionDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val application get() = context.applicationContext as Application
    private val stores = mutableListOf<ViewModelStore>()
    private fun <T : ViewModel> own(model: T): T = model.also { ViewModelStore().also { store -> store.put("qa", model); stores += store } }
    private suspend fun main(block: suspend () -> Unit) = withContext(Dispatchers.Main) { block() }
    private suspend fun waitFor(block: () -> Boolean) { withTimeout(30_000) { while (!block()) delay(20) } }
    private fun fixture(authority: String, name: String) = Uri.parse("content://com.example.contentstudio.test.$authority/$name")
    private fun original(authority: String, name: String): File = File(context.cacheDir, "session-original-$name").also { file ->
        context.contentResolver.openInputStream(fixture(authority, name))!!.use { input -> file.outputStream().use { input.copyTo(it) } }
    }
    private fun storage(name: String) = AtomicMediaSession(File(context.noBackupFilesDir, name))
    @Before fun cleanCheckpoints() { storage("video-compression").write(null); storage("photo-pdf").write(null) }
    @After fun clearModels() { instrumentation.runOnMainSync { stores.forEach { it.clear() } } }

    @Test fun videoRestoresOwnedAgedSourceAndAllSettingsWithoutOriginalOrConsent() = runBlocking<Unit> {
        lateinit var first: VideoCompressionViewModel
        main { first = own(VideoCompressionViewModel(application)) }; waitFor { !first.state.value.busy }
        val input = original("videos", "audio.mp4")
        main { first.load(Uri.fromFile(input)) }; waitFor { !first.state.value.busy }
        assertNotNull(first.state.value.source)
        main { first.setOptions(VideoQuality.HIGH, 1920); first.setTrim(1000, 4500); assertTrue(first.awaitSession()) }
        val source = first.state.value.source!!
        assertTrue(input.delete()); assertTrue(source.file.setLastModified(1))
        main { stores.last().clear() }
        lateinit var restored: VideoCompressionViewModel
        main { restored = own(VideoCompressionViewModel(application)) }; waitFor { !restored.state.value.busy }
        assertEquals(source.file, restored.state.value.source!!.file)
        assertEquals(VideoQuality.HIGH, restored.state.value.quality); assertEquals(1920, restored.state.value.maxEdge)
        assertEquals(VideoTrim(1000,4500), restored.state.value.trim); assertTrue(source.file.exists())
        assertNull(restored.state.value.result); assertFalse(restored.confirmNonSmallerCopy(source.file))
        main { restored.reset(); assertTrue(restored.awaitSession()) }
        assertFalse(source.file.exists()); assertNull(storage("video-compression").read())
    }

    @Test fun photoOrderResolutionAndPrivateInputsSurviveRemovalOfOriginals() = runBlocking<Unit> {
        lateinit var first: PhotoPdfViewModel
        main { first = own(PhotoPdfViewModel(application)) }; waitFor { !first.state.value.busy }
        val originals = listOf(original("fixtures", "orientation-6.jpg"), original("fixtures", "alpha.webp"))
        main { first.add(originals.map(Uri::fromFile)) }; waitFor { !first.state.value.busy }
        assertEquals(2, first.state.value.pages.size)
        main { first.move(first.state.value.pages.last().id, -1); first.setMaxEdge(4096); assertTrue(first.awaitSession()) }
        val pages = first.state.value.pages
        originals.forEach { assertTrue(it.delete()) }; pages.forEach { it.file.setLastModified(1) }
        main { stores.last().clear() }
        lateinit var restored: PhotoPdfViewModel
        main { restored = own(PhotoPdfViewModel(application)) }; waitFor { !restored.state.value.busy }
        assertEquals(pages, restored.state.value.pages); assertEquals(4096, restored.state.value.maxEdge)
        assertNull(restored.state.value.result); pages.forEach { assertTrue(it.file.exists()) }
        main { restored.reset(); assertTrue(restored.awaitSession()) }; pages.forEach { assertFalse(it.file.exists()) }
    }

    private class Failing(private val delegate: MediaSessionStorage) : MediaSessionStorage {
        @Volatile var fail = false
        override fun read() = delegate.read()
        override fun write(json: String?) { if (fail) throw IOException("synthetic storage failure"); delegate.write(json) }
    }
    @Test fun failedPhotoRemovalKeepsDurableSourceUntilRetryAndAtomicReset() = runBlocking<Unit> {
        val disk = storage("photo-pdf"); val failure = Failing(disk)
        lateinit var model: PhotoPdfViewModel
        main { model = own(PhotoPdfViewModel(application, failure)) }; waitFor { !model.state.value.busy }
        main { model.add(listOf(fixture("fixtures","tiny.png"),fixture("fixtures","alpha.webp"))) }; waitFor { !model.state.value.busy }
        main { assertTrue(model.awaitSession()) }
        val previous = disk.read(); val removed = model.state.value.pages.first()
        failure.fail = true
        main { model.remove(removed.id); assertFalse(model.awaitSession()) }
        assertEquals(SessionSaveStatus.ERROR, model.sessionStatus.value)
        assertTrue(removed.file.exists()); assertEquals(previous, disk.read())
        failure.fail = false
        main { model.retrySession(); assertTrue(model.awaitSession()) }
        assertFalse(removed.file.exists()); assertEquals(1, MediaSessionCodec.photos(disk.read()!!).assets.size)
        val retained = model.state.value.pages.single().file
        failure.fail = true
        main { model.reset(); assertFalse(model.awaitSession()) }
        assertTrue(retained.exists()); assertNotNull(disk.read())
        failure.fail = false
        main { model.retrySession(); assertTrue(model.awaitSession()) }
        assertNull(disk.read()); assertFalse(retained.exists())
    }

    @Test fun slowWriterPreservesLatestRevisionAndDoesNotRetireLatestReferencedFile() = runBlocking<Unit> {
        val folder = File(context.cacheDir,"session-writer-qa").apply { mkdirs() }
        val first = File(folder,"first").apply { writeText("a") }; val next = File(folder,"next").apply { writeText("b") }
        val started = java.util.concurrent.CountDownLatch(1); val release = java.util.concurrent.CountDownLatch(1)
        val delegate = AtomicMediaSession(folder)
        val slow = object : MediaSessionStorage {
            override fun read() = delegate.read()
            override fun write(json: String?) { if(json=="first") { started.countDown(); check(release.await(10,java.util.concurrent.TimeUnit.SECONDS)) }; delegate.write(json) }
        }
        val scope = CoroutineScope(SupervisorJob()+Dispatchers.Main)
        lateinit var writer: MediaSessionWriter
        try {
            main { writer = MediaSessionWriter(scope,slow); writer.update(SessionSnapshot("first",setOf(first))) }
            assertTrue(withContext(Dispatchers.IO) { started.await(10,java.util.concurrent.TimeUnit.SECONDS) })
            main { writer.update(SessionSnapshot("next",setOf(next)),setOf(first)) }
            assertTrue(first.exists()); release.countDown()
            main { assertTrue(writer.flush()) }
            assertEquals("next",delegate.read()); assertFalse(first.exists()); assertTrue(next.exists())
        } finally { release.countDown(); scope.cancel(); next.delete(); first.delete(); File(folder,"session.json").delete(); folder.delete() }
    }

    @Test fun interruptedAtomicWriteRecoversPreviousCheckpointAndInvalidSourceDoesNotDisappear() = runBlocking<Unit> {
        val folder = File(context.cacheDir,"atomic-session-qa").apply { mkdirs() }
        val disk = AtomicMediaSession(folder); disk.write("previous")
        val atomic = AtomicFile(File(folder,"session.json")); atomic.startWrite().use { it.write("unfinished".toByteArray()) }
        assertEquals("previous",disk.read())
        val source = File(context.noBackupFilesDir,"photo-pdf/page-invalid.png").apply { parentFile!!.mkdirs(); writeText("invalid image") }
        val session = PhotoSession(listOf(SessionAsset("id",source.name,"content://original","bad.png",source.length())),2048)
        storage("photo-pdf").write(MediaSessionCodec.photos(session))
        lateinit var restored: PhotoPdfViewModel
        main { restored = own(PhotoPdfViewModel(application)) }; waitFor { !restored.state.value.busy }
        assertTrue(restored.sessionRestoreFailed); assertEquals(PhotoPdfMessage.IMPORT_ERROR,restored.state.value.message)
        assertTrue(source.exists()); assertNotNull(storage("photo-pdf").read())
        main { restored.reset(); assertTrue(restored.awaitSession()) }; source.delete()
        File(folder,"session.json").delete(); folder.delete()
    }

    @Test fun stalePickerProtectsOldOriginalsAfterResetAndCannotExportADifferentResult() = runBlocking<Unit> {
        val photoOriginal = original("fixtures","tiny.png"); val videoOriginal = original("videos","silent.mp4")
        val photoBytes = photoOriginal.readBytes(); val videoBytes = videoOriginal.readBytes()
        lateinit var photo: PhotoPdfViewModel; lateinit var video: VideoCompressionViewModel
        main { photo=own(PhotoPdfViewModel(application)); video=own(VideoCompressionViewModel(application)) }
        waitFor { !photo.state.value.busy && !video.state.value.busy }
        main { photo.reset(); video.reset(); photo.export(Uri.fromFile(photoOriginal),"old-output",listOf(Uri.fromFile(photoOriginal).toString())); video.export(Uri.fromFile(videoOriginal),"old-output",Uri.fromFile(videoOriginal).toString()) }
        assertEquals(PhotoPdfMessage.EXPORT_ERROR,photo.state.value.message); assertEquals(VideoMessage.EXPORT_ERROR,video.state.value.message)
        assertArrayEquals(photoBytes,photoOriginal.readBytes()); assertArrayEquals(videoBytes,videoOriginal.readBytes())
        main { assertTrue(photo.awaitSession()); assertTrue(video.awaitSession()) }; photoOriginal.delete(); videoOriginal.delete()
    }
}

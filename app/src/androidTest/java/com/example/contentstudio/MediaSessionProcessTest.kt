package com.example.contentstudio

import android.app.LocaleManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.LocaleList
import android.os.Process
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.example.contentstudio.media.*
import java.io.File
import org.junit.*
import org.junit.Assert.*

/** Run prepare -> external force-stop -> verify, as separate instrumentation processes. */
class MediaSessionProcessTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private var activity: ActivityScenario<MainActivity>? = null
    private lateinit var photo: PhotoPdfViewModel
    private lateinit var video: VideoCompressionViewModel
    private val qa get() = File(context.getExternalFilesDir(null),"qa").apply { mkdirs() }
    @After fun closeActivity() { activity?.close() }
    private fun launch() {
        context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags("en-US")
        activity = ActivityScenario.launch(MainActivity::class.java)
        activity!!.onActivity { photo=ViewModelProvider(it)[PhotoPdfViewModel::class.java] }
        compose.waitUntil(30_000) { !photo.state.value.busy }
    }
    private fun open(tag: String) {
        compose.onNode(hasTestTag("compact-home") or hasTestTag("wide-home-tools")).performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performClick()
    }
    private fun openVideo() {
        open("home-edit-video")
        activity!!.onActivity { video=ViewModelProvider(it)[VideoCompressionViewModel::class.java] }
        compose.waitUntil(30_000) { !video.state.value.busy }
    }
    private fun commit() {
        var saved = false
        activity!!.onActivity { photo.flushSession(); video.flushSession() }
        compose.waitUntil(30_000) { saved = photo.sessionStatus.value==SessionSaveStatus.SAVED && video.sessionStatus.value==SessionSaveStatus.SAVED; saved }
        assertTrue(saved)
    }
    private fun copy(authority: String,name: String):File = File(context.cacheDir,"process-original-$name").also { file ->
        context.contentResolver.openInputStream(Uri.parse("content://com.example.contentstudio.test.$authority/$name"))!!.use { input -> file.outputStream().use { input.copyTo(it) } }
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap=instrumentation.uiAutomation.takeScreenshot()
        File(qa,name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle()
    }
    @Test fun prepareSourcesAndSettings() {
        AtomicMediaSession(File(context.noBackupFilesDir,"video-compression")).write(null)
        AtomicMediaSession(File(context.noBackupFilesDir,"photo-pdf")).write(null)
        launch(); openVideo()
        val source=copy("videos","audio.mp4")
        val images=listOf(copy("fixtures","orientation-6.jpg"),copy("fixtures","alpha.webp"))
        activity!!.onActivity { video.load(Uri.fromFile(source)); photo.add(images.map(Uri::fromFile)) }
        compose.waitUntil(30_000) { !video.state.value.busy && !photo.state.value.busy }
        assertNotNull(video.state.value.source); assertEquals(2,photo.state.value.pages.size)
        activity!!.onActivity { video.setOptions(VideoQuality.HIGH,1920); video.setTrim(1000,4500); photo.setMaxEdge(4096); photo.move(photo.state.value.pages.last().id,-1) }
        commit()
        val expected=MediaSessionCodec.photos(AtomicMediaSession(File(context.noBackupFilesDir,"photo-pdf")).read()!!)
        File(qa,"session-prepared-pages.json").writeText(MediaSessionCodec.photos(expected))
        File(qa,"session-prepared-pid.txt").writeText(Process.myPid().toString())
        assertTrue(source.delete()); images.forEach { assertTrue(it.delete()) }
        video.state.value.source!!.file.setLastModified(1); photo.state.value.pages.forEach { it.file.setLastModified(1) }
        compose.onNodeWithTag("video-compression-back").performClick()
        open("home-photo-pdf")
        compose.onNodeWithTag("photo-pdf-list").performScrollToNode(hasTestTag("photo-pdf-edge-4096"))
        compose.onNodeWithTag("photo-pdf-edge-4096").assertIsSelected()
        compose.onNodeWithTag("photo-pdf-back").performClick()
    }
    @Test fun verifyAfterExternalProcessStopAndContinueBothTools() {
        assertNotEquals(File(qa,"session-prepared-pid.txt").readText().toInt(),Process.myPid())
        launch(); openVideo()
        assertEquals(VideoQuality.HIGH,video.state.value.quality); assertEquals(1920,video.state.value.maxEdge)
        assertEquals(VideoTrim(1000,4500),video.state.value.trim); assertNotNull(video.state.value.source)
        assertFalse(File(context.cacheDir,"process-original-audio.mp4").exists()); assertNull(video.state.value.result)
        compose.onNodeWithTag("video-quality-HIGH").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("video-edge-1920").performScrollTo().assertIsSelected()
        capture("session-video-restored.png")
        activity!!.onActivity { video.setOptions(VideoQuality.SMALL,640); video.preview() }
        compose.waitUntil(120_000) { !video.state.value.busy }
        val result=video.state.value.result
        assertNotNull("Restored source could not encode: ${video.state.value.message}",result)
        assertTrue(kotlin.math.abs(result!!.info.durationMs-3500)<300); assertTrue(result.info.hasAudio)
        compose.onNodeWithTag("video-compression-back").performClick()
        open("home-photo-pdf")
        val expected=MediaSessionCodec.photos(File(qa,"session-prepared-pages.json").readText())
        assertEquals(expected.assets.map { it.id },photo.state.value.pages.map { it.id })
        assertEquals(4096,photo.state.value.maxEdge); assertNull(photo.state.value.result)
        compose.onNodeWithTag("photo-pdf-list").performScrollToNode(hasTestTag("photo-pdf-edge-4096"))
        compose.onNodeWithTag("photo-pdf-edge-4096").assertIsSelected()
        capture("session-photos-restored.png")
        activity!!.onActivity { photo.generate() }
        compose.waitUntil(90_000) { !photo.state.value.busy }
        assertNotNull("Restored pages could not generate PDF: ${photo.state.value.message}",photo.state.value.result)
        assertEquals(2,photo.state.value.result!!.pages)
        activity!!.onActivity { video.reset(); photo.reset() }; commit()
        assertNull(AtomicMediaSession(File(context.noBackupFilesDir,"video-compression")).read())
        assertNull(AtomicMediaSession(File(context.noBackupFilesDir,"photo-pdf")).read())
        File(qa,"session-verified-pid.txt").writeText(Process.myPid().toString())
    }
}

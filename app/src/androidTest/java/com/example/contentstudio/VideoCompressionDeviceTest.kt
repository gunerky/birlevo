package com.example.contentstudio

import android.app.Activity
import android.app.Instrumentation
import android.app.LocaleManager
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.contentstudio.media.*
import java.io.File
import java.security.MessageDigest
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@androidx.annotation.OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class VideoCompressionDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var model: VideoCompressionViewModel
    private fun uri(name: String) = Uri.parse("content://com.example.contentstudio.test.videos/$name")
    private fun hash(uri: Uri): List<Byte> = context.contentResolver.openInputStream(uri)!!.use {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(65536)
        while (true) { val size = it.read(buffer); if (size < 0) break; digest.update(buffer, 0, size) }
        digest.digest().toList()
    }

    @Before fun setup() {
        if (Build.VERSION.SDK_INT >= 33) context.getSystemService(LocaleManager::class.java)
            .applicationLocales = LocaleList.forLanguageTags("en-US")
        else instrumentation.runOnMainSync {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en-US"))
        }
        context.contentResolver.delete(uri("export.mp4"), null, null)
        Intents.init()
    }
    @After fun teardown() {
        if (::model.isInitialized) instrumentation.runOnMainSync { model.reset() }
        if (::model.isInitialized) kotlinx.coroutines.runBlocking { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { model.awaitSession() } }
        scenario?.close()
        Intents.release()
    }
    private fun launch(entry: String = "Compress video") {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(15_000) { compose.onAllNodesWithText(entry).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(entry).performScrollTo().performClick()
        scenario!!.onActivity { model = ViewModelProvider(it)[VideoCompressionViewModel::class.java] }
    }
    private fun choose(name: String) {
        Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(Instrumentation.ActivityResult(
            Activity.RESULT_OK, Intent().setData(uri(name)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)))
        compose.onNodeWithTag("choose-compression-video").performScrollTo().performClick()
        compose.waitUntil(20_000) { !model.state.value.busy &&
            (model.state.value.source != null || model.state.value.message != null) }
        assertNotNull("Import failed: ${model.state.value.message}", model.state.value.source)
    }
    private fun preview() {
        compose.onNodeWithTag("process-video").performScrollTo().performClick()
        compose.waitUntil(120_000) { !model.state.value.busy &&
            (model.state.value.result != null || model.state.value.message != null) }
        assertNotNull("Encode failed: ${model.state.value.message}", model.state.value.result)
    }
    private fun small() {
        compose.onNodeWithTag("video-quality-SMALL").performScrollTo().performClick()
        compose.onNodeWithTag("video-edge-640").performScrollTo().performClick()
    }

    @Test fun selectCompressPlayRecreateExportPreservesVideoAndAudio() {
        val original = hash(uri("audio.mp4"))
        launch(); choose("audio.mp4"); small(); preview()
        val source = model.state.value.source!!
        val result = model.state.value.result!!
        assertEquals(640, result.info.width); assertEquals(360, result.info.height)
        assertTrue(result.bytes < source.bytes)
        assertTrue(result.info.hasAudio)
        assertTrue(kotlin.math.abs(result.info.durationMs - 6000) < 300)
        scenario!!.recreate()
        assertEquals(result.file, model.state.value.result!!.file)
        compose.onNodeWithTag("play-output-video").performScrollTo().performClick()
        var player: Player? = null
        onView(isAssignableFrom(PlayerView::class.java)).inRoot(isDialog()).check { view, error ->
            if (error != null) throw error
            player = (view as PlayerView).player
        }
        compose.waitUntil(15_000) {
            var ready = false
            instrumentation.runOnMainSync { ready = player!!.playbackState == Player.STATE_READY }
            ready
        }
        instrumentation.runOnMainSync { player!!.play() }
        compose.waitUntil(15_000) {
            var playing = false
            instrumentation.runOnMainSync {
                playing = player!!.currentPosition > 300 && player!!.videoSize.width > 0 && player!!.playerError == null
            }
            playing
        }
        compose.onNodeWithTag("close-video-player").performClick()
        Intents.intending(hasAction(Intent.ACTION_CREATE_DOCUMENT)).respondWith(
            Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri("export.mp4"))))
        compose.onNodeWithTag("export-video").performScrollTo().performClick()
        compose.waitUntil(15_000) { model.state.value.message == VideoMessage.EXPORTED }
        val exported = File(context.cacheDir, "video-verification.mp4")
        try {
            context.contentResolver.openInputStream(uri("export.mp4"))!!.use { input ->
                exported.outputStream().use { input.copyTo(it) }
            }
            assertEquals(result.bytes, exported.length())
            verifyMovingFrames(exported)
            assertTrue("Decoded audio must contain actual sound", decodedAudioHasSignal(exported))
        } finally { exported.delete() }
        assertEquals(original, hash(uri("audio.mp4")))
        Intents.intended(hasAction(Intent.ACTION_OPEN_DOCUMENT))
        Intents.intended(hasAction(Intent.ACTION_CREATE_DOCUMENT))
        val memory = android.os.Debug.MemoryInfo()
        android.os.Debug.getMemoryInfo(memory)
        android.util.Log.i("VideoCompressionMetrics", "source=${source.bytes} output=${result.bytes} encodeMs=${result.elapsedMs} pssKb=${memory.totalPss}")
        compose.onNodeWithTag("video-message").performScrollTo()
        val file = File(context.getExternalFilesDir(null), "qa/video-compression.png").apply { parentFile!!.mkdirs() }
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        file.outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }; screenshot.recycle()
        compose.onNodeWithTag("video-edge-1280").performScrollTo().performClick()
        assertNull(model.state.value.result)
        compose.onNodeWithTag("export-video").assertDoesNotExist()
    }

    @Test fun silentSmallVideoIsNotUpscaledOrGivenAudio() {
        launch(); choose("silent.mp4"); small(); preview()
        val result = model.state.value.result!!
        assertEquals(320, result.info.width); assertEquals(240, result.info.height)
        assertFalse(result.info.hasAudio)
        verifyMovingFrames(result.file)
    }

    @Test fun largerVideoIsBlockedUntilExactCopyIsConfirmedAndOriginalIsPreserved() {
        val original = hash(uri("low-bitrate.mp4"))
        launch(); choose("low-bitrate.mp4")
        compose.onNodeWithTag("video-size-risk").performScrollTo().assertIsDisplayed()
        preview()
        val result = model.state.value.result!!
        assertTrue(result.bytes > model.state.value.source!!.bytes)
        compose.onNodeWithTag("video-size-change").performScrollTo().assertTextContains("Size increase", substring = true)
        compose.onNodeWithTag("export-video").performScrollTo().assertIsNotEnabled()
        instrumentation.runOnMainSync { model.export(uri("export.mp4")) }
        compose.waitUntil(10_000) { !model.state.value.busy && model.state.value.message == VideoMessage.EXPORT_ERROR }
        assertThrows(java.io.FileNotFoundException::class.java) {
            context.contentResolver.openInputStream(uri("export.mp4"))?.close()
        }
        compose.onNodeWithTag("export-video-anyway").performScrollTo().performClick()
        compose.onNodeWithTag("export-video-keep-original").performClick()
        compose.onNodeWithTag("export-video-confirm").assertDoesNotExist()
        Intents.intending(hasAction(Intent.ACTION_CREATE_DOCUMENT)).respondWith(
            Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri("export.mp4"))))
        compose.onNodeWithTag("export-video-anyway").performScrollTo().performClick()
        scenario!!.recreate()
        compose.onNodeWithTag("export-video-confirm").performClick()
        compose.waitUntil(15_000) { model.state.value.message == VideoMessage.EXPORTED }
        assertEquals(hash(Uri.fromFile(result.file)), hash(uri("export.mp4")))
        val savedCopyHash = hash(uri("export.mp4"))
        assertEquals(original, hash(uri("low-bitrate.mp4")))
        compose.onNodeWithTag("video-quality-HIGH").performScrollTo().performClick()
        preview()
        assertFalse("Old result approval must not authorize the new file", model.confirmNonSmallerCopy(result.file))
        instrumentation.runOnMainSync { model.export(uri("export.mp4")) }
        compose.waitUntil(10_000) { !model.state.value.busy && model.state.value.message == VideoMessage.EXPORT_ERROR }
        assertEquals("Rejected export must not replace a previously saved copy", savedCopyHash, hash(uri("export.mp4")))
        assertEquals(original, hash(uri("low-bitrate.mp4")))
    }

    @Test fun largerTrimmedCopyCanStillBeSavedAsAnExplicitEdit() {
        val original = hash(uri("low-bitrate.mp4"))
        launch("Edit video"); choose("low-bitrate.mp4")
        instrumentation.runOnMainSync { model.setTrim(500, 2_500) }
        compose.onNodeWithTag("video-size-risk").assertDoesNotExist()
        preview()
        val result = model.state.value.result!!
        assertTrue(result.bytes > model.state.value.source!!.bytes)
        assertTrue(kotlin.math.abs(result.info.durationMs - 2_000) <= 300)
        compose.onNodeWithTag("export-video").performScrollTo().assertIsNotEnabled()
        Intents.intending(hasAction(Intent.ACTION_CREATE_DOCUMENT)).respondWith(
            Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri("export.mp4"))))
        compose.onNodeWithTag("export-video-anyway").performScrollTo().performClick()
        compose.onNodeWithTag("export-video-confirm").performClick()
        compose.waitUntil(15_000) { model.state.value.message == VideoMessage.EXPORTED }
        assertEquals(hash(Uri.fromFile(result.file)), hash(uri("export.mp4")))
        assertEquals(original, hash(uri("low-bitrate.mp4")))
    }

    @Test fun selectedSegmentPreviewsAndExportsWithAudioWithoutChangingSource() {
        val original = hash(uri("audio.mp4"))
        launch("Edit video"); choose("audio.mp4"); small()
        compose.onNodeWithTag("video-trim-start").performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(1_500f) }
        compose.onNodeWithTag("video-trim-end").performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(3_500f) }
        assertEquals(VideoTrim(1_500, 3_500), model.state.value.trim)
        compose.onNodeWithTag("preview-video-trim").performScrollTo().performClick()
        var player: Player? = null
        onView(isAssignableFrom(PlayerView::class.java)).inRoot(isDialog()).check { view, error ->
            if (error != null) throw error
            player = (view as PlayerView).player
        }
        compose.waitUntil(15_000) {
            var ready = false
            instrumentation.runOnMainSync { ready = player!!.playbackState == Player.STATE_READY }
            ready
        }
        var previewDuration = 0L
        instrumentation.runOnMainSync { previewDuration = player!!.duration }
        assertTrue("Preview must show only the selection", kotlin.math.abs(previewDuration - 2_000) <= 100)
        compose.onNodeWithTag("close-video-player").performClick()
        preview()
        val result = model.state.value.result!!
        assertTrue("Export duration must match selection", kotlin.math.abs(result.info.durationMs - 2_000) <= 300)
        assertTrue(result.info.hasAudio)
        verifyMovingFrames(result.file)
        assertTrue("Trimmed file should be smaller than source", result.bytes < model.state.value.source!!.bytes)
        Intents.intending(hasAction(Intent.ACTION_CREATE_DOCUMENT)).respondWith(
            Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri("export.mp4"))))
        compose.onNodeWithTag("export-video").performScrollTo().performClick()
        compose.waitUntil(15_000) { model.state.value.message == VideoMessage.EXPORTED }
        val saved = File(context.cacheDir, "trim-verification.mp4")
        try {
            context.contentResolver.openInputStream(uri("export.mp4"))!!.use { input ->
                saved.outputStream().use { input.copyTo(it) }
            }
            assertEquals(result.bytes, saved.length())
            verifyMovingFrames(saved)
        } finally { saved.delete() }
        assertEquals(original, hash(uri("audio.mp4")))
        instrumentation.runOnMainSync { model.setTrim(500, 3_500) }
        assertNull("Changing selection invalidates the old export", model.state.value.result)
    }

    @Test fun balancedAndHighProfilesProducePlayableHdOutputs() {
        launch(); choose("audio.mp4")
        var balancedBytes = 0L
        for (quality in listOf(VideoQuality.BALANCED, VideoQuality.HIGH)) {
            compose.onNodeWithTag("video-quality-${quality.name}").performScrollTo().performClick()
            preview()
            val result = model.state.value.result!!
            assertEquals(1280, result.info.width); assertEquals(720, result.info.height)
            assertTrue(result.info.hasAudio)
            verifyMovingFrames(result.file)
            if (quality == VideoQuality.BALANCED) balancedBytes = result.bytes
            else assertTrue("Higher bitrate produces a larger output for this fixture", result.bytes > balancedBytes)
            android.util.Log.i("VideoCompressionMetrics", "quality=$quality output=${result.bytes} encodeMs=${result.elapsedMs}")
        }
    }

    @Test fun portraitRotationIsPreserved() {
        val original = hash(uri("portrait.mp4"))
        launch(); choose("portrait.mp4")
        assertEquals(360, model.state.value.source!!.info.width)
        assertEquals(640, model.state.value.source!!.info.height)
        small(); preview()
        val result = model.state.value.result!!
        assertEquals(360, result.info.width); assertEquals(640, result.info.height)
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(result.file.path)
            val frame = retriever.getFrameAtTime(0)!!
            assertTrue("Portrait decoded frame", frame.height > frame.width)
            frame.recycle()
        } finally { retriever.release() }
        assertTrue(result.info.hasAudio)
        assertEquals(original, hash(uri("portrait.mp4")))
    }

    @Test fun cancellingEncodingRemovesPartialOutputAndAllowsRetry() {
        launch(); choose("cancel.mp4"); small()
        val source = model.state.value.source!!
        val folder = File(context.noBackupFilesDir, "video-compression")
        val before = folder.listFiles()!!.map { it.name }.toSet()
        instrumentation.runOnMainSync { model.preview() }
        compose.waitUntil(15_000) { model.state.value.busy &&
            folder.listFiles()!!.any { it.name !in before && it.length() > 0 } }
        instrumentation.runOnMainSync { model.cancel() }
        compose.waitUntil(15_000) { !model.state.value.busy }
        assertNull(model.state.value.result)
        assertEquals(source, model.state.value.source)
        assertEquals(before, folder.listFiles()!!.map { it.name }.toSet())
        instrumentation.runOnMainSync { model.load(uri("silent.mp4")) }
        compose.waitUntil(15_000) { !model.state.value.busy && model.state.value.source!!.name == "silent.mp4" }
        small(); preview()
    }

    @Test fun invalidInputShowsErrorWithoutExport() {
        launch()
        Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(Instrumentation.ActivityResult(
            Activity.RESULT_OK, Intent().setData(uri("invalid.mp4"))))
        compose.onNodeWithTag("choose-compression-video").performClick()
        compose.waitUntil(10_000) { model.state.value.message == VideoMessage.LOAD_ERROR }
        compose.onNodeWithTag("video-message").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("export-video").assertDoesNotExist()
    }

    @Test fun originalDestinationIsRejectedAndSaveFailureCanBeRetried() {
        val original = hash(uri("silent.mp4"))
        launch(); choose("silent.mp4"); small(); preview()
        instrumentation.runOnMainSync { model.export(uri("silent.mp4")) }
        compose.waitUntil(15_000) { model.state.value.message == VideoMessage.EXPORT_ERROR }
        assertEquals(original, hash(uri("silent.mp4")))
        instrumentation.runOnMainSync { model.export(uri("fail.mp4")) }
        compose.waitUntil(15_000) { !model.state.value.busy && model.state.value.message == VideoMessage.EXPORT_ERROR }
        instrumentation.runOnMainSync { model.export(uri("export.mp4")) }
        compose.waitUntil(15_000) { model.state.value.message == VideoMessage.EXPORTED }
        assertEquals(original, hash(uri("silent.mp4")))
    }

    private fun verifyMovingFrames(file: File) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.path)
            val first = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST)!!
            val later = retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST)!!
            assertFalse("Output must contain moving pictures", first.sameAs(later))
            first.recycle(); later.recycle()
        } finally { retriever.release() }
    }

    private fun decodedAudioHasSignal(file: File): Boolean {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(file.path)
            val track = (0 until extractor.trackCount).first {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)!!.startsWith("audio/")
            }
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec = decoder
            decoder.configure(format, null, null, 0); decoder.start()
            var inputDone = false
            val info = MediaCodec.BufferInfo()
            val deadline = android.os.SystemClock.elapsedRealtime() + 10_000
            while (android.os.SystemClock.elapsedRealtime() < deadline) {
                if (!inputDone) {
                    val inputIndex = decoder.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val buffer = decoder.getInputBuffer(inputIndex)!!
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outputIndex = decoder.dequeueOutputBuffer(info, 10_000)
                if (outputIndex >= 0) {
                    val buffer = decoder.getOutputBuffer(outputIndex)!!
                    var signal = false
                    for (index in info.offset until info.offset + info.size) {
                        if (buffer.get(index).toInt() != 0) { signal = true; break }
                    }
                    decoder.releaseOutputBuffer(outputIndex, false)
                    if (signal) return true
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return false
                }
            }
            return false
        } finally { codec?.release(); extractor.release() }
    }
}

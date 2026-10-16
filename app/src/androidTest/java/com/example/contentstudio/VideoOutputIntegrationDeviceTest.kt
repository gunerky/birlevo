package com.example.contentstudio

import android.app.Activity
import android.app.Application
import android.app.Instrumentation
import android.app.LocaleManager
import android.content.Intent
import android.net.Uri
import android.os.LocaleList
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.*
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.platform.app.InstrumentationRegistry
import com.example.contentstudio.audio.AudioToolViewModel
import com.example.contentstudio.media.*
import com.example.contentstudio.processing.*
import kotlinx.coroutines.*
import org.hamcrest.Matchers.allOf
import org.junit.*
import org.junit.Assert.*
import java.io.File
import kotlin.math.abs

@androidx.annotation.OptIn(UnstableApi::class)
class VideoOutputIntegrationDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private var activity: ActivityScenario<MainActivity>? = null
    private var model: VideoCompressionViewModel? = null
    private var audio: AudioToolViewModel? = null
    private fun uri(name: String) = Uri.parse("content://com.example.contentstudio.test.videos/$name")
    private fun bytes(uri: Uri) = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
    private suspend fun main(block: suspend () -> Unit) = withContext(Dispatchers.Main) { block() }
    private suspend fun waitFor(block: () -> Boolean) { withTimeout(120_000) { while (!block()) delay(25) } }

    @Before fun before() = runBlocking<Unit> {
        context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags("en-US")
        main { ProcessingJobs.get(context).awaitReady(); ProcessingKind.entries.forEach { ProcessingJobs.get(context).forget(it) } }
        AtomicMediaSession(File(context.noBackupFilesDir, "video-compression")).write(null)
        context.contentResolver.delete(uri("export.mp4"), null, null)
        context.contentResolver.delete(uri("export.webm"), null, null)
        Intents.init()
    }
    @After fun after() = runBlocking<Unit> {
        main { model?.reset(); audio?.reset(); model?.awaitSession(); audio?.awaitSession() }
        activity?.close(); Intents.release()
    }

    @Test fun productionHevcKeepsAudioSilentAndPortraitSources() = engineMatrix(VideoOutputFormat.MP4_HEVC,
        listOf("audio.mp4", "silent.mp4", "portrait.mp4"))
    @Test fun productionWebmHandlesOpusVorbisHevcAndPortraitSources() = engineMatrix(VideoOutputFormat.WEBM_VP8,
        listOf("audio.mp4", "silent.mp4", "portrait.mp4", "hevc.mp4", "phone.webm", "phone-vorbis.webm"))
    @Test fun productionVp9KeepsRequestedShapeSoundAndSource() = engineMatrix(VideoOutputFormat.WEBM_VP9,
        listOf("audio.mp4", "silent.mp4", "portrait.mp4", "vp9.webm", "vp9.mkv"))
    @Test fun vp9UiRestoresExportsAndHandsOwnedBytesToAudio() = uiRoundTrip(VideoOutputFormat.WEBM_VP9)
    @Test fun vp9HighBitDepthAndHdrAreRejectedWithoutChangingSourceOrWorkingFiles() = runBlocking<Unit> {
        val engine = VideoCompressor(context)
        val folder = File(context.noBackupFilesDir, "video-compression").apply { mkdirs() }
        for (name in listOf("vp9-10bit.webm", "vp9-hdr.webm")) {
            val before = folder.listFiles()!!.map { it.name }.toSet()
            val original = bytes(uri(name))
            val rejected = try { engine.importVideo(uri(name)); null } catch (error: MediaImportException) { error.issue }
            assertTrue("Expected explicit VP9 rejection: $name / $rejected", rejected in listOf(ImportIssue.VIDEO_CODEC, ImportIssue.VIDEO_HDR))
            assertEquals(before, folder.listFiles()!!.map { it.name }.toSet())
            assertArrayEquals(original, bytes(uri(name)))
            val direct = File.createTempFile("qa-vp9-frame-", ".webm", context.cacheDir)
            try {
                direct.writeBytes(original)
                var refused = false
                try {
                    VideoFrameExtractor(context).read(VideoFrameTarget(direct, 2000, name), 0, false).bitmap.recycle()
                } catch (_: IllegalArgumentException) { refused = true }
                assertTrue("Frame path must also reject unsupported VP9: $name", refused)
                assertArrayEquals(original, direct.readBytes())
            } finally { direct.delete() }
        }
    }
    @Test fun selectedSmallAndHighDimensionsNeverFallBack() {
        for (format in listOf(VideoOutputFormat.MP4_HEVC, VideoOutputFormat.WEBM_VP8, VideoOutputFormat.WEBM_VP9)) {
            engineMatrix(format, listOf("audio.mp4"), VideoQuality.SMALL, 640)
            engineMatrix(format, listOf("audio.mp4"), VideoQuality.HIGH, 1280)
        }
    }
    private fun engineMatrix(format: VideoOutputFormat, names: List<String>,
        quality: VideoQuality = VideoQuality.BALANCED, edge: Int = 640) = runBlocking<Unit> {
        val engine = VideoCompressor(context)
        for (name in names) {
            val original = bytes(uri(name)); val source = engine.importVideo(uri(name))
            var result: CompressedVideo? = null
            try {
                assertNull(engine.outputSupport(source.info, quality, edge)[format])
                val trim = VideoTrim(250, minOf(1750, source.info.durationMs))
                result = engine.compress(source, quality, edge, trim, format) {}
                val evidence = File(context.getExternalFilesDir("qa"), "vp9-codecs").apply { mkdirs() }
                result.file.copyTo(File(evidence, "${format.name}-${name.replace('.', '_')}-${quality.name}-$edge.${format.extension}"), overwrite = true)
                assertEquals(format.videoMime, result.info.videoMime)
                assertEquals(format.extension, result.file.extension)
                assertEquals(source.info.hasAudio, result.info.hasAudio)
                if (source.info.hasAudio) {
                    assertEquals(format.audioMime, result.info.audioMime)
                    assertEquals(source.info.audioChannels, result.info.audioChannels)
                    assertEquals(if (format.isWebm) 48000 else source.info.audioSampleRate, result.info.audioSampleRate)
                }
                val target = videoTarget(source.info.width, source.info.height, edge, quality)
                assertEquals(target.width, result.info.width); assertEquals(target.height, result.info.height)
                assertTrue(abs(result.info.durationMs - trim.durationMs) <= 300)
                val restored = engine.restoreOutput(result.file, result.bytes, result.elapsedMs, format)
                try { assertEquals(format, restored.format) } finally { restored.info.poster?.recycle() }
                val wrong = if (format == VideoOutputFormat.MP4_HEVC) VideoOutputFormat.MP4_AVC else VideoOutputFormat.MP4_HEVC
                assertThrows(IllegalArgumentException::class.java) { runBlocking { engine.restoreOutput(result.file, result.bytes, 0, wrong) } }
                val equal = source.copy(bytes = result.bytes)
                val actual = result
                assertThrows(IllegalArgumentException::class.java) { runBlocking { engine.export(equal, actual, uri("export.${format.extension}")) } }
                if (result.bytes >= source.bytes) assertThrows(IllegalArgumentException::class.java) {
                    runBlocking { engine.export(source, actual, uri("export.${format.extension}")) }
                }
                engine.export(source, result, uri("export.${format.extension}"), true)
                assertArrayEquals(result.file.readBytes(), bytes(uri("export.${format.extension}")))
                val outputs = MediaOutputs(context)
                assertThrows(IllegalArgumentException::class.java) {
                    runBlocking { outputs.stage(actual.file, format.mime, "output.${format.extension}", actual.bytes) }
                }
                val staged = outputs.stage(result.file, format.mime, "output.${format.extension}", result.bytes, true)
                assertEquals(format.mime, staged.shareIntent().type); assertEquals(format.mime, staged.viewIntent().type)
                assertEquals("output.${format.extension}", staged.name)
                assertArrayEquals(result.file.readBytes(), bytes(staged.uri))
                assertArrayEquals(original, bytes(source.originalUri)); assertArrayEquals(original, source.file.readBytes())
            } finally { result?.file?.delete(); result?.info?.poster?.recycle(); source.file.delete(); source.info.poster?.recycle() }
        }
    }

    @Test fun missingEncoderAudioAndOrientationAreExplicitAndKeepDefaultMp4Available() = runBlocking<Unit> {
        val report = android.media.MediaCodecList(android.media.MediaCodecList.REGULAR_CODECS).codecInfos
            .filter { it.isEncoder && "video/hevc" in it.supportedTypes }.joinToString("\n") { codec ->
                val caps = codec.getCapabilitiesForType("video/hevc")
                val base = android.media.MediaFormat.createVideoFormat("video/hevc", 640, 360)
                val checks = mutableListOf("base=${caps.isFormatSupported(base)}")
                base.setFloat(android.media.MediaFormat.KEY_FRAME_RATE, 30f); checks += "rate=${caps.isFormatSupported(base)}"
                base.setInteger(android.media.MediaFormat.KEY_BIT_RATE, 500000); checks += "bitrate=${caps.isFormatSupported(base)}"
                base.setInteger(android.media.MediaFormat.KEY_COLOR_FORMAT, android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                checks += "surface=${caps.isFormatSupported(base)}"
                val video = caps.videoCapabilities
                "${codec.name}: $checks; colors=${caps.colorFormats.toList()}; profiles=${caps.profileLevels.map { it.profile to it.level }}; VBR=${caps.encoderCapabilities.isBitrateModeSupported(1)}; " +
                    "align=${video.widthAlignment}x${video.heightAlignment}; width=${video.supportedWidths}; height=${video.supportedHeights}; bitrate=${video.bitrateRange}; fps=${video.supportedFrameRates}; size=${video.isSizeSupported(640,360)}; aligned=${video.isSizeSupported(640,384)}"
            }
        File(context.getExternalFilesDir("qa"), "video-output-capabilities.txt").writeText(report)
        val engine = VideoCompressor(context); val source = engine.importVideo(uri("audio.mp4"))
        try {
            val target = videoTarget(source.info.width, source.info.height, 640, VideoQuality.BALANCED)
            val absent = VideoOutputSupport { emptyList() }
            assertEquals(VideoOutputIssue.VIDEO_ENCODER, absent.issue(source.info, target, VideoOutputFormat.MP4_HEVC))
            assertEquals(VideoOutputIssue.VIDEO_ENCODER, absent.issue(source.info, target, VideoOutputFormat.WEBM_VP9))
            assertNull(absent.issue(source.info, target, VideoOutputFormat.MP4_AVC))
            assertEquals(VideoOutputIssue.ORIENTATION, engine.outputSupport(source.info.copy(rotationDegrees = 180), VideoQuality.BALANCED, 640)[VideoOutputFormat.WEBM_VP8])
            assertEquals(VideoOutputIssue.AUDIO_ENCODER, engine.outputSupport(source.info.copy(audioChannels = 100), VideoQuality.BALANCED, 640)[VideoOutputFormat.WEBM_VP8])
            val folder = source.file.parentFile!!; val before = folder.listFiles()!!.map { it.name }.toSet()
            val incompatible = source.copy(info = source.info.copy(rotationDegrees = 180))
            val failure = try { engine.compress(incompatible, VideoQuality.BALANCED, 640, VideoTrim(0, 1000), VideoOutputFormat.WEBM_VP8) {}; null }
                catch (error: VideoOutputUnavailable) { error }
            assertEquals(VideoOutputIssue.ORIENTATION, failure?.issue)
            assertEquals(before, folder.listFiles()!!.map { it.name }.toSet())
        } finally { source.file.delete(); source.info.poster?.recycle() }
    }

    @Test fun cancellingProductionTargetsDoesNotLeaveOutputFiles() = runBlocking<Unit> {
        val engine = VideoCompressor(context); val source = engine.importVideo(uri("cancel.mp4"))
        try {
            for (format in listOf(VideoOutputFormat.MP4_HEVC, VideoOutputFormat.WEBM_VP8, VideoOutputFormat.WEBM_VP9)) {
                val folder = source.file.parentFile!!; val before = folder.listFiles()!!.map { it.name }.toSet()
                val job = launch(Dispatchers.IO) { engine.compress(source, VideoQuality.HIGH, 1280, VideoTrim(0, source.info.durationMs), format) {}.let { it.file.delete(); it.info.poster?.recycle() } }
                waitFor { folder.listFiles()!!.any { it.name.startsWith("working-") && it.name !in before } }
                job.cancelAndJoin()
                assertEquals(before, folder.listFiles()!!.map { it.name }.toSet())
                assertArrayEquals(bytes(uri("cancel.mp4")), source.file.readBytes())
            }
        } finally { source.file.delete(); source.info.poster?.recycle() }
    }

    @Test fun hevcUiRestoresCompletedResultAndExportsCorrectMime() = uiRoundTrip(VideoOutputFormat.MP4_HEVC)
    @Test fun webmUiRestoresExportsAndHandsOwnedBytesToAudio() = uiRoundTrip(VideoOutputFormat.WEBM_VP8)
    private fun uiRoundTrip(format: VideoOutputFormat) {
        activity = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag("compact-home") or hasTestTag("wide-home-tools")).fetchSemanticsNodes().isNotEmpty() }
        compose.homeNode("Compress video").performScrollTo().performClick()
        activity!!.onActivity { model = ViewModelProvider(it)[VideoCompressionViewModel::class.java] }
        compose.waitUntil(15_000) { !model!!.state.value.busy }
        Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri("audio.mp4"))))
        compose.workspaceNode("choose-compression-video").performScrollTo().performClick()
        compose.waitUntil(20_000) { model!!.state.value.source != null && !model!!.state.value.busy }
        compose.workspaceNode("video-format-${format.name}").performScrollTo().performClick()
        instrumentation.runOnMainSync { model!!.setOptions(VideoQuality.SMALL, 640); model!!.setTrim(500, 2000) }
        compose.waitUntil(20_000) { model!!.state.value.outputSupport.containsKey(format) }
        compose.workspaceNode("video-format-${format.name}").performScrollTo()
        screenshot("video-options-${format.name}")
        compose.workspaceNode("process-video").performScrollTo().assertIsEnabled().performClick()
        compose.waitUntil(120_000) { !model!!.state.value.busy && (model!!.state.value.result != null || model!!.state.value.message != null) }
        val result = model!!.state.value.result ?: error("UI encoding failed: ${model!!.state.value.message}")
        assertEquals(format, result.format)
        activity!!.recreate(); assertEquals(format, model!!.state.value.format)
        val store = ViewModelStore(); var fresh: VideoCompressionViewModel? = null
        try {
            instrumentation.runOnMainSync { fresh = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(context.applicationContext as Application))[VideoCompressionViewModel::class.java] }
            compose.waitUntil(20_000) { fresh!!.state.value.result != null && !fresh!!.state.value.busy }
            assertEquals(format, fresh!!.state.value.format); assertEquals(result.file, fresh!!.state.value.result!!.file)
        } finally { instrumentation.runOnMainSync { store.clear() } }
        compose.workspaceNode("play-output-video").performScrollTo().performClick()
        var player: Player? = null
        onView(isAssignableFrom(PlayerView::class.java)).inRoot(isDialog()).check { view, error ->
            if (error != null) throw error; player = (view as PlayerView).player
        }
        compose.waitUntil(15_000) {
            var ready = false; instrumentation.runOnMainSync { ready = player!!.playbackState == Player.STATE_READY && player!!.playerError == null }; ready
        }
        instrumentation.runOnMainSync { player!!.play() }
        compose.waitUntil(15_000) {
            var ended = false; instrumentation.runOnMainSync { ended = player!!.playbackState == Player.STATE_ENDED }; ended
        }
        compose.workspaceNode("close-video-player").performClick()
        Intents.intending(hasAction(Intent.ACTION_CREATE_DOCUMENT)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri("export.${format.extension}"))))
        compose.workspaceNode("export-video").performScrollTo().performClick()
        compose.waitUntil(15_000) { model!!.state.value.message == VideoMessage.EXPORTED }
        Intents.intended(allOf(hasAction(Intent.ACTION_CREATE_DOCUMENT), hasType(format.mime), hasExtra(Intent.EXTRA_TITLE, "audio-edited.${format.extension}")))
        assertArrayEquals(result.file.readBytes(), bytes(uri("export.${format.extension}")))
        screenshot("video-${format.name}")
        if (format.isWebm) {
            val original = result.file.readBytes()
            compose.workspaceNode("add-output-to-audio").performScrollTo().performClick()
            compose.waitUntil(20_000) { compose.onAllNodesWithTag("audio-list").fetchSemanticsNodes().isNotEmpty() }
            activity!!.onActivity { audio = ViewModelProvider(it)[AudioToolViewModel::class.java] }
            assertArrayEquals(original, audio!!.state.value.source!!.file.readBytes())
            instrumentation.runOnMainSync { model!!.reset() }
            runBlocking { main { model!!.awaitSession() } }
            assertArrayEquals(original, audio!!.state.value.source!!.file.readBytes())
        }
    }

    private fun screenshot(name: String) {
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try { File(context.getExternalFilesDir("qa"), "$name.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        } } finally { bitmap.recycle() }
    }
}

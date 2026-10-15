package com.example.contentstudio

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.contentstudio.audio.*
import com.example.contentstudio.batch.*
import com.example.contentstudio.media.*
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BatchMediaDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun video(name: String) = Uri.parse("content://com.example.contentstudio.test.videos/$name")
    private fun audio(name: String) = Uri.parse("content://com.example.contentstudio.test.audio/$name")
    private fun hash(file: File) = file.inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(65536)
        while(true) { val n = input.read(buffer); if(n < 0) break; digest.update(buffer, 0, n) }
        digest.digest().joinToString("") { "%02x".format(it) }
    }
    private fun approvals(queue: BatchQueue) = queue.items.filter { it.needsConsent }.mapNotNull { it.copyKey }.toSet()
    private val evidence get() = File(context.getExternalFilesDir(null), "qa/batch-media").apply { mkdirs() }
    private fun asset(engine: BatchEngine, name: String) = File(File(engine.folder, "assets"), name)
    private suspend fun rejected(work: suspend () -> Unit) { try { work(); fail("Expected rejection") } catch (_: IllegalArgumentException) { } }
    private suspend fun saveEvidence(engine: BatchEngine, queue: BatchQueue, label: String): JSONArray {
        val result = engine.zip(queue.revision, approvals(queue))
        val archive = result.resolve(engine.folder)
        val rows = JSONArray()
        ZipFile(archive).use { zip ->
            val selected = queue.items.filter { it.output != null && it.included }
            assertEquals(selected.size, zip.size())
            selected.forEachIndexed { index, item ->
                val name = BatchPolicy.entryName(index, item.name, queue.options.extension)
                val entry = zip.getEntry(name); assertNotNull(entry)
                assertEquals(item.outputBytes, entry.size)
                val file = asset(engine, item.output!!)
                val expected = hash(file)
                val extracted = File(context.cacheDir, "batch-evidence-${queue.options.extension}")
                try {
                    zip.getInputStream(entry).use { input -> extracted.outputStream().use { input.copyTo(it) } }
                    assertEquals(expected, hash(extracted))
                } finally { extracted.delete() }
                rows.put(JSONObject().put("name", name).put("sha256", expected).put("source", item.name)
                    .put("sourceDurationMs", item.durationMs).put("sourceWidth", item.width).put("sourceHeight", item.height)
                    .put("sourceBytes", item.bytes).put("bytes", item.outputBytes))
            }
        }
        archive.copyTo(File(evidence, "$label.zip"), overwrite = true)
        return rows
    }
    @Test fun videoBatchPreservesAllFourTargetsFullDurationAudioAndRequestedProfiles() = runBlocking(Dispatchers.IO) {
        val engine = BatchEngine(context, BatchKind.VIDEO); engine.clear()
        val reports = JSONArray()
        try {
            for ((index, format) in VideoOutputFormat.entries.withIndex()) {
                engine.clear()
                var queue = engine.add(listOf(video("audio.mp4"), video("silent.mp4"), video("portrait.mp4")))
                val originals = queue.items.associate { it.id to hash(asset(engine, it.source)) }
                val options = queue.options.copy(videoFormat = format, videoQuality = VideoQuality.entries[index % 3],
                    videoEdge = listOf(640,1280,1920)[index % 3])
                queue = engine.options(options)
                val progress = mutableListOf<Int>()
                engine.convert(queue.revision, { it?.let(progress::add) }) { _, _ -> }
                queue = engine.read()
                assertTrue("$format: ${queue.items}", queue.items.all { it.output != null && it.issue == null })
                assertTrue(progress.isNotEmpty()); assertTrue(progress.all { it in 0..100 })
                if (queue.items.any { it.needsConsent }) rejected { engine.zip(queue.revision, emptySet()) }
                reports.put(JSONObject().put("zip", "video-${format.name}.zip").put("kind", "VIDEO").put("format", format.name)
                    .put("edge", options.videoEdge).put("quality", options.videoQuality.name)
                    .put("entries", saveEvidence(engine, queue, "video-${format.name}")))
                queue.items.forEach { assertEquals(originals[it.id], hash(asset(engine,it.source))) }
            }
            File(evidence,"video-manifest.json").writeText(JSONObject().put("outputs",reports).toString(2))
        } finally { engine.clear() }
    }
    @Test fun audioBatchPreservesAllFiveTargetsMonoStereoAndVideoSoundExtraction() = runBlocking(Dispatchers.IO) {
        val engine = BatchEngine(context, BatchKind.AUDIO); engine.clear(); val reports = JSONArray()
        try {
            for ((index, target) in AudioTarget.entries.withIndex()) {
                engine.clear()
                var queue = engine.add(listOf(audio("tone.wav"), audio("mono44100.wav"), video("audio.mp4")))
                val inputs = queue.items.associate { it.id to AudioEngine(context).inspect(asset(engine,it.source)) }
                val originals = queue.items.associate { it.id to hash(asset(engine,it.source)) }
                queue = engine.options(queue.options.copy(audioTarget = target, audioBitrate = AudioPolicy.bitrates[index % 3]))
                engine.convert(queue.revision) { _, _ -> }; queue = engine.read()
                assertTrue("$target: ${queue.items}", queue.items.all { it.output != null && it.issue == null })
                val entries = saveEvidence(engine, queue, "audio-${target.name}")
                queue.items.forEachIndexed { ordinal, item ->
                    val input = inputs.getValue(item.id)
                    val result = AudioEngine(context, File(engine.folder,"assets")).restoreOutput(asset(engine,item.output!!),item.outputBytes,0)
                    assertEquals(target.sampleRate(input.sampleRate), result.info.sampleRate); assertEquals(input.channels, result.info.channels)
                    assertTrue(kotlin.math.abs(input.durationMs-result.info.durationMs) < 200)
                    entries.getJSONObject(ordinal).put("sampleRate",target.sampleRate(input.sampleRate)).put("channels",input.channels)
                    assertEquals(originals[item.id],hash(asset(engine,item.source)))
                }
                reports.put(JSONObject().put("zip","audio-${target.name}.zip").put("kind","AUDIO").put("format",target.name)
                    .put("bitrate",queue.options.audioBitrate).put("entries",entries))
            }
            File(evidence,"audio-manifest.json").writeText(JSONObject().put("outputs",reports).toString(2))
        } finally { engine.clear() }
    }
    @Test fun interruptedVideoAndAudioResumeWithoutReencodingCompletedResults() = runBlocking(Dispatchers.IO) {
        for (kind in listOf(BatchKind.VIDEO,BatchKind.AUDIO)) {
            val engine=BatchEngine(context,kind);engine.clear()
            try {
                val queue=engine.add(if(kind==BatchKind.VIDEO) listOf(video("audio.mp4"),video("silent.mp4")) else listOf(audio("tone.wav"),audio("mono44100.wav")))
                try { engine.convert(queue.revision) { _, _ -> throw CancellationException("Interrupt after durable first result") };fail() } catch (_:CancellationException) { }
                val restored=BatchEngine(context,kind).read()
                assertNotNull(restored.items[0].output);assertNull(restored.items[1].output)
                val result=asset(engine,restored.items[0].output!!);val before=hash(result);val modified=result.lastModified()
                engine.convert(restored.revision) { _, _ -> }
                val complete=engine.read();assertEquals(restored.items[0],complete.items[0]);assertEquals(before,hash(result));assertEquals(modified,result.lastModified())
                assertTrue(complete.items.all { it.output!=null })
                try { engine.zip(complete.revision,approvals(complete)) { throw CancellationException("ZIP byte cancellation") };fail() } catch (_:CancellationException) { }
                assertFalse(engine.folder.listFiles()!!.any { it.name.endsWith(".part") || it.name.endsWith(".zip") })
                assertEquals(complete,engine.read())
            } finally {engine.clear()}
        }
    }
    @Test fun perFileOrientationAndMp3RateFailuresDoNotFallbackOrBlockSuccessfulPeers() = runBlocking(Dispatchers.IO) {
        val videoEngine=BatchEngine(context,BatchKind.VIDEO);val audioEngine=BatchEngine(context,BatchKind.AUDIO)
        videoEngine.clear();audioEngine.clear()
        try {
            var videos=videoEngine.add(listOf(video("audio.mp4"),video("batch-turn180.mp4")))
            videos=videoEngine.options(videos.options.copy(videoFormat=VideoOutputFormat.WEBM_VP8,videoEdge=640))
            videoEngine.convert(videos.revision) { _, _ -> };videos=videoEngine.read()
            assertNotNull(videos.items[0].output);assertNull(videos.items[1].output);assertEquals(VideoOutputIssue.ORIENTATION,videos.items[1].videoIssue)
            ZipFile(videoEngine.zip(videos.revision,approvals(videos)).resolve(videoEngine.folder)).use {assertEquals(1,it.size())}
            videos=videoEngine.options(videos.options.copy(videoFormat=VideoOutputFormat.MP4_AVC));videoEngine.convert(videos.revision) { _, _ -> }
            assertTrue(videoEngine.read().items.all {it.output!=null})
            var audios=audioEngine.add(listOf(audio("mono44100.wav"),audio("batch-192000.wav")))
            audios=audioEngine.options(audios.options.copy(audioTarget=AudioTarget.MP3))
            audioEngine.convert(audios.revision) { _, _ -> };audios=audioEngine.read()
            assertNotNull(audios.items[0].output);assertNull(audios.items[1].output);assertEquals(AudioOutputIssue.ENCODER,audios.items[1].audioIssue)
            ZipFile(audioEngine.zip(audios.revision,approvals(audios)).resolve(audioEngine.folder)).use {assertEquals(1,it.size())}
            audios=audioEngine.options(audios.options.copy(audioTarget=AudioTarget.WAV));audioEngine.convert(audios.revision) { _, _ -> }
            assertTrue(audioEngine.read().items.all {it.output!=null})
        } finally {videoEngine.clear();audioEngine.clear()}
    }
    @Test fun isolatedStoresAtomicImportsStaleProfilesAndOriginalUrisStayProtected() = runBlocking(Dispatchers.IO) {
        val engines=BatchKind.entries.associateWith {BatchEngine(context,it)};engines.values.forEach {it.clear()}
        try {
            val audioEngine=engines.getValue(BatchKind.AUDIO);val videoEngine=engines.getValue(BatchKind.VIDEO)
            var audios=audioEngine.add(listOf(audio("tone.wav")));val videos=videoEngine.add(listOf(video("audio.mp4")))
            val original=hash(asset(audioEngine,audios.items.single().source))
            try {audioEngine.add(listOf(audio("mono44100.wav"),audio("invalid.mp3")));fail()} catch (_:Exception) {}
            assertEquals(audios,audioEngine.read());assertEquals(videos,videoEngine.read());assertTrue(engines.getValue(BatchKind.IMAGE).read().items.isEmpty())
            rejected {audioEngine.options(BatchOptions(kind=BatchKind.VIDEO))}
            audios=audioEngine.options(audios.options.copy(audioTarget=AudioTarget.WAV));audioEngine.convert(audios.revision) { _, _ -> };audios=audioEngine.read()
            val zip=audioEngine.zip(audios.revision,approvals(audios))
            rejected {audioEngine.export(zip,audios.revision,approvals(audios),Uri.parse(audios.items.single().uri))}
            audioEngine.options(audios.options.copy(audioTarget=AudioTarget.FLAC))
            rejected {audioEngine.export(zip,audios.revision,approvals(audios),audio("export.wav"))}
            assertEquals(original,hash(asset(audioEngine,audios.items.single().source)));assertEquals(videos,videoEngine.read())
        } finally {engines.values.forEach {it.clear()}}
    }
}

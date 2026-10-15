package com.example.contentstudio

import android.app.LocaleManager
import android.net.Uri
import android.os.LocaleList
import android.os.Process
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.example.contentstudio.audio.AudioTarget
import com.example.contentstudio.batch.*
import com.example.contentstudio.media.*
import com.example.contentstudio.processing.*
import com.example.contentstudio.ui.resumeWork
import java.io.File
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*

/** prepare -> host force-stop -> verify, keeping all three independent queues on disk. */
class BatchMediaProcessTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private var scenario:ActivityScenario<MainActivity>?=null
    private lateinit var model:BatchViewModel
    private val marker get()=File(context.getExternalFilesDir(null),"qa/batch-media/process.json").apply {parentFile!!.mkdirs()}
    private fun main(work:()->Unit)=instrumentation.runOnMainSync(work)
    private fun idle(){compose.waitUntil(40_000){model.state.value.ready&&!model.state.value.busy}}
    @After fun close(){scenario?.close()}
    private fun launch(){
        context.getSystemService(LocaleManager::class.java).applicationLocales=LocaleList.forLanguageTags("en-US")
        scenario=ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(30_000){compose.onAllNodesWithTag("home-batch-tools").fetchSemanticsNodes().isNotEmpty()}
    }
    private fun get(kind:BatchKind){scenario!!.onActivity {model=when(kind){
        BatchKind.IMAGE->ViewModelProvider(it)[BatchImageViewModel::class.java]
        BatchKind.VIDEO->ViewModelProvider(it)[BatchVideoViewModel::class.java]
        BatchKind.AUDIO->ViewModelProvider(it)[BatchAudioViewModel::class.java]
    }};idle();assertEquals(kind,model.mediaKind)}
    private fun open(kind:BatchKind){
        compose.onNodeWithTag("home-batch-tools").performScrollTo().performClick()
        compose.onNodeWithTag(when(kind){BatchKind.IMAGE->"home-batch-images";BatchKind.VIDEO->"home-batch-video";BatchKind.AUDIO->"home-batch-audio"}).performClick()
        get(kind)
    }
    @Test fun prepareThreeQueuesWithCompletedArchives(){
        runBlocking {
            withContext(Dispatchers.Main){ProcessingJobs.get(context).awaitReady();BatchKind.entries.forEach {ProcessingJobs.get(context).forget(it.processing)}}
            withContext(Dispatchers.IO){BatchKind.entries.forEach {BatchEngine(context,it).clear()}}
        }
        launch();val entries=JSONArray()
        for(kind in BatchKind.entries){
            open(kind)
            val source=when(kind){BatchKind.IMAGE->"content://com.example.contentstudio.test.fixtures/tiny.png"
                BatchKind.VIDEO->"content://com.example.contentstudio.test.videos/audio.mp4"
                BatchKind.AUDIO->"content://com.example.contentstudio.test.audio/tone.wav"}
            main{model.add(listOf(Uri.parse(source)))};idle()
            main{model.options(model.state.value.batch.options.copy(format=ImageOutputFormat.PNG,videoFormat=VideoOutputFormat.WEBM_VP9,
                videoQuality=VideoQuality.SMALL,videoEdge=640,audioTarget=AudioTarget.MP3,audioBitrate=256000))};idle()
            main{model.convert()};idle();assertNotNull(model.state.value.batch.items.single().output)
            main{model.state.value.batch.items.filter {it.needsConsent}.forEach {model.confirmCopy(it.copyKey!!)};model.pack(model.state.value.batch.revision)};idle()
            assertNotNull(model.state.value.archive)
            entries.put(JSONObject().put("kind",kind.name).put("result",model.state.value.batch.items.single().output))
            compose.onNodeWithTag("batch-back").performClick()
        }
        marker.writeText(JSONObject().put("pid",Process.myPid()).put("queues",entries).toString())
    }
    @Test fun verifyProfilesLegacyImagesAndResultGuardsAcrossRealProcessRestart(){
        val saved=JSONObject(marker.readText());assertNotEquals(saved.getInt("pid"),Process.myPid());launch()
        val entries=saved.getJSONArray("queues")
        for(index in 0 until entries.length()){
            val record=entries.getJSONObject(index);val kind=BatchKind.valueOf(record.getString("kind"));open(kind)
            val restored=model.state.value
            assertEquals(record.getString("result"),restored.batch.items.single().output)
            assertTrue(restored.approved.isEmpty());assertNull(restored.archive)
            when(kind){BatchKind.IMAGE->assertEquals(ImageOutputFormat.PNG,restored.batch.options.format)
                BatchKind.VIDEO->{assertEquals(VideoOutputFormat.WEBM_VP9,restored.batch.options.videoFormat);assertEquals(640,restored.batch.options.videoEdge)}
                BatchKind.AUDIO->{assertEquals(AudioTarget.MP3,restored.batch.options.audioTarget);assertEquals(256000,restored.batch.options.audioBitrate)}}
            if(restored.batch.items.any {it.needsConsent}){main{model.pack(restored.batch.revision)};idle();assertNull(model.state.value.archive)}
            compose.onNodeWithTag("batch-back").performClick()
        }
        assertTrue(runBlocking {resumeWork(context).map {it.kind}.containsAll(BatchKind.entries.map {it.processing.folder})})
        val list=compose.onNode(hasTestTag("compact-home") or hasTestTag("wide-home-tools"))
        list.performScrollToNode(hasText(context.getString(R.string.resume_work)))
        if(compose.onAllNodesWithTag("home-all-work").fetchSemanticsNodes().isNotEmpty())compose.onNodeWithTag("home-all-work").performClick()
        compose.homeTool("resume-batch-audio").performClick();get(BatchKind.AUDIO)
        compose.onNodeWithTag("batch-back").performClick()
        for(kind in BatchKind.entries){open(kind);main{model.clear()};idle();assertTrue(model.state.value.batch.items.isEmpty());compose.onNodeWithTag("batch-back").performClick()}
    }
}

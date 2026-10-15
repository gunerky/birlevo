package com.example.contentstudio

import android.app.Activity
import android.app.Instrumentation
import android.app.LocaleManager
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.LocaleList
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.platform.app.InstrumentationRegistry
import com.example.contentstudio.audio.*
import com.example.contentstudio.batch.*
import com.example.contentstudio.processing.*
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*

class BatchMediaUiDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var model: BatchViewModel
    private fun uri(kind: BatchKind, name: String) = Uri.parse("content://com.example.contentstudio.test.${if(kind == BatchKind.VIDEO) "videos" else "audio"}/$name")
    @Before fun setup() {
        context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(
            InstrumentationRegistry.getArguments().getString("audit-locale", "en-US"))
        runBlocking {
            withContext(Dispatchers.Main) { ProcessingJobs.get(context).awaitReady(); BatchKind.entries.forEach {ProcessingJobs.get(context).forget(it.processing)} }
            withContext(Dispatchers.IO) { BatchKind.entries.forEach {BatchEngine(context,it).clear()} }
        }
        Intents.init()
    }
    @After fun cleanup() {
        runBlocking {withContext(Dispatchers.Main) {BatchKind.entries.forEach {ProcessingJobs.get(context).forget(it.processing)}}}
        scenario?.close();Intents.release()
        runBlocking(Dispatchers.IO) {BatchKind.entries.forEach {BatchEngine(context,it).clear()}}
    }
    private fun main(work: () -> Unit) = instrumentation.runOnMainSync(work)
    private fun idle() {compose.waitUntil(40_000) {model.state.value.ready && !model.state.value.busy}}
    private fun open(kind: BatchKind) {
        if(scenario == null) scenario=ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(30_000) {compose.onAllNodesWithTag("home-batch-tools").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("home-batch-tools").performScrollTo().performClick()
        compose.onNodeWithTag(if(kind==BatchKind.VIDEO) "home-batch-video" else "home-batch-audio").performClick()
        scenario!!.onActivity {
            model=if(kind==BatchKind.VIDEO) ViewModelProvider(it)[BatchVideoViewModel::class.java] else ViewModelProvider(it)[BatchAudioViewModel::class.java]
            val expected=InstrumentationRegistry.getArguments().getString("audit-locale","en-US")!!.substringBefore('-')
            assertEquals(expected,it.resources.configuration.locales[0].language)
        };idle();assertEquals(kind,model.mediaKind)
    }
    private fun row(tag: String): SemanticsNodeInteraction {
        compose.onNodeWithTag("batch-list").performScrollToNode(hasTestTag(tag));return compose.onNodeWithTag(tag)
    }
    private fun select(kind: BatchKind, names: List<String>) {
        val clips=ClipData.newRawUri("media",uri(kind,names.first())).apply {names.drop(1).forEach {addItem(ClipData.Item(uri(kind,it)))}}
        Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK,
            Intent().apply {clipData=clips;addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)}))
        row("batch-add").performClick()
        compose.waitUntil(30_000) {model.state.value.batch.items.size==names.size && !model.state.value.busy}
        val pick=Intents.getIntents().last {it.action==Intent.ACTION_OPEN_DOCUMENT}
        assertTrue(pick.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE,false))
    }
    private fun convert() {
        compose.onNodeWithTag("batch-convert").performClick()
        compose.waitUntil(60_000) {!model.state.value.busy && model.state.value.batch.items.all {it.output!=null || it.issue!=null}}
    }
    private fun closeOptions() {
        if(compose.onAllNodesWithTag("batch-panel-close").fetchSemanticsNodes().isNotEmpty())
            compose.onNodeWithTag("batch-panel-close").performClick()
        else compose.onNodeWithTag("batch-panel-output").performClick()
    }
    private fun approve() {model.state.value.batch.items.forEachIndexed {index,item -> if(item.included && item.needsConsent) {
        row("batch-allow-$index").performClick();compose.onNodeWithTag("batch-confirm-copy").performClick()
    }}}
    private fun pack() {
        compose.onNodeWithTag("batch-zip").assertIsEnabled().performClick()
        compose.onNodeWithTag("batch-confirm-zip").performClick()
        compose.waitUntil(30_000) {!model.state.value.busy && model.state.value.archive!=null}
    }
    private fun save(count: Int) {
        val destination=uri(BatchKind.AUDIO,"export.wav")
        Intents.intending(hasAction(Intent.ACTION_CREATE_DOCUMENT)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK,Intent().setData(destination)))
        compose.onNodeWithTag("batch-save").performClick()
        compose.waitUntil(20_000) {model.state.value.saved && !model.state.value.busy}
        row("batch-saved").assertTextEquals(context.getString(R.string.reader_saved))
        assertEquals("application/zip",Intents.getIntents().last {it.action==Intent.ACTION_CREATE_DOCUMENT}.type)
        val file=File(context.cacheDir,"batch-media-ui.zip")
        try {context.contentResolver.openInputStream(destination)!!.use {input -> file.outputStream().use {input.copyTo(it)}}
            ZipFile(file).use {assertEquals(count,it.size())}
        } finally {file.delete()}
    }
    private fun shot(label: String) {
        compose.waitForIdle()
        // The Dialog window's enter animation is outside Compose's test clock.
        instrumentation.uiAutomation.waitForIdle(500, 5000)
        val file=File(context.getExternalFilesDir(null),"qa/batch-media/$label.png").apply {parentFile!!.mkdirs()}
        val bitmap=instrumentation.uiAutomation.takeScreenshot()
        try {file.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}} finally {bitmap.recycle()}
    }
    @Test fun videoMultiSelectVp9ProfilesConsentZipSaveAndActivityRecreation() {
        open(BatchKind.VIDEO);select(BatchKind.VIDEO,listOf("audio.mp4","portrait.mp4"))
        compose.onNodeWithTag("batch-panel-output").performClick()
        compose.onNodeWithTag("batch-video-format-WEBM_VP9").performScrollTo().performClick();idle()
        compose.onNodeWithTag("batch-video-quality-HIGH").performScrollTo().performClick();idle()
        compose.onNodeWithTag("batch-video-edge-1920").performScrollTo().performClick();idle()
        closeOptions();convert()
        assertTrue(model.state.value.batch.items.all {it.output!=null});approve();pack();save(2)
        scenario!!.recreate();idle();assertNotNull(model.state.value.archive)
        assertEquals(1920,model.state.value.batch.options.videoEdge);shot("video-result")
    }
    @Test fun audioMp3BitrateSaveAndChangedProfileInvalidatesOnlyAudioResults() {
        open(BatchKind.AUDIO);select(BatchKind.AUDIO,listOf("tone.wav","mono44100.wav"))
        compose.onNodeWithTag("batch-panel-output").performClick()
        compose.onNodeWithTag("batch-audio-target-MP3").performScrollTo().performClick();idle()
        compose.onNodeWithTag("batch-audio-rate-64000").performScrollTo().performClick();idle()
        closeOptions();convert();approve();pack();save(2)
        val old=model.state.value.archive!!.resolve(BatchEngine(context,BatchKind.AUDIO).folder)
        main {model.options(model.state.value.batch.options.copy(audioBitrate=256000))};idle()
        assertTrue(model.state.value.batch.items.all {it.output==null});assertTrue(model.state.value.approved.isEmpty())
        assertNull(model.state.value.archive);assertFalse(old.exists());compose.onNodeWithTag("batch-zip").assertIsNotEnabled()
        val case=InstrumentationRegistry.getArguments().getString("audit-case","audio")!!
        shot(case)
        compose.onNodeWithTag("batch-panel-output").performClick();shot("$case-options")
        closeOptions()
    }
    @Test fun failedPeerShowsExactIssueAndReviewSavesOnlySuccessfulFile() {
        open(BatchKind.AUDIO);select(BatchKind.AUDIO,listOf("mono44100.wav","batch-192000.wav"))
        main {model.options(model.state.value.batch.options.copy(audioTarget=AudioTarget.MP3))};idle();convert()
        assertNotNull(model.state.value.batch.items[0].output);assertEquals(AudioOutputIssue.ENCODER,model.state.value.batch.items[1].audioIssue)
        row("batch-failed-1").assertIsDisplayed();approve();pack();save(1)
    }
    @Test fun sharedHeavyQueueContinuesAfterLeavingScreenAndRefusesClearingActiveWork() {
        open(BatchKind.VIDEO);select(BatchKind.VIDEO,listOf("audio.mp4","silent.mp4"))
        val jobs=ProcessingJobs.get(context)
        main {jobs.start(ProcessingKind.BATCH_AUDIO,"synthetic-blocker") {awaitCancellation()}}
        compose.waitUntil(10_000) {jobs.records.value[ProcessingKind.BATCH_AUDIO]?.status==ProcessingStatus.RUNNING}
        compose.onNodeWithTag("batch-convert").performClick()
        compose.waitUntil(10_000) {jobs.records.value[ProcessingKind.BATCH_VIDEO]?.status==ProcessingStatus.QUEUED}
        val store=ViewModelStore()
        try {
            var cold:BatchVideoViewModel?=null
            scenario!!.onActivity {cold=ViewModelProvider(store,ViewModelProvider.AndroidViewModelFactory(it.application))[BatchVideoViewModel::class.java]}
            val cleared=runBlocking {withContext(Dispatchers.Main) {withTimeout(2000) {cold!!.clearSavedWork()}}}
            assertFalse(cleared)
            compose.onNodeWithTag("batch-back").performClick()
            main {jobs.cancel(ProcessingKind.BATCH_AUDIO)}
            compose.waitUntil(60_000) {!model.state.value.busy && model.state.value.batch.items.all {it.output!=null}}
            open(BatchKind.VIDEO);assertTrue(model.state.value.batch.items.all {it.output!=null})
        } finally {main {store.clear();jobs.cancel(ProcessingKind.BATCH_AUDIO)}}
    }
}

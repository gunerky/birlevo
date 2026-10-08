package com.example.contentstudio

import android.app.Instrumentation
import android.app.LocaleManager
import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.LocaleList
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.platform.app.InstrumentationRegistry
import com.example.contentstudio.data.*
import com.example.contentstudio.media.*
import com.example.contentstudio.processing.*
import java.io.File
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*

class SlideshowUiDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation
        get() = InstrumentationRegistry.getInstrumentation()

    private val context
        get() = instrumentation.targetContext

    private var activity: ActivityScenario<MainActivity>? = null
    private var model: SlideshowViewModel? = null

    private fun fixture(name: String) =
        Uri.parse("content://com.example.contentstudio.test.fixtures/$name")

    @Before
    fun before() =
        runBlocking<Unit> {
            context.getSystemService(LocaleManager::class.java).applicationLocales =
                LocaleList.forLanguageTags(
                    InstrumentationRegistry.getArguments().getString("audit-locale", "en-US")
                )
            withContext(Dispatchers.Main) {
                ProcessingJobs.get(context).awaitReady()
                ProcessingJobs.get(context).forget(ProcessingKind.SLIDESHOW)
                ProcessingJobs.get(context).forget(ProcessingKind.VIDEO)
            }
            AtomicMediaSession(File(context.noBackupFilesDir, SlideshowEngine.FOLDER)).write(null)
            AtomicMediaSession(File(context.noBackupFilesDir, "video-compression")).write(null)
            ProjectRepository(context).save(emptyList())
            Intents.init()
        }

    @After
    fun after() =
        runBlocking<Unit> {
            activity?.onActivity { model?.cancel() }
            if (model != null) compose.waitUntil(30_000) { !model!!.state.value.busy }
            withContext(Dispatchers.Main) {
                model?.reset()
                model?.awaitSession()
                ProcessingJobs.get(context).forget(ProcessingKind.SLIDESHOW)
            }
            activity?.close()
            Intents.release()
            ProjectRepository(context).save(emptyList())
        }

    private fun launch() {
        activity = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(30_000) {
            compose.onAllNodesWithTag("home-new").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun newVideo() {
        compose.onNodeWithTag("home-new").performClick()
        compose.onNodeWithTag("home-slideshow").performScrollTo().performClick()
        activity!!.onActivity { model = ViewModelProvider(it)[SlideshowViewModel::class.java] }
        compose.waitUntil(30_000) { !model!!.state.value.busy }
    }

    private fun choose() {
        val clip = ClipData.newRawUri("images", fixture("orientation-6.jpg"))
        clip.addItem(ClipData.Item(fixture("alpha.webp")))
        Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT))
            .respondWith(
                Instrumentation.ActivityResult(
                    -1,
                    Intent().apply {
                        clipData = clip
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    },
                )
            )
        compose.onNodeWithTag("slideshow-choose-images").performClick()
        compose.waitUntil(30_000) {
            !model!!.state.value.busy && model!!.state.value.timeline.frames.size == 2
        }
    }

    private fun closePanel() {
        if (compose.onAllNodesWithTag("slideshow-panel-close").fetchSemanticsNodes().isNotEmpty())
            compose.onNodeWithTag("slideshow-panel-close").performClick()
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        instrumentation.waitForIdleSync()
        Thread.sleep(300)
        val args = InstrumentationRegistry.getArguments()
        val prefix =
            args.getString("audit-layout", "phone") + "-" + args.getString("audit-locale", "en-US")
        val folder = File(context.getExternalFilesDir(null), "qa/slideshow").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try {
            File(folder, "$prefix-$name.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun timedImagesPersistCreateSaveAndTransferWithTheReturnPathIntact() {
        launch()
        newVideo()
        choose()
        compose.onNodeWithTag("slideshow-panel-frames").performClick()
        compose.onNodeWithTag("slideshow-frame-duration").performTextReplacement("0.75")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("slideshow-apply-duration").performScrollTo().performClick()
        compose.waitUntil(30_000) { model!!.state.value.timeline.frames[0].durationMs == 750L }
        closePanel()
        compose.onNodeWithTag("slideshow-select-1").performScrollTo().performClick()
        compose.waitUntil(30_000) {
            model!!.state.value.selectedId == model!!.state.value.timeline.frames[1].id
        }
        compose.onNodeWithTag("slideshow-panel-frames").performClick()
        compose.onNodeWithTag("slideshow-frame-duration").performTextReplacement("1.25")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("slideshow-apply-duration").performScrollTo().performClick()
        compose.waitUntil(30_000) { model!!.state.value.timeline.frames[1].durationMs == 1250L }
        closePanel()
        compose.onNodeWithTag("slideshow-panel-output").performClick()
        compose.onNodeWithTag("slideshow-edge-640").performScrollTo().performClick()
        closePanel()
        activity!!.recreate()
        compose.waitUntil(30_000) { model!!.state.value.supportReady }
        assertEquals(2000L, model!!.state.value.timeline.durationMs)
        assertEquals(640, model!!.state.value.timeline.edge)
        compose
            .onNodeWithTag("slideshow-create")
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()
        compose.waitUntil(120_000) { !model!!.state.value.busy }
        assertNotNull(model!!.state.value.result)
        val result = model!!.state.value.result!!
        shot("result")
        compose.onNodeWithTag("slideshow-save").assertIsDisplayed()
        val destination = File(context.cacheDir, "slideshow-ui-save.mp4")
        Intents.intending(hasAction(Intent.ACTION_CREATE_DOCUMENT))
            .respondWith(
                Instrumentation.ActivityResult(-1, Intent().setData(Uri.fromFile(destination)))
            )
        compose.onNodeWithTag("slideshow-save").performClick()
        compose.waitUntil(30_000) {
            !model!!.state.value.busy && destination.length() == result.bytes
        }
        assertArrayEquals(result.file.readBytes(), destination.readBytes())
        destination.delete()
        compose.onNodeWithTag("slideshow-edit-output").performClick()
        compose.waitUntil(30_000) {
            compose.onAllNodesWithTag("video-compression-back").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("video-compression-back").performClick()
        compose.onNodeWithTag("slideshow-save").assertIsDisplayed()
        assertEquals(result.file, model!!.state.value.result!!.file)
        compose.onNodeWithTag("slideshow-panel-frames").performClick()
        compose.onNodeWithTag("slideshow-frame-duration").performTextReplacement("1.5")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("slideshow-apply-duration").performScrollTo().performClick()
        closePanel()
        compose.onNodeWithTag("slideshow-save").assertDoesNotExist()
        compose.onNodeWithTag("slideshow-create").assertIsDisplayed()
    }

    @Test
    fun presentationRequiresReplacementApprovalAndRetainsTheOriginalDraft() =
        runBlocking<Unit> {
            val engine = SlideshowEngine(context)
            val previous =
                withContext(Dispatchers.IO) {
                    engine.importImages(
                        listOf(fixture("orientation-6.jpg")),
                        SlideshowTimeline(),
                    ) {}
                }
            AtomicMediaSession(engine.folder).write(SlideshowCodec.encode(previous))
            val project =
                StudioProject(
                    "slides-video",
                    ProjectKind.PRESENTATION,
                    "Project video",
                    1,
                    listOf(
                        Slide("Heading", "Body", fixture("alpha.webp").toString(), id = "one"),
                        Slide("Second", "", id = "two"),
                    ),
                    theme = PresentationTheme.WARM,
                )
            ProjectRepository(context).save(listOf(project))
            launch()
            compose.projectNode(project.title).performScrollTo().performClick()
            compose.onNodeWithTag("presentation-more").performClick()
            compose.onNodeWithTag("presentation-to-video").performClick()
            activity!!.onActivity { model = ViewModelProvider(it)[SlideshowViewModel::class.java] }
            compose.waitUntil(30_000) { !model!!.state.value.busy }
            compose.onNodeWithTag("slideshow-use-presentation").assertIsDisplayed()
            assertEquals(previous, model!!.state.value.timeline)
            compose.onNodeWithText("Cancel").performClick()
            compose.onNodeWithTag("slideshow-back").performClick()
            compose.onNodeWithTag("presentation-more").performClick()
            compose.onNodeWithTag("presentation-to-video").performClick()
            compose.onNodeWithTag("slideshow-use-presentation").performClick()
            compose.waitUntil(30_000) {
                !model!!.state.value.busy && model!!.state.value.timeline.presentation
            }
            assertEquals(project.theme, model!!.state.value.timeline.theme)
            assertEquals(
                listOf("Heading", "Second"),
                model!!.state.value.timeline.frames.map { it.slide!!.heading },
            )
            withContext(Dispatchers.Main) { assertTrue(model!!.awaitSession()) }
            assertTrue(previous.assets.all { !File(engine.folder, it.file).exists() })
            compose.onNodeWithTag("slideshow-preview").assertIsDisplayed()
            shot("presentation")
            compose.onNodeWithTag("slideshow-back").performClick()
            var draft: StudioProject? = null
            activity!!.onActivity {
                draft = ViewModelProvider(it)[ProjectEditorViewModel::class.java].state.value.draft
            }
            assertEquals(project.slides, draft!!.slides)
            assertEquals(project.theme, draft!!.theme)
        }

    @Test
    fun largerProcessedImageRequiresApprovalBeforeOwnedVideoTransfer() {
        launch()
        Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT))
            .respondWith(
                Instrumentation.ActivityResult(-1, Intent().setData(fixture("orientation-6.jpg")))
            )
        compose.onNodeWithTag("compress-image-action").performClick()
        compose.onNodeWithTag("choose-image").performClick()
        var image: ImageCompressionViewModel? = null
        activity!!.onActivity {
            image = ViewModelProvider(it)[ImageCompressionViewModel::class.java]
            model = ViewModelProvider(it)[SlideshowViewModel::class.java]
        }
        compose.waitUntil(30_000) {
            !image!!.state.value.busy &&
                image!!.state.value.source != null &&
                !model!!.state.value.busy
        }
        compose.onNodeWithTag("compression-panel-output").performClick()
        compose.onNodeWithTag("image-format-BMP").performScrollTo().performClick()
        compose.onNodeWithTag("compression-panel-close").performClick()
        compose.onNodeWithTag("image-preview").performClick()
        compose.waitUntil(30_000) { !image!!.state.value.busy }
        val output = image!!.state.value.result!!
        assertTrue(output.bytes >= image!!.state.value.source!!.bytes)
        compose.onNodeWithTag("output-more").performClick()
        compose.onNodeWithTag("add-output-to-slideshow").performScrollTo().performClick()
        assertTrue(model!!.state.value.timeline.frames.isEmpty())
        compose.onNodeWithTag("confirm-output-use").performClick()
        compose.waitUntil(30_000) {
            !model!!.state.value.busy && model!!.state.value.timeline.frames.size == 1
        }
        val file = SlideshowEngine(context).files(model!!.state.value.timeline).single()
        assertArrayEquals(output.file.readBytes(), file.readBytes())
        assertNotEquals(output.file.canonicalFile, file.canonicalFile)
        compose.waitUntil(30_000) {
            compose.onAllNodesWithTag("slideshow-back").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("slideshow-back").performClick()
        assertEquals(output.file, image!!.state.value.result!!.file)
        assertTrue(output.file.exists())
    }

    @Test
    fun adaptivePanelsKeepCreateReachableWithLargeTextAndRtl() {
        launch()
        newVideo()
        choose()
        compose.onNodeWithTag("slideshow-panel-output").performClick()
        compose.onNodeWithTag("slideshow-edge-640").performScrollTo().performClick()
        compose.waitUntil(30_000) { model!!.state.value.supportReady }
        compose.onNodeWithTag("slideshow-create").assertIsDisplayed()
        compose.onNodeWithTag("slideshow-edge-640").assertIsDisplayed()
        shot("inspector")
    }
}

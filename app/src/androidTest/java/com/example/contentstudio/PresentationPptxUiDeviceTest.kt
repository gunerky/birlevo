package com.example.contentstudio

import android.app.Application
import android.app.LocaleManager
import android.graphics.Bitmap
import android.os.LocaleList
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.example.contentstudio.data.*
import com.example.contentstudio.media.*
import com.example.contentstudio.processing.*
import java.io.File
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*

class PresentationPptxUiDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation
        get() = InstrumentationRegistry.getInstrumentation()

    private val context
        get() = instrumentation.targetContext

    private var scenario: ActivityScenario<MainActivity>? = null
    private var model: PresentationPptxViewModel? = null
    private val project =
        StudioProject(
            "pptx-ui",
            ProjectKind.PRESENTATION,
            "PPTX workspace",
            1,
            listOf(
                Slide(
                    "First slide",
                    "Appearance preserved",
                    id = "one",
                    notes = "Private first notes",
                ),
                Slide(
                    "Second slide",
                    "Settings remain available",
                    id = "two",
                    notes = "Private second notes",
                ),
            ),
        )

    @Before
    fun before() {
        runBlocking {
            withContext(Dispatchers.Main) {
                val jobs = ProcessingJobs.get(context.applicationContext as Application)
                jobs.awaitReady()
                jobs.forget(ProcessingKind.PRESENTATION_PPTX)
            }
        }
        AtomicMediaSession(PresentationPptxEngine(context).folder).write(null)
        context.getSystemService(LocaleManager::class.java).applicationLocales =
            LocaleList.forLanguageTags(
                InstrumentationRegistry.getArguments().getString("audit-locale", "en-US")
            )
        ProjectRepository(context).save(listOf(project))
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.projectNode(project.title).performScrollTo().performClick()
        if (compose.onAllNodesWithTag("project-panel-close").fetchSemanticsNodes().isNotEmpty())
            compose.onNodeWithTag("project-panel-close").performClick()
        compose.onNodeWithTag("presentation-more").performScrollTo().performClick()
        compose.onNodeWithTag("presentation-pptx").performClick()
        scenario!!.onActivity {
            model = ViewModelProvider(it)[PresentationPptxViewModel::class.java]
        }
        compose.waitUntil(10000) { model?.state?.value?.busy == false }
    }

    @After
    fun after() {
        scenario?.close()
        runBlocking {
            withContext(Dispatchers.Main) {
                ProcessingJobs.get(context.applicationContext as Application)
                    .forget(ProcessingKind.PRESENTATION_PPTX)
            }
        }
        ProjectRepository(context).save(emptyList())
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        instrumentation.waitForIdleSync()
        val args = InstrumentationRegistry.getArguments()
        val folder = File(context.getExternalFilesDir(null), "qa/pptx-export").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try {
            File(
                    folder,
                    "${args.getString("audit-layout", "phone")}-${args.getString("audit-locale", "en-US")}-$name.png",
                )
                .outputStream()
                .use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun settingsNotesDisclosureAndRecreationPreserveTheSelection() {
        compose.onNodeWithTag("pptx-loss").assertExists()
        assertFalse(model!!.state.value.options.includeNotes)
        compose.onNodeWithTag("pptx-panel-output").performClick()
        compose.onNodeWithTag("pptx-edge-1024").performScrollTo().performClick()
        compose.onNodeWithTag("pptx-notes").performScrollTo().performClick()
        assertEquals(PptxOptions(1024, true), model!!.state.value.options)
        shot("settings")
        scenario!!.recreate()
        scenario!!.onActivity {
            model = ViewModelProvider(it)[PresentationPptxViewModel::class.java]
        }
        compose.waitUntil(10000) {
            model!!.state.value.project != null && !model!!.state.value.busy
        }
        assertEquals(PptxOptions(1024, true), model!!.state.value.options)
        if (compose.onAllNodesWithTag("pptx-panel-close").fetchSemanticsNodes().isNotEmpty())
            compose.onNodeWithTag("pptx-panel-close").performClick()
        compose.onNodeWithTag("pptx-create").assertExists()
    }

    @Test
    fun unsupportedScriptKeepsAppearanceAvailableAndExplainsEditableRestriction() {
        scenario!!.close()
        ProjectRepository(context).save(listOf(project.copy(slides=listOf(Slide("العربية","مرحبا")))))
        scenario=ActivityScenario.launch(MainActivity::class.java)
        compose.projectNode(project.title).performScrollTo().performClick()
        compose.onNodeWithTag("presentation-more").performScrollTo().performClick()
        compose.onNodeWithTag("presentation-pptx").performClick()
        compose.onNodeWithTag("pptx-panel-output").performClick()
        compose.onNodeWithTag("pptx-mode-EDITABLE").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("pptx-mode-KEEP_APPEARANCE").assertIsEnabled()
        compose.onNodeWithTag("pptx-editable-unsupported").performScrollTo().assertIsDisplayed()
        shot("editable-restriction")
    }

    @Test
    fun editableModeRestoresAndSwitchingModesInvalidatesTheResult() {
        compose.onNodeWithTag("pptx-panel-output").performClick()
        compose.onNodeWithTag("pptx-mode-EDITABLE").performScrollTo().performClick()
        assertEquals(PptxMode.EDITABLE,model!!.state.value.options.mode)
        shot("editable-settings")
        scenario!!.recreate()
        scenario!!.onActivity{model=ViewModelProvider(it)[PresentationPptxViewModel::class.java]}
        compose.waitUntil(10000){!model!!.state.value.busy && model!!.state.value.project!=null}
        assertEquals(PptxMode.EDITABLE,model!!.state.value.options.mode)
        if(compose.onAllNodesWithTag("pptx-panel-close").fetchSemanticsNodes().isNotEmpty())
            compose.onNodeWithTag("pptx-panel-close").performClick()
        else compose.onNodeWithTag("pptx-panel-output").performClick()
        compose.onNodeWithTag("pptx-create").performClick()
        compose.waitUntil(30000){!model!!.state.value.busy && model!!.state.value.result!=null && model!!.state.value.preview!=null}
        assertEquals(PptxMode.EDITABLE,model!!.state.value.result!!.mode)
        shot("editable-result")
        compose.onNodeWithTag("pptx-panel-output").performClick()
        compose.onNodeWithTag("pptx-mode-KEEP_APPEARANCE").performScrollTo().performClick()
        assertNull(model!!.state.value.result)
        assertEquals(PptxMode.KEEP_APPEARANCE,model!!.state.value.options.mode)
    }

    @Test
    fun generatedResultHasActualPreviewSizeAndSaveAndOptionsInvalidateIt() {
        compose.onNodeWithTag("pptx-create").performClick()
        compose.waitUntil(30000) {
            model!!.state.value.result != null &&
                model!!.state.value.preview != null &&
                !model!!.state.value.busy
        }
        compose.onNodeWithTag("pptx-list").performScrollToNode(hasTestTag("pptx-preview"))
        compose.onNodeWithTag("pptx-preview").assertExists()
        compose.onNodeWithTag("pptx-size").assertExists()
        compose.onNodeWithTag("pptx-save").assertExists()
        shot("result")
        compose.onNodeWithTag("pptx-select-1").performScrollTo().performClick()
        compose.waitUntil(10000) {
            model!!.state.value.selected == 1 && model!!.state.value.preview != null
        }
        compose.onNodeWithTag("pptx-panel-output").performClick()
        compose.onNodeWithTag("pptx-edge-4096").performScrollTo().performClick()
        assertNull(model!!.state.value.result)
        if (compose.onAllNodesWithTag("pptx-panel-close").fetchSemanticsNodes().isNotEmpty())
            compose.onNodeWithTag("pptx-panel-close").performClick()
        else compose.onNodeWithTag("pptx-panel-output").performClick()
        compose.onNodeWithTag("pptx-create").assertExists()
        compose.onAllNodesWithTag("pptx-save").assertCountEquals(0)
    }
}

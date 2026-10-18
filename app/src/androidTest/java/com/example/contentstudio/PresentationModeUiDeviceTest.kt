package com.example.contentstudio

import android.app.Instrumentation
import android.app.LocaleManager
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.LocaleList
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.*
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.platform.app.InstrumentationRegistry
import com.example.contentstudio.data.*
import com.example.contentstudio.media.*
import java.io.File
import org.junit.*
import org.junit.Assert.*

class PresentationModeUiDeviceTest {
    @get:Rule(order = 0) val compose = createEmptyComposeRule()
    @get:Rule(order = 1) val residentAdvertising = ResidentAdvertisingRule(compose)
    private val instrumentation
        get() = InstrumentationRegistry.getInstrumentation()

    private val context
        get() = instrumentation.targetContext

    private var scenario: ActivityScenario<MainActivity>? = null
    private var player: PresentationPlayerViewModel? = null
    private var images: PresentationImageViewModel? = null
    private val project =
        StudioProject(
            "presentation-mode-ui",
            ProjectKind.PRESENTATION,
            "My presentation",
            1,
            listOf(
                Slide(
                    "First slide",
                    "Preview, then present",
                    id = "first",
                    notes = "Private first notes",
                ),
                Slide("Second slide", "Another page", id = "second", notes = "Private second notes"),
            ),
        )

    @Before
    fun before() {
        if (android.os.Build.VERSION.SDK_INT >= 33) context.getSystemService(LocaleManager::class.java).applicationLocales =
            LocaleList.forLanguageTags(
                InstrumentationRegistry.getArguments().getString("audit-locale", "en-US")
            )
        ProjectRepository(context).save(listOf(project))
        Intents.init()
    }

    @After
    fun after() {
        scenario?.onActivity {
            player?.leave()
            images?.leave()
        }
        scenario?.close()
        Intents.release()
        ProjectRepository(context).save(emptyList())
    }

    private fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.projectNode(project.title).performScrollTo().performClick()
    }

    private fun action(tag: String) {
        if (compose.onAllNodesWithTag("project-panel-close").fetchSemanticsNodes().isNotEmpty())
            compose.onNodeWithTag("project-panel-close").performClick()
        compose.onNodeWithTag("presentation-more").performScrollTo().performClick()
        compose.onNodeWithTag(tag).performClick()
        if (tag == "presentation-present") dismissSystemFullscreenHint()
        scenario!!.onActivity {
            player = ViewModelProvider(it)[PresentationPlayerViewModel::class.java]
            images = ViewModelProvider(it)[PresentationImageViewModel::class.java]
        }
    }

    private fun dismissSystemFullscreenHint() {
        // Android shows this system tutorial on the first immersive entry. Dismiss it as a user
        // would; never change system settings or suppress it in the production application.
        val end = android.os.SystemClock.elapsedRealtime() + 2500
        while (android.os.SystemClock.elapsedRealtime() < end) {
            val button =
                instrumentation.uiAutomation.rootInActiveWindow
                    ?.findAccessibilityNodeInfosByText("Got it")
                    ?.firstOrNull()
            if (button != null) {
                assertTrue(button.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                instrumentation.waitForIdleSync()
                return
            }
            Thread.sleep(50)
        }
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        instrumentation.waitForIdleSync()
        val args = InstrumentationRegistry.getArguments()
        val folder =
            File(context.getExternalFilesDir(null), "qa/presentation-mode").apply { mkdirs() }
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
    fun notesSaveAndFullscreenTimerNavigationRestoreTheEditorAndWindow() {
        launch()
        compose.onNodeWithTag("project-panel-notes").performClick()
        compose.onNodeWithTag("slide-notes-0").performTextReplacement("Revised notes 日本語 العربية")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        action("presentation-present")
        compose.waitUntil(30000) {
            !player!!.state.value.busy && player!!.state.value.preview != null
        }
        compose.onNodeWithTag("present-notes-toggle").performClick()
        compose.onNodeWithTag("present-notes").assertTextEquals("Revised notes 日本語 العربية")
        compose.onNodeWithTag("present-next").performClick()
        compose.waitUntil(30000) { !player!!.state.value.busy && player!!.state.value.index == 1 }
        compose.onNodeWithTag("present-notes").assertTextEquals("Private second notes")
        compose.onNodeWithTag("present-timer-toggle").performClick()
        compose.waitUntil(15000) { player!!.state.value.elapsedMs >= 700 }
        scenario!!.moveToState(Lifecycle.State.CREATED)
        assertFalse(player!!.state.value.running)
        val paused = player!!.state.value.elapsedMs
        scenario!!.moveToState(Lifecycle.State.RESUMED)
        context.startActivity(
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        )
        scenario!!.recreate()
        dismissSystemFullscreenHint()
        compose.waitUntil(15000) {
            var focused = false
            scenario!!.onActivity { focused = it.hasWindowFocus() }
            focused
        }
        compose.waitUntil(30000) {
            compose.onAllNodesWithTag("present-page").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(1, player!!.state.value.index)
        assertFalse(player!!.state.value.running)
        assertEquals(paused, player!!.state.value.elapsedMs)
        compose.onNodeWithTag("present-timer-reset").performClick()
        compose.onNodeWithTag("present-confirm-reset").performClick()
        assertEquals(0L, player!!.state.value.elapsedMs)
        shot("notes")
        compose.onNodeWithTag("present-hide-controls").performClick()
        compose.onNodeWithTag("present-next").assertDoesNotExist()
        shot("fullscreen")
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithTag("present-close").assertIsDisplayed().performClick()
        compose.onNodeWithTag("presentation-more").assertExists()
        scenario!!.onActivity {
            assertEquals(
                0,
                it.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            )
        }
        compose.waitUntil(15000) {
            ProjectRepository(context).load().single().slides[0].notes ==
                "Revised notes 日本語 العربية"
        }
        assertEquals(
            project.slides.map { it.id },
            ProjectRepository(context).load().single().slides.map { it.id },
        )
    }

    @Test
    fun slideImageCreatesSavesTransfersAndInvalidatesWithoutLosingDraft() {
        launch()
        action("presentation-images")
        compose.onNodeWithTag("slide-image-panel-output").performClick()
        compose.onNodeWithTag("slide-image-edge-1024").performScrollTo().performClick()
        if (compose.onAllNodesWithTag("slide-image-panel-close").fetchSemanticsNodes().isNotEmpty())
            compose.onNodeWithTag("slide-image-panel-close").performClick()
        compose.onNodeWithTag("slide-image-create").assertIsDisplayed().performClick()
        compose.waitUntil(30000) {
            !images!!.state.value.busy && images!!.state.value.result != null
        }
        val result = images!!.state.value.result!!
        val destination = File(context.cacheDir, "slide-image-ui-save.png")
        Intents.intending(hasAction(Intent.ACTION_CREATE_DOCUMENT))
            .respondWith(
                Instrumentation.ActivityResult(-1, Intent().setData(Uri.fromFile(destination)))
            )
        compose.onNodeWithTag("slide-image-save").performClick()
        compose.waitUntil(30000) {
            !images!!.state.value.busy && destination.length() == result.bytes
        }
        assertArrayEquals(result.file.readBytes(), destination.readBytes())
        destination.delete()
        shot("result")
        compose.onNodeWithTag("slide-image-to-image").performScrollTo().performClick()
        compose.waitUntil(30000) {
            compose.onAllNodesWithTag("compression-back").fetchSemanticsNodes().isNotEmpty()
        }
        compose.workspaceNode("compression-back").performClick()
        compose.onNodeWithTag("slide-image-save").assertExists()
        assertEquals(result.file, images!!.state.value.result!!.file)
        compose.onNodeWithTag("slide-image-select-1").performScrollTo().performClick()
        compose.onNodeWithTag("slide-image-save").assertDoesNotExist()
        compose.onNodeWithTag("slide-image-create").assertExists()
        scenario!!.recreate()
        assertEquals("second", images!!.state.value.slideId)
        assertEquals(1024, images!!.state.value.options.edge)
        compose.onNodeWithTag("slide-image-back").performClick()
        compose.onNodeWithTag("presentation-more").assertExists()
        assertEquals(
            project.slides.map { it.notes },
            ProjectRepository(context).load().single().slides.map { it.notes },
        )
    }

    @Test
    fun adaptiveControlsKeepSlideActionsReachable() {
        launch()
        action("presentation-present")
        compose.waitUntil(30000) {
            !player!!.state.value.busy && player!!.state.value.preview != null
        }
        compose.onNodeWithTag("present-next").assertIsDisplayed().performClick()
        compose.onNodeWithTag("present-prev").assertIsDisplayed().performClick()
        compose.onNodeWithTag("present-hide-controls").assertIsDisplayed()
        val textLayout = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose
            .onNodeWithText(context.getString(R.string.pdf_previous), useUnmergedTree = true)
            .performSemanticsAction(
                androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult
            ) {
                it(textLayout)
            }
        assertEquals(
            "Navigation labels must not break a word with large text",
            1,
            textLayout.single().lineCount,
        )
        shot("player")
        compose.onNodeWithTag("present-close").performClick()
        action("presentation-images")
        compose.onNodeWithTag("slide-image-create").assertIsDisplayed()
        compose.onNodeWithTag("slide-image-panel-output").performClick()
        compose
            .onNodeWithTag("slide-image-format-BMP")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        shot("output-panel")
    }
}

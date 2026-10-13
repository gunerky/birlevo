package com.example.contentstudio

import android.app.Activity
import android.app.Instrumentation
import android.app.LocaleManager
import android.content.Intent
import android.net.Uri
import android.os.LocaleList
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.platform.app.InstrumentationRegistry
import com.example.contentstudio.data.ProjectRepository
import org.junit.*
import java.io.File

/** Same concrete tasks on the pre-workspace commit and current APK; no navigation injection. */
class WorkspaceComparisonDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private var activity: ActivityScenario<MainActivity>? = null
    private var taps = 0
    private var scrollRequests = 0
    private fun exists(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    private fun reach(tag: String): SemanticsNodeInteraction {
        if (!exists(tag)) {
            val list = when {
                exists("project-editor-list") -> "project-editor-list"
                exists("compact-home") -> "compact-home"
                else -> "wide-home-tools"
            }
            compose.onNodeWithTag(list).performScrollToNode(hasTestTag(tag))
            scrollRequests++
        }
        val node = compose.onNodeWithTag(tag)
        try { node.assertIsDisplayed() } catch (_: AssertionError) { scrollRequests++; node.performScrollTo() }
        return node
    }
    private fun tap(tag: String) { reach(tag).performClick(); taps++ }
    private fun report(task: String) {
        val variant = InstrumentationRegistry.getArguments().getString("comparison-variant", "current")
        val folder = File(context.getExternalFilesDir(null), "qa").apply { mkdirs() }
        File(folder, "comparison-$variant-$task.json").writeText(
            "{\"variant\":\"$variant\",\"task\":\"$task\",\"taps\":$taps,\"scrollRequests\":$scrollRequests}")
    }
    @Before fun setup() {
        context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags("en-US")
        ProjectRepository(context).save(emptyList())
        Intents.init()
        activity = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(20_000) { exists("compress-image-action") }
    }
    @After fun cleanup() { activity?.close(); Intents.release() }
    @Test fun defaultImageConversion() {
        Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(Instrumentation.ActivityResult(
            Activity.RESULT_OK, Intent().setData(Uri.parse("content://com.example.contentstudio.test.fixtures/orientation-6.jpg"))))
        tap("compress-image-action"); tap("choose-image")
        compose.waitUntil(30_000) { exists("image-preview") }
        tap("image-preview")
        compose.waitUntil(30_000) { exists("export-image") || exists("export-image-anyway") }
        report("image")
    }
    @Test fun createAndPreviewOneSlide() {
        if (exists("home-new")) { tap("home-new"); tap("home-new-presentation") }
        else tap("home-presentation")
        compose.waitUntil(20_000) { exists("preview-pdf") }
        if (exists("project-panel-edit")) tap("project-panel-edit")
        reach("slide-heading-0").performTextReplacement("Example slide")
        if (exists("project-panel-close")) tap("project-panel-close")
        tap("preview-pdf")
        compose.waitUntil(30_000) { exists("save-pdf") }
        report("presentation")
    }
}

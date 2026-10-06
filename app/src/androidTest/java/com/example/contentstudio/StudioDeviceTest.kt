package com.example.contentstudio

import android.content.res.Configuration
import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import android.util.Log
import android.graphics.Bitmap
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.contentstudio.data.ProjectRepository
import com.example.contentstudio.data.ProjectKind
import com.example.contentstudio.data.StudioProject
import com.example.contentstudio.data.Slide
import com.example.contentstudio.data.ProjectEditorViewModel
import com.example.contentstudio.data.DraftSaveStatus
import com.example.contentstudio.media.PresentationPdfViewModel
import com.example.contentstudio.ui.appLocales
import java.io.File
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudioDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private var scenario: ActivityScenario<MainActivity>? = null

    private fun capture(name: String) {
        compose.waitForIdle()
        val folder = File(context.getExternalFilesDir(null), "qa").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Before fun resetFixture() {
        instrumentation.runOnMainSync {
            if (Build.VERSION.SDK_INT >= 33) {
                context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags("en-US")
            } else AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en-US"))
        }
        // Only this test application's projects are reset, never other emulator apps.
        File(context.filesDir, "projects.json").delete()
        File(context.filesDir, "projects.json.bak").delete()
        ProjectRepository(context).save(emptyList())
    }

    @After fun closeActivity() {
        scenario?.close()
        resetFixture()
    }

    private fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("Create presentation").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test fun presentationSurvivesRecreationSaveAndRelaunch() {
        launch()
        compose.onNodeWithText("Create presentation").performScrollTo().performClick()
        val title = "Çalışma 日本語 العربية"
        val heading = "İlk slayt — नमस्ते"
        val body = "Birinci satır\nİkinci satır 中文"
        compose.editorNode("project-title").performTextReplacement(title)
        compose.editorNode("slide-heading-0").performTextReplacement(heading)
        compose.editorNode("slide-body-0").performTextReplacement(body)
        scenario!!.recreate()
        compose.editorNode("project-title").assertTextContains(title)
        compose.editorNode("slide-body-0").assertTextContains(body)
        compose.editorNode("add-slide").performScrollTo().performClick()
        compose.editorNode("slide-heading-1").performScrollTo().performTextReplacement("İkinci slayt")
        compose.onNodeWithTag("save-project").performClick()
        compose.projectNode(title).assertExists()
        scenario!!.close()
        launch()
        compose.projectNode(title).performScrollTo().performClick()
        compose.editorNode("slide-heading-0").assertTextContains(heading)
        compose.editorNode("slide-body-0").assertTextContains(body)
        compose.editorNode("slide-heading-1").performScrollTo().assertTextContains("İkinci slayt")
        val saved = ProjectRepository(context).load().single()
        assertEquals(title, saved.title)
        assertEquals(2, saved.slides.size)
    }

    @Test fun previouslySavedVideoDraftCanStillBeOpened() {
        ProjectRepository(context).save(listOf(StudioProject(
            id = "saved-video", kind = ProjectKind.VIDEO, title = "Video taslağı", updatedAt = 1L,
        )))
        launch()
        compose.projectNode("Video taslağı").performScrollTo().performClick()
        compose.editorNode("project-title").assertTextContains("Video taslağı")
        compose.onNodeWithText("Choose video").assertIsDisplayed()
        assertEquals(ProjectKind.VIDEO, ProjectRepository(context).load().single().kind)
    }

    @Test fun slidesCanBeDuplicatedReorderedDeletedAndAutosavedWithoutSaveButton() {
        val photo = Slide("Photo heading", "Photo body", "content://com.example.contentstudio.test.fixtures/orientation-6.jpg")
        val text = Slide("Second heading", "Second body")
        ProjectRepository(context).save(listOf(StudioProject("slides", ProjectKind.PRESENTATION, "Slide workflow", 1,
            listOf(photo, text))))
        launch()
        compose.projectNode("Slide workflow").performScrollTo().performClick()
        compose.editorNode("slide-actions-0").performScrollTo().performClick()
        capture("presentation-slide-actions")
        compose.onNodeWithTag("move-slide-up-0").assertIsNotEnabled()
        compose.onNodeWithTag("duplicate-slide-0").performClick()
        compose.editorNode("slide-heading-1").performScrollTo().performTextReplacement("Copy heading")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.waitForIdle()
        compose.editorNode("slide-actions-1").performScrollTo().performClick()
        compose.onNodeWithTag("move-slide-down-1").performClick()
        compose.editorNode("slide-heading-1").performScrollTo().assertTextContains("Second heading")
        compose.editorNode("slide-heading-2").performScrollTo().assertTextContains("Copy heading")
        // Cancellation must retain the entire slide, including its image.
        compose.editorNode("slide-actions-0").performScrollTo().performClick()
        compose.onNodeWithTag("delete-slide-0").performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("confirm-delete-slide").fetchSemanticsNodes().isEmpty() }
        instrumentation.waitForIdleSync()
        compose.editorNode("slide-heading-0").performScrollTo().assertTextContains("Photo heading")
        compose.editorNode("slide-actions-1").performScrollTo()
        compose.waitForIdle()
        // Focus restoration after dismissing a dialog can move the lazy list between pointer events.
        // Invoke this composed button's accessibility action, then verify its actual menu and delete flow.
        compose.onNodeWithTag("slide-actions-1").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        try {
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("delete-slide-1").fetchSemanticsNodes().isNotEmpty() }
        } catch (failure: Throwable) {
            compose.onRoot().printToLog("SlideMenuFailure")
            capture("slide-menu-failure")
            throw failure
        }
        compose.onNodeWithTag("delete-slide-1").performClick()
        compose.onNodeWithTag("confirm-delete-slide").performClick()
        var editor: ProjectEditorViewModel? = null
        scenario!!.onActivity { editor = ViewModelProvider(it)[ProjectEditorViewModel::class.java] }
        compose.waitUntil(15_000) { editor!!.state.value.saveStatus == DraftSaveStatus.SAVED }
        val saved = ProjectRepository(context).load().single()
        assertEquals(listOf("Photo heading", "Copy heading"), saved.slides.map { it.heading })
        assertEquals(listOf(photo.imageUri, photo.imageUri), saved.slides.map { it.imageUri })
        assertNotEquals(saved.slides[0].id, saved.slides[1].id)
        // Preview must use the changed slide order; it also flushes pending edits.
        compose.editorNode("preview-pdf").performScrollTo().performClick()
        var pdf: PresentationPdfViewModel? = null
        scenario!!.onActivity { pdf = ViewModelProvider(it)[PresentationPdfViewModel::class.java] }
        compose.waitUntil(20_000) { pdf!!.state.value.result != null || pdf!!.state.value.message != null }
        assertEquals(2, pdf!!.state.value.result!!.pages)
        assertEquals(saved.slides, pdf!!.state.value.project!!.slides)
        compose.onNodeWithTag("pdf-back").performClick()
        scenario!!.recreate()
        compose.editorNode("slide-heading-1").performScrollTo().assertTextContains("Copy heading")
        scenario!!.close()
        launch()
        compose.projectNode("Slide workflow").performScrollTo().performClick()
        compose.editorNode("slide-heading-1").performScrollTo().assertTextContains("Copy heading")
    }

    @Test fun backgroundingFlushesPendingTypingAndLastSlideCannotBeDeleted() {
        launch()
        compose.onNodeWithText("Create presentation").performScrollTo().performClick()
        compose.editorNode("project-title").performTextReplacement("Background draft")
        compose.editorNode("slide-actions-0").performScrollTo().performClick()
        compose.onNodeWithTag("delete-slide-0").assertIsNotEnabled()
        compose.onNodeWithTag("move-slide-up-0").assertIsNotEnabled()
        compose.onNodeWithTag("move-slide-down-0").assertIsNotEnabled()
        // Dismiss the menu, type, then stop immediately rather than waiting for the debounce.
        instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        compose.editorNode("slide-heading-0").performTextReplacement("Latest before background")
        var editor: ProjectEditorViewModel? = null
        scenario!!.onActivity { editor = ViewModelProvider(it)[ProjectEditorViewModel::class.java] }
        scenario!!.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        compose.waitUntil(15_000) { editor!!.state.value.saveStatus == DraftSaveStatus.SAVED }
        assertEquals("Latest before background", ProjectRepository(context).load().single().slides.single().heading)
        scenario!!.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        compose.editorNode("slide-heading-0").assertTextContains("Latest before background")
        scenario!!.close()
        launch()
        compose.projectNode("Background draft").performScrollTo().performClick()
        compose.editorNode("slide-heading-0").assertTextContains("Latest before background")
    }

    @Test fun everyLanguageAppliesAndRtlDirectionsMatch() {
        launch()
        for (locale in appLocales) {
            val configuration = Configuration(context.resources.configuration)
            configuration.setLocale(java.util.Locale.forLanguageTag(locale.tag))
            val expected = context.createConfigurationContext(configuration).getString(R.string.home_title)
            Log.i("StudioDeviceTest", "Applying ${locale.tag}: $expected")
            scenario!!.close()
            instrumentation.runOnMainSync {
                if (Build.VERSION.SDK_INT >= 33) {
                    context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(locale.tag)
                } else AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(locale.tag))
            }
            instrumentation.waitForIdleSync()
            scenario = ActivityScenario.launch(MainActivity::class.java)
            // LocaleManager can deliver a late configuration change to the just-launched Activity.
            scenario!!.recreate()
            try {
                compose.waitUntil(20_000) {
                    compose.onAllNodesWithText(expected).fetchSemanticsNodes().isNotEmpty()
                }
            } catch (error: Throwable) {
                scenario!!.onActivity {
                    Log.e("StudioDeviceTest", "Failed ${locale.tag}: activity locale=${it.resources.configuration.locales[0]}")
                }
                compose.onRoot().printToLog("StudioLocaleFailure")
                throw error
            }
            instrumentation.waitForIdleSync()
            scenario!!.onActivity {
                assertEquals(locale.tag, it.resources.configuration.locales[0].toLanguageTag())
                assertEquals(if (locale.tag in listOf("ar", "fa")) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR,
                    it.resources.configuration.layoutDirection)
                assertEquals(expected, it.getString(R.string.home_title))
            }
            if (locale.tag in listOf("tr-TR", "ar", "zh-Hans")) capture("home-${locale.tag}")
        }
    }

    @Test fun languageMenuChangesToTurkish() {
        launch()
        compose.onNodeWithText("Language").performClick()
        compose.onNodeWithText("Türkçe").performScrollTo().performClick()
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("Bugün ne hazırlayalım?").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Sunum oluştur").performScrollTo().assertIsDisplayed()
    }

    @Test fun homeAdaptsToAvailableWidthAndActionsRemainReachable() {
        launch()
        capture("home-start")
        var wide = false
        scenario!!.onActivity {
            val availableDp = it.window.decorView.width / it.resources.displayMetrics.density
            wide = minOf(availableDp, 1100f) - 40f >= 700f
        }
        compose.onNodeWithTag(if (wide) "wide-home" else "compact-home").assertExists()
        compose.homeNode("Create presentation").performScrollTo().assertIsDisplayed()
        compose.homeNode("Edit video").performScrollTo().assertIsDisplayed()
        compose.homeNode("Projects").performScrollTo().assertIsDisplayed()
        compose.homeNode("Create presentation").performScrollTo()
        capture(if (wide) "home-wide" else "home-compact")
    }

    @Test fun unreadableProjectsAreNotOverwritten() {
        val file = File(context.filesDir, "projects.json")
        val corrupt = "{broken data"
        file.writeText(corrupt)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Try again").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Create presentation").assertDoesNotExist()
        compose.onNodeWithText("Try again").performClick()
        compose.waitForIdle()
        assertEquals(corrupt, file.readText())
    }
}

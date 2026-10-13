package com.example.contentstudio

import android.app.LocaleManager
import android.graphics.Bitmap
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.contentstudio.data.*
import com.example.contentstudio.media.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PresentationDesignDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private var scenario: ActivityScenario<MainActivity>? = null
    private val image = "content://com.example.contentstudio.test.fixtures/orientation-6.jpg"
    private val qa get() = File(context.getExternalFilesDir(null), "qa").apply { mkdirs() }

    @Before fun setup() {
        instrumentation.runOnMainSync {
            if (Build.VERSION.SDK_INT >= 33) context.getSystemService(LocaleManager::class.java)
                .applicationLocales = LocaleList.forLanguageTags("en-US")
            else AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en-US"))
        }
        ProjectRepository(context).save(emptyList())
    }
    @After fun teardown() { scenario?.close(); ProjectRepository(context).save(emptyList()) }
    private fun writeBitmap(bitmap: Bitmap, name: String) {
        File(qa, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun allDesignsMatchExportAndKeepImagesInTheirSelectedPosition() = runBlocking(Dispatchers.IO) {
        val engine = PresentationPdf(context)
        for (theme in PresentationTheme.entries) {
            val project = StudioProject("matrix", ProjectKind.PRESENTATION, "Our ideas", 1,
                SlideLayout.entries.map { Slide("Create together", "A clear plan.\nOne step at a time.", image, layout = it) }, theme = theme)
            val result = engine.create(project) {}
            try {
                for (index in project.slides.indices) {
                    val exported = engine.render(result, index)
                    val editor = engine.previewSlide(project, index)
                    try {
                        assertTrue("Editor and PDF must match: $theme / $index", exported.sameAs(editor))
                        assertEquals(theme.palette.background, exported.getPixel(2, 2))
                        val box = requireNotNull(slideGeometry(project.slides[index].layout, true).image)
                        val centre = exported.getPixel(((box.left + box.width / 2) * 4 / 3).toInt(),
                            ((box.top + box.height / 2) * 4 / 3).toInt())
                        assertNotEquals("Image must occupy the chosen region", theme.palette.background, centre)
                        writeBitmap(exported, "design-${theme.name}-${project.slides[index].layout.name}")
                    } finally { exported.recycle(); editor.recycle() }
                }
            } finally { result.file.delete() }
        }
        val blank = StudioProject("blank", ProjectKind.PRESENTATION, "Blank", 1, listOf(Slide()), theme = PresentationTheme.OCEAN)
        val preview = engine.previewSlide(blank, 0)
        try { assertEquals(blank.theme.palette.background, preview.getPixel(2, 2)) } finally { preview.recycle() }
        try { engine.create(blank) {}; fail("Empty export must still be rejected") } catch (_: PdfEmptyPresentation) { }
        val overflow = blank.copy(slides = listOf(Slide("Heading", "Very long text ".repeat(500), image, layout = SlideLayout.IMAGE_TOP)))
        val before = File(context.noBackupFilesDir, "presentation-pdf").listFiles()!!.map { it.name }.toSet()
        try { engine.previewSlide(overflow, 0); fail("Text must never be silently clipped") }
        catch (error: PdfTextOverflow) { assertEquals(1, error.slideNumber) }
        assertEquals(before, File(context.noBackupFilesDir, "presentation-pdf").listFiles()!!.map { it.name }.toSet())
    }

    @Test fun designChoicesAutosaveSurviveRecreationAndReachPdf() {
        val project = StudioProject("designed", ProjectKind.PRESENTATION, "Design workflow", 1,
            listOf(Slide("Create together", "A clear plan.", image)))
        ProjectRepository(context).save(listOf(project))
        fun open() {
            scenario = ActivityScenario.launch(MainActivity::class.java)
            compose.projectNode(project.title).performScrollTo().performClick()
        }
        open()
        compose.editorNode("presentation-theme").performScrollTo().performClick()
        compose.workspaceNode("theme-OCEAN").performClick()
        compose.editorNode("slide-layout-0").performScrollTo().performClick()
        compose.workspaceNode("layout-IMAGE_LEFT-0").performClick()
        compose.editorNode("presentation-theme").performScrollTo()
        instrumentation.uiAutomation.takeScreenshot().let { try { writeBitmap(it, "presentation-editor-controls") } finally { it.recycle() } }
        compose.onNodeWithTag("project-panel-close").performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("slide-design-preview-0").fetchSemanticsNodes().isNotEmpty() }
        compose.editorNode("slide-design-preview-0").performScrollTo().assertIsDisplayed()
        instrumentation.uiAutomation.takeScreenshot().let { try { writeBitmap(it, "presentation-theme-editor") } finally { it.recycle() } }
        // Changing the theme refreshes an already open design preview.
        compose.editorNode("presentation-theme").performScrollTo().performClick()
        compose.workspaceNode("theme-WARM").performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("slide-design-preview-0").fetchSemanticsNodes().isNotEmpty() }
        compose.editorNode("slide-actions-0").performScrollTo().performClick()
        compose.workspaceNode("duplicate-slide-0").performClick()
        compose.editorNode("slide-heading-1").performScrollTo().performTextReplacement("Second slide")
        compose.editorNode("slide-actions-1").performScrollTo().performClick()
        compose.workspaceNode("move-slide-up-1").performClick()
        var editor: ProjectEditorViewModel? = null
        scenario!!.onActivity { editor = ViewModelProvider(it)[ProjectEditorViewModel::class.java] }
        compose.waitUntil(15_000) { editor!!.state.value.saveStatus == DraftSaveStatus.SAVED }
        val saved = ProjectRepository(context).load().single()
        assertEquals(PresentationTheme.WARM, saved.theme)
        assertEquals(listOf(SlideLayout.IMAGE_LEFT, SlideLayout.IMAGE_LEFT), saved.slides.map { it.layout })
        assertEquals(listOf("Second slide", "Create together"), saved.slides.map { it.heading })
        assertEquals(listOf(image, image), saved.slides.map { it.imageUri })
        scenario!!.recreate()
        compose.editorNode("presentation-theme").performScrollTo().assertTextContains("Warm")
        compose.editorNode("slide-layout-0").performScrollTo().assertTextContains("Image on the left")
        scenario!!.close(); open()
        compose.editorNode("presentation-theme").performScrollTo().assertTextContains("Warm")
        compose.editorNode("preview-pdf").performScrollTo().performClick()
        var pdf: PresentationPdfViewModel? = null
        scenario!!.onActivity { pdf = ViewModelProvider(it)[PresentationPdfViewModel::class.java] }
        compose.waitUntil(20_000) { pdf!!.state.value.result != null || pdf!!.state.value.message != null }
        assertEquals(saved.theme, pdf!!.state.value.project!!.theme)
        assertEquals(saved.slides, pdf!!.state.value.project!!.slides)
        assertEquals(2, pdf!!.state.value.result!!.pages)
        assertEquals(saved.theme.palette.background, pdf!!.state.value.preview!!.getPixel(2, 2))
    }
}

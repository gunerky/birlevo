package com.example.contentstudio

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.ViewModelProvider
import com.example.contentstudio.data.*
import com.example.contentstudio.media.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.cancel
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class PerformanceDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private var scenario: ActivityScenario<MainActivity>? = null
    private val qa get() = File(context.getExternalFilesDir(null), "qa").apply { mkdirs() }
    @After fun close() { scenario?.close(); ProjectRepository(context).save(emptyList()) }

    @Test fun hundredImageSlidesRemainEditableWithoutComposingEverySlide() {
        val project = StudioProject("stress", ProjectKind.PRESENTATION, "Large presentation", 1,
            (0 until 100).map { Slide("Heading $it", "Body $it",
                "content://com.example.contentstudio.test.fixtures/orientation-6.jpg", id = "slide-$it") })
        ProjectRepository(context).save(listOf(project))
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.projectNode(project.title).performScrollTo().performClick()
        val composed = compose.onAllNodes(hasTestTagPrefix("slide-heading-")).fetchSemanticsNodes().size
        File(qa, "large-editor-composed-count.txt").writeText(composed.toString())
        assertTrue("Expected bounded visible slides, actually composed $composed", composed in 1..5)
        compose.editorNode("slide-heading-50").performScrollTo().performTextReplacement("Middle changed")
        compose.editorNode("slide-heading-99").performScrollTo().performTextReplacement("Last changed")
        var editor: ProjectEditorViewModel? = null
        scenario!!.onActivity { editor = ViewModelProvider(it)[ProjectEditorViewModel::class.java] }
        assertEquals("Last changed", editor!!.state.value.draft!!.slides.last().heading)
        compose.editorNode("slide-heading-0").performScrollTo().assertTextContains("Heading 0")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.waitForIdle()
        compose.onNodeWithTag("save-project").performClick()
        // Observe the writer, not AtomicFile.openRead while a write may be in flight.
        compose.waitUntil(15_000) { editor!!.state.value.draft == null }
        val saved = ProjectRepository(context).load().single()
        assertEquals(100, saved.slides.size)
        assertEquals("Middle changed", saved.slides[50].heading)
        assertTrue(saved.slides.all { it.imageUri != null })
    }

    @Test fun longProjectLibraryCanReachAndOpenLastProject() {
        ProjectRepository(context).save((0 until 200).map {
            StudioProject("p-$it", ProjectKind.PRESENTATION, "Project $it", (200 - it).toLong(), listOf(Slide("Slide $it")))
        })
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("compact-home").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("compact-home").performScrollToNode(hasText("Project 199"))
        compose.onNodeWithText("Project 199").performScrollTo().performClick()
        compose.editorNode("slide-heading-0").performScrollTo().assertTextContains("Slide 199")
    }

    @Test fun cancelledPreviewReleasesBitmapBeforeDispatcherHandoff() = runBlocking {
        var decoded: Bitmap? = null
        launch {
            PreviewLoader.load {
                Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888).also {
                    decoded = it
                    currentCoroutineContext().cancel()
                }
            }
        }.join()
        assertTrue(requireNotNull(decoded).isRecycled)
    }

    // Also runs against the frozen 0.1.3 APK: host QA compares the two fingerprint files.
    // Rasterized PDF pixels are compared because PDF container metadata is not deterministic.
    @Test fun exportFixtureFingerprints() = runBlocking(Dispatchers.IO) {
        val lines = mutableListOf<String>()
        val engine = ImageCompressor(context)
        for (name in listOf("noise.jpg", "tiny.png", "orientation-6.jpg")) {
            val source = engine.importImage(Uri.parse("content://com.example.contentstudio.test.fixtures/$name"))
            try {
                for (edge in listOf(1280, 1920, 2560)) {
                    val result = engine.compress(source, 80, edge)
                    try { lines += "$name/$edge=${digest(result.file.readBytes())}:${result.width}x${result.height}" }
                    finally { result.file.delete(); result.preview.recycle() }
                }
            } finally { source.file.delete(); source.preview.recycle() }
        }
        for (theme in PresentationTheme.entries) {
            val project = StudioProject("same", ProjectKind.PRESENTATION, "Identical output", 1,
                SlideLayout.entries.map { Slide("Heading", "Body", "content://com.example.contentstudio.test.fixtures/orientation-6.jpg", layout = it) }, theme = theme)
            val pdf = PresentationPdf(context)
            val result = pdf.create(project) {}
            try { for (index in project.slides.indices) {
                val bitmap = pdf.render(result, index)
                try {
                    val pixels = ByteBuffer.allocate(bitmap.byteCount)
                    bitmap.copyPixelsToBuffer(pixels)
                    lines += "$theme/$index=${digest(pixels.array())}:${bitmap.width}x${bitmap.height}"
                } finally { bitmap.recycle() }
            } } finally { result.file.delete() }
        }
        File(qa, "output-fingerprints.txt").writeText(lines.joinToString("\n"))
    }

    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun hasTestTagPrefix(prefix: String) = SemanticsMatcher("test tag starts with $prefix") {
        it.config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.TestTag) { "" }.startsWith(prefix)
    }
}

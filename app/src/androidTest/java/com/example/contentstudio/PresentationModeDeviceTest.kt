package com.example.contentstudio

import android.app.Application
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.example.contentstudio.data.*
import com.example.contentstudio.media.*
import java.io.File
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*

class PresentationModeDeviceTest {
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val stores = mutableListOf<ViewModelStore>()
    private val outputs = mutableListOf<CompressedImage>()
    private val project =
        StudioProject(
            "present-test",
            ProjectKind.PRESENTATION,
            "Slides",
            1,
            listOf(
                Slide("Visible title", "Body", id = "first", notes = "SECRET NOTES NOT IN OUTPUT"),
                Slide("Second", "العربية 日本語", id = "second", notes = "Another private note"),
            ),
        )

    private suspend fun waitFor(condition: () -> Boolean) {
        withTimeout(30000) { while (!condition()) delay(25) }
    }

    private fun own(model: androidx.lifecycle.ViewModel) {
        stores += ViewModelStore().apply { put("test", model) }
    }

    @After
    fun cleanup() =
        runBlocking<Unit> {
            withContext(Dispatchers.Main) { stores.forEach { it.clear() } }
            outputs.forEach {
                it.file.delete()
                if (!it.preview.isRecycled) it.preview.recycle()
            }
        }

    @Test
    fun allImageFormatsKeepActualDimensionsAndNotesNeverChangePixels() =
        runBlocking<Unit> {
            val engine = PresentationImageEngine(context)
            for (format in ImageOutputFormat.entries) {
                val result =
                    withContext(Dispatchers.IO) {
                        engine.create(project, "first", SlideImageOptions(format, 1024))
                    }
                outputs += result
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(result.file.path, bounds)
                assertEquals(1024, bounds.outWidth)
                assertEquals(576, bounds.outHeight)
                assertEquals(format.mime, bounds.outMimeType)
                assertEquals(result.bytes, result.file.length())
                assertTrue(result.preview.width <= 512 && result.preview.height <= 512)
                val visible =
                    withContext(Dispatchers.IO) {
                        engine.create(
                            project.visualSnapshot(),
                            "first",
                            SlideImageOptions(format, 1024),
                        )
                    }
                outputs += visible
                assertArrayEquals(
                    "Speaker notes must not enter $format output",
                    visible.file.readBytes(),
                    result.file.readBytes(),
                )
            }
            val full =
                withContext(Dispatchers.IO) {
                    engine.create(project, "second", SlideImageOptions(edge = 4096))
                }
            outputs += full
            assertEquals(4096, full.width)
            assertEquals(2304, full.height)
        }

    @Test
    fun exportsCopyBytesAndRefuseOriginalsOrStaleResultFiles() =
        runBlocking<Unit> {
            val engine = PresentationImageEngine(context)
            val result =
                withContext(Dispatchers.IO) {
                    engine.create(project, "first", SlideImageOptions(edge = 1024))
                }
            outputs += result
            val file = File(context.cacheDir, "slide-image-test-saved.png")
            try {
                withContext(Dispatchers.IO) { engine.export(project, result, Uri.fromFile(file)) }
                assertArrayEquals(result.file.readBytes(), file.readBytes())
                val bytes = file.readBytes()
                val protected =
                    project.copy(
                        slides =
                            listOf(project.slides[0].copy(imageUri = Uri.fromFile(file).toString()))
                    )
                try {
                    engine.export(protected, result, Uri.fromFile(file))
                    fail("Must protect originals")
                } catch (_: IllegalArgumentException) {}
                assertArrayEquals(bytes, file.readBytes())
                val bad = result.copy(bytes = result.bytes + 1)
                try {
                    engine.export(project, bad, Uri.fromFile(file))
                    fail("Must reject stale bytes")
                } catch (_: IllegalArgumentException) {}
                assertArrayEquals(bytes, file.readBytes())
            } finally {
                file.delete()
            }
        }

    @Test
    fun overflowAndCancellationLeaveNoPartialImage() =
        runBlocking<Unit> {
            val engine = PresentationImageEngine(context)
            val folder = File(context.cacheDir, "presentation-images")
            val before = folder.listFiles()?.map { it.name }?.toSet().orEmpty()
            try {
                withContext(Dispatchers.IO) {
                    engine.create(
                        project.copy(
                            slides =
                                listOf(project.slides[0].copy(body = "Too much text ".repeat(600)))
                        ),
                        "first",
                        SlideImageOptions(),
                    )
                }
                fail("Overflow must be explicit")
            } catch (_: PdfTextOverflow) {}
            assertEquals(before, folder.listFiles()?.map { it.name }?.toSet().orEmpty())
            var unexpected: CompressedImage? = null
            val job =
                launch(Dispatchers.IO) {
                    unexpected = engine.create(project, "first", SlideImageOptions(edge = 4096))
                }
            withTimeout(30000) {
                while (folder.listFiles()?.none { it.name !in before } != false) delay(1)
            }
            job.cancelAndJoin()
            unexpected?.let { outputs += it }
            assertNull("Cancellation must interrupt the in-flight render/encode", unexpected)
            assertEquals(before, folder.listFiles()?.map { it.name }?.toSet().orEmpty())
        }

    @Test
    fun savedPresenterSelectionClockAndImageSettingsRestoreWithoutOldResult() =
        runBlocking<Unit> {
            val application = context.applicationContext as Application
            val handle =
                SavedStateHandle(
                    mapOf(
                        "present-project" to project.id,
                        "present-slide" to "second",
                        "present-timer" to android.os.Bundle().apply { putLong("elapsed", 4000) },
                    )
                )
            lateinit var player: PresentationPlayerViewModel
            withContext(Dispatchers.Main) {
                player = PresentationPlayerViewModel(application, handle)
                own(player)
                player.prepare(project)
            }
            waitFor { !player.state.value.busy }
            assertEquals(1, player.state.value.index)
            assertEquals(4000L, player.state.value.elapsedMs)
            assertFalse(player.state.value.running)
            withContext(Dispatchers.Main) { player.toggleTimer() }
            waitFor { player.state.value.elapsedMs >= 4500 }
            withContext(Dispatchers.Main) { player.prepare(project.copy(updatedAt = 5000)) }
            assertTrue("Autosave metadata must not pause the stopwatch", player.state.value.running)
            withContext(Dispatchers.Main) { player.pause() }
            val paused = player.state.value.elapsedMs
            delay(600)
            assertEquals(paused, player.state.value.elapsedMs)
            withContext(Dispatchers.Main) {
                player.select(0)
                player.leave()
                player.prepare(project)
            }
            waitFor { !player.state.value.busy && player.state.value.preview != null }
            assertEquals(0, player.state.value.index)
            val imageHandle =
                SavedStateHandle(
                    mapOf(
                        "slide-image-project" to project.id,
                        "slide-image-selected" to "second",
                        "slide-image-format" to "BMP",
                        "slide-image-edge" to 4096,
                        "slide-image-quality" to 70,
                    )
                )
            lateinit var model: PresentationImageViewModel
            withContext(Dispatchers.Main) {
                model = PresentationImageViewModel(application, imageHandle)
                own(model)
                model.prepare(project)
            }
            assertEquals("second", model.state.value.slideId)
            assertEquals(
                SlideImageOptions(ImageOutputFormat.BMP, 4096, 70),
                model.state.value.options,
            )
            assertNull(model.state.value.result)
        }

    @Test
    fun changingSlideInvalidatesImageAndStaleSaveIsRefused() =
        runBlocking<Unit> {
            lateinit var model: PresentationImageViewModel
            withContext(Dispatchers.Main) {
                model =
                    PresentationImageViewModel(
                        context.applicationContext as Application,
                        SavedStateHandle(),
                    )
                own(model)
                model.prepare(project)
                model.options(SlideImageOptions(edge = 1024))
                model.generate()
            }
            waitFor { !model.state.value.busy }
            val output = checkNotNull(model.state.value.result)
            withContext(Dispatchers.Main) { model.prepare(project.copy(updatedAt = 5000)) }
            assertEquals(
                "Autosave metadata must not invalidate a valid image",
                output.file,
                model.state.value.result?.file,
            )
            // Delivered previews remain valid even when output options invalidate their file.
            withContext(Dispatchers.Main) { model.select("second") }
            assertNull(model.state.value.result)
            assertFalse(output.preview.isRecycled)
            val target = File(context.cacheDir, "slide-image-stale.png")
            try {
                withContext(Dispatchers.Main) {
                    model.export(Uri.fromFile(target), output.file.path)
                }
                assertEquals(SlideImageMessage.EXPORT_ERROR, model.state.value.message)
                assertFalse(target.exists())
            } finally {
                target.delete()
            }
        }
}

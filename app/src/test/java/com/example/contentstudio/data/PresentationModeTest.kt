package com.example.contentstudio.data

import com.example.contentstudio.media.SlideImageOptions
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PresentationModeTest {
    private val project =
        StudioProject(
            "notes",
            ProjectKind.PRESENTATION,
            "Demo",
            1,
            listOf(Slide("Visible", "Body", id = "first", notes = "Özel not\nالعربية 日本語")),
        )

    @Test
    fun `notes persist without becoming visible slide content`() {
        assertEquals(project, ProjectCodec.decode(ProjectCodec.encode(listOf(project))).single())
        val visible = project.visualSnapshot()
        assertEquals("", visible.slides.single().notes)
        assertEquals(project.slides.single().copy(notes = ""), visible.slides.single())
        assertFalse(project.slides.single().notes.isBlank())
    }

    @Test
    fun `legacy notes default and stable identities survive`() {
        val root = JSONObject(ProjectCodec.encode(listOf(project)))
        root
            .getJSONArray("projects")
            .getJSONObject(0)
            .getJSONArray("slides")
            .getJSONObject(0)
            .remove("notes")
        val restored = ProjectCodec.decode(root.toString()).single()
        assertEquals(project.visualSnapshot(), restored)
        assertEquals(restored, ProjectCodec.decode(ProjectCodec.encode(listOf(restored))).single())
    }

    @Test
    fun `duplicate and reorder retain each speakers notes`() {
        val duplicated = SlideOperations.duplicate(project.slides, "first")
        assertEquals(project.slides.single().notes, duplicated[1].notes)
        assertNotEquals("first", duplicated[1].id)
        val moved = SlideOperations.move(duplicated, duplicated[1].id, -1)
        assertEquals(duplicated.reversed(), moved)
    }

    @Test
    fun `notes limit accepts boundary and rejects oversized records`() {
        val boundary =
            project.copy(slides = listOf(project.slides[0].copy(notes = "n".repeat(8000))))
        val json = ProjectCodec.encode(listOf(boundary))
        assertEquals(boundary, ProjectCodec.decode(json).single())
        assertThrows(IllegalArgumentException::class.java) {
            ProjectCodec.encode(
                listOf(
                    boundary.copy(
                        slides = listOf(boundary.slides[0].copy(notes = "n".repeat(8001)))
                    )
                )
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            ProjectCodec.decode(json.replace("n".repeat(8000), "n".repeat(8001)))
        }
    }

    @Test
    fun `stopwatch counts only running monotonic intervals`() {
        val running = PresentationClock().start(100)
        assertEquals(750L, running.elapsed(850))
        assertEquals(running, running.start(600))
        val paused = running.pause(850)
        assertEquals(750L, paused.elapsed(90000))
        val resumed = paused.start(95000).pause(95250)
        assertEquals(1000L, resumed.accumulatedMs)
        assertEquals(PresentationClock(), resumed.reset())
        assertEquals(0L, PresentationClock().start(100).elapsed(50))
    }

    @Test
    fun `clock labels keep hours and resume paused after a snapshot`() {
        assertEquals("01:01:01", presentationClockLabel(3661000))
        assertEquals("100:00:00", presentationClockLabel(360000000))
        assertEquals("00:00:00", presentationClockLabel(-1))
        assertEquals(1234L, PresentationClock(1234).elapsed(900000))
        assertThrows(IllegalArgumentException::class.java) { PresentationClock(-1) }
    }

    @Test
    fun `slide image output sizes are explicit and independent of preview`() {
        for (edge in listOf(1024, 2048, 4096)) assertEquals(
            edge * 9 / 16,
            SlideImageOptions(edge = edge).height,
        )
        assertThrows(IllegalArgumentException::class.java) { SlideImageOptions(edge = 1280) }
        assertThrows(IllegalArgumentException::class.java) { SlideImageOptions(quality = 39) }
    }
}

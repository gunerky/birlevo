package com.example.contentstudio.data

import org.junit.Assert.*
import org.junit.Test

class SlideOperationsTest {
    private val photo = Slide("Photo", "Caption", "content://images/42", "photo", SlideLayout.IMAGE_LEFT)
    private val text = Slide("Text", "Body", id = "text")

    @Test fun `moving preserves identity and content through save and reopen`() {
        val moved = SlideOperations.move(listOf(photo, text), photo.id, 1)
        assertEquals(listOf(text, photo), moved)
        val project = StudioProject("presentation", ProjectKind.PRESENTATION, "Test", 1, moved)
        assertEquals(project, ProjectCodec.decode(ProjectCodec.encode(listOf(project))).single())
    }

    @Test fun `duplicate retains text and image but has an independent identity`() {
        val slides = SlideOperations.duplicate(listOf(photo, text), photo.id)
        assertEquals(listOf(photo, text), listOf(slides[0], slides[2]))
        assertNotEquals(photo.id, slides[1].id)
        assertEquals(photo, slides[1].copy(id = photo.id))
        assertEquals(listOf(slides[1], text), SlideOperations.remove(slides, photo.id))
    }

    @Test fun `last slide boundaries and maximum count are protected`() {
        assertEquals(listOf(photo), SlideOperations.remove(listOf(photo), photo.id))
        assertEquals(listOf(photo, text), SlideOperations.move(listOf(photo, text), photo.id, -1))
        assertEquals(listOf(photo, text), SlideOperations.move(listOf(photo, text), text.id, 1))
        val full = List(100) { Slide(id = "slide-$it") }
        assertEquals(full, SlideOperations.duplicate(full, full[0].id))
    }
}

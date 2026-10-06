package com.example.contentstudio.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProjectCodecTest {
    @Test fun `saved drafts retain multilingual slides and a persistent media reference`() {
        val drafts = listOf(
            StudioProject("slides", ProjectKind.PRESENTATION, "Yağış — دورة الماء", 123L,
                listOf(Slide("日本語", "中文 · فارسی · हिन्दी", "content://images/42"),
                    Slide("İkinci", "Satır 1\nSatır 2"))),
            StudioProject("video", ProjectKind.VIDEO, "Video", 456L,
                sourceUri = "content://media/external/video/media/42", sourceName = "örnek.mp4"),
        )
        assertEquals(drafts, ProjectCodec.decode(ProjectCodec.encode(drafts)))
    }

    @Test fun `older slide drafts without images still open`() {
        val old = """{"version":1,"projects":[{"id":"legacy","kind":"PRESENTATION","title":"Old","updatedAt":1,"sourceUri":null,"sourceName":null,"slides":[{"heading":"Title","body":"Text"}]}]}"""
        val project = ProjectCodec.decode(old).single()
        val slide = project.slides.single()
        assertEquals("Title", slide.heading)
        assertEquals("Text", slide.body)
        assertEquals(null, slide.imageUri)
        assertEquals(PresentationTheme.CLASSIC, project.theme)
        assertEquals(SlideLayout.TEXT_IMAGE, slide.layout)
        assertEquals(project, ProjectCodec.decode(old).single())
        assertEquals(project, ProjectCodec.decode(ProjectCodec.encode(listOf(project))).single())
    }

    @Test fun `a future schema fails instead of becoming an empty project list`() {
        assertThrows(IllegalArgumentException::class.java) {
            ProjectCodec.decode("""{"version":2,"projects":[]}""")
        }
    }

    @Test fun `duplicate ids fail instead of overwriting a saved draft`() {
        val draft = StudioProject("same", ProjectKind.VIDEO, "A", 1L)
        assertThrows(IllegalArgumentException::class.java) {
            ProjectCodec.decode(ProjectCodec.encode(listOf(draft, draft.copy(title = "B"))))
        }
    }

    @Test fun `duplicate slide ids fail instead of assigning edits to the wrong slide`() {
        val slide = Slide("A", id = "same-slide")
        val project = StudioProject("slides", ProjectKind.PRESENTATION, "A", 1L, listOf(slide, slide))
        assertThrows(IllegalArgumentException::class.java) { ProjectCodec.decode(ProjectCodec.encode(listOf(project))) }
    }

    @Test fun `legacy slides with identical text get distinct stable identities`() {
        val old = """{"version":1,"projects":[{"id":"legacy","kind":"PRESENTATION","title":"Old","updatedAt":1,"sourceUri":null,"sourceName":null,"slides":[{"heading":"Same","body":"Text"},{"heading":"Same","body":"Text"}]}]}"""
        val project = ProjectCodec.decode(old).single()
        org.junit.Assert.assertNotEquals(project.slides[0].id, project.slides[1].id)
        assertEquals(project, ProjectCodec.decode(old).single())
        assertEquals(project, ProjectCodec.decode(ProjectCodec.encode(listOf(project))).single())
    }

    @Test fun `all themes and layouts survive a draft round trip`() {
        val projects = PresentationTheme.entries.map { theme ->
            StudioProject(theme.name, ProjectKind.PRESENTATION, "Design", 1,
                SlideLayout.entries.map { Slide("Title", "Body", "content://image/42", layout = it) }, theme = theme)
        }
        assertEquals(projects, ProjectCodec.decode(ProjectCodec.encode(projects)))
    }

    @Test fun `unknown design values fail without silently replacing the saved design`() {
        val project = StudioProject("style", ProjectKind.PRESENTATION, "Design", 1, listOf(Slide("Title")))
        val json = ProjectCodec.encode(listOf(project))
        for (value in listOf("CLASSIC", "TEXT_IMAGE")) {
            assertThrows(IllegalArgumentException::class.java) { ProjectCodec.decode(json.replace(value, "FUTURE")) }
        }
    }
}

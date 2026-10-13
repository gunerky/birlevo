package com.example.contentstudio.media

import com.example.contentstudio.data.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URI
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class PptxExportTest {
    private val project =
        StudioProject(
            "pptx",
            ProjectKind.PRESENTATION,
            "Deck",
            1,
            listOf(Slide("Hello", "World", id = "slide", notes = "Secret")),
        )

    private fun parts(notes: Boolean): Map<String, ByteArray> {
        val bytes = ByteArrayOutputStream()
        PptxPackage(bytes, 2, notes).use { zip ->
            zip.slide("<private> & \"quoted\"\nالعربية\r\n😀") { it.write(byteArrayOf(1, 2, 3)) }
            zip.slide("") { it.write(byteArrayOf(4, 5, 6)) }
            zip.finish()
        }
        val result = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes.toByteArray())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                assertNull(result.put(entry.name, zip.readBytes()))
            }
        }
        return result
    }

    private fun xml(bytes: ByteArray) =
        DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(ByteArrayInputStream(bytes))

    @Test
    fun excludedNotesLeaveNoPartsRelationsOrText() {
        val parts = parts(false)
        assertFalse(parts.keys.any { "notes" in it.lowercase() })
        assertFalse(parts.values.any { it.toString(Charsets.UTF_8).contains("private") })
        parts.filterKeys { it.endsWith("xml") || it.endsWith("rels") }.values.forEach { xml(it) }
        assertEquals(
            2,
            xml(parts.getValue("ppt/presentation.xml"))
                .getElementsByTagNameNS(
                    "http://schemas.openxmlformats.org/presentationml/2006/main",
                    "sldId",
                )
                .length,
        )
    }

    @Test
    fun notesRoundTripXmlCharactersAndUnicode() {
        val parts = parts(true)
        val doc = xml(parts.getValue("ppt/notesSlides/notesSlide1.xml"))
        val texts =
            doc.getElementsByTagNameNS("http://schemas.openxmlformats.org/drawingml/2006/main", "t")
        assertEquals(
            listOf("<private> & \"quoted\"", "العربية\r", "😀"),
            (0 until texts.length).map { texts.item(it).textContent },
        )
        assertEquals(
            0,
            xml(parts.getValue("ppt/slides/slide1.xml"))
                .getElementsByTagNameNS(
                    "http://schemas.openxmlformats.org/drawingml/2006/main",
                    "t",
                )
                .length,
        )
    }

    @Test
    fun everyRelationshipIsInternalAndResolvesToAnActualPart() {
        val parts = parts(true)
        parts
            .filterKeys { it.endsWith(".rels") }
            .forEach { (name, bytes) ->
                val relationships = xml(bytes).documentElement.childNodes
                val base = if (name == "_rels/.rels") "" else name.substringBefore("/_rels/") + "/"
                for (i in 0 until relationships.length) {
                    val relation = relationships.item(i) as? org.w3c.dom.Element ?: continue
                    assertFalse(relation.hasAttribute("TargetMode"))
                    val target = URI(base + relation.getAttribute("Target")).normalize().path
                    assertTrue("$name -> $target", target in parts)
                }
            }
    }

    @Test
    fun requestIdentityTracksVisibleEditsAndExplicitNotesButNotTimestamps() {
        val noNotes = PptxOptions()
        val request = PptxRequest.encode(project, noNotes)
        assertEquals(
            request,
            PptxRequest.encode(
                project.copy(
                    updatedAt = 99,
                    slides = project.slides.map { it.copy(notes = "Other secret") },
                ),
                noNotes,
            ),
        )
        assertNotEquals(request, PptxRequest.encode(project, noNotes.copy(edge = 4096)))
        assertNotEquals(
            request,
            PptxRequest.encode(project.copy(theme = PresentationTheme.OCEAN), noNotes),
        )
        assertNotEquals(
            PptxRequest.encode(project, PptxOptions(includeNotes = true)),
            PptxRequest.encode(
                project.copy(slides = project.slides.map { it.copy(notes = "Other secret") }),
                PptxOptions(includeNotes = true),
            ),
        )
        assertEquals(PptxRequest.snapshot(project, noNotes) to noNotes, PptxRequest.decode(request))
    }

    @Test
    fun invalidXmlAndUnsupportedResolutionAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { PptxPackage.escape("bad\u0000") }
        assertThrows(IllegalArgumentException::class.java) { PptxPackage.escape("\uD800") }
        assertThrows(IllegalArgumentException::class.java) { PptxOptions(3000) }
        assertThrows(IllegalArgumentException::class.java) {
            PptxPackage(ByteArrayOutputStream(), 0, false)
        }
    }

    @Test
    fun pptxIsOutputOnlyAndNotesAreOptIn() {
        assertFalse(PptxOptions().includeNotes)
        assertTrue(
            FormatSupport.capabilities
                .single { it.tool == SupportedTool.PRESENTATION }
                .outputs
                .contains(PresentationPptxEngine.MIME)
        )
        assertFalse(FormatSupport.capabilities.any { PresentationPptxEngine.MIME in it.inputs })
    }

    @Test
    fun fullNotesCapacityRoundTripsWithoutSilentlyDroppingNotes() {
        val options = PptxOptions(includeNotes = true)
        val large =
            project.copy(
                slides =
                    (0 until 100).map {
                        Slide("Heading", "Body", id = "$it", notes = "ن".repeat(8000))
                    }
            )
        val json = PptxRequest.encode(large, options)
        assertTrue(json.toByteArray(Charsets.UTF_8).size > AtomicMediaSession.MAX_CHECKPOINT_BYTES)
        assertEquals(PptxRequest.snapshot(large, options) to options, PptxRequest.decode(json))
    }
}

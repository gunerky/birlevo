package com.example.contentstudio

import android.graphics.Bitmap
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.example.contentstudio.data.*
import com.example.contentstudio.media.*
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

class EditablePptxDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val engine get() = PresentationPptxEngine(context)
    private val image = "content://com.example.contentstudio.test.fixtures/orientation-6.jpg"
    private val project = StudioProject("editable", ProjectKind.PRESENTATION, "Birlevo & ideas <2026>", 1,
        SlideLayout.entries.mapIndexed { i, layout -> Slide("Heading ${i+1}", "İstanbul, café, Groß, Việt\nOne step at a time.", image,
            id="slide-$i", layout=layout, notes="Private العربية notes $i") } + Slide("Text only", "No image is required", id="text"))

    @Test fun nativeObjectsNotesAndOrientedImagesAreWrittenWithoutRasterizingText() = runBlocking(Dispatchers.IO) {
        val evidence=File(context.getExternalFilesDir(null),"qa/pptx-editable").apply{mkdirs()}
        for(theme in PresentationTheme.entries) for(notes in listOf(false,true)) {
            val source=project.copy(theme=theme)
            val options=PptxOptions(2048,notes,PptxMode.EDITABLE)
            val result=engine.create(source,options)
            try {
                assertEquals(PptxMode.EDITABLE,result.mode)
                ZipFile(result.file).use { zip ->
                    assertEquals(notes,zip.getEntry("ppt/notesSlides/notesSlide1.xml")!=null)
                    assertNotNull(zip.getEntry("birlevo/editable.xml"))
                    assertNull(zip.getEntry("ppt/media/image4.png"))
                    for(index in source.slides.indices) {
                        val doc=zip.getInputStream(zip.getEntry("ppt/slides/slide${index+1}.xml")).use {
                            DocumentBuilderFactory.newInstance().apply{isNamespaceAware=true}.newDocumentBuilder().parse(it)
                        }
                        val visible=doc.getElementsByTagNameNS("http://schemas.openxmlformats.org/drawingml/2006/main","t")
                        val text=(0 until visible.length).joinToString(""){visible.item(it).textContent}
                        assertTrue(text.contains(source.slides[index].heading))
                        assertTrue(text.contains(source.slides[index].body.replace("\n","")))
                        assertFalse(text.contains("Private"))
                        assertTrue(doc.getElementsByTagNameNS("http://schemas.openxmlformats.org/presentationml/2006/main","sp").length>=4)
                    }
                }
                val preview=engine.preview(result,0,source)
                try { assertEquals(512,preview.width); assertEquals(288,preview.height)
                    File(evidence,"${theme.name}-reference.png").outputStream().use{preview.compress(Bitmap.CompressFormat.PNG,100,it)} }
                finally {preview.recycle()}
                engine.export(source,result,Uri.fromFile(File(evidence,"${theme.name}-notes-$notes.pptx")))
            } finally {engine.discard(result.file)}
        }
        File(evidence,"expected.json").writeText(ProjectCodec.encode(listOf(project)))
    }

    @Test fun unsupportedScriptsEmojiAndDirectionControlsAreRejectedWithoutReplacingAppearanceMode() = runBlocking(Dispatchers.IO) {
        for(text in listOf("العربية","فارسی","中文","Привет","🙂","hello\u202Eworld")) {
            val source=project.copy(slides=listOf(Slide(text,"Body")))
            assertFalse(EditablePptxPolicy.supports(source))
            val before=engine.folder.listFiles()!!.map{it.name}.toSet()
            try {engine.create(source,PptxOptions(mode=PptxMode.EDITABLE));fail("Unsupported content must not be removed")}
            catch(_:IllegalArgumentException){}
            assertEquals(before,engine.folder.listFiles()!!.map{it.name}.toSet())
            if(text=="العربية") {
                val raster=engine.create(source,PptxOptions(1024))
                try {assertEquals(PptxMode.KEEP_APPEARANCE,raster.mode)}finally{engine.discard(raster.file)}
            }
        }
        assertFalse(EditablePptxPolicy.supports(project.copy(title="العربية")))
    }

    @Test fun legacyRequestsAndModeIdentityRestoreWithoutChangingOldDefaults() = runBlocking(Dispatchers.IO) {
        val legacy=PptxRequest.encode(project,PptxOptions(1024))
        assertFalse(JSONObject(legacy).has("mode"))
        assertEquals(PptxOptions(1024),PptxRequest.decode(legacy).second)
        val editable=PptxOptions(1024,true,PptxMode.EDITABLE)
        assertEquals(editable,PptxRequest.decode(PptxRequest.encode(project,editable)).second)
        assertNotEquals(legacy,PptxRequest.encode(project,editable))
        val result=engine.create(project,editable)
        try {
            assertEquals(result,engine.restore(result.file,result.pages,result.bytes,editable))
            try {engine.restore(result.file,result.pages,result.bytes,PptxOptions(1024,true));fail("Mode mismatch")}
            catch(_:IllegalArgumentException){}
        } finally {engine.discard(result.file)}
    }

    @Test fun cancellationAndOverflowCleanPartialArchivesAndKeepTheSource() = runBlocking(Dispatchers.IO) {
        val before=engine.folder.listFiles()!!.map{it.name}.toSet()
        val snapshot=ProjectCodec.encode(listOf(project))
        try {engine.create(project,PptxOptions(mode=PptxMode.EDITABLE)){throw CancellationException("QA")};fail("Must cancel")}
        catch(_:CancellationException){}
        val overflow=project.copy(slides=listOf(Slide("Heading","Very long text ".repeat(500),image,layout=SlideLayout.IMAGE_TOP)))
        try {engine.create(overflow,PptxOptions(mode=PptxMode.EDITABLE));fail("No silent truncation")}
        catch(_:PdfTextOverflow){}
        assertEquals(before,engine.folder.listFiles()!!.map{it.name}.toSet())
        assertEquals(snapshot,ProjectCodec.encode(listOf(project)))
    }
}

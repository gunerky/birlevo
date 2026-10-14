package com.example.contentstudio

import android.graphics.Bitmap
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.example.contentstudio.data.*
import com.example.contentstudio.media.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

/** Public editable export: original logical characters and measured visual order both survive. */
class PptxBidiDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val slides = listOf(
        Slide("مرحبا بالعالم", "مرحبا بالعالم", id="arabic"),
        Slide("سلام برای همه", "آزمایش متن و تصویر\nسلام برای همه", id="persian"),
        Slide("تقرير 2026", "مرحبا report_2026.pdf بالعالم ١٢٣\nآزمایش file.pdf برای همه ۱۲۳", id="mixed"),
        Slide("English and العربية", "  Hello العربية 123\n\nفارسی و Latin\nسلام‌دنیا", id="spaces"),
        Slide("تقرير2026", "report_عربي.pdf سلام123\nمرحبا\u00a0بالعالم", id="connected"),
        Slide("تقرير مُبسَّط", "مرحبًا بالعالم تقرير عربي مع صورة ونص طويل يحافظ على ترتيب الكلمات عند الانتقال إلى السطر التالي.",
            "content://com.example.contentstudio.test.fixtures/orientation-6.jpg", id="image", layout=SlideLayout.IMAGE_LEFT),
    )

    @Test fun positionedWordsPreserveEverySourceCharacterAndNativeOrder() = runBlocking(Dispatchers.IO) {
        val evidence=File(context.getExternalFilesDir(null),"qa/pptx-bidi").apply{mkdirs()}
        for(theme in PresentationTheme.entries) {
            val project=StudioProject("bidi",ProjectKind.PRESENTATION,"Birlevo تقرير 2026",1,slides,theme=theme)
            val target=File(evidence,"${theme.name}-positioned.pptx")
            assertTrue(EditablePptxPolicy.supports(project))
            assertTrue(EditablePptxPolicy.usesRtl(project))
            val engine=PresentationPptxEngine(context)
            val options=PptxOptions(mode=PptxMode.EDITABLE)
            val result=engine.create(project,options)
            try {
                assertEquals(result,engine.restore(result.file,result.pages,result.bytes,options))
                engine.export(project,result,Uri.fromFile(target))
                for(index in slides.indices) {
                        val measured=PresentationLayout.measure(project,index,slides[index].imageUri != null)
                        for(block in listOfNotNull(measured.caption,measured.heading,measured.body)) {
                            assertEquals(block.text,block.wordFragments().joinToString(""){it.text})
                        }
                        val first=measured.body!!.wordFragments()
                        if(index==0)assertTrue("Arabic first word is physically right",first[0].left>first[1].left)
                        if(index==5)assertTrue("Wrapped words must not acquire whole-column bounds",first.all{it.width<200f})
                        val bitmap=PresentationPdf(context).renderSlideBitmap(project,index,960,540)
                        try {File(evidence,"${theme.name}-native-${index+1}.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}}
                        finally{bitmap.recycle()}
                }
            } finally { engine.discard(result.file) }
            ZipFile(target).use { zip ->
                for(index in slides.indices) {
                    val doc=zip.getInputStream(zip.getEntry("ppt/slides/slide${index+1}.xml")).use {
                        DocumentBuilderFactory.newInstance().apply{isNamespaceAware=true}.newDocumentBuilder().parse(it)
                    }
                    val pieces=doc.getElementsByTagNameNS("http://schemas.openxmlformats.org/drawingml/2006/main","t")
                    val text=(0 until pieces.length).joinToString(""){pieces.item(it).textContent}
                    assertTrue(text.contains(slides[index].body))
                    assertTrue(text.contains(project.title))
                }
            }
        }
        File(evidence,"expected.json").writeText(ProjectCodec.encode(listOf(StudioProject("bidi",ProjectKind.PRESENTATION,"Birlevo تقرير 2026",1,slides))))
    }
}

package com.example.contentstudio.media

import com.example.contentstudio.data.*
import com.example.contentstudio.processing.ProcessingCodec
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SlideshowTimelineTest {
    private val asset =
        SessionAsset("source", "page-a.png", "content://test/image", "Image.png", 100)
    private val first = SlideshowFrame("first", asset, 750)

    @Test
    fun decimalKeyboardDigitsAndInvalidInputsUseTheSameTimingContract() {
        listOf("0.75", "0,75", "٠٫٧٥", "۰٫۷۵").forEach {
            assertEquals(750L, parseSlideshowDuration(it))
        }
        listOf("NaN", "Infinity", "1e1", "-1", "0.49", "60.1", "1,2,3", "").forEach {
            assertNull(parseSlideshowDuration(it))
        }
    }

    @Test
    fun exactTimingOrderOptionsAndUnicodeRoundTripWithDistinctRequests() {
        val value =
            SlideshowTimeline(
                listOf(first, SlideshowFrame("second", asset.copy(file = "page-b.png"), 1250)),
                title = "İstanbul مرحبا 日本語",
                canvas = VideoCanvas.PORTRAIT_9_16,
                quality = VideoQuality.HIGH,
                edge = 1920,
            )
        assertEquals(2000L, value.durationMs)
        assertEquals(value, SlideshowCodec.decode(SlideshowCodec.encode(value)))
        val fingerprint = ProcessingCodec.fingerprint(SlideshowCodec.encode(value))
        listOf(
                value.copy(frames = value.frames.reversed()),
                value.copy(fit = VideoFit.CENTER_CROP),
                value.copy(frames = listOf(first.copy(durationMs = 500), value.frames.last())),
            )
            .forEach {
                assertNotEquals(fingerprint, ProcessingCodec.fingerprint(SlideshowCodec.encode(it)))
            }
    }

    @Test
    fun targetNeverSilentlyReducesSelectedCanvasResolutionOrQuality() {
        for (edge in listOf(640, 1280, 1920)) for (quality in VideoQuality.entries) {
            val landscape =
                SlideshowTimeline(listOf(first), edge = edge, quality = quality).target()
            val portrait =
                SlideshowTimeline(
                        listOf(first),
                        edge = edge,
                        quality = quality,
                        canvas = VideoCanvas.PORTRAIT_9_16,
                    )
                    .target()
            val square =
                SlideshowTimeline(
                        listOf(first),
                        edge = edge,
                        quality = quality,
                        canvas = VideoCanvas.SQUARE,
                    )
                    .target()
            assertEquals(edge, landscape.width)
            assertEquals(edge * 9 / 16 / 2 * 2, landscape.height)
            assertEquals(landscape.height, portrait.width)
            assertEquals(edge, portrait.height)
            assertEquals(edge, square.width)
            assertEquals(edge, square.height)
            assertEquals(videoTarget(1920, 1080, edge, quality).bitrate, landscape.bitrate)
        }
    }

    @Test
    fun malformedTimelinesNeverFallBackToDefaultFramesOrDurations() {
        listOf(
                SlideshowTimeline(listOf(first.copy(durationMs = 0))),
                SlideshowTimeline(listOf(first, first)),
                SlideshowTimeline(List(11) { first.copy(id = "$it", durationMs = 60_000) }),
                SlideshowTimeline(listOf(first.copy(asset = asset.copy(file = "../page-a.png")))),
                SlideshowTimeline(listOf(first.copy(asset = asset.copy(bytes = -100)))),
                SlideshowTimeline(listOf(first), canvas = VideoCanvas.FIRST_CLIP),
            )
            .forEach {
                assertThrows(IllegalArgumentException::class.java) { SlideshowCodec.encode(it) }
            }
        val unknown =
            JSONObject(SlideshowCodec.encode(SlideshowTimeline(listOf(first)))).put("version", 2)
        assertThrows(IllegalArgumentException::class.java) {
            SlideshowCodec.decode(unknown.toString())
        }
    }

    @Test
    fun presentationKeepsDesignAndSharedAssetsWithoutDependingOnProjectCacheUris() {
        val value =
            SlideshowTimeline(
                listOf(
                    first.copy(
                        slide =
                            Slide("Heading", "Body", id = first.id, layout = SlideLayout.IMAGE_LEFT)
                    ),
                    SlideshowFrame("second", asset, 1500, Slide("", "", id = "second")),
                ),
                title = "Example",
                theme = PresentationTheme.OCEAN,
                presentation = true,
            )
        assertEquals(value, SlideshowCodec.decode(SlideshowCodec.encode(value)))
        assertEquals(1, value.assets.size)
        assertEquals(
            listOf("private://image", "private://image"),
            value.project { "private://image" }.slides.map { it.imageUri },
        )
        assertEquals(
            SlideLayout.IMAGE_LEFT,
            value.project { "private://image" }.slides.first().layout,
        )
        assertEquals(PresentationTheme.OCEAN, value.project { "private://image" }.theme)
    }

    @Test
    fun totalTimeAndImageBudgetAreIndependentAndBounded() {
        val longest = SlideshowTimeline(List(10) { first.copy(id = "$it", durationMs = 60_000) })
        assertEquals(600_000L, SlideshowCodec.decode(SlideshowCodec.encode(longest)).durationMs)
        val tooLarge =
            SlideshowTimeline(
                List(5) {
                    first.copy(
                        id = "$it",
                        asset =
                            asset.copy(file = "page-$it.png", bytes = FormatSupport.MAX_IMAGE_BYTES),
                    )
                }
            )
        assertThrows(IllegalArgumentException::class.java) { SlideshowCodec.encode(tooLarge) }
    }
}

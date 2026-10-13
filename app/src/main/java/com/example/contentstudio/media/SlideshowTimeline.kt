package com.example.contentstudio.media

import com.example.contentstudio.data.*
import org.json.JSONArray
import org.json.JSONObject

/** Still frames are a new video, never a compression success based on image byte totals. */
data class SlideshowFrame(
    val id: String,
    val asset: SessionAsset?,
    val durationMs: Long = 3000,
    val slide: Slide? = null,
)

data class SlideshowTimeline(
    val frames: List<SlideshowFrame> = emptyList(),
    val title: String = "",
    val theme: PresentationTheme = PresentationTheme.CLASSIC,
    val presentation: Boolean = false,
    val canvas: VideoCanvas = VideoCanvas.LANDSCAPE_16_9,
    val fit: VideoFit = VideoFit.FIT,
    val quality: VideoQuality = VideoQuality.BALANCED,
    val edge: Int = 1280,
) {
    val durationMs
        get() = frames.sumOf { it.durationMs }

    val assets
        get() = frames.mapNotNull { it.asset }.distinctBy { it.file }

    fun validate() {
        require(frames.size <= 100 && frames.map { it.id }.distinct().size == frames.size)
        require(title.length <= 500 && edge in listOf(640, 1280, 1920))
        require(canvas != VideoCanvas.FIRST_CLIP)
        require(frames.all { it.id.length in 1..128 && it.durationMs in 500..60_000 })
        require(durationMs <= FormatSupport.MAX_VIDEO_DURATION_MS)
        require(assets.sumOf { it.bytes } <= PhotoPdf.MAX_BATCH_BYTES)
        require(
            frames
                .mapNotNull { it.asset }
                .all {
                    it.bytes in 1..FormatSupport.MAX_IMAGE_BYTES &&
                        it.id.length in 1..128 &&
                        it.name.length <= 256 &&
                        it.uri.length in 1..8192 &&
                        it.file.startsWith("page-") &&
                        it.file == java.io.File(it.file).name &&
                        !it.file.contains('/') &&
                        !it.file.contains('\\')
                }
        )
        require(
            frames.all {
                if (presentation) it.slide?.id == it.id else it.slide == null && it.asset != null
            }
        )
        require(
            frames.all { frame ->
                frame.slide?.let {
                    it.heading.length <= 1000 && it.body.length <= 8000 && it.imageUri == null
                } != false
            }
        )
        require(
            frames.sumOf { (it.slide?.heading?.length ?: 0) + (it.slide?.body?.length ?: 0) } <=
                200_000
        )
    }

    fun target(): VideoTarget {
        validate()
        val (w, h) =
            when (canvas) {
                VideoCanvas.PORTRAIT_9_16 -> 1080 to 1920
                VideoCanvas.LANDSCAPE_16_9 -> 1920 to 1080
                VideoCanvas.SQUARE -> 1920 to 1920
                else -> error("No implicit source canvas")
            }
        return videoTarget(w, h, edge, quality)
    }

    fun project(imageUri: (SessionAsset) -> String): StudioProject =
        StudioProject(
            "slideshow",
            ProjectKind.PRESENTATION,
            title,
            0,
            frames.map { frame ->
                requireNotNull(frame.slide).copy(imageUri = frame.asset?.let(imageUri))
            },
            theme = theme,
        )
}

object SlideshowCodec {
    fun encode(value: SlideshowTimeline): String {
        value.validate()
        return JSONObject()
            .put("version", 1)
            .put("kind", "slideshow")
            .put("title", value.title)
            .put("theme", value.theme.name)
            .put("presentation", value.presentation)
            .put("canvas", value.canvas.name)
            .put("fit", value.fit.name)
            .put("quality", value.quality.name)
            .put("edge", value.edge)
            .put(
                "frames",
                JSONArray().also { list ->
                    value.frames.forEach { frame ->
                        list.put(
                            JSONObject()
                                .put("id", frame.id)
                                .put("duration", frame.durationMs)
                                .apply {
                                    frame.asset?.let {
                                        put(
                                            "asset",
                                            JSONObject()
                                                .put("id", it.id)
                                                .put("file", it.file)
                                                .put("uri", it.uri)
                                                .put("name", it.name)
                                                .put("bytes", it.bytes),
                                        )
                                    }
                                    frame.slide?.let {
                                        put(
                                            "slide",
                                            JSONObject()
                                                .put("heading", it.heading)
                                                .put("body", it.body)
                                                .put("layout", it.layout.name),
                                        )
                                    }
                                }
                        )
                    }
                },
            )
            .toString()
            .also {
                require(
                    it.toByteArray(Charsets.UTF_8).size <= AtomicMediaSession.MAX_CHECKPOINT_BYTES
                )
            }
    }

    fun decode(text: String): SlideshowTimeline {
        require(text.toByteArray(Charsets.UTF_8).size <= AtomicMediaSession.MAX_CHECKPOINT_BYTES)
        val root = JSONObject(text)
        require(root.getInt("version") == 1 && root.getString("kind") == "slideshow")
        val array = root.getJSONArray("frames")
        require(array.length() <= 100)
        return SlideshowTimeline(
                (0 until array.length()).map { index ->
                    val frame = array.getJSONObject(index)
                    val id = frame.getString("id")
                    val asset =
                        frame.optJSONObject("asset")?.let {
                            SessionAsset(
                                it.getString("id"),
                                it.getString("file"),
                                it.getString("uri"),
                                it.getString("name"),
                                it.getLong("bytes"),
                            )
                        }
                    SlideshowFrame(
                        id,
                        asset,
                        frame.getLong("duration"),
                        frame.optJSONObject("slide")?.let {
                            Slide(
                                it.getString("heading"),
                                it.getString("body"),
                                id = id,
                                layout = SlideLayout.valueOf(it.getString("layout")),
                            )
                        },
                    )
                },
                root.getString("title"),
                PresentationTheme.valueOf(root.getString("theme")),
                root.getBoolean("presentation"),
                VideoCanvas.valueOf(root.getString("canvas")),
                VideoFit.valueOf(root.getString("fit")),
                VideoQuality.valueOf(root.getString("quality")),
                root.getInt("edge"),
            )
            .also { it.validate() }
    }
}

/**
 * Decimal seconds in the user's keyboard digits; scientific notation/grouping is not a duration.
 */
fun parseSlideshowDuration(text: String): Long? {
    val normalized = buildString {
        text.trim().forEach { char ->
            val digit = Character.digit(char, 10)
            when {
                digit >= 0 -> append(digit)
                char in listOf('.', ',', '\u066b') -> append('.')
                else -> return null
            }
        }
    }
    val seconds =
        normalized.toDoubleOrNull()?.takeIf { it.isFinite() && it in 0.5..60.0 } ?: return null
    return kotlin.math.round(seconds * 1000).toLong()
}

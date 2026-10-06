package com.example.contentstudio.data

data class PresentationPalette(val background: Int, val text: Int, val muted: Int, val accent: Int)

enum class PresentationTheme(val palette: PresentationPalette) {
    CLASSIC(PresentationPalette(0xFFFFFFFF.toInt(), 0xFF1E232C.toInt(), 0xFF5C6067.toInt(), 0xFFAC4025.toInt())),
    OCEAN(PresentationPalette(0xFF0B1C3F.toInt(), 0xFFF1F5FD.toInt(), 0xFFBFCBDF.toInt(), 0xFF64DCC8.toInt())),
    WARM(PresentationPalette(0xFFFFF8EC.toInt(), 0xFF43342F.toInt(), 0xFF746258.toInt(), 0xFFC0563A.toInt())),
}

enum class SlideLayout { TEXT_IMAGE, IMAGE_LEFT, IMAGE_TOP }

data class SlideImageBox(val left: Float, val top: Float, val width: Float, val height: Float)
data class SlideGeometry(
    val textLeft: Float, val textTop: Int, val textWidth: Int, val textBottom: Int,
    val headingStart: Int = 44, val headingMinimum: Int = 28, val headingHeight: Int = 130,
    val bodyStart: Int = 26, val bodyMinimum: Int = 18,
    val image: SlideImageBox? = null,
)

/** Coordinates on the 960 x 540 PDF page, shared by editor previews and exported pages. */
fun slideGeometry(layout: SlideLayout, hasImage: Boolean): SlideGeometry {
    if (!hasImage) return SlideGeometry(64f, 104, 828, 474)
    return when (layout) {
        SlideLayout.TEXT_IMAGE -> SlideGeometry(64f, 104, 390, 474,
            image = SlideImageBox(500f, 104f, 396f, 366f))
        SlideLayout.IMAGE_LEFT -> SlideGeometry(506f, 104, 390, 474,
            image = SlideImageBox(64f, 104f, 396f, 366f))
        SlideLayout.IMAGE_TOP -> SlideGeometry(64f, 308, 828, 474,
            headingStart = 36, headingMinimum = 24, headingHeight = 72, bodyStart = 22, bodyMinimum = 16,
            image = SlideImageBox(64f, 104f, 828f, 180f))
    }
}

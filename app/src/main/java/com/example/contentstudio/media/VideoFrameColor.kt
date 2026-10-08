package com.example.contentstudio.media

import kotlin.math.roundToInt

/** Android color standard 1=709, 2/4=601, 6=2020; only SDR 8-bit input is admitted. */
internal object VideoFrameColor {
    // AOSP ColorUtils extended values: 64 + primaries + 7 * matrix coefficients.
    // Some streams declare their matrix while leaving primaries unspecified (e.g. 71 = BT709).
    fun matrix(standard: Int): Int = when (standard) {
        0, 2, 4 -> 3
        1 -> 1
        3, 8 -> 2
        5 -> 4
        6, 9 -> 5
        in 64 until 113 -> ((standard - 64) / 7).let { if (it == 0) 3 else it }
        else -> throw IllegalArgumentException("Unsupported frame color standard")
    }.also { require(it in 1..5) { "Unsupported frame color matrix" } }

    fun argb(y: Int, u: Int, v: Int, standard: Int, full: Boolean): Int {
        return convert(y, u, v, matrix(standard), full)
    }
    fun convert(y: Int, u: Int, v: Int, matrix: Int, full: Boolean): Int {
        val luma = if (full) y.toDouble() else (y - 16) * (255.0 / 219)
        val chroma = if (full) 1.0 else 255.0 / 224
        val cb = (u - 128) * chroma; val cr = (v - 128) * chroma
        val kr = when (matrix) { 1 -> .2126; 2 -> .30; 4 -> .212; 5 -> .2627; else -> .299 }
        val kb = when (matrix) { 1 -> .0722; 2 -> .11; 4 -> .087; 5 -> .0593; else -> .114 }
        fun channel(value: Double) = value.roundToInt().coerceIn(0, 255)
        val r = channel(luma + 2 * (1 - kr) * cr)
        val b = channel(luma + 2 * (1 - kb) * cb)
        val g = channel(luma - 2 * kb * (1 - kb) / (1 - kr - kb) * cb - 2 * kr * (1 - kr) / (1 - kr - kb) * cr)
        return (255 shl 24) or (r shl 16) or (g shl 8) or b
    }
}

package com.example.contentstudio

import androidx.compose.ui.graphics.Color
import com.example.contentstudio.ui.studioDark
import com.example.contentstudio.ui.studioLight
import org.junit.Test
import org.junit.Assert.assertTrue
import kotlin.math.pow

class WorkspaceColorTest {
    private fun luminance(c: Color): Double {
        fun channel(v: Float): Double = if (v <= .04045f) v / 12.92 else ((v + .055) / 1.055).pow(2.4)
        return .2126 * channel(c.red) + .7152 * channel(c.green) + .0722 * channel(c.blue)
    }
    @Test fun smallActionAndBodyTextHaveAccessibleContrastInBothThemes() {
        for (palette in listOf(studioLight, studioDark)) {
            val pairs = listOf(palette.primary to palette.onPrimary, palette.primaryContainer to palette.onPrimaryContainer,
                palette.background to palette.onBackground, palette.surface to palette.onSurface,
                palette.surface to palette.onSurfaceVariant, palette.background to palette.tertiary)
            for ((background, foreground) in pairs) {
                val a = luminance(background) + .05; val b = luminance(foreground) + .05
                val ratio = maxOf(a, b) / minOf(a, b)
                assertTrue("Text contrast $ratio for $background / $foreground", ratio >= 4.5)
            }
        }
    }
}

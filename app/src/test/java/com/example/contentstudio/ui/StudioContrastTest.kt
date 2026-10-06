package com.example.contentstudio.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.compositeOver
import org.junit.Test
import org.junit.Assert.assertTrue

class StudioContrastTest {
    private fun ratio(a: Color, b: Color): Float {
        val x = a.luminance(); val y = b.luminance()
        return (maxOf(x, y) + .05f) / (minOf(x, y) + .05f)
    }
    @Test fun bodyLabelsAndActionTextRemainReadableInBothThemes() {
        for (scheme in listOf(studioLight, studioDark)) {
            val pairs = listOf(scheme.onSurface to scheme.surface, scheme.onBackground to scheme.background,
                scheme.onSurfaceVariant to scheme.surface, scheme.onSurfaceVariant to scheme.surfaceContainerHigh,
                scheme.onPrimary to scheme.primary, scheme.onPrimaryContainer to scheme.primaryContainer,
                scheme.onSecondary to scheme.secondary, scheme.onSecondaryContainer to scheme.secondaryContainer,
                scheme.tertiary to scheme.surface, scheme.tertiary to scheme.tertiaryContainer,
                scheme.onTertiary to scheme.tertiary,
                scheme.onTertiaryContainer to scheme.tertiaryContainer, scheme.primary to scheme.surfaceContainerLow)
            pairs.forEach { (fg, bg) -> assertTrue("$fg / $bg = ${ratio(fg,bg)}", ratio(fg,bg) >= 4.5f) }
            assertTrue(ratio(scheme.outline, scheme.surfaceContainerLow) >= 3f)
        }
        // Hero actions are transparent: use the real gradient and its decorative overlay.
        for (background in listOf(StudioBrand.Navy, Color(0xFF103CA9),
            StudioBrand.Blue.copy(alpha = .28f).compositeOver(Color(0xFF103CA9)))) {
            assertTrue(ratio(Color(0xFFEAF6FF), background) >= 4.5f)
            assertTrue(ratio(studioHeroIconTint, background) >= 3f)
        }
        assertTrue(ratio(Color.White, Color(0xFF103CA9)) >= 4.5f)
    }
}

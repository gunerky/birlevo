package com.example.contentstudio.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.contentstudio.R

enum class StudioGlyph { IMAGE, VIDEO, SLIDES, TRIM, FORWARD, DROPDOWN }
internal val studioHeroIconTint = Color(0xFFFF9A90)

/** Small vector marks, decorative: the adjacent text provides the accessible action name. */
@Composable
fun StudioIcon(kind: StudioGlyph, tint: Color, modifier: Modifier = Modifier) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(modifier.size(24.dp)) {
        val unit = size.minDimension / 24f
        fun p(x: Float, y: Float) = Offset(x * unit, y * unit)
        val stroke = Stroke(1.7f * unit, cap = StrokeCap.Round)
        if (kind == StudioGlyph.DROPDOWN) {
            drawPath(Path().apply { moveTo(7 * unit, 10 * unit); lineTo(12 * unit, 15 * unit)
                lineTo(17 * unit, 10 * unit) }, tint, style = stroke)
        } else if (kind == StudioGlyph.FORWARD) {
            val path = Path().apply {
                moveTo((if (rtl) 14 else 10) * unit, 7 * unit)
                lineTo((if (rtl) 9 else 15) * unit, 12 * unit)
                lineTo((if (rtl) 14 else 10) * unit, 17 * unit)
            }
            drawPath(path, tint, style = stroke)
        } else {
            drawRoundRect(tint, p(3f, 4f), Size(18 * unit, 16 * unit), CornerRadius(3 * unit), style = stroke)
            when (kind) {
                StudioGlyph.IMAGE -> {
                    drawCircle(tint, 1.5f * unit, p(16f, 9f))
                    drawPath(Path().apply { moveTo(4 * unit, 17 * unit); lineTo(9 * unit, 11 * unit)
                        lineTo(13 * unit, 15 * unit); lineTo(16 * unit, 13 * unit); lineTo(20 * unit, 18 * unit) }, tint, style = stroke)
                }
                StudioGlyph.VIDEO -> drawPath(Path().apply { moveTo(10 * unit, 8 * unit)
                    lineTo(16 * unit, 12 * unit); lineTo(10 * unit, 16 * unit); close() }, tint)
                StudioGlyph.SLIDES -> {
                    drawRoundRect(tint, p(6f, 7f), Size(5 * unit, 5 * unit), CornerRadius(unit))
                    drawLine(tint, p(14f, 8f), p(18f, 8f), 1.7f * unit, StrokeCap.Round)
                    drawLine(tint, p(14f, 11f), p(18f, 11f), 1.7f * unit, StrokeCap.Round)
                    drawLine(tint, p(6f, 16f), p(17f, 16f), 1.7f * unit, StrokeCap.Round)
                }
                StudioGlyph.TRIM -> {
                    drawLine(tint, p(8f, 8f), p(8f, 16f), 2 * unit, StrokeCap.Round)
                    drawLine(tint, p(16f, 8f), p(16f, 16f), 2 * unit, StrokeCap.Round)
                    drawLine(tint, p(9f, 12f), p(15f, 12f), 1.7f * unit, StrokeCap.Round)
                }
                else -> Unit
            }
        }
    }
}

@Composable
fun IconBadge(kind: StudioGlyph, modifier: Modifier = Modifier) {
    Surface(modifier, color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(16.dp)) {
        Box(Modifier.padding(12.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
            StudioIcon(kind, MaterialTheme.colorScheme.tertiary)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudioHero(onImages: () -> Unit, onVideo: () -> Unit, onSlides: () -> Unit) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
        .background(Brush.linearGradient(listOf(StudioBrand.Navy, Color(0xFF103CA9))))) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(StudioBrand.Blue.copy(alpha = .28f), size.width * .40f, Offset(size.width, 0f))
            drawCircle(StudioBrand.Cyan.copy(alpha = .08f), size.width * .32f, Offset(0f, size.height * 1.2f))
            drawCircle(StudioBrand.Coral, 4.dp.toPx(), Offset(size.width - 26.dp.toPx(), size.height - 26.dp.toPx()))
        }
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.home_title), color = Color.White, style = MaterialTheme.typography.headlineLarge)
            Text(stringResource(R.string.studio_tagline), color = Color(0xFFDDE8FF), style = MaterialTheme.typography.bodyLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HeroAction(StudioGlyph.IMAGE, stringResource(R.string.short_images), "hero-images", onImages)
                HeroAction(StudioGlyph.TRIM, stringResource(R.string.video), "hero-video", onVideo)
                HeroAction(StudioGlyph.SLIDES, stringResource(R.string.short_slides), "hero-slides", onSlides)
            }
        }
    }
}

@Composable
private fun HeroAction(kind: StudioGlyph, label: String, tag: String, onClick: () -> Unit) {
    // Keep a generous touch target; no separate colored tile behind the icon or label.
    Row(Modifier.clip(RoundedCornerShape(16.dp)).clickable(role = Role.Button, onClick = onClick).testTag(tag)
        .heightIn(min = 48.dp).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StudioIcon(kind, studioHeroIconTint)
        Text(label, color = Color(0xFFEAF6FF), style = MaterialTheme.typography.labelLarge)
    }
}

/** Shared gently filled fields; exports retain their own slide theme. */
@Composable
fun studioFieldColors() = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    cursorColor = MaterialTheme.colorScheme.primary,
)

@Composable
fun ToolImportCard(kind: StudioGlyph, hint: String, chooseLabel: String, losses: String,
    enabled: Boolean, tag: String, onChoose: () -> Unit) {
    androidx.compose.material3.Card(
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            val largeText = LocalDensity.current.fontScale > 1.3f
            if (largeText) {
                IconBadge(kind)
                androidx.compose.material3.Button(onClick = onChoose, enabled = enabled,
                    modifier = Modifier.fillMaxWidth().testTag(tag)) { Text(chooseLabel) }
                Text(hint, style = MaterialTheme.typography.bodyLarge)
            } else {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                IconBadge(kind)
                Text(hint, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            }
            androidx.compose.material3.Button(onClick = onChoose, enabled = enabled,
                modifier = Modifier.fillMaxWidth().testTag(tag)) { Text(chooseLabel) }
            }
            Text(losses, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

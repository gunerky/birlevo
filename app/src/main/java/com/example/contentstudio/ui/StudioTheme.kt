package com.example.contentstudio.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object StudioBrand {
    val Navy = Color(0xFF08164D)
    val Blue = Color(0xFF315CFF)
    val Cyan = Color(0xFF38D7FE)
    val Coral = Color(0xFFFC5B54)
}

internal val studioLight = lightColorScheme(
    primary = StudioBrand.Navy, onPrimary = Color.White,
    primaryContainer = Color(0xFFE5ECF8), onPrimaryContainer = StudioBrand.Navy,
    secondary = Color(0xFF15637C), onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCF5FA), onSecondaryContainer = Color(0xFF134D60),
    tertiary = Color(0xFFB73B35), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFEAE7), onTertiaryContainer = Color(0xFF8A2924),
    background = Color(0xFFF5F7FC), onBackground = Color(0xFF152449),
    surface = Color.White, onSurface = Color(0xFF152449),
    surfaceVariant = Color(0xFFF0F3FA), onSurfaceVariant = Color(0xFF566581),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFAFBFF),
    surfaceContainer = Color(0xFFF0F3FA), surfaceContainerHigh = Color(0xFFEAF0FA),
    surfaceContainerHighest = Color(0xFFE4EAF6),
    outline = Color(0xFF7785A1), outlineVariant = Color(0xFFDEE5F1),
    inverseSurface = StudioBrand.Navy, inverseOnSurface = Color(0xFFF3F5FF),
)
internal val studioDark = darkColorScheme(
    primary = Color(0xFF9BDCEF), onPrimary = StudioBrand.Navy,
    primaryContainer = Color(0xFF263B59), onPrimaryContainer = Color(0xFFD9F3FF),
    secondary = Color(0xFF87D7ED), onSecondary = Color(0xFF083A49),
    secondaryContainer = Color(0xFF153F51), onSecondaryContainer = Color(0xFFC6F0FA),
    tertiary = Color(0xFFFF9A90), onTertiary = Color(0xFF541B16),
    tertiaryContainer = Color(0xFF4D292D), onTertiaryContainer = Color(0xFFFFDAD5),
    background = Color(0xFF080E24), onBackground = Color(0xFFEDF1FF),
    surface = Color(0xFF192947), onSurface = Color(0xFFEDF1FF),
    surfaceVariant = Color(0xFF202D49), onSurfaceVariant = Color(0xFFB7C2DB),
    surfaceContainerLowest = Color(0xFF0C142C), surfaceContainerLow = Color(0xFF121D38),
    surfaceContainer = Color(0xFF17223E), surfaceContainerHigh = Color(0xFF202D49),
    surfaceContainerHighest = Color(0xFF2A3756),
    outline = Color(0xFF8897B4), outlineVariant = Color(0xFF405371),
    inverseSurface = Color(0xFFEDF1FF), inverseOnSurface = StudioBrand.Navy,
)

@Composable
fun StudioTheme(content: @Composable () -> Unit) {
    val typography = Typography(
        headlineLarge = androidx.compose.ui.text.TextStyle(fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 38.sp),
        titleLarge = androidx.compose.ui.text.TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
        titleMedium = androidx.compose.ui.text.TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    )
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) studioDark else studioLight,
        typography = typography, shapes = Shapes(
            extraSmall = RoundedCornerShape(16.dp), small = RoundedCornerShape(20.dp),
            medium = RoundedCornerShape(24.dp), large = RoundedCornerShape(28.dp), extraLarge = RoundedCornerShape(32.dp),
        ), content = content)
}

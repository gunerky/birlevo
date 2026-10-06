package com.example.contentstudio.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.contentstudio.R
import com.example.contentstudio.data.*
import com.example.contentstudio.media.PresentationPdf
import com.example.contentstudio.media.PdfTextOverflow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private fun themeLabel(theme: PresentationTheme) = when (theme) {
    PresentationTheme.CLASSIC -> R.string.theme_classic
    PresentationTheme.OCEAN -> R.string.theme_ocean
    PresentationTheme.WARM -> R.string.theme_warm
}
private fun layoutLabel(layout: SlideLayout) = when (layout) {
    SlideLayout.TEXT_IMAGE -> R.string.layout_right
    SlideLayout.IMAGE_LEFT -> R.string.layout_left
    SlideLayout.IMAGE_TOP -> R.string.layout_top
}

@Composable
fun PresentationThemePicker(theme: PresentationTheme, enabled: Boolean, onSelect: (PresentationTheme) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Text(stringResource(R.string.presentation_theme), style = MaterialTheme.typography.titleSmall)
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.testTag("presentation-theme")) {
            ThemeSwatch(theme)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(themeLabel(theme)))
            Spacer(Modifier.width(8.dp))
            StudioIcon(StudioGlyph.DROPDOWN, MaterialTheme.colorScheme.primary)
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            PresentationTheme.entries.forEach { choice ->
                DropdownMenuItem(text = { Text(stringResource(themeLabel(choice))) },
                    leadingIcon = { ThemeSwatch(choice) },
                    onClick = { expanded = false; onSelect(choice) },
                    modifier = Modifier.testTag("theme-${choice.name}"))
            }
        }
    }
}

@Composable
private fun ThemeSwatch(theme: PresentationTheme) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(theme.palette.text, theme.palette.accent).forEach { color ->
            Box(Modifier.size(16.dp).background(Color(color), CircleShape))
        }
    }
}

@Composable
fun SlideLayoutPicker(layout: SlideLayout, index: Int, enabled: Boolean, onSelect: (SlideLayout) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Text(stringResource(R.string.slide_layout), style = MaterialTheme.typography.titleSmall)
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled,
            modifier = Modifier.testTag("slide-layout-$index")) {
            Text(stringResource(layoutLabel(layout)), modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(8.dp))
            StudioIcon(StudioGlyph.DROPDOWN, MaterialTheme.colorScheme.primary)
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            SlideLayout.entries.forEach { choice ->
                DropdownMenuItem(text = { Text(stringResource(layoutLabel(choice))) },
                    onClick = { expanded = false; onSelect(choice) },
                    modifier = Modifier.testTag("layout-${choice.name}-$index"))
            }
        }
    }
}

@Composable
fun PresentationSlidePreview(project: StudioProject, index: Int) {
    val context = LocalContext.current
    val slide = project.slides[index]
    var retry by remember { mutableIntStateOf(0) }
    val keys = arrayOf(project.id, project.title, project.theme, slide, index, project.slides.size, retry)
    var bitmap by remember(*keys) { mutableStateOf<Bitmap?>(null) }
    var error by remember(*keys) { mutableStateOf<Int?>(null) }
    LaunchedEffect(*keys) {
        delay(250)
        try {
            bitmap = com.example.contentstudio.media.PreviewLoader.load { PresentationPdf(context).previewSlide(project, index) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (overflow: PdfTextOverflow) { error = R.string.pdf_overflow }
        catch (_: Exception) { error = R.string.pdf_generate_error }
        catch (_: OutOfMemoryError) { error = R.string.pdf_generate_error }
    }
    bitmap?.let {
        Image(it.asImageBitmap(), contentDescription = stringResource(R.string.slide_design_preview),
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).testTag("slide-design-preview-$index"))
    } ?: if (error != null) {
        Text(if (error == R.string.pdf_overflow) stringResource(R.string.pdf_overflow, index + 1)
            else stringResource(requireNotNull(error)), color = MaterialTheme.colorScheme.error)
        TextButton(onClick = { retry++ }) { Text(stringResource(R.string.retry)) }
    } else {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f), contentAlignment = androidx.compose.ui.Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}

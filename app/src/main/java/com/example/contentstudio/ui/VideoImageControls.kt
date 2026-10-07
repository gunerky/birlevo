package com.example.contentstudio.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.contentstudio.R
import com.example.contentstudio.media.*
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToLong

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VideoImageControls(layer: VideoImageLayer?, preview: Bitmap?, durationMs: Long, enabled: Boolean,
    locale: Locale, onPick: () -> Unit, onChange: (VideoImageLayer?) -> Unit) {
    val sizeLabel = stringResource(R.string.video_image_size)
    val opacityLabel = stringResource(R.string.video_image_opacity)
    val startLabel = stringResource(R.string.video_trim_start)
    val endLabel = stringResource(R.string.video_trim_end)
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.video_image), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.video_image_hint), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onPick, enabled = enabled, modifier = Modifier.testTag("video-image-pick")) {
                Text(stringResource(R.string.choose_image))
            }
            if (layer != null) {
                Text(layer.asset.name)
                preview?.let { Image(it.asImageBitmap(), contentDescription = layer.asset.name,
                    contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().height(100.dp).testTag("video-image-preview")) }
                TextButton(onClick = { onChange(null) }, enabled = enabled,
                    modifier = Modifier.testTag("video-image-remove")) { Text(stringResource(R.string.remove_image)) }
                Text(stringResource(R.string.video_text_position))
                val rows = listOf(R.string.video_text_top, R.string.video_text_middle, R.string.video_text_bottom)
                val columns = listOf(R.string.video_text_left, R.string.video_text_middle, R.string.video_text_right)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VideoTextPosition.entries.forEach { position ->
                        FilterChip(layer.position == position, onClick = { onChange(layer.copy(position = position)) }, enabled = enabled,
                            label = { Text("${stringResource(rows[position.row])} · ${stringResource(columns[position.column])}") },
                            modifier = Modifier.testTag("video-image-position-${position.name}"))
                    }
                }
                fun percent(value: Float) = NumberFormat.getPercentInstance(locale).apply { maximumFractionDigits = 1 }.format(value)
                Text("$sizeLabel: ${percent(layer.size)}")
                Slider(layer.size, onValueChange = { onChange(layer.copy(size = it)) }, valueRange = .05f.. .8f, enabled = enabled,
                    modifier = Modifier.testTag("video-image-size").semantics { contentDescription = sizeLabel })
                Text("$opacityLabel: ${percent(layer.opacity)}")
                Slider(layer.opacity, onValueChange = { onChange(layer.copy(opacity = it)) }, valueRange = .05f..1f, enabled = enabled,
                    modifier = Modifier.testTag("video-image-opacity").semantics { contentDescription = opacityLabel })
                fun time(value: Long) = String.format(locale, "%.1f s", value / 1000.0)
                Text("$startLabel: ${time(layer.startMs)}")
                if (durationMs > 1) Slider(layer.startMs.toFloat().coerceIn(0f, (durationMs - 1).toFloat()),
                    onValueChange = { value -> val start = value.roundToLong().coerceIn(0, durationMs - 1)
                        onChange(layer.copy(startMs = start, endMs = layer.endMs?.coerceAtLeast(start + 1))) },
                    valueRange = 0f..(durationMs - 1).toFloat(), enabled = enabled,
                    modifier = Modifier.testTag("video-image-start").semantics { contentDescription = startLabel })
                Text("$endLabel: ${time(layer.end(durationMs))}")
                TextButton(onClick = { onChange(layer.copy(endMs = null)) }, enabled = enabled,
                    modifier = Modifier.testTag("video-image-full-end")) { Text(stringResource(R.string.video_text_full)) }
                if (layer.startMs < durationMs - 1) Slider(layer.end(durationMs).toFloat(),
                    onValueChange = { onChange(layer.copy(endMs = it.roundToLong().coerceIn(layer.startMs + 1, durationMs))) },
                    valueRange = (layer.startMs + 1).toFloat()..durationMs.toFloat(), enabled = enabled,
                    modifier = Modifier.testTag("video-image-end").semantics { contentDescription = endLabel })
            }
        }
    }
}

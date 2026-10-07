package com.example.contentstudio.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
fun VideoTextControls(layer: VideoTextLayer?, durationMs: Long, enabled: Boolean, locale: Locale,
    onChange: (VideoTextLayer?) -> Unit) {
    val title = stringResource(R.string.video_text)
    val sizeLabel = stringResource(R.string.video_text_size)
    val startLabel = stringResource(R.string.video_trim_start)
    val endLabel = stringResource(R.string.video_trim_end)
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.video_text), style = MaterialTheme.typography.titleMedium)
                Switch(layer != null, onCheckedChange = { onChange(if (it) VideoTextLayer() else null) },
                    enabled = enabled, modifier = Modifier.testTag("video-text-enable").semantics { contentDescription = title })
            }
            if (layer != null) {
                Text(stringResource(R.string.video_text_hint), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(layer.text, onValueChange = { value ->
                    if (value.length <= VideoTextLayer.MAX_LENGTH && value.count { it == '\n' } < 6)
                        onChange(layer.copy(text = value))
                }, enabled = enabled, label = { Text(stringResource(R.string.video_text)) },
                    supportingText = { Text(stringResource(R.string.video_text_limit, layer.text.length, VideoTextLayer.MAX_LENGTH)) },
                    modifier = Modifier.fillMaxWidth().testTag("video-text-input"))
                Text(stringResource(R.string.video_text_position))
                val rows = listOf(R.string.video_text_top, R.string.video_text_middle, R.string.video_text_bottom)
                val columns = listOf(R.string.video_text_left, R.string.video_text_middle, R.string.video_text_right)
                // Wrapping text buttons keep every choice reachable with larger accessibility fonts.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VideoTextPosition.entries.forEach { position ->
                        FilterChip(layer.position == position, onClick = { onChange(layer.copy(position = position)) },
                            enabled = enabled, label = { Text("${stringResource(rows[position.row])} · ${stringResource(columns[position.column])}") },
                            modifier = Modifier.testTag("video-text-position-${position.name}"))
                    }
                }
                Text("${stringResource(R.string.video_text_size)}: ${NumberFormat.getPercentInstance(locale).format(layer.size)}")
                Slider(layer.size, onValueChange = { onChange(layer.copy(size = it)) }, valueRange = .025f.. .10f,
                    enabled = enabled, modifier = Modifier.testTag("video-text-size").semantics { contentDescription = sizeLabel })
                Text(stringResource(R.string.video_text_color))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VideoTextColor.entries.forEach { color ->
                        FilterChip(layer.color == color, onClick = { onChange(layer.copy(color = color)) }, enabled = enabled,
                            label = { Text("#%06X".format(color.argb and 0xFFFFFF)) },
                            leadingIcon = { Surface(color = androidx.compose.ui.graphics.Color(color.argb),
                                shape = MaterialTheme.shapes.small, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.size(16.dp)) {} },
                            modifier = Modifier.testTag("video-text-color-${color.name}"))
                    }
                }
                fun time(value: Long) = String.format(locale, "%.1f s", value / 1000.0)
                Text("${stringResource(R.string.video_trim_start)}: ${time(layer.startMs)}")
                if (durationMs > 1) Slider(layer.startMs.toFloat().coerceIn(0f, (durationMs - 1).toFloat()),
                    onValueChange = { value ->
                        val start = value.roundToLong().coerceIn(0, durationMs - 1)
                        onChange(layer.copy(startMs = start, endMs = layer.endMs?.coerceAtLeast(start + 1)))
                    }, valueRange = 0f..(durationMs - 1).toFloat(), enabled = enabled,
                    modifier = Modifier.testTag("video-text-start").semantics { contentDescription = startLabel })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${stringResource(R.string.video_trim_end)}: ${time(layer.end(durationMs))}")
                    TextButton(onClick = { onChange(layer.copy(endMs = null)) }, enabled = enabled,
                        modifier = Modifier.testTag("video-text-full-end")) { Text(stringResource(R.string.video_text_full)) }
                }
                if (layer.startMs < durationMs - 1) Slider(layer.end(durationMs).toFloat(),
                    onValueChange = { value -> onChange(layer.copy(endMs = value.roundToLong().coerceIn(layer.startMs + 1, durationMs))) },
                    valueRange = (layer.startMs + 1).toFloat()..durationMs.toFloat(), enabled = enabled,
                    modifier = Modifier.testTag("video-text-end").semantics { contentDescription = endLabel })
            }
        }
    }
}

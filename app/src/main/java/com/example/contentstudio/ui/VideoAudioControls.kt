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
import com.example.contentstudio.media.VideoAudioLayer
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToLong

@Composable
fun VideoAudioControls(layer: VideoAudioLayer?, durationMs: Long, enabled: Boolean, locale: Locale,
    onPick: () -> Unit, onChange: (VideoAudioLayer?) -> Unit) {
    val volumeLabel = stringResource(R.string.video_audio_volume)
    val originalLabel = stringResource(R.string.video_audio_original_volume)
    val placementLabel = stringResource(R.string.video_audio_start)
    val startLabel = stringResource(R.string.audio_start_seconds)
    val endLabel = stringResource(R.string.audio_end_seconds)
    fun time(ms: Long) = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 3 }.format(ms / 1000.0)
    fun percent(value: Float) = NumberFormat.getPercentInstance(locale).format(value)
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.video_audio), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.video_audio_hint), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onPick, enabled = enabled, modifier = Modifier.testTag("video-audio-pick")) { Text(stringResource(R.string.audio_choose)) }
            if (layer != null) {
                Text(layer.asset.name)
                Text(stringResource(R.string.audio_limits), style = MaterialTheme.typography.bodySmall)
                TextButton({ onChange(null) }, enabled = enabled, modifier = Modifier.testTag("video-audio-remove")) { Text(stringResource(R.string.audio_remove)) }
                Text(stringResource(R.string.audio_trim), style = MaterialTheme.typography.titleSmall)
                Text("$startLabel: ${time(layer.sourceStartMs)}")
                if (layer.durationMs > 1) Slider(layer.sourceStartMs.toFloat(), { value ->
                    val start = value.roundToLong().coerceIn(0, layer.durationMs - 1)
                    onChange(layer.copy(sourceStartMs = start, sourceEndMs = maxOf(layer.sourceEndMs, start + 1)))
                }, enabled = enabled, valueRange = 0f..(layer.durationMs - 1).toFloat(),
                    modifier = Modifier.testTag("video-audio-source-start").semantics { contentDescription = startLabel })
                Text("$endLabel: ${time(layer.sourceEndMs)}")
                if (layer.sourceStartMs < layer.durationMs - 1) Slider(layer.sourceEndMs.toFloat(), { value ->
                    onChange(layer.copy(sourceEndMs = value.roundToLong().coerceIn(layer.sourceStartMs + 1, layer.durationMs)))
                }, enabled = enabled, valueRange = (layer.sourceStartMs + 1).toFloat()..layer.durationMs.toFloat(),
                    modifier = Modifier.testTag("video-audio-source-end").semantics { contentDescription = endLabel })
                Text("$placementLabel: ${time(layer.startMs)} s")
                if (durationMs > 1) Slider(layer.startMs.toFloat().coerceIn(0f, (durationMs - 1).toFloat()),
                    { onChange(layer.copy(startMs = it.roundToLong().coerceIn(0, durationMs - 1))) }, enabled = enabled,
                    valueRange = 0f..(durationMs - 1).toFloat(), modifier = Modifier.testTag("video-audio-start").semantics { contentDescription = placementLabel })
                Text("$volumeLabel: ${percent(layer.volume)}")
                Slider(layer.volume, { onChange(layer.copy(volume = it)) }, enabled = enabled, valueRange = 0f..1f,
                    modifier = Modifier.testTag("video-audio-volume").semantics { contentDescription = volumeLabel })
                Text("$originalLabel: ${percent(layer.originalVolume)}")
                Slider(layer.originalVolume, { onChange(layer.copy(originalVolume = it)) }, enabled = enabled, valueRange = 0f..1f,
                    modifier = Modifier.testTag("video-audio-original-volume").semantics { contentDescription = originalLabel })
            }
        }
    }
}

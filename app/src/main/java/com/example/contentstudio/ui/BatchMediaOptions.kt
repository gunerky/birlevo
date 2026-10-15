package com.example.contentstudio.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.contentstudio.R
import com.example.contentstudio.batch.*
import com.example.contentstudio.media.*
import com.example.contentstudio.audio.*

internal fun videoQualityText(quality: VideoQuality) = when(quality) {
    VideoQuality.SMALL -> R.string.quality_small; VideoQuality.BALANCED -> R.string.quality_balanced; VideoQuality.HIGH -> R.string.quality_high
}
internal fun batchVideoFormatLabel(format: VideoOutputFormat) = when(format) {
    VideoOutputFormat.MP4_AVC -> "MP4 · H.264"; VideoOutputFormat.MP4_HEVC -> "MP4 · HEVC"
    VideoOutputFormat.WEBM_VP8 -> "WebM · VP8"; VideoOutputFormat.WEBM_VP9 -> "WebM · VP9"
}
internal fun batchAudioIssueText(issue: AudioOutputIssue) = when(issue) {
    AudioOutputIssue.ANDROID_VERSION -> R.string.audio_output_android; AudioOutputIssue.ENCODER -> R.string.audio_output_encoder
    AudioOutputIssue.DECODER -> R.string.audio_output_decoder; AudioOutputIssue.CHECK_FAILED -> R.string.audio_output_check
}
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BatchMediaOptions(options: BatchOptions, enabled: Boolean, change: (BatchOptions) -> Unit) {
    Text(stringResource(R.string.output_format))
    if (options.kind == BatchKind.VIDEO) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VideoOutputFormat.entries.forEach { format ->
                FilterChip(selected = options.videoFormat == format, enabled = enabled,
                    onClick = { change(options.copy(videoFormat = format)) },
                    label = { Text(batchVideoFormatLabel(format)) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("batch-video-format-${format.name}"))
            }
        }
        Text(stringResource(R.string.image_quality))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VideoQuality.entries.forEach { quality -> FilterChip(selected = options.videoQuality == quality, enabled = enabled,
                onClick = { change(options.copy(videoQuality = quality)) }, label = { Text(stringResource(videoQualityText(quality))) },
                modifier = Modifier.heightIn(min = 48.dp).testTag("batch-video-quality-${quality.name}")) }
        }
        Text(stringResource(R.string.max_edge))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(640, 1280, 1920).forEach { edge -> FilterChip(selected = options.videoEdge == edge, enabled = enabled,
                onClick = { change(options.copy(videoEdge = edge)) }, label = { Text("$edge px") },
                modifier = Modifier.heightIn(min = 48.dp).testTag("batch-video-edge-$edge")) }
        }
        Text(stringResource(R.string.video_output_hint))
        Text(stringResource(when(options.videoFormat) {
            VideoOutputFormat.WEBM_VP8 -> R.string.video_webm_losses
            VideoOutputFormat.WEBM_VP9 -> R.string.video_vp9_losses
            else -> R.string.video_losses
        }))
    } else {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AudioTarget.entries.forEach { target -> FilterChip(selected = options.audioTarget == target, enabled = enabled,
                onClick = { change(options.copy(audioTarget = target)) }, label = { Text(target.label) },
                modifier = Modifier.heightIn(min = 48.dp).testTag("batch-audio-target-${target.name}")) }
        }
        if (options.audioTarget.lossy) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AudioPolicy.bitrates.forEach { bitrate -> FilterChip(selected = options.audioBitrate == bitrate, enabled = enabled,
                onClick = { change(options.copy(audioBitrate = bitrate)) }, label = { Text("${bitrate / 1000} kbps") },
                modifier = Modifier.heightIn(min = 48.dp).testTag("batch-audio-rate-$bitrate")) }
        }
        Text(stringResource(when(options.audioTarget) {
            AudioTarget.OGG -> R.string.audio_ogg_description; AudioTarget.FLAC -> R.string.audio_flac_description
            AudioTarget.MP3 -> R.string.audio_mp3_description; else -> R.string.audio_pcm_description
        }))
        Text(stringResource(R.string.audio_metadata))
    }
}

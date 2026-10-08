package com.example.contentstudio.ui

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.contentstudio.R
import com.example.contentstudio.media.*
import java.io.File
import kotlin.math.roundToLong

@Composable
fun VideoFrameDialog(targets: List<VideoFrameTarget>, originals: Set<Uri>, model: VideoFrameViewModel,
    onImage: suspend (File, Set<Uri>) -> Boolean, onClose: () -> Unit, onTransferred: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]
    LaunchedEffect(targets) {
        if (state.target !in targets && !state.busy) targets.firstOrNull()?.let { model.select(it) }
    }
    fun close() { model.clear(); onClose() }
    Dialog(onDismissRequest = { if (!state.busy) close() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.padding(16.dp).widthIn(max = 640.dp).fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
            Column(Modifier.heightIn(max = 720.dp).verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.video_frame), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.video_frame_hint), style = MaterialTheme.typography.bodySmall)
                targets.forEachIndexed { index, target ->
                    FilterChip(state.target == target, onClick = { model.select(target) }, enabled = !state.busy,
                        label = { Text(target.name) }, modifier = Modifier.fillMaxWidth().testTag("video-frame-target-$index"))
                }
                state.target?.let { target ->
                    Text(stringResource(R.string.video_frame_time, String.format(locale, "%.3f", state.timeMs / 1000.0)))
                    if (target.durationMs > 1) Slider(state.timeMs.toFloat(), onValueChange = {
                        model.select(target, it.roundToLong().coerceIn(0, target.durationMs - 1))
                    }, valueRange = 0f..(target.durationMs - 1).toFloat(), enabled = !state.busy,
                        modifier = Modifier.testTag("video-frame-time"))
                    Button(onClick = model::preview, enabled = !state.busy, modifier = Modifier.testTag("video-frame-preview")) {
                        Text(stringResource(R.string.image_preview))
                    }
                }
                state.frame?.let { frame ->
                    Image(frame.bitmap.asImageBitmap(), stringResource(R.string.video_frame), contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().height(180.dp).testTag("video-frame-image"))
                    Text(stringResource(R.string.video_frame_actual, String.format(locale, "%.3f", frame.timeUs / 1000000.0)),
                        modifier = Modifier.testTag("video-frame-actual"))
                    Button(onClick = { model.transfer(originals, onImage) { model.clear(); onTransferred() } },
                        enabled = !state.busy, modifier = Modifier.testTag("video-frame-transfer")) {
                        Text(stringResource(R.string.video_frame_to_image))
                    }
                }
                if (state.busy) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    TextButton(onClick = model::cancel, modifier = Modifier.testTag("video-frame-cancel")) { Text(stringResource(R.string.cancel)) }
                }
                if (state.error) Text(stringResource(R.string.video_frame_error), color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("video-frame-error"))
                TextButton(onClick = ::close, enabled = !state.busy, modifier = Modifier.testTag("video-frame-close")) { Text(stringResource(R.string.close)) }
            }
        }
    }
}

package com.example.contentstudio.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.contentstudio.R
import com.example.contentstudio.audio.*
import com.example.contentstudio.media.*
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AudioToolScreen(model: AudioToolViewModel, onClose: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val saveStatus by model.sessionStatus.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(model::load) }
    var expected by rememberSaveable { mutableStateOf<String?>(null) }
    var original by rememberSaveable { mutableStateOf<String?>(null) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) it.data?.data?.let { uri -> model.export(uri, expected, original) }
        expected = null; original = null
    }
    // Playback paths are deliberately not saved across process death or input replacement.
    var playing by remember(state.source?.file?.path, state.result?.file?.path) { mutableStateOf<File?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(model, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) model.flushSession() }
        lifecycle.addObserver(observer); onDispose { lifecycle.removeObserver(observer); model.flushSession() }
    }
    fun close() { model.leave(); onClose() }
    BackHandler { close() }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.audio_tool), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = { TextButton(onClick = ::close, modifier = Modifier.testTag("audio-back")) { Text(stringResource(R.string.back)) } }) }) { padding ->
        LazyColumn(Modifier.widthIn(max = 900.dp).fillMaxSize().padding(padding).padding(horizontal = 20.dp).testTag("audio-list"),
            contentPadding = PaddingValues(vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(key = "choose") { ToolImportCard(StudioGlyph.AUDIO, "MP3 / M4A / WAV / OGG / FLAC + VIDEO",
                stringResource(R.string.audio_choose), stringResource(R.string.audio_limits), !state.busy, "choose-audio") {
                pick.launch((AudioPolicy.inputMimes + FormatSupport.videoMimes + listOf("audio/x-wav", "audio/x-flac", "application/ogg")).toTypedArray())
            } }
            item(key = "support") { SupportedFilesButton(SupportedTool.AUDIO) }
            item(key = "session") { MediaSessionControls(saveStatus, model.sessionRestoreFailed, !state.busy, "audio", model::retrySession, model::reset) }
            item(key = "status") { ProcessingStatusHint(state.workStatus, "audio") }
            state.source?.let { source ->
                item(key = "source") {
                    Text(source.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("audio-source"))
                    Text(Formatter.formatShortFileSize(context, source.bytes) + " · ${source.info.sampleRate} Hz · ${source.info.channels} ch · " + seconds(source.info.durationMs / 1000))
                    TextButton(onClick = { playing = source.file }, enabled = !state.busy, modifier = Modifier.testTag("play-source-audio")) { Text(stringResource(R.string.audio_play)) }
                }
                item(key = "options") {
                    Text(stringResource(R.string.output_format), style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { AudioTarget.entries.forEach { target ->
                        FilterChip(state.target == target, { model.setOptions(target = target) }, enabled = !state.busy,
                            label = { Text(target.name) }, modifier = Modifier.testTag("audio-target-${target.name}"))
                    } }
                    if (state.target == AudioTarget.M4A) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { AudioPolicy.bitrates.forEach { rate ->
                        FilterChip(state.bitrate == rate, { model.setOptions(bitrate = rate) }, enabled = !state.busy,
                            label = { Text("${rate / 1000} kbps") }, modifier = Modifier.testTag("audio-rate-$rate"))
                    } }
                    Text(stringResource(R.string.audio_losses), style = MaterialTheme.typography.bodySmall)
                    Button(onClick = model::preview, enabled = !state.busy, modifier = Modifier.testTag("audio-create")) { Text(stringResource(R.string.audio_convert)) }
                }
                state.result?.let { result -> item(key = "result") {
                    Text(state.target.name + " · " + Formatter.formatShortFileSize(context, result.bytes), style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.testTag("audio-result"))
                    Text(stringResource(R.string.original_size) + ": " + Formatter.formatShortFileSize(context, source.bytes))
                    TextButton(onClick = { playing = result.file }, enabled = !state.busy, modifier = Modifier.testTag("play-output-audio")) { Text(stringResource(R.string.audio_play)) }
                    val target = state.target
                    val name = source.name.substringBeforeLast('.').replace('/', '_').replace('\\', '_').filterNot { it.isISOControl() }.take(64).ifBlank { "audio" } + ".${target.extension}"
                    fun export() {
                        expected = result.file.name; original = source.originalUri.toString()
                        save.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(target.mime).putExtra(Intent.EXTRA_TITLE, name))
                    }
                    CompressionExportActions(result.file.path, compressionBenefit(source.bytes, result.bytes), !state.busy,
                        stringResource(R.string.audio_save), "audio-save", onSave = ::export,
                        onSaveAnyway = { if (model.confirmNonSmallerCopy(result.file)) export() })
                    OutputActions(result.file, target.mime, name, !state.busy, source.bytes)
                } }
            }
            if (state.busy) item(key = "busy") {
                state.progress?.let { LinearProgressIndicator(progress = { it / 100f }, modifier = Modifier.fillMaxWidth()) } ?: LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(R.string.processing))
                TextButton(onClick = model::cancel, modifier = Modifier.testTag("audio-cancel")) { Text(stringResource(R.string.cancel)) }
            }
            state.message?.let { message -> item(key = "message") {
                Text(stringResource(when (message) {
                    AudioMessage.LOAD_ERROR -> R.string.audio_error
                    AudioMessage.PROCESS_ERROR -> R.string.audio_process_error
                    AudioMessage.EXPORT_ERROR -> R.string.video_export_error
                    AudioMessage.EXPORTED -> R.string.audio_saved
                }), color = if (message == AudioMessage.EXPORTED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("audio-message"))
            } }
        }
    }
    playing?.let { AudioPlayback(it) { playing = null } }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun AudioPlayback(file: File, onClose: () -> Unit) {
    val context = LocalContext.current; val lifecycle = LocalLifecycleOwner.current.lifecycle
    var failed by remember(file.path) { mutableStateOf(false) }
    val player = remember(file.path) { ExoPlayer.Builder(context).build().apply {
        trackSelectionParameters = trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true).build()
        setMediaItem(MediaItem.fromUri(Uri.fromFile(file))); prepare()
    } }
    DisposableEffect(player, lifecycle) {
        val listener = object : Player.Listener { override fun onPlayerError(error: PlaybackException) { failed = true } }
        player.addListener(listener)
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) player.pause() }
        lifecycle.addObserver(observer); onDispose { lifecycle.removeObserver(observer); player.removeListener(listener); player.release() }
    }
    AlertDialog(onDismissRequest = onClose, title = { Text(stringResource(R.string.audio_play)) },
        text = { Column {
            AndroidView(factory = { PlayerView(it).apply { this.player = player; controllerShowTimeoutMs = 0 } },
                modifier = Modifier.fillMaxWidth().height(140.dp).testTag("audio-player"))
            if (failed) Text(stringResource(R.string.audio_error), color = MaterialTheme.colorScheme.error)
        } },
        confirmButton = { TextButton(onClick = onClose, modifier = Modifier.testTag("audio-close-player")) { Text(stringResource(R.string.close)) } })
}

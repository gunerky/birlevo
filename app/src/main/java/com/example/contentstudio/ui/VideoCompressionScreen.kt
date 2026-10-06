package com.example.contentstudio.ui

import android.graphics.Bitmap
import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.contentstudio.R
import com.example.contentstudio.media.*
import java.io.File
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VideoCompressionScreen(onClose: () -> Unit, editMode: Boolean = false,
    model: VideoCompressionViewModel = viewModel(), initialUri: Uri? = null,
    onAddToAudio: (suspend (Uri) -> Boolean)? = null) {
    val state by model.state.collectAsStateWithLifecycle()
    val sessionStatus by model.sessionStatus.collectAsStateWithLifecycle()
    var initialAttempt by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(initialUri, state.busy) {
        if (initialUri != null && !state.busy && initialAttempt != initialUri.toString()) {
            initialAttempt = initialUri.toString()
            if (state.source?.originalUri != initialUri) model.load(initialUri)
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(model, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) model.flushSession() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    var playingPath by rememberSaveable { mutableStateOf<String?>(null) }
    var playingStartMs by rememberSaveable { mutableLongStateOf(0L) }
    var playingEndMs by rememberSaveable { mutableStateOf<Long?>(null) }
    LaunchedEffect(state.busy, state.source?.file, state.result?.file) {
        if (!state.busy && playingPath != null && playingPath !in listOf(state.source?.file?.path, state.result?.file?.path)) playingPath = null
    }
    fun play(file: File, trim: VideoTrim? = null) {
        playingPath = file.path
        playingStartMs = trim?.startMs ?: 0L
        playingEndMs = trim?.endMs
    }
    fun close() { playingPath = null; model.leave(); onClose() }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(model::load) }
    var pickerResult by rememberSaveable { mutableStateOf<String?>(null) }
    var pickerOriginal by rememberSaveable { mutableStateOf<String?>(null) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("video/mp4")) { uri ->
        uri?.let { model.export(it, pickerResult, pickerOriginal) }; pickerResult = null; pickerOriginal = null
    }
    BackHandler { close() }
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(if (editMode) R.string.edit_video else R.string.compress_video),
            maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = { TextButton(onClick = ::close, modifier = Modifier.testTag("video-compression-back")) {
                Text(stringResource(R.string.back))
            } })
    }) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            val wide = maxWidth >= 700.dp
            Column(Modifier.widthIn(max = 1100.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ToolImportCard(if (editMode) StudioGlyph.TRIM else StudioGlyph.VIDEO,
                    stringResource(if (editMode) R.string.video_trim_hint else R.string.video_compression_hint),
                    stringResource(R.string.choose_video), stringResource(R.string.video_losses),
                    !state.busy, "choose-compression-video") { pick.launch(com.example.contentstudio.media.FormatSupport.videoMimes.toTypedArray()) }
                SupportedFilesButton(com.example.contentstudio.media.SupportedTool.VIDEO)
                Text(stringResource(R.string.media_session_hint), style = MaterialTheme.typography.bodySmall)
                MediaSessionControls(sessionStatus, model.sessionRestoreFailed, !state.busy, "video", model::retrySession) {
                    playingPath = null; model.reset()
                }
                ProcessingStatusHint(state.workStatus, "video")
                state.source?.let { source ->
                    val trim = state.trim ?: VideoTrim(0, source.info.durationMs)
                    Text(source.name, style = MaterialTheme.typography.titleMedium)
                    if (wide) Row(Modifier.testTag("video-compression-wide"), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        VideoPreviewCard(source.file, source.info, source.bytes, stringResource(R.string.original_size), !state.busy,
                            { play(it, trim) }, Modifier.weight(1f))
                        state.result?.let { result -> VideoPreviewCard(result.file, result.info, result.bytes,
                            stringResource(R.string.output_size), !state.busy, { play(it) }, Modifier.weight(1f)) }
                    } else Column(Modifier.testTag("video-compression-compact"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        VideoPreviewCard(source.file, source.info, source.bytes, stringResource(R.string.original_size), !state.busy,
                            { play(it, trim) })
                        state.result?.let { result -> VideoPreviewCard(result.file, result.info, result.bytes,
                            stringResource(R.string.output_size), !state.busy, { play(it) }) }
                    }
                    VideoTrimControls(source.info.durationMs, trim, !state.busy, model::setTrim,
                        onPreview = { play(source.file, trim) }, locale = configuration.locales[0])
                    Text(stringResource(R.string.image_quality))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VideoQuality.entries.forEach { quality ->
                            FilterChip(state.quality == quality, onClick = { model.setOptions(quality = quality) },
                                enabled = !state.busy, label = { Text(stringResource(when (quality) {
                                    VideoQuality.SMALL -> R.string.quality_small
                                    VideoQuality.BALANCED -> R.string.quality_balanced
                                    VideoQuality.HIGH -> R.string.quality_high
                                })) }, modifier = Modifier.testTag("video-quality-${quality.name}"))
                        }
                    }
                    Text(stringResource(R.string.max_edge))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(640, 1280, 1920).forEach { edge ->
                            FilterChip(state.maxEdge == edge, onClick = { model.setOptions(maxEdge = edge) },
                                enabled = !state.busy, label = { Text(pixels(edge)) }, modifier = Modifier.testTag("video-edge-$edge"))
                        }
                    }
                    val target = videoTarget(source.info.width, source.info.height, state.maxEdge, state.quality)
                    if (trim.startMs == 0L && trim.endMs == source.info.durationMs &&
                        videoSizeIncreaseRisk(source.bytes, source.info.durationMs, target.bitrate)) {
                        Text(stringResource(R.string.video_size_risk),
                            modifier = Modifier.testTag("video-size-risk"), color = MaterialTheme.colorScheme.error)
                    }
                    Button(onClick = model::preview, enabled = !state.busy, modifier = Modifier.testTag("process-video")) {
                        Text(stringResource(R.string.process_video))
                    }
                    state.result?.let { result ->
                        val benefit = compressionBenefit(source.bytes, result.bytes)
                        val label = if (benefit == CompressionBenefit.LARGER) R.string.size_increase else R.string.size_change
                        Text(if (benefit == CompressionBenefit.SAME_SIZE) stringResource(R.string.size_unchanged)
                            else "${stringResource(label)}: ${NumberFormat.getPercentInstance(configuration.locales[0])
                                .format(kotlin.math.abs(1.0 - result.bytes.toDouble() / source.bytes))}",
                            modifier = Modifier.testTag("video-size-change"))
                        if (result.bytes >= source.bytes) Text(stringResource(R.string.video_not_smaller))
                        fun saveCopy() {
                            val base = source.name.substringBeforeLast('.').replace('/', '_').replace('\\', '_')
                                .filterNot { it.isISOControl() }.take(64).ifBlank { "video" }
                            pickerResult = result.file.name; pickerOriginal = source.originalUri.toString()
                            save.launch("$base-edited.mp4")
                        }
                        CompressionExportActions(result.file.path, benefit, !state.busy,
                            stringResource(R.string.export_video), "export-video", onSave = ::saveCopy,
                            onSaveAnyway = { if (model.confirmNonSmallerCopy(result.file)) saveCopy() })
                        OutputActions(result.file, "video/mp4", source.name.substringBeforeLast('.') + "-edited.mp4",
                            !state.busy, source.bytes, onAddToAudio = if (result.info.hasAudio) onAddToAudio else null)
                    }
                }
                if (state.busy) {
                    state.progress?.let { progress ->
                        LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth().testTag("video-progress"))
                        Text(NumberFormat.getPercentInstance(configuration.locales[0]).format(progress / 100.0))
                    } ?: CircularProgressIndicator(Modifier.testTag("video-progress"))
                    Text(stringResource(R.string.processing))
                    TextButton(onClick = model::cancel, modifier = Modifier.testTag("cancel-video")) { Text(stringResource(R.string.cancel)) }
                }
                state.message?.let { message ->
                    Text(stringResource(state.issue?.let(::importIssueText) ?: when (message) {
                        VideoMessage.LOAD_ERROR -> R.string.video_load_error
                        VideoMessage.PROCESS_ERROR -> R.string.video_process_error
                        VideoMessage.EXPORT_ERROR -> R.string.video_export_error
                        VideoMessage.EXPORTED -> R.string.video_saved
                    }), color = if (message == VideoMessage.EXPORTED) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error, modifier = Modifier.testTag("video-message"))
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
    playingPath?.let { path -> VideoPlayerDialog(path, playingStartMs, playingEndMs,
        onDismiss = { playingPath = null }) }
}

@Composable
private fun VideoTrimControls(durationMs: Long, trim: VideoTrim, enabled: Boolean,
    onChange: (Long, Long) -> Unit, onPreview: () -> Unit, locale: Locale) {
    val minimumMs = minOf(1_000L, durationMs)
    Text(stringResource(R.string.video_trim), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.video_trim_hint), style = MaterialTheme.typography.bodySmall)
    Text("${stringResource(R.string.video_trim_start)}: ${formatVideoTime(trim.startMs, locale)}")
    if (durationMs > minimumMs) Slider(value = trim.startMs.toFloat(),
        onValueChange = { position ->
            val next = ((position / 100f).roundToLong() * 100L).coerceIn(0L, trim.endMs - minimumMs)
            onChange(next, trim.endMs)
        }, valueRange = 0f..(trim.endMs - minimumMs).toFloat(), enabled = enabled,
        modifier = Modifier.fillMaxWidth().testTag("video-trim-start"))
    Text("${stringResource(R.string.video_trim_end)}: ${formatVideoTime(trim.endMs, locale)}")
    if (durationMs > minimumMs) Slider(value = trim.endMs.toFloat(),
        onValueChange = { position ->
            val next = ((position / 100f).roundToLong() * 100L)
                .coerceIn(trim.startMs + minimumMs, durationMs)
            onChange(trim.startMs, next)
        }, valueRange = (trim.startMs + minimumMs).toFloat()..durationMs.toFloat(), enabled = enabled,
        modifier = Modifier.fillMaxWidth().testTag("video-trim-end"))
    Text("${stringResource(R.string.video_duration)}: ${formatVideoTime(trim.durationMs, locale)}",
        style = MaterialTheme.typography.bodySmall)
    TextButton(onClick = onPreview, enabled = enabled, modifier = Modifier.testTag("preview-video-trim")) {
        Text(stringResource(R.string.video_trim_preview))
    }
}

private fun formatVideoTime(timeMs: Long, locale: Locale): String = String.format(locale,
    "%d:%02d.%01d", timeMs / 60_000, timeMs / 1_000 % 60, timeMs % 1_000 / 100)

@Composable
private fun VideoPreviewCard(file: File, info: VideoInfo, bytes: Long, label: String, enabled: Boolean,
    onPlay: (File) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    ElevatedCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("$label · ${Formatter.formatShortFileSize(context, bytes)}", style = MaterialTheme.typography.titleSmall)
            Text("${pixels(info.width)} × ${pixels(info.height)} · ${stringResource(R.string.video_duration)}: ${seconds(info.durationMs / 1000)}")
            info.poster?.let { poster: Bitmap ->
                Image(poster.asImageBitmap(), contentDescription = label, contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().height(160.dp))
            }
            TextButton(onClick = { onPlay(file) }, enabled = enabled, modifier = Modifier.testTag(
                if (file.name.startsWith("source-")) "play-source-video" else "play-output-video")) {
                Text(stringResource(R.string.play_video))
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun VideoPlayerDialog(path: String, startMs: Long, endMs: Long?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val player = remember(path, startMs, endMs) {
        ExoPlayer.Builder(context).build().apply {
            val item = MediaItem.Builder().setUri(Uri.fromFile(File(path))).apply {
                if (endMs != null) setClippingConfiguration(MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(startMs).setEndPositionMs(endMs).build())
            }.build()
            setMediaItem(item)
            prepare()
        }
    }
    DisposableEffect(player, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) player.pause() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); player.release() }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.play_video)) }, text = {
        AndroidView(factory = { PlayerView(it).apply { this.player = player } },
            modifier = Modifier.fillMaxWidth().height(220.dp).testTag("video-player"))
    }, confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("close-video-player")) {
        Text(stringResource(R.string.close))
    } })
}

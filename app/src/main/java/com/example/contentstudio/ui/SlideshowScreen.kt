package com.example.contentstudio.ui

import android.graphics.Bitmap
import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.contentstudio.R
import com.example.contentstudio.data.StudioProject
import com.example.contentstudio.media.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SlideshowScreen(
    onClose: () -> Unit,
    model: SlideshowViewModel,
    project: StudioProject? = null,
    importRequest: Int = 0,
    onEditOutput: (suspend (Uri) -> Boolean)? = null,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val saved by model.sessionStatus.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val number =
        remember(locale) {
            java.text.NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 3 }
        }
    val timeline = state.timeline
    val selectedIndex = timeline.frames.indexOfFirst { it.id == state.selectedId }.coerceAtLeast(0)
    val selected = timeline.frames.getOrNull(selectedIndex)
    var proposal by rememberSaveable { mutableStateOf(project != null) }
    var proposedRequest by rememberSaveable { mutableIntStateOf(importRequest) }
    var clear by rememberSaveable { mutableStateOf(false) }
    var expectedOutput by rememberSaveable { mutableStateOf<String?>(null) }
    var playing by rememberSaveable { mutableStateOf<String?>(null) }
    var transferring by remember { mutableStateOf(false) }
    var transferFailed by remember { mutableStateOf(false) }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            model.add(uris)
        }
    val save =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("video/mp4")) { uri
            ->
            if (uri != null) model.export(uri, expectedOutput.orEmpty())
            expectedOutput = null
        }
    LaunchedEffect(state.result?.file?.path) {
        if (playing != state.result?.file?.path) playing = null
    }
    LaunchedEffect(importRequest) {
        if (proposedRequest != importRequest) {
            proposedRequest = importRequest
            proposal = project != null
        }
    }
    LaunchedEffect(proposal, state.busy) {
        if (proposal && !state.busy && project != null && timeline.frames.isEmpty()) {
            proposal = false
            model.usePresentation(project)
        }
    }
    DisposableEffect(model, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) model.flushSession()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    ToolWorkspace(
        stringResource(R.string.slideshow_title),
        "slideshow",
        {
            model.leave()
            onClose()
        },
        panels =
            if (selected == null) emptyList()
            else
                listOf(
                    WorkspacePanel("frames", stringResource(R.string.workspace_clips)),
                    WorkspacePanel("output", stringResource(R.string.workspace_output)),
                ),
        resultKey = state.result?.file?.path,
        actions = {
            if (state.busy) {
                val progress = state.progress
                if (progress == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                else
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                TextButton(
                    onClick = model::cancel,
                    modifier = Modifier.testTag("slideshow-cancel"),
                ) {
                    Text(stringResource(R.string.cancel))
                }
            } else if (state.result != null) {
                val result = state.result!!
                Button(
                    onClick = {
                        expectedOutput = result.file.path
                        val name =
                            timeline.title
                                .replace('/', '_')
                                .replace('\\', '_')
                                .filterNot { it.isISOControl() }
                                .take(64)
                                .ifBlank { "slideshow" }
                        save.launch("$name.mp4")
                    },
                    enabled = !transferring,
                    modifier = Modifier.testTag("slideshow-save"),
                ) {
                    Text(stringResource(R.string.export_video))
                }
                OutputActions(result.file, "video/mp4", "slideshow.mp4", !transferring)
                if (onEditOutput != null)
                    TextButton(
                        onClick = {
                            transferring = true
                            scope.launch {
                                try {
                                    val output =
                                        kotlinx.coroutines.withContext(
                                            kotlinx.coroutines.Dispatchers.IO
                                        ) {
                                            MediaOutputs(context)
                                                .stage(result.file, "video/mp4", "slideshow.mp4")
                                        }
                                    if (!onEditOutput(output.uri)) transferFailed = true
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    transferFailed = true
                                } finally {
                                    transferring = false
                                }
                            }
                        },
                        enabled = !transferring,
                        modifier = Modifier.testTag("slideshow-edit-output"),
                    ) {
                        Text(stringResource(R.string.edit_video))
                    }
            } else if (selected != null)
                Button(
                    onClick = model::generate,
                    enabled = state.supportReady && state.issue == null,
                    modifier = Modifier.testTag("slideshow-create"),
                ) {
                    Text(stringResource(R.string.create_result))
                }
        },
        panelContent = { panel ->
            if (panel == "frames" && selected != null) {
                Text(
                    stringResource(
                        R.string.slideshow_frame,
                        selectedIndex + 1,
                        timeline.frames.size,
                    )
                )
                var durationText by
                    remember(selected.id, selected.durationMs, locale) {
                        mutableStateOf(number.format(selected.durationMs / 1000.0))
                    }
                val duration = parseSlideshowDuration(durationText)
                val valid =
                    duration != null &&
                        timeline.durationMs - selected.durationMs + duration <=
                            FormatSupport.MAX_VIDEO_DURATION_MS
                OutlinedTextField(
                    durationText,
                    { value -> if (value.length <= 8) durationText = value },
                    label = { Text(stringResource(R.string.video_duration) + " (s)") },
                    isError = !valid,
                    enabled = !state.busy && !transferring,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().testTag("slideshow-frame-duration"),
                )
                TextButton(
                    onClick = { duration?.let { model.duration(selected.id, it) } },
                    enabled = !state.busy && !transferring && valid,
                    modifier = Modifier.testTag("slideshow-apply-duration"),
                ) {
                    Text(stringResource(R.string.slideshow_apply))
                }
                Row {
                    TextButton(
                        onClick = { model.move(selected.id, -1) },
                        enabled = !state.busy && !transferring && selectedIndex > 0,
                        modifier = Modifier.testTag("slideshow-move-up"),
                    ) {
                        Text(stringResource(R.string.move_slide_up))
                    }
                    TextButton(
                        onClick = { model.move(selected.id, 1) },
                        enabled =
                            !state.busy &&
                                !transferring &&
                                selectedIndex < timeline.frames.lastIndex,
                        modifier = Modifier.testTag("slideshow-move-down"),
                    ) {
                        Text(stringResource(R.string.move_slide_down))
                    }
                }
                TextButton(
                    onClick = { model.remove(selected.id) },
                    enabled = !state.busy && !transferring,
                    modifier = Modifier.testTag("slideshow-remove-frame"),
                ) {
                    Text(stringResource(R.string.remove_image))
                }
                OutlinedButton(
                    onClick = { picker.launch(ImageFormats.inputMimes) },
                    enabled = !state.busy && !transferring && timeline.frames.size < 100,
                    modifier = Modifier.testTag("slideshow-add"),
                ) {
                    Text(stringResource(R.string.choose_images))
                }
                Text(
                    stringResource(R.string.slideshow_limits),
                    style = MaterialTheme.typography.bodySmall,
                )
            } else if (panel == "output") {
                Text("MP4 · H.264 · 30 fps")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VideoCanvas.entries
                        .filter { it != VideoCanvas.FIRST_CLIP }
                        .forEach { canvas ->
                            FilterChip(
                                timeline.canvas == canvas,
                                { model.options(canvas = canvas) },
                                enabled = !state.busy && !transferring,
                                label = {
                                    Text(
                                        when (canvas) {
                                            VideoCanvas.PORTRAIT_9_16 -> "9:16"
                                            VideoCanvas.SQUARE -> "1:1"
                                            else -> "16:9"
                                        }
                                    )
                                },
                                modifier = Modifier.testTag("slideshow-canvas-${canvas.name}"),
                            )
                        }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VideoFit.entries.forEach { fit ->
                        FilterChip(
                            timeline.fit == fit,
                            { model.options(fit = fit) },
                            enabled = !state.busy && !transferring,
                            label = {
                                Text(
                                    stringResource(
                                        if (fit == VideoFit.FIT) R.string.video_fit
                                        else R.string.video_crop
                                    )
                                )
                            },
                            modifier = Modifier.testTag("slideshow-fit-${fit.name}"),
                        )
                    }
                }
                Text(stringResource(R.string.image_quality))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VideoQuality.entries.forEach { quality ->
                        FilterChip(
                            timeline.quality == quality,
                            { model.options(quality = quality) },
                            enabled = !state.busy && !transferring,
                            label = {
                                Text(
                                    stringResource(
                                        when (quality) {
                                            VideoQuality.SMALL -> R.string.quality_small
                                            VideoQuality.BALANCED -> R.string.quality_balanced
                                            VideoQuality.HIGH -> R.string.quality_high
                                        }
                                    )
                                )
                            },
                            modifier = Modifier.testTag("slideshow-quality-${quality.name}"),
                        )
                    }
                }
                Text(stringResource(R.string.max_edge))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(640, 1280, 1920).forEach { edge ->
                        FilterChip(
                            timeline.edge == edge,
                            { model.options(edge = edge) },
                            enabled = !state.busy && !transferring,
                            label = { Text(pixels(edge)) },
                            modifier = Modifier.testTag("slideshow-edge-$edge"),
                        )
                    }
                }
                Text(
                    stringResource(R.string.slideshow_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    stringResource(R.string.slideshow_visual_losses),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().testTag("slideshow-list"),
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                if (selected == null) {
                    Text(stringResource(R.string.slideshow_hint))
                    Button(
                        onClick = { picker.launch(ImageFormats.inputMimes) },
                        enabled = !state.busy && !transferring,
                        modifier = Modifier.testTag("slideshow-choose-images"),
                    ) {
                        Text(stringResource(R.string.choose_images))
                    }
                } else {
                    SourceHeader(
                        timeline.title.ifBlank {
                            selected.asset?.name ?: context.getString(R.string.presentation)
                        },
                        stringResource(
                            R.string.slideshow_frame,
                            selectedIndex + 1,
                            timeline.frames.size,
                        ),
                        !state.busy,
                        "slideshow-clear",
                        stringResource(R.string.clear_session),
                    ) {
                        clear = true
                    }
                    state.preview?.let {
                        Image(
                            it.asImageBitmap(),
                            stringResource(R.string.slide_image_added),
                            Modifier.fillMaxWidth()
                                .heightIn(max = 300.dp)
                                .testTag("slideshow-preview"),
                        )
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(timeline.frames, key = { _, frame -> frame.id }) { index, frame
                            ->
                            OutlinedCard(
                                onClick = { model.select(frame.id) },
                                modifier = Modifier.width(132.dp).testTag("slideshow-select-$index").accessibleSelection(index == selectedIndex),
                            ) {
                                Column(Modifier.padding(8.dp)) {
                                    Text(
                                        stringResource(
                                            R.string.slideshow_frame,
                                            index + 1,
                                            timeline.frames.size,
                                        ),
                                        maxLines = 1,
                                    )
                                    SlideshowThumbnail(timeline, index)
                                    Text(
                                        "${number.format(frame.durationMs / 1000.0)} s",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        stringResource(R.string.video_duration) +
                            ": ${number.format(timeline.durationMs / 1000.0)} s",
                        modifier = Modifier.testTag("slideshow-duration"),
                    )
                }
                state.result?.let { result ->
                    Text(
                        stringResource(R.string.output_size) +
                            ": " +
                            Formatter.formatFileSize(context, result.bytes),
                        modifier = Modifier.testTag("slideshow-result"),
                    )
                    Text("${result.info.width} × ${result.info.height} · MP4 · H.264")
                    result.info.poster?.let {
                        Image(
                            it.asImageBitmap(),
                            stringResource(R.string.output_size),
                            Modifier.fillMaxWidth().heightIn(max = 240.dp),
                        )
                    }
                    Button(
                        onClick = { playing = result.file.path },
                        modifier = Modifier.testTag("slideshow-play"),
                    ) {
                        Text(stringResource(R.string.play_video))
                    }
                }
                if (state.issue != null)
                    Text(
                        stringResource(R.string.video_output_device),
                        color = MaterialTheme.colorScheme.error,
                    )
                ProcessingStatusHint(state.workStatus, "slideshow")
                if (transferFailed)
                    Text(
                        stringResource(R.string.slideshow_error),
                        color = MaterialTheme.colorScheme.error,
                    )
                state.message?.let {
                    Text(
                        stringResource(
                            when (it) {
                                SlideshowMessage.IMPORT_ERROR -> R.string.photo_pdf_import_error
                                SlideshowMessage.GENERATE_ERROR -> R.string.slideshow_error
                                SlideshowMessage.EXPORT_ERROR -> R.string.video_export_error
                                SlideshowMessage.EXPORTED -> R.string.video_saved
                                SlideshowMessage.RESTORE_ERROR -> R.string.session_error
                                SlideshowMessage.LIMIT -> R.string.slideshow_limits
                                SlideshowMessage.MEMORY -> R.string.error_memory
                            }
                        ),
                        color =
                            if (it == SlideshowMessage.EXPORTED) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("slideshow-message"),
                    )
                }
                if (saved == SessionSaveStatus.ERROR)
                    TextButton(onClick = model::retrySession) {
                        Text(stringResource(R.string.session_error))
                    }
                WorkspaceHelp {
                    Text(stringResource(R.string.slideshow_limits))
                    Text(stringResource(R.string.media_session_hint))
                    Text(stringResource(R.string.slideshow_hint))
                }
            }
        }
    }
    if (proposal && !state.busy && project != null && timeline.frames.isNotEmpty())
        AlertDialog(
            onDismissRequest = { proposal = false },
            title = { Text(stringResource(R.string.slideshow_use)) },
            text = { Text(stringResource(R.string.slideshow_replace)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        proposal = false
                        model.usePresentation(project)
                    },
                    modifier = Modifier.testTag("slideshow-use-presentation"),
                ) {
                    Text(stringResource(R.string.slideshow_use))
                }
            },
            dismissButton = {
                TextButton(onClick = { proposal = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    if (clear)
        AlertDialog(
            onDismissRequest = { clear = false },
            title = { Text(stringResource(R.string.clear_session)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        clear = false
                        model.reset()
                    },
                    modifier = Modifier.testTag("slideshow-confirm-clear"),
                ) {
                    Text(stringResource(R.string.clear_session))
                }
            },
            dismissButton = {
                TextButton(onClick = { clear = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    playing?.let { VideoPlayerDialog(it, 0, null) { playing = null } }
}

@Composable
private fun SlideshowThumbnail(timeline: SlideshowTimeline, index: Int) {
    val context = LocalContext.current
    val engine = remember(context) { SlideshowEngine(context.applicationContext) }
    val bitmap by
        produceState<Bitmap?>(null, timeline, index) {
            value =
                try {
                    PreviewLoader.load { engine.preview(timeline, index, 160) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                } catch (_: OutOfMemoryError) {
                    null
                }
        }
    bitmap?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxWidth().height(66.dp)) }
}

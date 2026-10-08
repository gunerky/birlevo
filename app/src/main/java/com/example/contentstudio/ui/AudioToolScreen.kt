package com.example.contentstudio.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
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
    val pick =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
            it?.let(model::load)
        }
    var expected by rememberSaveable { mutableStateOf<String?>(null) }
    var original by rememberSaveable { mutableStateOf<String?>(null) }
    var originals by
        rememberSaveable(
            stateSaver = listSaver<List<String>, String>(save = { it }, restore = { it })
        ) {
            mutableStateOf<List<String>>(emptyList())
        }
    val add =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
            it?.let(model::addSource)
        }
    val save =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK)
                it.data?.data?.let { uri -> model.export(uri, expected, original, originals) }
            expected = null
            original = null
            originals = emptyList()
        }
    // Playback paths are deliberately not saved across process death or input replacement.
    var playing by
        remember(state.audioClips.map { it.source.file.path }, state.result?.file?.path) {
            mutableStateOf<File?>(null)
        }
    var editing by
        remember(state.audioClips.map { it.source.file.path }) { mutableStateOf<AudioClip?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(model, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) model.flushSession()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            model.flushSession()
        }
    }
    fun close() {
        model.leave()
        onClose()
    }
    var compareOriginal by rememberSaveable(state.result?.file?.path) { mutableStateOf(false) }
    ToolWorkspace(
        stringResource(R.string.audio_tool),
        "audio",
        ::close,
        panels =
            if (state.source != null)
                listOf(
                    WorkspacePanel("clips", stringResource(R.string.audio_trim)),
                    WorkspacePanel("output", stringResource(R.string.output_format)),
                )
            else emptyList(),
        resultKey = state.result?.file?.path,
        actions = {
            if (state.busy) {
                state.progress?.let {
                    LinearProgressIndicator(
                        progress = { it / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } ?: LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(R.string.processing))
                TextButton(onClick = model::cancel, modifier = Modifier.testTag("audio-cancel")) {
                    Text(stringResource(R.string.cancel))
                }
            } else
                state.source?.let { source ->
                    state.result?.let { result ->
                        val target = state.target
                        val name =
                            source.name
                                .substringBeforeLast('.')
                                .replace('/', '_')
                                .replace('\\', '_')
                                .filterNot { it.isISOControl() }
                                .take(64)
                                .ifBlank { "audio" } + ".${target.extension}"
                        fun export() {
                            expected = result.file.name
                            original = source.originalUri.toString()
                            originals = state.audioClips.map { it.source.originalUri.toString() }
                            save.launch(
                                Intent(Intent.ACTION_CREATE_DOCUMENT)
                                    .addCategory(Intent.CATEGORY_OPENABLE)
                                    .setType(target.mime)
                                    .putExtra(Intent.EXTRA_TITLE, name)
                            )
                        }
                        CompressionExportActions(
                            result.file.path,
                            compressionBenefit(state.sourceBytes, result.bytes),
                            !state.busy,
                            stringResource(R.string.audio_save),
                            "audio-save",
                            onSave = ::export,
                            onSaveAnyway = {
                                if (model.confirmNonSmallerCopy(result.file)) export()
                            },
                        )
                        OutputActions(
                            result.file,
                            target.mime,
                            name,
                            !state.busy,
                            state.sourceBytes,
                        )
                    }
                        ?: run {
                            Button(
                                onClick = model::preview,
                                enabled =
                                    !state.busy &&
                                        state.durationMs <= AudioPolicy.MAX_DURATION_MS &&
                                        state.outputSupport.containsKey(state.target) &&
                                        state.outputSupport[state.target] == null,
                                modifier = Modifier.testTag("audio-create"),
                            ) {
                                Text(stringResource(R.string.create_result))
                            }
                        }
                }
        },
        panelContent = { panel ->
            if (panel == "clips") {
                state.audioClips.forEachIndexed { index, clip ->
                    key(clip.source.file.path) {
                        val input = clip.source
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "${index + 1}. ${input.name}",
                                style = MaterialTheme.typography.titleMedium,
                                modifier =
                                    Modifier.testTag(
                                        if (index == 0) "audio-source" else "audio-source-$index"
                                    ),
                            )
                            Text(
                                Formatter.formatShortFileSize(context, input.bytes) +
                                    " · ${input.info.sampleRate} Hz · ${input.info.channels} ch · " +
                                    seconds(input.info.durationMs / 1000)
                            )
                            Text(
                                stringResource(
                                    R.string.audio_selected_range,
                                    AudioRange.seconds(clip.range.startMs),
                                    AudioRange.seconds(clip.range.end(input.info.durationMs)),
                                )
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(
                                    onClick = { playing = input.file },
                                    enabled = !state.busy,
                                    modifier = Modifier.testTag("play-clip-audio-$index"),
                                ) {
                                    Text(stringResource(R.string.audio_play))
                                }
                                TextButton(
                                    onClick = { editing = clip },
                                    enabled = !state.busy,
                                    modifier = Modifier.testTag("audio-trim-$index"),
                                ) {
                                    Text(stringResource(R.string.audio_trim))
                                }
                                if (state.audioClips.size == 2)
                                    TextButton(
                                        onClick = { model.removeSource(input.file) },
                                        enabled = !state.busy,
                                        modifier = Modifier.testTag("audio-remove-$index"),
                                    ) {
                                        Text(stringResource(R.string.audio_remove))
                                    }
                            }
                        }
                    }
                }

                if (state.audioClips.size < 2)
                    OutlinedButton(
                        onClick = {
                            add.launch(
                                (AudioPolicy.inputMimes +
                                        FormatSupport.videoMimes +
                                        listOf("audio/x-wav", "audio/x-flac", "application/ogg"))
                                    .toTypedArray()
                            )
                        },
                        enabled = !state.busy,
                        modifier = Modifier.testTag("audio-add"),
                    ) {
                        Text(stringResource(R.string.audio_add))
                    }
                else
                    OutlinedButton(
                        onClick = model::swapSources,
                        enabled = !state.busy,
                        modifier = Modifier.testTag("audio-swap"),
                    ) {
                        Text(stringResource(R.string.audio_swap))
                    }
                Text(
                    stringResource(R.string.audio_edit_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    stringResource(
                        R.string.audio_total_duration,
                        AudioRange.seconds(state.durationMs),
                    )
                )
                if (state.durationMs > AudioPolicy.MAX_DURATION_MS)
                    Text(
                        stringResource(R.string.audio_edit_limit),
                        color = MaterialTheme.colorScheme.error,
                    )
            } else {
                Text(
                    stringResource(R.string.audio_profile, state.rate, state.channels),
                    style = MaterialTheme.typography.titleSmall,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (state.audioClips.map { it.source.info.sampleRate } + state.rate + 48_000)
                        .distinct()
                        .sorted()
                        .forEach { rate ->
                            FilterChip(
                                state.rate == rate,
                                { model.setProfile(rate = rate) },
                                enabled = !state.busy,
                                label = { Text("$rate Hz") },
                                modifier = Modifier.testTag("audio-profile-$rate"),
                            )
                        }
                    for (channels in 1..2) FilterChip(
                        state.channels == channels,
                        { model.setProfile(channels = channels) },
                        enabled = !state.busy,
                        label = { Text("$channels ch") },
                        modifier = Modifier.testTag("audio-channels-$channels"),
                    )
                }
                Text(
                    stringResource(R.string.audio_profile_hint),
                    style = MaterialTheme.typography.bodySmall,
                )

                Text(
                    stringResource(R.string.output_format),
                    style = MaterialTheme.typography.titleMedium,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AudioTarget.entries.forEach { target ->
                        FilterChip(
                            state.target == target,
                            { model.setOptions(target = target) },
                            enabled = !state.busy,
                            label = { Text(target.label) },
                            modifier = Modifier.testTag("audio-target-${target.name}"),
                        )
                    }
                }
                if (state.target.lossy)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AudioPolicy.bitrates.forEach { rate ->
                            FilterChip(
                                state.bitrate == rate,
                                { model.setOptions(bitrate = rate) },
                                enabled = !state.busy,
                                label = { Text("${rate / 1000} kbps") },
                                modifier = Modifier.testTag("audio-rate-$rate"),
                            )
                        }
                    }
                Text(
                    stringResource(
                        when (state.target) {
                            AudioTarget.OGG -> R.string.audio_ogg_description
                            AudioTarget.FLAC -> R.string.audio_flac_description
                            AudioTarget.MP3 -> R.string.audio_mp3_description
                            else -> R.string.audio_pcm_description
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    stringResource(R.string.audio_metadata),
                    style = MaterialTheme.typography.bodySmall,
                )
                state.outputSupport[state.target]?.let { issue ->
                    Text(
                        stringResource(
                            when (issue) {
                                AudioOutputIssue.ANDROID_VERSION -> R.string.audio_output_android
                                AudioOutputIssue.ENCODER -> R.string.audio_output_encoder
                                AudioOutputIssue.DECODER -> R.string.audio_output_decoder
                                AudioOutputIssue.CHECK_FAILED -> R.string.audio_output_check
                            }
                        ),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("audio-output-issue"),
                    )
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().testTag("audio-list"),
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "preview") {
                if (state.source == null) {
                    ToolImportCard(
                        StudioGlyph.AUDIO,
                        "MP3 / M4A / WAV / OGG / FLAC + VIDEO",
                        stringResource(R.string.audio_choose),
                        stringResource(R.string.audio_limits),
                        !state.busy,
                        "choose-audio",
                    ) {
                        pick.launch(
                            (AudioPolicy.inputMimes +
                                    FormatSupport.videoMimes +
                                    listOf("audio/x-wav", "audio/x-flac", "application/ogg"))
                                .toTypedArray()
                        )
                    }
                }
                state.source?.let { source ->
                    SourceHeader(
                        source.name,
                        Formatter.formatShortFileSize(context, state.sourceBytes) +
                            " · " +
                            seconds(state.durationMs / 1000),
                        !state.busy,
                        "choose-audio",
                    ) {
                        pick.launch(
                            (AudioPolicy.inputMimes +
                                    FormatSupport.videoMimes +
                                    listOf("audio/x-wav", "audio/x-flac", "application/ogg"))
                                .toTypedArray()
                        )
                    }
                    if (state.result != null)
                        Row {
                            FilterChip(
                                compareOriginal,
                                { compareOriginal = true },
                                label = { Text(stringResource(R.string.original_size)) },
                            )
                            FilterChip(
                                !compareOriginal,
                                { compareOriginal = false },
                                label = { Text(stringResource(R.string.output_size)) },
                            )
                        }
                    val result = state.result
                    if (result != null && !compareOriginal) {
                        Text(
                            state.target.label +
                                " · " +
                                Formatter.formatShortFileSize(context, result.bytes),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.testTag("audio-result"),
                        )
                        Text(
                            stringResource(R.string.original_size) +
                                ": " +
                                Formatter.formatShortFileSize(context, state.sourceBytes)
                        )
                        TextButton(
                            onClick = { playing = result.file },
                            enabled = !state.busy,
                            modifier = Modifier.testTag("play-output-audio"),
                        ) {
                            Text(stringResource(R.string.audio_play))
                        }
                    } else {
                        TextButton(
                            onClick = { playing = source.file },
                            enabled = !state.busy,
                            modifier = Modifier.testTag("play-source-audio"),
                        ) {
                            Text(stringResource(R.string.audio_play))
                        }
                    }
                    state.outputSupport[state.target]?.let {
                        Text(
                            stringResource(R.string.audio_output_check),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                ProcessingStatusHint(state.workStatus, "audio")
            }
            state.message?.let { message ->
                item(key = "message") {
                    Text(
                        stringResource(
                            when (message) {
                                AudioMessage.LOAD_ERROR -> R.string.audio_error
                                AudioMessage.PROCESS_ERROR -> R.string.audio_process_error
                                AudioMessage.EXPORT_ERROR -> R.string.video_export_error
                                AudioMessage.EXPORTED -> R.string.audio_saved
                            }
                        ),
                        color =
                            if (message == AudioMessage.EXPORTED) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("audio-message"),
                    )
                }
            }
            item(key = "help") {
                WorkspaceHelp {
                    SupportedFilesButton(SupportedTool.AUDIO)
                    MediaSessionControls(
                        saveStatus,
                        model.sessionRestoreFailed,
                        !state.busy,
                        "audio",
                        model::retrySession,
                        model::reset,
                    )
                }
            }
        }
    }
    playing?.let { file ->
        AudioPlayback(file, state.audioClips.firstOrNull { it.source.file == file }?.range) {
            playing = null
        }
    }
    editing?.let { clip ->
        AudioRangeDialog(clip, { editing = null }) { range ->
            model.setRange(clip.source.file, range)
            editing = null
        }
    }
}

@Composable
private fun AudioRangeDialog(clip: AudioClip, onClose: () -> Unit, onApply: (AudioRange) -> Unit) {
    var start by
        rememberSaveable(clip.source.file.name) {
            mutableStateOf(AudioRange.seconds(clip.range.startMs))
        }
    var end by
        rememberSaveable(clip.source.file.name) {
            mutableStateOf(AudioRange.seconds(clip.range.end(clip.source.info.durationMs)))
        }
    val range = AudioRange.parse(start, end, clip.source.info.durationMs)
    AlertDialog(
        onDismissRequest = onClose,
        modifier = Modifier.imePadding(),
        properties = DialogProperties(decorFitsSystemWindows = false),
        title = { Text(stringResource(R.string.audio_trim)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(clip.source.name)
                OutlinedTextField(
                    start,
                    { start = it },
                    label = { Text(stringResource(R.string.audio_start_seconds)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().testTag("audio-trim-start"),
                )
                OutlinedTextField(
                    end,
                    { end = it },
                    label = { Text(stringResource(R.string.audio_end_seconds)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().testTag("audio-trim-end"),
                )
                if (range == null)
                    Text(
                        stringResource(R.string.audio_range_invalid),
                        color = MaterialTheme.colorScheme.error,
                    )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { range?.let(onApply) },
                enabled = range != null,
                modifier = Modifier.testTag("audio-trim-apply"),
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.cancel)) } },
    )
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun AudioPlayback(file: File, range: AudioRange?, onClose: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var failed by remember(file.path) { mutableStateOf(false) }
    val player =
        remember(file.path, range) {
            ExoPlayer.Builder(context).build().apply {
                trackSelectionParameters =
                    trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
                        .build()
                val item = MediaItem.Builder().setUri(Uri.fromFile(file))
                if (range != null && range != AudioRange())
                    item.setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(range.startMs)
                            .setEndPositionMs(range.endMs ?: C.TIME_END_OF_SOURCE)
                            .build()
                    )
                setMediaItem(item.build())
                prepare()
            }
        }
    DisposableEffect(player, lifecycle) {
        val listener =
            object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    failed = true
                }
            }
        player.addListener(listener)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) player.pause()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            player.removeListener(listener)
            player.release()
        }
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.audio_play)) },
        text = {
            Column {
                AndroidView(
                    factory = {
                        PlayerView(it).apply {
                            this.player = player
                            controllerShowTimeoutMs = 0
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(140.dp).testTag("audio-player"),
                )
                if (failed)
                    Text(
                        stringResource(R.string.audio_error),
                        color = MaterialTheme.colorScheme.error,
                    )
            }
        },
        confirmButton = {
            TextButton(onClick = onClose, modifier = Modifier.testTag("audio-close-player")) {
                Text(stringResource(R.string.close))
            }
        },
    )
}

package com.example.contentstudio.ui

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.contentstudio.R
import com.example.contentstudio.batch.*
import com.example.contentstudio.media.*
import com.example.contentstudio.audio.*
import com.example.contentstudio.documents.*
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BatchImageScreen(onClose: () -> Unit, model: BatchImageViewModel = viewModel()) = BatchScreen(onClose, model)
@Composable
fun BatchVideoScreen(onClose: () -> Unit, model: BatchVideoViewModel = viewModel()) = BatchScreen(onClose, model)
@Composable
fun BatchAudioScreen(onClose: () -> Unit, model: BatchAudioViewModel = viewModel()) = BatchScreen(onClose, model)
@Composable
fun BatchDocumentScreen(onClose: () -> Unit, model: BatchDocumentViewModel = viewModel()) = BatchScreen(onClose, model)

internal fun batchTitle(kind: BatchKind): Int = when(kind) {
    BatchKind.IMAGE -> R.string.batch_images; BatchKind.VIDEO -> R.string.batch_video; BatchKind.AUDIO -> R.string.batch_audio; BatchKind.DOCUMENT -> R.string.batch_documents
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BatchScreen(onClose: () -> Unit, model: BatchViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val batch = state.batch
    val context = LocalContext.current
    val numberFormat = java.text.NumberFormat.getNumberInstance(LocalConfiguration.current.locales[0])
    val kind = model.mediaKind
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {
        if (it.isNotEmpty()) model.add(it)
    }
    var pendingArchive by rememberSaveable { mutableStateOf<String?>(null) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) {
        it?.let { uri -> model.export(uri, pendingArchive) }; pendingArchive = null
    }
    var copyKey by remember { mutableStateOf<String?>(null) }
    var reviewing by remember { mutableStateOf<String?>(null) }
    var clearing by remember { mutableStateOf(false) }
    val selected = batch.items.count { it.output != null && it.included }
    val canPack = !state.busy && runCatching { BatchPolicy.requireExportable(batch, state.approved, state.documentApproved) }.isSuccess
    ToolWorkspace(stringResource(batchTitle(kind)), "batch", onClose,
        panels = listOf(WorkspacePanel("output", stringResource(R.string.output_format))),
        resultKey = state.archive?.name,
        actions = {
            if (state.busy) {
                state.progress?.let { Text("$it%") }
                LinearProgressIndicator(Modifier.fillMaxWidth().testTag("batch-busy"))
                TextButton(onClick = model::cancel, modifier = Modifier.testTag("batch-cancel")) {
                    Text(stringResource(R.string.cancel))
                }
            } else if (batch.items.isNotEmpty()) {
                if (batch.items.any { it.output == null })
                    Button(onClick = model::convert, modifier = Modifier.fillMaxWidth().testTag("batch-convert")) {
                        Text(stringResource(R.string.create_result))
                    }
                if (state.archive == null)
                    Button(onClick = { reviewing = batch.revision }, enabled = canPack,
                        modifier = Modifier.fillMaxWidth().testTag("batch-zip")) {
                        Text(stringResource(R.string.batch_zip, selected, batch.items.size))
                    }
                else Button(onClick = {
                    pendingArchive = state.archive?.name; save.launch("Birlevo-${kind.name.lowercase()}.zip")
                }, modifier = Modifier.fillMaxWidth().testTag("batch-save")) {
                    Text(stringResource(R.string.batch_save))
                }
            }
        },
        panelContent = {
            val options = batch.options
            if (kind == BatchKind.IMAGE) {
            Text(stringResource(R.string.output_format))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ImageOutputFormat.entries.forEach { format ->
                    FilterChip(selected = options.format == format, enabled = !state.busy,
                        onClick = { model.options(options.copy(format = format)) },
                        label = { Text(format.name) }, modifier = Modifier.heightIn(min = 48.dp).testTag("batch-format-${format.name}"))
                }
            }
            Text(stringResource(R.string.max_edge))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1280, 1920, 2560).forEach { edge ->
                    FilterChip(selected = options.edge == edge, enabled = !state.busy,
                        onClick = { model.options(options.copy(edge = edge)) }, label = { Text("$edge px") },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("batch-edge-$edge"))
                }
            }
            if (options.format.usesQuality) {
                var quality by remember(options.quality) { mutableFloatStateOf(options.quality.toFloat()) }
                Text("${stringResource(R.string.image_quality)}: ${quality.roundToInt()}")
                Slider(value = quality, onValueChange = { quality = it }, enabled = !state.busy,
                    valueRange = 40f..95f, onValueChangeFinished = { model.options(options.copy(quality = quality.roundToInt())) },
                    modifier = Modifier.testTag("batch-quality"))
            }
            Text(stringResource(when (options.format) {
                ImageOutputFormat.PNG -> R.string.png_losses
                ImageOutputFormat.BMP -> R.string.bmp_losses
                ImageOutputFormat.WEBP -> R.string.webp_losses
                ImageOutputFormat.JPEG -> R.string.image_losses
            }))
            if (options.format == ImageOutputFormat.PNG) Text(stringResource(R.string.png_quality_hint))
            if (options.format == ImageOutputFormat.BMP) Text(stringResource(R.string.bmp_quality_hint))
            } else if (kind == BatchKind.DOCUMENT) BatchDocumentOptions(options, !state.busy, model::options)
            else BatchMediaOptions(options, !state.busy, model::options)
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().testTag("batch-list"), contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(key = "intro") {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text(stringResource(when(kind) { BatchKind.IMAGE -> R.string.batch_hint; BatchKind.DOCUMENT -> R.string.batch_documents_hint; else -> R.string.batch_media_hint }))
                    if (kind == BatchKind.IMAGE) {
                        Text(stringResource(R.string.compression_hint), style = MaterialTheme.typography.bodySmall)
                        Text("${batch.options.format.name} · ${batch.options.edge} px" +
                            if (batch.options.format.usesQuality) " · ${batch.options.quality}" else "")
                    } else if (kind == BatchKind.DOCUMENT) Text(batch.options.documentTarget.name)
                    else Text(if (kind == BatchKind.VIDEO) "${batchVideoFormatLabel(batch.options.videoFormat)} · ${batch.options.videoEdge} px · ${stringResource(videoQualityText(batch.options.videoQuality))}"
                        else batch.options.audioTarget.label + if (batch.options.audioTarget.lossy) " · ${batch.options.audioBitrate / 1000} kbps" else "")
                    TextButton(onClick = { pick.launch(when (kind) {
                        BatchKind.IMAGE -> ImageFormats.inputMimes
                        BatchKind.VIDEO -> FormatSupport.videoMimes.toTypedArray()
                        BatchKind.AUDIO -> (AudioPolicy.inputMimes + FormatSupport.videoMimes).toTypedArray()
                        BatchKind.DOCUMENT -> (DocumentPolicy.inputMimes + listOf("application/csv", "application/vnd.ms-excel", "application/octet-stream")).toTypedArray()
                    }) },
                        enabled = !state.busy && state.ready && batch.items.size < BatchPolicy.MAX_FILES,
                        modifier = Modifier.testTag("batch-add")) { Text(stringResource(if (kind == BatchKind.IMAGE) R.string.batch_add else R.string.batch_add_media)) }
                    if (batch.items.isNotEmpty() || state.error) TextButton(onClick = { clearing = true }, enabled = !state.busy,
                        modifier = Modifier.testTag("batch-clear")) { Text(stringResource(R.string.clear_session)) }
                    if (state.error) Text(stringResource(R.string.batch_error), color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("batch-error"))
                    state.documentError?.let { Text(stringResource(batchDocumentIssueText(it)), color = MaterialTheme.colorScheme.error) }
                    if (state.saved) Text(stringResource(R.string.reader_saved), modifier = Modifier.testTag("batch-saved"))
                    state.archive?.let { Text("ZIP · ${Formatter.formatFileSize(context, it.bytes)}") }
                }
            }
            itemsIndexed(batch.items, key = { _, item -> item.id }) { index, item ->
                OutlinedCard(Modifier.padding(horizontal = 20.dp).fillMaxWidth().testTag("batch-item-$index")) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(item.name, style = MaterialTheme.typography.titleSmall)
                        Text(Formatter.formatFileSize(context, item.bytes))
                        if (kind == BatchKind.DOCUMENT) BatchDocumentRow(item, batch.options, index, state, model)
                        // Width × height is an ordered technical value, including in RTL locales.
                        if (item.width > 0) Text("${item.width} × ${item.height}",
                            style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr),
                            modifier = Modifier.testTag("batch-dimensions-$index"))
                        if (item.durationMs > 0) Text(stringResource(R.string.unit_seconds, numberFormat.format(item.durationMs / 1000.0)),
                            modifier = Modifier.testTag("batch-duration-$index"))
                        if (item.output != null) {
                            Text("${stringResource(R.string.output_size)}: ${Formatter.formatFileSize(context, item.outputBytes)}",
                                modifier = Modifier.testTag("batch-result-$index"))
                            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .toggleable(item.included, enabled = !state.busy, role = Role.Checkbox,
                                    onValueChange = { model.include(item.id, it) }).testTag("batch-include-$index"),
                                verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(item.included, onCheckedChange = null)
                                Text(stringResource(R.string.batch_include), Modifier.weight(1f))
                            }
                            if (item.needsConsent) {
                                Text(stringResource(if (kind == BatchKind.DOCUMENT) R.string.output_use_warning else R.string.compression_save_warning))
                                if (item.copyKey in state.approved) Text(stringResource(R.string.batch_approved),
                                    modifier = Modifier.testTag("batch-approved-$index"))
                                else TextButton(onClick = { copyKey = item.copyKey }, enabled = !state.busy,
                                    modifier = Modifier.testTag("batch-allow-$index")) { Text(stringResource(R.string.continue_output)) }
                            }
                        } else if (item.issue != null)
                            Text(stringResource(item.documentIssue?.let(::batchDocumentIssueText) ?: item.videoIssue?.let(::videoOutputIssueText)
                                ?: item.audioIssue?.let(::batchAudioIssueText) ?: importIssueText(item.issue)), color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.testTag("batch-failed-$index"))
                        else Text(stringResource(R.string.batch_pending))
                        TextButton(onClick = { model.remove(item.id) }, enabled = !state.busy,
                            modifier = Modifier.testTag("batch-remove-$index")) { Text(stringResource(R.string.audio_remove)) }
                    }
                }
            }
        }
    }
    copyKey?.let { captured ->
        AlertDialog(onDismissRequest = { copyKey = null }, title = { Text(stringResource(R.string.compression_no_saving)) },
            text = { Text(stringResource(R.string.output_use_warning)) },
            confirmButton = { TextButton(onClick = { model.confirmCopy(captured); copyKey = null },
                modifier = Modifier.testTag("batch-confirm-copy")) { Text(stringResource(R.string.continue_output)) } },
            dismissButton = { TextButton(onClick = { copyKey = null }) { Text(stringResource(R.string.cancel)) } })
    }
    reviewing?.let { revision ->
        AlertDialog(onDismissRequest = { reviewing = null }, title = { Text(stringResource(R.string.batch_zip, selected, batch.items.size)) },
            text = { Text(stringResource(R.string.batch_review, selected, batch.items.size)) },
            confirmButton = { TextButton(onClick = { model.pack(revision); reviewing = null }, enabled = canPack,
                modifier = Modifier.testTag("batch-confirm-zip")) { Text(stringResource(R.string.continue_output)) } },
            dismissButton = { TextButton(onClick = { reviewing = null }) { Text(stringResource(R.string.cancel)) } })
    }
    if (clearing) AlertDialog(onDismissRequest = { clearing = false }, title = { Text(stringResource(R.string.clear_session)) },
        text = { Text(stringResource(R.string.clear_work_confirmation, stringResource(batchTitle(kind)))) },
        confirmButton = { TextButton(onClick = { model.clear(); clearing = false }, modifier = Modifier.testTag("batch-confirm-clear")) {
            Text(stringResource(R.string.clear_session)) } },
        dismissButton = { TextButton(onClick = { clearing = false }) { Text(stringResource(R.string.cancel)) } })
}

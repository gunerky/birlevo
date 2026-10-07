package com.example.contentstudio.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.contentstudio.R
import com.example.contentstudio.data.*
import com.example.contentstudio.documents.*
import com.example.contentstudio.media.MediaOutputs
import com.example.contentstudio.media.SupportedTool
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TextDocumentScreen(model: TextDocumentViewModel, onClose: () -> Unit, presentations: List<StudioProject>,
    onReadPdf: (Uri) -> Unit, onAddText: suspend (List<Slide>, String?) -> Boolean) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(model::load) }
    var pendingResult by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingOriginal by rememberSaveable { mutableStateOf<String?>(null) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) it.data?.data?.let { uri -> model.export(uri, pendingResult, pendingOriginal) }
        pendingResult = null; pendingOriginal = null
    }
    var actionBusy by remember { mutableStateOf(false) }
    var actionFailed by remember { mutableStateOf(false) }
    var choosePresentation by rememberSaveable { mutableStateOf(false) }
    var detailRow by rememberSaveable(state.source?.file?.path) { mutableStateOf<Int?>(null) }
    var selection by rememberSaveable(state.source?.file?.path, state.kind) { mutableIntStateOf(0) }
    LaunchedEffect(state.content) {
        val count = if (state.kind == DocumentKind.TEXT) state.previewChunks.size else state.content?.rows?.size ?: 0
        selection = selection.coerceIn(0, maxOf(0, count - 1)); detailRow = null
    }
    val active = !state.busy && !actionBusy
    fun close() { if (!actionBusy) { model.reset(); onClose() } }
    fun add(target: String?) {
        val content = state.content ?: return
        val name = state.source?.name?.substringBeforeLast('.')?.take(40).orEmpty()
        val text = if (content.kind == DocumentKind.TEXT) state.previewChunks.getOrNull(selection)
            else content.rows.getOrNull(selection)?.mapIndexed { column, value -> context.getString(R.string.document_column, column + 1) + ": " + value }?.joinToString("\n")
        if (text == null || !active) return
        choosePresentation = false; actionBusy = true; actionFailed = false
        scope.launch {
            try {
                val slides = withContext(Dispatchers.Default) {
                    TextDocumentCodec.chunks(text, 420).map { Slide(heading = name, body = it) }
                }
                check(slides.isNotEmpty() && slides.size <= SlideOperations.MAX_SLIDES && onAddText(slides, target))
                model.reset()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { actionFailed = true }
            finally { actionBusy = false }
        }
    }
    BackHandler { close() }
    Scaffold(contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout), topBar = { TopAppBar(windowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top), title = { Text(stringResource(R.string.read_documents), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = { TextButton(onClick = ::close, enabled = !actionBusy, modifier = Modifier.testTag("document-back")) { Text(stringResource(R.string.back)) } }) }) { padding ->
        LazyColumn(Modifier.widthIn(max = 900.dp).fillMaxSize().padding(padding).padding(horizontal = 20.dp).testTag("document-list"),
            contentPadding = PaddingValues(vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(key = "choose") { ToolImportCard(StudioGlyph.SLIDES, "TXT / CSV / TSV / MD", stringResource(R.string.choose_document),
                stringResource(R.string.document_limits), active, "choose-document") { pick.launch((DocumentPolicy.inputMimes + "application/csv" + "application/vnd.ms-excel").toTypedArray()) } }
            item(key = "support") { SupportedFilesButton(SupportedTool.TEXT_DOCUMENT) }
            item(key = "encoding") {
                Text(stringResource(R.string.document_encoding), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DocumentEncoding.entries.forEach { encoding -> FilterChip(state.encoding == encoding, { model.setEncoding(encoding) }, enabled = active,
                        label = { Text(encoding.charsetName ?: stringResource(R.string.document_auto)) }, modifier = Modifier.testTag("document-encoding-${encoding.name}")) }
                }
            }
            if (state.busy || actionBusy) item(key = "busy") {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(R.string.processing))
                if (state.busy) TextButton(onClick = model::cancel, modifier = Modifier.testTag("document-cancel")) { Text(stringResource(R.string.cancel)) }
            }
            state.issue?.let { issue -> item(key = "error") { Text(stringResource(when (issue) {
                DocumentIssue.ACCESS -> R.string.error_file_access
                DocumentIssue.ENCODING -> R.string.document_bad_encoding
                DocumentIssue.INVALID -> R.string.document_invalid
                DocumentIssue.LIMIT -> R.string.document_table_limits
                DocumentIssue.PDF_LIMIT -> R.string.document_pdf_limit
                DocumentIssue.EXPORT -> R.string.pdf_export_error
                DocumentIssue.MEMORY -> R.string.error_memory
            }), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("document-error")) } }
            if (actionFailed) item(key = "action-error") { Text(stringResource(R.string.output_action_error), color = MaterialTheme.colorScheme.error) }
            state.source?.let { source -> item(key = "source") {
                Text(source.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("document-source"))
                Text(Formatter.formatShortFileSize(context, source.bytes) + " · " + state.content?.encoding.orEmpty())
            } }
            if (state.source != null) item(key = "kind") {
                Text(stringResource(R.string.document_input_format))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DocumentKind.entries.forEach { kind -> FilterChip(state.kind == kind, { model.setKind(kind) }, enabled = active,
                        label = { Text(if (kind == DocumentKind.TEXT) "TXT" else kind.name) }, modifier = Modifier.testTag("document-kind-${kind.name}")) }
                }
                if (state.kind == DocumentKind.CSV) {
                    Text(stringResource(R.string.document_separator))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(null to R.string.document_auto, ',' to R.string.document_comma, ';' to R.string.document_semicolon, '\t' to R.string.document_tab).forEach { (separator, label) ->
                            FilterChip(state.delimiter == separator, { model.setDelimiter(separator) }, enabled = active,
                                label = { Text(stringResource(label)) }, modifier = Modifier.testTag("document-separator-${separator?.code ?: 0}"))
                        }
                    }
                }
            }
            state.content?.let { content ->
                item(key = "output") {
                    Text(stringResource(R.string.output_format), style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { DocumentPolicy.targets(content.kind).forEach { target ->
                        FilterChip(state.target == target, { model.setTarget(target) }, enabled = active,
                            label = { Text(target.name) }, modifier = Modifier.testTag("document-target-${target.name}"))
                    } }
                    Text(stringResource(R.string.document_losses), style = MaterialTheme.typography.bodySmall)
                    Button(onClick = model::create, enabled = active, modifier = Modifier.testTag("document-create")) { Text(stringResource(R.string.document_generate)) }
                }
                state.result?.let { result -> item(key = "result") {
                    val base = state.source?.name?.substringBeforeLast('.')?.replace('/', '_')?.replace('\\', '_')?.filterNot { it.isISOControl() }?.take(64)?.ifBlank { "document" } ?: "document"
                    val name = "$base.${result.target.extension}"
                    Text(result.target.name + " · " + Formatter.formatShortFileSize(context, result.file.length()), modifier = Modifier.testTag("document-result"))
                    Button(onClick = {
                        pendingResult = result.file.name; pendingOriginal = state.source?.originalUri?.toString()
                        save.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(result.target.mime).putExtra(Intent.EXTRA_TITLE, name))
                    }, enabled = active, modifier = Modifier.testTag("document-save")) { Text(stringResource(R.string.document_save)) }
                    OutputActions(result.file, result.target.mime, name, active)
                    if (result.target == DocumentTarget.PDF) OutlinedButton(onClick = {
                        actionBusy = true; actionFailed = false
                        scope.launch {
                            try {
                                val uri = withContext(Dispatchers.IO) { MediaOutputs(context).stage(result.file, result.target.mime, name).uri }
                                onReadPdf(uri); model.reset()
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { actionFailed = true }
                            finally { actionBusy = false }
                        }
                    }, enabled = active, modifier = Modifier.testTag("document-read-pdf")) { Text(stringResource(R.string.read_pdf)) }
                } }
                if (state.saved) item(key = "saved") { Text(stringResource(R.string.document_saved), modifier = Modifier.testTag("document-saved")) }
                item(key = "preview-title") {
                    if (content.kind != DocumentKind.TEXT) Text(stringResource(R.string.document_table_info, content.rows.size, content.columns), modifier = Modifier.testTag("document-table-info"))
                    Text(stringResource(R.string.document_preview_hint), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.document_selected, selection + 1))
                    OutlinedButton(onClick = { choosePresentation = true }, enabled = active, modifier = Modifier.testTag("document-add-presentation")) { Text(stringResource(R.string.add_to_presentation)) }
                }
                if (content.kind == DocumentKind.TEXT) itemsIndexed(state.previewChunks, key = { index, _ -> "text-$index" }, contentType = { _, _ -> "text" }) { index, text ->
                    Card(onClick = { selection = index }, enabled = active, border = BorderStroke(1.dp, if (selection == index) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth().testTag("document-text-$index")) { Text(text, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium) }
                } else itemsIndexed(content.rows, key = { index, _ -> "row-$index" }, contentType = { _, _ -> "row" }) { index, row ->
                    Card(onClick = { selection = index; detailRow = index }, enabled = active, border = BorderStroke(1.dp,
                        if (selection == index) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth().testTag("document-row-$index")) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.document_row, index + 1), style = MaterialTheme.typography.labelLarge)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.take(3).forEach { value ->
                                    val prefix = value.take(256).let { if (it.lastOrNull()?.isHighSurrogate() == true) it.dropLast(1) else it }
                                    Text(prefix, Modifier.weight(1f), maxLines = 4, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    detailRow?.let { index ->
        val row = state.content?.rows?.getOrNull(index)
        if (row != null) AlertDialog(onDismissRequest = { detailRow = null }, title = { Text(stringResource(R.string.document_row, index + 1)) },
            text = { LazyColumn(Modifier.heightIn(max = 440.dp)) {
                row.forEachIndexed { column, value ->
                    item(key = "label-$column") { Text(stringResource(R.string.document_column, column + 1), style = MaterialTheme.typography.titleSmall) }
                    TextDocumentCodec.chunks(value).forEachIndexed { chunk, text -> item(key = "cell-$column-$chunk") { Text(text) } }
                }
            } }, confirmButton = { TextButton(onClick = { detailRow = null }) { Text(stringResource(R.string.close)) } })
    }
    if (choosePresentation) AlertDialog(onDismissRequest = { choosePresentation = false }, title = { Text(stringResource(R.string.add_to_presentation)) },
        text = { LazyColumn(Modifier.heightIn(max = 360.dp)) {
            item(key = "new") { TextButton(onClick = { add(null) }, modifier = Modifier.testTag("document-new-presentation")) { Text(stringResource(R.string.new_presentation)) } }
            presentations.filter { it.kind == ProjectKind.PRESENTATION }.forEach { project -> item(key = project.id) {
                TextButton(onClick = { add(project.id) }, enabled = project.slides.size < SlideOperations.MAX_SLIDES) { Text(project.title) }
            } }
        } }, confirmButton = { TextButton(onClick = { choosePresentation = false }) { Text(stringResource(R.string.cancel)) } })
}

package com.example.contentstudio.ui

import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.contentstudio.R
import com.example.contentstudio.media.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PdfPagesScreen(model: PdfPagesViewModel, onClose: () -> Unit, onRead: (Uri) -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    var range by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf(false) }
    val selected = PdfPagePlan.selection(range, state.pages.size)
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { model.add(it) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { if (it != null) model.export(it) else model.cancelExport() }
    fun close() { focus.clearFocus(); model.reset(); onClose() }
    BackHandler { close() }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.pdf_pages), maxLines = 1, overflow = TextOverflow.Ellipsis) }, navigationIcon = {
        TextButton(onClick = ::close, modifier = Modifier.testTag("pages-back")) { Text(stringResource(R.string.back)) }
    }) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max = 900.dp).fillMaxSize().padding(horizontal = 20.dp).testTag("pages-list"),
                contentPadding = PaddingValues(vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item(key = "import") {
                    ToolImportCard(StudioGlyph.SLIDES, stringResource(R.string.pages_hint), stringResource(R.string.pages_add),
                        stringResource(R.string.pages_limits), !state.busy, "pages-add") { pick.launch(arrayOf("application/pdf")) }
                }
                item(key = "support") { SupportedFilesButton(SupportedTool.PDF_PAGES) }
                if (state.busy) item(key = "busy") {
                    Column { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(stringResource(R.string.processing))
                        TextButton(onClick = model::cancel, modifier = Modifier.testTag("pages-cancel")) { Text(stringResource(R.string.cancel)) } }
                }
                state.issue?.let { issue -> item(key = "error") {
                    Text(stringResource(when (issue) {
                        PdfPageIssue.PROTECTED -> R.string.pages_protected
                        PdfPageIssue.COMPLEX -> R.string.pages_complex
                        PdfPageIssue.LIMIT -> R.string.pages_limits
                        PdfPageIssue.ACCESS -> R.string.error_file_access
                        PdfPageIssue.MEMORY -> R.string.error_memory
                        PdfPageIssue.PROCESS -> R.string.reader_process_error
                        PdfPageIssue.EXPORT -> R.string.pdf_export_error
                        PdfPageIssue.INVALID -> R.string.reader_invalid
                    }), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("pages-error"))
                } }
                if (state.pages.isNotEmpty()) {
                    item(key = "selection") {
                        ToolSection(stringResource(R.string.pages_range)) {
                            Text(stringResource(R.string.pages_range_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedTextField(range, { range = it }, singleLine = true, enabled = !state.busy,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr),
                                colors = studioFieldColors(), isError = range.isNotBlank() && selected == null,
                                label = { Text(stringResource(R.string.pages_range)) },
                                modifier = Modifier.fillMaxWidth().testTag("pages-range"))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { focus.clearFocus(); model.select(range); range = "" }, enabled = !state.busy && selected != null,
                                    modifier = Modifier.testTag("pages-select")) { Text(stringResource(R.string.pages_select)) }
                                TextButton(onClick = model::undo, enabled = !state.busy && state.canUndo,
                                    modifier = Modifier.testTag("pages-undo")) { Text(stringResource(R.string.pages_undo)) }
                            }
                        }
                    }
                    item(key = "create") {
                        ToolSection(stringResource(R.string.pages_create)) {
                            Text(stringResource(R.string.pages_count, state.sources.size, state.pages.size),
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(stringResource(R.string.pages_loss), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(onClick = { focus.clearFocus(); confirm = true }, enabled = !state.busy,
                                modifier = Modifier.fillMaxWidth().testTag("pages-create")) { Text(stringResource(R.string.pages_create)) }
                        }
                    }
                    state.output?.let { output -> item(key = "output") {
                        ToolSection(stringResource(R.string.preview_pdf)) {
                            state.preview?.let { Image(it.asImageBitmap(), stringResource(R.string.preview_pdf),
                                Modifier.fillMaxWidth().height(220.dp).testTag("pages-preview")) }
                            Text(Formatter.formatShortFileSize(context, output.bytes), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(onClick = { model.prepareExport(); save.launch("pages.pdf") }, enabled = !state.busy,
                                modifier = Modifier.fillMaxWidth().testTag("pages-save")) { Text(stringResource(R.string.reader_save_copy)) }
                            OutlinedButton(onClick = { model.stageForReader(onRead) }, enabled = !state.busy,
                                modifier = Modifier.testTag("pages-read")) { Text(stringResource(R.string.read_pdf)) }
                            OutputActions(output.file, "application/pdf", output.name, !state.busy)
                            if (state.saved) Text(stringResource(R.string.reader_saved), Modifier.testTag("pages-saved"))
                        }
                    } }
                    itemsIndexed(state.pages, key = { _, page -> page.id }, contentType = { _, _ -> "page" }) { index, page ->
                        ToolSection(stringResource(R.string.pdf_page, index + 1, state.pages.size)) {
                            Text(state.sources[page.source].name, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(stringResource(R.string.pages_source, page.page + 1, page.rotation), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("pages-info-$index"))
                            if (state.previewId == page.id) state.pagePreview?.let { bitmap ->
                                Image(bitmap.asImageBitmap(), stringResource(R.string.pdf_page, index + 1, state.pages.size),
                                    Modifier.fillMaxWidth().height(240.dp).testTag("pages-page-preview-$index"))
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { model.previewPage(page.id) }, enabled = !state.busy,
                                    modifier = Modifier.testTag("pages-page-show-$index")) {
                                    Text(stringResource(if (state.previewId == page.id) R.string.hide_slide_preview else R.string.pages_preview))
                                }
                                TextButton(onClick = { model.move(page.id, -1) }, enabled = !state.busy && index > 0,
                                    modifier = Modifier.testTag("pages-up-$index")) { Text(stringResource(R.string.move_up)) }
                                TextButton(onClick = { model.move(page.id, 1) }, enabled = !state.busy && index < state.pages.lastIndex,
                                    modifier = Modifier.testTag("pages-down-$index")) { Text(stringResource(R.string.move_down)) }
                                OutlinedButton(onClick = { model.rotate(page.id) }, enabled = !state.busy,
                                    modifier = Modifier.testTag("pages-rotate-$index")) { Text(stringResource(R.string.pages_rotate)) }
                                TextButton(onClick = { model.remove(page.id) }, enabled = !state.busy && state.pages.size > 1,
                                    modifier = Modifier.testTag("pages-remove-$index")) { Text(stringResource(R.string.pages_remove)) }
                            }
                        }
                    }
                }
            }
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text(stringResource(R.string.pages_create)) },
        text = { Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.pages_loss))
        } },
        confirmButton = { TextButton(onClick = { confirm = false; model.create() }, modifier = Modifier.testTag("pages-confirm")) {
            Text(stringResource(R.string.pages_create)) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) } })
}

/** A neutral section surface separates settings from results without competing accent fills. */
@Composable
internal fun ToolSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            content()
        }
    }
}

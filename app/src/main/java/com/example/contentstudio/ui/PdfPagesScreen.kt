package com.example.contentstudio.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
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
    val pick =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {
            model.add(it)
        }
    val save =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/pdf")
        ) {
            if (it != null) model.export(it) else model.cancelExport()
        }
    fun close() {
        focus.clearFocus()
        model.cancel()
        onClose()
    }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedPage = state.pages.firstOrNull { it.id == selectedId } ?: state.pages.firstOrNull()
    var compareOriginal by rememberSaveable(state.output?.file?.path) { mutableStateOf(false) }
    LaunchedEffect(
        selectedPage?.id,
        selectedPage?.rotation,
        state.busy,
        state.output?.file,
        compareOriginal,
    ) {
        selectedPage?.let {
            if (
                !state.busy && state.issue == null && (state.output == null || compareOriginal) && state.previewId != it.id
            )
                model.previewPage(it.id)
        }
    }
    ToolWorkspace(
        stringResource(R.string.pdf_pages),
        "pages",
        ::close,
        resultKey = state.output?.file?.path,
        panels =
            if (state.pages.isNotEmpty())
                listOf(WorkspacePanel("selection", stringResource(R.string.pages_range)))
            else emptyList(),
        actions = {
            if (state.busy) {
                Column {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(stringResource(R.string.processing))
                    TextButton(
                        onClick = model::cancel,
                        modifier = Modifier.testTag("pages-cancel"),
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            } else if (state.output != null)
                state.output?.let { output ->
                    Button(
                        onClick = {
                            model.prepareExport()
                            save.launch("pages.pdf")
                        },
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth().testTag("pages-save"),
                    ) {
                        Text(stringResource(R.string.reader_save_copy))
                    }
                    OutlinedButton(
                        onClick = { model.stageForReader(onRead) },
                        enabled = !state.busy,
                        modifier = Modifier.testTag("pages-read"),
                    ) {
                        Text(stringResource(R.string.read_pdf))
                    }
                    OutputActions(output.file, "application/pdf", output.name, !state.busy)
                }
            else if (state.pages.isNotEmpty())
                Button(
                    onClick = {
                        focus.clearFocus()
                        confirm = true
                    },
                    modifier = Modifier.fillMaxWidth().testTag("pages-create"),
                ) {
                    Text(stringResource(R.string.create_result))
                }
        },
        panelContent = {
            ToolSection(stringResource(R.string.pages_range)) {
                Text(
                    stringResource(R.string.pages_range_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    range,
                    { range = it },
                    singleLine = true,
                    enabled = !state.busy,
                    textStyle =
                        MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr),
                    colors = studioFieldColors(),
                    isError = range.isNotBlank() && selected == null,
                    label = { Text(stringResource(R.string.pages_range)) },
                    modifier = Modifier.fillMaxWidth().testTag("pages-range"),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            focus.clearFocus()
                            model.select(range)
                            range = ""
                        },
                        enabled = !state.busy && selected != null,
                        modifier = Modifier.testTag("pages-select"),
                    ) {
                        Text(stringResource(R.string.pages_select))
                    }
                    TextButton(
                        onClick = model::undo,
                        enabled = !state.busy && state.canUndo,
                        modifier = Modifier.testTag("pages-undo"),
                    ) {
                        Text(stringResource(R.string.pages_undo))
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().testTag("pages-list"),
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "import") {
                if (state.pages.isEmpty()) {
                    ToolImportCard(
                        StudioGlyph.SLIDES,
                        stringResource(R.string.pages_hint),
                        stringResource(R.string.pages_add),
                        stringResource(R.string.pages_limits),
                        !state.busy,
                        "pages-add",
                    ) {
                        pick.launch(arrayOf("application/pdf"))
                    }
                } else
                    SourceHeader(
                        stringResource(R.string.pdf_pages),
                        stringResource(R.string.pages_count, state.sources.size, state.pages.size),
                        !state.busy,
                        "pages-add",
                        stringResource(R.string.pages_add),
                    ) {
                        pick.launch(arrayOf("application/pdf"))
                    }
                Text(
                    stringResource(R.string.pages_loss),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.issue?.let { issue ->
                item(key = "error") {
                    Text(
                        stringResource(
                            when (issue) {
                                PdfPageIssue.PROTECTED -> R.string.pages_protected
                                PdfPageIssue.COMPLEX -> R.string.pages_complex
                                PdfPageIssue.LIMIT -> R.string.pages_limits
                                PdfPageIssue.ACCESS -> R.string.error_file_access
                                PdfPageIssue.MEMORY -> R.string.error_memory
                                PdfPageIssue.PROCESS -> R.string.reader_process_error
                                PdfPageIssue.EXPORT -> R.string.pdf_export_error
                                PdfPageIssue.INVALID -> R.string.reader_invalid
                            }
                        ),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("pages-error"),
                    )
                    if (selectedPage != null && state.previewFailureId == selectedPage.id)
                        TextButton(onClick = { model.previewPage(selectedPage.id) }, enabled = !state.busy,
                            modifier = Modifier.testTag("pages-preview-retry")) { Text(stringResource(R.string.retry)) }
                }
            }
            item(key = "preview") {
                if (state.output != null)
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
                state.preview
                    ?.takeIf { state.output != null && !compareOriginal }
                    ?.let {
                        Image(
                            it.asImageBitmap(),
                            stringResource(R.string.preview_pdf),
                            Modifier.fillMaxWidth().height(240.dp).testTag("pages-preview"),
                        )
                    }
                if ((state.output == null || compareOriginal) && state.previewId == selectedPage?.id)
                    state.pagePreview?.let {
                        Image(
                            it.asImageBitmap(),
                            stringResource(R.string.preview_pdf),
                            Modifier.fillMaxWidth()
                                .height(300.dp)
                                .testTag("pages-page-preview-${state.pages.indexOf(selectedPage)}"),
                        )
                    }
                if (state.saved)
                    Text(stringResource(R.string.reader_saved), Modifier.testTag("pages-saved"))
            }
            itemsIndexed(
                state.pages,
                key = { _, page -> page.id },
                contentType = { _, _ -> "page" },
            ) { index, page ->
                Card(
                    onClick = {
                        selectedId = page.id
                        compareOriginal = true
                        if (state.previewId != page.id || state.previewFailureId == page.id) model.previewPage(page.id)
                    },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth().testTag("pages-item-$index").accessibleSelection(selectedPage?.id == page.id),
                ) {
                    ToolSection(stringResource(R.string.pdf_page, index + 1, state.pages.size)) {
                        Text(
                            state.sources[page.source].name,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            stringResource(R.string.pages_source, page.page + 1, page.rotation),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("pages-info-$index"),
                        )
                        if (page.id == selectedPage?.id)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(
                                    onClick = { model.move(page.id, -1) },
                                    enabled = !state.busy && index > 0,
                                    modifier = Modifier.testTag("pages-up-$index"),
                                ) {
                                    Text(stringResource(R.string.move_up))
                                }
                                TextButton(
                                    onClick = { model.move(page.id, 1) },
                                    enabled = !state.busy && index < state.pages.lastIndex,
                                    modifier = Modifier.testTag("pages-down-$index"),
                                ) {
                                    Text(stringResource(R.string.move_down))
                                }
                                OutlinedButton(
                                    onClick = { model.rotate(page.id) },
                                    enabled = !state.busy,
                                    modifier = Modifier.testTag("pages-rotate-$index"),
                                ) {
                                    Text(stringResource(R.string.pages_rotate))
                                }
                                TextButton(
                                    onClick = { model.remove(page.id) },
                                    enabled = !state.busy && state.pages.size > 1,
                                    modifier = Modifier.testTag("pages-remove-$index"),
                                ) {
                                    Text(stringResource(R.string.pages_remove))
                                }
                            }
                    }
                }
            }
            item(key = "help") { WorkspaceHelp { SupportedFilesButton(SupportedTool.PDF_PAGES) } }
        }
    }
    if (confirm)
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.pages_create)) },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.pages_loss))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirm = false
                        model.create()
                    },
                    modifier = Modifier.testTag("pages-confirm"),
                ) {
                    Text(stringResource(R.string.pages_create))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
}

/** A neutral section surface separates settings from results without competing accent fills. */
@Composable
internal fun ToolSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                title,
                modifier = Modifier.accessibleHeading(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            content()
        }
    }
}

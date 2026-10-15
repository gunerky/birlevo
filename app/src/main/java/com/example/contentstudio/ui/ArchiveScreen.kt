package com.example.contentstudio.ui

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.net.toUri
import com.example.contentstudio.R
import com.example.contentstudio.archive.*

@Composable
fun ArchiveScreen(onClose: () -> Unit, initialUri: String? = null, model: ArchiveViewModel = viewModel()) {
    val state by model.state.collectAsState()
    var consumed by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { if (initialUri == null || consumed == initialUri) model.reload() }
    var pendingRevision by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingPath by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingFolder by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(model::open) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val revision = pendingRevision; val path = pendingPath
        if (uri != null && revision != null && path != null) model.save(revision, path, uri)
        pendingRevision = null; pendingPath = null
    }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val token = pendingFolder
        if (uri != null && token != null) model.extract(token, uri)
        pendingFolder = null
    }
    LaunchedEffect(state.picker, state.busy) {
        val token = state.picker
        if (token != null && !state.busy && pendingFolder == null) {
            pendingFolder = token; model.pickerLaunched(); folder.launch(null)
        }
    }
    LaunchedEffect(initialUri, state.busy) {
        if (initialUri != null && initialUri != consumed && !state.busy) {
            consumed = initialUri; model.open(initialUri.toUri())
        }
    }
    val context = LocalContext.current
    ToolWorkspace(stringResource(R.string.archive_title), "archive", {
        model.cancel(); onClose()
    }, actions = {
        if (state.busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(stringResource(R.string.archive_foreground), style = MaterialTheme.typography.bodySmall)
            if (state.total > 0) Text("${state.completed} / ${state.total}")
            TextButton(onClick = model::cancel, modifier = Modifier.testTag("archive-cancel")) { Text(stringResource(R.string.cancel)) }
        } else {
            if (state.selected.isNotEmpty()) Button(onClick = model::prepareFolder,
                enabled = pendingFolder == null && pendingPath == null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("archive-extract")) {
                Text(stringResource(R.string.archive_extract))
            }
            OutlinedButton(onClick = { open.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("archive-open")) {
                Text(stringResource(if (state.session == null) R.string.open_file else R.string.change_file))
            }
            if (state.session != null || state.error != null) TextButton(onClick = { confirmClear = true }, modifier = Modifier.testTag("archive-clear")) {
                Text(stringResource(R.string.clear_session))
            }
        }
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("archive-list"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { WorkspaceHelp { Column {
                Text(stringResource(R.string.archive_hint), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.archive_folder_hint), style = MaterialTheme.typography.bodySmall)
                state.inputLimit?.let { Text(stringResource(R.string.archive_capacity,
                    Formatter.formatFileSize(context, it)), style = MaterialTheme.typography.bodySmall) }
            } } }
            state.error?.let { error -> item {
                Text(stringResource(when(error) {
                    ArchiveIssue.UNSUPPORTED -> R.string.archive_unsupported
                    ArchiveIssue.PATH -> R.string.archive_path
                    ArchiveIssue.LIMIT -> R.string.archive_limit
                    ArchiveIssue.INVALID, ArchiveIssue.CORRUPT -> R.string.archive_corrupt
                    ArchiveIssue.ACCESS -> R.string.archive_access
                    ArchiveIssue.STORAGE -> R.string.storage_capacity_error
                }), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("archive-error"))
            } }
            if (state.saved != null) item { Text(stringResource(R.string.reader_saved), modifier = Modifier.testTag("archive-saved")) }
            if (state.extracted != null) item {
                Text(stringResource(R.string.archive_extracted, state.completed), modifier = Modifier.testTag("archive-extracted"))
            }
            state.partial?.let { uri -> item {
                Text(stringResource(R.string.archive_partial) + "\n" + uri.toString(),
                    color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("archive-partial"))
            } }
            state.session?.let { session ->
                item { Text(session.name, style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Ltr)) }
                if (session.entries.any { !it.directory }) item {
                    TextButton(onClick = model::selectAll, enabled = !state.busy && pendingFolder == null,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("archive-select-all")) {
                        Text(stringResource(if (state.selected.size == session.entries.count { !it.directory }) R.string.archive_select_none else R.string.archive_select_all))
                    }
                }
                if (session.entries.none { !it.directory }) item { Text(stringResource(R.string.archive_empty)) }
                items(session.entries.filterNot { it.directory }, key = { it.path }) { entry ->
                    val saveLabel = stringResource(R.string.save)
                    Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 1.dp) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                                value = entry.path in state.selected, enabled = !state.busy && pendingFolder == null,
                                role = Role.Checkbox, onValueChange = { model.select(entry.path, it) })
                                .testTag("archive-select-${entry.path}")) {
                                Checkbox(checked = entry.path in state.selected, onCheckedChange = null)
                                Text(entry.path, modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr))
                            }
                            Text(Formatter.formatFileSize(context, entry.bytes), style = MaterialTheme.typography.bodySmall)
                            TextButton(enabled = !state.busy && pendingPath == null, onClick = {
                                pendingRevision = session.revision; pendingPath = entry.path
                                save.launch(entry.path.substringAfterLast('/'))
                            }, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "$saveLabel: ${entry.path}" }
                                .testTag("archive-save-${entry.path}")) { Text(saveLabel) }
                        }
                    }
                }
            }
        }
    }
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text(stringResource(R.string.clear_session)) },
        text = { Text(stringResource(R.string.archive_clear_hint)) },
        confirmButton = { TextButton(onClick = { confirmClear = false; model.clear() }) { Text(stringResource(R.string.clear_session)) } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) } })
}

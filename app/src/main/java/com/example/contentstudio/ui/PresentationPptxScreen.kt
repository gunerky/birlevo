package com.example.contentstudio.ui

import android.content.Intent
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.contentstudio.R
import com.example.contentstudio.data.StudioProject
import com.example.contentstudio.media.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PresentationPptxScreen(
    project: StudioProject,
    model: PresentationPptxViewModel,
    onClose: () -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val session by model.sessionStatus.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var expected by rememberSaveable { mutableStateOf<String?>(null) }
    val save =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            response ->
            if (response.resultCode == android.app.Activity.RESULT_OK)
                response.data?.data?.let { model.export(it, expected.orEmpty()) }
            expected = null
        }
    LaunchedEffect(project) { model.prepare(project) }
    DisposableEffect(model) { onDispose { model.leave() } }
    ToolWorkspace(
        stringResource(R.string.pptx_title),
        "pptx",
        {
            model.leave()
            onClose()
        },
        panels = listOf(WorkspacePanel("output", stringResource(R.string.workspace_output))),
        resultKey = state.result?.file?.path,
        actions = {
            if (state.busy) {
                state.progress?.let {
                    LinearProgressIndicator(
                        progress = { it / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } ?: LinearProgressIndicator(Modifier.fillMaxWidth())
                TextButton(model::cancel, Modifier.testTag("pptx-cancel")) {
                    Text(stringResource(R.string.cancel))
                }
            } else if (state.result != null) {
                val result = state.result!!
                Button(
                    {
                        expected = result.file.path
                        save.launch(
                            Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = PresentationPptxEngine.MIME
                                putExtra(
                                    Intent.EXTRA_TITLE,
                                    project.title
                                        .filterNot { it.isISOControl() || it == '/' || it == '\\' }
                                        .take(64)
                                        .ifBlank { "presentation" } + ".pptx",
                                )
                            }
                        )
                    },
                    Modifier.testTag("pptx-save"),
                ) {
                    Text(stringResource(R.string.save))
                }
                OutputActions(
                    result.file,
                    PresentationPptxEngine.MIME,
                    "presentation.pptx",
                    true,
                    validateResult = { model.verify(result) },
                )
            } else
                Button(
                    model::generate,
                    Modifier.testTag("pptx-create"),
                    enabled = state.project?.slides?.isNotEmpty() == true,
                ) {
                    Text(stringResource(R.string.create_result))
                }
        },
        panelContent = {
            Text(stringResource(R.string.max_edge))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1024, 2048, 4096).forEach { edge ->
                    FilterChip(
                        state.options.edge == edge,
                        { model.options(state.options.copy(edge = edge)) },
                        enabled = !state.busy,
                        label = { Text("$edge px") },
                        modifier = Modifier.testTag("pptx-edge-$edge"),
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .semantics(mergeDescendants = true) {}
                    .toggleable(
                        state.options.includeNotes,
                        enabled = !state.busy,
                        role = Role.Checkbox,
                        onValueChange = { model.options(state.options.copy(includeNotes = it)) },
                    )
                    .testTag("pptx-notes"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(state.options.includeNotes, onCheckedChange = null, enabled = !state.busy)
                Text(
                    stringResource(R.string.pptx_notes),
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
            Text(
                stringResource(R.string.pptx_notes_hint),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(stringResource(R.string.pptx_limit), style = MaterialTheme.typography.bodySmall)
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().testTag("pptx-list"),
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(project.title, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.pptx_loss), modifier = Modifier.testTag("pptx-loss"))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(project.slides, key = { _, slide -> slide.id }) { index, _ ->
                        FilterChip(
                            state.selected == index,
                            { model.select(index) },
                            label = { Text(stringResource(R.string.slide_number, index + 1)) },
                            modifier = Modifier.testTag("pptx-select-$index"),
                        )
                    }
                }
            }
            item {
                if (state.result == null) {
                    Text(
                        stringResource(R.string.pptx_source_preview),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    if (state.selected in project.slides.indices)
                        PresentationSlidePreview(project, state.selected, rasterEdge = 512)
                } else {
                    Text(
                        stringResource(R.string.pptx_result_preview),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    state.preview?.let { bitmap ->
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            Image(
                                bitmap.asImageBitmap(),
                                stringResource(R.string.slide_design_preview),
                                Modifier.fillMaxWidth()
                                    .height(minOf(maxWidth * 9 / 16, 360.dp))
                                    .testTag("pptx-preview"),
                            )
                        }
                    }
                    state.result?.let {
                        Text(
                            "${it.edge} × ${it.edge * 9 / 16} · ${it.pages} · ${Formatter.formatFileSize(context, it.bytes)}",
                            modifier = Modifier.testTag("pptx-size"),
                        )
                    }
                }
            }
            item {
                ProcessingStatusHint(state.workStatus, "pptx")
                if (session == SessionSaveStatus.ERROR) {
                    Text(
                        stringResource(R.string.session_error),
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(model::retrySession) { Text(stringResource(R.string.retry)) }
                }
                state.message?.let { message ->
                    Text(
                        stringResource(
                            when (message) {
                                SlideImageMessage.EXPORTED -> R.string.reader_saved
                                SlideImageMessage.EXPORT_ERROR -> R.string.video_export_error
                                else -> R.string.pptx_error
                            }
                        ),
                        color =
                            if (message == SlideImageMessage.EXPORTED)
                                MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("pptx-message"),
                    )
                }
            }
        }
    }
}

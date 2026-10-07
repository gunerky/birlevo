package com.example.contentstudio.ui

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.contentstudio.R
import com.example.contentstudio.data.StudioProject
import com.example.contentstudio.media.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresentationPdfScreen(project: StudioProject, onClose: () -> Unit,
    model: PresentationPdfViewModel = viewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri?.let(model::export)
    }
    LaunchedEffect(project) { model.prepare(project) }
    fun close() { model.reset(); onClose() }
    BackHandler { close() }
    Scaffold(contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout), topBar = {
        TopAppBar(windowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top), title = { Text(stringResource(R.string.preview_pdf), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = { TextButton(onClick = ::close, modifier = Modifier.testTag("pdf-back")) {
                Text(stringResource(R.string.back))
            } })
    }) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            val wide = maxWidth >= 700.dp
            Column(Modifier.widthIn(max = 1100.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(project.title, style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.pdf_hint))
                if (wide) Row(Modifier.testTag("pdf-wide"), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    PdfPagePreview(state, model, Modifier.weight(2f))
                    PdfOutputControls(state, onSave = {
                        val base = project.title.replace('/', '_').replace('\\', '_').filterNot { it.isISOControl() }
                            .take(64).ifBlank { "presentation" }
                        save.launch("$base.pdf")
                    }, onRetry = { model.prepare(project) }, onCancel = model::cancel, modifier = Modifier.weight(1f))
                } else Column(Modifier.testTag("pdf-compact"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    PdfPagePreview(state, model)
                    PdfOutputControls(state, onSave = {
                        val base = project.title.replace('/', '_').replace('\\', '_').filterNot { it.isISOControl() }
                            .take(64).ifBlank { "presentation" }
                        save.launch("$base.pdf")
                    }, onRetry = { model.prepare(project) }, onCancel = model::cancel)
                }
                state.result?.let { Text(Formatter.formatShortFileSize(context, it.bytes)) }
                state.result?.let { OutputActions(it.file, "application/pdf", project.title + ".pdf", !state.busy) }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun PdfPagePreview(state: PresentationPdfState, model: PresentationPdfViewModel, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        state.preview?.let { bitmap ->
            Image(bitmap.asImageBitmap(), contentDescription = stringResource(R.string.pdf_page, state.pageIndex + 1, state.result!!.pages),
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9).testTag("pdf-page-preview"))
        }
        state.result?.let { result ->
            Text(stringResource(R.string.pdf_page, state.pageIndex + 1, result.pages), modifier = Modifier.testTag("pdf-page-number"))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { model.showPage(state.pageIndex - 1) }, enabled = !state.busy && state.pageIndex > 0,
                    modifier = Modifier.testTag("pdf-previous")) { Text(stringResource(R.string.pdf_previous)) }
                OutlinedButton(onClick = { model.showPage(state.pageIndex + 1) }, enabled = !state.busy && state.pageIndex + 1 < result.pages,
                    modifier = Modifier.testTag("pdf-next")) { Text(stringResource(R.string.pdf_next)) }
            }
        }
    }
}

@Composable
private fun PdfOutputControls(state: PresentationPdfState, onSave: () -> Unit, onRetry: () -> Unit,
    onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.result != null) Button(onClick = onSave, enabled = !state.busy, modifier = Modifier.testTag("save-pdf")) {
            Text(stringResource(R.string.save_pdf))
        }
        if (state.busy) {
            state.progress?.let { LinearProgressIndicator(progress = { it / 100f }, modifier = Modifier.fillMaxWidth()) }
                ?: CircularProgressIndicator()
            Text(stringResource(R.string.processing))
            TextButton(onClick = onCancel, modifier = Modifier.testTag("cancel-pdf")) { Text(stringResource(R.string.cancel)) }
        } else if (state.result == null) OutlinedButton(onClick = onRetry, modifier = Modifier.testTag("retry-pdf")) {
            Text(stringResource(R.string.retry))
        }
        state.message?.let { message ->
            Text(when (message) {
                PdfMessage.EMPTY -> stringResource(R.string.pdf_empty)
                PdfMessage.OVERFLOW -> stringResource(R.string.pdf_overflow, state.overflowSlide)
                PdfMessage.GENERATE_ERROR -> stringResource(R.string.pdf_generate_error)
                PdfMessage.EXPORT_ERROR -> stringResource(R.string.pdf_export_error)
                PdfMessage.EXPORTED -> stringResource(R.string.pdf_saved)
            }, modifier = Modifier.testTag("pdf-message"), color = if (message == PdfMessage.EXPORTED)
                MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        }
    }
}

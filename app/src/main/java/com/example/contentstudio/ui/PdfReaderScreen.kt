package com.example.contentstudio.ui

import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.contentstudio.R
import com.example.contentstudio.data.StudioProject
import com.example.contentstudio.media.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PdfReaderScreen(onClose: () -> Unit, presentations: List<StudioProject>,
    onAddToPresentation: suspend (Uri, String?) -> Boolean,
    onAddToPhotoPdf: suspend (Uri) -> Boolean,
    model: PdfReaderViewModel = viewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(model::load) }
    val copy = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { it?.let { uri -> model.export(uri, false) } }
    val image = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { it?.let { uri -> model.export(uri, true) } }
    fun close() { focus.clearFocus(); model.reset(); onClose() }
    BackHandler { close() }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.read_pdf), maxLines = 1, overflow = TextOverflow.Ellipsis) }, navigationIcon = {
        TextButton(onClick = ::close, modifier = Modifier.testTag("reader-back")) { Text(stringResource(R.string.back)) }
    }) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = 900.dp).fillMaxSize().padding(horizontal = 20.dp).testTag("reader-list"),
            contentPadding = PaddingValues(vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item(key = "choose") {
                ToolImportCard(StudioGlyph.SLIDES, stringResource(R.string.reader_hint), stringResource(R.string.choose_pdf),
                    stringResource(R.string.reader_limits), !state.busy, "choose-pdf") { pick.launch(arrayOf("application/pdf")) }
            }
            item(key = "support") { SupportedFilesButton(SupportedTool.PDF_READER) }
            if (state.busy) item(key = "busy") {
                Column { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(stringResource(R.string.processing))
                    TextButton(onClick = model::cancel, modifier = Modifier.testTag("reader-cancel")) { Text(stringResource(R.string.cancel)) } }
            }
            state.issue?.let { issue -> item(key = "error") {
                Text(stringResource(when (issue) {
                    PdfReadIssue.PASSWORD -> R.string.reader_password
                    PdfReadIssue.LIMIT -> R.string.reader_limits
                    PdfReadIssue.ACCESS -> R.string.error_file_access
                    PdfReadIssue.MEMORY -> R.string.error_memory
                    PdfReadIssue.TIMEOUT, PdfReadIssue.PROCESS -> R.string.reader_process_error
                    PdfReadIssue.EXPORT -> R.string.pdf_export_error
                    PdfReadIssue.INVALID -> R.string.reader_invalid
                }), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("reader-error"))
            } }
            state.source?.let { source ->
                item(key = "name") { Text(source.name, style = MaterialTheme.typography.titleMedium)
                    Text(Formatter.formatShortFileSize(context, source.bytes)) }
                item(key = "preview") {
                    state.preview?.let { bitmap -> key(source.file.path, state.pageIndex) {
                        var zoom by remember { mutableFloatStateOf(1f) }
                        var offset by remember { mutableStateOf(Offset.Zero) }
                        var viewport by remember { mutableStateOf(IntSize.Zero) }
                        fun changeZoom(value: Float, pan: Offset = Offset.Zero) {
                            zoom = value.coerceIn(1f, 4f)
                            val ratio = bitmap.width.toFloat() / bitmap.height
                            val drawnWidth = minOf(viewport.width.toFloat(), viewport.height * ratio)
                            val drawnHeight = minOf(viewport.height.toFloat(), viewport.width / ratio)
                            val x = maxOf(0f, (drawnWidth * zoom - viewport.width) / 2)
                            val y = maxOf(0f, (drawnHeight * zoom - viewport.height) / 2)
                            offset = Offset((offset.x + pan.x).coerceIn(-x, x), (offset.y + pan.y).coerceIn(-y, y))
                        }
                        val transform = rememberTransformableState { scale, pan, _ -> changeZoom(zoom * scale, pan) }
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val previewHeight = (maxWidth * (bitmap.height.toFloat() / bitmap.width)).coerceAtMost(700.dp)
                            Box(Modifier.fillMaxWidth().height(previewHeight)
                                .onSizeChanged { viewport = it }.clipToBounds().transformable(transform, canPan = { zoom > 1f })) {
                                Image(bitmap.asImageBitmap(), stringResource(R.string.pdf_page, state.pageIndex + 1, source.pages),
                                    Modifier.fillMaxSize().graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = offset.x; translationY = offset.y }
                                        .testTag("reader-preview"))
                            }
                        }
                        Text(stringResource(R.string.reader_zoom_hint))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.reader_zoom))
                            OutlinedButton(onClick = { changeZoom(zoom / 1.5f) }, enabled = zoom > 1f,
                                modifier = Modifier.testTag("reader-zoom-out")) { Text("−") }
                            OutlinedButton(onClick = { changeZoom(zoom * 1.5f) }, enabled = zoom < 4f,
                                modifier = Modifier.testTag("reader-zoom-in")) { Text("+") }
                            TextButton(onClick = { zoom = 1f; offset = Offset.Zero }, modifier = Modifier.testTag("reader-zoom-reset")) {
                                Text(stringResource(R.string.reader_reset_zoom))
                            }
                        }
                    } }
                }
                item(key = "pages") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.pdf_page, state.pageIndex + 1, source.pages), Modifier.testTag("reader-page-number"))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { model.showPage(state.pageIndex - 1) }, enabled = !state.busy && state.pageIndex > 0,
                                modifier = Modifier.testTag("reader-previous")) { Text(stringResource(R.string.pdf_previous)) }
                            OutlinedButton(onClick = { model.showPage(state.pageIndex + 1) }, enabled = !state.busy && state.pageIndex + 1 < source.pages,
                                modifier = Modifier.testTag("reader-next")) { Text(stringResource(R.string.pdf_next)) }
                        }
                        var page by rememberSaveable(source.file.path) { mutableStateOf("") }
                        OutlinedTextField(page, { page = it }, label = { Text(stringResource(R.string.reader_go_page)) }, singleLine = true,
                            colors = studioFieldColors(),
                            enabled = !state.busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth().testTag("reader-page-input"))
                        OutlinedButton(onClick = { focus.clearFocus(); page.toIntOrNull()?.let { model.showPage(it - 1) } },
                            enabled = !state.busy && (page.toIntOrNull() ?: 0) in 1..source.pages,
                            modifier = Modifier.testTag("reader-go")) { Text(stringResource(R.string.reader_go)) }
                    }
                }
                item(key = "copy") {
                    Button(onClick = { copy.launch("copy-${source.name.substringBeforeLast('.').replace('/', '_').replace('\\', '_').filterNot { it.isISOControl() }.take(64)}.pdf") }, enabled = !state.busy,
                        modifier = Modifier.testTag("reader-save-copy")) { Text(stringResource(R.string.reader_save_copy)) }
                    Column(Modifier.testTag("reader-copy-actions")) { OutputActions(source.file, "application/pdf", source.name, !state.busy) }
                }
                item(key = "extract") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.reader_page_image), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.reader_image_loss))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PdfReaderPolicy.outputEdges.forEach { edge -> FilterChip(state.outputEdge == edge, { model.setOutputEdge(edge) },
                                label = { Text(pixels(edge)) }, enabled = !state.busy, modifier = Modifier.testTag("reader-edge-$edge")) }
                        }
                        Button(onClick = model::createPageImage, enabled = !state.busy, modifier = Modifier.testTag("reader-create-image")) {
                            Text(stringResource(R.string.reader_create_image))
                        }
                    }
                }
                state.image?.let { result -> item(key = "image-output") {
                    Button(onClick = { image.launch("page-${result.pageIndex + 1}.png") }, enabled = !state.busy,
                        modifier = Modifier.testTag("reader-save-image")) { Text(stringResource(R.string.reader_save_image)) }
                    Text(Formatter.formatShortFileSize(context, result.file.length()))
                    Column(Modifier.testTag("reader-image-actions")) {
                        OutputActions(result.file, "image/png", "page-${result.pageIndex + 1}.png", !state.busy,
                            presentations = presentations, onAddToPresentation = onAddToPresentation, onAddToPhotoPdf = onAddToPhotoPdf)
                    }
                } }
            }
            if (state.saved) item(key = "saved") { Text(stringResource(R.string.reader_saved), Modifier.testTag("reader-saved")) }
        }
        }
    }
}

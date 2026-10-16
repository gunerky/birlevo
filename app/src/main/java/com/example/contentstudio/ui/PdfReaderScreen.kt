package com.example.contentstudio.ui

import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.contentstudio.R
import com.example.contentstudio.data.StudioProject
import com.example.contentstudio.media.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PdfReaderScreen(
    onClose: () -> Unit,
    presentations: List<StudioProject>,
    onAddToPresentation: suspend (Uri, String?) -> Boolean,
    onAddToPhotoPdf: suspend (Uri) -> Boolean,
    model: PdfReaderViewModel = viewModel(),
    initialPanel: String? = null,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val pick =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
            it?.let(model::load)
        }
    var expectedCopy by rememberSaveable { mutableStateOf<String?>(null) }
    var expectedCopyOriginal by rememberSaveable { mutableStateOf<String?>(null) }
    val copy =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/pdf")
        ) {
            it?.let { uri -> model.export(uri, false, expectedCopy, expectedCopyOriginal) }
            expectedCopy = null
            expectedCopyOriginal = null
        }
    var expectedImage by rememberSaveable { mutableStateOf<String?>(null) }
    var expectedOriginal by rememberSaveable { mutableStateOf<String?>(null) }
    val image =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument(
                state.image?.format?.mime ?: state.imageFormat.mime
            )
        ) {
            it?.let { uri -> model.export(uri, true, expectedImage, expectedOriginal) }
            expectedImage = null
            expectedOriginal = null
        }
    fun close() {
        focus.clearFocus()
        model.cancel()
        onClose()
    }
    ToolWorkspace(
        stringResource(R.string.read_pdf),
        "reader",
        ::close,
        initialPanel = initialPanel,
        resultKey = state.image?.file?.path,
        panels =
            if (state.source != null)
                listOf(
                    WorkspacePanel("pages", stringResource(R.string.pdf_pages)),
                    WorkspacePanel("image", stringResource(R.string.reader_page_image)),
                    WorkspacePanel("copy", stringResource(R.string.reader_save_copy)),
                )
            else emptyList(),
        actions = { panel ->
            if (state.busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(R.string.processing))
                TextButton(onClick = model::cancel, modifier = Modifier.testTag("reader-cancel")) {
                    Text(stringResource(R.string.cancel))
                }
            } else
                state.source?.let { source ->
                    val result = state.image
                    when {
                        panel == "image" && result == null ->
                            Button(
                                onClick = model::createPageImage,
                                modifier = Modifier.testTag("reader-create-image"),
                            ) {
                                Text(
                                    stringResource(
                                        R.string.reader_create_image,
                                        state.imageFormat.name,
                                    )
                                )
                            }
                        panel == "copy" || result == null -> {
                            Button(
                                onClick = {
                                    expectedCopy = source.file.path
                                    expectedCopyOriginal = source.originalUri.toString()
                                    copy.launch(
                                        "copy-" +
                                            source.name
                                                .substringBeforeLast('.')
                                                .replace('/', '_')
                                                .replace(92.toChar(), '_')
                                                .filterNot { it.isISOControl() }
                                                .take(64) +
                                            ".pdf"
                                    )
                                },
                                modifier = Modifier.testTag("reader-save-copy"),
                            ) {
                                Text(stringResource(R.string.reader_save_copy))
                            }
                            Column(Modifier.testTag("reader-copy-actions")) {
                                OutputActions(source.file, "application/pdf", source.name, true)
                            }
                        }
                        else -> {
                            Button(
                                onClick = {
                                    expectedImage = result.file.path
                                    expectedOriginal = source.originalUri.toString()
                                    image.launch(
                                        "page-${result.pageIndex + 1}.${result.format.extension}"
                                    )
                                },
                                modifier = Modifier.testTag("reader-save-image"),
                            ) {
                                Text(stringResource(R.string.reader_save_image, result.format.name))
                            }
                            Text(Formatter.formatShortFileSize(context, result.file.length()))
                            Column(Modifier.testTag("reader-image-actions")) {
                                OutputActions(
                                    result.file,
                                    result.format.mime,
                                    "page-${result.pageIndex + 1}.${result.format.extension}",
                                    true,
                                    presentations = presentations,
                                    onAddToPresentation = onAddToPresentation,
                                    onAddToPhotoPdf = onAddToPhotoPdf,
                                )
                            }
                        }
                    }
                }
        },
        panelContent = { panel ->
            state.source?.let { source ->
                when (panel) {
                    "pages" -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                stringResource(
                                    R.string.pdf_page,
                                    state.pageIndex + 1,
                                    source.pages,
                                ),
                                Modifier.testTag("reader-panel-page-number"),
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { model.showPage(state.pageIndex - 1) },
                                    enabled = !state.busy && state.pageIndex > 0,
                                    modifier = Modifier.testTag("reader-panel-previous"),
                                ) {
                                    Text(stringResource(R.string.pdf_previous))
                                }
                                OutlinedButton(
                                    onClick = { model.showPage(state.pageIndex + 1) },
                                    enabled = !state.busy && state.pageIndex + 1 < source.pages,
                                    modifier = Modifier.testTag("reader-panel-next"),
                                ) {
                                    Text(stringResource(R.string.pdf_next))
                                }
                            }
                            var page by rememberSaveable(source.file.path) { mutableStateOf("") }
                            OutlinedTextField(
                                page,
                                { page = it },
                                label = { Text(stringResource(R.string.reader_go_page)) },
                                singleLine = true,
                                colors = studioFieldColors(),
                                enabled = !state.busy,
                                keyboardOptions =
                                    KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth().testTag("reader-page-input"),
                            )
                            OutlinedButton(
                                onClick = {
                                    focus.clearFocus()
                                    page.toIntOrNull()?.let { model.showPage(it - 1) }
                                },
                                enabled =
                                    !state.busy && (page.toIntOrNull() ?: 0) in 1..source.pages,
                                modifier = Modifier.testTag("reader-go"),
                            ) {
                                Text(stringResource(R.string.reader_go))
                            }
                        }
                    }
                    "image" -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                stringResource(R.string.reader_page_image),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(stringResource(R.string.reader_image_loss))
                            Text(stringResource(R.string.reader_encoding_hint))
                            Text(stringResource(R.string.output_format))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PdfReaderPolicy.imageFormats.forEach { format ->
                                    FilterChip(
                                        state.imageFormat == format,
                                        { model.setImageOptions(format = format) },
                                        label = { Text(format.name) },
                                        enabled = !state.busy,
                                        modifier = Modifier.testTag("reader-format-${format.name}"),
                                    )
                                }
                            }
                            if (state.imageFormat.usesQuality) {
                                val qualityLabel = stringResource(R.string.image_quality)
                                Text("${stringResource(R.string.image_quality)}: ${state.quality}")
                                Slider(
                                    state.quality.toFloat(),
                                    { model.setImageOptions(quality = it.roundToInt()) },
                                    valueRange = 40f..95f,
                                    enabled = !state.busy,
                                    modifier =
                                        Modifier.testTag("reader-quality").accessibleSlider(qualityLabel, state.quality.toString()),
                                )
                            } else
                                Text(
                                    stringResource(
                                        if (state.imageFormat == ImageOutputFormat.BMP)
                                            R.string.bmp_quality_hint
                                        else R.string.png_quality_hint
                                    )
                                )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PdfReaderPolicy.outputEdges.forEach { edge ->
                                    FilterChip(
                                        state.outputEdge == edge,
                                        { model.setOutputEdge(edge) },
                                        label = { Text(pixels(edge)) },
                                        enabled = !state.busy,
                                        modifier = Modifier.testTag("reader-edge-$edge"),
                                    )
                                }
                            }
                        }
                    }
                    "copy" -> Text(source.name, style = MaterialTheme.typography.titleMedium)
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().testTag("reader-list"),
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "source") {
                if (state.source == null) {
                    ToolImportCard(
                        StudioGlyph.SLIDES,
                        stringResource(R.string.reader_hint),
                        stringResource(R.string.choose_pdf),
                        stringResource(R.string.reader_limits),
                        !state.busy,
                        "choose-pdf",
                    ) {
                        pick.launch(arrayOf("application/pdf"))
                    }
                }
                state.source?.let { source ->
                    SourceHeader(
                        source.name,
                        Formatter.formatShortFileSize(context, source.bytes),
                        !state.busy,
                        "choose-pdf",
                    ) {
                        pick.launch(arrayOf("application/pdf"))
                    }
                }
            }
            state.issue?.let { issue ->
                item(key = "error") {
                    Text(
                        stringResource(
                            when (issue) {
                                PdfReadIssue.PASSWORD -> R.string.reader_password
                                PdfReadIssue.LIMIT -> R.string.reader_limits
                                PdfReadIssue.ACCESS -> R.string.error_file_access
                                PdfReadIssue.MEMORY -> R.string.error_memory
                                PdfReadIssue.STORAGE -> R.string.storage_capacity_error
                                PdfReadIssue.TIMEOUT,
                                PdfReadIssue.PROCESS -> R.string.reader_process_error
                                PdfReadIssue.EXPORT -> R.string.pdf_export_error
                                PdfReadIssue.INVALID -> R.string.reader_invalid
                            }
                        ),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("reader-error"),
                    )
                }
            }
            state.source?.let { source ->
                item(key = "preview") {
                    state.preview?.let { bitmap ->
                        key(source.file.path, state.pageIndex) {
                            var zoom by remember { mutableFloatStateOf(1f) }
                            var offset by remember { mutableStateOf(Offset.Zero) }
                            var viewport by remember { mutableStateOf(IntSize.Zero) }
                            fun changeZoom(value: Float, pan: Offset = Offset.Zero) {
                                zoom = value.coerceIn(1f, 4f)
                                val ratio = bitmap.width.toFloat() / bitmap.height
                                val drawnWidth =
                                    minOf(viewport.width.toFloat(), viewport.height * ratio)
                                val drawnHeight =
                                    minOf(viewport.height.toFloat(), viewport.width / ratio)
                                val x = maxOf(0f, (drawnWidth * zoom - viewport.width) / 2)
                                val y = maxOf(0f, (drawnHeight * zoom - viewport.height) / 2)
                                offset =
                                    Offset(
                                        (offset.x + pan.x).coerceIn(-x, x),
                                        (offset.y + pan.y).coerceIn(-y, y),
                                    )
                            }
                            val transform = rememberTransformableState { scale, pan, _ ->
                                changeZoom(zoom * scale, pan)
                            }
                            BoxWithConstraints(Modifier.fillMaxWidth()) {
                                val previewHeight =
                                    (maxWidth * (bitmap.height.toFloat() / bitmap.width))
                                        .coerceAtMost(700.dp)
                                Box(
                                    Modifier.fillMaxWidth()
                                        .height(previewHeight)
                                        .onSizeChanged { viewport = it }
                                        .clipToBounds()
                                        .transformable(transform, canPan = { zoom > 1f })
                                ) {
                                    Image(
                                        bitmap.asImageBitmap(),
                                        stringResource(
                                            R.string.pdf_page,
                                            state.pageIndex + 1,
                                            source.pages,
                                        ),
                                        Modifier.fillMaxSize()
                                            .graphicsLayer {
                                                scaleX = zoom
                                                scaleY = zoom
                                                translationX = offset.x
                                                translationY = offset.y
                                            }
                                            .testTag("reader-preview"),
                                    )
                                }
                            }
                            Text(stringResource(R.string.reader_zoom_hint))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(R.string.reader_zoom))
                                OutlinedButton(
                                    onClick = { changeZoom(zoom / 1.5f) },
                                    enabled = zoom > 1f,
                                    modifier = Modifier.testTag("reader-zoom-out"),
                                ) {
                                    Text("−")
                                }
                                OutlinedButton(
                                    onClick = { changeZoom(zoom * 1.5f) },
                                    enabled = zoom < 4f,
                                    modifier = Modifier.testTag("reader-zoom-in"),
                                ) {
                                    Text("+")
                                }
                                TextButton(
                                    onClick = {
                                        zoom = 1f
                                        offset = Offset.Zero
                                    },
                                    modifier = Modifier.testTag("reader-zoom-reset"),
                                ) {
                                    Text(stringResource(R.string.reader_reset_zoom))
                                }
                            }
                        }
                    }
                }
                item(key = "page-navigation") {
                    Text(
                        stringResource(R.string.pdf_page, state.pageIndex + 1, source.pages),
                        Modifier.testTag("reader-page-number"),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { model.showPage(state.pageIndex - 1) },
                            enabled = !state.busy && state.pageIndex > 0,
                            modifier = Modifier.testTag("reader-previous"),
                        ) {
                            Text(stringResource(R.string.pdf_previous))
                        }
                        OutlinedButton(
                            onClick = { model.showPage(state.pageIndex + 1) },
                            enabled = !state.busy && state.pageIndex + 1 < source.pages,
                            modifier = Modifier.testTag("reader-next"),
                        ) {
                            Text(stringResource(R.string.pdf_next))
                        }
                    }
                }
            }
            if (state.saved)
                item(key = "saved") {
                    Text(stringResource(R.string.reader_saved), Modifier.testTag("reader-saved"))
                }
            item(key = "help") { WorkspaceHelp { SupportedFilesButton(SupportedTool.PDF_READER) } }
        }
    }
}

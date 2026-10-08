package com.example.contentstudio.ui

import android.graphics.Bitmap
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.contentstudio.R
import com.example.contentstudio.media.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PhotoPdfScreen(onClose: () -> Unit, model: PhotoPdfViewModel = viewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    val sessionStatus by model.sessionStatus.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(model, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) model.flushSession()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val context = LocalContext.current
    val picker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenMultipleDocuments(),
            model::add,
        )
    var pickerResult by rememberSaveable { mutableStateOf<String?>(null) }
    var pickerOriginals by
        rememberSaveable(
            stateSaver = listSaver<List<String>, String>(save = { it }, restore = { it.toList() })
        ) {
            mutableStateOf<List<String>>(emptyList())
        }
    val save =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/pdf")
        ) { uri ->
            uri?.let { model.export(it, pickerResult, pickerOriginals) }
            pickerResult = null
            pickerOriginals = emptyList()
        }
    fun close() {
        model.leave()
        onClose()
    }
    var selectedPhoto by rememberSaveable { mutableStateOf<String?>(null) }
    var compareOriginal by rememberSaveable(state.result?.file?.path) { mutableStateOf(false) }
    val selected = state.pages.firstOrNull { it.id == selectedPhoto } ?: state.pages.firstOrNull()
    ToolWorkspace(
        stringResource(R.string.photos_to_pdf),
        "photo-pdf",
        ::close,
        resultKey = state.result?.file?.path,
        panels =
            if (state.pages.isNotEmpty())
                listOf(WorkspacePanel("output", stringResource(R.string.output_format)))
            else emptyList(),
        actions = {
            if (state.busy) {
                state.progress?.let {
                    LinearProgressIndicator(
                        progress = { it / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } ?: CircularProgressIndicator()
                Text(stringResource(R.string.processing))
                TextButton(
                    onClick = model::cancel,
                    modifier = Modifier.testTag("cancel-photo-pdf"),
                ) {
                    Text(stringResource(R.string.cancel))
                }
            } else
                state.result?.let { result ->
                    Button(
                        onClick = {
                            pickerResult = result.file.name
                            pickerOriginals = state.pages.map { it.sourceUri.toString() }
                            save.launch("photos.pdf")
                        },
                        enabled = !state.busy,
                        modifier = Modifier.testTag("save-photo-pdf"),
                    ) {
                        Text(stringResource(R.string.save_pdf))
                    }
                    OutputActions(result.file, "application/pdf", "photos.pdf", !state.busy)
                }
                    ?: run {
                        Button(
                            onClick = model::generate,
                            enabled = !state.busy && state.pages.isNotEmpty(),
                            modifier = Modifier.testTag("generate-photo-pdf"),
                        ) {
                            Text(stringResource(R.string.create_result))
                        }
                    }
        },
        panelContent = {
            Text(stringResource(R.string.max_edge))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(2048, 4096).forEach { edge ->
                    FilterChip(
                        state.maxEdge == edge,
                        onClick = { model.setMaxEdge(edge) },
                        enabled = !state.busy,
                        label = { Text(pixels(edge)) },
                        modifier = Modifier.testTag("photo-pdf-edge-$edge"),
                    )
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().testTag("photo-pdf-list"),
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "source") {
                if (state.pages.isEmpty()) {
                    ToolImportCard(
                        StudioGlyph.SLIDES,
                        stringResource(R.string.photo_pdf_hint),
                        stringResource(R.string.choose_images),
                        stringResource(R.string.photo_pdf_session),
                        !state.busy && state.pages.size < PhotoPdf.MAX_PAGES,
                        "choose-photo-pdf",
                    ) {
                        picker.launch(ImageFormats.inputMimes)
                    }
                } else
                    SourceHeader(
                        stringResource(R.string.photos_to_pdf),
                        stringResource(
                            R.string.pdf_page,
                            state.pages.indexOf(selected) + 1,
                            state.pages.size,
                        ),
                        !state.busy && state.pages.size < PhotoPdf.MAX_PAGES,
                        "choose-photo-pdf",
                        stringResource(R.string.choose_images),
                    ) {
                        picker.launch(ImageFormats.inputMimes)
                    }
                ProcessingStatusHint(state.workStatus, "photo")
                if (
                    state.pages.any {
                        it.file.extension.lowercase(java.util.Locale.ROOT) in
                            setOf("heic", "heif", "avif")
                    }
                )
                    Text(
                        stringResource(R.string.files_modern_images),
                        style = MaterialTheme.typography.bodySmall,
                    )
                state.message?.let { message ->
                    Text(
                        stringResource(
                            state.issue?.let(::importIssueText)
                                ?: when (message) {
                                    PhotoPdfMessage.IMPORT_ERROR -> R.string.photo_pdf_import_error
                                    PhotoPdfMessage.GENERATE_ERROR ->
                                        R.string.photo_pdf_generate_error
                                    PhotoPdfMessage.EXPORT_ERROR -> R.string.pdf_export_error
                                    PhotoPdfMessage.EXPORTED -> R.string.pdf_saved
                                }
                        ),
                        modifier = Modifier.testTag("photo-pdf-message"),
                        color =
                            if (message == PhotoPdfMessage.EXPORTED)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                    )
                }
            }
            if (state.result != null)
                item(key = "comparison") {
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
                }
            if (state.result != null && !compareOriginal)
                item(key = "result") {
                    state.result?.let { result ->
                        state.preview?.let { bitmap ->
                            Image(
                                bitmap.asImageBitmap(),
                                contentDescription =
                                    stringResource(
                                        R.string.pdf_page,
                                        state.pageIndex + 1,
                                        result.pages,
                                    ),
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .aspectRatio(bitmap.width.toFloat() / bitmap.height)
                                        .testTag("photo-pdf-preview"),
                            )
                        }
                        Text(
                            stringResource(R.string.pdf_page, state.pageIndex + 1, result.pages),
                            modifier = Modifier.testTag("photo-pdf-page-number"),
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { model.showPage(state.pageIndex - 1) },
                                enabled = !state.busy && state.pageIndex > 0,
                                modifier = Modifier.testTag("photo-pdf-previous"),
                            ) {
                                Text(stringResource(R.string.pdf_previous))
                            }
                            OutlinedButton(
                                onClick = { model.showPage(state.pageIndex + 1) },
                                enabled = !state.busy && state.pageIndex + 1 < result.pages,
                                modifier = Modifier.testTag("photo-pdf-next"),
                            ) {
                                Text(stringResource(R.string.pdf_next))
                            }
                        }
                        Text(Formatter.formatShortFileSize(context, result.bytes))
                    }
                }
            else
                selected?.let { page ->
                    item(key = "selected-preview") { PhotoThumbnail(page, 1024, 240.dp) }
                }
            itemsIndexed(
                state.pages,
                key = { _, page -> page.id },
                contentType = { _, _ -> "photo" },
            ) { index, page ->
                Card(
                    onClick = {
                        selectedPhoto = page.id
                        compareOriginal = true
                    },
                    modifier = Modifier.fillMaxWidth().testTag("photo-page-${page.id}"),
                ) {
                    Column(
                        Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            PhotoThumbnail(page)
                            Text(
                                stringResource(R.string.pdf_page, index + 1, state.pages.size) +
                                    " · " +
                                    page.name,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (page.id == selected?.id) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { model.move(page.id, -1) },
                                    enabled = !state.busy && index > 0,
                                    modifier = Modifier.testTag("photo-up-${page.id}"),
                                ) {
                                    Text(stringResource(R.string.move_up))
                                }
                                OutlinedButton(
                                    onClick = { model.move(page.id, 1) },
                                    enabled = !state.busy && index + 1 < state.pages.size,
                                    modifier = Modifier.testTag("photo-down-${page.id}"),
                                ) {
                                    Text(stringResource(R.string.move_down))
                                }
                                TextButton(
                                    onClick = { model.remove(page.id) },
                                    enabled = !state.busy,
                                    modifier = Modifier.testTag("photo-remove-${page.id}"),
                                ) {
                                    Text(stringResource(R.string.remove_image))
                                }
                            }
                        }
                    }
                }
            }
            item(key = "help") {
                WorkspaceHelp {
                    SupportedFilesButton(SupportedTool.PHOTO_PDF)
                    MediaSessionControls(
                        sessionStatus,
                        model.sessionRestoreFailed,
                        !state.busy,
                        "photo",
                        model::retrySession,
                        model::reset,
                    )
                    Text(
                        stringResource(R.string.photo_pdf_session),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun PhotoThumbnail(
    page: PhotoPage,
    edge: Int = 128,
    size: androidx.compose.ui.unit.Dp = 64.dp,
) {
    val context = LocalContext.current
    val bitmap by
        produceState<Bitmap?>(null, page.id, edge) {
            var decoded: Bitmap? = null
            var delivered = false
            try {
                withContext(Dispatchers.IO) {
                    decoded = SlideImages.decode(context, page.uri, edge)
                }
                value = decoded
                delivered = true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {} catch (_: OutOfMemoryError) {} finally {
                if (!delivered) decoded?.recycle()
            }
        }
    bitmap?.let {
        Image(
            it.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = if (edge > 128) Modifier.fillMaxWidth().height(size) else Modifier.size(size),
        )
    }
        ?: Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
            StudioIcon(StudioGlyph.IMAGE, MaterialTheme.colorScheme.tertiary)
        }
}

package com.example.contentstudio.ui

import android.graphics.Bitmap
import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.contentstudio.R
import com.example.contentstudio.media.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PhotoPdfScreen(onClose: () -> Unit, model: PhotoPdfViewModel = viewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    val sessionStatus by model.sessionStatus.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(model, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) model.flushSession() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments(), model::add)
    var pickerResult by rememberSaveable { mutableStateOf<String?>(null) }
    var pickerOriginals by rememberSaveable(stateSaver = listSaver<List<String>, String>(save = { it }, restore = { it.toList() })) {
        mutableStateOf<List<String>>(emptyList())
    }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri?.let { model.export(it, pickerResult, pickerOriginals) }; pickerResult = null; pickerOriginals = emptyList()
    }
    fun close() { model.leave(); onClose() }
    BackHandler { close() }
    Scaffold(contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout), topBar = {
        TopAppBar(windowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top), title = { Text(stringResource(R.string.photos_to_pdf), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = { TextButton(onClick = ::close, modifier = Modifier.testTag("photo-pdf-back")) { Text(stringResource(R.string.back)) } })
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max = 900.dp).fillMaxSize().testTag("photo-pdf-list"),
                contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item(key = "import") {
                    ToolImportCard(StudioGlyph.SLIDES, stringResource(R.string.photo_pdf_hint),
                        stringResource(R.string.choose_images), stringResource(R.string.photo_pdf_session),
                        !state.busy && state.pages.size < PhotoPdf.MAX_PAGES, "choose-photo-pdf") { picker.launch(ImageFormats.inputMimes) }
                    SupportedFilesButton(SupportedTool.PHOTO_PDF)
                    MediaSessionControls(sessionStatus, model.sessionRestoreFailed, !state.busy, "photo", model::retrySession, model::reset)
                    ProcessingStatusHint(state.workStatus, "photo")
                }
                itemsIndexed(state.pages, key = { _, page -> page.id }, contentType = { _, _ -> "photo" }) { index, page ->
                    ElevatedCard(Modifier.fillMaxWidth().testTag("photo-page-${page.id}"), shape = RoundedCornerShape(24.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                PhotoThumbnail(page)
                                Column(Modifier.weight(1f)) {
                                    Text(stringResource(R.string.pdf_page, index + 1, state.pages.size))
                                    Text(page.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { model.move(page.id, -1) }, enabled = !state.busy && index > 0,
                                    modifier = Modifier.testTag("photo-up-${page.id}")) { Text(stringResource(R.string.move_up)) }
                                OutlinedButton(onClick = { model.move(page.id, 1) }, enabled = !state.busy && index + 1 < state.pages.size,
                                    modifier = Modifier.testTag("photo-down-${page.id}")) { Text(stringResource(R.string.move_down)) }
                                TextButton(onClick = { model.remove(page.id) }, enabled = !state.busy,
                                    modifier = Modifier.testTag("photo-remove-${page.id}")) { Text(stringResource(R.string.remove_image)) }
                            }
                        }
                    }
                }
                item(key = "generate") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (state.pages.any { it.file.extension in listOf("heic", "heif", "avif") })
                            Text(stringResource(R.string.files_modern_images), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.max_edge))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(2048, 4096).forEach { edge ->
                                FilterChip(state.maxEdge == edge, onClick = { model.setMaxEdge(edge) }, enabled = !state.busy,
                                    label = { Text(pixels(edge)) }, modifier = Modifier.testTag("photo-pdf-edge-$edge"))
                            }
                        }
                        Button(onClick = model::generate, enabled = !state.busy && state.pages.isNotEmpty(),
                            modifier = Modifier.testTag("generate-photo-pdf")) { Text(stringResource(R.string.create_pdf)) }
                        if (state.busy) {
                            state.progress?.let { LinearProgressIndicator(progress = { it / 100f }, modifier = Modifier.fillMaxWidth()) }
                                ?: CircularProgressIndicator()
                            Text(stringResource(R.string.processing))
                            TextButton(onClick = model::cancel, modifier = Modifier.testTag("cancel-photo-pdf")) { Text(stringResource(R.string.cancel)) }
                        }
                        state.message?.let { message -> Text(stringResource(state.issue?.let(::importIssueText) ?: when (message) {
                            PhotoPdfMessage.IMPORT_ERROR -> R.string.photo_pdf_import_error
                            PhotoPdfMessage.GENERATE_ERROR -> R.string.photo_pdf_generate_error
                            PhotoPdfMessage.EXPORT_ERROR -> R.string.pdf_export_error
                            PhotoPdfMessage.EXPORTED -> R.string.pdf_saved
                        }), modifier = Modifier.testTag("photo-pdf-message"), color = if (message == PhotoPdfMessage.EXPORTED)
                            MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
                    }
                }
                state.result?.let { result -> item(key = "output") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        state.preview?.let { bitmap -> Image(bitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.pdf_page, state.pageIndex + 1, result.pages),
                            modifier = Modifier.fillMaxWidth().aspectRatio(bitmap.width.toFloat() / bitmap.height).testTag("photo-pdf-preview")) }
                        Text(stringResource(R.string.pdf_page, state.pageIndex + 1, result.pages), modifier = Modifier.testTag("photo-pdf-page-number"))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { model.showPage(state.pageIndex - 1) }, enabled = !state.busy && state.pageIndex > 0,
                                modifier = Modifier.testTag("photo-pdf-previous")) { Text(stringResource(R.string.pdf_previous)) }
                            OutlinedButton(onClick = { model.showPage(state.pageIndex + 1) }, enabled = !state.busy && state.pageIndex + 1 < result.pages,
                                modifier = Modifier.testTag("photo-pdf-next")) { Text(stringResource(R.string.pdf_next)) }
                        }
                        Text(Formatter.formatShortFileSize(context, result.bytes))
                        Button(onClick = {
                            pickerResult = result.file.name; pickerOriginals = state.pages.map { it.sourceUri.toString() }
                            save.launch("photos.pdf")
                        }, enabled = !state.busy,
                            modifier = Modifier.testTag("save-photo-pdf")) { Text(stringResource(R.string.save_pdf)) }
                        OutputActions(result.file, "application/pdf", "photos.pdf", !state.busy)
                    }
                } }
            }
        }
    }
}

@Composable
private fun PhotoThumbnail(page: PhotoPage) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, page.id) {
        var decoded: Bitmap? = null
        var delivered = false
        try {
            withContext(Dispatchers.IO) { decoded = SlideImages.decode(context, page.uri, 128) }
            value = decoded
            delivered = true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { }
        catch (_: OutOfMemoryError) { }
        finally { if (!delivered) decoded?.recycle() }
    }
    bitmap?.let { Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit,
        modifier = Modifier.size(64.dp)) } ?: Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
        StudioIcon(StudioGlyph.IMAGE, MaterialTheme.colorScheme.tertiary)
    }
}

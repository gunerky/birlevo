package com.example.contentstudio.ui

import android.graphics.Bitmap
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.contentstudio.R
import com.example.contentstudio.media.CompressionMessage
import com.example.contentstudio.media.ImageCompressionViewModel
import com.example.contentstudio.media.CompressionBenefit
import com.example.contentstudio.media.compressionBenefit
import com.example.contentstudio.media.ImageOutputFormat
import com.example.contentstudio.media.ImageFormats
import java.text.NumberFormat
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ImageCompressionScreen(onClose: () -> Unit, model: ImageCompressionViewModel = viewModel(),
    presentations: List<com.example.contentstudio.data.StudioProject> = emptyList(),
    onAddToPresentation: (suspend (android.net.Uri, String?) -> Boolean)? = null,
    onAddToPhotoPdf: (suspend (android.net.Uri) -> Boolean)? = null) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    fun close() { model.reset(); onClose() }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(model::load)
    }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(state.format.mime)) { uri ->
        uri?.let(model::export)
    }
    BackHandler { close() }
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.compress_image), maxLines = 1,
            overflow = TextOverflow.Ellipsis) }, navigationIcon = {
            TextButton(onClick = ::close, modifier = Modifier.testTag("compression-back")) {
                Text(stringResource(R.string.back))
            }
        })
    }) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            val wide = maxWidth >= 700.dp
            Column(Modifier.widthIn(max = 1100.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ToolImportCard(StudioGlyph.IMAGE, stringResource(R.string.compression_hint),
                    stringResource(R.string.choose_image), stringResource(when (state.format) {
                        ImageOutputFormat.JPEG -> R.string.image_losses
                        ImageOutputFormat.PNG -> R.string.png_losses
                        ImageOutputFormat.WEBP -> R.string.webp_losses
                    }), !state.busy, "choose-image") { pick.launch(ImageFormats.inputMimes) }
                if (state.format == ImageOutputFormat.PNG) Text(stringResource(R.string.image_static_output))
                SupportedFilesButton(com.example.contentstudio.media.SupportedTool.IMAGE)
                state.source?.let { source ->
                    Text(source.name, style = MaterialTheme.typography.titleMedium)
                    if (com.example.contentstudio.media.ModernImages.isModern(source.mime))
                        Text(stringResource(R.string.files_modern_images), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("modern-image-notice"))
                    val beforeLabel = stringResource(R.string.original_size)
                    val afterLabel = stringResource(R.string.output_size)
                    if (wide) Row(Modifier.testTag("compression-wide"), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Preview(source.preview, beforeLabel, source.bytes, source.width, source.height, Modifier.weight(1f))
                        state.result?.let { result ->
                            Preview(result.preview, afterLabel, result.bytes, result.width, result.height, Modifier.weight(1f))
                        }
                    } else Column(Modifier.testTag("compression-compact"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Preview(source.preview, beforeLabel, source.bytes, source.width, source.height)
                        state.result?.let { result -> Preview(result.preview, afterLabel, result.bytes, result.width, result.height) }
                    }
                    Text(stringResource(R.string.output_format))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ImageOutputFormat.entries.forEach { format ->
                            FilterChip(state.format == format, onClick = { model.setOptions(format = format) },
                                enabled = !state.busy, label = { Text(format.name) },
                                modifier = Modifier.testTag("image-format-${format.name}"))
                        }
                    }
                    if (state.format.usesQuality) {
                    Text("${stringResource(R.string.image_quality)}: ${state.quality}")
                    Slider(value = state.quality.toFloat(), onValueChange = { model.setOptions(quality = it.roundToInt()) },
                        enabled = !state.busy, valueRange = 40f..95f,
                        modifier = Modifier.testTag("image-quality"))
                    } else Text(stringResource(R.string.png_quality_hint))
                    Text(stringResource(R.string.max_edge))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1280, 1920, 2560).forEach { edge ->
                            FilterChip(selected = state.maxEdge == edge, onClick = { model.setOptions(maxEdge = edge) },
                                enabled = !state.busy, label = { Text(pixels(edge)) }, modifier = Modifier.testTag("edge-$edge"))
                        }
                    }
                    Button(onClick = model::preview, enabled = !state.busy, modifier = Modifier.testTag("image-preview")) {
                        Text(stringResource(R.string.image_preview))
                    }
                    state.result?.let { result ->
                        val benefit = compressionBenefit(source.bytes, result.bytes)
                        val label = if (benefit == CompressionBenefit.LARGER) R.string.size_increase else R.string.size_change
                        val change = NumberFormat.getPercentInstance(configuration.locales[0])
                            .format(kotlin.math.abs(1.0 - result.bytes.toDouble() / source.bytes))
                        Text(if (benefit == CompressionBenefit.SAME_SIZE) stringResource(R.string.size_unchanged)
                            else "${stringResource(label)}: $change", modifier = Modifier.testTag("size-change"))
                        if (result.bytes >= source.bytes) Text(stringResource(if (result.format == ImageOutputFormat.PNG)
                            R.string.png_quality_hint else R.string.image_not_smaller))
                        fun saveCopy() {
                            val base = source.name.substringBeforeLast('.').replace('/', '_').replace('\\', '_')
                                .filterNot { it.isISOControl() }.take(64).ifBlank { "image" }
                            val extension = result.format.extension
                            save.launch(if (benefit == CompressionBenefit.SMALLER) "$base-compressed.$extension" else "$base-converted.$extension")
                        }
                        CompressionExportActions(result.file.path, benefit, !state.busy,
                            stringResource(R.string.image_export), "export-image", onSave = ::saveCopy,
                            onSaveAnyway = { if (model.confirmNonSmallerCopy(result.file)) saveCopy() })
                        OutputActions(result.file, result.format.mime, source.name.substringBeforeLast('.') +
                            (if (benefit == CompressionBenefit.SMALLER) "-compressed." else "-converted.") + result.format.extension,
                            !state.busy, source.bytes, presentations, onAddToPresentation?.let { attach ->
                                { uri, target -> attach(uri, target).also { if (it) model.reset() } }
                            }, onAddToPhotoPdf?.let { attach -> { uri -> attach(uri).also { if (it) model.reset() } } })
                    }
                }
                if (state.busy) {
                    CircularProgressIndicator(Modifier.testTag("compression-busy"))
                    Text(stringResource(R.string.processing))
                    TextButton(onClick = model::cancel, modifier = Modifier.testTag("cancel-compression")) {
                        Text(stringResource(R.string.cancel))
                    }
                }
                state.message?.let { message ->
                    Text(stringResource(state.issue?.let(::importIssueText) ?: when (message) {
                        CompressionMessage.IMAGE_ERROR -> R.string.image_error
                        CompressionMessage.EXPORT_ERROR -> R.string.image_export_error
                        CompressionMessage.EXPORTED -> R.string.export_complete
                    }), color = if (message == CompressionMessage.EXPORTED) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error, modifier = Modifier.testTag("compression-message"))
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun Preview(bitmap: Bitmap, label: String, bytes: Long, width: Int, height: Int,
    modifier: Modifier = Modifier) {
    val context = LocalContext.current
    ElevatedCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("$label · ${Formatter.formatShortFileSize(context, bytes)}", style = MaterialTheme.typography.titleSmall)
            Text("${pixels(width)} × ${pixels(height)}", style = MaterialTheme.typography.bodySmall)
            Image(bitmap.asImageBitmap(), contentDescription = label, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().height(180.dp))
        }
    }
}

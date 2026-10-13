package com.example.contentstudio.ui

import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.contentstudio.R
import com.example.contentstudio.data.*
import com.example.contentstudio.media.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PresentationImageScreen(
    project: StudioProject,
    model: PresentationImageViewModel,
    onClose: () -> Unit,
    onToImage: suspend (Uri) -> Boolean,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var expected by rememberSaveable { mutableStateOf<String?>(null) }
    val save =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            response ->
            if (response.resultCode == android.app.Activity.RESULT_OK)
                response.data?.data?.let { model.export(it, expected.orEmpty()) }
            expected = null
        }
    val scope = rememberCoroutineScope()
    var transferring by remember { mutableStateOf(false) }
    var transferFailed by remember { mutableStateOf(false) }
    LaunchedEffect(project) { model.prepare(project) }
    ToolWorkspace(
        stringResource(R.string.present_images),
        "slide-image",
        {
            model.leave()
            onClose()
        },
        panels = listOf(WorkspacePanel("output", stringResource(R.string.workspace_output))),
        resultKey = state.result?.file?.path,
        actions = {
            if (state.busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                TextButton(model::cancel, Modifier.testTag("slide-image-cancel")) {
                    Text(stringResource(R.string.cancel))
                }
            } else if (state.result != null) {
                val result = state.result!!
                Button(
                    {
                        expected = result.file.path
                        save.launch(
                            android.content
                                .Intent(android.content.Intent.ACTION_CREATE_DOCUMENT)
                                .apply {
                                    addCategory(android.content.Intent.CATEGORY_OPENABLE)
                                    type = result.format.mime
                                    putExtra(
                                        android.content.Intent.EXTRA_TITLE,
                                        "slide-${(state.project?.slides?.indexOfFirst { it.id == state.slideId } ?: 0) + 1}.${result.format.extension}",
                                    )
                                }
                        )
                    },
                    enabled = !transferring,
                    modifier = Modifier.testTag("slide-image-save"),
                ) {
                    Text(stringResource(R.string.save))
                }
                OutputActions(
                    result.file,
                    result.format.mime,
                    "slide.${result.format.extension}",
                    !transferring,
                )
                TextButton(
                    {
                        transferring = true
                        scope.launch {
                            try {
                                val staged =
                                    kotlinx.coroutines.withContext(
                                        kotlinx.coroutines.Dispatchers.IO
                                    ) {
                                        MediaOutputs(context)
                                            .stage(
                                                result.file,
                                                result.format.mime,
                                                "slide.${result.format.extension}",
                                            )
                                    }
                                if (!onToImage(staged.uri)) transferFailed = true
                            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                transferFailed = true
                            } finally {
                                transferring = false
                            }
                        }
                    },
                    enabled = !transferring,
                    modifier = Modifier.testTag("slide-image-to-image"),
                ) {
                    Text(stringResource(R.string.video_frame_to_image))
                }
            } else
                Button(
                    model::generate,
                    enabled = state.slideId != null && !transferring,
                    modifier = Modifier.testTag("slide-image-create"),
                ) {
                    Text(stringResource(R.string.create_result))
                }
        },
        panelContent = {
            Text(stringResource(R.string.output_format))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ImageOutputFormat.entries.forEach { format ->
                    FilterChip(
                        state.options.format == format,
                        { model.options(state.options.copy(format = format)) },
                        enabled = !state.busy && !transferring,
                        label = { Text(format.name) },
                        modifier = Modifier.testTag("slide-image-format-${format.name}"),
                    )
                }
            }
            if (state.options.format.usesQuality) {
                val qualityLabel = stringResource(R.string.image_quality)
                Text("${stringResource(R.string.image_quality)}: ${state.options.quality}")
                Slider(
                    state.options.quality.toFloat(),
                    { model.options(state.options.copy(quality = it.roundToInt())) },
                    enabled = !state.busy && !transferring,
                    valueRange = 40f..95f,
                    modifier = Modifier.testTag("slide-image-quality").accessibleSlider(qualityLabel, state.options.quality.toString()),
                )
            }
            Text(stringResource(R.string.max_edge))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1024, 2048, 4096).forEach { edge ->
                    FilterChip(
                        state.options.edge == edge,
                        { model.options(state.options.copy(edge = edge)) },
                        enabled = !state.busy && !transferring,
                        label = { Text("$edge px") },
                        modifier = Modifier.testTag("slide-image-edge-$edge"),
                    )
                }
            }
            Text(
                stringResource(R.string.present_image_hint),
                style = MaterialTheme.typography.bodySmall,
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().testTag("slide-image-list"),
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(project.title, style = MaterialTheme.typography.titleMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(project.slides, key = { _, slide -> slide.id }) { index, slide ->
                        FilterChip(
                            state.slideId == slide.id,
                            { model.select(slide.id) },
                            enabled = !state.busy && !transferring,
                            label = { Text(stringResource(R.string.slide_number, index + 1)) },
                            modifier = Modifier.testTag("slide-image-select-$index"),
                        )
                    }
                }
            }
            if (state.result == null && state.slideId != null)
                item {
                    val visible = state.project ?: project.visualSnapshot()
                    PresentationSlidePreview(
                        visible,
                        visible.slides.indexOfFirst { it.id == state.slideId }.coerceAtLeast(0),
                        rasterEdge = 512,
                    )
                }
            state.result?.let { result ->
                item {
                    Image(
                        result.preview.asImageBitmap(),
                        stringResource(R.string.slide_design_preview),
                        Modifier.fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .testTag("slide-image-preview"),
                    )
                    Text(
                        "${result.width} × ${result.height} · ${Formatter.formatFileSize(context, result.bytes)}"
                    )
                }
            }
            item {
                if (state.message != null || transferFailed)
                    Text(
                        stringResource(
                            if (transferFailed) R.string.output_action_error
                            else
                                when (state.message) {
                                    SlideImageMessage.EXPORTED -> R.string.export_complete
                                    SlideImageMessage.EXPORT_ERROR -> R.string.video_export_error
                                    SlideImageMessage.OVERFLOW -> R.string.pdf_overflow
                                    else -> R.string.present_error
                                },
                            (state.project?.slides?.indexOfFirst { it.id == state.slideId } ?: 0) +
                                1,
                        ),
                        color =
                            if (state.message == SlideImageMessage.EXPORTED)
                                MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("slide-image-message"),
                    )
            }
        }
    }
}

package com.example.contentstudio.ui

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.contentstudio.R
import com.example.contentstudio.data.*
import com.example.contentstudio.media.SlideImages
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun ProjectDraft(
    project: StudioProject,
    enabled: Boolean,
    saveStatus: DraftSaveStatus,
    onRetrySave: () -> Unit,
    onChange: (StudioProject) -> Unit,
    onPdf: () -> Unit,
    onMedia: (String, String?) -> Unit,
    onSlideImage: (String, String) -> Unit,
    onError: (Int) -> Unit,
    onEditVideo: (String) -> Unit,
    onVideo: () -> Unit,
    onPresent: () -> Unit,
    onSlideImages: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null)
                scope.launch {
                    try {
                        val name =
                            withContext(Dispatchers.IO) {
                                context.contentResolver.takePersistableUriPermission(
                                    uri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                                )
                                context.contentResolver
                                    .query(
                                        uri,
                                        arrayOf(OpenableColumns.DISPLAY_NAME),
                                        null,
                                        null,
                                        null,
                                    )
                                    ?.use { cursor ->
                                        if (cursor.moveToFirst())
                                            cursor.getString(
                                                cursor.getColumnIndexOrThrow(
                                                    OpenableColumns.DISPLAY_NAME
                                                )
                                            )
                                        else null
                                    }
                            }
                        onMedia(uri.toString(), name)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        onError(R.string.media_error)
                    }
                }
        }
    var selectedSlide by rememberSaveable(project.id) { mutableStateOf<String?>(null) }
    var deleteSlide by rememberSaveable(project.id) { mutableStateOf<String?>(null) }
    val imagePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            val id = selectedSlide
            selectedSlide = null
            if (uri != null && id != null)
                scope.launch {
                    try {
                        // The editor copies this temporary grant into a durable, private project
                        // asset.
                        onSlideImage(id, uri.toString())
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        android.util.Log.e("SlideImage", "Could not add image", error)
                        onError(R.string.slide_image_error)
                    }
                }
        }
    var editingSlideId by rememberSaveable(project.id) { mutableStateOf<String?>(null) }
    val index = project.slides.indexOfFirst { it.id == editingSlideId }.coerceAtLeast(0)
    val slide = project.slides.getOrNull(index)
    ToolWorkspace(
        stringResource(R.string.presentation),
        "project",
        {},
        embedded = true,
        panels =
            if (project.kind == ProjectKind.PRESENTATION)
                listOf(
                    WorkspacePanel("edit", stringResource(R.string.slide_number, index + 1)),
                    WorkspacePanel("theme", stringResource(R.string.presentation_theme)),
                    WorkspacePanel("notes", stringResource(R.string.present_notes)),
                    WorkspacePanel("info", stringResource(R.string.project_name)),
                )
            else emptyList(),
        actions = {
            if (project.kind == ProjectKind.PRESENTATION) {
                Button(
                    onClick = onPdf,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth().testTag("preview-pdf"),
                ) {
                    Text(stringResource(R.string.preview_pdf))
                }
                var more by remember { mutableStateOf(false) }
                Box {
                    TextButton(
                        { more = true },
                        enabled = enabled,
                        modifier = Modifier.testTag("presentation-more"),
                    ) {
                        Text(stringResource(R.string.other_actions))
                    }
                    DropdownMenu(more, onDismissRequest = { more = false }) {
                        listOf(
                                Triple(R.string.present_action, "presentation-present", onPresent),
                                Triple(
                                    R.string.present_images,
                                    "presentation-images",
                                    onSlideImages,
                                ),
                                Triple(R.string.slideshow_title, "presentation-to-video", onVideo),
                            )
                            .forEach { (label, tag, action) ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(label)) },
                                    onClick = {
                                        more = false
                                        action()
                                    },
                                    modifier = Modifier.testTag(tag),
                                )
                            }
                    }
                }
                TextButton(
                    onClick = {
                        val added = Slide()
                        editingSlideId = added.id
                        onChange(project.copy(slides = project.slides + added))
                    },
                    enabled = enabled && project.slides.size < SlideOperations.MAX_SLIDES,
                    modifier = Modifier.testTag("add-slide"),
                ) {
                    Text(stringResource(R.string.add_slide))
                }
            }
        },
        panelContent = { panel ->
            when (panel) {
                "notes" ->
                    slide?.let { current ->
                        OutlinedTextField(
                            current.notes,
                            { text ->
                                if (text.length <= 8000)
                                    onChange(
                                        project.copy(
                                            slides =
                                                project.slides.map {
                                                    if (it.id == current.id) it.copy(notes = text)
                                                    else it
                                                }
                                        )
                                    )
                            },
                            enabled = enabled,
                            label = { Text(stringResource(R.string.present_notes)) },
                            supportingText = { Text("${current.notes.length} / 8000") },
                            modifier = Modifier.fillMaxWidth().testTag("slide-notes-$index"),
                            minLines = 4,
                            maxLines = 10,
                        )
                        Text(
                            stringResource(R.string.present_notes_hint),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                "info" -> {
                    OutlinedTextField(
                        value = project.title,
                        onValueChange = { onChange(project.copy(title = it)) },
                        label = { Text(stringResource(R.string.project_name)) },
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth().testTag("project-title"),
                        singleLine = true,
                        keyboardOptions =
                            KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        shape = RoundedCornerShape(20.dp),
                        colors = studioFieldColors(),
                    )
                    if (
                        project.kind == ProjectKind.PRESENTATION ||
                            saveStatus == DraftSaveStatus.ERROR
                    ) {
                        Text(
                            stringResource(
                                when (saveStatus) {
                                    DraftSaveStatus.PENDING -> R.string.autosave_pending
                                    DraftSaveStatus.SAVING -> R.string.autosave_saving
                                    DraftSaveStatus.SAVED -> R.string.saved
                                    DraftSaveStatus.ERROR -> R.string.save_error
                                }
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color =
                                if (saveStatus == DraftSaveStatus.ERROR)
                                    MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("draft-save-status-info"),
                        )
                        if (
                            saveStatus == DraftSaveStatus.ERROR &&
                                project.kind == ProjectKind.PRESENTATION
                        ) {
                            TextButton(
                                onClick = onRetrySave,
                                enabled = enabled,
                                modifier = Modifier.testTag("retry-draft-save-info"),
                            ) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }
                }
                "theme" ->
                    PresentationThemePicker(project.theme, enabled) {
                        onChange(project.copy(theme = it))
                    }
                "edit" ->
                    slide?.let { slide ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.slide_number, index + 1),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            var menuOpen by remember { mutableStateOf(false) }
                            Box {
                                TextButton(
                                    onClick = { menuOpen = true },
                                    enabled = enabled,
                                    modifier = Modifier.testTag("slide-actions-$index"),
                                ) {
                                    Text(stringResource(R.string.slide_actions))
                                }
                                DropdownMenu(
                                    expanded = menuOpen,
                                    onDismissRequest = { menuOpen = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.duplicate_slide)) },
                                        enabled = project.slides.size < SlideOperations.MAX_SLIDES,
                                        onClick = {
                                            menuOpen = false
                                            onChange(
                                                project.copy(
                                                    slides =
                                                        SlideOperations.duplicate(
                                                            project.slides,
                                                            slide.id,
                                                        )
                                                )
                                            )
                                        },
                                        modifier = Modifier.testTag("duplicate-slide-$index"),
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.move_slide_up)) },
                                        enabled = index > 0,
                                        onClick = {
                                            menuOpen = false
                                            onChange(
                                                project.copy(
                                                    slides =
                                                        SlideOperations.move(
                                                            project.slides,
                                                            slide.id,
                                                            -1,
                                                        )
                                                )
                                            )
                                        },
                                        modifier = Modifier.testTag("move-slide-up-$index"),
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.move_slide_down)) },
                                        enabled = index < project.slides.lastIndex,
                                        onClick = {
                                            menuOpen = false
                                            onChange(
                                                project.copy(
                                                    slides =
                                                        SlideOperations.move(
                                                            project.slides,
                                                            slide.id,
                                                            1,
                                                        )
                                                )
                                            )
                                        },
                                        modifier = Modifier.testTag("move-slide-down-$index"),
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.delete_slide)) },
                                        enabled = project.slides.size > 1,
                                        onClick = {
                                            menuOpen = false
                                            deleteSlide = slide.id
                                        },
                                        modifier = Modifier.testTag("delete-slide-$index"),
                                    )
                                }
                            }
                        }
                        SlideLayoutPicker(slide.layout, index, enabled) { layout ->
                            onChange(
                                project.copy(
                                    slides =
                                        project.slides.map { old ->
                                            if (old.id == slide.id) old.copy(layout = layout)
                                            else old
                                        }
                                )
                            )
                        }
                        if (slide.imageUri == null)
                            Text(
                                stringResource(R.string.layout_image_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        OutlinedTextField(
                            slide.heading,
                            onValueChange = { heading ->
                                onChange(
                                    project.copy(
                                        slides =
                                            project.slides.mapIndexed { i, old ->
                                                if (i == index) old.copy(heading = heading) else old
                                            }
                                    )
                                )
                            },
                            label = { Text(stringResource(R.string.heading)) },
                            enabled = enabled,
                            shape = RoundedCornerShape(18.dp),
                            colors = studioFieldColors(),
                            modifier = Modifier.fillMaxWidth().testTag("slide-heading-$index"),
                        )
                        OutlinedTextField(
                            slide.body,
                            onValueChange = { body ->
                                onChange(
                                    project.copy(
                                        slides =
                                            project.slides.mapIndexed { i, old ->
                                                if (i == index) old.copy(body = body) else old
                                            }
                                    )
                                )
                            },
                            label = { Text(stringResource(R.string.body)) },
                            enabled = enabled,
                            shape = RoundedCornerShape(18.dp),
                            colors = studioFieldColors(),
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth().testTag("slide-body-$index"),
                        )
                        slide.imageUri?.let { imageUri ->
                            SlideImageThumbnail(imageUri, index)
                            if (
                                Uri.parse(imageUri).lastPathSegment?.substringAfterLast('.') in
                                    listOf("heic", "heif", "avif")
                            ) {
                                Text(
                                    stringResource(R.string.files_modern_images),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(
                                onClick = {
                                    onChange(
                                        project.copy(
                                            slides =
                                                project.slides.mapIndexed { i, old ->
                                                    if (i == index) old.copy(imageUri = null)
                                                    else old
                                                }
                                        )
                                    )
                                },
                                enabled = enabled,
                                modifier = Modifier.testTag("remove-slide-image-$index"),
                            ) {
                                Text(stringResource(R.string.remove_image))
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                selectedSlide = slide.id
                                imagePicker.launch(
                                    com.example.contentstudio.media.ImageFormats.inputMimes
                                )
                            },
                            enabled = enabled,
                            modifier = Modifier.testTag("choose-slide-image-$index"),
                        ) {
                            Text(stringResource(R.string.choose_image))
                        }
                    }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().testTag("project-editor-list"),
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (project.kind == ProjectKind.PRESENTATION && slide != null) {
                item(key = "selected-preview") { PresentationSlidePreview(project, index) }
                item(key = "slides") {
                    androidx.compose.foundation.lazy.LazyRow(
                        Modifier.testTag("project-slides"),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(project.slides, key = { _, value -> value.id }) { number, value
                            ->
                            Card(
                                onClick = { editingSlideId = value.id },
                                modifier = Modifier.width(140.dp).testTag("select-slide-$number"),
                                border =
                                    androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (number == index) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outlineVariant,
                                    ),
                            ) {
                                Column(
                                    Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        stringResource(R.string.slide_number, number + 1),
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                    Text(
                                        value.heading,
                                        maxLines = 2,
                                        overflow =
                                            androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    )
                                    value.imageUri?.let {
                                        SlideImageThumbnail(it, number, 128, 48.dp)
                                    }
                                    Text(
                                        value.body,
                                        maxLines = 2,
                                        overflow =
                                            androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                }
                item(key = "save-status") {
                    Text(
                        stringResource(
                            when (saveStatus) {
                                DraftSaveStatus.ERROR -> R.string.save_error
                                DraftSaveStatus.SAVING -> R.string.autosave_saving
                                DraftSaveStatus.PENDING -> R.string.autosave_pending
                                else -> R.string.saved
                            }
                        ),
                        modifier = Modifier.testTag("draft-save-status"),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (saveStatus == DraftSaveStatus.ERROR)
                        TextButton(
                            onClick = onRetrySave,
                            modifier = Modifier.testTag("retry-draft-save"),
                        ) {
                            Text(stringResource(R.string.retry))
                        }
                }
            } else
                item(key = "legacy-video") {
                    OutlinedTextField(
                        value = project.title,
                        onValueChange = { onChange(project.copy(title = it)) },
                        label = { Text(stringResource(R.string.project_name)) },
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth().testTag("project-title"),
                        singleLine = true,
                        keyboardOptions =
                            KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        shape = RoundedCornerShape(20.dp),
                        colors = studioFieldColors(),
                    )
                    if (
                        project.kind == ProjectKind.PRESENTATION ||
                            saveStatus == DraftSaveStatus.ERROR
                    ) {
                        Text(
                            stringResource(
                                when (saveStatus) {
                                    DraftSaveStatus.PENDING -> R.string.autosave_pending
                                    DraftSaveStatus.SAVING -> R.string.autosave_saving
                                    DraftSaveStatus.SAVED -> R.string.saved
                                    DraftSaveStatus.ERROR -> R.string.save_error
                                }
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color =
                                if (saveStatus == DraftSaveStatus.ERROR)
                                    MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("draft-save-status"),
                        )
                        if (
                            saveStatus == DraftSaveStatus.ERROR &&
                                project.kind == ProjectKind.PRESENTATION
                        ) {
                            TextButton(
                                onClick = onRetrySave,
                                enabled = enabled,
                                modifier = Modifier.testTag("retry-draft-save"),
                            ) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }
                    FilledTonalButton(
                        onClick = { picker.launch(arrayOf("video/*")) },
                        enabled = enabled,
                    ) {
                        Text(stringResource(R.string.choose_video))
                    }
                    project.sourceUri?.let {
                        Text(
                            project.sourceName ?: stringResource(R.string.video),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(R.string.media_added),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = { onEditVideo(it) },
                            enabled = enabled,
                            modifier = Modifier.testTag("draft-edit-video"),
                        ) {
                            Text(stringResource(R.string.edit_video))
                        }
                    }
                }
            item(key = "help") {
                WorkspaceHelp {
                    SupportedFilesButton(
                        if (project.kind == ProjectKind.PRESENTATION)
                            com.example.contentstudio.media.SupportedTool.PRESENTATION
                        else com.example.contentstudio.media.SupportedTool.VIDEO
                    )
                }
            }
        }
    }
    val deleteIndex = project.slides.indexOfFirst { it.id == deleteSlide }
    if (deleteIndex >= 0)
        AlertDialog(
            onDismissRequest = { deleteSlide = null },
            title = { Text(stringResource(R.string.delete_slide)) },
            text = { Text(stringResource(R.string.delete_slide_confirmation, deleteIndex + 1)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onChange(
                            project.copy(
                                slides =
                                    SlideOperations.remove(
                                        project.slides,
                                        project.slides[deleteIndex].id,
                                    )
                            )
                        )
                        deleteSlide = null
                    },
                    enabled = enabled && project.slides.size > 1,
                    modifier = Modifier.testTag("confirm-delete-slide"),
                ) {
                    Text(stringResource(R.string.delete_slide))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteSlide = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
}

@Composable
private fun SlideImageThumbnail(
    uri: String,
    index: Int,
    edge: Int = 384,
    height: androidx.compose.ui.unit.Dp = 120.dp,
) {
    val context = LocalContext.current
    val bitmap by
        produceState<Bitmap?>(initialValue = null, uri, edge) {
            value =
                try {
                    com.example.contentstudio.media.PreviewLoader.load {
                        SlideImages.decode(context, Uri.parse(uri), edge)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                } catch (_: OutOfMemoryError) {
                    null
                }
        }
    bitmap?.let {
        Image(
            it.asImageBitmap(),
            contentDescription = stringResource(R.string.slide_image_added),
            modifier =
                Modifier.fillMaxWidth()
                    .height(height)
                    .testTag(if (edge == 128) "slide-thumb-$index" else "slide-image-$index"),
        )
    }
}

package com.example.contentstudio.ui

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.example.contentstudio.data.*
import com.example.contentstudio.ui.PresentationThemePicker
import com.example.contentstudio.ui.SlideLayoutPicker
import com.example.contentstudio.ui.PresentationSlidePreview
import com.example.contentstudio.ui.studioFieldColors
import com.example.contentstudio.media.SlideImages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.example.contentstudio.R
import androidx.compose.foundation.lazy.itemsIndexed
@Composable
internal fun ProjectDraft(project: StudioProject, enabled: Boolean, saveStatus: DraftSaveStatus,
    onRetrySave: () -> Unit, onChange: (StudioProject) -> Unit,
    onPdf: () -> Unit, onMedia: (String, String?) -> Unit, onSlideImage: (String, String) -> Unit,
    onError: (Int) -> Unit, onEditVideo: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            try {
                val name = withContext(Dispatchers.IO) {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null
                    }
                }
                onMedia(uri.toString(), name)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) { onError(R.string.media_error) }
        }
    }
    var selectedSlide by rememberSaveable(project.id) { mutableStateOf<String?>(null) }
    var deleteSlide by rememberSaveable(project.id) { mutableStateOf<String?>(null) }
    var previewedSlide by rememberSaveable(project.id) { mutableStateOf<String?>(null) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val id = selectedSlide
        selectedSlide = null
        if (uri != null && id != null) scope.launch {
            try {
                // The editor copies this temporary grant into a durable, private project asset.
                onSlideImage(id, uri.toString())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                android.util.Log.e("SlideImage", "Could not add image", error)
                onError(R.string.slide_image_error)
            }
        }
    }
    LazyColumn(Modifier.widthIn(max = 900.dp).fillMaxSize().testTag("project-editor-list"),
        contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item(key = "header", contentType = "header") {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SupportedFilesButton(if (project.kind == ProjectKind.PRESENTATION)
            com.example.contentstudio.media.SupportedTool.PRESENTATION else com.example.contentstudio.media.SupportedTool.VIDEO)
        OutlinedTextField(
            value = project.title, onValueChange = { onChange(project.copy(title = it)) },
            label = { Text(stringResource(R.string.project_name)) }, enabled = enabled,
            modifier = Modifier.fillMaxWidth().testTag("project-title"), singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            shape = RoundedCornerShape(20.dp), colors = studioFieldColors(),
        )
        if (project.kind == ProjectKind.PRESENTATION || saveStatus == DraftSaveStatus.ERROR) {
            Text(stringResource(when (saveStatus) {
                DraftSaveStatus.PENDING -> R.string.autosave_pending
                DraftSaveStatus.SAVING -> R.string.autosave_saving
                DraftSaveStatus.SAVED -> R.string.saved
                DraftSaveStatus.ERROR -> R.string.save_error
            }), style = MaterialTheme.typography.bodySmall,
                color = if (saveStatus == DraftSaveStatus.ERROR) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("draft-save-status"))
            if (saveStatus == DraftSaveStatus.ERROR && project.kind == ProjectKind.PRESENTATION) {
                TextButton(onClick = onRetrySave, enabled = enabled, modifier = Modifier.testTag("retry-draft-save")) {
                    Text(stringResource(R.string.retry))
                }
            }
        }
        }
        }
        if (project.kind == ProjectKind.PRESENTATION) {
            item(key = "design", contentType = "design") {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            PresentationThemePicker(project.theme, enabled) { onChange(project.copy(theme = it)) }
            Button(onClick = onPdf, enabled = enabled, modifier = Modifier.testTag("preview-pdf")) {
                Text(stringResource(R.string.preview_pdf))
            }
            }
            }
            itemsIndexed(project.slides, key = { _, slide -> "slide-${slide.id}" }, contentType = { _, _ -> "slide" }) { index, slide ->
                ElevatedCard(Modifier.fillMaxWidth().testTag("slide-item-$index").border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(28.dp)),
                    shape = RoundedCornerShape(28.dp), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.slide_number, index + 1),
                                style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            var menuOpen by remember { mutableStateOf(false) }
                            Box {
                                TextButton(onClick = { menuOpen = true }, enabled = enabled,
                                    modifier = Modifier.testTag("slide-actions-$index")) {
                                    Text(stringResource(R.string.slide_actions))
                                }
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    DropdownMenuItem(text = { Text(stringResource(R.string.duplicate_slide)) },
                                        enabled = project.slides.size < SlideOperations.MAX_SLIDES,
                                        onClick = { menuOpen = false; onChange(project.copy(slides = SlideOperations.duplicate(project.slides, slide.id))) },
                                        modifier = Modifier.testTag("duplicate-slide-$index"))
                                    DropdownMenuItem(text = { Text(stringResource(R.string.move_slide_up)) },
                                        enabled = index > 0,
                                        onClick = { menuOpen = false; onChange(project.copy(slides = SlideOperations.move(project.slides, slide.id, -1))) },
                                        modifier = Modifier.testTag("move-slide-up-$index"))
                                    DropdownMenuItem(text = { Text(stringResource(R.string.move_slide_down)) },
                                        enabled = index < project.slides.lastIndex,
                                        onClick = { menuOpen = false; onChange(project.copy(slides = SlideOperations.move(project.slides, slide.id, 1))) },
                                        modifier = Modifier.testTag("move-slide-down-$index"))
                                    DropdownMenuItem(text = { Text(stringResource(R.string.delete_slide)) },
                                        enabled = project.slides.size > 1,
                                        onClick = { menuOpen = false; deleteSlide = slide.id },
                                        modifier = Modifier.testTag("delete-slide-$index"))
                                }
                            }
                        }
                        SlideLayoutPicker(slide.layout, index, enabled) { layout ->
                            onChange(project.copy(slides = project.slides.map { old ->
                                if (old.id == slide.id) old.copy(layout = layout) else old
                            }))
                        }
                        if (slide.imageUri == null) Text(stringResource(R.string.layout_image_hint),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(slide.heading, onValueChange = { heading ->
                            onChange(project.copy(slides = project.slides.mapIndexed { i, old -> if (i == index) old.copy(heading = heading) else old }))
                        }, label = { Text(stringResource(R.string.heading)) }, enabled = enabled,
                            shape = RoundedCornerShape(18.dp), colors = studioFieldColors(),
                            modifier = Modifier.fillMaxWidth().testTag("slide-heading-$index"))
                        OutlinedTextField(slide.body, onValueChange = { body ->
                            onChange(project.copy(slides = project.slides.mapIndexed { i, old -> if (i == index) old.copy(body = body) else old }))
                        }, label = { Text(stringResource(R.string.body)) }, enabled = enabled,
                            shape = RoundedCornerShape(18.dp), colors = studioFieldColors(),
                            minLines = 3, modifier = Modifier.fillMaxWidth().testTag("slide-body-$index"))
                        slide.imageUri?.let { imageUri ->
                            SlideImageThumbnail(imageUri, index)
                            if (Uri.parse(imageUri).lastPathSegment?.substringAfterLast('.') in listOf("heic", "heif", "avif"))
                                Text(stringResource(R.string.files_modern_images), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = {
                                onChange(project.copy(slides = project.slides.mapIndexed { i, old ->
                                    if (i == index) old.copy(imageUri = null) else old
                                }))
                            }, enabled = enabled, modifier = Modifier.testTag("remove-slide-image-$index")) {
                                Text(stringResource(R.string.remove_image))
                            }
                        }
                        OutlinedButton(onClick = {
                            selectedSlide = slide.id
                            imagePicker.launch(com.example.contentstudio.media.ImageFormats.inputMimes)
                        }, enabled = enabled, modifier = Modifier.testTag("choose-slide-image-$index")) {
                            Text(stringResource(R.string.choose_image))
                        }
                        TextButton(onClick = { previewedSlide = if (previewedSlide == slide.id) null else slide.id },
                            enabled = enabled, modifier = Modifier.testTag("toggle-slide-preview-$index")) {
                            Text(stringResource(if (previewedSlide == slide.id) R.string.hide_slide_preview else R.string.show_slide_preview))
                        }
                        if (previewedSlide == slide.id) PresentationSlidePreview(project, index)
                    }
                }
            }
            item(key = "add-slide", contentType = "action") {
            OutlinedButton(onClick = { onChange(project.copy(slides = project.slides + Slide())) },
                enabled = enabled && project.slides.size < SlideOperations.MAX_SLIDES,
                modifier = Modifier.testTag("add-slide")) {
                Text(stringResource(R.string.add_slide))
            }
            }
        } else {
            item(key = "video-source", contentType = "video") {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            FilledTonalButton(onClick = { picker.launch(arrayOf("video/*")) }, enabled = enabled) {
                Text(stringResource(R.string.choose_video))
            }
            project.sourceUri?.let {
                Text(project.sourceName ?: stringResource(R.string.video), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.media_added), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { onEditVideo(it) }, enabled = enabled, modifier = Modifier.testTag("draft-edit-video")) {
                    Text(stringResource(R.string.edit_video))
                }
            }
        }
        }
        }
        item(key = "footer", contentType = "footer") {
        Text(stringResource(if (project.kind == ProjectKind.PRESENTATION) R.string.autosave_hint else R.string.local_draft),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    }
    val deleteIndex = project.slides.indexOfFirst { it.id == deleteSlide }
    if (deleteIndex >= 0) AlertDialog(
        onDismissRequest = { deleteSlide = null },
        title = { Text(stringResource(R.string.delete_slide)) },
        text = { Text(stringResource(R.string.delete_slide_confirmation, deleteIndex + 1)) },
        confirmButton = {
            TextButton(onClick = {
                onChange(project.copy(slides = SlideOperations.remove(project.slides, project.slides[deleteIndex].id)))
                deleteSlide = null
            }, enabled = enabled && project.slides.size > 1, modifier = Modifier.testTag("confirm-delete-slide")) {
                Text(stringResource(R.string.delete_slide))
            }
        },
        dismissButton = { TextButton(onClick = { deleteSlide = null }) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun SlideImageThumbnail(uri: String, index: Int) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, uri) {
        value = try { com.example.contentstudio.media.PreviewLoader.load { SlideImages.decode(context, Uri.parse(uri), 384) } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }
        catch (_: OutOfMemoryError) { null }
    }
    bitmap?.let {
        Image(it.asImageBitmap(), contentDescription = stringResource(R.string.slide_image_added),
            modifier = Modifier.fillMaxWidth().height(120.dp).testTag("slide-image-$index"))
    }
}


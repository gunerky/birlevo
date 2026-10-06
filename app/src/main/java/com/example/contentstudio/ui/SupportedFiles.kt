package com.example.contentstudio.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.contentstudio.R
import com.example.contentstudio.media.*
import java.text.NumberFormat

internal fun importIssueText(issue: ImportIssue): Int = when (issue) {
    ImportIssue.IMAGE_FORMAT -> R.string.error_image_format
    ImportIssue.IMAGE_ANIMATED -> R.string.error_image_animation
    ImportIssue.IMAGE_LIMIT -> R.string.error_image_limit
    ImportIssue.IMAGE_DEVICE -> R.string.error_image_device
    ImportIssue.VIDEO_CONTAINER -> R.string.error_video_container
    ImportIssue.VIDEO_CODEC -> R.string.error_video_codec
    ImportIssue.VIDEO_HDR -> R.string.error_video_hdr
    ImportIssue.VIDEO_TRACKS -> R.string.error_video_tracks
    ImportIssue.VIDEO_LIMIT -> R.string.error_video_limit
    ImportIssue.INVALID_MEDIA -> R.string.error_bad_media
    ImportIssue.FILE_ACCESS -> R.string.error_file_access
    ImportIssue.MEMORY -> R.string.error_memory
    ImportIssue.PHOTO_LIMIT -> R.string.photo_pdf_import_error
}

@Composable
internal fun pixels(value: Int): String = stringResource(R.string.unit_pixels,
    NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0]).format(value))

@Composable
internal fun seconds(value: Long): String = stringResource(R.string.unit_seconds,
    NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0]).format(value))

@Composable
internal fun SupportedFilesButton(tool: SupportedTool? = null) {
    var open by rememberSaveable { mutableStateOf(false) }
    var licenses by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { open = true }, modifier = Modifier.testTag("supported-files-${tool?.name ?: "all"}")) {
        Text(stringResource(R.string.supported_files))
    }
    if (open) SupportedFilesDialog(tool, { open = false }, { licenses = true })
    if (licenses) OpenSourceDialog { licenses = false }
}

@Composable
private fun SupportedFilesDialog(tool: SupportedTool?, onClose: () -> Unit, onLicenses: () -> Unit) {
    AlertDialog(onDismissRequest = onClose,
        title = { Text(stringResource(R.string.supported_files)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 480.dp).testTag("supported-files-list"),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(FormatSupport.capabilities.filter { tool == null || it.tool == tool }, key = { it.tool }) { capability ->
                    Column(Modifier.fillMaxWidth().testTag("support-${capability.tool.name}"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(when (capability.tool) {
                            SupportedTool.IMAGE -> R.string.compress_image
                            SupportedTool.VIDEO -> R.string.edit_video
                            SupportedTool.PRESENTATION -> R.string.create_presentation
                            SupportedTool.PHOTO_PDF -> R.string.photos_to_pdf
                            SupportedTool.PDF_READER -> R.string.read_pdf
                            SupportedTool.PDF_PAGES -> R.string.pdf_pages
                            SupportedTool.TEXT_DOCUMENT -> R.string.read_documents
                        }), style = MaterialTheme.typography.titleSmall)
                        fun label(mime: String) = when (mime) { "image/jpeg" -> "JPEG"; "image/png" -> "PNG";
                            "image/webp" -> "WebP"; "image/heic" -> "HEIC"; "image/heif" -> "HEIF"; "image/avif" -> "AVIF";
                            "video/mp4" -> "MP4"; "video/quicktime" -> "MOV"; "video/3gpp" -> "3GP";
                            "video/x-matroska" -> "MKV"; "video/webm" -> "WebM"; "application/pdf" -> "PDF";
                            "text/plain" -> "TXT"; "text/csv" -> "CSV"; "text/tab-separated-values" -> "TSV"; "text/markdown" -> "MD (TXT)"; else -> mime }
                        val input = capability.inputs.joinToString(" / ", transform = ::label)
                        Text(stringResource(R.string.files_input) + ": " + input +
                            if (capability.tool == SupportedTool.PRESENTATION) " + " + stringResource(R.string.body) else "")
                        Text(stringResource(R.string.files_output) + ": " + capability.outputs.joinToString(" / ", transform = ::label))
                        Text(stringResource(when (capability.tool) {
                            SupportedTool.IMAGE -> R.string.files_still_images
                            SupportedTool.VIDEO -> R.string.video_losses
                            SupportedTool.PDF_READER -> R.string.reader_hint
                            SupportedTool.PDF_PAGES -> R.string.pages_hint
                            SupportedTool.TEXT_DOCUMENT -> R.string.document_losses
                            else -> R.string.files_pdf_creation
                        }), style = MaterialTheme.typography.bodySmall)
                        if (capability.tool in listOf(SupportedTool.IMAGE, SupportedTool.PRESENTATION, SupportedTool.PHOTO_PDF))
                            Text(stringResource(R.string.files_modern_images), style = MaterialTheme.typography.bodySmall)
                        if (capability.tool == SupportedTool.IMAGE) Text(stringResource(R.string.compression_hint))
                        if (capability.tool == SupportedTool.VIDEO) {
                            Text(stringResource(R.string.video_compression_hint))
                            Text(stringResource(R.string.files_video_decoders), style = MaterialTheme.typography.bodySmall)
                            Text("≤4096 px · ≤8.8 MP · ≤60 fps", style = MaterialTheme.typography.bodySmall)
                        }
                        if (capability.tool == SupportedTool.PHOTO_PDF) Text(stringResource(R.string.photo_pdf_hint))
                        if (capability.tool == SupportedTool.PDF_READER) {
                            Text(stringResource(R.string.reader_limits)); Text(stringResource(R.string.reader_image_loss))
                        }
                        if (capability.tool == SupportedTool.PDF_PAGES) {
                            Text(stringResource(R.string.pages_limits)); Text(stringResource(R.string.pages_loss))
                        }
                        if (capability.tool == SupportedTool.TEXT_DOCUMENT) {
                            Text(stringResource(R.string.document_limits)); Text(stringResource(R.string.document_table_limits))
                            Text("TXT / MD → TXT / PDF\nCSV / TSV → TXT / CSV / TSV / PDF")
                        }
                        HorizontalDivider()
                    }
                }
                item { Text(stringResource(R.string.files_pending), style = MaterialTheme.typography.bodySmall) }
                item { TextButton(onClick = onLicenses, modifier = Modifier.testTag("open-source-licenses")) {
                    Text(stringResource(R.string.open_source_licenses))
                } }
            }
        },
        confirmButton = { TextButton(onClick = onClose, modifier = Modifier.testTag("support-close")) { Text(stringResource(R.string.close)) } })
}

@Composable
private fun OpenSourceDialog(onClose: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val text by produceState<String?>(null) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            context.assets.open("licenses/NOTICES.txt").bufferedReader().use { it.readText() }
        }
    }
    AlertDialog(onDismissRequest = onClose, title = { Text(stringResource(R.string.open_source_licenses)) },
        text = { LazyColumn(Modifier.heightIn(max = 480.dp)) {
            items(text?.split("\n\n").orEmpty()) { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface) }
        } }, confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.close)) } })
}

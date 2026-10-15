package com.example.contentstudio.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.contentstudio.R
import com.example.contentstudio.audio.AudioSessionCodec
import com.example.contentstudio.media.*
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class StudioRoute {
    HOME,
    PROJECT,
    PRESENTATION_PDF,
    PRESENTATION_PLAYER,
    PRESENTATION_IMAGES,
    PRESENTATION_PPTX,
    IMAGE,
    BATCH_IMAGES,
    BATCH_VIDEO,
    BATCH_AUDIO,
    VIDEO,
    VIDEO_FRAME,
    AUDIO,
    DOCUMENTS,
    PDF_READER,
    PDF_IMAGE,
    PDF_PAGES,
    PHOTO_PDF,
    FILE_ACTIONS,
    SLIDESHOW,
    SLIDESHOW_PRESENTATION,
}

enum class FileCategory {
    IMAGE,
    VIDEO,
    AUDIO,
    PDF,
    TEXT,
}

enum class FileAction {
    IMAGE,
    PHOTO_PDF,
    PRESENTATION_IMAGE,
    SLIDESHOW,
    VIDEO,
    AUDIO,
    FRAME,
    PDF_READER,
    PDF_PAGES,
    PDF_IMAGE,
    DOCUMENTS,
}

/** Selection hints only. Existing engines validate actual bytes and device capabilities. */
object FileActions {
    private val extensions =
        mapOf(
            FileCategory.IMAGE to
                setOf("jpg", "jpeg", "png", "bmp", "webp", "heic", "heif", "avif"),
            FileCategory.VIDEO to setOf("mp4", "mov", "3gp", "mkv", "webm"),
            FileCategory.AUDIO to setOf("mp3", "m4a", "aac", "wav", "ogg", "opus", "flac"),
            FileCategory.PDF to setOf("pdf"),
            FileCategory.TEXT to setOf("txt", "csv", "tsv", "md", "docx", "xlsx"),
        )

    fun category(mime: String?, name: String): FileCategory? {
        val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val named = extensions.entries.firstOrNull { extension in it.value }?.key
        val type = mime?.substringBefore(';')?.lowercase(Locale.ROOT)
        val typed =
            when {
                type in FormatSupport.imageMimes || type == "image/jpg" -> FileCategory.IMAGE
                type in FormatSupport.videoMimes -> FileCategory.VIDEO
                type?.startsWith("audio/") == true || type == "application/ogg" ->
                    FileCategory.AUDIO
                type == "application/pdf" -> FileCategory.PDF
                type in com.example.contentstudio.documents.DocumentPolicy.inputMimes ||
                    type == "application/csv" -> FileCategory.TEXT
                else -> null
            }
        if (named != null && typed != null && named != typed) return null
        if (type == "application/vnd.ms-excel") return named?.takeIf { it == FileCategory.TEXT }
        return typed ?: named
    }

    fun actions(category: FileCategory) =
        when (category) {
            FileCategory.IMAGE ->
                listOf(
                    FileAction.IMAGE,
                    FileAction.PHOTO_PDF,
                    FileAction.PRESENTATION_IMAGE,
                    FileAction.SLIDESHOW,
                )
            FileCategory.VIDEO -> listOf(FileAction.VIDEO, FileAction.AUDIO, FileAction.FRAME)
            FileCategory.AUDIO -> listOf(FileAction.AUDIO)
            FileCategory.PDF ->
                listOf(FileAction.PDF_READER, FileAction.PDF_PAGES, FileAction.PDF_IMAGE)
            FileCategory.TEXT -> listOf(FileAction.DOCUMENTS)
        }

    val inputMimes
        get() =
            (FormatSupport.capabilities.flatMap { it.inputs } +
                    listOf(
                        "audio/x-wav",
                        "audio/x-flac",
                        "application/ogg",
                        "application/csv",
                        "application/vnd.ms-excel",
                        "application/octet-stream",
                    ))
                .distinct()
                .toTypedArray()
}

data class PickedFile(val name: String, val category: FileCategory?)

suspend fun describeFile(context: Context, uri: Uri): PickedFile =
    withContext(Dispatchers.IO) {
        val name =
            context.contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
                ?: uri.lastPathSegment.orEmpty()
        PickedFile(name, FileActions.category(context.contentResolver.getType(uri), name))
    }

/** Bounded metadata reads; this never initializes players, media ViewModels or bitmaps. */
suspend fun resumeWork(context: Context): List<ResumeWork> =
    withContext(Dispatchers.IO) {
        listOf("video-compression", "audio-conversion", "photo-pdf", SlideshowEngine.FOLDER, "batch-images", "batch-video", "batch-audio")
            .mapNotNull { kind ->
                runCatching {
                        val folder = File(context.noBackupFilesDir, kind)
                        val json = AtomicMediaSession(folder).read() ?: return@runCatching null
                        if (kind in setOf("batch-images", "batch-video", "batch-audio")) {
                            val batch = com.example.contentstudio.batch.BatchPolicy.decode(json)
                            require(batch.options.kind.processing.folder == kind)
                            if (batch.items.isEmpty()) return@runCatching null
                            return@runCatching ResumeWork(kind, batch.items.first().name, when(kind) {
                                "batch-video" -> StudioGlyph.VIDEO; "batch-audio" -> StudioGlyph.AUDIO; else -> StudioGlyph.IMAGE
                            })
                        }
                        if (kind == SlideshowEngine.FOLDER) {
                            val timeline = SlideshowCodec.decode(json)
                            if (timeline.frames.isEmpty()) return@runCatching null
                            timeline.assets.forEach {
                                it.resolve(folder, "page-", FormatSupport.MAX_IMAGE_BYTES)
                            }
                            return@runCatching ResumeWork(
                                kind,
                                timeline.title.ifBlank {
                                    timeline.frames.first().asset?.name
                                        ?: context.getString(R.string.slideshow_title)
                                },
                                StudioGlyph.VIDEO,
                            )
                        }
                        val assets =
                            when (kind) {
                                "video-compression" ->
                                    MediaSessionCodec.video(json).clips.map { it.asset }
                                "audio-conversion" ->
                                    AudioSessionCodec.decode(json).clips.map { it.asset }
                                else -> MediaSessionCodec.photos(json).assets
                            }
                        if (
                            assets.isEmpty() ||
                                assets.any {
                                    it.file != File(it.file).name ||
                                        File(folder, it.file).let { f ->
                                            f.canonicalFile.parentFile != folder.canonicalFile ||
                                                !f.isFile ||
                                                f.length() != it.bytes
                                        }
                                }
                        )
                            return@runCatching null
                        ResumeWork(
                            kind,
                            assets.first().name,
                            when (kind) {
                                "video-compression" -> StudioGlyph.VIDEO
                                "audio-conversion" -> StudioGlyph.AUDIO
                                else -> StudioGlyph.SLIDES
                            },
                        )
                    }
                    .getOrNull()
            }
    }

@Composable
fun FileActionScreen(
    file: PickedFile?,
    busy: Boolean,
    onClose: () -> Unit,
    onAction: (FileAction) -> Unit,
) {
    ToolWorkspace(
        stringResource(R.string.open_file),
        "file-actions",
        onClose,
        actions = {
            if (busy) TextButton(onClick = onClose) { Text(stringResource(R.string.cancel)) }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(file?.name.orEmpty(), style = MaterialTheme.typography.titleMedium)
            if (file == null || busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            else if (file.category == null)
                Text(
                    stringResource(R.string.error_bad_media),
                    color = MaterialTheme.colorScheme.error,
                )
            else {
                Text(
                    stringResource(R.string.home_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                FileActions.actions(file.category).forEach { action ->
                    val label =
                        when (action) {
                            FileAction.SLIDESHOW -> R.string.slideshow_title
                            FileAction.IMAGE -> R.string.compress_image
                            FileAction.PHOTO_PDF -> R.string.photos_to_pdf
                            FileAction.PRESENTATION_IMAGE -> R.string.add_to_presentation
                            FileAction.VIDEO -> R.string.edit_video
                            FileAction.AUDIO -> R.string.audio_tool
                            FileAction.FRAME -> R.string.video_frame
                            FileAction.PDF_READER -> R.string.read_pdf
                            FileAction.PDF_PAGES -> R.string.pdf_pages
                            FileAction.PDF_IMAGE -> R.string.reader_page_image
                            FileAction.DOCUMENTS -> R.string.read_documents
                        }
                    OutlinedButton(
                        onClick = { onAction(action) },
                        modifier =
                            Modifier.fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("file-action-${action.name}"),
                    ) {
                        Text(stringResource(label))
                    }
                }
            }
        }
    }
}

package com.example.contentstudio.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.contentstudio.R
import com.example.contentstudio.data.*
import kotlinx.coroutines.launch

data class ResumeWork(val kind: String, val name: String, val glyph: StudioGlyph)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeScreen(
    projects: List<StudioProject>,
    onOpen: (StudioProject) -> Unit,
    onCompressImage: () -> Unit,
    onCompressVideo: () -> Unit,
    onEditVideo: () -> Unit,
    onNew: (ProjectKind) -> Unit,
    onPhotosPdf: () -> Unit,
    onReadPdf: () -> Unit,
    onPdfPages: () -> Unit,
    onReadDocuments: () -> Unit,
    onAudio: () -> Unit,
    onOpenFile: () -> Unit,
    sessions: List<ResumeWork> = emptyList(),
    onResume: (String) -> Unit = {},
    onSlideshow: () -> Unit = {},
    onDeleteProject: suspend (String) -> Boolean = { false },
    onClearWork: suspend (String) -> Boolean = { false },
    sessionsLoaded: Boolean = true,
    onBatchImages: (() -> Unit)? = null,
    onBatchVideo: (() -> Unit)? = null,
    onBatchAudio: (() -> Unit)? = null,
) {
    var menu by rememberSaveable { mutableStateOf<String?>(null) }
    var all by rememberSaveable { mutableStateOf(false) }
    var actionId by rememberSaveable { mutableStateOf<String?>(null) }
    var actionName by rememberSaveable { mutableStateOf("") }
    var projectAction by rememberSaveable { mutableStateOf(false) }
    var confirming by rememberSaveable { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun manage(id: String, name: String, project: Boolean) {
        actionId = id; actionName = name; projectAction = project
        confirming = false; failed = false
    }
    LaunchedEffect(actionId, projects, sessions, sessionsLoaded) {
        actionId?.let { id ->
            if (if (projectAction) projects.none { it.id == id }
                else sessionsLoaded && sessions.none { it.kind == id }) actionId = null
        }
    }
    val recent = remember(projects) { projects.sortedByDescending { it.updatedAt } }
    BoxWithConstraints(Modifier.widthIn(max = 1100.dp).fillMaxSize()) {
        val wide = maxWidth >= 840.dp && LocalDensity.current.fontScale <= 1.3f
        LazyColumn(
            Modifier.fillMaxSize().testTag(if (wide) "wide-home-tools" else "compact-home"),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "start") {
                Text(
                    stringResource(R.string.home_start),
                    modifier = Modifier.accessibleHeading(),
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onOpenFile,
                    modifier =
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("home-open-file"),
                ) {
                    Text(stringResource(R.string.open_file))
                }
                TextButton(onClick = { menu = "new" }, modifier = Modifier.testTag("home-new")) {
                    Text(stringResource(R.string.new_create))
                }
                onBatchImages?.let {
                    TextButton(onClick = { menu = "batch" }, modifier = Modifier.testTag("home-batch-tools")) {
                        Text(stringResource(R.string.batch_tools))
                    }
                }
            }
            item(key = "categories") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CategoryRow(
                        stringResource(R.string.short_images),
                        stringResource(R.string.photo_tool_subtitle),
                        StudioGlyph.IMAGE,
                        "compress-image-action",
                        onCompressImage,
                    )
                    CategoryRow(
                        stringResource(R.string.video),
                        stringResource(R.string.edit_tool_subtitle),
                        StudioGlyph.VIDEO,
                        "home-edit-video",
                        onEditVideo,
                    )
                    CategoryRow(
                        stringResource(R.string.audio_category),
                        stringResource(R.string.audio_subtitle),
                        StudioGlyph.AUDIO,
                        "home-audio",
                        onAudio,
                    )
                    CategoryRow(
                        stringResource(R.string.documents_category),
                        stringResource(R.string.documents_subtitle),
                        StudioGlyph.SLIDES,
                        "home-documents",
                        { menu = "documents" },
                    )
                    CategoryRow(
                        stringResource(R.string.presentation),
                        stringResource(R.string.presentation_hint),
                        StudioGlyph.SLIDES,
                        "home-presentation",
                        { menu = "presentation" },
                    )
                }
            }
            item(key = "resume-title") {
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.resume_work),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f).accessibleHeading(),
                    )
                    if (sessions.size + recent.size > 3)
                        TextButton(
                            onClick = { all = !all },
                            modifier = Modifier.testTag("home-all-work"),
                        ) {
                            Text(stringResource(if (all) R.string.close else R.string.view_all))
                        }
                }
                if (sessions.isEmpty() && recent.isEmpty())
                    Text(
                        stringResource(R.string.empty_projects),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
            }
            val visibleSessions = if (all) sessions else sessions.take(3)
            items(visibleSessions, key = { "session-${it.kind}" }) { work ->
                CategoryRow(
                    work.name,
                    stringResource(when (work.kind) {
                        "video-compression" -> R.string.video
                        "audio-conversion" -> R.string.audio_category
                        "photo-pdf" -> R.string.photos_to_pdf
                        "batch-images" -> R.string.batch_images
                        "batch-video" -> R.string.batch_video
                        "batch-audio" -> R.string.batch_audio
                        else -> R.string.slideshow_title
                    }),
                    work.glyph,
                    "resume-${work.kind}",
                    { onResume(work.kind) },
                    onManage = { manage(work.kind, work.name, false) },
                )
            }
            items(
                if (all) recent else recent.take((3 - visibleSessions.size).coerceAtLeast(0)),
                key = { "project-${it.id}" },
            ) {
                ProjectCard(it, onOpen) { manage(it.id, it.title, true) }
            }
            item(key = "help") { WorkspaceHelp { SupportedFilesButton() } }
        }
    }
    menu?.let { chosen ->
        ModalBottomSheet(onDismissRequest = { menu = null }) {
            LazyColumn(
                Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (chosen == "batch") {
                    listOf(Triple(onBatchImages, R.string.batch_images, "home-batch-images"),
                        Triple(onBatchVideo, R.string.batch_video, "home-batch-video"),
                        Triple(onBatchAudio, R.string.batch_audio, "home-batch-audio")).forEach { (action, label, tag) ->
                        if (action != null) item { TextButton(onClick = { menu = null; action() }, modifier = Modifier.testTag(tag)) {
                            Text(stringResource(label)) } }
                    }
                } else if (chosen != "documents") {
                    item {
                        TextButton(
                            onClick = {
                                menu = null
                                onNew(ProjectKind.PRESENTATION)
                            },
                            modifier = Modifier.testTag("home-new-presentation"),
                        ) {
                            Text(stringResource(R.string.create_presentation))
                        }
                    }
                    if (chosen == "new")
                        item {
                            TextButton(
                                onClick = {
                                    menu = null
                                    onPhotosPdf()
                                },
                                modifier = Modifier.testTag("home-photo-pdf"),
                            ) {
                                Text(stringResource(R.string.photos_to_pdf))
                            }
                        }
                    if (chosen == "new")
                        item {
                            TextButton(
                                onClick = {
                                    menu = null
                                    onSlideshow()
                                },
                                modifier = Modifier.testTag("home-slideshow"),
                            ) {
                                Text(stringResource(R.string.slideshow_title))
                            }
                        }
                    if (chosen == "presentation")
                        items(
                            recent.filter { it.kind == ProjectKind.PRESENTATION },
                            key = { it.id },
                        ) {
                            ProjectCard(it, { project ->
                                menu = null
                                onOpen(project)
                            }) { menu = null; manage(it.id, it.title, true) }
                        }
                } else {
                    item {
                        TextButton(
                            onClick = {
                                menu = null
                                onReadPdf()
                            },
                            modifier = Modifier.testTag("home-read-pdf"),
                        ) {
                            Text(stringResource(R.string.read_pdf))
                        }
                    }
                    item {
                        TextButton(
                            onClick = {
                                menu = null
                                onPdfPages()
                            },
                            modifier = Modifier.testTag("home-pdf-pages"),
                        ) {
                            Text(stringResource(R.string.pdf_pages))
                        }
                    }
                    item {
                        TextButton(
                            onClick = {
                                menu = null
                                onReadDocuments()
                            },
                            modifier = Modifier.testTag("home-read-documents"),
                        ) {
                            Text(stringResource(R.string.read_documents))
                        }
                    }
                    item {
                        TextButton(
                            onClick = {
                                menu = null
                                onPhotosPdf()
                            },
                            modifier = Modifier.testTag("home-photo-pdf"),
                        ) {
                            Text(stringResource(R.string.photos_to_pdf))
                        }
                    }
                }
                item {
                    TextButton(onClick = { menu = null }) { Text(stringResource(R.string.close)) }
                }
            }
        }
    }
    actionId?.let { id ->
        val label = stringResource(if (projectAction) R.string.delete_project else R.string.clear_session)
        AlertDialog(
            onDismissRequest = { if (!removing) actionId = null },
            title = { Text(if (confirming) label else actionName) },
            text = {
                if (confirming) Column {
                    Text(stringResource(if (projectAction) R.string.delete_project_confirmation
                        else R.string.clear_work_confirmation, actionName))
                    if (failed) Text(stringResource(if (projectAction) R.string.save_error else R.string.clear_work_error), color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                TextButton(enabled = !removing, onClick = {
                    if (!confirming) confirming = true
                    else {
                        removing = true; failed = false
                        scope.launch {
                            try {
                                if (if (projectAction) onDeleteProject(id) else onClearWork(id)) actionId = null
                                else failed = true
                            } finally { removing = false }
                        }
                    }
                }, modifier = Modifier.testTag(if (confirming) "work-delete-confirm" else "work-delete-request")) {
                    Text(if (removing) stringResource(R.string.processing) else label)
                }
            },
            dismissButton = {
                TextButton(enabled = !removing, onClick = { actionId = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun CategoryRow(
    title: String,
    subtitle: String,
    glyph: StudioGlyph,
    tag: String,
    onClick: () -> Unit,
    onManage: (() -> Unit)? = null,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(tag),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StudioIcon(glyph, MaterialTheme.colorScheme.tertiary)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StudioIcon(StudioGlyph.FORWARD, MaterialTheme.colorScheme.onSurfaceVariant)
            if (onManage != null) {
                val actionDescription = stringResource(R.string.work_actions, title)
                TextButton(onClick = onManage, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .testTag("$tag-manage").semantics { contentDescription = actionDescription }) {
                    Text("⋮", style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}

@Composable
private fun ProjectCard(project: StudioProject, onOpen: (StudioProject) -> Unit, onManage: (() -> Unit)? = null) {
    CategoryRow(
        project.title.ifBlank { stringResource(R.string.presentation) },
        stringResource(
            if (project.kind == ProjectKind.PRESENTATION) R.string.presentation else R.string.video
        ),
        if (project.kind == ProjectKind.PRESENTATION) StudioGlyph.SLIDES else StudioGlyph.VIDEO,
        "project-${project.id}",
        { onOpen(project) },
        onManage,
    )
}

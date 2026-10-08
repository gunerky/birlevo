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
import androidx.compose.ui.unit.dp
import com.example.contentstudio.R
import com.example.contentstudio.data.*

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
) {
    var menu by rememberSaveable { mutableStateOf<String?>(null) }
    var all by rememberSaveable { mutableStateOf(false) }
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
                        modifier = Modifier.weight(1f),
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
                    stringResource(R.string.resume_work),
                    work.glyph,
                    "resume-${work.kind}",
                    { onResume(work.kind) },
                )
            }
            items(
                if (all) recent else recent.take((3 - visibleSessions.size).coerceAtLeast(0)),
                key = { "project-${it.id}" },
            ) {
                ProjectCard(it, onOpen)
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
                if (chosen != "documents") {
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
                            ProjectCard(it) { project ->
                                menu = null
                                onOpen(project)
                            }
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
}

@Composable
private fun CategoryRow(
    title: String,
    subtitle: String,
    glyph: StudioGlyph,
    tag: String,
    onClick: () -> Unit,
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
        }
    }
}

@Composable
private fun ProjectCard(project: StudioProject, onOpen: (StudioProject) -> Unit) {
    CategoryRow(
        project.title.ifBlank { stringResource(R.string.presentation) },
        stringResource(
            if (project.kind == ProjectKind.PRESENTATION) R.string.presentation else R.string.video
        ),
        if (project.kind == ProjectKind.PRESENTATION) StudioGlyph.SLIDES else StudioGlyph.VIDEO,
        "project-${project.id}",
        { onOpen(project) },
    )
}

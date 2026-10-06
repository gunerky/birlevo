package com.example.contentstudio.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.contentstudio.data.*
import com.example.contentstudio.ui.StudioHero
import com.example.contentstudio.ui.StudioGlyph
import com.example.contentstudio.ui.StudioIcon
import com.example.contentstudio.ui.IconBadge

import com.example.contentstudio.R
@Composable
internal fun HomeScreen(projects: List<StudioProject>, onOpen: (StudioProject) -> Unit,
    onCompressImage: () -> Unit, onCompressVideo: () -> Unit, onEditVideo: () -> Unit,
    onNew: (ProjectKind) -> Unit, onPhotosPdf: () -> Unit, onReadPdf: () -> Unit,
    onPdfPages: () -> Unit, onReadDocuments: () -> Unit, onAudio: () -> Unit) {
    BoxWithConstraints(Modifier.widthIn(max = 1100.dp).fillMaxSize().padding(horizontal = 20.dp)) {
        val wide = maxWidth >= 700.dp
        val fontScale = LocalDensity.current.fontScale
        val compactGrid = maxWidth >= 340.dp && fontScale <= 1.3f
        val wideGrid = (maxWidth - 32.dp) / 2 >= 340.dp && fontScale <= 1.3f
        if (wide) Row(Modifier.fillMaxSize().testTag("wide-home"), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            LazyColumn(Modifier.weight(1f).testTag("wide-home-tools"), contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item(key = "hero") { StudioHero(onCompressImage, onEditVideo, { onNew(ProjectKind.PRESENTATION) }); Spacer(Modifier.height(12.dp)) }
                projectActions(wideGrid, onNew, onCompressImage, onCompressVideo, onEditVideo, onPhotosPdf, onReadPdf, onPdfPages, onReadDocuments, onAudio)
            }
            ProjectList(projects, onOpen, Modifier.weight(1f))
        } else {
            LazyColumn(Modifier.fillMaxSize().testTag("compact-home"),
                contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item(key = "hero") { StudioHero(onCompressImage, onEditVideo, { onNew(ProjectKind.PRESENTATION) }); Spacer(Modifier.height(12.dp)) }
                projectActions(compactGrid, onNew, onCompressImage, onCompressVideo, onEditVideo, onPhotosPdf, onReadPdf, onPdfPages, onReadDocuments, onAudio)
                item(key = "projects-title") { Spacer(Modifier.height(16.dp)); Text(stringResource(R.string.projects), style = MaterialTheme.typography.titleLarge) }
                if (projects.isEmpty()) item(key = "empty") { EmptyProjects() }
                items(projects, key = { "project-${it.id}" }, contentType = { "project" }) { ProjectCard(it, onOpen) }
            }
        }
    }
}

private fun LazyListScope.projectActions(grid: Boolean, onNew: (ProjectKind) -> Unit, onCompressImage: () -> Unit,
    onCompressVideo: () -> Unit, onEditVideo: () -> Unit, onPhotosPdf: () -> Unit, onReadPdf: () -> Unit, onPdfPages: () -> Unit, onReadDocuments: () -> Unit, onAudio: () -> Unit) {
    item(key = "tools-title", contentType = "title") {
        Text(stringResource(R.string.tools_title), style = MaterialTheme.typography.titleMedium)
    }
    item(key = "supported-files", contentType = "help") { SupportedFilesButton() }
    val actions = listOf(
        Triple(R.string.create_presentation, R.string.presentation_hint, StudioGlyph.SLIDES),
        Triple(R.string.edit_video, R.string.edit_tool_subtitle, StudioGlyph.TRIM),
        Triple(R.string.compress_image, R.string.photo_tool_subtitle, StudioGlyph.IMAGE),
        Triple(R.string.compress_video, R.string.video_tool_subtitle, StudioGlyph.VIDEO),
        Triple(R.string.photos_to_pdf, R.string.photo_pdf_subtitle, StudioGlyph.SLIDES),
        Triple(R.string.read_pdf, R.string.reader_subtitle, StudioGlyph.SLIDES),
        Triple(R.string.pdf_pages, R.string.pdf_pages_subtitle, StudioGlyph.SLIDES),
        Triple(R.string.read_documents, R.string.documents_subtitle, StudioGlyph.SLIDES),
        Triple(R.string.audio_tool, R.string.audio_subtitle, StudioGlyph.AUDIO),
    )
    val callbacks = listOf({ onNew(ProjectKind.PRESENTATION) }, onEditVideo, onCompressImage, onCompressVideo, onPhotosPdf, onReadPdf, onPdfPages, onReadDocuments, onAudio)
    val tags = listOf("home-presentation", "home-edit-video", "compress-image-action", "home-compress-video",
        "home-photo-pdf", "home-read-pdf", "home-pdf-pages", "home-read-documents", "home-audio")
    actions.indices.toList().chunked(if (grid) 2 else 1).forEach { indices ->
        item(key = "action-row-${indices.first()}", contentType = "action-row") {
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                indices.forEach { index ->
                    val (title, subtitle, glyph) = actions[index]
                    ActionCard(stringResource(title), stringResource(subtitle), glyph, callbacks[index],
                        Modifier.weight(1f).fillMaxHeight().testTag(tags[index]))
                }
            }
        }
    }
}

@Composable
private fun ActionCard(title: String, subtitle: String, glyph: StudioGlyph, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconBadge(glyph)
                Spacer(Modifier.weight(1f))
                StudioIcon(StudioGlyph.FORWARD, MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ProjectList(projects: List<StudioProject>, onOpen: (StudioProject) -> Unit, modifier: Modifier) {
    LazyColumn(modifier, contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "title") { Text(stringResource(R.string.projects), style = MaterialTheme.typography.titleLarge) }
        if (projects.isEmpty()) item(key = "empty") { EmptyProjects() }
        items(projects, key = { "project-${it.id}" }, contentType = { "project" }) { ProjectCard(it, onOpen) }
    }
}

@Composable
private fun EmptyProjects() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBadge(StudioGlyph.SLIDES)
            Text(stringResource(R.string.empty_projects_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.empty_projects), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ProjectCard(project: StudioProject, onOpen: (StudioProject) -> Unit) {
        Card(onClick = { onOpen(project) }, modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                IconBadge(if (project.kind == ProjectKind.PRESENTATION) StudioGlyph.SLIDES else StudioGlyph.VIDEO)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(project.title.ifBlank {
                stringResource(if (project.kind == ProjectKind.PRESENTATION) R.string.presentation else R.string.video)
                    }, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(if (project.kind == ProjectKind.PRESENTATION) R.string.presentation else R.string.video),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                StudioIcon(StudioGlyph.FORWARD, MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
}

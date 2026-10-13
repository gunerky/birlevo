package com.example.contentstudio

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull

private fun ComposeTestRule.exists(tag: String) = onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
private fun ComposeTestRule.homeList() = onNode(hasTestTag("compact-home") or hasTestTag("wide-home-tools"))

/** Explicitly follow the new category menus; do not inject navigation or domain state. */
fun ComposeTestRule.homeTool(tag: String): SemanticsNodeInteraction {
    val parent = when (tag) {
        "home-new-presentation", "home-photo-pdf" -> "home-new"
        "home-read-pdf", "home-pdf-pages", "home-read-documents" -> "home-documents"
        else -> null
    }
    if (parent != null && !exists(tag)) {
        homeList().performScrollToNode(hasTestTag(parent))
        onNodeWithTag(parent).performClick()
    } else if (parent == null) homeList().performScrollToNode(hasTestTag(tag))
    return onNodeWithTag(tag)
}

fun ComposeTestRule.homeNode(text: String): SemanticsNodeInteraction {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val entries = listOf(
        R.string.create_presentation to "home-new-presentation", R.string.photos_to_pdf to "home-photo-pdf",
        R.string.read_pdf to "home-read-pdf", R.string.pdf_pages to "home-pdf-pages",
        R.string.read_documents to "home-read-documents", R.string.compress_image to "compress-image-action",
        R.string.edit_video to "home-edit-video", R.string.compress_video to "home-edit-video",
        R.string.audio_tool to "home-audio")
    entries.firstOrNull { context.getString(it.first) == text }?.let { return homeTool(it.second) }
    homeList().performScrollToNode(hasText(text))
    return onNodeWithText(text)
}

fun ComposeTestRule.projectNode(title: String): SemanticsNodeInteraction {
    waitUntil(15_000) { exists("compact-home") || exists("wide-home-tools") }
    val viewAll = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.view_all)
    if (onAllNodes(hasTestTag("home-all-work") and hasText(viewAll)).fetchSemanticsNodes().isNotEmpty()) homeTool("home-all-work").performClick()
    val project = hasText(title) and SemanticsMatcher("project row") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("project-") == true }
    homeList().performScrollToNode(project)
    return onNode(project)
}

/** Open only the requested inspector. Footer actions first dismiss a phone inspector. */
fun ComposeTestRule.workspaceNode(tag: String): SemanticsNodeInteraction {
    val footer = tag.endsWith("-back") || tag.startsWith("slide-design-preview-") || tag in setOf("process-video", "image-preview", "audio-create", "generate-photo-pdf",
        "pages-create", "save-project", "preview-pdf", "add-slide", "document-create", "save-pdf", "video-frame-open",
        "play-source-video", "play-output-video", "play-source-audio", "play-output-audio", "reader-save-image",
        "open-output", "share-output", "add-output-to-presentation", "add-output-to-photo-pdf", "add-output-to-audio") ||
        tag.startsWith("export-") || tag.startsWith("save-compressed") || tag.startsWith("audio-save") || tag.startsWith("output-") ||
        tag in setOf("pages-save", "pages-read", "document-save", "document-read-pdf", "save-photo-pdf")
    fun closePanel() {
        listOf("project", "video-compression", "compression", "audio", "reader", "photo-pdf", "pages", "document").forEach {
            val close = "$it-panel-close"
            if (exists(close)) onNodeWithTag(close).performClick()
        }
    }
    if (footer && !tag.endsWith("-confirm")) closePanel()
    if (!exists(tag)) {
        Regex("pages-(?:up|down|rotate|remove|show)-(\\d+)").matchEntire(tag)?.groupValues?.get(1)?.let { page ->
            onNodeWithTag("pages-list").performScrollToNode(hasTestTag("pages-item-$page"))
            onNodeWithTag("pages-item-$page").performClick()
        }
        val panel = when {
            tag.startsWith("video-text-") -> "video-compression-panel-text"
            tag.startsWith("video-image-") -> "video-compression-panel-image"
            tag.startsWith("video-audio-") || tag == "video-mix-profile" -> "video-compression-panel-audio"
            tag.startsWith("video-trim-") || tag.startsWith("preview-video-trim") || tag.startsWith("video-add-") || tag.startsWith("video-remove-") || tag == "video-swap" -> "video-compression-panel-clips"
            tag.startsWith("video-canvas-") || tag.startsWith("video-fit-") || tag.startsWith("video-speed-") || tag == "video-output-duration" -> "video-compression-panel-canvas"
            tag.startsWith("video-format-") || tag.startsWith("video-quality-") || tag.startsWith("video-edge-") || tag.startsWith("video-output-") || tag in setOf("video-target-loss", "video-size-risk") -> "video-compression-panel-output"
            tag.startsWith("image-format-") || tag == "image-quality" || tag.startsWith("edge-") -> "compression-panel-output"
            tag.startsWith("audio-target-") || tag.startsWith("audio-rate-") || tag.startsWith("audio-profile-") || tag.startsWith("audio-channels-") || tag == "audio-output-issue" -> "audio-panel-output"
            tag == "audio-add" || tag == "audio-swap" || tag.startsWith("audio-trim-") || tag.startsWith("audio-remove-") || tag.startsWith("audio-source") -> "audio-panel-clips"
            tag.startsWith("reader-format-") || tag.startsWith("reader-edge-") || tag == "reader-quality" || tag == "reader-create-image" -> "reader-panel-image"
            tag in setOf("reader-page-input", "reader-go") -> "reader-panel-pages"
            tag == "reader-save-copy" -> "reader-panel-copy"
            tag in setOf("pages-range", "pages-select", "pages-undo") -> "pages-panel-selection"
            tag.startsWith("photo-pdf-edge-") -> "photo-pdf-panel-output"
            tag.startsWith("document-encoding-") || tag.startsWith("document-kind-") || tag.startsWith("document-separator-") -> "document-panel-input"
            tag.startsWith("document-target-") || tag == "document-loss" -> "document-panel-output"
            else -> null
        }
        if (panel != null && exists(panel)) { closePanel(); onNodeWithTag(panel).performScrollTo().performClick() }
        if (tag in setOf("open-output", "share-output", "add-output-to-presentation", "add-output-to-photo-pdf", "add-output-to-audio") && exists("output-more")) {
            onNodeWithTag("output-more").performClick()
        }
    }
    return onNodeWithTag(tag)
}

fun ComposeTestRule.editorNode(tag: String): SemanticsNodeInteraction {
    if (!exists(tag)) {
        if (tag == "project-title" && exists("project-panel-info")) {
            if (exists("project-panel-close")) onNodeWithTag("project-panel-close").performClick()
            onNodeWithTag("project-panel-info").performScrollTo().performClick()
        } else if ((tag.startsWith("theme-") || tag == "presentation-theme") && exists("project-panel-theme")) {
            if (exists("project-panel-close")) onNodeWithTag("project-panel-close").performClick()
            onNodeWithTag("project-panel-theme").performScrollTo().performClick()
        }
        else {
            val slide = Regex("(?:slide-(?:heading|body|image|actions|layout)|choose-slide-image|remove-slide-image)-(\\d+)(?:-.*)?").matchEntire(tag)?.groupValues?.get(1)
            // Close an old inspector before selecting a different thumbnail.
            if (exists("project-panel-close")) onNodeWithTag("project-panel-close").performClick()
            if (slide != null && exists("project-slides")) {
                onNodeWithTag("project-slides").performScrollToNode(hasTestTag("select-slide-$slide"))
                onNodeWithTag("select-slide-$slide").performClick()
            }
            if (exists("project-panel-edit")) onNodeWithTag("project-panel-edit").performClick()
        }
    }
    return workspaceNode(tag)
}

fun ComposeTestRule.photoNode(tag: String): SemanticsNodeInteraction {
    if (tag in setOf("generate-photo-pdf", "save-photo-pdf", "share-output", "open-output", "output-more") || tag.startsWith("photo-pdf-edge-")) return workspaceNode(tag)
    if (!exists(tag)) {
        val page = Regex("photo-(?:up|down|remove)-(.+)").matchEntire(tag)?.groupValues?.get(1)
        val target = if (page != null) "photo-page-$page" else tag
        onNodeWithTag("photo-pdf-list").performScrollToNode(hasTestTag(target))
        if (page != null) onNodeWithTag(target).performClick()
    }
    return workspaceNode(tag)
}

package com.example.contentstudio.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.contentstudio.R

data class WorkspacePanel(val id: String, val label: String)

/** Presentation only: panels never own, reset or encode domain state. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ToolWorkspace(
    title: String,
    tag: String,
    onClose: () -> Unit,
    panels: List<WorkspacePanel> = emptyList(),
    initialPanel: String? = null,
    resultKey: String? = null,
    embedded: Boolean = false,
    actions: @Composable ColumnScope.(String?) -> Unit = {},
    panelContent: @Composable ColumnScope.(String) -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    var selected by rememberSaveable(tag) { mutableStateOf(initialPanel) }
    var previousResult by rememberSaveable(tag) { mutableStateOf(resultKey) }
    LaunchedEffect(resultKey) {
        if (resultKey != null && resultKey != previousResult) selected = null
        if (resultKey != null) previousResult = resultKey
    }
    val active = selected?.takeIf { id -> panels.any { it.id == id } }
    fun back() {
        if (active != null) selected = null else onClose()
    }
    BackHandler(enabled = !embedded || active != null) { back() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp && LocalDensity.current.fontScale <= 1.3f
        val panelHeight = maxHeight * .9f
        @Composable
        fun Footer() {
            Surface(shadowElevation = 2.dp, color = MaterialTheme.colorScheme.surface) {
                Column(
                    Modifier.fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    actions(active)
                }
            }
        }
        Scaffold(
            contentWindowInsets =
                if (embedded) WindowInsets(0)
                else WindowInsets.systemBars.union(WindowInsets.displayCutout),
            topBar = {
                if (!embedded)
                    TopAppBar(
                        windowInsets =
                            WindowInsets.systemBars
                                .union(WindowInsets.displayCutout)
                                .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
                        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        navigationIcon = {
                            TextButton(onClick = ::back, modifier = Modifier.testTag("$tag-back")) {
                                Text(stringResource(R.string.back))
                            }
                        },
                        colors =
                            TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background
                            ),
                    )
            },
            bottomBar = {
                if (wide || active == null)
                    Box(if (embedded) Modifier else Modifier.navigationBarsPadding().imePadding()) {
                        Footer()
                    }
            },
        ) { insets ->
            Column(Modifier.fillMaxSize().padding(insets)) {
                if (panels.isNotEmpty())
                    Row(
                        Modifier.fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp)
                            .testTag("$tag-panel-list"),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        panels.forEach { panel ->
                            TextButton(
                                onClick = { selected = if (active == panel.id) null else panel.id },
                                modifier =
                                    Modifier.heightIn(min = 48.dp)
                                        .testTag("$tag-panel-${panel.id}"),
                                colors =
                                    ButtonDefaults.textButtonColors(
                                        contentColor =
                                            if (active == panel.id)
                                                MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface
                                    ),
                            ) {
                                Text(panel.label, maxLines = 1)
                            }
                        }
                    }
                Row(
                    Modifier.weight(1f)
                        .fillMaxWidth()
                        .testTag(if (wide) "$tag-workspace-wide" else "$tag-workspace-compact")
                ) {
                    Box(Modifier.weight(1f).fillMaxHeight()) { content(PaddingValues(16.dp)) }
                    if (wide && active != null)
                        Surface(
                            Modifier.width(360.dp).fillMaxHeight(),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            Column(
                                Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(
                                    panels.first { it.id == active }.label,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                panelContent(active)
                            }
                        }
                }
            }
        }
        if (!wide && active != null)
            ModalBottomSheet(
                onDismissRequest = { selected = null },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            ) {
                Column(Modifier.fillMaxWidth().heightIn(max = panelHeight).imePadding()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            panels.first { it.id == active }.label,
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        TextButton(
                            onClick = { selected = null },
                            modifier = Modifier.testTag("$tag-panel-close"),
                        ) {
                            Text(stringResource(R.string.close))
                        }
                    }
                    Column(
                        Modifier.weight(1f, fill = false)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        panelContent(active)
                    }
                    Footer()
                }
            }
    }
}

@Composable
fun SourceHeader(
    name: String,
    detail: String,
    enabled: Boolean,
    tag: String,
    actionLabel: String? = null,
    onChange: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail.isNotBlank())
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
        }
        TextButton(onClick = onChange, enabled = enabled, modifier = Modifier.testTag(tag)) {
            Text(actionLabel ?: stringResource(R.string.change_file))
        }
    }
}

@Composable
fun WorkspaceHelp(content: @Composable ColumnScope.() -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { open = !open }) { Text(stringResource(R.string.workspace_help)) }
    if (open) Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

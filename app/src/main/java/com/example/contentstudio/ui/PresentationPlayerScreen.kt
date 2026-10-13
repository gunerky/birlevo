package com.example.contentstudio.ui

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.*
import androidx.lifecycle.*
import androidx.lifecycle.compose.*
import com.example.contentstudio.R
import com.example.contentstudio.data.*
import com.example.contentstudio.media.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PresentationPlayerScreen(
    project: StudioProject,
    model: PresentationPlayerViewModel,
    onClose: () -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val preview = remember(state.preview) { state.preview?.asImageBitmap() }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var controls by rememberSaveable(project.id) { mutableStateOf(true) }
    var notes by rememberSaveable(project.id) { mutableStateOf(false) }
    var reset by rememberSaveable(project.id) { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(project) { model.prepare(project) }
    LaunchedEffect(Unit) { focus.requestFocus() }
    DisposableEffect(lifecycle, model) {
        val window = (context as Activity).window
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        val insets = ViewCompat.getRootWindowInsets(window.decorView)
        val statusVisible = insets?.isVisible(WindowInsetsCompat.Type.statusBars()) ?: true
        val navVisible = insets?.isVisible(WindowInsetsCompat.Type.navigationBars()) ?: true
        val behavior = controller.systemBarsBehavior
        val keep = window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
        fun restoreWindow() {
            if (!keep) window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            controller.systemBarsBehavior = behavior
            if (statusVisible) controller.show(WindowInsetsCompat.Type.statusBars())
            else controller.hide(WindowInsetsCompat.Type.statusBars())
            if (navVisible) controller.show(WindowInsetsCompat.Type.navigationBars())
            else controller.hide(WindowInsetsCompat.Type.navigationBars())
        }
        fun enter() {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) enter()
            if (event == Lifecycle.Event.ON_STOP) {
                model.pause()
                restoreWindow()
            }
        }
        lifecycle.addObserver(observer)
        enter()
        onDispose {
            lifecycle.removeObserver(observer)
            model.leave()
            restoreWindow()
        }
    }
    fun close() {
        model.leave()
        onClose()
    }
    fun back() {
        if (notes) notes = false else if (!controls) controls = true else close()
    }
    BackHandler { back() }
    Column(
        Modifier.fillMaxSize()
            .background(Color.Black)
            .safeDrawingPadding()
            .focusRequester(focus)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) false
                else
                    when (event.key) {
                        Key.DirectionRight,
                        Key.PageDown,
                        Key.Spacebar -> {
                            model.select(state.index + 1)
                            true
                        }
                        Key.DirectionLeft,
                        Key.PageUp -> {
                            model.select(state.index - 1)
                            true
                        }
                        Key.MoveHome -> {
                            model.select(0)
                            true
                        }
                        Key.MoveEnd -> {
                            model.select(project.slides.lastIndex)
                            true
                        }
                        Key.Escape -> {
                            back()
                            true
                        }
                        else -> false
                    }
            }
            .focusable()
            .testTag("present-screen")
    ) {
        if (controls)
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(::close, Modifier.testTag("present-close")) {
                        Text(stringResource(R.string.back))
                    }
                    TextButton({ notes = !notes }, Modifier.testTag("present-notes-toggle")) {
                        Text(stringResource(R.string.present_notes))
                    }
                    TextButton(
                        {
                            controls = false
                            notes = false
                        },
                        Modifier.testTag("present-hide-controls"),
                    ) {
                        Text(stringResource(R.string.present_controls))
                    }
                    Text(
                        presentationClockLabel(state.elapsedMs),
                        Modifier.align(Alignment.CenterVertically).testTag("present-clock"),
                    )
                    TextButton(model::toggleTimer, Modifier.testTag("present-timer-toggle")) {
                        Text(
                            stringResource(
                                if (state.running) R.string.present_pause
                                else R.string.present_start
                            )
                        )
                    }
                    TextButton({ reset = true }, Modifier.testTag("present-timer-reset")) {
                        Text(stringResource(R.string.present_reset))
                    }
                }
            }
        Box(
            Modifier.weight(1f)
                .fillMaxWidth()
                .clickable(
                    onClickLabel =
                        stringResource(
                            if (controls) R.string.present_controls
                            else R.string.present_show_controls
                        )
                ) {
                    controls = !controls
                    if (!controls) notes = false
                }
                .testTag("present-slide"),
            contentAlignment = Alignment.Center,
        ) {
            preview?.let {
                Image(
                    it,
                    stringResource(R.string.slide_number, state.index + 1),
                    Modifier.fillMaxSize(),
                )
            }
            if (state.busy) CircularProgressIndicator()
            if (state.error)
                Surface {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.present_error),
                            Modifier.testTag("present-error"),
                        )
                        TextButton(model::retry) { Text(stringResource(R.string.retry)) }
                    }
                }
        }
        if (controls && notes)
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Text(
                    project.slides.getOrNull(state.index)?.notes?.ifBlank {
                        stringResource(R.string.present_no_notes)
                    } ?: stringResource(R.string.present_no_notes),
                    Modifier.fillMaxWidth()
                        .heightIn(max = 160.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                        .testTag("present-notes"),
                )
            }
        if (controls)
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    Text(
                        "${state.index + 1} / ${project.slides.size}",
                        Modifier.align(Alignment.CenterHorizontally).testTag("present-page"),
                    )
                    FlowRow(
                        Modifier.fillMaxWidth(),
                        maxItemsInEachRow = if (LocalDensity.current.fontScale > 1.3f) 1 else 2,
                    ) {
                        TextButton(
                            { model.select(state.index - 1) },
                            enabled = state.index > 0,
                            modifier = Modifier.weight(1f).testTag("present-prev"),
                        ) {
                            Text(stringResource(R.string.pdf_previous))
                        }
                        TextButton(
                            { model.select(state.index + 1) },
                            enabled = state.index < project.slides.lastIndex,
                            modifier = Modifier.weight(1f).testTag("present-next"),
                        ) {
                            Text(stringResource(R.string.pdf_next))
                        }
                    }
                }
            }
    }
    if (reset)
        AlertDialog(
            onDismissRequest = { reset = false },
            title = { Text(stringResource(R.string.present_reset)) },
            confirmButton = {
                TextButton(
                    {
                        reset = false
                        model.resetTimer()
                    },
                    Modifier.testTag("present-confirm-reset"),
                ) {
                    Text(stringResource(R.string.present_reset))
                }
            },
            dismissButton = {
                TextButton({ reset = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
}

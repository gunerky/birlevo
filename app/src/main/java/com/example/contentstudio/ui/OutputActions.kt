package com.example.contentstudio.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.contentstudio.R
import com.example.contentstudio.data.ProjectKind
import com.example.contentstudio.data.SlideOperations
import com.example.contentstudio.data.StudioProject
import com.example.contentstudio.media.MediaOutput
import com.example.contentstudio.media.MediaOutputs
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The same approval and read-only sharing contract applies to images, video and generated PDF. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OutputActions(file: File, mime: String, name: String, enabled: Boolean, sourceBytes: Long? = null,
    presentations: List<StudioProject> = emptyList(),
    onAddToPresentation: (suspend (Uri, String?) -> Boolean)? = null,
    onAddToPhotoPdf: (suspend (Uri) -> Boolean)? = null) {
    key(file.path) { OutputActionsForResult(file, mime, name, enabled, sourceBytes, presentations, onAddToPresentation, onAddToPhotoPdf) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OutputActionsForResult(file: File, mime: String, name: String, enabled: Boolean, sourceBytes: Long?,
    presentations: List<StudioProject>, onAddToPresentation: (suspend (Uri, String?) -> Boolean)?,
    onAddToPhotoPdf: (suspend (Uri) -> Boolean)?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val outputs = remember(context) { MediaOutputs(context.applicationContext) }
    var busy by remember(file.path) { mutableStateOf(false) }
    var failed by remember(file.path) { mutableStateOf(false) }
    var snapshot by remember(file.path) { mutableStateOf<MediaOutput?>(null) }
    var pending by rememberSaveable(file.path) { mutableStateOf<String?>(null) }
    var pickPresentation by rememberSaveable(file.path) { mutableStateOf(false) }
    val needsApproval = sourceBytes != null && file.length() >= sourceBytes
    val chooserTitle = stringResource(R.string.share_output)

    fun perform(action: String, approved: Boolean, target: String? = null) {
        if (busy || !enabled) return
        busy = true
        failed = false
        scope.launch {
            try {
                val cached = snapshot
                val output = withContext(Dispatchers.IO) {
                    cached?.takeIf { saved -> runCatching {
                        context.contentResolver.openFileDescriptor(saved.uri, "r")?.use { it.statSize == saved.bytes } == true
                    }.getOrDefault(false) } ?: outputs.stage(file, mime, name, sourceBytes,
                        allowNonSmallerCopy = approved)
                }.also { snapshot = it }
                // Every invocation still needs approval, even when a snapshot is already available.
                check(!needsApproval || approved)
                when (action) {
                    "open" -> context.startActivity(output.viewIntent())
                    "share" -> context.startActivity(Intent.createChooser(output.shareIntent(), chooserTitle))
                    "add" -> check(onAddToPresentation?.invoke(output.uri, target) == true)
                    "pdf" -> check(onAddToPhotoPdf?.invoke(output.uri) == true)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
            finally { busy = false }
        }
    }
    fun request(action: String) {
        if (needsApproval) pending = action
        else if (action == "add") pickPresentation = true
        else perform(action, approved = false)
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { request("open") }, enabled = enabled && !busy,
            modifier = Modifier.testTag("open-output")) { Text(stringResource(R.string.open_output)) }
        OutlinedButton(onClick = { request("share") }, enabled = enabled && !busy,
            modifier = Modifier.testTag("share-output")) { Text(stringResource(R.string.share_output)) }
        if (onAddToPresentation != null) FilledTonalButton(onClick = { request("add") }, enabled = enabled && !busy,
            modifier = Modifier.testTag("add-output-to-presentation")) { Text(stringResource(R.string.add_to_presentation)) }
        if (onAddToPhotoPdf != null) FilledTonalButton(onClick = { request("pdf") }, enabled = enabled && !busy,
            modifier = Modifier.testTag("add-output-to-photo-pdf")) { Text(stringResource(R.string.add_to_photo_pdf)) }
    }
    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("output-busy"))
    if (failed) Text(stringResource(R.string.output_action_error), color = MaterialTheme.colorScheme.error,
        modifier = Modifier.testTag("output-error"))
    pending?.let { action ->
        AlertDialog(onDismissRequest = { pending = null }, title = { Text(stringResource(R.string.compression_no_saving)) },
            text = { Text(stringResource(R.string.output_use_warning), modifier = Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = {
                pending = null
                if (action == "add") pickPresentation = true else perform(action, approved = true)
            }, modifier = Modifier.testTag("confirm-output-use")) { Text(stringResource(R.string.continue_output)) } },
            dismissButton = { TextButton(onClick = { pending = null }, modifier = Modifier.testTag("cancel-output-use")) {
                Text(stringResource(R.string.keep_original))
            } })
    }
    if (pickPresentation) AlertDialog(onDismissRequest = { pickPresentation = false },
        title = { Text(stringResource(R.string.add_to_presentation)) }, text = {
            LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item(key = "new") {
                    FilledTonalButton(onClick = { pickPresentation = false; perform("add", needsApproval) },
                        modifier = Modifier.fillMaxWidth().testTag("output-new-presentation")) {
                        Text(stringResource(R.string.new_presentation))
                    }
                }
                items(presentations.filter { it.kind == ProjectKind.PRESENTATION }, key = { it.id }) { project ->
                    OutlinedButton(onClick = { pickPresentation = false; perform("add", needsApproval, project.id) },
                        enabled = project.slides.size < SlideOperations.MAX_SLIDES,
                        modifier = Modifier.fillMaxWidth().testTag("output-target-${project.id}")) {
                        Text(project.title)
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { pickPresentation = false }) { Text(stringResource(R.string.cancel)) } })
}

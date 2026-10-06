package com.example.contentstudio.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.example.contentstudio.R
import com.example.contentstudio.media.CompressionBenefit

/** Non-saving copies remain available, but require an explicit decision for this exact result. */
@Composable
fun CompressionExportActions(resultKey: String, benefit: CompressionBenefit, enabled: Boolean,
    label: String, tag: String, onSave: () -> Unit, onSaveAnyway: () -> Unit) {
    var confirm by rememberSaveable(resultKey) { mutableStateOf(false) }
    val smaller = benefit == CompressionBenefit.SMALLER
    Button(onClick = onSave, enabled = enabled && smaller, modifier = Modifier.testTag(tag)) {
        Text(label)
    }
    if (!smaller) {
        TextButton(onClick = { confirm = true }, enabled = enabled,
            modifier = Modifier.testTag("$tag-anyway")) {
            Text(stringResource(R.string.save_anyway))
        }
    }
    if (confirm && !smaller) AlertDialog(onDismissRequest = { confirm = false },
        title = { Text(stringResource(R.string.compression_no_saving)) },
        text = { Text(stringResource(R.string.compression_save_warning),
            modifier = Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = {
            TextButton(onClick = { confirm = false; onSaveAnyway() }, enabled = enabled,
                modifier = Modifier.testTag("$tag-confirm")) {
                Text(stringResource(R.string.save_anyway))
            }
        }, dismissButton = {
            TextButton(onClick = { confirm = false }, modifier = Modifier.testTag("$tag-keep-original")) {
                Text(stringResource(R.string.keep_original))
            }
        })
}

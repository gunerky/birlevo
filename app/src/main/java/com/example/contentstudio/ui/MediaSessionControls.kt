package com.example.contentstudio.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.example.contentstudio.R
import com.example.contentstudio.media.SessionSaveStatus

@Composable
internal fun MediaSessionControls(status: SessionSaveStatus, restoreFailed: Boolean, enabled: Boolean,
    tag: String, onRetry: () -> Unit, onClear: () -> Unit) {
    Column {
        if (enabled && !restoreFailed && status != SessionSaveStatus.ERROR) Text(stringResource(
            if (status == SessionSaveStatus.SAVED) R.string.session_saved else R.string.session_saving),
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("$tag-session-status"))
        if (status == SessionSaveStatus.ERROR || restoreFailed) {
            Text(stringResource(R.string.session_error),
                color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("$tag-session-error"))
            TextButton(onClick = onRetry, enabled = enabled, modifier = Modifier.testTag("$tag-session-retry")) { Text(stringResource(R.string.retry)) }
        }
        TextButton(onClick = onClear, enabled = enabled, modifier = Modifier.testTag("$tag-session-clear")) { Text(stringResource(R.string.clear_session)) }
    }
}

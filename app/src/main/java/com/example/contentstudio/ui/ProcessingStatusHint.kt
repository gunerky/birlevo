package com.example.contentstudio.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.example.contentstudio.R
import com.example.contentstudio.processing.ProcessingStatus

@Composable
internal fun ProcessingStatusHint(status: ProcessingStatus?, tag: String) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    val label = when (status) {
        ProcessingStatus.QUEUED -> R.string.processing_queued
        ProcessingStatus.RUNNING -> R.string.processing_background
        ProcessingStatus.INTERRUPTED -> R.string.processing_interrupted
        else -> null
    }
    label?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("$tag-work-status")) }
    if (!granted) TextButton(onClick = { if (Build.VERSION.SDK_INT >= 33) request.launch(Manifest.permission.POST_NOTIFICATIONS) },
        modifier = Modifier.testTag("$tag-enable-notifications")) { Text(stringResource(R.string.processing_enable_notifications)) }
}

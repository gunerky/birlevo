package com.example.contentstudio.advertising

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.contentstudio.BuildConfig
import com.example.contentstudio.R

/** Debug-only entry; release ad activation remains closed until account/disclosure review. */
@Composable
internal fun AdPrivacyEntry() {
    if (!BuildConfig.AD_TESTING) return
    var open by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { open = true }, modifier = Modifier.testTag("ad-privacy-entry")) {
        Text(stringResource(R.string.ad_privacy))
    }
    if (open) {
        val activity = LocalActivity.current ?: return
        val model: AdPrivacyViewModel = viewModel()
        val session = model.session
        val state by session.state.collectAsStateWithLifecycle()
        fun close() { session.detach(); open = false }
        DisposableEffect(activity, session) {
            val owner = activity as androidx.lifecycle.LifecycleOwner
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) session.detach()
            }
            owner.lifecycle.addObserver(observer)
            onDispose { owner.lifecycle.removeObserver(observer); session.detach() }
        }
        AlertDialog(
            onDismissRequest = ::close,
            title = { Text(stringResource(R.string.ad_privacy)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.ad_test_notice))
                    Text(stringResource(when (state.phase) {
                        AdPrivacyPhase.CHECKING -> R.string.ad_checking
                        AdPrivacyPhase.READY -> R.string.ad_ready
                        AdPrivacyPhase.UNAVAILABLE -> R.string.ad_unavailable
                        else -> R.string.ad_not_started
                    }), modifier = Modifier.testTag("ad-privacy-status"))
                    TextButton(onClick = { session.refresh(activity) },
                        enabled = state.phase != AdPrivacyPhase.CHECKING,
                        modifier = Modifier.testTag("ad-privacy-refresh")) {
                        Text(stringResource(R.string.ad_check_privacy))
                    }
                    if (state.optionsRequired) TextButton(onClick = { session.showOptions(activity) },
                        enabled = state.phase != AdPrivacyPhase.CHECKING,
                        modifier = Modifier.testTag("ad-privacy-options")) {
                        Text(stringResource(R.string.ad_privacy_choices))
                    }
                }
            },
            confirmButton = { TextButton(onClick = ::close) { Text(stringResource(R.string.close)) } },
        )
    }
}

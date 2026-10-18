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
import com.example.contentstudio.processing.ProcessingJobs

/** Shared Home entry with a separate manual debug panel; live release activation stays closed. */
@Composable
internal fun AdPrivacyEntry(available: Boolean = true) {
    if (!AdBuildPolicy.enabled) return
    if (AdBuildPolicy.homeFlow) {
        HomeAdPreviewEntry(available)
        return
    }
    var open by rememberSaveable { mutableStateOf(false) }
    // A test banner is deliberately not saved: recreation must never restart a request.
    var banner by remember { mutableStateOf(false) }
    var bannerUnavailable by remember { mutableStateOf(false) }
    val activity = LocalActivity.current ?: return
    val model: AdPrivacyViewModel = viewModel()
    TextButton(onClick = {
        banner = false; bannerUnavailable = false
        model.session.detach()
        open = true
    }, enabled = available, modifier = Modifier.testTag("ad-privacy-entry")) {
        Text(stringResource(R.string.ad_privacy))
    }
    if (open || banner) {
        val session = model.session
        val state by session.state.collectAsStateWithLifecycle()
        val jobs = remember(activity) { ProcessingJobs.get(activity) }
        val records by jobs.records.collectAsStateWithLifecycle()
        var jobsReady by remember { mutableStateOf(false) }
        LaunchedEffect(jobs) { jobs.awaitReady(); jobsReady = !jobs.recoveryFailed }
        val quiet = available && jobsReady && records.values.none { it.status.active }
        val owner = activity as androidx.lifecycle.LifecycleOwner
        var resumed by remember(activity) { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
        val canLoad = quiet && resumed && state.phase == AdPrivacyPhase.READY
        LaunchedEffect(quiet) { if (!quiet) { banner = false; session.detach() } }
        fun close() { session.detach(); open = false }
        DisposableEffect(activity, session) {
            val observer = LifecycleEventObserver { _, event ->
                resumed = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                if (event == Lifecycle.Event.ON_PAUSE) banner = false
                if (event == Lifecycle.Event.ON_STOP) { banner = false; session.detach() }
            }
            owner.lifecycle.addObserver(observer)
            onDispose { owner.lifecycle.removeObserver(observer); session.detach() }
        }
        if (banner && canLoad && !open) HomeTestBanner(activity,
            eligible = { quiet && resumed && !open && session.canRequestAds() },
            reserveAttempt = model.bannerAttempts::reserve,
            onUnavailable = { banner = false; bannerUnavailable = true })
        if (open) AlertDialog(
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
                    if (state.phase == AdPrivacyPhase.READY) TextButton(
                        onClick = { bannerUnavailable = false; banner = true; open = false },
                        enabled = canLoad,
                        modifier = Modifier.testTag("ad-show-test-banner")) {
                        Text(stringResource(R.string.ad_show_test_banner))
                    }
                }
            },
            confirmButton = { TextButton(onClick = ::close) { Text(stringResource(R.string.close)) } },
        )
    }
    if (bannerUnavailable) Text(stringResource(R.string.ad_banner_unavailable),
        modifier = Modifier.padding(horizontal = 20.dp).testTag("ad-banner-unavailable"),
        style = MaterialTheme.typography.bodySmall)
}

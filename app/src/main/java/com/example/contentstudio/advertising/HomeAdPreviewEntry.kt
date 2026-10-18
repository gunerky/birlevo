package com.example.contentstudio.advertising

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.contentstudio.BuildConfig
import com.example.contentstudio.R
import com.example.contentstudio.processing.ProcessingJobs

/** Created only after a durable eligible age choice. Blocked users do not instantiate UMP. */
@Composable
private fun EligibleHomeAdPreview(quiet: Boolean, automatic: Boolean, model: AdPrivacyViewModel) {
    val activity = LocalActivity.current ?: return
    val session = model.session
    val state by session.state.collectAsStateWithLifecycle()
    val owner = activity as androidx.lifecycle.LifecycleOwner
    var resumed by remember(activity) { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    // Recreation/backgrounding must not resurrect a banner or restart automatic requests.
    var attempted by rememberSaveable { mutableStateOf(false) }
    var armed by remember { mutableStateOf(false) }

    DisposableEffect(owner, session) {
        val observer = LifecycleEventObserver { _, event ->
            resumed = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if (event == Lifecycle.Event.ON_PAUSE) armed = false
            if (event == Lifecycle.Event.ON_STOP) session.detach()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); session.detach() }
    }
    LaunchedEffect(quiet, resumed, automatic) {
        if (!quiet || !resumed) {
            armed = false
            session.detach()
        } else if (!attempted) {
            attempted = true
            if (automatic && model.homeAttempts.reserve()) {
                armed = true
                session.refresh(activity)
            }
        }
    }

    if (state.phase == AdPrivacyPhase.CHECKING || state.phase == AdPrivacyPhase.UNAVAILABLE) {
        Text(stringResource(if (state.phase == AdPrivacyPhase.CHECKING) R.string.ad_checking else R.string.ad_unavailable),
            modifier = Modifier.padding(horizontal = 20.dp).testTag("ad-privacy-status"),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (armed && quiet && resumed && state.phase == AdPrivacyPhase.READY) {
        HomeTestBanner(activity,
            eligible = { quiet && resumed && armed && model.currentTreatment() != AdAgeTreatment.BLOCKED &&
                session.canRequestAds() },
            reserveAttempt = model.bannerAttempts::reserve,
            // Failure leaves no empty slot; preferences and file controls remain available.
            onUnavailable = { armed = false })
    }
}

/** Emulated preview of the user flow; every advertisement remains Google's sample unit. */
@Composable
internal fun HomeAdPreviewEntry(available: Boolean) {
    if (!BuildConfig.DEBUG || !BuildConfig.AD_TESTING || !BuildConfig.AD_HOME_PREVIEW ||
        android.os.Build.HARDWARE !in setOf("ranchu", "goldfish")) return
    val activity = LocalActivity.current ?: return
    val model: AdPrivacyViewModel = viewModel()
    val audience by model.audience.state.collectAsStateWithLifecycle()
    val jobs = remember(activity) { ProcessingJobs.get(activity) }
    val records by jobs.records.collectAsStateWithLifecycle()
    var jobsReady by remember { mutableStateOf(false) }
    LaunchedEffect(jobs) { jobs.awaitReady(); jobsReady = !jobs.recoveryFailed }
    val quiet = available && jobsReady && records.values.none { it.status.active }
    var ageOpen by rememberSaveable { mutableStateOf(false) }
    var prompted by rememberSaveable { mutableStateOf(false) }
    var manualPrivacy by rememberSaveable { mutableStateOf(false) }
    val allowed = audience.group.treatment != AdAgeTreatment.BLOCKED && !audience.saving

    LaunchedEffect(quiet, audience) {
        if (quiet && !prompted && !audience.saving) {
            prompted = true
            if (audience.group == AdAudienceGroup.UNSET && !audience.saveFailed) ageOpen = true
        }
    }
    fun dismiss() {
        ageOpen = false
        if (audience.group == AdAudienceGroup.UNSET && !audience.saving)
            model.audience.choose(AdAudienceGroup.UNDISCLOSED)
    }
    TextButton(onClick = { manualPrivacy = true; ageOpen = true }, enabled = available && !audience.saving,
        modifier = Modifier.testTag("ad-privacy-entry")) {
        Text(stringResource(R.string.ad_privacy))
    }
    if (audience.saving || audience.saveFailed) {
        Text(stringResource(if (audience.saving) R.string.ad_age_saving else R.string.ad_age_save_failed),
            modifier = Modifier.padding(horizontal = 20.dp).testTag("ad-age-save-status"),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (allowed) key(audience.group) { EligibleHomeAdPreview(quiet && !ageOpen, !manualPrivacy, model) }
    if (ageOpen) AlertDialog(
        onDismissRequest = ::dismiss,
        title = { Text(stringResource(R.string.ad_privacy)) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.ad_age_notice), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.ad_age_title), style = MaterialTheme.typography.titleSmall)
                for ((group, label) in listOf(
                    AdAudienceGroup.UNDER_13 to R.string.ad_age_under_13,
                    AdAudienceGroup.AGE_13_15 to R.string.ad_age_13_15,
                    AdAudienceGroup.AGE_16_17 to R.string.ad_age_16_17,
                    AdAudienceGroup.ADULT to R.string.ad_age_adult,
                    AdAudienceGroup.UNDISCLOSED to R.string.ad_age_undisclosed,
                )) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .selectable(audience.group == group, enabled = !audience.saving, role = Role.RadioButton) {
                            ageOpen = false
                            model.audience.choose(group)
                        }.testTag("ad-age-${group.name.lowercase()}"),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(selected = audience.group == group, onClick = null)
                        val text = stringResource(label)
                        Text(if (group == AdAudienceGroup.AGE_13_15 || group == AdAudienceGroup.AGE_16_17)
                            androidx.core.text.BidiFormatter.getInstance().unicodeWrap(text,
                                androidx.core.text.TextDirectionHeuristicsCompat.LTR) else text,
                            modifier = Modifier.weight(1f))
                    }
                }
                if (allowed) {
                    val state by model.session.state.collectAsStateWithLifecycle()
                    TextButton(onClick = {
                        ageOpen = false
                        if (state.optionsRequired) model.session.showOptions(activity)
                        else model.session.refresh(activity)
                    }, enabled = state.phase != AdPrivacyPhase.CHECKING,
                        modifier = Modifier.testTag("ad-privacy-options")) {
                        Text(stringResource(R.string.ad_privacy_choices))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = ::dismiss) { Text(stringResource(R.string.close)) } },
    )
}

package com.example.contentstudio.advertising

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal interface AdConsentGateway<H> {
    suspend fun refresh(host: H)
    suspend fun collectIfRequired(host: H)
    suspend fun showOptions(host: H)
    fun canRequestAds(): Boolean
    fun optionsRequired(): Boolean
    fun canShowOptions(): Boolean = optionsRequired()
}

internal enum class AdPrivacyPhase { DISABLED, IDLE, CHECKING, READY, UNAVAILABLE }

internal data class AdPrivacyState(
    val phase: AdPrivacyPhase = AdPrivacyPhase.IDLE,
    val optionsRequired: Boolean = false,
)

/** Confined to the UI thread. Never persists a second, potentially stale consent decision. */
internal class AdPrivacySession<H>(
    private val enabled: Boolean,
    private val gateway: AdConsentGateway<H>,
    private val initialize: suspend () -> Unit,
    private val scope: CoroutineScope,
    private val timeoutMs: Long = 15_000,
) {
    private val mutableState = MutableStateFlow(
        AdPrivacyState(if (enabled) AdPrivacyPhase.IDLE else AdPrivacyPhase.DISABLED)
    )
    val state = mutableState.asStateFlow()
    private var operation: Job? = null
    private var generation = 0L

    // A future placement must check this again immediately before loading/showing an ad.
    fun canRequestAds(): Boolean = enabled && state.value.phase == AdPrivacyPhase.READY &&
        gateway.canRequestAds()

    fun refresh(host: H) = run(host, options = false)
    fun showOptions(host: H) {
        if (state.value.optionsRequired) run(host, options = gateway.canShowOptions())
    }

    private fun run(host: H, options: Boolean) {
        if (!enabled || state.value.phase == AdPrivacyPhase.CHECKING) return
        val token = ++generation
        mutableState.value = state.value.copy(phase = AdPrivacyPhase.CHECKING)
        operation = scope.launch {
            try {
                if (options) gateway.showOptions(host)
                else {
                    withTimeout(timeoutMs) { gateway.refresh(host) }
                    currentCoroutineContext().ensureActive()
                    // The gateway bounds form loading; reading a displayed form has no deadline.
                    gateway.collectIfRequired(host)
                }
                currentCoroutineContext().ensureActive()
                if (gateway.canRequestAds()) withTimeout(timeoutMs) { initialize() }
                if (token == generation) mutableState.value = AdPrivacyState(
                    if (gateway.canRequestAds()) AdPrivacyPhase.READY else AdPrivacyPhase.UNAVAILABLE,
                    gateway.optionsRequired(),
                )
            } catch (error: Exception) {
                if (token == generation) mutableState.value = AdPrivacyState(
                    AdPrivacyPhase.UNAVAILABLE, gateway.optionsRequired(),
                )
                if (error is CancellationException) throw error
            }
        }
    }

    /** Revoke eligibility before cancelling callbacks from a closed/recreated Activity. */
    fun detach() {
        generation++
        operation?.cancel()
        operation = null
        mutableState.value = state.value.copy(
            phase = if (enabled) AdPrivacyPhase.IDLE else AdPrivacyPhase.DISABLED
        )
    }
}

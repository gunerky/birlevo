package com.example.contentstudio.advertising

import android.app.Activity
import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.content.edit
import androidx.work.Configuration
import androidx.work.WorkManager
import com.example.contentstudio.BuildConfig
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.common.RequestConfiguration
import com.google.android.libraries.ads.mobile.sdk.common.AgeRestrictedTreatment
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentForm
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal object AdTestIds {
    const val SAMPLE_APPLICATION = "ca-app-pub-3940256099942544~3347511713"
    const val APPLICATION = BuildConfig.AD_APPLICATION_ID
    const val BANNER = "ca-app-pub-3940256099942544/9214589741"
}

/** No startup initialization, media, filenames, URIs, content URLs or targeting parameters. */
internal class GoogleAdConsent(context: Context,
    private val treatment: () -> AdAgeTreatment = { AdAgeTreatment.ADULT },
    private val policyVersion: () -> Long = { 0 },
) : AdConsentGateway<Activity> {
    private val information = UserMessagingPlatform.getConsentInformation(context.applicationContext)
    private var verifiedPolicy: Pair<AdAgeTreatment, Long>? = null

    override suspend fun refresh(host: Activity): Unit = suspendCancellableCoroutine { continuation ->
        val current = treatment()
        val version = policyVersion()
        verifiedPolicy = null
        if (current == AdAgeTreatment.BLOCKED) {
            continuation.resumeWithException(ConsentUnavailable())
            return@suspendCancellableCoroutine
        }
        val parameters = ConsentRequestParameters.Builder().apply {
            // UMP does not forward this flag to the ads SDK; initialize configures both paths.
            setTagForUnderAgeOfConsent(current == AdAgeTreatment.PROTECTED)
            // Only debug builds can enter this integration. Emulators are UMP test devices.
            if (BuildConfig.AD_TESTING) setConsentDebugSettings(
                ConsentDebugSettings.Builder(host)
                    .setDebugGeography(when (BuildConfig.AD_PRIVACY_REGION) {
                        "eea" -> ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA
                        "us" -> ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_REGULATED_US_STATE
                        "other" -> ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_OTHER
                        else -> ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_DISABLED
                    })
                    .build()
            )
        }.build()
        information.requestConsentInfoUpdate(host, parameters,
            { if (continuation.isActive && treatment() == current && policyVersion() == version) {
                verifiedPolicy = current to version
                continuation.resume(Unit)
            } else if (continuation.isActive) continuation.resumeWithException(ConsentUnavailable()) },
            { if (continuation.isActive) continuation.resumeWithException(ConsentUnavailable()) },
        )
    }

    override suspend fun collectIfRequired(host: Activity) {
        if (treatment() == AdAgeTreatment.BLOCKED) throw ConsentUnavailable()
        if (information.consentStatus != ConsentInformation.ConsentStatus.REQUIRED) return
        // Never ask a protected user to make an adult consent decision.
        if (treatment() != AdAgeTreatment.ADULT) throw ConsentUnavailable()
        if (!information.isConsentFormAvailable) throw ConsentUnavailable()
        val form = withTimeout(15_000) {
            suspendCancellableCoroutine<ConsentForm> { continuation ->
                // Separate load/show so a late load cannot open a form after closing the panel.
                UserMessagingPlatform.loadConsentForm(host.applicationContext, {
                    if (continuation.isActive) continuation.resume(it)
                }, {
                    if (continuation.isActive) continuation.resumeWithException(ConsentUnavailable())
                })
            }
        }
        currentCoroutineContext().ensureActive()
        if (host.isFinishing || host.isDestroyed) throw ConsentUnavailable()
        // Do not time-limit a person's reading or decision once the form is displayed.
        suspendCancellableCoroutine<Unit> { continuation ->
            form.show(host) { error ->
                if (continuation.isActive) {
                    if (error == null) continuation.resume(Unit)
                    else continuation.resumeWithException(ConsentUnavailable())
                }
            }
        }
    }

    override suspend fun showOptions(host: Activity): Unit = suspendCancellableCoroutine { continuation ->
        if (!canShowOptions()) {
            continuation.resumeWithException(ConsentUnavailable())
            return@suspendCancellableCoroutine
        }
        UserMessagingPlatform.showPrivacyOptionsForm(host) { error ->
            if (continuation.isActive) {
                if (error == null) continuation.resume(Unit)
                else continuation.resumeWithException(ConsentUnavailable())
            }
        }
    }

    override fun canRequestAds() = treatment() != AdAgeTreatment.BLOCKED &&
        verifiedPolicy == (treatment() to policyVersion()) && information.canRequestAds()
    override fun optionsRequired() = information.privacyOptionsRequirementStatus ==
        ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    override fun canShowOptions() = treatment() != AdAgeTreatment.BLOCKED &&
        verifiedPolicy == (treatment() to policyVersion()) && optionsRequired()
    private class ConsentUnavailable : Exception()
}

/** One process-wide initializer; background initialization never blocks media UI. */
internal object GoogleAdInitializer {
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var initialization: Deferred<Unit>? = null
    private var workInitialized = false
    @Volatile private var started = false
    private val treatment = java.util.concurrent.atomic.AtomicReference(AdAgeTreatment.BLOCKED)
    private val configurationLock = Any()

    private fun configuration(age: AdAgeTreatment, initializing: Boolean = false) = RequestConfiguration.Builder()
        .setMaxAdContentRating(RequestConfiguration.MaxAdContentRating.MAX_AD_CONTENT_RATING_G)
        // Conservatively apply the stronger under-consent protection to every 13-17 user.
        .setAgeRestrictedTreatment(if (age == AdAgeTreatment.ADULT && !initializing) AgeRestrictedTreatment.UNSPECIFIED
            else AgeRestrictedTreatment.CHILD)
        .setPublisherPrivacyPersonalizationState(if (age == AdAgeTreatment.ADULT)
            RequestConfiguration.PublisherPrivacyPersonalizationState.DEFAULT
            else RequestConfiguration.PublisherPrivacyPersonalizationState.DISABLED)
        .build()

    fun applyAudience(age: AdAgeTreatment) {
        synchronized(configurationLock) {
            treatment.set(age)
            if (started && MobileAds.isInitialized) MobileAds.setRequestConfiguration(configuration(age))
        }
    }

    suspend fun initialize(context: Context, age: AdAgeTreatment = AdAgeTreatment.ADULT) {
        check(age != AdAgeTreatment.BLOCKED)
        applyAudience(age)
        val application = context.applicationContext
        val pending = mutex.withLock {
            initialization?.takeUnless { it.isCancelled } ?: scope.async {
                check(treatment.get() != AdAgeTreatment.BLOCKED)
                if (!workInitialized) {
                    WorkManager.initialize(application, Configuration.Builder().build())
                    workInitialized = true
                }
                check(treatment.get() != AdAgeTreatment.BLOCKED)
                started = true
                MobileAds.initialize(application,
                    InitializationConfig.Builder(AdTestIds.APPLICATION)
                        // Initialize with the strict configuration so a concurrent age change
                        // cannot start the SDK with an outdated adult configuration.
                        .setRequestConfiguration(configuration(treatment.get(), initializing = true))
                        .disableSdkCrashReporting().build())
                // An in-flight initialization must not restore an older adult configuration.
                synchronized(configurationLock) {
                    MobileAds.setRequestConfiguration(configuration(treatment.get()))
                }
            }.also { initialization = it }
        }
        // A closed UI or deadline stops waiting immediately, even if the SDK's synchronous
        // initialization continues. It receives only application context and no media data.
        pending.await()
    }
}

internal class AdPrivacyViewModel(application: Application) : AndroidViewModel(application) {
    val audience: AdAudienceChoices = AdAudienceChoices(
        runCatching { application.getSharedPreferences("ad_privacy_preferences", Context.MODE_PRIVATE)
            .getString("age_group", null) }.getOrNull(),
        viewModelScope,
        persist = { group -> withContext(Dispatchers.IO) {
            val saved = application.getSharedPreferences("ad_privacy_preferences", Context.MODE_PRIVATE)
                .edit().putString("age_group", group.name).commit()
            if (!saved) application.deleteSharedPreferences("ad_privacy_preferences")
            saved
        } },
        restrictImmediately = {
            if (sessionDelegate.isInitialized()) sessionDelegate.value.detach()
            GoogleAdInitializer.applyAudience(AdAgeTreatment.BLOCKED)
        },
    )
    fun currentTreatment(): AdAgeTreatment = if (BuildConfig.AD_HOME_PREVIEW) audience.state.value.group.treatment else AdAgeTreatment.ADULT
    val homeAttempts by lazy {
        val preferences = application.getSharedPreferences("ad_test_attempts", Context.MODE_PRIVATE)
        BannerAttemptLimiter(System::currentTimeMillis,
            { if (preferences.contains("last_home_check")) preferences.getLong("last_home_check", 0) else null },
            { time -> preferences.edit { putLong("last_home_check", time) } })
    }
    val bannerAttempts by lazy {
        val preferences = application.getSharedPreferences("ad_test_attempts", Context.MODE_PRIVATE)
        BannerAttemptLimiter(System::currentTimeMillis,
            { if (preferences.contains("last_banner")) preferences.getLong("last_banner", 0) else null },
            { time -> preferences.edit { putLong("last_banner", time) } })
    }
    private val sessionDelegate: Lazy<AdPrivacySession<Activity>> = lazy {
        AdPrivacySession(BuildConfig.AD_TESTING, GoogleAdConsent(application, ::currentTreatment,
            { if (BuildConfig.AD_HOME_PREVIEW) audience.version else 0 }),
            { GoogleAdInitializer.initialize(application, currentTreatment()) }, viewModelScope)
    }
    val session get() = sessionDelegate.value
    val hasConsentSession get() = sessionDelegate.isInitialized()
}

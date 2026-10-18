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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal object AdTestIds {
    const val APPLICATION = "ca-app-pub-3940256099942544~3347511713"
    const val BANNER = "ca-app-pub-3940256099942544/9214589741"
}

/** No startup initialization, media, filenames, URIs, content URLs or targeting parameters. */
internal class GoogleAdConsent(context: Context) : AdConsentGateway<Activity> {
    private val information = UserMessagingPlatform.getConsentInformation(context.applicationContext)

    override suspend fun refresh(host: Activity): Unit = suspendCancellableCoroutine { continuation ->
        val parameters = ConsentRequestParameters.Builder().apply {
            // Only debug builds can enter this integration. Emulators are UMP test devices.
            if (BuildConfig.AD_TESTING) setConsentDebugSettings(
                ConsentDebugSettings.Builder(host)
                    .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                    .build()
            )
        }.build()
        information.requestConsentInfoUpdate(host, parameters,
            { if (continuation.isActive) continuation.resume(Unit) },
            { if (continuation.isActive) continuation.resumeWithException(ConsentUnavailable()) },
        )
    }

    override suspend fun collectIfRequired(host: Activity) {
        if (information.consentStatus != ConsentInformation.ConsentStatus.REQUIRED) return
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
        UserMessagingPlatform.showPrivacyOptionsForm(host) { error ->
            if (continuation.isActive) {
                if (error == null) continuation.resume(Unit)
                else continuation.resumeWithException(ConsentUnavailable())
            }
        }
    }

    override fun canRequestAds() = information.canRequestAds()
    override fun optionsRequired() = information.privacyOptionsRequirementStatus ==
        ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    private class ConsentUnavailable : Exception()
}

/** One process-wide initializer; background initialization never blocks media UI. */
internal object GoogleAdInitializer {
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var initialization: Deferred<Unit>? = null
    private var workInitialized = false

    suspend fun initialize(context: Context) {
        val application = context.applicationContext
        val pending = mutex.withLock {
            initialization?.takeUnless { it.isCancelled } ?: scope.async {
                if (!workInitialized) {
                    WorkManager.initialize(application, Configuration.Builder().build())
                    workInitialized = true
                }
                MobileAds.initialize(application,
                    InitializationConfig.Builder(AdTestIds.APPLICATION).disableSdkCrashReporting().build())
            }.also { initialization = it }
        }
        // A closed UI or deadline stops waiting immediately, even if the SDK's synchronous
        // initialization continues. It receives only application context and no media data.
        pending.await()
    }
}

internal class AdPrivacyViewModel(application: Application) : AndroidViewModel(application) {
    val bannerAttempts by lazy {
        val preferences = application.getSharedPreferences("ad_test_attempts", Context.MODE_PRIVATE)
        BannerAttemptLimiter(System::currentTimeMillis,
            { if (preferences.contains("last_banner")) preferences.getLong("last_banner", 0) else null },
            { time -> preferences.edit { putLong("last_banner", time) } })
    }
    val session by lazy {
        AdPrivacySession(BuildConfig.AD_TESTING, GoogleAdConsent(application),
            { GoogleAdInitializer.initialize(application) }, viewModelScope)
    }
}

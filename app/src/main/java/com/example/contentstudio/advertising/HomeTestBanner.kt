package com.example.contentstudio.advertising

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.contentstudio.BuildConfig
import com.example.contentstudio.R
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.AdView
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError

/** Sample identifiers only. No targeting data, preloading, retry loop or full-screen format. */
@Composable
internal fun HomeTestBanner(
    activity: Activity,
    eligible: () -> Boolean,
    reserveAttempt: () -> Boolean,
    onUnavailable: () -> Unit,
) {
    if (!BuildConfig.AD_TESTING) return
    val currentEligible by rememberUpdatedState(eligible)
    val unavailable by rememberUpdatedState(onUnavailable)
    val scope = rememberCoroutineScope()
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        val width = maxWidth.value.toInt().coerceAtMost(720)
        val size = remember(activity, width) {
            AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, width.coerceAtLeast(1))
        }
        // Preserve useful home content in short windows and at large accessibility scales.
        if (width < 320 || size.height.dp > maxHeight / 4) {
            LaunchedEffect(Unit) { unavailable() }
            return@BoxWithConstraints
        }
        val session = remember(activity, width) {
            HomeBannerSession(scope, { currentEligible() }, reserveAttempt)
        }
        val phase by session.phase.collectAsState()
        val banner = remember(activity, width) { GoogleBannerHandle(activity, size) }
        DisposableEffect(session, banner) {
            session.start(banner)
            onDispose { session.stop(); banner.destroy() }
        }
        LaunchedEffect(phase) { if (phase == BannerPhase.UNAVAILABLE) unavailable() }
        Column(Modifier.fillMaxWidth().testTag("home-test-banner"), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.ad_test_banner), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            AndroidView(factory = { banner.view }, modifier = Modifier.width(width.dp).height(size.height.dp)
                .testTag("home-test-banner-${phase.name.lowercase()}"),
                onRelease = { banner.destroy() })
        }
    }
}

private class GoogleBannerHandle(activity: Activity, private val size: AdSize) : BannerHandle {
    val view = AdView(activity).apply { visibility = View.INVISIBLE }
    private var destroyed = false
    override fun load(loaded: () -> Unit, failed: () -> Unit) {
        check(!destroyed)
        view.loadAd(BannerAdRequest.Builder(AdTestIds.BANNER, size).build(),
            object : AdLoadCallback<BannerAd> {
                override fun onAdLoaded(ad: BannerAd) { loaded() }
                override fun onAdFailedToLoad(adError: LoadAdError) { failed() }
            })
    }
    override fun reveal() { if (!destroyed) view.visibility = View.VISIBLE }
    override fun destroy() {
        if (destroyed) return
        destroyed = true
        view.visibility = View.GONE
        (view.parent as? ViewGroup)?.removeView(view)
        view.destroy()
    }
}

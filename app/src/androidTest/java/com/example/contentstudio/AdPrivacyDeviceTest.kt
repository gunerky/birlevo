package com.example.contentstudio

import android.content.pm.PackageManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.example.contentstudio.advertising.*
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.ump.UserMessagingPlatform
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.work.WorkManager
import org.junit.*
import org.junit.Assert.*

class AdPrivacyDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private var scenario: ActivityScenario<MainActivity>? = null
    @Before fun locale() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                androidx.core.os.LocaleListCompat.forLanguageTags("en-US"))
        }
    }
    @After fun close() { scenario?.close() }

    private fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(20_000) {
            compose.onAllNodes(hasTestTag("compact-home") or hasTestTag("wide-home-tools"))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }
    private fun privacy() {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("ad-privacy-entry"))
        compose.onNodeWithTag("ad-privacy-entry").performClick()
    }

    @Test fun ordinaryLaunchAndRecreationDoNotInitializeAdsOrWorkManager() {
        launch()
        assertFalse(MobileAds.isInitialized)
        try { WorkManager.getInstance(context); fail("Unexpected startup database") }
        catch (_: IllegalStateException) { }
        scenario!!.recreate(); compose.waitForIdle()
        assertFalse(MobileAds.isInitialized)
        assertEquals("1.5.0", MobileAds.getVersion().toString())
    }

    @Test fun privacyPanelDoesNotContactProviderUntilExplicitActionAndToolsStillOpen() {
        launch(); privacy()
        compose.onNodeWithTag("ad-privacy-status").assertTextEquals(context.getString(R.string.ad_not_started))
        assertFalse(MobileAds.isInitialized)
        compose.onNodeWithText(context.getString(R.string.close)).performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("compress-image-action"))
        compose.onNodeWithTag("compress-image-action").performClick()
        assertFalse(MobileAds.isInitialized)
    }

    @Test fun privacyPanelSurvivesRecreationWithoutStartingNetwork() {
        launch(); privacy(); scenario!!.recreate(); compose.waitForIdle()
        compose.onNodeWithTag("ad-privacy-status").assertTextEquals(context.getString(R.string.ad_not_started))
        compose.onNodeWithTag("ad-privacy-refresh").assertIsEnabled()
        assertFalse(MobileAds.isInitialized)
    }

    @Test fun packagedManifestUsesSampleIdAndExpectedNetworkPermissions() {
        val info = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
        assertEquals(AdTestIds.APPLICATION, info.metaData.getString("com.google.android.gms.ads.APPLICATION_ID"))
        val requested = context.packageManager.getPackageInfo(context.packageName,
            PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toSet()
        for (permission in listOf("android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE",
                "android.permission.READ_BASIC_PHONE_STATE", "com.google.android.gms.permission.AD_ID")) {
            assertTrue("Missing declared permission: $permission", permission in requested)
        }
    }

    @Test fun realSampleConsentFailureOrDeadlineLeavesFileToolsAvailable() {
        // Opt-in network QA only. No production account or ad request is used.
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("ad-network") == "true")
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            // QA-only reset: the public Publisher Test Ads form must actually be exercised.
            UserMessagingPlatform.getConsentInformation(context).reset()
        }
        launch(); privacy()
        val beforePss = android.os.Debug.getPss()
        compose.onNodeWithTag("ad-privacy-refresh").performClick()
        compose.waitUntil(50_000) {
            var finished = false
            scenario!!.onActivity { activity ->
                val session = ViewModelProvider(activity)[AdPrivacyViewModel::class.java].session
                finished = session.state.value.phase != AdPrivacyPhase.CHECKING
            }
            if (!finished && hasProviderWebView()) declineSampleProviderForm()
            finished
        }
        scenario!!.onActivity { activity ->
            val state = ViewModelProvider(activity)[AdPrivacyViewModel::class.java].session.state.value
            java.io.File(context.getExternalFilesDir(null), "qa").apply { mkdirs() }
                .resolve("ad-consent-result.txt").writeText(
                    "$state\nSDK initialized=${MobileAds.isInitialized}\n" +
                    "Main process PSS KiB: before=$beforePss after=${android.os.Debug.getPss()}\n")
        }
        compose.onNodeWithText(context.getString(R.string.close)).performClick()
        scenario!!.onActivity { activity ->
            assertFalse(ViewModelProvider(activity)[AdPrivacyViewModel::class.java].session.canRequestAds())
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("compress-image-action"))
        compose.onNodeWithTag("compress-image-action").performClick()
    }

    private fun hasProviderWebView(): Boolean {
        fun contains(node: android.view.accessibility.AccessibilityNodeInfo?): Boolean {
            if (node == null) return false
            if (node.className == "android.webkit.WebView") return true
            return (0 until node.childCount).any { contains(node.getChild(it)) }
        }
        return contains(InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow)
    }

    private fun declineSampleProviderForm() {
        // Interact with the actual SDK-hosted test form; never set an IAB consent string.
        onView(isAssignableFrom(android.webkit.WebView::class.java)).inRoot(isDialog())
            .check { view, error ->
                if (error != null) throw error
                (view as android.webkit.WebView).evaluateJavascript("""
                    (function() {
                        const button = Array.from(document.querySelectorAll('button'))
                            .find(item => item.textContent.trim() === 'Do not consent');
                        if (button) { button.click(); return true; }
                        return false;
                    })();
                """.trimIndent(), null)
            }
    }
}

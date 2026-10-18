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
        compose.onNodeWithTag("ad-privacy-entry").performClick()
    }

    private fun showRealSampleBanner() {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("ad-network") == "true")
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            UserMessagingPlatform.getConsentInformation(context).reset()
            context.getSharedPreferences("ad_test_attempts", 0).edit().clear().commit()
        }
        launch(); privacy()
        compose.onNodeWithTag("ad-privacy-refresh").performClick()
        compose.waitUntil(50_000) {
            var finished = false
            scenario!!.onActivity { activity ->
                finished = ViewModelProvider(activity)[AdPrivacyViewModel::class.java]
                    .session.state.value.phase != AdPrivacyPhase.CHECKING
            }
            if (!finished && hasProviderWebView()) declineSampleProviderForm()
            finished
        }
        compose.onNodeWithTag("ad-show-test-banner").assertIsEnabled().performClick()
        compose.waitUntil(25_000) {
            compose.onAllNodesWithTag("home-test-banner-visible").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithTag("ad-banner-unavailable").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("home-test-banner-visible").assertIsDisplayed()
        compose.onNodeWithTag("ad-privacy-entry").assertIsDisplayed()
        compose.onNodeWithTag("home-open-file").assertIsDisplayed()
    }

    @Test fun realSampleBannerIsHomeOnlyAndStopsAfterRecreation() {
        showRealSampleBanner()
        val image = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        java.io.File(context.getExternalFilesDir(null), "qa").apply { mkdirs() }
            .resolve("home-test-banner.png").outputStream().use {
                assertTrue(image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
            }
        image.recycle()
        scenario!!.recreate(); compose.waitForIdle()
        compose.onNodeWithTag("home-test-banner").assertDoesNotExist()
        privacy()
        compose.onNodeWithTag("ad-privacy-status").assertTextEquals(context.getString(R.string.ad_not_started))
        compose.onNodeWithText(context.getString(R.string.close)).performClick()
        compose.onNodeWithTag("compress-image-action").performClick()
        compose.onNodeWithTag("home-test-banner").assertDoesNotExist()
    }

    @Test fun realSampleBannerStopsForHomePanelAndEditing() {
        showRealSampleBanner()
        compose.onNodeWithTag("home-new").performClick()
        compose.onNodeWithTag("home-test-banner").assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.close)).performClick()
        compose.onNodeWithTag("home-test-banner").assertDoesNotExist()
        compose.onNodeWithTag("compress-image-action").performClick()
        compose.onNodeWithTag("ad-privacy-entry").assertDoesNotExist()
        compose.onNodeWithTag("home-test-banner").assertDoesNotExist()
    }

    @Test fun realSampleBannerStopsInBackgroundAndDoesNotResumeAutomatically() {
        showRealSampleBanner()
        scenario!!.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        scenario!!.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        compose.waitForIdle()
        compose.onNodeWithTag("home-test-banner").assertDoesNotExist()
        scenario!!.onActivity { activity ->
            assertFalse(ViewModelProvider(activity)[AdPrivacyViewModel::class.java].session.canRequestAds())
        }
        compose.onNodeWithTag("home-open-file").assertIsDisplayed()
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
        // Exercise the actual SDK-hosted test form, never a synthesized IAB consent string.
        // Its native accessibility snapshot may outlive the dismissing WebView. The underlying
        // Compose privacy dialog stays available, so retry only that specific lookup race.
        try {
            onView(isAssignableFrom(android.webkit.WebView::class.java)).inRoot(isDialog())
                .check { view, error ->
                    if (error != null) throw error
                    (view as android.webkit.WebView).evaluateJavascript("""
                        (function() {
                            const button = Array.from(document.querySelectorAll('button'))
                                .find(item => item.textContent.trim() === 'Do not consent');
                            if (button) button.click();
                        })();
                    """.trimIndent(), null)
                }
        } catch (_: androidx.test.espresso.NoMatchingViewException) {
            // Eligibility and a visible real sample banner are still required by the wait.
        }
    }
}

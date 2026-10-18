package com.example.contentstudio

import android.webkit.WebView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.platform.app.InstrumentationRegistry
import com.example.contentstudio.advertising.*
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.UserMessagingPlatform
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Explicit account-network QA on dedicated emulators; never writes consent preferences. */
class AdAccountPrivacyDeviceTest {
    @get:Rule var compose: androidx.compose.ui.test.junit4.ComposeTestRule = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private var scenario: ActivityScenario<MainActivity>? = null
    private val evidence get() = java.io.File(context.getExternalFilesDir(null), "qa/account-privacy").apply { mkdirs() }

    @Before fun prepare() {
        Assume.assumeTrue("Account QA needs an explicit account debug build and network opt-in",
            BuildConfig.DEBUG && BuildConfig.AD_ACCOUNT_TESTING &&
                InstrumentationRegistry.getArguments().getString("ad-account-network") == "true")
        assertTrue("Only QA emulators are supported", android.os.Build.MODEL.contains("sdk", true))
        assertEquals("Runner region must match the installed APK", BuildConfig.AD_PRIVACY_REGION,
            InstrumentationRegistry.getArguments().getString("ad-account-region"))
        val language = InstrumentationRegistry.getArguments().getString("ad-ui-locale", "en-US")
        instrumentation.runOnMainSync {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                context.getSystemService(android.app.LocaleManager::class.java).applicationLocales =
                    android.os.LocaleList.forLanguageTags(language)
            } else androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                    androidx.core.os.LocaleListCompat.forLanguageTags(language))
            UserMessagingPlatform.getConsentInformation(context).reset()
            context.getSharedPreferences("ad_test_attempts", 0).edit().clear().commit()
            context.getSharedPreferences("ad_privacy_preferences", 0).edit().clear().commit()
        }
        assertEquals("ca-app-pub-5967660342681325~8485054661", AdTestIds.APPLICATION)
        assertEquals("ca-app-pub-3940256099942544/9214589741", AdTestIds.BANNER)
    }

    @After fun close() { scenario?.close() }

    /** Shared fixture for opt-in same-process workloads, using the real visible Home flow. */
    internal fun prepareResidentAdvertising(protectedUser: Boolean) {
        prepare()
        assertFalse("Resident QA must start in a fresh process", MobileAds.isInitialized)
        if (protectedUser) protectedBanner(AdAudienceGroup.AGE_13_15) else homePreviewBanner()
        screenshot("resident-${if (protectedUser) "protected" else "adult"}")
        scenario!!.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        compose.waitForIdle()
        compose.onNodeWithTag("home-test-banner").assertDoesNotExist()
        assertFalse("Backgrounded UI cannot authorize another ad", state().phase == AdPrivacyPhase.READY)
        scenario!!.close()
        scenario = null
        assertTrue("Closing the banner does not unload the SDK", MobileAds.isInitialized)
    }

    private fun launchAndCheck() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(20_000) {
            compose.onAllNodesWithTag("ad-privacy-entry").fetchSemanticsNodes().isNotEmpty()
        }
        assertFalse("Normal Home must not initialize advertising", MobileAds.isInitialized)
        compose.onNodeWithTag("ad-privacy-entry").performClick()
        compose.onNodeWithTag("ad-privacy-refresh").performClick()
    }

    private fun launchHomePreview(group: AdAudienceGroup? = AdAudienceGroup.ADULT) {
        assertTrue("Home interaction preview must be explicitly enabled", BuildConfig.AD_HOME_PREVIEW)
        assertEquals("eea", BuildConfig.AD_PRIVACY_REGION)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(20_000) {
            compose.onAllNodesWithTag("ad-age-adult").fetchSemanticsNodes().isNotEmpty()
        }
        if (group != null) chooseAge(group)
    }

    private fun chooseAge(group: AdAudienceGroup) {
        compose.onNodeWithTag("ad-age-${group.name.lowercase()}").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            var saved = false
            scenario!!.onActivity { saved = ViewModelProvider(it)[AdPrivacyViewModel::class.java]
                .audience.state.value.let { choice -> choice.group == group && !choice.saving } }
            saved
        }
    }

    private fun homePreviewBanner() {
        launchHomePreview()
        form("home-flow-initial")
        assertFalse("No SDK initialization before the required choice", MobileAds.isInitialized)
        click("Do not consent"); ready("home-flow-rejected")
        compose.waitUntil(25_000) {
            compose.onAllNodesWithTag("home-test-banner-visible").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("home-test-banner-visible").assertIsDisplayed()
        compose.onNodeWithTag("home-open-file").assertIsDisplayed()
        compose.onNodeWithTag("ad-privacy-entry").assertIsDisplayed()
    }

    private fun state(): AdPrivacyState {
        var result = AdPrivacyState()
        scenario!!.onActivity { result = ViewModelProvider(it)[AdPrivacyViewModel::class.java].session.state.value }
        return result
    }

    private fun hasWebView(): Boolean {
        fun contains(node: android.view.accessibility.AccessibilityNodeInfo?): Boolean {
            if (node == null) return false
            if (node.className == WebView::class.java.name) return true
            return (0 until node.childCount).any { contains(node.getChild(it)) }
        }
        return contains(instrumentation.uiAutomation.rootInActiveWindow)
    }

    private fun javascript(script: String): String {
        val done = CountDownLatch(1)
        var result = "null"
        onView(org.hamcrest.Matchers.allOf(isAssignableFrom(WebView::class.java),
            androidx.test.espresso.matcher.ViewMatchers.isDisplayed())).inRoot(isDialog()).check { view, error ->
            if (error != null) throw error
            (view as WebView).evaluateJavascript(script) { result = it; done.countDown() }
        }
        assertTrue("Provider DOM callback", done.await(5, TimeUnit.SECONDS))
        return result
    }

    private fun form(name: String) {
        compose.waitUntil(40_000) { hasWebView() || state().phase == AdPrivacyPhase.UNAVAILABLE }
        assertTrue("Expected the account's published form; current=${state()}", hasWebView())
        var content = JSONObject()
        compose.waitUntil(10_000) {
            try {
                val json = javascript("""
                    (function() {
                      const visible = e => e.getClientRects().length > 0 &&
                        getComputedStyle(e).visibility !== 'hidden';
                      return JSON.stringify({brand: document.body.innerText.includes('Birlevo'),
                        buttons: Array.from(document.querySelectorAll('button')).filter(visible)
                          .map(b => b.textContent.trim()).filter(Boolean),
                        toggles: Array.from(document.querySelectorAll('input')).filter(visible)
                          .map(i => ({type:i.type, checked:i.checked,
                            label: Array.from(i.labels || []).map(l => l.innerText.trim()).join(' ')}))});
                    })()
                """.trimIndent())
                content = JSONObject(JSONTokener(json).nextValue() as String)
                (BuildConfig.AD_PRIVACY_REGION != "eea" || content.optBoolean("brand")) &&
                    content.getJSONArray("buttons").length() > 0
            } catch (_: androidx.test.espresso.NoMatchingViewException) { false }
        }
        evidence.resolve("$name-form.json").writeText(content.toString(2))
        screenshot("$name-form")
        if (BuildConfig.AD_PRIVACY_REGION == "eea")
            assertTrue("The EEA form must identify Birlevo, not Publisher Test Ads", content.getBoolean("brand"))
    }

    private fun click(label: String) {
        val selected = javascript("""
            (function() {
              const button = Array.from(document.querySelectorAll('button'))
                .find(b => b.getClientRects().length > 0 && getComputedStyle(b).visibility !== 'hidden' &&
                  b.textContent.trim() === ${JSONObject.quote(label)});
              if (!button) return false;
              button.click(); return true;
            })()
        """.trimIndent())
        assertEquals("Expected provider button: $label", "true", selected)
    }

    private fun ready(name: String) {
        compose.waitUntil(40_000) { state().phase in setOf(AdPrivacyPhase.READY, AdPrivacyPhase.UNAVAILABLE) }
        report(name)
        assertEquals(AdPrivacyPhase.READY, state().phase)
        scenario!!.onActivity { assertTrue(ViewModelProvider(it)[AdPrivacyViewModel::class.java].session.canRequestAds()) }
        assertTrue(MobileAds.isInitialized)
        assertEquals(com.google.android.libraries.ads.mobile.sdk.common.RequestConfiguration.MaxAdContentRating.MAX_AD_CONTENT_RATING_G,
            MobileAds.getRequestConfiguration().maxAdContentRating)
    }

    private fun openOptions() {
        var attempts = 0
        do {
            compose.onNodeWithTag("ad-privacy-options").assertIsEnabled().performClick()
            scenario!!.onActivity { assertFalse(ViewModelProvider(it)[AdPrivacyViewModel::class.java].session.canRequestAds()) }
            compose.waitUntil(20_000) { hasWebView() || state().phase != AdPrivacyPhase.CHECKING }
            if (hasWebView()) return
            report("options-retry-${++attempts}")
            assertTrue("Options entry must remain available after a preload failure", state().optionsRequired)
            // UMP can report that automatic preloading is still in progress. Model another
            // explicit user tap; the application itself must not show a form automatically.
            android.os.SystemClock.sleep(1_000)
        } while (attempts < 3)
        fail("Account privacy options were unavailable after explicit retries")
    }

    private fun report(name: String) {
        val info = UserMessagingPlatform.getConsentInformation(context)
        val preferences = context.getSharedPreferences(context.packageName + "_preferences", 0)
        evidence.resolve("$name.json").writeText(JSONObject().apply {
            put("region", BuildConfig.AD_PRIVACY_REGION)
            put("application", AdTestIds.APPLICATION)
            put("banner", AdTestIds.BANNER)
            put("phase", state().phase.name)
            put("consentStatus", info.consentStatus)
            put("optionsRequired", state().optionsRequired)
            put("canRequestAds", info.canRequestAds())
            put("sdkInitialized", MobileAds.isInitialized)
            // Only aggregate choice indicators; no identifiers or raw TCF/GPP strings.
            put("tcfPresent", !preferences.getString("IABTCF_TCString", "").isNullOrEmpty())
            put("purposeConsents", preferences.getString("IABTCF_PurposeConsents", ""))
            put("gppPresent", !preferences.getString("IABGPP_HDR_GppString", "").isNullOrEmpty())
            put("usSaleOptOut", preferences.all["IABGPP_7_SaleOptOut"])
            put("usSharingOptOut", preferences.all["IABGPP_7_SharingOptOut"])
        }.toString(2))
    }

    private fun screenshot(name: String) {
        // DOM completion precedes the compositor's first frame. Preserve an actual rendered view.
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(2_000)
        val image = instrumentation.uiAutomation.takeScreenshot()
        evidence.resolve("$name.png").outputStream().use {
            assertTrue(image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
        }
        image.recycle()
    }

    private fun closePanelAndUseTools() {
        compose.onNodeWithText(context.getString(R.string.close)).performClick()
        scenario!!.onActivity { assertFalse(ViewModelProvider(it)[AdPrivacyViewModel::class.java].session.canRequestAds()) }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("compress-image-action"))
        compose.onNodeWithTag("compress-image-action").performClick()
        compose.onNodeWithTag("ad-privacy-entry").assertDoesNotExist()
    }

    @Test fun eeaRejectAndReopenChoicesThenAccept() {
        assertEquals("eea", BuildConfig.AD_PRIVACY_REGION)
        launchAndCheck(); form("eea-reject"); click("Do not consent"); ready("eea-rejected")
        assertEquals("00000000000", purposeConsents())
        assertTrue(state().optionsRequired)
        assertEquals(ConsentInformation.ConsentStatus.OBTAINED,
            UserMessagingPlatform.getConsentInformation(context).consentStatus)
        openOptions()
        form("eea-options"); click("Consent"); ready("eea-accepted-after-reject")
        assertEquals("11111111111", purposeConsents())
        closePanelAndUseTools()
    }

    @Test fun eeaAcceptThenWithdrawAndSampleBanner() {
        assertEquals("eea", BuildConfig.AD_PRIVACY_REGION)
        launchAndCheck(); form("eea-accept"); click("Consent"); ready("eea-accepted")
        assertEquals("11111111111", purposeConsents())
        openOptions()
        form("eea-withdraw"); click("Do not consent"); ready("eea-withdrawn")
        assertEquals("00000000000", purposeConsents())
        compose.onNodeWithTag("ad-show-test-banner").assertIsEnabled().performClick()
        compose.waitUntil(25_000) {
            compose.onAllNodesWithTag("home-test-banner-visible").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithTag("ad-banner-unavailable").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("home-test-banner-visible").assertIsDisplayed()
        screenshot("account-sample-banner")
        compose.onNodeWithTag("ad-privacy-entry").performClick()
        compose.onNodeWithTag("home-test-banner").assertDoesNotExist()
        compose.onNodeWithTag("ad-privacy-status").assertTextEquals(context.getString(R.string.ad_not_started))
        closePanelAndUseTools()
    }

    private fun purposeConsents() = context.getSharedPreferences(context.packageName + "_preferences", 0)
        .getString("IABTCF_PurposeConsents", "")

    @Test fun unavailableProviderLeavesToolsUsable() {
        val connectivity = context.getSystemService(android.net.ConnectivityManager::class.java)
        @Suppress("DEPRECATION")
        assertTrue("Offline QA must actually have no connected network", connectivity.activeNetworkInfo?.isConnected != true)
        launchAndCheck()
        compose.waitUntil(25_000) { state().phase == AdPrivacyPhase.UNAVAILABLE }
        assertFalse(MobileAds.isInitialized)
        report("offline-unavailable")
        closePanelAndUseTools()
    }

    @Test fun otherRegionDoesNotForceEuropeanForm() {
        assertEquals("other", BuildConfig.AD_PRIVACY_REGION)
        launchAndCheck()
        compose.waitUntil(40_000) { hasWebView() || state().phase != AdPrivacyPhase.CHECKING }
        assertFalse("A regional message must not be forced globally", hasWebView())
        ready("other-region")
        closePanelAndUseTools()
    }

    @Test fun regulatedUsChoicesCanBeReopened() {
        assertEquals("us", BuildConfig.AD_PRIVACY_REGION)
        launchAndCheck()
        // The account may require the initial US notice or only the privacy-options entry.
        compose.waitUntil(40_000) { hasWebView() || state().phase != AdPrivacyPhase.CHECKING }
        if (hasWebView()) { form("us-initial"); click("Save and close") }
        ready("us-updated")
        assertTrue("Published US message must expose privacy options", state().optionsRequired)
        val before = gpp()
        openOptions()
        form("us-options"); chooseUsOptOut(true); click("Save and close"); ready("us-opted-out")
        val rejected = gpp()
        assertTrue("The provider must save the selected US preference", rejected.isNotEmpty() && rejected != before)
        openOptions(); form("us-reopened")
        assertEquals("true", javascript("""
            Array.from(document.querySelectorAll('input[type=radio]')).some(i => i.checked &&
              Array.from(i.labels || []).some(l => l.innerText.includes("Don't sell or share my data")))
        """.trimIndent()))
        chooseUsOptOut(false); click("Save and close"); ready("us-allowed-after-opt-out")
        assertNotEquals(rejected, gpp())
        closePanelAndUseTools()
    }

    private fun gpp() = context.getSharedPreferences(context.packageName + "_preferences", 0)
        .getString("IABGPP_HDR_GppString", "").orEmpty()

    private fun chooseUsOptOut(reject: Boolean) {
        val label = if (reject) "Don't sell or share my data" else "Allow the sale or sharing of my data"
        assertEquals("Expected the visible US preference radio", "true", javascript("""
            (function() {
              const radio = Array.from(document.querySelectorAll('input[type=radio]'))
                .find(i => i.getClientRects().length > 0 &&
                  Array.from(i.labels || []).some(l => l.innerText.includes(${JSONObject.quote(label)})));
              if (!radio) return false;
              radio.click(); return radio.checked;
            })()
        """.trimIndent()))
    }

    @Test fun homePreviewRemovesBannerBeforePrivacyChangesAndRespectsReturnCooldown() {
        homePreviewBanner(); screenshot("home-flow-banner")
        compose.onNodeWithTag("ad-privacy-entry").performClick()
        compose.onNodeWithTag("home-test-banner").assertDoesNotExist()
        compose.onNodeWithTag("ad-privacy-options").performScrollTo().performClick()
        form("home-flow-options"); click("Consent"); ready("home-flow-options-saved")
        compose.onNodeWithTag("home-test-banner").assertDoesNotExist()
        compose.onNodeWithTag("compress-image-action").performClick()
        compose.onNodeWithTag("ad-privacy-entry").assertDoesNotExist()
        compose.onNodeWithTag("home-test-banner").assertDoesNotExist()
        androidx.test.espresso.Espresso.pressBack()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("ad-privacy-entry").fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
        compose.onNodeWithTag("home-test-banner").assertDoesNotExist()
        assertEquals(AdPrivacyPhase.IDLE, state().phase)
        assertFalse(hasWebView())
    }

    @Test fun homePreviewBackgroundAndRecreationDoNotRestartRequests() {
        homePreviewBanner()
        scenario!!.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        scenario!!.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        compose.waitForIdle()
        compose.onNodeWithTag("home-test-banner").assertDoesNotExist()
        assertEquals(AdPrivacyPhase.IDLE, state().phase)
        scenario!!.recreate(); compose.waitForIdle()
        compose.onNodeWithTag("ad-privacy-entry").assertIsDisplayed()
        compose.onNodeWithTag("home-test-banner").assertDoesNotExist()
        assertEquals(AdPrivacyPhase.IDLE, state().phase)
        assertFalse(hasWebView())
    }

    @Test fun homePreviewOfflineKeepsPrivacyAndFileToolsAvailable() {
        val connectivity = context.getSystemService(android.net.ConnectivityManager::class.java)
        @Suppress("DEPRECATION")
        assertTrue(connectivity.activeNetworkInfo?.isConnected != true)
        launchHomePreview()
        compose.waitUntil(25_000) { state().phase == AdPrivacyPhase.UNAVAILABLE }
        assertFalse(MobileAds.isInitialized)
        compose.onNodeWithTag("home-test-banner").assertDoesNotExist()
        compose.onNodeWithTag("ad-privacy-entry").assertIsEnabled()
        compose.onNodeWithTag("compress-image-action").performClick()
        compose.onNodeWithTag("ad-privacy-entry").assertDoesNotExist()
    }

    @Test fun homePreviewUnknownAgeAndDecliningAgeNeverCreateConsentSession() {
        launchHomePreview(null)
        for (group in AdAudienceGroup.entries.filter { it != AdAudienceGroup.UNSET })
            compose.onNodeWithTag("ad-age-${group.name.lowercase()}").assertIsNotSelected()
        assertFalse(MobileAds.isInitialized)
        scenario!!.onActivity { assertFalse(ViewModelProvider(it)[AdPrivacyViewModel::class.java].hasConsentSession) }
        compose.onNodeWithText(context.getString(R.string.close)).performClick()
        compose.waitUntil(10_000) {
            context.getSharedPreferences("ad_privacy_preferences", 0).getString("age_group", null) == "UNDISCLOSED"
        }
        scenario!!.recreate(); compose.waitForIdle()
        scenario!!.onActivity { assertFalse(ViewModelProvider(it)[AdPrivacyViewModel::class.java].hasConsentSession) }
        assertFalse(MobileAds.isInitialized)
        compose.onNodeWithTag("compress-image-action").performClick()
    }

    @Test fun homePreviewUnder13KeepsToolsWithoutConsentOrAdRequests() {
        launchHomePreview(AdAudienceGroup.UNDER_13)
        scenario!!.onActivity { assertFalse(ViewModelProvider(it)[AdPrivacyViewModel::class.java].hasConsentSession) }
        assertFalse(MobileAds.isInitialized)
        compose.onNodeWithTag("home-test-banner").assertDoesNotExist()
        compose.onNodeWithTag("ad-privacy-entry").assertIsEnabled()
        compose.onNodeWithTag("compress-image-action").performClick()
    }

    @Test fun homePreviewAgeChoicesAreAccessibleAndPersistAcrossNewActivity() {
        launchHomePreview(null)
        screenshot("age-dialog-first")
        for (group in AdAudienceGroup.entries.filter { it != AdAudienceGroup.UNSET }) {
            val row = compose.onNodeWithTag("ad-age-${group.name.lowercase()}")
            row.performScrollTo().assertIsDisplayed().assertIsNotSelected().assertHasClickAction()
                .assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.Role,
                    androidx.compose.ui.semantics.Role.RadioButton))
            val bounds = row.getUnclippedBoundsInRoot()
            assertTrue("At least 48 dp touch height", (bounds.bottom - bounds.top).value >= 48f)
        }
        screenshot("age-dialog-last")
        chooseAge(AdAudienceGroup.UNDISCLOSED)
        scenario!!.close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("ad-privacy-entry").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("ad-age-adult").assertDoesNotExist()
        scenario!!.onActivity {
            val model = ViewModelProvider(it)[AdPrivacyViewModel::class.java]
            assertEquals(AdAudienceGroup.UNDISCLOSED, model.audience.state.value.group)
            assertFalse(model.hasConsentSession)
        }
        assertFalse(MobileAds.isInitialized)
        compose.onNodeWithTag("home-open-file").assertIsDisplayed()
    }

    private fun protectedBanner(group: AdAudienceGroup) {
        launchHomePreview(group)
        ready("protected-${group.name.lowercase()}")
        assertFalse("Protected users must not get an adult consent form", hasWebView())
        val config = MobileAds.getRequestConfiguration()
        assertEquals(com.google.android.libraries.ads.mobile.sdk.common.AgeRestrictedTreatment.CHILD,
            config.ageRestrictedTreatment)
        assertEquals(com.google.android.libraries.ads.mobile.sdk.common.RequestConfiguration.PublisherPrivacyPersonalizationState.DISABLED,
            config.publisherPrivacyPersonalizationState)
        compose.waitUntil(25_000) {
            compose.onAllNodesWithTag("home-test-banner-visible").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("home-open-file").assertIsDisplayed()
    }

    @Test fun homePreviewYoungTeenUsesProtectedRequestsWithoutAdultConsent() {
        protectedBanner(AdAudienceGroup.AGE_13_15)
        screenshot("young-teen-sample-banner")
    }

    @Test fun homePreviewOlderTeenCanDisableAdsByWithholdingAge() {
        protectedBanner(AdAudienceGroup.AGE_16_17)
        compose.onNodeWithTag("ad-privacy-entry").performClick()
        chooseAge(AdAudienceGroup.UNDISCLOSED)
        compose.onNodeWithTag("home-test-banner").assertDoesNotExist()
        assertFalse(hasWebView())
        scenario!!.onActivity { assertFalse(ViewModelProvider(it)[AdPrivacyViewModel::class.java].session.canRequestAds()) }
        scenario!!.recreate(); compose.waitForIdle()
        compose.onNodeWithTag("home-test-banner").assertDoesNotExist()
        scenario!!.onActivity { assertEquals(AdAgeTreatment.BLOCKED,
            ViewModelProvider(it)[AdPrivacyViewModel::class.java].currentTreatment()) }
    }

    @Test fun homePreviewAdultToTeenCannotReuseAnAdultPrivacyForm() {
        homePreviewBanner()
        compose.onNodeWithTag("ad-privacy-entry").performClick()
        chooseAge(AdAudienceGroup.AGE_13_15)
        compose.onNodeWithTag("home-test-banner").assertDoesNotExist()
        compose.onNodeWithTag("ad-privacy-entry").performClick()
        compose.onNodeWithTag("ad-privacy-options").performScrollTo().performClick()
        ready("adult-to-protected")
        assertFalse(hasWebView())
        assertEquals(com.google.android.libraries.ads.mobile.sdk.common.AgeRestrictedTreatment.CHILD,
            MobileAds.getRequestConfiguration().ageRestrictedTreatment)
        compose.onNodeWithTag("home-test-banner").assertDoesNotExist()
    }
}

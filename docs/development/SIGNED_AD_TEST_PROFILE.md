# Signed sample-ad profile

The ordinary release profile keeps advertising disabled. An explicit **`birlevo.releaseTestAds=true`** opt-in creates an upload-signed, non-debuggable, R8/resource-shrunk internal-test candidate that can display Google's sample adaptive banner on physical devices as well as emulators. It does not enable the owner's live banner unit or monetization.

The profile uses the owner's AdMob application ID for the actual published UMP messages, Google's sample banner ID `ca-app-pub-3940256099942544/9214589741`, `AD_PRODUCTION=false`, and `AD_TESTING`, `AD_RELEASE_TESTING`, `AD_ACCOUNT_TESTING`, `AD_HOME_PREVIEW=true`. Signed builds use the actual region, without UMP debug geography or hardcoded physical test-device IDs. Ordinary development preview properties cannot enable this signed mode. The older debug Home preview retains its emulator boundary.

```powershell
tools/release/build-signed-release.ps1 -TestAds
python tools/testing/verify_ad_build_configuration.py --release-test-ads --report output/development/ad-test-build-metadata.json
```

The verifier checks generated variant metadata and the merged manifest; it does not by itself establish that an APK was built or that a network ad loaded. A release record must separately identify and qualify the exact signed artifacts. The test APK exposes banner phase test tags as accessibility resource IDs only in test-ad builds, so the independent platform runner can assert the real successful-load state after R8 without depending on application class names.

The existing [age and privacy flow](HOME_AD_PRIVACY_FLOW.md) remains in control. First Home entry asks for an optional age choice. Under-13, undisclosed, unsaved and failed-save states cannot start the application's consent/ad session. Teen requests use the protected configuration. Eligible adults complete the actual regional UMP flow when required. The application requests a banner only after the current consent check permits it; provider failure leaves tools available. Closing preferences, leaving Home, backgrounding or withdrawing an eligible choice removes the banner. No media or filename targeting, new placement, full-screen format or automatic retry is added.

The sample build has the same SDK data-disclosure obligations as its SDK behavior; a sample ad unit is not a claim of zero data collection. Keep the Play advertising, advertising-ID, Data safety and published privacy declarations aligned with the exact candidate. The signed sample profile is for internal testing; live activation and broader distribution remain separate steps.

For exact signed-candidate checks, the independent release runner has `ad-test-blocked`, `ad-test-banner`, `ad-test-protected` and `ad-test-media` workflows. Start the blocked-audience check with fresh QA data, before any initialized-SDK check. They exercise real age selection, actual provider forms where regionally required, real sample-banner callbacks, preference/withdrawal removal and selected media actions in the SDK-resident process. The fixture does not fabricate consent strings. Only the dedicated QA emulator may be reset.

Official references: Google's [sample ad units](https://developers.google.com/admob/android/next-gen/test-ads), [UMP setup and current consent checks](https://developers.google.com/admob/android/privacy), and [age-related request configuration](https://developers.google.com/admob/android/next-gen/targeting). The public sample unit is independent of the owner's live unit readiness; regional UMP messages still come from the configured application ID.

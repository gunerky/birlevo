# Account privacy message qualification

18 October 2026. This follows the [sample Home banner](ADVERTISING_PLACEMENTS.md) and [website publication](../releases/ADVERTISING_SITE_SETUP.md). The owner chose English-only account messages and reported completing the European/US configuration. This step verifies the messages returned by UMP on dedicated QA emulators; it does not approve the account, publish an Android build or enable production advertisements.

## Configuration and boundaries

- Ordinary debug builds keep Google's sample application and adaptive-banner unit. A developer must explicitly pass `'-Pbirlevo.adPrivacyAccount=true'` to use Birlevo's registered application ID for UMP and SDK initialization together. The manifest and SDK initialization share the same generated build-type value.
- `'-Pbirlevo.adPrivacyRegion=eea'`, `us` or `other` selects UMP's documented test geography. The default is `eea`. These settings apply only to debug testing on recognized test devices; emulators are test devices by default. Invalid property values fail configuration.
- The banner unit remains Google's sample `ca-app-pub-3940256099942544/9214589741` in every development profile. Birlevo's revenue unit is not used. There is no remote/runtime switch, identifier credential or live-ad test request.
- Release hardcodes `AD_TESTING=false`, `AD_ACCOUNT_TESTING=false`, the sample application ID and disabled test geography, even when the account properties are supplied to Gradle. Only release BuildConfig/manifest generation is used to inspect this boundary; no release APK/AAB, version increment or signing task is needed.
- Normal Home startup and opening the privacy panel still do not initialize UMP, Mobile Ads or WorkManager. Check privacy and Show test banner remain explicit user actions. Current provider eligibility, view destruction, cancellation, cooldown and media boundaries are unchanged.
- UMP can reject an early privacy-options tap while automatically preloading that form. The existing unavailable state removes ad eligibility and preserves the choices entry. QA models another explicit user tap and records the initial unavailable result; the app does not automatically show a form or retry an ad.

## Reproduction

Commands run from the repository root. The network switch explicitly authorizes requests to Google's consent/ad services for these tests. No selected media or project contents are supplied to them.

```powershell
./gradlew.bat '-Pbirlevo.adPrivacyAccount=true' '-Pbirlevo.adPrivacyRegion=eea' :app:assembleDebug :app:assembleDebugAndroidTest
./tools/testing/account-privacy-qa.ps1 -Serial emulator-5580 -Api 36 -Region eea -Network -Offline
./tools/testing/account-privacy-qa.ps1 -Serial emulator-5582 -Api 24 -Region eea -Network -Offline
```

Build and run the same commands with region `us`, then `other`, omitting `-Offline`. Each region must match the installed APK. The runner rejects unexpected AVD names/APIs and skipped account tests. Consent reset is instrumentation-only. Tests read aggregate TCF/GPP evidence but never synthesize consent strings or write the selected preferences themselves.

The API 24 image's `svc wifi` command exits 137; disabling mobile data alone leaves its Wi-Fi connected. The runner instead discovers the actual checked Settings switch through a fresh UI hierarchy and taps its observed bounds, then verifies the switch. It restores the previous Wi-Fi/mobile-data setting in `finally`. The test independently requires no connected network before accepting an offline result. Newer Android uses its supported service commands. Both paths affect only dedicated QA emulators.

Use `home-banner-qa.ps1 -Review account-privacy-common` without `-Network` for the four startup/panel/recreation/manifest checks on the account build; use a separate review name for the default sample build so historical evidence is retained. Ordinary debug builds need no account properties.

## Accepted verification

All four debug application profiles below are **40,674,921 bytes**, version 0.1.11 / code 12 for development. They are separate test artifacts, not one production candidate. Frozen APKs, matching instrumentation APKs, case logs and `verification.json` are under ignored `output/development/account-privacy/`.

| Application profile | SHA-256 |
| --- | --- |
| Birlevo account, EEA | `f462faf84956198a0c8fbd5221b1da0935f8e4364f96b9c3ccafd9a3824eb75a` |
| Birlevo account, regulated US | `2cf5bbd2d8831d5ee249c93d4752fcf8b5d339798ca0ab05d58e1380f6269422` |
| Birlevo account, other | `64b06e858cbc0b6df383ccac673f32c5949dfb5b380bf92e1e835c4aff4ad6c5` |
| Default sample account | `bbe921fac95e715a93ff915ccc5950e6c143dab29db3c3f10f65a8d1e34fef2f` |

**26 emulator checks passed, with no skips: 13 on API 24 and 13 on API 36.** Each instrumentation case starts a fresh process. Per API this comprises:

- Three EEA cases: reject then reopen/accept; accept then reopen/withdraw and load the actual sample banner; disconnected-provider failure with file tools still available and no SDK initialization. The first two verify the provider's real stored TCF purpose bits change between all-zero and all-one decisions.
- One regulated-US case: the account exposes required privacy options; the real visible radio and **Save and close** button save the opt-out, reopening retains that selection, and changing it again updates the provider's GPP string. The US form identifies the app with its logo rather than a Birlevo text heading; the build's manifest/SDK IDs are independently checked. No GPP/TCF strings are synthesized or written by QA.
- One other-region case: the tested account returns eligibility without forcing a European/US form. This is one SDK region simulation, not a country-by-country claim.
- Four account-build and four default-build checks: no SDK/WorkManager startup, panel opening without a request, recreation without a request, and matching packaged application ID/sample banner plus the expected permission set.

Actual EEA/US form and sample-banner screenshots were inspected on both APIs. Rejection/withdrawal can still yield `canRequestAds=true`; that is eligibility for the applicable request, not personalized-ad opt-in. The sample unit is used even after opting out. A failed options load leaves the application session ineligible despite an older provider eligibility value.

Debug app/test builds and **176 JVM tests** pass with no failures/errors/skips. Final lint is **0 errors / 7 existing warnings / 0 hints**; none are suppressed. Exact runtime coverage remains **132 reviewed artifacts plus the existing native encoder**, with no dependency changes. The exact EEA APK passes eight MP3 encoder/bridge libraries across four ABIs, 16 KiB LOAD/RELRO, dynamic linkage, source and notice checks. The development-tool isolation guard passes 395 source/build files and the exact default APK; no Jev API call occurs. All 22 existing locale resources pass generation/key/placeholder checks; no new application strings are added.

Account-opt-in release BuildConfig and manifest snapshots confirm disabled advertising, disabled geography and the sample ID. The frozen signed 0.1.11 package remains SHA-256 `d1924881005d06aa7f4df643433938d3c183572dcfcb406ea765945720c5f881`. Graphify's code-only update has 4,721 nodes / 12,770 edges; the 27 existing partial Kotlin-parser warnings are not treated as compilation results.

Initial diagnostic logs retain the old Wi-Fi command failure, the mobile-only attempt that correctly failed the independent offline assertion, early US form preloading, and test assumptions about branding/button text. Final QA uses the actual visible form and correct controls. Screenshots wait for composition instead of accepting the earlier underlying-dialog image. No production behavior is weakened to make these tests pass. QA networks are restored; the dedicated emulators remain available for the following placement work.

## Remaining production gates

This report records the first, frozen account-QA artifacts. The subsequent [Home flow and age-protection preview](HOME_AD_PRIVACY_FLOW.md) implements the persistent entry and the owner-confirmed 13+ age policy. The next development step is same-process initialized-SDK/media workflow qualification. Final live unit refresh remains a pre-activation decision. The public store connection, app-ads.txt crawling/readiness and final Play disclosures are separate account/release checks. Do not infer full AdMob approval, every-country legal compliance, physical-device resource behavior or complete TalkBack speech coverage from these debug tests. Final R8, native symbols and signed-candidate checks remain tied to an explicit release request.

## Official references

- [UMP lifecycle, test devices and geography](https://developers.google.com/admob/android/next-gen/privacy).
- [Privacy-options loading and user-action requirement](https://developers.google.com/admob/android/reference/privacy/com/google/android/ump/UserMessagingPlatform).
- [Test ads and sample banner identifier](https://developers.google.com/admob/android/next-gen/test-ads).

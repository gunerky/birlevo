# Home advertising and protected age choices

18 October 2026. The owner confirmed Play target groups **13–15, 16–17 and 18+**, and explicitly chose to prepare protected advertising for teenagers. This implements the Home interaction as an opt-in **emulator-only debug preview**, following [account message qualification](ADVERTISING_ACCOUNT_QA.md). It does not activate production advertising or change the frozen signed 0.1.11 package.

## User flow

The first quiet Home visit offers five equally presented age choices with no preselection: under 13, 13–15, 16–17, 18+, and prefer not to say. Closing without choosing stores the last option. No birth date, precise age, account or access to file tools is required. The Ad privacy text entry remains on Home for later changes. Existing adult choices are preserved when that dialog is simply closed.

| Choice | Request policy |
| --- | --- |
| Missing, invalid, saving, failed save, under 13, undisclosed | No application consent session or new ad request |
| 13–15 or 16–17 | UMP under-age-of-consent flag; SDK `AgeRestrictedTreatment.CHILD`, personalization disabled, maximum content rating G |
| 18+ | Fresh regional UMP eligibility and required form; maximum content rating G |

Both teen groups deliberately receive the stronger under-consent protection rather than varying it by country. This is a conservative product choice; it does not add under-13 users to the declared target audience. UMP does not forward its age flag to the ads SDK, so both are configured. Protected users are never asked to complete an adult consent form. If the provider cannot establish eligibility, advertising stays unavailable.

The age-group enum is stored in app-private preferences only after a serialized successful write. It is not supplied as a custom ad-targeting field. The derived under-age/treatment flags are supplied to Google; this is not a claim that the advertising SDK processes no data. Pending, failed and obsolete writes cannot enable a request in the current session. A fresh Activity reads the acknowledged choice. A change that has not been durably acknowledged is not promised to survive process termination or a storage failure.

Changing age first detaches the session and applies restrictive SDK configuration. A policy revision prevents earlier provider callbacks/eligibility or an adult privacy form from authorizing the new choice. Initialization begins with the strict age configuration and reapplies the latest policy afterward. Google documents that disabling publisher personalization can remain restrictive for the process lifetime; switching back to adult does not promise immediate personalized ads. A previously initialized SDK cannot be unloaded or its past traffic undone; blocked choices stop subsequent application requests and remove the banner.

## Placement and lifecycle

Only a Home adaptive sample banner is used. There are no full-screen, app-open or rewarded formats, no file-content targeting, preload or automatic retry. Active processing, an open Home panel, a privacy change, leaving Home, backgrounding and recreation remove eligibility and the view. Returning does not automatically resurrect the old banner. Privacy controls stay available after a provider error.

Automatic Home privacy checks and application banner attempts each have a persisted 60-second cooldown. These are application attempt limits, not a claim about server-configured unit refresh. Opening preferences suppresses automatic advertising for the rest of that Home visit; a provider-options tap is an explicit action. This keeps advertisements out of the editing workflow. The final live unit refresh setting remains an activation gate.

## Reproduction and build boundary

```powershell
./gradlew.bat '-Pbirlevo.adPrivacyAccount=true' '-Pbirlevo.adPrivacyRegion=eea' '-Pbirlevo.adHomePreview=true' :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug
./tools/testing/account-privacy-qa.ps1 -Serial emulator-5580 -Api 36 -Region eea -Network -HomePreview -Offline
./tools/testing/account-privacy-qa.ps1 -Serial emulator-5582 -Api 24 -Region eea -Network -HomePreview -Offline
./tools/testing/ad-age-ui-qa.ps1 -Serial emulator-5580
```

The sample banner unit is hardcoded in all profiles. `AD_HOME_PREVIEW` additionally requires debug, test advertising and emulator hardware. Ordinary debug retains the manually operated test panel, whose adult QA scenario is not a production age screen. Release hardcodes preview/account/testing flags false and the sample application ID even if all development properties are supplied. Only release configuration/manifest generation is inspected; no release APK/AAB is built.

## Verification

The EEA Home-preview application is **40,692,433 bytes**, SHA-256 `5313ec5e592e1dba60d06cad3ab98f3266f57543af0aa1523bbac11414ceceee`. Ignored `output/development/home-ad-flow/` retains the application/instrumentation artifacts, exact-case logs, screenshots and initial test-compilation diagnostic. No provider consent strings are fabricated by tests.

**16 request/lifecycle emulator checks pass without skips**, eight each on API 24 and API 36: actual adult form and sample banner; removal before changing preferences and Home return cooldown; background/recreation; unknown/declined age without an application consent session; under-13 tools without ads; each teen group with an actual protected sample banner; adult-to-teen stale-form prevention; actual disconnected-provider failure with tools still usable. The fixture resets UMP for repeatability; the blocked-user assertion concerns the application model's uncreated consent session, not the test fixture's own UMP object.

**182 JVM tests pass**, including stale options, failed writes, superseded/serialized writes and immediate restriction. Lint is **0 errors / 7 existing warnings / 0 hints**, with no suppression. Exact runtime licensing remains **132 reviewed artifacts plus the native encoder**, with no dependency changes. The exact preview APK passes eight native libraries/four ABIs, 16 KiB LOAD/RELRO, dynamic linkage, matching source/notices and the development-tool isolation guard. Nine new UI messages are generated and structurally checked in all 22 locales; linguistic review is not inferred from key/placeholder checks.

Four additional API 36 UI checks pass on a 320 dp phone, that phone with 200% text/dark theme, Arabic RTL with 200% text/dark theme, and a 1,067 dp tablet. Each choice exposes the radio role, a click action and at least 48 dp touch height, remains reachable by scrolling, and starts unselected. Actual screenshots were inspected. A new Activity restores the acknowledged undisclosed choice without creating the application consent session. This is accessibility-semantics/layout coverage, not complete TalkBack speech or physical-device performance. The older regional/account evidence remains tied to its frozen artifacts rather than being relabeled as tests of this new preview.

The ordinary debug profile also passes **14 regression checks**, seven per API: startup without SDK/WorkManager, manual-panel entry/recreation without a request, manifest/sample-ID boundaries, and actual sample banners removed for recreation, a Home panel/editing and backgrounding. Its application SHA-256 is `105242732b20abd1c72f66df77f34aa5c0757edd2b53c78b3c2ae8723987c143`, also 40,692,433 bytes. This yields **34 emulator checks in this phase with no skips**, distinct from the 26 historical account checks. `verification.json` maps named case logs to application profiles; age-UI cases add instrumentation coverage without changing the preview application. The code-only Graphify update reports 4,773 nodes / 12,930 edges and the same 27 partial-parser warnings; compilation and runtime tests provide the verification. QA networks/layout settings are restored before the dedicated emulators are stopped.

## Next production gates

The next development step is **same-process initialized-SDK plus media workflow qualification**. Earlier fresh-process media tests do not establish that combination. Then finalize the live placement/refresh configuration, publish the age-aware privacy text and reconcile exact candidate Play declarations, verify public store linking/app-ads.txt/readiness, and perform requested signed/R8 qualification. Production activation requires that separate review; this debug preview is not a production-approval certificate.

## Official references

- [Google age treatment, content rating and publisher personalization](https://developers.google.com/admob/android/next-gen/targeting).
- [UMP under-age behavior and separate SDK configuration](https://developers.google.com/admob/android/next-gen/privacy/gdpr).
- [Age-restricted request protections](https://support.google.com/admob/answer/6219315?hl=en).
- [Test ads](https://developers.google.com/admob/android/next-gen/test-ads).

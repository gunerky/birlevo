# Pre-release readiness and account handoff

18 October 2026. The owner requested completion of the remaining development preparation together, before requesting a new signed build. Work starts at merged `main` commit `3a7b2e2` ([PR #25](https://github.com/gunerky/birlevo/pull/25)); both checks of that PR's final head passed. The implementation is subsequently merged in `17c1a96` ([PR #26](https://github.com/gunerky/birlevo/pull/26)); both checks of its head `bc266f5` passed. This document distinguishes repository verification, published website content, account actions and exact-package qualification.

## Implementation boundary

- Image save destinations are bound to the result that opened the picker. Cancellation does not discard the result. A stale destination cannot export a newer result; both current source URIs and original URIs captured when opening the picker are protected from rejected-destination cleanup, including after the source is replaced or working state is lost. The pending result path survives Activity recreation and does not create a valid result after process loss.
- The age-aware Home entry, consent session and banner now use shared build policy. Debug always selects Google's sample banner, including the actual-account UMP profile. Release metadata selects the owner's application/banner IDs, with no debug geography, while `AD_PRODUCTION` remains hardcoded **false**. Development properties cannot turn it on. No SDK initialization or age prompt is reachable through the disabled release entry. All existing file tools remain available.
- A future explicit activation uses the same neutral age and regional privacy flow; protected/unknown-age handling, request cooldown, Home-only placement and lifecycle removal remain intact. A real identifier in metadata is not an ad request, account approval or proof of release/R8 behavior.
- No runtime dependency, engine, supported output dimension, quality choice, stable schema or provider root changes. Media input remains local; advertising receives no media contents, filenames or project assets. The initialized SDK cannot be unloaded after an earlier eligible choice.

## Verification

The integrated UI matrix uses `ResidentAdvertisingRule` with each suite's existing Compose rule. Explicit opt-in first loads a real sample banner through the age-aware Home flow, backgrounds/removes it, and then runs the workload in the same parent process. The fixture is a no-op in normal tests. Each case records the process identity, initialized SDK and sampled parent allocations; samples do not establish total memory or a physical-device performance guarantee.

```powershell
./gradlew.bat '-Pbirlevo.adPrivacyAccount=true' '-Pbirlevo.adPrivacyRegion=eea' '-Pbirlevo.adHomePreview=true' :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug :app:generateReleaseBuildConfig :app:processReleaseMainManifest exportRuntimeLicenseInventory
./tools/testing/resident-ad-ui-qa.ps1 -Serial emulator-5580 -Api 36 -Network
./tools/testing/resident-ad-ui-qa.ps1 -Serial emulator-5582 -Api 24 -Network
python tools/testing/verify_ad_build_configuration.py --report output/development/release-readiness/ad-build-metadata.json
python tools/licensing/audit_runtime.py --check
python tools/localization/generate.py --check
```

Only dedicated QA emulators are used. The generated release configuration/merged manifest are inspected without compiling or signing a release APK/AAB. The baseline matrix has **32 passing UI checks without skips**, 16 per API. It covers general file routing/recreation, image inspector/result invalidation, successful/cancelled/failed/obsolete saves, result-specific larger-copy approval for open/share/transfer, durable new/appended presentation assets, cross-UID read grants, photo-PDF append/cancel, audio edit/save/restore, two-clip video editing and presentation image/player state. Independent [log verification](../../tools/testing/verify_resident_ad_ui.py) confirms test identity, artifact hashes, no skips, one parent PID and initialized SDK throughout each workload.

The baseline application is 40,692,433 bytes, SHA-256 `0c8478942082d8dfaa3a581c83de184ae3e5606b06b627e92d44206f5cd30da6`. Initial instrumentation is `45690e59b7778ad9d856651d2cb914740cd99cefdcce2d1ffdf98d09087de181` on API 36 and `93b06cdd48273e542a454d7ef274f9d11e7037bebd20ab9a212a819c135d7ded` on API 24. The corrected photo-PDF/remaining media cases use `17b678e814d84550323c3587dfac422c0256ca71305b102eb774a15ca863466e`; the corrected presenter case on both APIs uses `d5c01bf4377b1c330e79d4d3bea50c81bcee39a918e936fb942c0953b952b4aa`. Per-case `artifacts.json`, invocation environments, logs, `proof.json` and `memory.csv` keep that mapping explicit. The matrix does not pretend every case used one instrumentation APK.

Initial diagnostics are retained rather than counted as passes. The legacy High-profile video fixture was unsupported before UI import; baseline-profile synthetic clips retain dimensions, duration, audio and edit assertions. The photo-PDF test now explicitly seeds existing work, checks that cancel preserves it, and verifies an exact one-page append instead of assuming an empty restored session. Legacy presenter logs showed that a test `NEW_TASK` launch created a second host and stopped the tracked one; reordering inside the same task fixes the fixture without weakening lifecycle/state assertions. Neither diagnostic is suppressed or converted to a skip.

A separate source-protection follow-up profile exercises the additional original-URI snapshot fix. Its Android DocumentsContract spy first proves the real framework cleanup call reaches the fixture, then verifies that source replacement and a fresh empty ViewModel cause no delete call for the former original; unrelated empty-destination cleanup still works. This is a fresh-ViewModel/restored-callback check, not a claim of a new whole-process-death test. The application and focused results are recorded separately from the baseline matrix. Earlier account, age/layout and heavy-media results retain their own artifacts in their [respective reports](../development/ADVERTISING_MEDIA_QUALIFICATION.md); they are not relabeled as current-package checks.

## Completed debug qualification

**67 named emulator checks pass without skips**, with exact per-case artifact mapping in ignored `output/development/release-readiness/verification.json`:

| Profile | Checks | Scope |
| --- | ---: | --- |
| Baseline initialized-SDK UI | 32 | Sixteen workflows per API, described above |
| Original-URI follow-up | 10 | Five per API: former-original protection, stale destination, save failure/retry, ordinary export/recreation and larger-copy export |
| Blocked age choices | 4 | Unknown/undisclosed and under-13 choices, both APIs, without an application consent session/ad request |
| Age dialog layouts | 4 | Narrow phone, 200% dark text, Arabic RTL/200% dark text and tablet |
| Ordinary debug startup/privacy | 8 | Four per API: no startup SDK/WorkManager, explicit-only panel, recreation and configured manifest/sample IDs |
| Workspace layouts | 6 | EN/TR phone, large/dark, Arabic RTL/large/dark, tablet and large-text tablet |
| TalkBack | 1 | Actual enabled service, native focus/click actions and reachable file conversion |
| Immutable saved-state regression | 2 | Larger-copy approval, Activity recreation and export, one per API |

The original-URI profile application SHA-256 is `1d376b06111dc3ae657608781e4f99e722bd7694fee94abc17568644525abb62`, instrumentation `d5c01bf4377b1c330e79d4d3bea50c81bcee39a918e936fb942c0953b952b4aa`. The workspace/startup/TalkBack profile is `816cfc229da4c2fef2fe256e8bfe907b30712d9bb22cdcc8bb8e43c26e70a537` with instrumentation `06845a26a4287f94f9bdc34e359bf222b6f33f4629782431ffde9b118d110ad7`. The final immutable-list correction is separately checked with application `53c9817a839f41d7f68a773f745ed3ff2d658fe5c2247e7c5c04fbc5f984ead2` and the same instrumentation hash. Each application is 40,692,433 bytes. These are successive debug qualifications, not one signed candidate or one common APK for every check.

Visual inspection found a short filename's extension wrapping alone at 200% text/RTL. The shared source header now places its name above the detail/change row at large font scales, preserving full semantics and the existing actions. Tests assert one rendered line for the short fixture filename. Snapshot capture waits for the current editor's ready state and a committed frame; the earlier loading-frame screenshot is retained as a capture diagnostic. EN/TR, dark, RTL and tablet screenshots were inspected after correction. A new mutable-collection lint warning was fixed with read-only replacement state, without suppression. The final pair of Android tests passed in their raw logs; a temporary desktop wrapper's over-escaped result pattern was corrected by independent log validation, not by treating a failed/skipped test as a pass. The stale manifest-test selector likewise failed at zero tests and was corrected before counting the four actual cases.

**182 JVM tests pass** with no failures/errors/skips. Final lint is **0 errors / 7 existing reviewed warnings / 0 hints**. Locale generation verifies all 22 key/placeholder/resource sets; changed EN/TR wording was directly reviewed. This does not assert linguistic review of every string in every language or complete TalkBack speech coverage. Exact licensing remains **132 artifacts plus the native encoder**; final native checks cover eight libraries, four ABIs, 16 KiB LOAD/RELRO, matching offline source/notices and dynamic linkage without the GPL decoder. Development-tool isolation passes without any live Jev call. The frozen signed 0.1.11 remains `d1924881005d06aa7f4df643433938d3c183572dcfcb406ea765945720c5f881`.

Reproduce the focused matrix with the Home/account preview build and explicit `-Cases source-guard,late-save,save-failure,image-save,copy-save`, using a separate `-OutputDirectory` for each API. The [UI phase runner](../../tools/testing/pre-release-ui-qa.ps1) accepts explicit app/instrumentation artifacts: `-Stage age -Network` needs that preview profile; `-Stage layouts` and `-Stage accessibility` need ordinary debug. The [normal advertising runner](../../tools/testing/ad-privacy-qa.ps1) similarly accepts pinned artifacts and an output directory. No earlier evidence is overwritten by a different profile without preserving its diagnostics and artifact mapping. New tests do not reduce formats, quality, dimensions or stored-work compatibility. Physical ARM/GPU/battery/thermal and every provider combination remain outside this selected emulator matrix; signed/R8 qualification is reserved for the requested candidate.

## Published privacy content

The EN/TR age-group paragraphs are published at [English privacy](https://birlevo.com/en/privacy/) and [Turkish privacy](https://birlevo.com/privacy/). They explain private local group storage, no birth date, the derived provider flags, protected 13–17 requests, no new app requests for unknown/under-13/failed saves, and the limits of withdrawing after prior SDK initialization. The development-build paragraph now also covers automatic checks after an eligible durable age choice.

Cloudflare Pages production deployment **`ce8b495f-f6ee-48f2-a6ef-a1fe7691d348`** succeeded at **2026-10-18 12:57:47 UTC**. All nine live URLs returned 200 and match the reviewed files: the two policies, original homepage, publisher text and five icons. Existing narrow Cloudflare email/analytics normalization applies to HTML; publisher/icon bytes are exact. No TLS bypass, redesign, account/source setting change or Android distribution occurred. Prior production deployment is `4bc9815d-91a3-4618-99fa-cb0bf47a1bdc`. Evidence is ignored under `output/development/release-readiness/site/`.

## Account actions before live activation

The user operates Play Console and AdMob. On 18 October the owner explicitly confirmed that the Ads, Advertising ID and Data safety declarations have **not yet been updated** for the SDK. They remain required account work before live activation/distribution of an affected candidate. No account status or form submission is inferred from code, sample-ad success or a public HTTP check. These items remain account actions; they do not prevent compiling a candidate with live advertising disabled.

| Surface | Prepared facts / required action |
| --- | --- |
| Play developer website | Use `https://birlevo.com`; the owner already reported this correction. The separate privacy URL remains `https://birlevo.com/en/privacy/`. |
| AdMob app | Existing app `ca-app-pub-5967660342681325~8485054661`, Home banner `ca-app-pub-5967660342681325/5970571966`. Link the actual public supported-store listing when available; last owner evidence showed an unlinked app requiring review with limited serving. Check the current account instead of assuming approval. |
| app-ads.txt | Published line is `google.com, pub-5967660342681325, DIRECT, f08c47fec0942fa0`. Request/recheck account verification after store linking; HTTP availability does not certify Google's crawl or app readiness. |
| Privacy & messaging | Owner reports European and US messages published, English retained. Real account regional messages have emulator evidence. Recheck publication for Birlevo before live activation; the application's age group must never substitute for required regional choices. |
| Banner refresh | Owner confirmed **Google-optimized**. Retain it. The app has attempt cooldowns, not another periodic refresh timer. |
| Play target audience | Owner-confirmed **13–15, 16–17, 18+**. Do not add under-13 groups incidentally. Both teen groups use the stronger protected treatment. |
| Contains ads | For an **ad-enabled** distributed candidate, declare ads even when individual users receive none. Do not claim this disabled candidate is already monetizing or automatically overwrite old-track answers from this draft. Verify the actual package/selected activation before submission. |
| Advertising ID | Merged SDK manifest includes `com.google.android.gms.permission.AD_ID`. For activation using the ID, declare the actual SDK use and purposes, including advertising, analytics and fraud prevention where applicable. A former ad-free build's answer does not describe an activated SDK. |
| Data safety | Reconcile collection/sharing of approximate location inferred from IP, device/other identifiers, app interactions and provider diagnostics against the actual candidate and provider configuration. Review purposes, optionality, retention/deletion and TLS. There is no Birlevo login/cloud media upload. Do not claim the whole application never uses the network or that deleting local data deletes Google's records. |
| Foreground services | Existing user-started local media work retains `dataSync`/`mediaProcessing`, progress/stop controls and the previously prepared demonstration; advertising does not introduce a new foreground-service use. Reconcile with the exact merged manifest and current Console declaration. |

Google's [SDK disclosure](https://developers.google.com/admob/android/next-gen/privacy/play-data-disclosure), [age-treatment guide](https://developers.google.com/admob/android/next-gen/targeting), [UMP guide](https://developers.google.com/admob/android/next-gen/privacy/gdpr) and [Play Data safety instructions](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en) were rechecked for preparation. Their general guidance is not account approval or an exact-candidate declaration submitted on the owner's behalf. Provider documentation describes its latest SDK; this repository retains the pinned, hash-reviewed 1.5.0/4.0.0 integration and must reconcile the exact packaged configuration at release.

## Work reserved for the next explicit build request

Choose the version and freeze a commit; prepare EN/TR notes, signed R8/resource-shrunk APK/AAB, exact-package smoke/restoration checks, source/notice/library replacement, four ABIs, 16 KiB alignment and matching native symbols. No version increment, signed package or rollout is part of this preparation. The frozen signed 0.1.11 (12) stays unchanged. Live activation also requires the current account gates above; the switch remains closed until both account and signed-package evidence support it.

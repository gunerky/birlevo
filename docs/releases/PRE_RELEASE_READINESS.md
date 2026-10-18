# Pre-release readiness and account handoff

18 October 2026. The owner requested completion of the remaining development preparation together, before requesting a new signed build. Work starts at merged `main` commit `3a7b2e2` ([PR #25](https://github.com/gunerky/birlevo/pull/25)); both checks of that PR's final head passed. This document distinguishes repository verification, published website content, account actions and exact-package qualification.

## Implementation boundary

- Image save destinations are bound to the result that opened the picker. Cancellation does not discard the result. A stale destination cannot export a newer result; source URIs are protected from rejected-destination cleanup. The pending result path survives Activity recreation and does not create a valid result after process loss.
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

Only dedicated QA emulators are used. The generated release configuration/merged manifest are inspected without compiling or signing a release APK/AAB. Current case results and exact artifact identities will be recorded here after the matrix completes. Earlier account, age/layout and heavy-media results retain their own artifacts in their [respective reports](../development/ADVERTISING_MEDIA_QUALIFICATION.md); they are not relabeled as current-package checks.

## Published privacy content

The EN/TR age-group paragraphs are published at [English privacy](https://birlevo.com/en/privacy/) and [Turkish privacy](https://birlevo.com/privacy/). They explain private local group storage, no birth date, the derived provider flags, protected 13–17 requests, no new app requests for unknown/under-13/failed saves, and the limits of withdrawing after prior SDK initialization. The development-build paragraph now also covers automatic checks after an eligible durable age choice.

Cloudflare Pages production deployment **`ce8b495f-f6ee-48f2-a6ef-a1fe7691d348`** succeeded at **2026-10-18 12:57:47 UTC**. All nine live URLs returned 200 and match the reviewed files: the two policies, original homepage, publisher text and five icons. Existing narrow Cloudflare email/analytics normalization applies to HTML; publisher/icon bytes are exact. No TLS bypass, redesign, account/source setting change or Android distribution occurred. Prior production deployment is `4bc9815d-91a3-4618-99fa-cb0bf47a1bdc`. Evidence is ignored under `output/development/release-readiness/site/`.

## Account actions before live activation

The user operates Play Console and AdMob. No account status or form submission is inferred from code, sample-ad success or a public HTTP check. These items remain account actions; they do not prevent compiling a candidate with live advertising disabled.

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

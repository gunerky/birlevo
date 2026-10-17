# Advertising foundation

18 October 2026. First-production step 5 implementation. Debug verification only; no version bump, signed release, live ad placement, website deployment or Play submission.

## Provider and activation boundary

The initial provider is **Google AdMob**, using **GMA Next-Gen SDK 1.5.0** and **UMP 4.0.0**, without mediation, Firebase Analytics or additional ad providers. The current official quick start points to this SDK; it supports the existing API 24 minimum and compile SDK 36. Its synchronous initialization is dispatched to IO, not the UI thread. The alternative legacy SDK is not included alongside it. Resource cost is measured against the debug artifact, not inferred from marketing claims; no ARM, battery or low-end FPS guarantees follow from emulator results.

The owner has an AdMob account but has not registered Birlevo. This patch therefore uses only Google's public sample application ID `ca-app-pub-3940256099942544~3347511713`. The reserved adaptive-banner test unit is `ca-app-pub-3940256099942544/9214589741`; **no ad load or display is implemented in this step**. Production account/message behavior is an outstanding integration gate, not a completed live test.

`BuildConfig.AD_TESTING` is true only in debug. Release hardcodes it false and exposes no runtime switch to activate it. Ordinary startup never instantiates UMP or initializes Mobile Ads. The debug home footer contains **Ad privacy**, a test panel explaining that checking contacts Google; only its explicit **Check privacy** action starts the request. Opening the panel itself does not contact the provider. No consent dialog appears over editing/encoding/saving because of automatic startup behavior.

## Consent and lifecycle contract

- `AdPrivacySession` is independent of media engines and is confined to the UI thread. It owns only phase/operation state; UMP owns consent storage. No second consent string or app-maintained "agreed" boolean is saved.
- Refresh calls UMP's information update, then loads a form only when its current consent status requires one. The loaded form is explicitly shown only while its continuation and Activity are still valid; cancelling an asynchronous load cannot open a late form over another tool. SDK initialization is permitted only after `canRequestAds()` succeeds. Even cached consent is rejected after a failed refresh/form in this implementation; tools remain usable without ads.
- A future placement must call the session's eligibility check immediately before a request/show. It rechecks UMP, not just an earlier state snapshot. `canRequestAds()` is not interpreted as proof of personalized-ad consent.
- Privacy options are exposed when UMP requires them, including after a failed edit. Editing immediately clears eligibility; failure leaves it cleared. A successful change is evaluated again against UMP.
- Repeated taps during a request are ignored. Separate 15-second deadlines bound information refresh, form loading and initialization waiting; reading or deciding in a displayed provider form has no time limit. A network/initialization deadline clears eligibility and lets the user retry. SDK callbacks use cancellable continuations; closing/recreating/stopping the Activity invalidates the generation and cancels waiting. Late callbacks cannot restore eligibility. The synchronous SDK initializer may finish on its application-only IO job after the UI stops waiting; it never receives an Activity or media data, and Birlevo does not issue an ad-load call as a result.
- Initialization is shared across requests within a process. WorkManager's transitive startup initializer is removed from the merged manifest; it is initialized only immediately before permitted SDK initialization. An SDK initialization failure can be retried without initializing that database twice.
- Process restart begins with no eligibility. There is no automatic provider retry loop, ad request on media failure, per-file batch ad or persisted frequency cap in this foundation. Placements/caps belong to step 6.

## Data and license review

See the [exact SDK/license review](../licensing/ADVERTISING_SDK_REVIEW.md) and [privacy/Console drafts](../releases/ADVERTISING_DISCLOSURE_DRAFT.md). The release classpath expands from 97 to **128 artifacts**. No runtime dependency is approved merely because its POM mentions a known license. New proprietary/composite declarations require exact coordinate, declaration URL and pinned supplemental notice text; future versions fail coverage until reviewed.

The manifest adds normal Internet/network-state/basic-phone-state permissions and the advertising-ID permission through the SDK. No broad storage, microphone, contacts, fine-location or camera permission is introduced. No selected files, project data, document text, source names, URIs or output metadata are supplied to an ad API. This does not make the SDK data-free: once activated, provider disclosures include identifiers, IP-derived location, interactions and diagnostics. The draft records that distinction and the actual permission set.

The current public privacy pages remain scoped to frozen 0.1.11 and earlier ad-free packages; they are not deployed as a policy for a new ad-enabled candidate. Before enabling production, register the app/messages, settle audience/age treatment, verify current provider forms/withdrawal/offline behavior, update privacy/Play declarations and qualify placements. No production consent configuration or account-policy approval is implied by these code changes.

## Reproduction and evidence

```powershell
./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug
./gradlew.bat exportRuntimeLicenseInventory
python tools/licensing/audit_runtime.py --check
python -m unittest discover -s tools/licensing -p 'test_*.py'
python tools/localization/generate.py --check
tools/testing/ad-privacy-qa.ps1 -Serial emulator-5580 -Api 36 -Network -Workflows
tools/testing/ad-privacy-qa.ps1 -Serial emulator-5582 -Api 24 -Network
```

Only dedicated QA AVDs are accepted. Network QA explicitly calls the provider with the sample app ID; local fake-provider tests exercise approval, denial, stale callbacks, changed options and failures without network or real users. The sample ID cannot certify account-hosted EEA/US forms, production targeting or mediation. All changed interface keys are generated in 22 locales; English/Turkish wording is directly reviewed, other languages still require linguistic feedback. Release shrinking and actual ad rendering are later gates.

Local evidence is retained under ignored `output/development/advertising-foundation/`.

## Accepted verification

- Final debug APK SHA-256: `7f3d5db1c97dee17c65910a43c2de4d003463ce914acb73938fb0803c1b633eb`, **39,081,949 bytes**. Version remains 0.1.11 / code 12 for development. The frozen signed 0.1.11 APK is unchanged (`d1924881005d06aa7f4df643433938d3c183572dcfcb406ea765945720c5f881`).
- **167 JVM tests**, zero failures/errors/skips, including ten consent/session cases; debug app/test builds and lint **0 errors / 60 warnings / 2 hints**. Five license-audit tests, 20 offline Jev tests, five SDK-preparation tests and 12 PPTX tooling tests pass. The offline Windows credential test uses only a synthetic key and a temporary store; no live Jev call is made.
- **40 dedicated emulator checks**: API 24 has five privacy/manifest/recreation/sample-provider checks; API 36 has those five plus six image, seven audio, five photo-PDF, six cleanup and six output/ownership cases. `qa-summary.json`, `qa24-verified.log`, `qa36-verified.log` and each `api24/` / `api36/` case log record the final artifact.
- On both APIs the real **Publisher Test Ads** EEA form opens and the test selects **Do not consent** through the actual SDK-hosted form; it does not write an IAB string or bypass UMP. UMP then reports eligibility and the SDK initializes. That is provider permission to make the applicable ad request, **not proof of personalized-ad consent**. Birlevo calls no ad-load API and displays no ad. Closing the test panel immediately clears eligibility, and opening the image tool still works.
- The real provider test exposed an immediate-close race after successful initialization; closing now calls `detach()` in the same action before changing Compose state, rather than waiting for disposal. Delayed form loading is separately guarded against opening after cancellation. A displayed form has no reading deadline.
- Regression selection exposed three stale audio checks: two expected pre-workspace navigation, and one compared total padded MP4 sizes as an AAC bitrate ratio. The tests now use current public UI actions and compare actual extracted AAC packet bytes; all seven pass. Compression benefit/export consent still uses complete actual source/output file sizes. No media implementation or quality option was changed to satisfy the tests. Initial diagnostics remain in `audio-stale-tests.log` / `qa36-stale-tests.log`.
- Exact **128-artifact** license coverage and all supplemental notice texts in the APK pass. Existing eight MP3 encoder/bridge ELF files retain matching offline source, dynamic linkage, four ABIs and 16 KiB LOAD/RELRO checks; the four existing graphics-path libraries are not new SDK payload. Jev source/APK isolation and 22-locale generation checks pass.
- Graphify is updated locally without LLM/network use: 4,603 code nodes / 12,469 edges. Its 27 partial Kotlin parser warnings remain a tooling limitation; source compilation/tests, not the graph alone, verify the changed paths.

### Resource observations and remaining gates

The debug APK increases from the previous cleanup artifact's 31,744,968 bytes to 39,081,949 bytes: **7,336,981 bytes (about 7.00 MiB / 23.1%)**. This is unshrunk development size, not a predicted release/download delta.

During one cold sample-form/SDK session, main-process PSS was **48,877 → 161,387 KiB** on API 24 and **177,131 → 282,842 KiB** on API 36. These snapshots include Compose, CMP/WebView/class loading and initialization; they do not isolate the SDK's retained heap, account for every renderer/system process, measure peaks or establish physical-device costs. Ordinary launch does not initialize the SDK/WorkManager. Media regression cases run in fresh instrumentation processes; they are **not combined initialized-SDK-plus-heavy-encoding qualification**. Step 6/7 must measure that combined resident/peak cost, ad-view destruction, rendering/caps, real-account form changes and offline behavior before production activation. Do not reduce output quality/resolutions to hide ad resource cost.

The public sample form and SDK initialization are checked on Android 7 and current Android. Birlevo's own AdMob app/messages, audience/age treatment, app-ads.txt/readiness, production units, privacy/Play declarations and the signed/shrunk candidate remain open gates. No production or physical-device certification follows from these debug results.

## Official implementation references

- [Current SDK setup](https://developers.google.com/admob/android/next-gen/quick-start) and [release notes](https://developers.google.com/admob/android/next-gen/rel-notes).
- [UMP information/form/options lifecycle](https://developers.google.com/admob/android/next-gen/privacy).
- [Public test identifiers](https://developers.google.com/admob/android/next-gen/test-ads).
- [Initialization threading and behavior](https://developers.google.com/admob/android/next-gen/reference/com/google/android/libraries/ads/mobile/sdk/MobileAds).
- [Provider data disclosure](https://developers.google.com/admob/android/next-gen/privacy/play-data-disclosure).

# Lint warning review and compatible dependency maintenance

18 October 2026. Follow-up requested during the advertising-foundation review. Debug/emulator verification only; version remains 0.1.11 / code 12. No signed release or production ad activation.

## Warning disposition

| Android lint rule | Before | After | Disposition |
| --- | ---: | ---: | --- |
| UseKtx | 22 | 0 | Use the existing Core KTX URI/bitmap helpers with the same dimensions, config, filter flag and ownership checks. A missing restored PDF URI still rejects the session; it is not replaced with an empty URI. |
| UnusedResources | 18 | 0 | Remove 17 demonstrably unused string keys from the catalog and all 22 generated locales. Retain original logo artwork in the [design source](../design/assets/birlevo_logo.png); the launcher still uses its unchanged safe drawable. |
| PluralsCandidate | 8 | 0 | Clarify four English labels in default/en-US resources. PDF counters contain two independent quantities, character text is a current/maximum counter, and the Office number is a year system, not a quantity. Label-style wording remains grammatical at one without choosing one count to inflect another. Batch review retains both counts and its exclusion/ZIP warning. |
| GradleDependency | 8 | 4 | Apply the compatible Activity/UI/Lifecycle group and three test-library upgrades. Newer Compose/Lifecycle targets require a separate toolchain migration. |
| NewerVersionAvailable | 1 | 0 | Update the host-only JSON test library. Android still uses the platform JSON implementation. |
| TrustAllX509TrustManager | 3 | 3 | Trace the specific existing Bouncy Castle EST helpers; no application use identified by the bounded static check below. Do not suppress or claim complete unreachability. |
| AutoboxingStateCreation (hint) | 2 | 0 | Use saveable primitive Int state for slideshow request counters; values and saved-state meaning stay unchanged. |

Result: **0 errors, 7 warnings, 0 hints**, compared with 0 errors / 60 warnings / 2 hints. No lint baseline, disable rule, ignore file, severity reduction or suppression was added. The redundant exhaustive glyph `when` fallback is removed. Other compiler diagnostics are separate from this lint count; notably the existing deprecated Media3 `SpeedChangeEffect` path needs a coordinated audio/video speed migration rather than a blind constructor replacement that could apply speed twice.

Resource removal affects Android UI resources only. Persisted JSON `projects` and all other schema keys, draft/source names, FileProvider authority/roots and user media remain intact. Bitmap helpers preserve existing export dimensions, quality, original-byte checks and delivered-bitmap lifetime. The generator writes explicit LF output on both platforms.

## Compatible dependency selection

- Activity/Activity Compose/KTX: **1.13.0**.
- Compose BOM for app and instrumentation: **2025.09.01**, aligning UI/Foundation/Runtime **1.9.2** and Material3 **1.4.0**. Activity's transitive requirements must not leave the old UI family partially upgraded.
- Lifecycle Compose entry points: **2.9.4**, matching the resolved compatible family.
- Test-only Runner **1.7.0**, AndroidX JUnit **1.3.0**, Espresso Intents **3.7.0**, JSON-Java **20260814**.

The application remains minSdk **24**, compile/target SDK **36**, AGP **8.12.0**, Kotlin **2.3.0**. Exact official AAR metadata for Activity/Core 1.18.0/NavigationEvent 1.0.0 requires compile SDK 36 and AGP 8.9.1, satisfied by this project. All changed AAR manifests and build metadata were checked for the actual resolved group.

Four remaining freshness warnings cover the two BOM declarations and two Lifecycle Compose declarations. The proposed 2026.09.00 BOM resolves UI 1.12.1, and Lifecycle 2.11.0's Android artifact requires compile SDK 37 and at least AGP 9.1.0 in its AAR metadata; the current Lifecycle release notes specify AGP 9.2.0 for its Compose update. Do not bypass AAR checks or raise the supported-device floor to silence them. A future SDK 37/AGP migration must requalify build tooling, saved state, insets, media, accessibility and license inventory together.

Official inputs: [Activity notes](https://developer.android.com/jetpack/androidx/releases/activity), [Lifecycle notes](https://developer.android.com/jetpack/androidx/releases/lifecycle), [BOM mapping](https://developer.android.com/develop/ui/compose/bom/bom-mapping), [AndroidX Test](https://developer.android.com/jetpack/androidx/releases/test), [JSON-Java release](https://github.com/stleary/JSON-java/releases/tag/20260814). Exact downloaded metadata is retained locally in `output/development/advertising-foundation/lint-cleanup/runtime-upgrade-metadata.json`.

## Certificate warning trace and limits

Lint flags three empty methods in `org.bouncycastle:bcpkix-jdk15to18:1.72`. `javap` confirms empty client/server checks in `org.bouncycastle.est.jcajce.JcaJceUtils$1` and an empty client check in `$2`; the second helper does validate server chains.

Scanning class bytes in all 132 actual runtime JAR/AAR files, including nested `classes.jar`, found EST package references only in the 50 EST classes inside bcpkix itself. Application source has no EST/JcaJceUtils or custom TLS trust-manager call. These are existing PDF dependencies, not new ad SDK trust managers. No reachable application path was identified by this static check. It does not exclude reflection, native/dynamic calls, future dependencies or every possible connection path, and is not a full Bouncy Castle security audit. Do not register these permissive helpers as a network trust manager. Removing cryptographic modules merely to lower the warning count could break PDF capability; no such exclusion or insecure global override was made.

These are transport-security warnings, not license violations. The commercial license review remains separate.

## Exact license review

The compatible group resolves **132 runtime artifacts**, up from the initial foundation's 128. **43 coordinates/artifacts changed or were added**. Each changed artifact's official POM, packaged notices, SHA-256, AAR SDK/AGP metadata, manifest minimum and native contents were reviewed. Changed components declare Apache-2.0; their embedded notice texts match already retained full Apache notices. No changed package introduces a native shared library. Tracing and Kotlin serialization declare Apache-2.0 with no additional embedded notice; [serialization 1.7.3's actual upstream license](https://github.com/Kotlin/kotlinx.serialization/blob/v1.7.3/LICENSE.txt) was checked. Exact hashes/evidence remain in [runtime-licenses.json](../../tools/licensing/runtime-licenses.json); existing proprietary SDK, Cronet, Protobuf, PDFBox, Bouncy Castle and LGPL obligations remain intact.

Test-only AndroidX packages declare Apache-2.0 and JSON-Java's matching tag is [Public Domain](https://github.com/stleary/JSON-java/blob/20260814/LICENSE). Test packages are not added to the consumer application's runtime inventory. Official POM/artifact hashes and inspection records are retained locally in `test-dependency-review/review.json` and `cohort-license-review.json` under the evidence directory. No future version is approved by this review.

## Verification

Final debug APK: SHA-256 `e4b23fd4a2d05230fe1a8b9620078fd9488388b421c953ceb4be1727d884fc84`, **40,668,357 bytes**. **167 JVM tests** pass with zero failures/errors/skips. Debug app/instrumentation compilation and lint pass. The 132-artifact/native license check, offline encoder/source/alignment check and Jev source/APK isolation check pass. **91 selected emulator checks** pass on the same frozen APK: **12 on API 24**, **79 on API 36**. The latter includes eight additional cases at **font scale 2.0 / dark mode / Arabic RTL**; the ordinary suite runs at font scale 1.0 / light mode. UiAutomation checks actual Android accessibility actions and resulting application values; these runs do not claim TalkBack speech verification. Both APIs also complete the real sample form and SDK initialization after choosing Do not consent, without an application ad-load call.

The APK is **1,586,408 bytes larger** than the initial advertising-foundation debug artifact even after removing unused artwork. This is an unshrunk development comparison, not a release/download estimate or physical-device performance guarantee. Initial sample-form/SDK PSS observations in the [foundation report](ADVERTISING_FOUNDATION.md) belong to its original artifact, not a new memory qualification for this dependency group. Production ads, combined initialized-SDK/media memory stress and signed R8 validation remain open gates.

Evidence: ignored `output/development/advertising-foundation/lint-cleanup/`, including exact environment/APK hashes, build logs, per-class emulator logs, dependency metadata and bounded EST reference inventory. Physical devices and personal AVDs are not used. All selected emulator groups are complete. QA font/night settings were restored. The frozen debug app and instrumentation APKs are retained alongside their hash/count summary in `verification.json`. No signed release is produced.


### Test diagnostics and current resource observations

Initial accessibility checks assumed a range control and its name occupied the same Android node. Compose 1.9 exposes named child nodes; a video-dialog heading also shares the slider label. The test now requires the exact named node to belong to an enabled, visible range ancestor in the same application, then invokes the real ACTION_SET_PROGRESS. It does not select an arbitrary slider or weaken the value/save assertions. Failure dumps retain the actual native tree. All eight cases pass in both normal/light and 200%-text/dark Arabic RTL conditions. No production accessibility workaround or label removal was needed.

On API 24, a legacy MediaMetadataRetriever returned a sync frame even for OPTION_CLOSEST. The diagnostic retained the actual video before assertions; independent local FFmpeg decoding confirmed the expected red/blue timeline. The legacy test uses timestamp-aware frame decoding, preserving the original color tolerance, time, dimensions and source/export checks. Nine independent color/time samples across landscape, portrait and square outputs also pass. Modern Android retains its retriever check. This changes test measurement, not encoded output quality.

Two shared VideoFrameDeviceTest input fixtures returned VIDEO_CODEC at API 24's existing platform decoder preflight. These initial failures are retained in `initial-api24/` and are **not counted as passes**; no device-codec gate, fixture or output quality was weakened. Those two cases pass on API 36. The accepted API 24 group covers five legacy image/PDF cases, two slideshow cases, four ordinary privacy/manifest/recreation cases and the real sample-provider case. This is scoped emulator coverage, not universal codec support.

| Current sample-form/SDK run | Main-process PSS before | After |
| --- | ---: | ---: |
| API 24 | 50,512 KiB | 172,001 KiB |
| API 36 | 185,347 KiB | 288,822 KiB |

These single cold-session snapshots include Compose, form/WebView/class loading and SDK initialization. They are not SDK-only retained heap, peak/all-process memory, a controlled comparison with the earlier artifact or a physical-device claim. Other media regressions run in fresh instrumentation processes. Combined resident-SDK/heavy-media stress remains a production-placement gate. Selected source data, quality choices and final resolutions are not reduced to hide SDK cost.

# Advertising SDK and distribution review

18 October 2026. Exact development runtime review for [the advertising foundation](../development/ADVERTISING_FOUNDATION.md). This is Android application-integration review, not acceptance of account agreements, legal advice, a standalone SDK redistribution license, patent clearance or approval to serve production ads.

The initial advertising snapshot below resolves 128 artifacts. The later [compatible UI dependency review](../development/LINT_WARNING_REVIEW.md) updates the current runtime to **132 artifacts**, with exact changed-artifact hashes and notices refreshed. The selected advertising SDKs and their proprietary/composite terms are unchanged.

## Selected implementation and artifact evidence

The pinned initial provider is AdMob: `com.google.android.libraries.ads.mobile.sdk:ads-mobile-sdk:1.5.0` plus `com.google.android.ump:user-messaging-platform:4.0.0`. No mediation adapter or Firebase Analytics is included. Official setup targets API 24+, preserving Birlevo's supported minimum. The main SDK AAR is 6,217,706 bytes and contains no `.so` files. The complete resolved classpath, including each transitive package, is recorded by hash in [runtime-licenses.json](../../tools/licensing/runtime-licenses.json); packaged native checks remain separate from POM inspection.

The classpath contains **128 artifacts**, versus 97 before integration: 31 additional modules and an existing Error Prone annotation version change (32 new coordinates, one old coordinate removed). In addition to the ad and consent SDKs, new modules include AndroidX Browser/WebKit/WorkManager/Room/SQLite/DataStore, Google Play services identifiers/appset/base/basement/cronet/tasks, Play HSDP, Gson, Tink, Protobuf, OkHttp/Okio and Cronet Java providers. No additional codec is introduced. The current compile minimum, exact manifest and final APK must still be verified on every release.

| Family | Reviewed conditions and retained material |
| --- | --- |
| Mobile Ads, UMP, six Play services artifacts and Play HSDP | Proprietary Android SDK terms, plus AdMob service terms for SDK use. Do not label these Apache/open source or relicense their binaries. The exact nine coordinates/versions and POM declaration URLs are allowlisted individually in the supplemental review; original rights and service references are retained in the application notices. |
| Added AndroidX, Gson, Tink, Error Prone, Kotlin JDK stubs, OkHttp and Okio | Apache-2.0 with applicable upstream attribution/embedded notices; existing complete license retained. OkHttp's packaged Public Suffix notice is retained verbatim. |
| Protobuf javalite 3.22.3 | BSD-3-Clause. Complete copyright/conditions/disclaimer from the matching upstream tag are retained in notices. |
| Cronet 143.7445.0: API/common/fallback/shared/httpengine-native-provider | Composite Chromium and built-in component licenses, not a single Apache declaration. The matching distribution's complete 326,255-byte LICENSE is retained verbatim, including third-party notices. Its BSD/Apache/MIT-style and other permissive texts retain their attribution/disclaimers. GPL references in that text concern an optional combined-software clause, not a bundled GPL decoder. No attribution is removed on that basis. |
| Existing PDFBox, Bouncy Castle and dynamically linked LAME | Previous obligations remain; [commercial inventory](COMMERCIAL_LICENSE_INVENTORY.md) and offline LAME source/replacement checks still apply. |

The POM's Android SDK license alone is not a service-distribution conclusion. Google's official [AdMob setup](https://developers.google.com/admob/android/next-gen/quick-start) explicitly documents application integration and revenue use, while [AdMob SDK terms](https://developers.google.com/admob/terms) direct use to the governing AdMob account terms. Preserve account/publisher/privacy obligations; a working test ID is not account approval. No GPL restriction on Birlevo's own application source is inferred from this integration, and the repository is not automatically relicensed as open source.

## Reproducible exact coverage

[advertising-licenses.json](../../tools/licensing/advertising-licenses.json) maps 15 exact coordinates to their matching declaration and supplemental notice SHA-256. The audit rejects a changed version, declaration URL or notice file; `--check` also requires that supplemental text remain in application notices. Other new licenses still fail rather than being automatically accepted by a new broad license-name rule. Official Google artifacts use Google Maven; unrelated Google OSS packages continue to use Maven Central.

Sources retained without modifying their license text:

- [Cronet complete notices](../../tools/licensing/notices/Cronet-143.7445.0.txt), from the [matching Chromium distribution](https://storage.googleapis.com/chromium-cronet/android/143.0.7445.0/Release/cronet/LICENSE).
- [Protobuf complete license](../../tools/licensing/notices/Protobuf-3.22.3.txt), from [upstream v22.3](https://github.com/protocolbuffers/protobuf/blob/v22.3/LICENSE).
- [Google SDK integration notice](../../tools/licensing/notices/Google-Advertising-SDK.txt), referencing the [Android SDK terms](https://developer.android.com/studio/terms) and [AdMob terms](https://developers.google.com/admob/terms). This descriptive notice does not replace those agreements.

Run the inventory export and offline hash check described in the [main inventory](COMMERCIAL_LICENSE_INVENTORY.md). Before shipping an ad-enabled build, verify the [data/Console drafts](../releases/ADVERTISING_DISCLOSURE_DRAFT.md), exact R8 package, provider/account terms and actual message/placement behavior together. Do not infer commercial-service or country-specific approval solely from this inventory.

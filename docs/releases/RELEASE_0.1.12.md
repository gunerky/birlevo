# Birlevo 0.1.12 signed test release

18 October 2026. Requested signed candidate **0.1.12 (13)**, package `studio.birlevo.android`, minSdk 24 / targetSdk 36. The application is built from `291f308`, based on merged `e548f10` / [PR #27](https://github.com/gunerky/birlevo/pull/27). Later harness/document changes do not change the frozen application. Source and qualification are reviewed in [PR #28](https://github.com/gunerky/birlevo/pull/28). No Google Play upload or rollout is performed.

## Frozen files

The existing APK is also distributed as [GitHub pre-release v0.1.12](https://github.com/gunerky/birlevo/releases/tag/v0.1.12), tagged at merged source `2deda7381bfe4fe299867616897c64f5566e24f4`. This includes the original application commit and later qualification records. The public assets are the signed APK, its SHA-256 checksum and the EN/TR notes; the Play AAB and developer-only qualification packages remain local. Publishing this GitHub entry does not upload or roll out a Google Play release. No replacement APK/AAB is built.

| File | Bytes | SHA-256 |
| --- | ---: | --- |
| `output/releases/0.1.12/birlevo-0.1.12-13.aab` | 19,295,128 | `5da23880a29bf8c171de1401a2bef1793ea1e92b167ddb87da13e756cff1d643` |
| `output/development/0.1.12/birlevo-0.1.12-13-release.apk` | 14,447,869 | `684650ecb31f8b64910492777fd576ea170fdd83b4bc7792518a1f4cfcb52085` |

The AAB is for Play; the APK is for local installation. Both use the existing upload certificate, SHA-256 `3d14f0559a4e0f6332303fd352017b68d328daad41771e2b8bd383bf56856f2c`. Signing credentials remain outside Git and are passed only through the signing process environment. Earlier packages remain untouched.

`output/releases/0.1.12/release-notes.txt` contains the EN/TR Play notes below. The exact R8 mapping is `output/development/0.1.12/mapping.txt`; its bytes match the AAB metadata. Genuine matching native symbols are embedded in the AAB for all eight LAME/bridge libraries across four ABIs. Four AndroidX graphics-path libraries arrive pre-stripped, so coverage is **8/12**. The optional matching ZIP/report in `output/releases/0.1.12/native-debug-symbols/` cannot restore missing upstream symbols. Upload the AAB normally; the ZIP is only a fallback if Console asks for symbols for code 13.

## Scope and advertising boundary

Compared with the previous signed 0.1.11 package, this candidate includes the developed batch/ZIP tools, bounded DOCX/XLSX import and simplified output, supported editable PPTX text, device-aware resource admission, compatible dependency updates, original-source save guards and large-text/RTL source-header improvements. Existing engines, supported output sizes/quality choices, larger-copy approval, durable records and FileProvider identifiers remain intact. Office support is a bounded subset, without general PPTX import or an Excel calculation engine.

The reviewed GMA Next-Gen 1.5.0 / UMP 4.0.0 dependencies are now in the signed candidate. **Live advertising remains disabled**: release `AD_PRODUCTION`, `AD_TESTING`, account testing and Home preview are false; debug geography is disabled. SDK application metadata uses the owner's configured app ID, but an ID in metadata is not activation. R8 removes the unreachable Home/age/privacy implementation; one synthetic advertising mapping entry remains. The signed platform check covers all five stored audience groups without a visible ad/age entry or WorkManager database. This bounded check is not a claim that every possible network path has been traffic-audited.

The exact bundle contains `INTERNET`, `READ_BASIC_PHONE_STATE` and `AD_ID` in addition to existing media/service permissions. No broad media or precise-location permission is added. SDK presence, manifest permission and actual collection are distinct. EN/TR website policies already describe the age-aware development/ad-enabled boundary. After the guided Console walkthrough the owner reported the three declarations completed; actual submission/review, public store linking and AdMob readiness are not independently observed or assumed. Keep [the account handoff](PRE_RELEASE_READINESS.md#account-actions-before-live-activation) separate from this inactive candidate. Production-ad activation requires a separately reviewed change and exact-candidate qualification.

## Qualification

- Signed, minified/resource-shrunk APK/AAB build succeeds. APK and AAB signatures match the existing upload key; bundletool validation, manifest inspection, ZIP/config and native 16 KiB alignment pass.
- Release lint: **0 errors / 7 existing reviewed warnings / 0 hints**. Existing R8 Kotlin-metadata and Kotlin/dependency deprecation/nullability warnings remain diagnostic limitations; no warnings are suppressed to make the build pass.
- The updated source passes **182 JVM tests**, all 22 locale key/placeholder/resource sets, exact license coverage for **132 artifacts plus native encoder**, and offline Jev source/APK isolation. The earlier [67 selected debug emulator checks](PRE_RELEASE_READINESS.md#completed-debug-qualification) retain their earlier exact artifacts and are not relabeled as tests of this signed APK.
- Same-key in-place upgrade from frozen signed 0.1.11 preserves a real saved project and its private image assets. A fresh-process restore check passes before general smoke tests.
- The separate signed advertising-isolation workflow passes for undisclosed, under-13, both teen groups and adult stored choices. The separate Office workflow passes DOCX/XLSX import, explicit loss approval, leading-zero preservation, output XML, saving and unchanged originals.
- **18 distinct signed R8 workflows pass across the retained partial run and targeted follow-ups**, not one successful full-matrix invocation. Seven workflows finish in the first retained diagnostic; the remaining eleven plus the support regression finish in named follow-ups. Office and advertising isolation are two additional named workflows. Post-matrix process restoration passes. Five actual media outputs are independently decoded for duration/profile/tone/frame order; two PPTX files and all 180 slideshow frames are independently compared. Two Office ZIP/XML outputs are independently parsed for Unicode, leading zeros and absence of live spreadsheet formulas.
- Offline LGPL source rebuild and replacement pass: only the x86_64 encoder changes in a separate QA-key APK, the modified native version runs through MP3/OGG/FLAC UI workflows, and four actual audio outputs independently decode correctly. The official signed package is reinstalled; original native version 4.0 and the inactive-ad workflow pass again. The replacement APK is not a distribution artifact.
- Three additional PPTX output workflows pass on tablet, narrow/200%-text and dark layouts; screenshots are visually checked. The 200%-text case reuses a saved two-slide project and validates output controls, not new-draft form creation. Earlier unmeasured large-font form and complete accessibility paths are not declared solved by these three checks.

The first full-matrix diagnostic reached the saved video but looked for the former chooser label while restored work had switched it to `Change`. The platform harness now reacquires the actual change action during its wait; the application APK is unchanged. A second diagnostic returned from scrolled help content and could no longer see the old home-action pair; the harness now identifies Home by its fixed wordmark/language header. Both failed invocations are retained and are not counted as complete passing runs. The Office follow-up uses a separately recorded harness; both target the same frozen APK.

Evidence stays outside Git under `output/development/0.1.12/` and `output/releases/0.1.12/`. Exact final results are recorded in `output/releases/0.1.12/qa-summary.json`. Signed runtime checks use only the dedicated API 36 x86_64 QA emulator. Physical ARM/GPU/battery/thermal measurements, every provider combination, full human translation review and real Microsoft Office validation remain outside this selected matrix.

## Google Play notes

Each locale stays below 500 characters.

```text
<en-US>
Expanded batch tools and ZIP extraction. Improved DOCX/XLSX reading and editable PPTX export for supported content. Safer device-aware resource checks and image saving. Improved large-text, dark-mode and RTL layouts. Includes advertising privacy groundwork; live ads remain disabled in this test release.
</en-US>
<tr-TR>
Toplu işlem araçları ve ZIP çıkarma genişletildi. Desteklenen içeriklerde DOCX/XLSX okuma ve düzenlenebilir PPTX çıktısı iyileştirildi. Cihaza göre kaynak kontrolleri ve görsel kaydetme güvenliği geliştirildi. Büyük yazı, koyu tema ve RTL düzenleri iyileştirildi. Reklam gizliliği altyapısı eklendi; bu test sürümünde canlı reklamlar kapalıdır.
</tr-TR>
```

Upload the **code 13 AAB** to internal testing first, paste the tagged notes and promote the same AAB to closed testing after review. Reconcile Console declarations with the actual distributed candidate; prepared ad-enabled answers do not imply this inactive package is already monetizing. Do not reuse earlier native symbol ZIPs, upload private keys or infer Google approval from local success.

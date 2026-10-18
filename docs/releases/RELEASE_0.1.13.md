# Birlevo 0.1.13 signed maintenance test release

19 October 2026. Requested signed candidate **0.1.13 (14)**, package `studio.birlevo.android`, minSdk 24 / targetSdk 36. Application source: `3f49d34c35448105c5dacb42f510cee2f4ac6d5e`, based on merged `4ba565d` / [PR #29](https://github.com/gunerky/birlevo/pull/29). This request builds a new local candidate; no Google Play rollout or new GitHub release publication is performed.

## Change and Console boundary

The application version increases from 0.1.12 (13) to 0.1.13 (14). `com.google.android.gms.permission.AD_ID` is explicitly declared in the application's own manifest rather than relying solely on the SDK manifest merge. The previous signed code 13 bundle already contained the permission. The new bundle is also inspected for it; this change does not repair an older active bundle in Play Console.

If Console reports an active artifact without the permission, inspect the version codes in the edited release, retained bundles and other active tracks. Use the intended code 14 candidate from the artifact library where appropriate; do not change declarations solely to suppress validation. Actual collection, SDK presence and manifest permission are distinct. Live advertising remains disabled, and account linking/review plus exact-candidate disclosures remain separate gates.

No engine, format, quality option, final resolution, FileProvider identifier or saved schema changes. All ZIP entries in the new APK match the frozen 0.1.12 APK byte-for-byte except `AndroidManifest.xml` and `META-INF/version-control-info.textproto`. In particular, the DEX, resources, all 12 native libraries, third-party notices and offline MP3 corresponding-source bundle are unchanged. This comparison excludes ZIP metadata and APK signing blocks; it does not make the APK hashes equal.

## Frozen files

| File | Bytes | SHA-256 |
| --- | ---: | --- |
| `output/releases/0.1.13/birlevo-0.1.13-14.aab` | 19,295,129 | `19101c478f4daca784ef628f6c256eb935004486a2ce7d7dfe8d0cb6d394caf1` |
| `output/development/0.1.13/birlevo-0.1.13-14-release.apk` | 14,447,869 | `4b45af17f393e733b17d4852e48b9622ce7299386795762451a294f4575dbbd9` |

The AAB is for Play; the APK is for local installation. Both use the existing upload certificate, SHA-256 `3d14f0559a4e0f6332303fd352017b68d328daad41771e2b8bd383bf56856f2c`. Signing secrets stay outside Git and are used only in process environment variables. The former 0.1.12 artifacts and GitHub pre-release remain unchanged. Play app-signing can use a separate certificate, so the local upload-signed APK need not install over a Play-installed copy.

The exact R8 mapping is `output/development/0.1.13/mapping.txt`, SHA-256 `e50cf2bc4a15aad8da2f2b078562efc03afe87b08ab3d2df7b1f641d67ede4df`; its bytes match the mapping embedded in the AAB. Genuine matching native symbols for the eight owned LAME/bridge libraries across four ABIs are also embedded. Four upstream AndroidX libraries are pre-stripped, leaving coverage at **8/12**. Upload the AAB normally; this version's optional native-symbol ZIP is only a fallback if Console requests it. Private keys and replacement-library/test-harness APKs are not distribution artifacts.

## Qualification

- Signed R8/resource-shrunk APK/AAB build, existing signer verification, bundletool validation and exact final manifest inspection pass. The permission set is unchanged from code 13 and contains `AD_ID`; no precise-location or broad-media permission is added.
- APK ZIP/config and all 12 native libraries pass 16 KiB LOAD checks; all eight owned encoder/bridge libraries also pass RELRO, separate dynamic linkage, decoder exclusion and exact corresponding-source/notices verification.
- **182 JVM tests pass**, with no failures, errors or skips. Release lint reports **0 errors / 7 existing reviewed warnings / 0 hints**. Existing R8 Kotlin-metadata and Kotlin/dependency diagnostics remain; no suppression is introduced.
- The refreshed actual runtime inventory passes exact coordinate/file/hash coverage for **132 artifacts plus the native encoder**. All 22 locale keys/placeholders/resources and development-tool source/APK isolation pass. Licensing/CI tooling policy tests pass.

- Same-key in-place **0.1.12 → 0.1.13 upgrade passes**, preserving a real saved project and its private image asset and producing its PDF. Post-test fresh-process restoration also passes.
- **Seven selected signed R8 workflows pass** on the dedicated API 36 x86_64 `ofice_qa` emulator: inactive advertising across all five stored audiences, image, video, support/error handling, Office, expanded MP3/OGG/FLAC and MP3 source export. The disabled-ad checks find no age/ad entry or WorkManager database; they are not an exhaustive network-traffic audit.
- Four actual audio outputs independently decode with the expected duration, channel/sample profile and tone order. Two actual DOCX/XLSX outputs pass independent ZIP CRC/XML checks with leading zeros preserved and no live spreadsheet formulas. This is not validation in Microsoft Office.
- Offline corresponding-source rebuild and library replacement pass again for this exact APK. A separate QA-key copy runs the modified x86_64 encoder through real MP3/OGG/FLAC workflows and four independent decodes. The official signed APK is restored; native version 4.0 is verified again. The replacement APK is not a distribution artifact.

The [0.1.12 report](RELEASE_0.1.12.md) retains its broader 18-workflow, Office/privacy and layout matrix as historical evidence; those executions are not relabeled as tests of code 14. Because the runtime payload is unchanged, this maintenance release uses the selected checks above rather than claiming a new full matrix or repeating the three layout configurations.

Two initial old-version seed attempts encountered a QA Android **System UI / KeyguardService startup ANR** and are excluded from passing results. Their logs remain under `upgrade/seed-0.1.12.log` and `upgrade/seed-clean-0.1.12.log`; the system ANR event and current window hierarchy were captured. After cold boot and dismissal of the retained Android ANR window, the old-version seed passed, followed by the successful same-key upgrade and selected new-package checks. No application or harness change was made to hide these diagnostics.

Evidence is local under `output/development/0.1.13/` and `output/releases/0.1.13/`. Physical ARM/GPU/battery/thermal behavior, every provider combination, full linguistic review and general Microsoft Office compatibility remain outside this selected qualification.

## Google Play notes

The tagged EN/TR file is `output/releases/0.1.13/release-notes.txt`; both notes stay below 500 characters.

```text
<en-US>
Maintenance test update. The advertising ID permission is explicitly declared in the app manifest. Existing file tools and output quality settings are preserved. Live advertising remains disabled in this test release.
</en-US>
<tr-TR>
Bakım amaçlı test güncellemesi. Reklam kimliği izni uygulama manifestinde açıkça tanımlandı. Mevcut dosya araçları ve çıktı kalitesi seçenekleri korundu. Bu test sürümünde canlı reklamlar kapalıdır.
</tr-TR>
```

Upload **code 14** to the intended test track, or select it from the artifact library if already uploaded. Review included/retained versions and other active tracks if the advertising-ID error remains. A new build does not deactivate previous artifacts automatically, activate live ads or establish Google account/store approval.

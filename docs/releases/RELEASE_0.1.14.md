# Birlevo 0.1.14 signed sample-ad internal test

19 October 2026. Requested candidate **0.1.14 / version code 15**, package `studio.birlevo.android`, minSdk 24 / targetSdk 36. Application source commit: `d3b74044f7373fe397f16006cf80ed776566ac34`, based on merged `435a3b9` / [PR #32](https://github.com/gunerky/birlevo/pull/32). This is a local signed internal-test build. No Google Play upload/rollout or GitHub Release publication was performed.

## Behavior

The previous 0.1.13 package disabled both live and sample advertising. In addition, the development Home banner was restricted to debug emulators. This candidate explicitly uses the [signed sample-ad profile](../development/SIGNED_AD_TEST_PROFILE.md), enabling the Home flow on physical devices as well as emulators while using only Google's sample adaptive banner. Live advertising and revenue remain disabled. The owner's live banner identifier is absent from the APK's DEX; the sample banner identifier is present.

First Home entry asks for an optional age choice. Under-13 and undisclosed choices keep all file tools available without creating the application's consent/ad flow. Eligible choices use the existing age-aware regional privacy flow. An actual sample banner loaded for an adult and for the protected 13–15 group. Opening preferences and withdrawing age eligibility removes it; the acknowledged undisclosed choice remains after a new Activity. The actual QA region did not require a provider form in these signed runs. This does not re-label the historical forced-EEA debug checks as new signed-candidate form coverage.

The [build script](../../tools/release/build-signed-release.ps1) requires explicit `-TestAds`. Ordinary release generation remains inactive, debug preview still requires emulator hardware, and no development preview property can activate a live unit. Signed sample builds use the actual region without forced UMP debug geography. The configured application ID is the owner's, so the provider's published messages remain application-specific. The sample unit does not establish that the owner's live ad unit or store/account review is approved.

No runtime dependency, media engine, output quality/resolution, saved schema, FileProvider authority/root or processing capability changed. The existing SDK licenses, privacy wording and Play disclosures still apply to actual SDK data behavior; test ads are not a claim of zero collection. The owner previously reported completing the Console declarations; this build does not independently audit or modify account forms.

## Frozen artifacts

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| [Play AAB](../../output/releases/0.1.14/birlevo-0.1.14-15.aab) | 22,576,297 | `c9409950ed527743afee03ec8dc21ee3f12c6a46d86c223a8b00acb1128407bc` |
| [Local release APK](../../output/development/0.1.14/birlevo-0.1.14-15-release.apk) | 16,046,229 | `10bebbef091b211e5bfb6e973ae0ec9487ea5e87724983bc415311f1264cbd4d` |

Both use the existing upload certificate, SHA-256 `3d14f0559a4e0f6332303fd352017b68d328daad41771e2b8bd383bf56856f2c`. Signing secrets remain outside Git and were used only in process environment variables. The exact [R8 mapping](../../output/development/0.1.14/mapping.txt) is 92,376,604 bytes, SHA-256 `58a390db82a0631298bd58af3106f690117a9d783815a772f8f14dc627e6cdc0`; its bytes equal the map embedded in the AAB. Matching native symbols cover the eight owned LAME/bridge libraries out of twelve total; four upstream libraries remain pre-stripped. All frozen 0.1.13 artifacts remain unchanged.

Use the AAB in the **internal test** track. If a Play-installed copy uses a different Play App Signing certificate, update through Play rather than removing user data to install the local upload-signed APK. [Upload instructions](../../output/releases/0.1.14/UPLOAD_README.txt) and [release notes](../../output/releases/0.1.14/release-notes.txt) accompany the candidate.

## Verification

- **185 JVM tests pass**, no failures/errors/skips, including signed sample availability on physical-device hardware and the retained debug/emulator boundary. Both default-disabled and explicit sample release metadata pass their matching validator mode. Debug and release lint each report **0 errors / 7 existing warnings**.
- The requested signed build uses R8 and resource shrinking. Existing Kotlin metadata and native-strip diagnostics remain visible in the build log; no suppression was added. AAB signature/certificate matching, bundletool validation, exact manifest inspection, APK v2 signature and 16 KiB ZIP/ELF alignment pass. `AD_ID` is present; no new permission or fixture provider is added.
- Exact runtime license coverage remains **132 reviewed artifacts plus the native encoder**, with no dependency change. All 22 locales pass key/placeholder/generated-resource checks. Development-tool isolation passes against the exact APK without API calls.
- **Eight selected signed R8 workflows pass** on the dedicated API 36 x86_64 `ofice_qa` emulator: blocked audiences; actual adult sample banner/withdrawal/new Activity; actual protected young-teen sample banner; image/video processing with the initialized SDK resident in the same instrumentation process; Office; format/error handling; expanded MP3/OGG/FLAC; and offline corresponding-source export. [QA summary](../../output/releases/0.1.14/qa-summary.json) binds each successful case to its test-harness hash. Full-resolution [adult](../../output/development/0.1.14/runtime/sample-banner-adult.png) and [protected](../../output/development/0.1.14/runtime/sample-banner-age_13_15.png) banner captures were visually inspected.
- Four synthetic audio outputs independently decode with their intended duration, 48 kHz/stereo profile and tone order/pitch; the FLAC PCM digest also matches. Two Office outputs independently pass ZIP/XML parsing and leading-zero preservation; this is not a Microsoft Office interoperability guarantee.
- Corresponding native source, exact notices, dynamic JNI linkage, all four ABIs and 16 KiB LOAD/RELRO pass. The packaged source rebuilds offline, a customer-signed modified encoder runs MP3/OGG/FLAC workflows through the R8 app, and the official APK restores the native 4.0 ABI. The pulled restored APK matches the frozen APK hash exactly.
- Only the dedicated QA emulator was reset. Its display was restored and it was stopped after qualification. No personal device or user media was used.

The initial platform fixture could not see the Compose tag on an AndroidView even though the sample creative was visibly loaded; its original failure and screenshot were preserved. The reader now also checks the actual native visible banner WebView, which is revealed only after the real SDK callback. An independent protected-user scenario initially inherited the preceding scenario's persisted 60-second application limiter; cold-run verification passed, then the fixture was corrected to reset both QA audience and attempt preferences between independent scenarios. The production cooldown remains intact. A source-export test initially scrolled forward into long license text; its targeted fixture now returns to the first control before saving exact source ZIP bytes. None of these diagnostics was converted to a pass or fixed by weakening consent/age policy, and **the signed application artifacts did not change during the fixture corrections**.

The first seven final cases used harness SHA-256 `5758c9b0663e6f6ade8bec21aca18015ea62bafa1067fa118dd0f23d10f26134`; the targeted source-export follow-up and native replacement/restoration use `69f0211ac260619ed8191a2982de4b17280fcd6451387df32ed7d5300e1a4368`. Evidence, diagnostic harnesses and logs remain local under `output/development/0.1.14` and `output/releases/0.1.14`.

This selected exact-candidate qualification does not establish physical-device ARM/GPU/battery/thermal performance, every provider/network region, all file inputs or live advertising eligibility. Sample ads require network access and current provider permission; an unavailable provider leaves tools usable without an automatic retry loop.

## Reproduction

```powershell
tools/release/build-signed-release.ps1 -TestAds
python tools/testing/verify_ad_build_configuration.py --release-test-ads --report output/development/0.1.14/sample-ad-build-metadata.json
tools/release/verify-release-smoke.ps1 -Version 0.1.14 -Serial emulator-5580 -Workflows ad-test-blocked,ad-test-banner,ad-test-protected,ad-test-media,office,support,expanded-audio,mp3-source
tools/release/verify-lgpl-replacement.ps1 -Version 0.1.14 -Serial emulator-5580
```

Google references: [sample banner/test-device behavior](https://developers.google.com/admob/android/next-gen/test-ads), [UMP refresh and current consent](https://developers.google.com/admob/android/privacy).

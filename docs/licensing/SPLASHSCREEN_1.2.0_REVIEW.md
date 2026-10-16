# Core Splashscreen 1.2.0 review

Verified on 16 October 2026 for [PR #14](https://github.com/gunerky/birlevo/pull/14). This is a development dependency review; no version bump or signed release was produced.

## Runtime and commercial review

The only changed runtime artifact is `androidx.core:core-splashscreen:1.2.0`, SHA-256 `5ace865c4eb7e0f24369790d2c76eddafd0dbb1ebea3288f55c18d9605ea0ee0`. The [official Google Maven POM](https://dl.google.com/dl/android/maven2/androidx/core/core-splashscreen/1.2.0/core-splashscreen-1.2.0.pom) and the actual AAR's `META-INF/androidx/core/core-splashscreen/LICENSE.txt` both identify Apache-2.0. Its complete packaged license text is retained in the exact inventory and readable application notices. No advertising restriction was identified in those terms; notices and other distribution conditions still apply.

The resolved runtime remains 97 artifacts: 93 Apache-2.0, three Bouncy Castle and one MIT. Exact coordinate/filename/hash coverage passes without relaxing the licensing gate. The reviewed runtime contains embedded notices in 25 artifacts; duplicate full texts are deduplicated only in the readable notices.

## Application impact

The [official Core Splashscreen release notes](https://developer.android.com/jetpack/androidx/releases/core) were checked. Birlevo uses the compatibility starting-theme resources, but deliberately retains its own platform exit listener on API 31+ and first-frame fade on older Android. The upgrade does not replace that behavior or reintroduce the compatibility exit listener's deprecated system-bar setters. AppCompat remains 1.7.1 in this PR.

The reusable `tools/testing/startup-dependency-qa.ps1` runner verifies the dedicated AVD identity and API before installing the debug app/test APKs. System-bar tests now accept an explicit AppCompat day/night mode and assert the Activity's actual configuration. API 24 does not implement `cmd uimode night`; merely running that command would otherwise give false confidence about dark-theme coverage. The test restores its previous AppCompat mode.

## Validation

- `assembleDebug`, `assembleDebugAndroidTest`, all 147 JVM tests and `lintDebug` pass. Lint reports no errors, 61 warnings and two hints.
- API 24 `ofice_qa_api24` and API 36 `ofice_qa`: four instrumentation cases each, eight passed in total. Each checks light/dark system-bar appearance, edge-to-edge content and usable language dialog; all 22 locale configurations/RTL directions across Activity recreation; and presentation save/recreate/relaunch behavior.
- All 22 localization resources and three licensing-policy tests pass.
- Current debug APK SHA-256: `75c96406ef9cb4cceda6e072ffe9f87fd2b0e10870104691ab2d7b8d572f5acf`. Native verification passes for eight libraries across four ABIs, 16 KiB LOAD/RELRO alignment, separate encoder linkage, decoder exclusion and exact source/notices. The development-tool source/APK isolation guard passes.
- Local AST graph refreshed without an API call. The existing parser reports partial extraction for 24 Kotlin files; compiler and device-test results remain authoritative.

Ignored evidence is under `output/development/splashscreen-1.2.0/api24`, `api36` and `output/development/mp3-expansion/splashscreen-*`. These emulator/debug checks do not certify a signed release, physical-device performance or every device-specific splash animation.

Reproduce after a debug build:

```powershell
./tools/testing/startup-dependency-qa.ps1 -Api 24 -Serial emulator-5556 -Review splashscreen-1.2.0
./tools/testing/startup-dependency-qa.ps1 -Api 36 -Serial emulator-5554 -Review splashscreen-1.2.0
```

# AppCompat 1.8.0 review

Verified on 16 October 2026 for [PR #16](https://github.com/gunerky/birlevo/pull/16). This branch includes the reviewed [Splashscreen PR #14](https://github.com/gunerky/birlevo/pull/14); merge #14 before #16 so the reviewed runtime inventory remains cumulative. No application version bump or signed release was produced.

## Runtime and commercial review

Relative to the reviewed Splashscreen branch, only these runtime artifacts change:

| Actual AAR | SHA-256 |
| --- | --- |
| `androidx.appcompat:appcompat:1.8.0` | `773f79306eb0ca2447af8752e923ce6269e201e7fe6b275cc76e2998ee87e838` |
| `androidx.appcompat:appcompat-resources:1.8.0` | `3fe48cc3df409bed03063a4f5f5f9f8c51f1d63ea9e88d41e6489bf58495a5af` |

The official [AppCompat POM](https://dl.google.com/dl/android/maven2/androidx/appcompat/appcompat/1.8.0/appcompat-1.8.0.pom) and [resources POM](https://dl.google.com/dl/android/maven2/androidx/appcompat/appcompat-resources/1.8.0/appcompat-resources-1.8.0.pom) declare Apache-2.0. Both actual AARs contain the full license under their respective `META-INF/androidx/appcompat/<module>/LICENSE.txt` paths. Each is 10,175 bytes with text SHA-256 `809fa1ed21450f59827d1e9aec720bbc4b687434fa22283c6cb5dd82a47ab9c0`, identical to the previously reviewed Apache text. Full texts, coordinates, exact hashes and POM evidence remain in the machine-readable manifest; readable notices retain applicable attribution and terms.

The runtime remains 97 artifacts: 93 Apache-2.0, three Bouncy Castle and one MIT. There is no new native codec, font asset or advertising SDK. No commercial/advertising restriction was identified in the changed code licenses, subject to retaining their conditions. The exact licensing gate remains enabled.

## Application impact and validation

The [official AppCompat release notes](https://developer.android.com/jetpack/androidx/releases/appcompat#1.8.0) were checked against `MainActivity`, the AppCompat day/night starting theme and `LanguageDialog`'s locale delegate. Existing application APIs compile without a production-code change; saved-project keys, FileProvider identifiers and media engines are preserved.

Debug app/test builds, all 147 JVM tests and lint pass. Lint reports no errors, 60 warnings and two hints. Exact runtime license coverage, all 22 localization resources, three licensing-policy tests, five native-SDK preparation policy tests and development-tool isolation pass. The debug APK SHA-256 is `71def69c6e34ad50cd978ffafccf81dd7bd15e5be69d157d2ad212a15af1d343`; native verification passes for eight libraries across four ABIs, exact source/notices, 16 KiB LOAD/RELRO alignment, separate dynamic linkage and decoder exclusion.

All eight instrumentation cases passed on dedicated API 24 and API 36 emulators: actual AppCompat light/dark configuration, edge-to-edge/system-bar appearance and usable dialogs; all 22 locales and RTL directions across Activity recreation on each API; and presentation save/recreate/relaunch. The local AST graph was refreshed without a remote call; existing partial Kotlin parsing warnings do not replace compiler/test evidence. Raw logs and screenshots are ignored under `output/development/appcompat-1.8.0/api24`, `api36` and `output/development/mp3-expansion/appcompat-*`.

```powershell
./tools/testing/startup-dependency-qa.ps1 -Api 24 -Serial emulator-5556 -Review appcompat-1.8.0
./tools/testing/startup-dependency-qa.ps1 -Api 36 -Serial emulator-5554 -Review appcompat-1.8.0
```

Emulator/debug evidence does not replace requested signed-release validation or establish physical-device performance.

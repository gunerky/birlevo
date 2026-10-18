<p align="center">
  <img src="web/favicon-192.png" alt="Birlevo logo" width="80" height="80">
</p>

<h1 align="center">Birlevo</h1>

<p align="center"><strong>Convert, edit and create with your files on Android.</strong></p>
<p align="center">Android 7.0+ · Kotlin &amp; Jetpack Compose · 22 interface languages</p>
<p align="center">
  <a href="https://birlevo.com">Website</a> ·
  <a href="https://birlevo.com/en/privacy/">Privacy policy</a> ·
  <a href="mailto:support@birlevo.com">Support</a>
</p>

Birlevo brings image, video, audio, PDF, document and presentation tools into one Android app. File processing happens on the device and does not require a Birlevo account. A file-focused interface combines previews, light/dark themes and resumable workspaces.

[Features](#features) · [Local development](#local-development) · [Validation](#validation) · [Documentation](#documentation)

## Features

| Area | Capabilities |
| --- | --- |
| **Images** | Convert and compress to JPEG, PNG, WebP and BMP. Import HEIC, HEIF and AVIF on supported devices. |
| **Video** | Trim, join two clips, adjust framing and speed, and add text, image and audio layers. Export MP4/WebM; extract audio or a frame. |
| **Audio** | Trim, join two sources and convert to WAV, M4A, OGG/Opus, FLAC and MP3. |
| **PDF** | Read and preview pages; merge, select, reorder and rotate them. Export a page as an image or create a PDF from photos. |
| **Text and tables** | Read/convert TXT, Markdown source text, CSV and TSV. Import a bounded DOCX/XLSX subset and create simplified documents. |
| **Presentations** | Edit slides, themes, layouts, images and speaker notes; present full-screen. Export PDF, slide images, MP4 and PPTX. |
| **Batch and ZIP** | Separate image, video, audio and document queues. Package results as ZIP, inspect archives and extract selected files to a folder. |

Support depends on actual file content, Android version and device decoders/encoders. Conversion follows supported input/output pairs; arbitrary conversion between unrelated formats is not supported. See the [codec matrix](docs/features/CODEC_MATRIX_AND_VP9_SUPPORT.md).

DOCX/XLSX support is not a full Office renderer or an Excel formula engine. PPTX export offers appearance-preserving output and an editable subset for supported text; general PPTX import is not available. See [document scope](docs/features/documents/DOCX_XLSX_UI_INTEGRATION.md) and [PPTX/RTL coverage](docs/features/presentations/PPTX_RTL_AND_FONT_SCOPE.md).

## File handling and resource policy

- Originals are kept separate from working copies. New results are saved through Android's file picker.
- Compression is evaluated using actual source/output sizes. An equal or larger output requires explicit approval for that specific copy. Quality is never lowered automatically.
- Projects and media checkpoints use atomic storage. Cancellation, interruption and retry behavior is validated per tool; interrupted jobs do not restart automatically.
- Preview resource use is separate from final output quality and resolution.
- ZIP workspace admission uses available device storage with a safety margin. Image, video and PDF raster work also checks estimated memory/temporary space without changing requested output settings. These checks reduce risk; they do not guarantee native/GPU allocation success or a remote provider's quota.

## Source code and release builds

This README describes **the development scope of its branch**. Features in source may be newer than a previously generated APK/AAB or the version available to testers.

The latest prepared signed package is **0.1.11 / versionCode 12**. Its exact scope and English/Turkish release notes are recorded in [the release report](docs/releases/RELEASE_0.1.11.md). Version increments, signed builds and publishing are separate, explicitly requested steps. Normal development checks use debug builds.

## Local development

| Requirement | Version / purpose |
| --- | --- |
| JDK | 17 |
| Android SDK | Platform 36; minimum application API level 24 |
| Android NDK | 27.1.12297006 |
| CMake | 3.22.1 |
| Gradle | Use the repository wrapper; no separate installation needed |
| Python | 3.10+ for development and validation tools |

1. Clone the repository and open its root in Android Studio.
2. Install the required SDK, NDK and CMake versions through SDK Manager. Configure Java 17 and the SDK location through Android Studio's local settings or `ANDROID_HOME` in your terminal.
3. Build a debug APK from the repository root:

```powershell
.\gradlew.bat assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`. On macOS/Linux, use `bash gradlew assembleDebug`.

Debug builds do not require upload-signing credentials or a Jev/TypeSafe key. Machine-specific SDK settings, credentials and generated packages stay outside Git.

## Validation

Build the application/test APKs, run JVM tests and check Android lint:

```powershell
.\gradlew.bat assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug
```

Check documentation links, localization and the exact runtime license inventory:

```powershell
python tools/repository/check_documentation.py
python tools/localization/generate.py --check
.\gradlew.bat exportRuntimeLicenseInventory
python tools/licensing/audit_runtime.py --check
```

GitHub Actions also checks repository hygiene, offline development-tool tests, native libraries and APK isolation. It does not create signed releases or deploy the app/site.

Android end-to-end checks use a dedicated **QA emulator**. Tests can clear application data, so do not use an environment containing personal work. Start with `tools/testing/archive-flow-qa.ps1` and the [integrated regression report](docs/features/batch/BATCH_ZIP_FOLDER_FLOW_AND_REGRESSION.md). Existing PowerShell runners contain local SDK/Python paths that must be adapted to your environment. Emulator and independent-reader results are not physical-device performance measurements or a guarantee of general Microsoft Office compatibility.

### Localization

The source catalog for 22 interface languages is `tools/localization/catalog.json`. After editing it, regenerate and verify Android resources:

```powershell
python tools/localization/generate.py
python tools/localization/generate.py --check
```

This validates keys and format placeholders; linguistic review is still required. English developer naming does not remove Turkish or other application translations.

## Repository layout

```text
app/src/main/          Android application, media engines and interface
app/src/test/          JVM tests
app/src/androidTest/   Emulator tests and synthetic data providers
docs/                 Product, design, development, feature and QA documentation
docs/releases/        Release records and Play preparation
docs/licensing/       Commercial license and dependency reviews
tools/testing/        QA runners and independent output checks
tools/localization/   Translation catalog and resource generation
tools/licensing/      Commercial distribution dependency inventory
tools/jev/            Manually invoked development review tool
web/                  Website and privacy pages
```

`output/`, `graphify-out/`, build caches and local credentials are ignored. Jev/TypeSafe and Graphify are development tools, not runtime dependencies of the Android application.

## Licensing and commercial distribution

Birlevo is developed for commercial distribution with planned advertising revenue. Third-party SDKs, codecs and fonts are reviewed for that use; being free of charge is not sufficient. The [AdMob foundation](docs/development/ADVERTISING_FOUNDATION.md) provides debug consent testing, a [Home sample banner](docs/development/ADVERTISING_PLACEMENTS.md), and an optional [account privacy test profile](docs/development/ADVERTISING_ACCOUNT_QA.md). The [emulated Home flow](docs/development/HOME_AD_PRIVACY_FLOW.md) adds optional age choices, protected teen requests and a persistent privacy entry for the declared 13+ audience. Selected [initialized-SDK/media workflows](docs/development/ADVERTISING_MEDIA_QUALIFICATION.md) are qualified on dedicated emulators. Selected integrated UI/save/transfer, age and accessibility paths have a [pre-release qualification and account handoff](docs/releases/PRE_RELEASE_READINESS.md). Production advertising remains disabled pending account readiness, exact candidate disclosures and signed/R8 qualification. File contents are not supplied to advertising APIs. See the [exact SDK review](docs/licensing/ADVERTISING_SDK_REVIEW.md) and [privacy/Play drafts](docs/releases/ADVERTISING_DISCLOSURE_DRAFT.md) before distributing an affected candidate.

The English [commercial license review](docs/licensing/COMMERCIAL_LICENSE_INVENTORY.md) records exact dependency/hash coverage, required notices, offline corresponding source and the review process for dependency updates. Export the actual runtime inventory and run the checks above before distributing changed dependencies; a Dependabot version bump does not update that approval automatically.

The separate LAME encoder and JNI/Java bridge retain their LGPL licenses and offline source/replacement instructions; see the [source guide](third_party/lame/README.md) and [MP3 implementation report](docs/features/audio/MP3_OUTPUT_TARGET.md). Third-party licenses apply to their respective components. This repository does not currently declare a general open-source license for the application, and the inventory does not grant one. Advertising SDK integration must include a fresh terms/data review and updates to Play and privacy declarations.

## Documentation

Browse the [documentation hub](docs/README.md) for reports organized by topic. Developer-facing filenames and this entry point use English; detailed historical reports may still be written in Turkish. The [path migration index](docs/DOCUMENTATION_INDEX.md) maps earlier paths to their replacements.

| Topic | Entry point |
| --- | --- |
| Product scope and roadmap | [Product roadmap](docs/product/PRODUCT_ROADMAP.md) · [Conversion scope](docs/product/CONVERSION_SCOPE_AND_NEXT_STEPS.md) |
| Interface and accessibility | [File-focused interface](docs/design/FILE_FOCUSED_INTERFACE.md) · [Accessibility/RTL](docs/design/ACCESSIBILITY_AND_RTL.md) |
| Architecture and performance | [Modularity and performance](docs/development/MODULARITY_AND_PERFORMANCE.md) |
| Tests and releases | [Closed-test checklist](docs/testing/CURRENT_CLOSED_TEST_CHECKLIST.md) · [0.1.11 package](docs/releases/RELEASE_0.1.11.md) |
| Development rules and tools | [Working rules](AGENTS.md) · [Git/GitHub](docs/development/GITHUB_AND_VERSION_CONTROL.md) · [Jev](docs/development/JEV_DEVELOPMENT_TOOL.md) |
| Historical notes | [Previous README archive](docs/history/DEVELOPMENT_HISTORY.md) |

When reporting an issue, include the application version, Android version, file type and reproduction steps. Prefer a synthetic sample that reproduces the problem over personal files; never include credentials. Contact: [support@birlevo.com](mailto:support@birlevo.com).

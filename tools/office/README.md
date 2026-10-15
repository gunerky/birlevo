# DOCX and XLSX development trial

The bounded reader/writer is now in production as `office/OfficeCodec.kt`, connected through
`documents/OfficeDocument.kt` to the retained document workspace. DOCX/XLSX are advertised only
for the documented plain-text/cell subset; no runtime SDK was added. See
`DOCX_XLSX_ARAYUZ_ENTEGRASYONU.md` for the shipping controls, consent and source protection.
It reads paragraph/table text, sheet names, sparse coordinates, shared/inline strings, typed values,
formula text and optional stored results. Formula evaluation, Office page rendering and rich formatting
roundtrip are deliberately not claimed. `DOCX_XLSX_MOTOR_VE_LISANS_DENEMESI.md` records the earlier probe.

The fixtures contain synthetic text, a generated solid-color picture and a deliberately malformed ZIP.
No user files, embedded fonts, macros or online content are included. The external example hyperlink
is metadata only; Android never follows it. `manifest.json` records exact checked-in fixture hashes.
Builders are manual development commands, never Gradle/CI/hooks. Regeneration changes package IDs
and metadata, so review and refresh the manifest rather than silently regenerating during builds.

Resolve bundled Node/Python and packages with `load_workspace_dependencies`. `workbook.mjs` uses the
bundled Artifact Tool through a local, ignored `node_modules` junction; do not install packages into
the app. Word fixtures/semantic checks use bundled `python-docx`; the independent XLSX reader also
uses bundled `openpyxl`. None is shipped in the Android application.

```powershell
# Use the resolved bundled executables for the Python/Node commands below.
python tools/office/fixtures.py create output/development/office-trial/fixtures
node tools/office/workbook.mjs create output/development/office-trial/fixtures
python tools/office/fixtures.py manifest output/development/office-trial/fixtures
# Copy only rich.docx, rich.xlsx, shared-no-cache.xlsx, duplicate.zip and manifest.json into
# app/src/androidTest/assets/office after review. Preview images stay in ignored output/.
./gradlew.bat assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug exportRuntimeLicenseInventory
python tools/licensing/audit_runtime.py --check
./tools/testing/office-trial-qa.ps1
./tools/testing/office-document-qa.ps1 -SkipInstall -Profile Phone
./tools/testing/office-document-qa.ps1 -SkipInstall -Profile LargeText
./tools/testing/office-document-qa.ps1 -SkipInstall -Profile DarkTablet
python tools/office/verify.py output/development/office-trial/native output/development/office-trial/independent output/development/office-trial/fixtures
node tools/office/workbook.mjs verify output/development/office-trial/native output/development/office-trial/independent
./tools/testing/office-trial-qa.ps1 -SkipInstall -Roundtrip
```

Keep instrumentation runs sequential on the dedicated QA emulator. Do not interpret a screenshot,
formula cache, successful unzip or re-export as actual Microsoft Office compatibility. Source Word
layout rendering was attempted with the packaged renderer; LibreOffice was unavailable. No DOCX
visual-fidelity approval or Office installation is implied. XLSX preview rendering is an independent
development check and font/renderer differences still apply. Physical-device performance is unmeasured.

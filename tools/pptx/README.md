# PPTX export QA and historical compatibility trial

Production now includes **raster, appearance-preserving export only** using its own `PptxPackage` writer;
it does not use the test template or editable prototype below. `verify_export.py` checks the six actual
production outputs (1024/2048/4096, notes excluded/included) against independent rendering and notes inspection.
Run `tools/testing/pptx-export-qa.ps1` for feature/layout/regression suites, then the existing host render command
with `output/development/pptx-export/native/*.pptx`, followed by `python tools/pptx/verify_export.py output/development/pptx-export`.
All host artifacts remain ignored and no runtime package is added. See `PPTX_GORUNUM_KORUYAN_DISA_AKTARIM.md`.

## Historical engineering probe

This is an engineering probe, not advertised Office support. No Android production code,
runtime dependencies, storage schema, fonts, or format-support registry changed.

- `PptxTrialExporter.kt` lives **only in androidTest**. It uses Android/Java ZIP, the existing
  native slide renderer and an authored blank test template. It compares editable text + PNG
  against flattened full-slide PNG, with speaker notes in separate notes parts.
- `PptxTrialDeviceTest.kt` exports four synthetic multilingual slides in all three themes /
  three layouts, checks source byte preservation and cancellation/invalid-XML cleanup.
- `inspect_pptx.py` inventories content and loss flags offline. It neither maps to the limited
  Birlevo slide model nor renders arbitrary PPTX. It always reports `general_import_ready=false`.
- `fixtures.mjs` uses bundled Artifact Tool **2.8.89** on the development host to author a
  separate reference (including native table/chart), import Android results, render, and edit
  a heading through the independent reader. No `python-pptx`, online service, or media upload.
- `verify_trial.py` checks Unicode, notes separation, native object editability, image hashes,
  real image dimensions, independent edit round-trip and raster comparison. Host Pillow only.

The blank test asset contains generated shapes/styles and no embedded font or external artwork.
Its checked-in SHA-256 is `531c1bbd1a124a782f62a8c1ef0fb9fc4feb66fd2b1dc637ecf64f2326eea83a`.
Regeneration changes generated relationship IDs; do not silently replace it during Gradle builds.
Android exports font **names**, not font files. Raster mode uses the existing Android font shaping.

## Repeat locally

Resolve bundled Node / Artifact Tool paths with `load_workspace_dependencies`, then:

```powershell
python -m unittest discover -s tools/pptx -p 'test_*.py' -v
./gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest exportRuntimeLicenseInventory
python tools/licensing/audit_runtime.py --check
powershell -NoProfile -ExecutionPolicy Bypass -File tools/testing/pptx-trial-qa.ps1
# Host fixture generation, not part of Gradle/CI:
node tools/pptx/fixtures.mjs create <absolute-artifact_tool.mjs> output/development/pptx-trial/fixtures
# Pass the six explicit native PPTX paths to the independent reader:
node tools/pptx/fixtures.mjs render <absolute-artifact_tool.mjs> output/development/pptx-trial/rendered <native-pptx-paths...>
python tools/pptx/verify_trial.py output/development/pptx-trial
```

Generated PPTX, images, inventories and measurements remain in ignored `output/development/pptx-trial/`.
The test-only template must never become a main application asset without a deliberate production
implementation / package review. No automatic regeneration, live AI call or release build.

## Input bounds and claim boundary

The inventory accepts UTF-8 Transitional OOXML only: compressed file <=64 MiB, <=2,000 parts,
each part <=32 MiB, total inflated <=128 MiB, XML <=2 MiB / 50,000 elements / depth 100,
1–100 slides. Duplicate/unsafe paths, broken links, DTD/entities, embedded binary/macros and
unsupported namespaces fail closed. External links are inventoried without resolving or fetching.
There is no extraction to disk and no native command execution. These are **probe limits**,
not changes to product export resolutions or supported existing files.

The host inventory keeps bounded ZIP data in memory. Production import must stream selected parts,
use an Android parser with equivalent guards and privately own copied images before project save.
The editable writer's Android text measurement deliberately mirrors current fitting only in this
test probe. Production needs shared layout metadata, not a second fitting implementation.

Independent reader success does not prove PowerPoint / Google Slides rendering or general OOXML
schema validity. RTL / font substitution remain unaccepted. Do not turn rich slides into one
heading/body/image without an explicit loss preview and user approval.

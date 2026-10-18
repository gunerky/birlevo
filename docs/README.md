# Birlevo documentation

Start with the [repository README](../README.md) for product capabilities, setup and validation commands. This directory contains the detailed product, engineering and release records.

Unless a document says otherwise, run commands from the **repository root**, including commands shown in historical reports. Local evidence under `output/` is ignored by Git and is available only when the corresponding QA/release outputs have been generated.

## Start here

| Topic | Entry points |
| --- | --- |
| Product scope and planned work | [First production plan](product/FIRST_PRODUCTION_PLAN.md) · [Product roadmap](product/PRODUCT_ROADMAP.md) · [Conversion scope and next steps](product/CONVERSION_SCOPE_AND_NEXT_STEPS.md) |
| Interface and branding | [File-focused interface](design/FILE_FOCUSED_INTERFACE.md) · [Design language](design/DESIGN_LANGUAGE.md) · [Brand and logo](design/BRAND_AND_LOGO.md) |
| Accessibility and languages | [Accessibility and RTL](design/ACCESSIBILITY_AND_RTL.md) · [Localization review](development/LOCALIZATION_FORMAT_AND_JEV_REVIEW.md) |
| Architecture and resource use | [Modularity and performance](development/MODULARITY_AND_PERFORMANCE.md) · [Operation resource budgets](development/OPERATION_RESOURCE_BUDGETS.md) · [Legacy Android/PDF memory](development/LEGACY_ANDROID_AND_PDF_MEMORY.md) · [Presentation export resources](development/PRESENTATION_EXPORT_RESOURCES.md) · [External PDF resources](development/EXTERNAL_PDF_RESOURCES.md) · [Video resources](development/VIDEO_RESOURCE_RELIABILITY.md) |
| Saved work and processing | [Project/workspace management](development/PROJECT_AND_WORKSPACE_MANAGEMENT.md) · [Background processing](development/BACKGROUND_PROCESSING.md) · [Media session resilience](development/MEDIA_SESSION_RESILIENCE.md) · [Storage cleanup safety](development/STORAGE_CLEANUP_SAFETY.md) |
| Development tools | [Git and GitHub](development/GITHUB_AND_VERSION_CONTROL.md) · [Jev development tool](development/JEV_DEVELOPMENT_TOOL.md) · [Lint and compatible dependencies](development/LINT_WARNING_REVIEW.md) · [Working rules](../AGENTS.md) |
| Testing | [Current closed-test checklist](testing/CURRENT_CLOSED_TEST_CHECKLIST.md) · [Emulator report](testing/EMULATOR_TEST_REPORT.md) · [System file picker](testing/SYSTEM_FILE_PICKER_TEST_REPORT.md) |
| Releases | [0.1.11 release record](releases/RELEASE_0.1.11.md) · [All release records](releases/) |
| Commercial distribution | [License inventory and obligations](licensing/COMMERCIAL_LICENSE_INVENTORY.md) · [Dependency reviews](licensing/) |
| Advertising | [Foundation and consent lifecycle](development/ADVERTISING_FOUNDATION.md) · [Home banner and activation gates](development/ADVERTISING_PLACEMENTS.md) · [Account privacy message QA](development/ADVERTISING_ACCOUNT_QA.md) · [Home flow and protected age choices](development/HOME_AD_PRIVACY_FLOW.md) · [Published website setup](releases/ADVERTISING_SITE_SETUP.md) · [SDK terms and exact licenses](licensing/ADVERTISING_SDK_REVIEW.md) · [Privacy and Play drafts](releases/ADVERTISING_DISCLOSURE_DRAFT.md) |

## Features

The [codec matrix](features/CODEC_MATRIX_AND_VP9_SUPPORT.md) describes shared media capabilities and device-dependent support.

| Area | Reports and implementation scope |
| --- | --- |
| [Images](features/images/) | [Formats and photo PDF](features/images/IMAGE_FORMATS_AND_PHOTO_PDF.md) · [Image/PDF output targets](features/images/IMAGE_AND_PDF_OUTPUT_TARGETS.md) |
| [Video](features/video/) | [Output targets](features/video/VIDEO_OUTPUT_TARGETS.md) · [Clip joining](features/video/VIDEO_CLIP_JOINING.md) · [Framing and speed](features/video/VIDEO_FRAMING_AND_SPEED.md) |
| [Audio](features/audio/) | [Audio tools and formats](features/audio/AUDIO_TOOLS_AND_OUTPUT_FORMATS.md) · [Trimming/joining](features/audio/AUDIO_TRIMMING_AND_JOINING.md) · [MP3](features/audio/MP3_OUTPUT_TARGET.md) |
| [PDF](features/pdf/) | [Reader and page handoff](features/pdf/PDF_READER_AND_PAGE_HANDOFF.md) · [Page tools](features/pdf/PDF_PAGE_TOOLS_AND_UI_REVIEW.md) |
| [Text, tables and Office documents](features/documents/) | [Text/table conversion](features/documents/TEXT_TABLE_READER_AND_CONVERSION.md) · [DOCX/XLSX scope](features/documents/DOCX_XLSX_UI_INTEGRATION.md) |
| [Presentations](features/presentations/) | [Editable PPTX subset](features/presentations/PPTX_EDITABLE_SUBSET.md) · [PPTX/RTL/font scope](features/presentations/PPTX_RTL_AND_FONT_SCOPE.md) · [Slide video](features/presentations/IMAGE_AND_SLIDE_VIDEO.md) |
| [Batch processing](features/batch/) | [Shared media queue](features/batch/BATCH_VIDEO_AUDIO_AND_SHARED_QUEUE.md) · [Documents and approved recipes](features/batch/BATCH_DOCUMENTS_AND_APPROVED_RECIPES.md) · [Integrated regression](features/batch/BATCH_ZIP_FOLDER_FLOW_AND_REGRESSION.md) |
| [ZIP archives](features/archives/) | [Safe extraction](features/archives/ZIP_READING_AND_SAFE_EXTRACTION.md) · [Multiple selection and folders](features/archives/ZIP_MULTI_SELECTION_AND_FOLDER_EXTRACTION.md) |

## History and document maintenance

Historical reports describe the versions, test environments and limitations recorded at the time; they do not automatically certify later releases. Their original language is preserved. See the [development history](history/DEVELOPMENT_HISTORY.md).

The [path migration index](DOCUMENTATION_INDEX.md) lists every relocated root document and the earlier Turkish-to-English filename mappings. Git history retains the old paths.

Add detailed reports under the matching topic directory and update this hub when adding a new area. Keep the root README focused on the product and contributor entry points. Check repository-relative documentation links with:

```powershell
python tools/repository/check_documentation.py
```

This check validates tracked local paths, including images and directory links. It does not fetch external websites or validate heading fragments. Generated QA/release references are reported separately because they are not published in GitHub.

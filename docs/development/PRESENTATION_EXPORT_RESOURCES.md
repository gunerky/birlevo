# Presentation PDF and slideshow export resources

Date: 16 October 2026. Development validation only; version 0.1.11 (12) and the frozen signed release are unchanged.

## Problem and implementation

Closing a decoded slide bitmap did not release the copy retained by Android's native multipage `PdfDocument`. In the API 28 baseline, 24 completed image slides retained approximately 72 MiB of additional native allocations. Slideshow rendering already released each raster, but lacked its own operation admission and live encoder disk monitor, and reconstructed an immutable presentation for every frame.

Presentation PDF now uses two stages:

1. Generate the complete text/vector document without drawing images. Native Android shaping, common font subsets, page dimensions, layout, captions and footer numbers stay shared across pages. Close and sync this document before loading it with the existing disk-backed PDFBox dependency.
2. Decode one image at the existing 1024 PDF edge, draw it into a transparent native page at its original position, save and close that native document, then copy its encoded resources as a form into the matching vector page. Release the private bitmap and delete the temporary page before processing the next image. Save, sync and atomically rename the completed result.

`GeneratedPdfSpool` accepts only generated files in the owning private folder with the expected work-file prefixes. Its disk scratch rejects PDFBox's zero-means-unlimited setting. Arbitrary external PDFs continue to use the isolated worker; this change does not bring external parsing into the application process. The shared writer adds cancellation checks, guarded writes and descriptor sync to the existing photo-PDF path. The [PDFBox form import API](https://pdfbox.apache.org/docs/2.0.13/javadocs/org/apache/pdfbox/multipdf/LayerUtility.html) copies a saved source page into the destination; the imported form is ordinary page content, not an optional layer.

One-page previews retain the direct native path. Text remains searchable, images are not encoded a second time, alpha is preserved and existing text overflow remains an explicit error. Native font/text structures and PDFBox page/resource metadata still grow with content; this removes retained decoded images, not every allocation proportional to page count. The existing 100-slide / 200,000-character bounds remain.

An initial experiment generated every complete slide separately. It reduced native retention but repeated font subsets and grew the 24-page fixture from 109,740 to 770,824 bytes. That implementation was rejected. The shared-font result is 126,358 bytes: 16,618 bytes (15.1%) above the original small synthetic PDF due to composition resources. No compression-size improvement is claimed, and new-document creation is not classified as compression.

Slideshow export now constructs one immutable presentation per job, checks the requested raster/encoder memory budget, guards PNG writes, reserves estimated encoded output space and checks free storage during native encoding. A pressure failure cancels the Transformer through the existing main-thread cleanup, removes only that job's work/result files, and leaves sources and previous results intact. No automatic bitrate, frame-rate, quality or resolution fallback was added. The estimate is admission guidance, not a reservation or exact native/GPU allocation bound.

## Measurements

The synthetic fixture has distinct 1536×1024 PNG sources, text, an image-left layout and a dark theme. PDF images remain 1024×683. Video uses the existing 640×360 / 30 fps setting and 500 ms per slide. Samples record Java used bytes, allocated native bytes and process PSS before generation, at progress callbacks and after save. They are retained-memory samples, not exhaustive peaks, GPU measurements, physical-device results or a latency benchmark. Source preparation and independent verification occur outside the measured interval.

| PDF run | Highest sampled native | Native growth from first sample | Highest sampled Java | Highest sampled PSS | Generation |
| --- | ---: | ---: | ---: | ---: | ---: |
| API 28 baseline, 24 slides | 77.08 MiB | 72.06 MiB | 1.90 MiB | 190.17 MiB | 2.61 s |
| API 28 final, same 24 slides | 5.33 MiB | 0.24 MiB | 4.01 MiB | 135.25 MiB | 2.62 s |
| API 28 final, 100 slides | 5.91 MiB | 0.44 MiB | 9.15 MiB | 139.80 MiB | 9.94 s |
| API 24 final, 24 slides | 5.90 MiB | 0.00 MiB | 16.89 MiB | 46.71 MiB | 2.58 s |
| API 36 final, 24 slides | 10.65 MiB | 0.20 MiB | 6.45 MiB | 141.97 MiB | 2.98 s |

The 100-page result is 447,197 bytes. It retains all 100 ordered images and searchable slide text. Slideshow's 24-slide native samples stay around 12 MiB both before and after; the video changes are admission, reuse and failure handling, not a demonstrated large memory reduction. The final 100-slide MP4 has all 1,500 frames and a 50-second duration.

Android versions account bitmap and font allocations differently. The API 24 first sample already includes pending fixture/native allocations, so zero sampled growth is not a zero-allocation claim. Compare the baseline and final workload on API 28; do not rank hardware performance using these cross-version columns.

API 24/28 QA emulators use 2 GiB RAM and a 256 MiB app heap. The API 36 Play image enforces at least 2.5 GiB RAM. Host GPU acceleration and x86 emulator codecs are used; these do not establish ARM/32-bit, low-end hardware, battery or FPS performance.

## Reproduction and evidence

```powershell
./gradlew.bat assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug
./tools/testing/presentation-resource-qa.ps1 -Serial <dedicated-QA-serial> -Api 24 -ProfilePages 24
./tools/testing/presentation-resource-qa.ps1 -Api 28 -ProfilePages 24 -ProfileOnly
./tools/testing/presentation-resource-qa.ps1 -Api 28 -ProfilePages 100 -ProfileOnly
./tools/testing/presentation-resource-qa.ps1 -Serial <dedicated-QA-serial> -Api 36 -ProfilePages 24
python tools/testing/verify_presentation_resources.py <evidence>/presentation-resources --pages 24 --baseline <baseline>/presentation-resources --photos <evidence>/legacy-pdf --ffmpeg <host-ffmpeg> --report <local-report.json>
```

The host verifier requires the development-only `pypdf`, `pypdfium2`, NumPy/Pillow and FFmpeg runtimes. They are not Android dependencies. It decodes every embedded PDF image, extracts every slide's text, compares all 24 baseline/final 1280×720 page renders and streams every decoded video frame. A final newline introduced by form-aware text extraction is ignored; actual slide text, order and image pixels must match. Video dimensions, duration, frame count, silent H.264 encoding and color/order are checked. The 100-slide case checks every source color and all 1,500 frames without holding the whole decoded video in host memory.

`PresentationResourceDeviceTest` adds cancellation, disk pressure during page generation/save and during native video encoding, cleanup/retry, existing-result/source preservation, requested-size admission and delivered-preview ownership. Its appearance reference uses the original native multipage composition with the production painter, comparing all three themes and layouts with alpha/mixed-script text and a text-only page. Single-page and multipage native font subsets can differ in raster hinting; the reference deliberately compares the same multipage workload rather than requiring separate previews to have identical hinting.

Ignored raw evidence is under `output/development/presentation-resources/`: `api24-final-24`, `api28-baseline-24`, `api28-final-24`, `api28-final-100` and `api36-final-24`. `api28-comparison.json` and `api28-100.json` contain independent checks and byte-level measurements. `first-spool-experiment/` retains the rejected font-bloat approach. Earlier failing appearance comparisons are retained separately and are not counted as passes.

Validation completed:

- Debug application/instrumentation builds and 147 JVM tests pass with no failures, errors or skips. Lint has 0 errors, 60 warnings and 2 hints; the additional warning suggests a KTX URI wrapper for the new call.
- All seven resource cases pass on API 24/28/36. Two shared photo-PDF regressions on each API retain exact 1536×1024 pixels and the 4096×2048 option, cancellation and disk-error cleanup. The additional API 28 100-slide PDF/video profiles pass separately.
- All 33 API 36 consumer cases pass: presentation PDF, photo PDF, shared layout/mixed scripts, slideshow engine/UI, transfer/consent/session handling and PPTX's 1024/2048/4096 choices. Together with the 27 cross-API resource/photo cases this is 60 targeted emulator checks. The first consumer run exposed an asynchronous preview assertion; the test now waits for its separate preview job and scrolls the preview into view. The initial failure and isolated retry remain logged; the complete corrected suite passes in `consumer-regression-final.log`.
- Independent host checks validate all 24 PDF images/texts and all 360 video frames on each API; API 28 additionally compares the baseline/final page rasters, image pixels and decoded video frames exactly. The 100-slide run verifies every page/image and all 1,500 frames.
- Repository hygiene, documentation links, 22 locale keys/placeholders/resources, 20 offline/mock Jev tests, 12 PPTX Python tests, five CI-tool tests and three license-tool tests pass. No live Jev call or source/media upload occurred.
- Exact commercial runtime coverage remains 97 artifacts plus the existing native encoder. The final debug APK passes eight native libraries / four ABIs / 16 KiB LOAD and RELRO / dynamic linkage / corresponding source and notices; the offline tool-isolation guard passes. No SDK, codec, font or runtime package was added.

Final debug application SHA-256: `36f4b7615ffea2dcb48e93662a73ddbb4f327977cadde7aac98c08d6566f2df1`. This is not a new distribution artifact. The frozen signed 0.1.11 (12) release remains untouched. Local AST extraction is updated without an API key, clustering or a remote model (4,285 nodes / 11,604 edges); 24 existing partial Kotlin parser warnings are not accepted in place of compiler/test results.

## Remaining scope

Next: profile image-heavy external PDFs near the existing input byte ceiling, including isolated-worker memory, encoded resource copying, cancellation/disk failures and exact content preservation. Pathological object graphs, physical-device behavior and all document providers remain unmeasured. New release work still requires a user request and must verify the final R8/shrunk signed artifact, native source/replacement package, alignment and matching symbols.

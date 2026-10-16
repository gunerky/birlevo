# Legacy Android and multipage PDF memory

16 October 2026. Development changes only; version 0.1.11 / code 12 and the frozen signed artifacts are unchanged.

## Changes

- `SlideImages` uses the shared decode plan: sample only while enough pixels remain, apply EXIF orientation, then resize to the selected edge without upscaling small inputs. Common formats use the platform target decoder where available. The API 24/28 path previously returned 2000 pixels for a 4000-pixel input when 2048 was selected (and also undershot 2560).
- Photo PDF creation closes each native `PdfDocument` after one page. Encoded page streams are cloned into a disk-backed document using the existing PDFBox-Android dependency. Images receive no second encoding; A4 orientation, margins, white transparency background, page order and 2048/4096 choices remain.
- Scratch and intermediate files use the existing private `photo-pdf` folder. Actual device free space is checked per page and output writes are guarded. A zero scratch allowance is rejected because PDFBox interprets zero as unlimited. Source pages and an existing result survive cancellation or storage failure; temporary pages/scratch/output are removed, and retry is tested.
- Final output is synced before the existing atomic rename. The output wrapper prevents PDFBox from closing the descriptor before sync. Project/session formats, FileProvider roots and source-specific copy consent are unchanged.

The scratch configuration follows the [PDFBox memory guidance](https://pdfbox.apache.org/2.0/faq.html); scratch limits bound stream storage, not every Java object. No new runtime package, codec or font was added. The existing isolated external-PDF page worker remains isolated and keeps its separate 64 MiB stream budgets; this change does not move untrusted PDF parsing into the application process. The in-process photo writer reads only its own generated one-page files.

## Reproduction and measurements

Dedicated x86_64 QA AVDs run individually with 2 GiB configured RAM; API 24/28 have 256 MiB VM heaps and API 36 has a 228 MiB heap. API 36 is launched with an explicit `-memory 2048` override. GPU must be enabled in the legacy AVD config; tested launch uses `-gpu host -feature GLESDynamicVersion`. Confirm the legacy guest reports OpenGL ES 3.1 (`ro.opengles.version=196609`). Initial ES 2-only configuration failed EGL setup and was not accepted as a passing video test. API 36's software-GPU boot stalled; its accepted run uses the host GPU. No physical-device performance is inferred from host GPU results.

The stock API 24 image does not advertise a decoder for the existing AVC High fixture. Its video budget/retry test uses an additional synthetic AVC Constrained Baseline 3.1 / 720p / AAC fixture instead. Supported application profiles and output dimensions are not reduced; capability-dependent rejection remains. The new test fixture is not included in the application APK.

Photo samples record Java used bytes, allocated native bytes and process PSS before generation, after every completed page, and after save. These are sampled retained-memory values, not an exhaustive allocation peak, GPU usage or a physical-device guarantee. Inputs are synthetic solid-color pages with a distinct marker and known order; photographic noise, complex vector resources and arbitrary third-party PDFs are different workloads.

| Run | Source / output images | Highest sampled allocated native | Highest sampled Java used | Highest sampled PSS | Generation time |
| --- | --- | ---: | ---: | ---: | ---: |
| API 28 baseline, 24 pages | 1536×1024 / same | 148.7 MiB | 1.4 MiB | 257.2 MiB | 2.27 s |
| API 28 fixed, same 24 pages | 1536×1024 / same | 12.2 MiB | 24.4 MiB | 147.4 MiB | 1.25 s |
| API 28 fixed, 100 pages | 1536×1024 / same | 12.1 MiB | 24.6 MiB | 147.3 MiB | 5.28 s |
| API 24 fixed, 100 pages at 4096 | 5000×2500 / 4096×2048 | 5.8 MiB | 66.1 MiB | 95.7 MiB | 28.95 s |
| API 36 fixed, 100 pages at 4096 | 5000×2500 / 4096×2048 | 57.6 MiB | 12.5 MiB | 194.0 MiB | 31.73 s |

These are single runs with instrumentation/sampling overhead, not a latency benchmark. Source preparation and later independent validation are outside the generation interval; memory samples can include pending fixture-preparation allocations (API 36's first sample includes the 5000×2500 preparation bitmap/Canvas; completed-page native samples are about 10 MiB). Android versions account bitmap allocations differently; compare baseline and fixed results on the same API rather than ranking devices by one column.

Independent host decoding verified pixel equality between all 24 baseline/fixed image pages. It also decoded every image in the 100-page PDFs and checked dimensions/order/pixels. API 36's platform PDF display rasterizer changed one sampled red value from 119 to 115; the embedded Flate image still decoded to exactly 119/80/180. The device preview check therefore allows six component levels, while host checks remain exact for every saved page. A separate large-image case confirms embedded 4096×2048 output, EXIF rotation and alpha handling. The isolated worker reordered and rotated 2000 vector/text pages; host extraction checks all 2000 strings and that pages were not rasterized. This is a page-count stress case, not a claim about the worst 128 MiB image-heavy PDF.

## Commands and evidence

```powershell
./gradlew.bat assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug
./tools/testing/legacy-pdf-qa.ps1 -Api 24 -ProfilePages 100 -HighResolution -SkipBuild
./tools/testing/legacy-pdf-qa.ps1 -Api 28 -ProfilePages 100 -SkipBuild
./tools/testing/legacy-pdf-qa.ps1 -Api 36 -ProfilePages 100 -HighResolution -SkipBuild
python tools/testing/verify_legacy_pdf.py <evidence-folder>/legacy-pdf
python tools/testing/verify_media_resources.py <evidence-folder>/media-budgets --ffmpeg <host-ffmpeg>
```

`LegacyPdfMemoryDeviceTest` is compatible with API 24 and avoids API 33 locale setup. It checks exact sampling/EXIF, available modern format APIs, retained native memory, disk/cancel cleanup, 4096 embedded pixels and 2000 vector pages. `MediaResourceBudgetDeviceTest` adds image formats, memory/storage failures, video monitor/retry and isolated PDF raster export. Per-page completed native retention must remain below the synthetic test's 64 MiB growth bound for 24+ pages; this is a regression assertion, not a general allocation guarantee.

Accepted validation:

- 147 JVM tests: no errors, failures or skips. Debug application/test builds pass; lint has 0 errors, 68 warnings and 2 hints (the new warning recommends a KTX bitmap scaling wrapper).
- 27 targeted device checks across API 24/28/36: five legacy/PDF tests and four resource-budget tests per API. Additional 100-page profiles are retained separately. API 36's existing photo PDF, PDF pages, image compression/formats, presentation PDF/PPTX and slideshow classes add 40 passing regression checks.
- Independent host checks decode every photo-PDF image, all 2000 vector-page strings/rotations, four 2560 image formats, three 4096 PDF raster formats and the complete 640×360 H.264/AAC video on each API.
- Repository hygiene, 22 locale keys/placeholders/resources, 20 offline/mock Jev tests, 12 PPTX Python tests and 5 CI-tool tests pass. Windows Jev mock/temp/credential tests require the normal user environment; the restricted sandbox's temporary-file access errors were not accepted as a pass. No live API call was made.
- Exact commercial runtime coverage remains 95 artifacts plus the native encoder. Existing debug APK passes eight native libraries / four ABIs / 16 KiB LOAD and RELRO / dynamic linkage / exact corresponding source and notices. Offline APK isolation passes. Local AST graph is updated without clustering or an LLM (4176 nodes / 11257 edges; 24 existing partial-parser warnings are not compiler failures).

Ignored local evidence is under `output/development/legacy-pdf/`, including environment/APK hash, device logs, CSV files and actual PDFs/images/video. Accepted matrix folders are `api24-24-2048`, `api28-24-2048` and `api36-100-4096`; the extra high-resolution API 24 profile is `api24-100-4096`, the API 28 100-page profile is `api28-100`, and UI/consumer regressions are under `api36-regression`. Baseline raw evidence is under `output/development/api28-baseline/`. Earlier failing attempts remain separately logged, not counted as passes.

The debug application SHA-256 is `52ed87400f56f0e80f5da17d6a4f7518649c3cd9cf6f2a299b15e9e5965cd84b`; it is a development artifact, not a new distribution. Worker memory sampling observed a highest PSS of 65.1 MiB on API 24 and 100.6 MiB on API 28 in the 2000-page case. These are sampled values only. API 36's sampler did not capture the short-lived isolated worker, so no worker peak is claimed there; only completion/content checks and parent photo-PDF profiles are established.

The additional test-only Baseline video is derived from the existing synthetic `audio.mp4` using host FFmpeg: `-c:v libx264 -profile:v baseline -level:v 3.1 -pix_fmt yuv420p -b:v 1500k -maxrate 1800k -bufsize 3600k -c:a copy -movflags +faststart`. Neither FFmpeg nor this asset is added to the runtime APK.

## Remaining scope

The follow-up [presentation PDF/slideshow resource work](PRESENTATION_EXPORT_RESOURCES.md) is complete. The next resource task is to profile image-heavy external PDFs near their byte limit. Preserve text/vectors/encoded resources, explicit failures and selected export quality. ARM/32-bit ABI runtime behavior, real low-memory hardware, battery/FPS, all document providers and pathological PDF object graphs remain unmeasured. A release request must still run R8/shrink, signed smoke, native package/source replacement checks and matching symbols on the final artifact.

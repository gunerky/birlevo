# Image-heavy external PDF resources

16–17 October 2026. First-production plan step 2. Debug development work only; no version increment or signed release is included.

## Reproduced failure

The application accepted PDFs up to 128 MiB, but the isolated page worker loaded each source `InputStream` into a 64 MiB main-memory scratch buffer. Parsed source streams and the destination had further main-memory storage. A real 132,355,914-byte (126.22 MiB), 28-image-page fixture failed with `INVALID` before producing output. Its size comes from distinct lossless image data, not file padding. The unchanged application and matching baseline test were run before the worker edits; ignored evidence is under `output/development/external-pdf/api28-baseline/` and `baseline-tests.log`.

## Implementation and invariants

- Source PDF descriptors remain read-only. A positional reader uses one 64 KiB cache instead of copying the whole file into parser memory. The PDF parser and page copying remain in the same non-exported, isolated service; no source path, network permission or application-directory access is granted.
- The parent supplies one disposable read/write scratch descriptor. `PdfScratchArena` implements PDFBox's stream-buffer contract with reusable 64 KiB disk blocks, one shared cache and document-specific ownership scopes. Source close releases only that source's buffers; cloned destination streams remain valid. Buffer clearing/reuse cannot expose old logical content.
- PDFBox's existing `PDFParser(RandomAccessRead, ScratchFile)` and `COSDocument(ScratchFile)` APIs permit this adapter without modifying or replacing the reviewed library. A fresh trailer/catalog/page tree is built for the destination. The [fork's parser API](https://raw.githubusercontent.com/TomRoush/PdfBox-Android/master/library/src/main/java/com/tom_roush/pdfbox/pdfparser/PDFParser.java) and [scratch-buffer API](https://raw.githubusercontent.com/TomRoush/PdfBox-Android/master/library/src/main/java/com/tom_roush/pdfbox/io/ScratchFile.java) describe these extension points; compilation/tests verify the installed 2.0.27.0 artifact rather than assuming master matches it.
- Encoded content/resources are cloned without decoding/re-encoding images or rasterizing text. Source iteration stays sequential; unused source documents are not parsed. Existing page-box, rotation, version, protected/form/portfolio/attachment rejection and active-content exclusion rules remain.
- The arena has a 320 MiB encoded-stream scratch ceiling, live descriptor-volume checks before writes and checked allocation requests for `readFully`. This is a working-resource budget, not a reduced source/output byte ceiling. Source and destination metadata/object graphs still consume heap and are not described by the 64 KiB cache size. Pathological object graphs can still fail explicitly in the isolated process.
- The client monitors working space while the worker runs. Success requires save/flush/descriptor sync before reply. Timeout, cancellation, worker death or storage failure unbinds the worker and removes only that operation's disposable output/scratch. A nonempty output file is rejected rather than overwritten. Existing sources and previous completed results remain untouched.
- PDF workspace imports now guard and sync their additional private copy. The intermediate reader snapshot is also removed if creating the workspace target itself fails. A storage issue uses the existing 22-language working-space message instead of being conflated with invalid PDF content.

Limits and contracts remain: eight sources, 128 MiB total source/output, 2000 pages, 60-second page-worker deadline, 30-second raster-worker deadline, and PNG/JPEG/WebP at 1280/2048/4096. No automatic quality/resolution/format fallback, new runtime package, project/session schema change or FileProvider authority/root migration. Creating/reordering a PDF is not labeled compression; actual-byte copy consent in compression/handoff paths is unchanged.

## Workload and independent checks

`make_external_pdf_fixtures.py` creates 28 distinct 1600×1024 noisy Flate RGB images with corner markers and standard-font vector text, plus a small second document. Their combined size is **132,373,735 bytes**, approximately 98.6% of the existing 128 MiB ceiling. No personal media is used. Fixtures and large outputs are ignored QA files, not APK assets.

On each accepted API, the host verifier checks:

- 28 reversed pages with alternating rotation, 29 merged pages with an inserted second-document page, and three extracted/rotated pages. All **60 output pages** retain exact compressed-image SHA-256, decoded pixel SHA-256, searchable text and pixel-identical independent PDFium renders against their corresponding source pages.
- Extraction contains only the selected page resources (14,181,906 bytes), while full reorder/merge remain below the output ceiling. Reordered output is 132,362,885 bytes; merged output is 132,380,641 bytes. Small structural growth is expected for a newly composed document; no compression-size benefit is claimed.
- PNG, JPEG and WebP raster outputs remain **4096×2731** with the requested settings and oriented color markers. PNG marker tolerance is six component levels for platform rasterization; lossy outputs allow fifteen. This is separate from exact source/output PDF image-stream and decoded-pixel comparisons.
- The 2000-page vector regression independently checks every page's text, order and 90-degree rotation, with no page image rasterization.

## Measurements and limits

Worker PSS is sampled independently through ADB during the eight-case resource suite. Parent Java/native/PSS samples come from the large reorder profile. Samples are not allocation peaks, GPU accounting or physical-device guarantees; PSS does not include all filesystem page-cache costs. Cross-API PSS accounting and shared-code charging differ and must not be treated as a hardware performance ranking.

| Emulator | Highest sampled page-worker PSS | Highest sampled 4096 raster-worker PSS | Reorder worker wall time |
| --- | ---: | ---: | ---: |
| API 24, 2 GiB configured RAM | 10.69 MiB | 141.53 MiB | 3.96 s |
| API 28, 2 GiB configured RAM | 70.63 MiB | 245.22 MiB | 7.39 s |
| API 36, 2.5 GiB minimum enforced by the Play image | 75.62 MiB | 213.58 MiB | 5.41 s |

The raster worker's higher native cost is reported separately: this patch fixes page-copy stream retention, not PDFium's complete decoded/native memory. Export and preview allocations remain separate. The native renderer may still fail under device memory pressure; a selected output is never silently downgraded. Source/scratch/output copies also require real disk space; the 320 MiB scratch allowance is not a disk reservation or the total size of all user/working files.

## Reproduction and evidence

```powershell
./gradlew.bat assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug
python tools/testing/make_external_pdf_fixtures.py output/development/external-pdf/fixtures
./tools/testing/external-pdf-qa.ps1 -Serial <dedicated-QA-serial> -Api 24
./tools/testing/external-pdf-qa.ps1 -Serial <dedicated-QA-serial> -Api 28
./tools/testing/external-pdf-qa.ps1 -Serial <dedicated-QA-serial> -Api 36
python tools/testing/verify_external_pdf.py output/development/external-pdf/api24-accepted --fixtures output/development/external-pdf/fixtures --vector output/development/external-pdf/api24-accepted/vector-2000.pdf --report output/development/external-pdf/api24-verified.json
```

The fixture/verifier uses development-only NumPy, Pillow, pypdf and pypdfium2. The QA driver verifies the exact dedicated AVD and API before starting tests. It injects worker death from the host only after the instrumentation arms its private debug signal and `dumpsys` identifies the exact isolated PDF process. API 24's debug AVD uses its supported host `su` command; later APIs use ActivityManager's crash command. The API 36 image appends the service class to isolated process names, which the sampler/driver accounts for. Arming occurs before binding to avoid missing fast legacy jobs. There is no production fault-injection hook. Initial attempts using unsupported shell commands, a late signal or the old process-name pattern were invalid test setups; logs are retained separately and are not passing evidence.

Raw evidence is ignored under `output/development/external-pdf/`: baseline APK/failure, per-API `api24-accepted`, `api28-accepted`, `api36-accepted`, independent `api*-verified.json`, process-memory logs, parent CSV, actual PDFs/rasters and build/audit logs. The existing 2000-vector tests are separate from the eight-case image-heavy suite.

Accepted validation:

- 153 JVM tests pass (147 existing plus six stream-adapter tests), with no failures/errors/skips. Coverage includes interleaved read/write, block boundaries, buffer reuse/clearing, source/destination ownership, scratch ceilings, backing IO failure, EOF and pre-allocation memory rejection.
- Debug application/instrumentation builds and lint pass: 0 errors, 60 warnings, 2 hints. Only the dedicated API 24/28/36 emulators are used; none of these results imply physical-device performance.
- Eight image-heavy resource cases pass per API, plus the API 24 2000-vector case and 13 API 36 PDF reader/page/2000-vector regressions: **38 targeted checks**. Source/previous-result hashes, low-space import, mid-worker disk pressure, cancellation, isolated-worker death and retry are checked. The protected/form rejection test now supplies a fresh disposable target for each attempt because failed operations remove it.
- Independent checks validate **180 PDF output pages**, nine 4096 raster outputs and both complete 2000-vector outputs. Compressed image bytes and decoded pixels remain exact; the rotated page renders match their original pages independently.
- The exact 97-artifact runtime inventory, eight native libraries/four ABIs, 16 KiB LOAD/RELRO alignment, dynamic linkage and corresponding encoder source/notices pass. Offline APK tool-isolation passes. No SDK, font or codec was added.
- All 22 locale keys/placeholders/resources and 40 offline developer-tool tests pass (20 Jev/mock, 12 PPTX, five CI-tool and three licensing tests). Repository hygiene, documentation links and the local AST update pass; existing partial Kotlin parser warnings are not treated as compiler validation. No live AI call or source/media upload occurred.

Final debug APK SHA-256: `77602ea3d9081c13b021944395a2f7e16751f3a1cdcbd699f0512cfbbd21e6f6`. Per-run environments identify the artifact used; earlier intermediate/failing runs are separate from acceptance logs. The frozen signed 0.1.11 (12) SHA-256 remains `d1924881005d06aa7f4df643433938d3c183572dcfcb406ea765945720c5f881`.

## Next

Proceed to [first-production plan](../product/FIRST_PRODUCTION_PLAN.md) step 3: long/high-resolution video and legacy preview resource handling. Arbitrary pathological PDFs, all storage providers, ARM/32-bit runtime, physical-device RAM/FPS/battery and release/R8 behavior remain outside this debug evidence. Signed artifact, source/replacement package, alignment and matching-symbol checks will be repeated only on a user-requested release candidate.

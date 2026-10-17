# Long video and legacy frame reliability

17 October 2026. First-production plan step 3. Debug development only; version 0.1.11 / code 12 and the frozen signed packages remain unchanged.

## Demonstrated problems and changes

- API 24–26 previously requested a full-resolution ARGB poster and then resized it to 512 pixels. `VideoPosterReader` now separates this optional preview from video metadata and encoding. On legacy Android it samples the existing decoder's YUV planes directly into one small bitmap. API 27+ keeps its scaled retriever API. Vendor decoders without usable Image planes retain the existing full-frame compatibility fallback, admitted against the full-frame memory cost; it is not claimed to have bounded native/GPU memory.
- Independent pixel checks found an existing legacy frame-extraction defect: repeated `MediaExtractor.seekTo` calls could skip the sync sample. The API 24 diagnostic returned the 83,333 µs delta frame with near-zero planes instead of the 0 µs sync frame, producing green posters/stills. A single seek on a fresh extractor returned the actual marker pixels. Probing sample timestamps and decoding now use separate extractor lifetimes. The selected presentation timestamp, end-of-video handling, B-frame selection, rotation and full still resolution remain separate from thumbnail sampling.
- Frame bitmap allocation now uses the existing live memory admission policy at the actual requested dimensions. A 512-pixel preview can fit when full-resolution still export cannot; export reports memory pressure rather than silently reducing resolution. Cancellation propagates out of optional poster loading. Undelivered images/decoder resources are cleaned up; UI-owned bitmaps are not forcibly recycled.

No media engine, codec, SDK, font or runtime dependency was added. Video import remains limited to the existing 512 MiB / 600,000 ms / 4096-edge and 8,847,360-pixel bounds. All quality, bitrate, frame-rate, format, trim, canvas, speed, layer and 640/1280/1920 output choices remain. Source bytes, per-result larger-copy consent, foreground jobs, durable checkpoints, session schema and FileProvider roots remain unchanged.

## Workload and acceptance

Host-generated original inputs contain three changing color phases, flashing center content, four orientation markers and 440 Hz mono AAC. The long input is 599 seconds at 1280×720 / 12 fps, inside the existing ten-minute ceiling including platform AAC timestamp rounding. Short sources are eight seconds at 1280×720, 1920×1080 and 3840×2160, with 90-degree metadata copies. Large fixtures stay in ignored QA output and are pushed to the dedicated emulators; they are not application/test APK assets.

Each API suite checks supported source/poster dimensions and marker pixels, explicit 4K codec capability, selected high-resolution encoding, full-size PNG frame export, independently admitted previews, cancellation after native writes, live disk pressure, preservation of the source/previous result, retry, a complete long encode with the Activity destroyed and screen off, result reattachment, and external force-stop followed by explicit retry with restored source/quality/trim settings. The external-stop case uses a deterministic hold after the durable RUNNING record, not partial-bitstream resumption; the separate long encode performs real native processing throughout. The API 24 image rejects the supplied 4K/1080p AVC levels; its accepted source is Baseline 3.1 / 720p. API 28 uses 1080p; API 36 exercises 4K input and 1920 output. Capability-dependent rejection is not counted as successful 4K decoding.

The host verifier decodes every frame and the complete audio stream of each long/high-resolution/tail output, checks frame counts, selected dimensions and duration, verifies the 440 Hz signal at the start/middle/end, and compares orientation/content markers against the original. Full stills and all rotated posters receive independent pixel checks. Lossy video marker checks allow 48 component levels; PNG stills allow 20 and posters 25 for legacy color-matrix/rasterization differences. These checks do not claim lossless video or colorimetric equivalence. API 24's existing surface encoder changes some green values; identical before/after short encodes produce zero sampled RGB difference, showing that this poster/frame repair does not introduce that conversion. Temporary seek/plane diagnostics were removed from the test sources after reproducing the defect.

## Measurements and limits

Samples contain Java used bytes, allocated native bytes and PSS. Poster sampling runs every five milliseconds; long-job sampling every 250 ms. These are observed samples with instrumentation/host scheduling overhead, not exhaustive allocation peaks or latency benchmarks. Native decoder buffers, mediaserver, GPU and kernel costs are not bounded by the 512-pixel bitmap size. Only one dedicated QA emulator runs at a time with host GPU; no physical-device RAM/FPS/battery guarantee follows.

| Emulator / configured RAM | Poster Java / native / PSS high samples (MiB) | Long-job Java / native / PSS high samples (MiB) | Long encode + reattachment wall time |
| --- | ---: | ---: | ---: |
| API 24 / 2 GiB | 8.61 / 4.94 / 26.71 | 24.93 / 26.11 / 76.96 | 104.50 s |
| API 28 / 2 GiB | 4.49 / 13.12 / 125.58 | 26.28 / 21.55 / 160.21 | 129.80 s |
| API 36 / 2.5 GiB | 4.64 / 69.10 / 177.42 | 47.75 / 31.24 / 191.22 | 76.62 s |

The API 24 baseline three-import profile sampled 10.86 MiB Java / 5.66 MiB native / 27.79 MiB PSS. Its 0.431-second test interval becomes 0.761 seconds with the final sampled CPU preview path; no speedup is claimed. A 1280×720 ARGB allocation alone is 3.52 MiB, versus 0.56 MiB at 512×288, but neither value describes the complete decoder. Cross-API accounting/source sizes differ and the table is not a device ranking. The API 36 4K poster's native allocation is deliberately reported rather than hidden behind the small bitmap size.

These are low-complexity synthetic inputs, not a 512 MiB noisy/high-motion 4K workload or a thermal test. Existing color behavior, all vendor plane layouts, API 25/26, ARM/32-bit, adversarial files and physical devices remain outside the measured matrix. A codec may still fail explicitly under real native/GPU pressure. The compatibility fallback can still allocate a full ARGB frame when Image planes are unavailable.

## Accepted validation

- Final debug app SHA-256: `fa63ba0ff517d19b5388e3a3dcf4e099e43aa6764294dc4ba80d7a994b1ef8a2`. All accepted API 24/28/36 resource suites identify this artifact. Earlier green-frame/failing setups and intermediate APKs are not passing evidence.
- 153 JVM tests pass with no failures/errors/skips. Debug app/instrumentation builds and lint pass (0 errors, 60 warnings, 2 hints).
- Eight resource cases per API, plus 15 existing API 36 frame/compression/transform tests: **39 targeted workflow checks pass**. They cover rotation, frame timestamps/end selection, multiple output formats, crop/speed/audio, copy consent, source protection and save failure/retry. The extra unchanged/fixed encoder control also passes on both artifacts, with zero sampled RGB difference.
- Independent complete decode verifies **7188 frames** per long output and complete audio on all three APIs, plus 96-frame high-resolution and two 36-frame tail outputs per API. Nine oriented posters and three full-size PNG stills pass the host checks; API 36 retains 3840×2160 pixels in its still and 1920×1080 in its selected video output.
- The existing save-retry test initially omitted copy approval when a platform encoder produced a larger result. It now explicitly approves that exact result when needed before exercising the failed provider and compares saved bytes afterward. The separate unapproved-larger-copy rejection test remains. No production save or consent rule was relaxed.
- Exact commercial coverage passes for 97 runtime artifacts and the native encoder. Eight native libraries/four ABIs retain corresponding source/notices, dynamic linkage, no decoder, and 16 KiB LOAD/RELRO alignment. APK development-tool isolation passes; no new runtime package is introduced.
- All 22 locale resources and 40 offline developer-tool tests pass (20 Jev/mock, 12 PPTX, five CI and three licensing). On Windows the mock/DPAPI tests require the ordinary temporary-file/credential environment; the restricted-sandbox attempt was not a passing run. No live API request or real credential read occurred. Documentation links, indexed repository hygiene and local AST update pass; partial Kotlin parser warnings are not compiler validation.

The frozen signed 0.1.11 (12) APK remains SHA-256 `d1924881005d06aa7f4df643433938d3c183572dcfcb406ea765945720c5f881`. No version increment, signed APK/AAB, store update or release validation is included.

## Reproduction

```powershell
./gradlew.bat assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug
python tools/testing/make_video_resource_fixtures.py output/development/video-resources/fixtures
./tools/testing/video-resources-qa.ps1 -Serial <dedicated-QA-serial> -Api 24
./tools/testing/video-resources-qa.ps1 -Serial <dedicated-QA-serial> -Api 28
./tools/testing/video-resources-qa.ps1 -Serial <dedicated-QA-serial> -Api 36
python tools/testing/verify_video_resources.py output/development/video-resources/api24-accepted --fixtures output/development/video-resources/fixtures --ffmpeg <host-ffmpeg> --api 24 --report output/development/video-resources/api24-verified.json
```

Fixture generation uses development-only imageio-ffmpeg. Verification uses host FFmpeg, NumPy and Pillow. For API 24's optional before/after color check, run `VideoResourceDeviceTest#recordLegacyColorReference` with `-e profilePhase baseline` against the saved unchanged debug APK and `-e profilePhase final` against the current app; the runner/API call contracts used by that method are unchanged. Restore the current debug APK afterward. The QA driver runs the prepare/force-stop/verify tests as separate invocations and rejects missing/failing test results. It operates only on the exact dedicated AVD/API selected by its arguments.

Ignored evidence is under `output/development/video-resources/`: baseline APK/samples, per-API environment and case logs, actual output files, independent JSON verification, build/license/native/tool-isolation logs and intermediate diagnostic logs. No live AI call, source upload or user media is involved. Release/R8/native-symbol gates remain for a separately requested release candidate.

## Next

Follow [first-production plan](../product/FIRST_PRODUCTION_PLAN.md) step 4: storage and cleanup safety across jobs and saved work. Advertising remains steps 5–6, ahead of post-launch format/Office/OCR expansion.

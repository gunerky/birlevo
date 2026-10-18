# Initialized advertising SDK and media workflows

18 October 2026. This follows the [age-aware Home preview](HOME_AD_PRIVACY_FLOW.md). Earlier advertising and media tests used separate application processes. This qualification deliberately initializes the SDK through the actual consent/sample-banner flow before performing media work in the same process. Production activation remains off; no version increment, signed APK/AAB or live advertisement is produced.

## Test design

`ResidentAdvertisingRule` is an instrumentation-only, explicit network opt-in. Normal tests return their original statement without creating the advertising fixture or Compose rule. The resident profile requires the account/Home-preview debug build, EEA region and an adult/protected test choice.

For each selected case, the fixture uses the visible Home age selection and a real loaded sample banner. Adult cases exercise Birlevo's actual European form and decline consent; protected cases use the conservative under-age flow. It then backgrounds/closes the Home Activity and checks removal/ineligibility. The same parent application process initiates the media work with the SDK and WorkManager still initialized; PDF workers retain their existing process isolation. There is no banner in the editing/background workload and no developer restart between SDK initialization and that workload.

The evidence records SDK state false before consent, true before/during/after work, and the same application PID at every sample. Each matrix case starts a fresh instrumentation process for repeatability; this is distinct from restarting the process between the two parts of a case. CSV samples at approximately 250 ms record parent Java/native allocations and PSS. They are samples, not exhaustive peaks or a controlled estimate of overhead caused by advertisements. Other processes, including isolated PDF workers and SDK WebView renderers, are outside those parent values; they do not measure the total application footprint.

The rule wraps existing standalone video, external-PDF and presentation tests, retaining their source hashes, exact settings, cancellation, disk-pressure, cleanup, retry and background-result assertions. A separate cross-engine image/audio test verifies dimensions, pixels, exact lossless PCM, MP3 decoding and result-specific larger-copy approval. No media engine, format, quality option, admission budget, project/session format or FileProvider identifier is changed.

## Matrix per Android API

| Case ID | Workload | Advertising fixture |
| --- | --- | --- |
| `basic` | 3840×2160 input → 2560×1440 PNG; larger BMP approval; WAV/FLAC/MP3 and cancelled encoder | Protected |
| `pdf-merge` | Near-limit 28-image-page PDF plus one page, merge/extract and preserved encoded resources | Adult |
| `pdf-raster` | Import near-limit PDF; selected PNG/JPEG/WebP pages at 4096×2731 | Protected |
| `pdf-cancel` | Partial worker cancellation, synthetic live disk pressure, prior-result/source retention and retry | Protected |
| `video-size` | Explicit 1920 edge target (legacy source stays 1280), full-resolution source still and source preservation | Adult |
| `video-cancel` | Cancel long input, live disk pressure, previous result and tail retry | Protected |
| `video-background` | Complete 599-second input → 640×360 MP4 with audio; screen off, background completion and result reattachment | Adult |
| `presentation-pdf` | 24 image/text slides → PDF with vector text and embedded images | Adult |
| `presentation-video` | Same 24 slides → 12-second, 640×360, 30 fps slideshow | Protected |

This is a selected combination matrix on API 24 and API 36, not every age × format × device combination. Both age treatments are exercised on each API. Device-dependent codecs remain explicit: the legacy FLAC decoder restriction is compared before and after SDK startup; WAV/MP3 remain required. An unsupported FLAC result must fail before creating output, with the same reason as before advertising. It is not silently substituted, counted as a FLAC round-trip, or treated as a skipped test.

## Reproduction

Generate the existing artificial [video fixtures](VIDEO_RESOURCE_RELIABILITY.md) and [PDF fixtures](EXTERNAL_PDF_RESOURCES.md) first. They contain no user media and stay outside the APK.

```powershell
./gradlew.bat '-Pbirlevo.adPrivacyAccount=true' '-Pbirlevo.adPrivacyRegion=eea' '-Pbirlevo.adHomePreview=true' :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug
./tools/testing/resident-ad-media-qa.ps1 -Serial emulator-5580 -Api 36 -Network
./tools/testing/resident-ad-media-qa.ps1 -Serial emulator-5582 -Api 24 -Network
```

The driver verifies the dedicated AVD/API, captures app/instrumentation/fixture hashes, rejects skips and clears only named instrumentation evidence directories before each case. Application work, saved sessions, user sources and host historical evidence are not deleted. Short case/evidence names avoid the Windows path-length failure encountered in the initial driver. Logs for that setup error and the initial unconditional FLAC assertion are retained as diagnostics rather than accepted passes.

```powershell
$qaFfmpeg = python -c "import imageio_ffmpeg; print(imageio_ffmpeg.get_ffmpeg_exe())"
python tools/testing/verify_resident_ad_media.py output/development/resident-ad-media/api36 --api 36 --ffmpeg $qaFfmpeg --pdf-fixtures output/development/external-pdf/fixtures --video-fixtures output/development/video-resources/fixtures --report output/development/resident-ad-media/api36-verified.json
python tools/testing/verify_resident_ad_media.py output/development/resident-ad-media/api24 --api 24 --ffmpeg $qaFfmpeg --pdf-fixtures output/development/external-pdf/fixtures --video-fixtures output/development/video-resources/fixtures --report output/development/resident-ad-media/api24-verified.json
```

Use a Python interpreter providing `pypdfium2`, `pypdf`, NumPy and Pillow; `imageio_ffmpeg` locates the host FFmpeg binary in the example. The accepted desktop runs use the bundled workspace Python for PDF/image checks. Host verification independently checks encoded/decoded PDF image hashes, vector text, exact page renders/rotation, full raster dimensions/colors, complete video/audio decodes, timing, corner markers, source-still pixels, presentation PDF content and every slideshow frame. Reading the Android result's metadata alone is insufficient. Development-only Python/FFmpeg tools are not Android dependencies.

## Verification record

**All 18 initialized-SDK cases pass without skips**, nine per API. Independent verification passes on both APIs: 32 merged/extracted PDF pages retain exact encoded image streams, decoded pixels, vector text, rotation and renders; three 4096×2731 rasters retain their expected colors; complete video/audio streams, timings, source-still pixels, all 24 PDF slides and all 360 slideshow frames are checked. The legacy FLAC result is explicitly `DECODER` both before and after SDK initialization, with no partial output; API 36 produces independently verified exact FLAC PCM. WAV/MP3 and image outputs pass on both.

Two additional ordinary-path image/audio cases pass with the final instrumentation APK and without a network opt-in. Both assert that the SDK is uninitialized before and after the workload. This phase therefore adds **20 passing emulator checks with no skips**. Current **182 JVM tests** pass; lint remains **0 errors / 7 existing warnings / 0 hints** without suppression. Exact commercial coverage remains **132 artifacts plus the native encoder**. The app APK passes four-ABI/eight-library source/notices, dynamic linkage and 16 KiB LOAD/RELRO checks, and the development-tool isolation guard; no API call to Jev occurs. The 22 locale resources remain structurally valid, with no new strings or runtime dependencies in this phase. Graphify's code-only update reports 4,810 nodes / 13,048 edges with 27 existing partial-parser warnings.

| API | Highest sampled parent Java bytes | Native bytes | PSS KiB | Long-video elapsed ms |
| --- | ---: | ---: | ---: | ---: |
| 24 | 98,941,800 | 64,272,936 | 229,683 | 135,677 |
| 36 | 41,930,688 | 114,012,432 | 338,997 | 138,374 |

The memory columns are separate maxima across selected cases, not one simultaneous observation. Video timing includes the tested background operation/reattachment. Concurrent emulators, cold provider setup and instrumentation differ from earlier profiles; these figures are not a causal before/after advertising benchmark or a physical-device promise. Sources, dimensions and selected quality are preserved.

Ignored `output/development/resident-ad-media/` retains named logs, raw samples, independently checked outputs, `verification.json`, per-API verifier reports and exact artifacts. The **40,692,433-byte** application SHA-256 is `6e8b676f0fededcc917d2d99a94ea8ec7abf76b01ec57cefe4cbfcd48b508542`. Accepted resident instrumentation hashes are `fed56d57737824425f4c433a7dc27db953d0f2355c5174091b0f348a53dbd46d` on API 24 and `c098c2dc9731946e3ec6825bac30761552967bd8419dcf19285607113e29cde6` on API 36. Those fixtures differ in the explicit legacy unsupported-codec assertion, which is exercised on API 24; supported-format behavior is unchanged. The final no-opt-in assertions use instrumentation `87db0a5bcea04053617da447bde253ac3aae46c2691ba0a804bc12407fd10e99`.

The earlier 34 Home/age checks and 26 regional account checks retain their own frozen artifact identities. The prior implementation commit `c81d5c6` passed both GitHub development jobs; that does not certify a later head automatically. The signed 0.1.11 (12) remains unchanged at SHA-256 `d1924881005d06aa7f4df643433938d3c183572dcfcb406ea765945720c5f881`. QA connectivity/layout settings are restored and the two dedicated emulators are stopped.

## Next activation steps

The owner confirmed that the Home banner unit currently uses **Google-optimized automatic refresh**; keep that account setting. It is separate from the application's 60-second attempt cooldown and does not promise a fixed 60-second impression interval. These tests request Google's sample unit, so they do not measure the live unit's account-controlled refresh schedule. Google's [banner guide](https://developers.google.com/admob/android/next-gen/banner) describes refresh while visible, and its [refresh settings guide](https://support.google.com/admob/answer/3245199?hl=en-GB) describes the account options. Do not add a second application refresh timer.

The next development step is remaining integrated UI, destination/save-error and cross-tool transfer coverage with the resident SDK. The selected engine/background matrix does not certify every UI/provider path. Then publish the prepared age-aware EN/TR privacy additions before distributing an affected candidate, reconcile exact Play disclosures, and verify actual public store linking/app-ads.txt/account readiness. Production advertising remains disabled until those gates and the requested signed/R8 candidate checks are complete. Emulator results are not physical-device battery/FPS/thermal or every-country compliance guarantees. Whole-process death/restoration remains covered by the earlier media-session tests, not by these same-process samples.

## Follow-up preparation

The subsequent [pre-release readiness report](../releases/PRE_RELEASE_READINESS.md) records integrated UI/provider qualification, a result-bound image destination fix, separate debug/live identifier configuration with production disabled, published age-aware policies and the remaining owner account/signed-package gates. This report above remains the earlier engine matrix, with its original artifact hashes.

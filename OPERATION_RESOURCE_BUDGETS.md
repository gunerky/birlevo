# Operation resource budgets and English developer naming

16 October 2026. This development step extends ZIP workspace admission to image conversion, video processing and PDF raster export. Requested formats, quality settings, final size choices, cancellation and existing checkpoint/URI contracts are preserved. No signed release or version increment is included.

## Resource admission

`WorkingMemory` separates a pure policy from Android measurements. The default adapter reads the current runtime heap ceiling/usage and, when accessible, system available memory and the Android low-memory threshold. Heap headroom retains a policy margin of one eighth of the ceiling, bounded to 4–32 MiB. The admission allowance is the smaller of that heap headroom and system headroom above the threshold. If system measurements are unavailable, the heap policy remains active; invalid measurements are rejected.

This is a conservative admission estimate, not a reservation or a proof that native/GPU/codec allocation will succeed. Bitmap memory on modern Android and vendor codec buffers are not fully described by Java heap usage. Existing exception handling remains necessary. There is no automatic change to quality, target dimensions, bitrate or frame rate.

- **Images:** inspect dimensions without full decoding, estimate simultaneous raster buffers before allocation, and check temporary disk space before encoding. On supported platforms, target-size `ImageDecoder` decoding avoids an unnecessarily large intermediate bitmap. The legacy sampling plan retains enough pixels for the requested size. The old standalone image path could sample a 4000×4000 source down to 2000×2000 even when 2560 was selected; conversion now produces the selected 2560×2560 output. Private conversion bitmaps are released normally; UI-owned previews are not forcibly recycled by the policy.
- **Video:** admission estimates three source-sized and five target-sized raster buffers plus 16 MiB of pipeline overhead. Temporary output space is estimated from requested bitrate, audio and actual edited duration, with 25% plus 4 MiB of working margin. Neither estimate is an exact codec or VBR guarantee. While the path-writing native encoder runs, an IO child coroutine checks remaining disk headroom every 250 ms. Pressure cancels the existing Transformer operation and uses its normal cleanup path. Codec fallback remains disabled; the requested target is not silently reduced. Optional posters have a separate raster check, including the larger legacy frame-retrieval path.
- **PDF:** the isolated raster worker checks its own memory before allocating a page bitmap. Storage measurements use the granted output descriptor via `fstatvfs`, not access to the application's private directory. Encoded output remains byte-bounded and is guarded while writing. The reader checks memory before decoding UI previews and guards input/output copies. Photo-PDF raster allocations reuse image admission; its final write is also space-guarded.

The shared storage policy remains defined in [the ZIP/capacity report](DEVICE_CAPACITY_AND_ZIP_LIMITS.md). A provider's remote/removable storage quota is not inferred from app-private storage. Measurements can race with other applications; IO errors, cancellation and cleanup remain authoritative.

API references: [MemoryInfo](https://developer.android.com/reference/android/app/ActivityManager.MemoryInfo), [Runtime](https://developer.android.com/reference/java/lang/Runtime), [ImageDecoder](https://developer.android.com/reference/android/graphics/ImageDecoder), [BitmapFactory sampling](https://developer.android.com/reference/android/graphics/BitmapFactory.Options#inSampleSize). The `isMimeTypeSupported` probe is guarded at API 29; the existing API 28 HEIF path no longer invokes that newer probe unconditionally.

## English naming scope

- The README is now English; 75 root developer-document filenames and their repository references were migrated. [The mapping index](DOCUMENTATION_INDEX.md) preserves discoverability of earlier paths. Historical report prose remains in its original language, with an English archive notice.
- The application code already used English class/function/property names. No cosmetic package or persistence renames were invented. Native language names, Turkish UI translations and multilingual test data are retained.
- The application ID, namespace, FileProvider authority/roots, private workspace paths and existing serialized keys are unchanged. Naming rules now explicitly require English for new developer-facing names/comments and keep chronological notes out of the README.
- This does not change repository visibility, application licensing or third-party source/license notices.

## Validation

```powershell
.\gradlew.bat assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug
powershell -NoProfile -ExecutionPolicy Bypass -File tools/testing/media-resource-qa.ps1 -SkipBuild
python tools/localization/generate.py --check
python tools/licensing/audit_runtime.py --check
graphify update . --no-cluster
```

- **147 JVM tests passed.** New coverage includes changing heap/system pressure, malformed measurements, overflow saturation and sampling that retains the requested pixels.
- Debug application/test APKs and lint passed: **0 errors, 67 existing warnings, 2 hints**. Initial lint caught an API-29 probe behind an API-28 condition; the guard was corrected before acceptance.
- **34 emulator tests passed:** resource admission/output 4; existing image conversion/UI/EXIF 6; image formats 3; PDF reader/UI 6; photo-PDF/source ownership 5; production video outputs/cancellation/UI 10. Only the dedicated `ofice_qa` API-36 emulator was used. An initial synthetic video filename did not exist and was corrected; that failed run is not acceptance evidence.
- Independent host checks fully decoded four **2560×2560** image outputs (JPEG/PNG/WebP/BMP), three **4096×2048** PDF page outputs (PNG/JPEG/WebP) and the retried **640×360 H.264/AAC** video. Image color-region checks passed; FFmpeg decoded the complete video.
- The 22-locale key/placeholder check passed without changing localized application content. Exact license coverage remains **95 runtime artifacts plus the native encoder**. Four-ABI/eight-library source/notice/alignment checks and APK development-tool isolation passed. No SDK/codec/font was added.

Evidence is ignored under `output/development/media-budgets/`: `build-final.log`, per-class device logs, `qa.log`, exported files and `independent-check.json`. Failed attempts are kept separate from accepted results.

## Remaining limits

Physical-device RAM/FPS/battery and Android 24/28 runtime behavior are not measured here. Legacy SlideImages sampling, multipage PdfDocument retention, PDFBox object graphs and vendor-native/GPU allocation peaks still need targeted profiling; the new checks are not a complete accounting model for them. Low-space/memory admission failures use synthetic measurements rather than filling the disk or destabilizing the host. Full release/R8 checks remain tied to a future explicit release request.

Next: extend the emulator matrix to legacy API paths and large multipage PDF workloads, then refine measured native-memory/temporary-space costs without reducing output capabilities.

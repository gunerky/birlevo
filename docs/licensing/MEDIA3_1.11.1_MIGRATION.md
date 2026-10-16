# Media3 1.11.1 migration and runtime review

16 October 2026. Existing PR #5 carries the joint Media3 update and includes the reviewed ExifInterface change from #4. Merge #4 before #5. PR #6 originally changed the same five Media3 declarations and is superseded by #5. No signed release or version bump is part of this work.

## Preserved contracts

- Forward the optional encoder log-session argument without constructing platform log-session objects. Keep requested sample rate/channels/bitrate checks and disabled encoder fallback.
- Use integer muxer track IDs and Media3's separate sample-info/exception types. Validate tracks, size and nonnegative timestamps before writing. Map flags using Media3's public platform conversion and use the ByteBuffer's actual position as the native sample offset. OGG/WebM remain platform muxers; no codec is bundled by this change.
- Use common overlay settings with the static builder, preserving opacity, position, scale and time ranges. Keep export-private texture ownership and release behavior.
- Use the constant-gain channel matrix equivalent and explicit sequence track types. Joined clips with sound retain audio/silence intervals; entirely silent input without an added sound remains video-only. An added sound requires a full-length silent base so the encoded audio tail does not end before the video. The added sound sequence is audio-only; slideshow sequences are video-only. This replaces the deprecated composition-wide force-audio setting.
- Flush the directly used Sonic resampler with default stream metadata. This avoids a runtime exception in mixed-rate audio editing while retaining the selected output rate/channels and sample-range policy.

These details are based on the [official Media3 release notes](https://developer.android.com/jetpack/androidx/releases/media3) and the exact [1.11.1 source tag](https://github.com/androidx/media/tree/1.11.1). Compilation alone did not catch the sequence/resampler contract changes; real output tests exercise both.

## Commercial runtime evidence

The resolved runtime changes from 95 to **97** artifacts: **93 Apache-2.0, three Bouncy Castle and one MIT**. Changed/new resolved artifacts were inspected through official Google Maven/Maven Central POMs, parent declarations and actual archive notices. The exact coordinate/filename/hash set and evidence paths are retained in [the manifest](../../tools/licensing/runtime-licenses.json). Readable application notices are regenerated without removing existing terms.

Checker Framework's `checker-qual:3.43.0` includes a complete 1,126-byte MIT copyright/permission/disclaimer in `META-INF/LICENSE.txt`; that text is now retained in the application. The license recognizer accepts its reviewed declaration explicitly; unknown and lookalike names remain rejected in new policy tests. Exact runtime/hash approval remains mandatory and is not granted by recognizing a license name. No advertising SDK, font or native codec is added. LAME's separate LGPL/source/replacement obligations are unchanged.

Dependabot groups all `androidx.media3:*` proposals into one future version-update PR. This prevents duplicate independent proposals for interdependent Media3 modules; it does not automatically approve updates or repair existing PRs.

## Validation and reproduction

```powershell
./gradlew.bat assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug
./gradlew.bat exportRuntimeLicenseInventory
python tools/licensing/audit_runtime.py --check
python -m unittest discover -s tools/licensing -p 'test_*.py'
./tools/testing/dependency-update-qa.ps1 -Api 36 -SkipBuild
./tools/testing/dependency-update-qa.ps1 -Api 24 -SkipBuild
```

The dedicated-emulator runner records the application hash, environment and per-class instrumentation output under ignored `output/development/media3-1.11.1/`. `-StartAt <class>` resumes affected classes after a repair; passing earlier independent classes is retained as separate evidence. No physical-device measurements are inferred.

The new muxer cases compare real encoded packet bytes at nonzero buffer offsets, timestamps/keyframes, invalid track/size/time rejection, requested AAC bitrate/rate/channels, and joined speed-adjusted AVC with sound. Android reconstructs Ogg granule times, so its timing is compared with a direct platform muxer fed identical packets rather than assuming arbitrary extractor timestamps survive remuxing unchanged. Existing suites cover output formats, audio sample selection/resampling, cancellation, copy consent, joined/silent clips, text/image layers, mixing, session restore, sharing and slideshow output. A new decoded-audio duration assertion protects the silent-video/added-sound tail: container/video duration alone did not catch that shorter audio stream during migration.

Independent host checks now cover VP9 as well as the earlier three targets for both clip orders, image overlays and sound mixing. The VP9 full-decode check preserves the demuxer time base and inspects every decoded frame's strictly increasing PTS; otherwise FFmpeg's null sink can round distinct WebM timestamps to identical output DTS. Decoder errors remain fatal. This changes the host validation procedure, not application output timestamps.

Validation passed: debug application/test builds, 147 JVM tests, lint with zero errors / 62 warnings / two hints, three license-recognition policy tests and the existing 20 offline Jev / 12 PPTX / five CI tooling tests. All 22 localization catalogs and local documentation links pass. Exact 97-artifact coverage, native source/notices, four ABIs / 16 KiB alignment and source/APK development-tool isolation pass. Final debug APK SHA-256: `36ec4925da9fd476aaaa5220f688789ecf6f501b57b5b7379db761694efa8350`.

Device coverage is **55 cases on API 36 plus three on API 24**. The final silent-tail repair reran its three video-audio cases plus the four muxer/encoder cases successfully. API 24 uses an AVC Baseline synthetic fixture and 0.5× speed, matching that emulator's advertised decoder; High-profile fixtures are rejected by the existing capability check. This does not remove a production codec/profile or claim older devices support every format. The earlier ExifInterface step separately passed 12 image/PDF-target cases.

Independent host checks passed six audio targets (including four exact FLAC PCM digests), ten audio edits, eight ordered video joins, eight text overlays, eight image overlays, ten sound mixes/replacements, seven VP9 profile/timestamp outputs, three full timed-photo slideshow decodes, and seven migration outputs across API 24/36. The graph was refreshed locally to 4,207 nodes / 11,344 edges; partial Kotlin extraction warnings remain and do not replace compiler/tests.

Release/R8/signing, physical ARM/16 KiB runtime, provider-wide behavior and battery/FPS are outside this development update. The existing signed 0.1.11 packages remain frozen.

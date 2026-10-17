# Storage and cleanup safety

17 October 2026. First-production plan step 4. Debug development only; the frozen signed 0.1.11 / code 12 packages are unchanged.

## Problems and behavior

An aged shared snapshot could be deleted by `MediaOutputs.stage` before it was copied into a new snapshot. The dedicated API 36 baseline run reproduces `FileNotFoundException`/`ENOENT` for `first.jpg`; the same case passes with the fix. Cleanup now retains the input file and its containing snapshot. Its existing FileProvider URI remains readable during this operation; the new snapshot contains identical encoded bytes. Unrelated expired snapshots can still be removed.

Several age sweepers previously accepted every file in their private folder. `OwnedFileCleanup` now checks explicit generated filename patterns, a positive measured modification time, the age threshold, canonical child ownership and recovered references. It never recurses or follows a file/directory symlink. Flat scratch/snapshot directories are removed only if every child is a known, old, direct regular file; an unknown/recent child prevents partial cleanup. Failed file deletion does not trigger recursive fallback. Unknown files and timestamps remain untouched.

Age alone does not establish that an output is obsolete. `ProcessingJobs.cleanupRetention` returns no cleanup permission until recovery finishes, while an operation is queued/running, after index recovery fails, or when an output reference cannot be resolved. A completed output is retained even if the current session settings have changed. This is deliberately different from `retainedOutput`, which requires a matching request before presenting a result as current.

Video, audio, photo PDF, slideshow, PPTX and batch startup cleanup use this gate. Video/audio/photo sessions still validate and restore their source assets before sweeping. The PDF reader and text/table reader skip their sweep when an Android checkpoint cannot restore. Opening a settings panel, changing a recipe, or an unsuccessful save does not authorize deleting an original or a saved export.

The integrated restart setup also reproduced an idle-service shutdown race: a new job could become interrupted while the previous completed PDF stayed intact. The service now rechecks the coordinator's current active records before idle shutdown and revokes its readiness before requesting that shutdown. Per-instance ownership tokens prevent an old service's destruction callback from cancelling work queued for or attached to the next service. Unexpected destruction and timeout still interrupt the current owner's work. A deterministic regression exercises stale callbacks both before and after the next attachment, in addition to the actual foreground/cancellation/restart cases. Android's [service lifecycle reference](https://developer.android.com/reference/android/app/Service) documents the separate start, stop and destruction callbacks; the ownership protocol is application code.

## Ownership audit

| Area | Ownership and recovery boundary |
| --- | --- |
| Image/video/audio import and encoding | Private generated files; operation failure/cancellation deletes its undelivered files. Startup sweep accepts only known patterns. Persisted video/audio sources and result records are retained. Video overlay/audio assets remain part of the session reference set. |
| Photo PDF / presentation PDF | Per-page/partial generated files have operation-owned `finally` cleanup. Photo inputs survive failed checkpoint writes and are retired by the atomic session writer only after a successful commit. Known aged output/partial files can be swept after successful recovery. |
| Slideshow | Session assets and completed output survive restart. Flat `render-UUID` directories require old numeric PNG children and no unknown entries; new/running work is not swept. |
| PPTX | UUID output, `.part` and `.sha256` names are recognized. The retained output and matching digest sidecar are kept. Request snapshots/project images are not swept. |
| PDF reader / text and tables | Retain restored input; cleanup only known temporary names after successful checkpoint recovery. Failed restore skips cleanup. |
| PDF page editor | Sources may be referenced by saved Android task state that this instance cannot enumerate. Keep the existing no-global-sweep policy; explicit reset/removal and operation-owned failure cleanup remain. |
| Batch image/video/audio/document | Separate queue folders and mutex; validated atomic queue metadata identifies asset references. Root output pruning is skipped when processing recovery cannot prove safety. Completed per-item outputs are not receipts owned by `ProcessingJobs`, so cancelling a job does not erase them. |
| ZIP reader / extraction | Existing mutex and validated atomic metadata gate private archive pruning. Only generated archive/partial names are eligible. Destination-folder cleanup is restricted to the newly created extraction root; original ZIP and preexisting destination contents stay outside it. |
| Share/open snapshots | Temporary seven-day cache with independent encoded copies. Source snapshot is retained while restaging. Existing provider authority, `shared` and `project-images` root identifiers are unchanged. Other expired snapshots are still temporary, not permanent links. |
| Project images | No global asset garbage collection. Saved drafts, duplicated slides, presentation snapshots and other projects can share an image. Only a newly copied image that never attached to a draft can be discarded automatically. Project deletion does not imply asset exclusivity. |

## Deliberate limits

This is conservative cleanup, not a complete cross-project/task reference collector. Unrecognized files, unenumerated PDF-editor task sources and uncertain references can consume storage until a later explicit recovery/cleanup design proves them unused. In particular, third-party scratch filenames are not inferred from their extension alone. No external user folder, original URI or saved export is passed to the age sweeper.

The retention gate is a startup/recovery contract in the app's single-process job coordinator. It is not an interprocess filesystem lock or a guarantee against a hostile concurrent mutation of private files. The isolated PDF worker keeps its existing descriptor/job-owned cleanup. A process killed during encoding does not resume a partial bitstream automatically.

No codec, SDK, permission, persisted schema, FileProvider root, output resolution or quality choice changes. Actual-byte larger-copy approval still applies to save/share/handoff. No advertising SDK or live Jev request is included. Emulator checks do not establish ARM hardware, battery, provider-quota or physical-device guarantees. Signed R8/native release validation waits for the user's release-build request.

## Validation

Evidence is kept under ignored `output/development/storage-cleanup/`; commands run from the repository root.

- Final debug APK SHA-256: `a180dd4d0328bf9b79960e6342e2d0c2898fe7c413c8191f8d0c137797ce28c6`. Accepted API 24/36 environment files identify this artifact; earlier intermediate builds do not certify it.
- 157 JVM tests pass without failures/errors/skips, including four ownership/grace-period/directory tests. Final service-ownership debug app/test builds and lint pass: 0 errors, 60 warnings, 2 hints (`service-verified-build.log`). Earlier intermediate artifacts are superseded.
- **49 targeted emulator checks pass:** six cleanup/service-ownership cases on API 24 and 43 checks on API 36. The API 36 set includes source/session retirement, failed save/retry, share grants/larger-copy approval, photo PDF/PDF reader flows, slideshow/PPTX cancellation and restoration, shared project images, text checkpoints, batch ZIP cancellation, foreground timeout/queue handling and a completed-PDF/interrupted-video external restart. PID changes from 9204 to 9289; the original completed two-page PDF is restored and video retry produces a fresh output. These are scoped checks, not whole-product/release or physical-device qualification.
- The six new device cases cover aged share restaging/URI reads, known orphan versus retained assets, file/directory/root symlinks and cycles, corrupted processing recovery, active-job deferral, completed-result retention across a different request/reloaded coordinator, and stale service ownership during rapid job transitions.
- Exact commercial coverage remains 97 runtime artifacts plus the native encoder. Eight libraries/four ABIs pass corresponding source/notices, dynamic linkage, no decoder and 16 KiB LOAD/RELRO checks. Development-tool APK isolation and 22 locale resource checks pass; no live API is used.
- Local AST update succeeds with 26 partial Kotlin parser warnings; source/compiler/device checks remain the authority for those paths. Documentation links and indexed repository hygiene are checked before commit.
- Frozen signed APK SHA-256 remains `d1924881005d06aa7f4df643433938d3c183572dcfcb406ea765945720c5f881`. No version increment, signed APK/AAB, store/site publication or release validation occurs.

```powershell
./gradlew.bat assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug exportRuntimeLicenseInventory
./tools/testing/storage-cleanup-qa.ps1 -Serial <api36-qa-serial> -Api 36 -Workflows
./tools/testing/storage-cleanup-qa.ps1 -Serial <api24-qa-serial> -Api 24
python tools/licensing/audit_runtime.py --check
python tools/repository/check_documentation.py
graphify update . --no-cluster
```

The process-restart regression initially timed out after it changed video settings and immediately called creation before the asynchronous encoder-support check was ready. It had already restored the interrupted job and the completed PDF. The test now awaits the same support-ready condition used by the UI and asserts that the selected format is supported before explicitly retrying. Production support admission is unchanged. The failed run is kept separately from accepted evidence. An initial overlapping local Gradle invocation also collided in lint intermediate files; only the subsequent successful build and final lint report count as accepted validation.

The later idle-service failure is preserved in `service-race-before.log` and fixed in runtime code, rather than accepted as a passing retry. An API 24 ADB-daemon launch failure ran no application test; its diagnostic is also separate. Accepted hashes/counts and the frozen release check are in `qa-summary.json`. The final host/native/tool checks and all 49 device results use the service-ownership artifact above.

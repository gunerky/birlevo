# Commercial distribution and third-party license review

Last verified: 16 October 2026. Birlevo is a commercial Android application with planned advertising revenue. This inventory covers the actual resolved application runtime and the separate MP3 encoder. Free-of-charge availability or an open-source label alone is not a distribution approval.

## Application license and scope

The application does not currently have a general open-source license. This document records third-party obligations; it does not grant permission to redistribute Birlevo's application code, branding or assets. The separate LGPL encoder and bridge retain their own licenses and corresponding-source access.

The current `releaseRuntimeClasspath` resolves **97 artifacts / 97 Maven modules**: 93 declare Apache-2.0, three declare the Bouncy Castle license and one declares MIT. This is conservative coverage before R8, not a claim that every class appears in the final release. JUnit, Espresso, test providers and debug-only tools are outside this application-runtime inventory. New fonts, SDKs, codecs and other packages require their own review.

Exact coordinates, filenames, SHA-256 hashes, official POM/parent-POM evidence and embedded notices are recorded in [the machine-readable inventory](tools/licensing/runtime-licenses.json). The 16 October check exported this branch's dependency graph without building a release and passed exact offline coverage. Current reviewed versions include Core Splashscreen **1.2.0**, AppCompat **1.8.0**, ExifInterface **1.4.2**, Media3 **1.11.1**, PDFBox-Android **2.0.27.0**, Bouncy Castle **1.72** and the separate LAME **4.0** encoder. The [ExifInterface review](EXIFINTERFACE_1.4.2_REVIEW.md) and [Media3 migration](MEDIA3_1.11.1_MIGRATION.md) record the changed artifact evidence. This approval covers those exact resolved artifacts, not future dependency updates.

## Reviewed components and retained obligations

| Component | Reviewed terms | Implementation in Birlevo |
| --- | --- | --- |
| AndroidX, Compose, Media3, Kotlin, Coroutines, Guava, JSpecify and annotations | Apache-2.0; retain the applicable license, copyright and attribution notices when distributing | Full license text, artifact list and embedded license texts from 25 artifacts are retained in readable application notices |
| PDFBox-Android 2.0.27.0 | Apache-2.0 plus bundled Adobe AFM/CMap, Unicode and historical PDFBox permission notices | Complete PDFBox LICENSE/NOTICE and additional component texts are retained; the top-level POM alone is not used to discard them |
| Bouncy Castle bcprov/bcpkix/bcutil 1.72 | Retain its copyright, permission and disclaimer text, including for commercial distribution | The previously missing 2000–2022 notice was extracted from the actual bcprov `org/bouncycastle/LICENSE.class` and added to the application |
| Checker Framework checker-qual 3.43.0 | MIT; retain the copyright, permission and warranty disclaimer text | Its complete actual `META-INF/LICENSE.txt` is retained in the readable notices and exact manifest; this does not change the application's license |
| LAME 4.0 encoder | LGPL-2.0-or-later, with separate source, linking and notice obligations | LGPL 2.1 is elected under the upstream later-version option; `libmp3lame.so` and `libbirlevo_mp3.so` remain separate dynamic libraries. Complete corresponding source and the full LGPL are exportable offline |
| LAME JNI/Java bridge | Preserve its source license and rebuildable interface | The bridge is LGPL-2.0-or-later in the source bundle; its Java class and native methods retain their ABI through R8 rules |
| LAME mpglib, executable frontend and host FFmpeg | Not additional application runtime components | The GPL decoder and executable frontend are excluded from the compiled source list. Host FFmpeg is used only for synthetic QA input and independent output verification |

Unused components in the complete LAME upstream archive retain their original licenses. Providing that archive does not mean those components are compiled into the app: CMake explicitly lists encoder sources. Native verification checks decoder-symbol exclusion and separate dynamic linkage.

No prohibition on advertising revenue or commercial distribution was identified in the reviewed code licenses, subject to preserving their conditions. That conclusion does not extend to new dependencies or country-specific codec patent rights. H.264, HEVC and AAC paths use Android platform codecs; this code-license inventory is not patent-pool or regional-use clearance.

## Offline corresponding source and library replacement

In the application, open **Supported files → Open-source licenses → Save MP3 encoder source**. The document picker exports `application/zip` as `birlevo-mp3-sources.zip`. Internet access, an account, a personal key or access to the private GitHub repository is not required.

The bundle contains the unmodified complete LAME 4.0 archive, original COPYING, full elected LGPL 2.1 text, Android CMake configuration, config, C JNI bridge, Java interface and rebuild/replacement instructions. No runtime library hash or signature check is added to prevent replacement. The notice permits modification of the combined work for the customer's own use and reverse engineering to debug those modifications. Android requires a modified APK to be signed again with the customer's key; official updates and private application data do not automatically transfer to a differently signed installation.

The [LAME source and replacement guide](third_party/lame/README.md) describes the interface and build procedure. Tests rebuilt the exported encoder and bridge for x86_64 without the application/private repository and verified that the bridge has `libmp3lame.so` in `DT_NEEDED`. Release validation additionally installs a separately signed QA copy with the replacement encoder and exercises the actual shrunk application. This must be repeated for each requested release; a debug check alone does not complete it.

## Reproducible verification

```powershell
./gradlew.bat exportRuntimeLicenseInventory
python tools/licensing/audit_runtime.py --check
python tools/testing/verify_mp3_artifact.py <path-to-apk>
```

Inventory export resolves dependencies without building a release. `--check` compares the complete coordinate/filename/hash set with the reviewed manifest and checks the native upstream archive hash. A new package or changed version must be reviewed before the inventory is regenerated. CI checks exact coverage and native source/notices; it does not automatically approve a changed dependency.

During an explicit initial/revised review, missing POMs may be downloaded only with `--fetch`, from official Google Maven or Maven Central. Parent-POM inheritance is followed. An absent or unrecognized license is not automatically accepted, and a POM declaration alone does not settle all bundled terms or patent questions. Upstream license texts and notices remain verbatim.

### Dependency update review history

The initially inspected failures on 16 October 2026 had two different causes:

| Proposed update | Observed CI failure | Required work before merging |
| --- | --- | --- |
| [PR #4: ExifInterface 1.4.1 → 1.4.2](https://github.com/gunerky/birlevo/pull/4) | Initially rejected by exact commercial runtime coverage after debug checks | Resolved on this branch: official POM and actual packaged Apache-2.0 text reviewed; inventory and readable notices updated. Exact coverage passes without weakening the gate |
| [PR #5: Media3 family 1.5.1 → 1.11.1](https://github.com/gunerky/birlevo/pull/5) and duplicate [PR #6](https://github.com/gunerky/birlevo/pull/6) | Initially failed Kotlin compilation against changed encoder, muxer and overlay APIs | The joint migration in #5 updates those contracts plus explicit sequence track types, constant-gain channel matrices and resampler flush metadata. The 97-artifact runtime is reviewed, including MIT attribution. See the migration report for validation; future Media3 proposals are grouped |
| [PR #14: Core Splashscreen 1.0.1 → 1.2.0](https://github.com/gunerky/birlevo/pull/14) | Exact runtime coverage rejected the previously unreviewed AAR | Official POM and actual packaged Apache-2.0 text reviewed; 97-artifact inventory and readable notices refreshed. The [review](SPLASHSCREEN_1.2.0_REVIEW.md) records API 24/36 day/night, locale and recreation checks |
| [PR #16: AppCompat 1.7.1 → 1.8.0](https://github.com/gunerky/birlevo/pull/16) | Exact runtime coverage rejected the two previously unreviewed AppCompat AARs | Both official POMs and actual packaged Apache-2.0 texts reviewed; cumulative 97-artifact inventory retains Splashscreen 1.2.0. See the [AppCompat review](APPCOMPAT_1.8.0_REVIEW.md) |

Those initial failures belonged to update branches and did not revoke the earlier versions' license coverage. Dependency approval requires the actual review, updated inventory and checks; rerunning the old failing workflow alone cannot provide it. The [official Media3 release notes](https://developer.android.com/jetpack/androidx/releases/media3) and [ExifInterface release notes](https://developer.android.com/jetpack/androidx/releases/exifinterface) are migration inputs; actual compiler, output and license checks remain required.

## Advertising integration

No advertising/network SDK is added by this review. Before integrating an advertising SDK, review its commercial service terms and data behavior. Update the Play advertising declaration, advertising-ID declaration, data-safety form and website/application privacy statements together with that integration. Planned advertising is not declared as an already implemented feature.

## Release evidence and limits

The [0.1.10 release record](RELEASE_0.1.10.md) documents exact runtime coverage, all eight encoder/bridge ELF libraries across four ABIs, 16 KiB LOAD/RELRO/ZIP alignment, dynamic linkage, absence of GPL decoder symbols, complete sources/notices and offline source export from the R8 interface. It also records an x86_64 rebuild from the exported package, separately signed replacement-library QA, actual R8 audio workflows, independent decoding and restoration of the official APK. The QA replacement copy is not a distribution artifact.

The current frozen [0.1.11 release record](RELEASE_0.1.11.md) also records 95-artifact/native coverage, exact source verification, four-ABI alignment and a successful offline-source rebuild/replacement exercise in the shrunk application. Development work after that frozen package does not produce a new signed release or inherit release validation automatically. The 16 October documentation check reverified the current development APK's native layout, current source matching and notices. The frozen 0.1.11 APK remains unchanged. Historical exact-source checks must use the source snapshot corresponding to that package, including its original CMake configuration.

Physical ARM/16 KiB runtime behavior and country-specific codec rights cannot be inferred from these emulator/artifact checks. Future releases must repeat the applicable release, source-replacement and packaging checks.

## Primary evidence

- [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).
- [LAME licensing explanation](https://lame.sourceforge.io/license.txt) and the exact bundled upstream COPYING.
- [Full GNU LGPL 2.1](https://www.gnu.org/licenses/old-licenses/lgpl-2.1.txt).
- [Bouncy Castle 1.72 upstream](https://github.com/bcgit/bc-java/tree/r1rv72) and the [notice extracted from the reviewed artifact](tools/licensing/notices/BouncyCastle-1.72.txt).
- [Exact runtime coordinates, hashes and per-artifact official evidence](tools/licensing/runtime-licenses.json).

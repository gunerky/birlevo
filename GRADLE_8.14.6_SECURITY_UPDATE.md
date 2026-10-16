# Gradle 8.14.6 security update and deferred 9.x migration

Reviewed on 16 October 2026 for [PR #15](https://github.com/gunerky/birlevo/pull/15). The PR originally proposed Gradle 9.8.1. Its final scope uses the compatible 8.14.6 security backport, retaining AGP 8.12.0, Kotlin/Compose plugin 2.3.0, JDK 17, compile/target SDK 36 and min SDK 24.

## Why the original proposal failed

GitHub run `38889043075` failed while applying `com.android.internal.application`, before application compilation. AGP 8.12.0 requests Gradle's internal `InternalProblems` service; Gradle 9.6+ removed that service. The runner explicitly suggested upgrading the plugin or using an earlier Gradle version. This is a plugin/toolchain compatibility failure, not a Kotlin source or runtime-license failure. Re-running the unchanged 9.8.1 branch cannot fix it.

A future 9.x upgrade must coordinate the supported AGP, Kotlin integration and Gradle versions, then validate the Android DSL, native/JNI packaging, lint, R8/resource shrinking, license export and development-tool isolation. It must preserve signing, output quality, saved schemas and FileProvider contracts. No Gradle-internal shim or disabled validation gate is introduced here.

## Compatible security fix

The [official 8.14.6 release notes](https://docs.gradle.org/8.14.6/release-notes.html) and all three upstream advisory records identify **8.14.6 and 9.8.1** as patched versions:

- [GHSA-mvvg-497x-hmj8](https://github.com/gradle/gradle/security/advisories/GHSA-mvvg-497x-hmj8): worker-to-daemon deserialization before authentication.
- [GHSA-xwqc-3h47-hg64](https://github.com/gradle/gradle/security/advisories/GHSA-xwqc-3h47-hg64): client-to-daemon deserialization before authentication.
- [GHSA-j5m7-59rp-24f5](https://github.com/gradle/gradle/security/advisories/GHSA-j5m7-59rp-24f5): repository fallback after SSL connection failure.

Thus retaining 8.14.5 would leave these reported build-tool issues unresolved. The existing PR is adapted to 8.14.6 instead of merging an incompatible major upgrade or ignoring future dependency/security proposals.

Both checksums were fetched from official Gradle distribution endpoints and verified:

| File | SHA-256 |
| --- | --- |
| `gradle-8.14.6-bin.zip` | `7988ed071b2a07900e2ec715fca15c6ab72bce6db433af301fa7fa7e9407bd24` |
| Generated `gradle-wrapper.jar` for 8.14.6 | `ad16bca46cb71bb5887c546860d93992aeee35c1fdaf4a242b0a612375d937e0` |

The distribution checksum is pinned in `gradle-wrapper.properties`; official wrapper scripts/JAR are regenerated using 8.14.6. The new wrapper was invoked again successfully after generation. On Windows the initial invocation replaced its running 9.8.1 batch script, resulting in a missing old batch label after the Gradle task had succeeded; the subsequent new-script invocation passed normally.

## Integration and verification

This branch includes reviewed Splashscreen #14 and AppCompat #16. Merge order is **#14 → #16 → #15**. The cumulative runtime inventory and notices must remain intact. The Gradle patch adds no Android runtime package or application behavior change; it is build tooling, separate from the application's runtime license inventory.

The new 8.14.6 wrapper completes debug app/test builds, all 147 JVM tests, lint and runtime inventory export successfully. Lint has no errors, 59 warnings and two hints. Exact 97-artifact/native license coverage passes without a manifest change. The new debug APK SHA-256 is `05845ed04242d6eea7d0e9bd0becbb98a85c62652f1210f083503557f7b571a8`. Native checks pass for eight libraries across four ABIs, 16 KiB LOAD/RELRO alignment, separate linkage, decoder exclusion and exact source/notices; the development-tool isolation guard also passes. Raw evidence is ignored under `output/development/mp3-expansion/gradle-8.14.6-*`. The newly built APK also passes all four API 36 instrumentation cases: real light/dark system-bar and dialog behavior, all 22 locale/RTL configurations across Activity recreation, and saved-project recreation/relaunch. Earlier runtime-dependency reviews additionally passed these cases on API 24. The new-wrapper device logs and screenshots are under `output/development/gradle-8.14.6/api36`. Repository hygiene and the local AST graph were refreshed; no remote source submission was used. No application version increase, signed APK/AAB or new release package is produced by this task.

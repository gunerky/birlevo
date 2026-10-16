# ExifInterface 1.4.2 update review

16 October 2026. Updates existing PR #4 on top of current main. No version bump, signed release, new capability restriction or application-license change.

## Artifact and license evidence

The official Google Maven POM declares Apache-2.0. The actual resolved AAR includes `META-INF/androidx/exifinterface/exifinterface/LICENSE.txt` (10,175 bytes, SHA-256 `809fa1ed21450f59827d1e9aec720bbc4b687434fa22283c6cb5dd82a47ab9c0`). Its Apache text matches the retained license terms. The entire resolved runtime remains 95 artifacts: 92 Apache-2.0 and three Bouncy Castle components. Only the ExifInterface coordinate, artifact filename/hash and version-specific evidence path change in the reviewed manifest; the readable artifact list is updated too. No attribution is removed.

Sources: [official release notes](https://developer.android.com/jetpack/androidx/releases/exifinterface#1.4.2), [official POM](https://dl.google.com/dl/android/maven2/androidx/exifinterface/exifinterface/1.4.2/exifinterface-1.4.2.pom), [exact artifact inventory](tools/licensing/runtime-licenses.json). The prior CI failure was an intentionally strict stale-inventory rejection, not evidence of a newly incompatible license.

## Validation

Debug application/test builds, 147 JVM tests and lint passed. Exact runtime coverage and the debug APK's native source/notices, encoder-only dynamic linkage, four ABIs and 16 KiB alignment passed. API 36 emulator validation covers six image-compression cases (including all eight EXIF orientations and metadata removal), three format/alpha/owned-transfer cases and three image/PDF target cases. A stale UI assertion expected Create after a result was already ready; it now checks Save, matching the existing result-stage interface. The initial failed assertion remains in ignored QA evidence and is not counted as a product regression.

Raw local logs are retained in `output/development/exif-1.4.2/`; no user media or credentials are involved. Codec/patent rights, physical-device performance and a new release are outside this dependency patch.

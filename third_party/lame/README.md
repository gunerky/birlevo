# LAME 4.0 encoder used by Birlevo

Unmodified complete upstream archive, downloaded from the official project's file area:
https://downloads.sourceforge.net/project/lame/lame/4.0/lame-4.0.tar.gz
SHA-256: `3df5124d5ad3a98312ffd7ba6a9b36230e4f8a3e66d3ce0f425e336c32d216eb`

The encoder is LGPL-2.0-or-later; COPYING is included verbatim.
Birlevo elects LGPL 2.1 for distribution under the upstream 'or later' option;
LICENSE-2.1.txt is the full applicable license (including section 6(b)).
Only libmp3lame's encoder source list is compiled. No GPL mpglib decoder, executable frontend,
assembly, networking or external audio engine is included in the app.
The complete upstream source archive contains additional components that retain
their own original licenses. Those components are not compiled into Birlevo.
The separate C JNI bridge and Java interface are also LGPL-2.0-or-later.
Birlevo's other code is not part of this source archive.

The app contains `assets/licenses/birlevo-mp3-sources.zip`: the upstream archive,
this license/readme, exact CMake configuration, JNI bridge and Java interface.
It can be saved from the app's open-source licenses dialog. No account/network
access or private repository access is required to obtain corresponding source.

Build or replace the library
----------------------------
Unzip this source bundle at a clean directory. Install Android NDK 27.1.12297006
and CMake 3.22.1 (or compatible later tools). Put CMake and Ninja on PATH; Android
SDK CMake provides both in its bin directory. With NDK and these tools available,
run, replacing `<NDK>` and `<ABI>` with their absolute path and chosen ABI:

```
cmake -S app/src/main/cpp -B work/<ABI> -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE=<NDK>/build/cmake/android.toolchain.cmake \
  -DANDROID_ABI=<ABI> -DANDROID_PLATFORM=android-24 \
  -DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON -DCMAKE_BUILD_TYPE=Release
cmake --build work/<ABI>
```

Supported packaged ABIs: armeabi-v7a, arm64-v8a, x86, x86_64. Output includes
libmp3lame.so and libbirlevo_mp3.so; these are dynamically linked. There is no
encoder checksum/signature restriction at runtime. To replace the encoder,
keep its public LAME ABI and library name, replace `lib/<ABI>/libmp3lame.so` in
a copy of the APK, remove old META-INF signature entries, zipalign with 16 KiB
alignment, and sign the changed APK using your own key with Android build-tools.
Android requires a signature; your own key is sufficient. An already installed
official app has a different signer: install the changed app in a separate
profile/device or uninstall only your own copy first (export your work first).
The official app's private projects/updates cannot transfer across signers.
Modification of this combined work for the customer's own use and reverse
engineering for debugging these modifications are permitted under LGPL section 6.
No Birlevo signing key or full proprietary source is needed for library replacement.

Changing this unmodified pinned archive requires adjusting the SHA-256 in CMake.
Publish modified library sources under their license and preserve notices.
The app records exact source/build files offline, rather than depending on an
upstream download remaining available. See COPYING for the full terms.

"""Offline QA: rebuild the APK's LGPL source and replace one encoder using a QA key.

Writes only below output/development; never changes a release artifact or installs an app.
The caller must restrict installation/data resets to the dedicated QA emulator.
"""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import re
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[2]
MARKER = "4.0-birlevo-qa-replacement"


def digest(data):
    return hashlib.sha256(data).hexdigest()


def run(args, log, env=None):
    with log.open("a", encoding="utf-8") as stream:
        subprocess.run([str(arg) for arg in args], check=True, stdout=stream,
                       stderr=subprocess.STDOUT, env=env)


def prepare(apk, work, sdk, key):
    apk, work = apk.resolve(), work.resolve()
    if not work.is_relative_to((ROOT / "output/development").resolve()):
        raise ValueError("QA files must stay below output/development")
    if work.exists() and any(work.iterdir()):
        raise ValueError("Use a new empty QA directory; existing evidence is preserved")
    work.mkdir(parents=True, exist_ok=True)
    source = work / "source"
    with zipfile.ZipFile(apk) as original:
        source_zip = original.read("assets/licenses/birlevo-mp3-sources.zip")
    with zipfile.ZipFile(io.BytesIO(source_zip)) as bundle:
        for entry in bundle.infolist():
            target = (source / entry.filename).resolve()
            if not target.is_relative_to(source.resolve()):
                raise ValueError("Source ZIP path escapes its destination")
            if not entry.is_dir():
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(bundle.read(entry))
    cmake = sdk / "cmake/3.22.1/bin/cmake.exe"
    build = work / "build"
    log = work / "rebuild.log"
    env = dict(os.environ)
    env["PATH"] = str(cmake.parent) + os.pathsep + env.get("PATH", "")
    run([cmake, "-S", source / "app/src/main/cpp", "-B", build, "-G", "Ninja",
         "-DCMAKE_TOOLCHAIN_FILE=" + str(sdk / "ndk/27.1.12297006/build/cmake/android.toolchain.cmake"),
         "-DANDROID_ABI=x86_64", "-DANDROID_PLATFORM=android-24",
         "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON", "-DCMAKE_BUILD_TYPE=Release"], log, env)
    version_file = build / "upstream/lame-4.0/libmp3lame/version.c"
    before = version_file.read_text(encoding="utf-8")
    match = re.search(r"get_lame_version\(void\).*?\n}", before, re.S)
    if match is None or match.group().count("return str;") != 1:
        raise ValueError("Unexpected source version function")
    changed = match.group().replace("return str;", 'return "' + MARKER + '";')
    version_file.write_text(before[:match.start()] + changed + before[match.end():], encoding="utf-8")
    run([cmake, "--build", build], log, env)
    library = (build / "libmp3lame.so").read_bytes()
    # Check real ELF alignment, RELRO and dependencies with the existing artifact parser.
    import sys
    sys.path.insert(0, str(ROOT / "tools/testing"))
    from verify_mp3_artifact import elf
    alignment = elf(library)
    member = "lib/x86_64/libmp3lame.so"
    unsigned = work / "replacement-unsigned.apk"
    removed = []
    with zipfile.ZipFile(apk) as original, zipfile.ZipFile(unsigned, "w") as patched:
        for entry in original.infolist():
            if re.fullmatch(r"META-INF/(?:MANIFEST\.MF|[^/]+\.(?:SF|RSA|DSA|EC))", entry.filename, re.I):
                removed.append(entry.filename)
                continue
            data = library if entry.filename == member else original.read(entry)
            patched.writestr(entry, data)
    with zipfile.ZipFile(apk) as original, zipfile.ZipFile(unsigned) as patched:
        changes = [name for name in patched.namelist() if patched.read(name) != original.read(name)]
        if changes != [member]:
            raise ValueError("Replacement must change only the encoder entry")
        original_library_hash = digest(original.read(member))
    tools = sdk / "build-tools/36.0.0"
    aligned = work / "replacement-aligned.apk"
    signed = work / "replacement-qa-only.apk"
    run([tools / "zipalign.exe", "-P", "16", "-f", "4", unsigned, aligned], log)
    # Standard local Android debug key, deliberately unrelated to Birlevo's upload key.
    env["BIRLEVO_REPLACEMENT_QA_PASSWORD"] = "android"
    run([tools / "apksigner.bat", "sign", "--ks", key, "--ks-key-alias", "androiddebugkey",
         "--ks-pass", "env:BIRLEVO_REPLACEMENT_QA_PASSWORD", "--key-pass", "env:BIRLEVO_REPLACEMENT_QA_PASSWORD",
         "--out", signed, aligned], log, env)
    run([tools / "apksigner.bat", "verify", "--verbose", "--print-certs", signed], log)
    run([tools / "zipalign.exe", "-c", "-P", "16", "4", signed], log)
    report = dict(prepared=True, installed_or_runtime_verified=False, qa_only=True,
                  source_apk_sha256=digest(apk.read_bytes()), source_zip_sha256=digest(source_zip),
                  original_encoder_sha256=original_library_hash, replacement_encoder_sha256=digest(library),
                  replacement_apk_sha256=digest(signed.read_bytes()), changed_entries=changes,
                  removed_jar_signature_entries=removed, expected_lame_version=MARKER,
                  native_alignment=alignment, signer="local Android debug key, not Birlevo upload key")
    (work / "preparation.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--work", type=Path, required=True)
    parser.add_argument("--sdk", type=Path, default=Path(os.environ["LOCALAPPDATA"]) / "Android/Sdk")
    parser.add_argument("--qa-key", type=Path, default=Path.home() / ".android/debug.keystore")
    args = parser.parse_args()
    prepare(args.apk, args.work, args.sdk, args.qa_key)

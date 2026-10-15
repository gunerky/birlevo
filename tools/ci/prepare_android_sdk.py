"""Verify pinned native tools before Gradle; retry official SDK installation."""
import os
from pathlib import Path
import re
import subprocess
import sys
import time


def required_packages(build_file):
    source = Path(build_file).read_text(encoding="utf-8")
    ndk = re.search(r'ndkVersion\s*=\s*"([\d.]+)"', source)
    cmake = re.search(r'cmake\s*\{[^}]*version\s*=\s*"([\d.]+)"', source, re.S)
    if not ndk or not cmake:
        raise RuntimeError("Cannot read pinned NDK/CMake versions from Gradle")
    return [("ndk", ndk[1]), ("cmake", cmake[1])]


def installed(root, kind, version):
    directory = root / kind / version
    properties = directory / "source.properties"
    if not properties.is_file():
        return False
    if not re.search(r"^Pkg.Revision\s*=\s*" + re.escape(version) + r"\s*$",
                     properties.read_text(encoding="utf-8"), re.M):
        return False
    windows = sys.platform == "win32"
    if kind == "ndk":
        host = "windows-x86_64" if windows else "linux-x86_64"
        binary = directory / "toolchains/llvm/prebuilt" / host / "bin" / ("clang.exe" if windows else "clang")
    else:
        binary = directory / "bin" / ("cmake.exe" if windows else "cmake")
    return binary.is_file() and (windows or os.access(binary, os.X_OK))


def prepare(root, packages, run=subprocess.run, sleep=time.sleep):
    manager = root / "cmdline-tools/latest/bin" / ("sdkmanager.bat" if sys.platform == "win32" else "sdkmanager")
    for attempt in range(3):
        missing = [(kind, version) for kind, version in packages if not installed(root, kind, version)]
        if not missing:
            print("Pinned NDK and CMake verified", flush=True)
            return
        if not manager.is_file():
            raise RuntimeError("Android SDK command-line tools are missing")
        print(f"Native SDK preparation attempt {attempt + 1}/3", flush=True)
        try:
            result = run([str(manager), f"--sdk_root={root}", "--install",
                          *[f"{kind};{version}" for kind, version in missing]],
                         check=False, timeout=300)
            print(f"sdkmanager exit code: {result.returncode}", flush=True)
        except subprocess.TimeoutExpired:
            print("sdkmanager timed out", flush=True)
        if all(installed(root, kind, version) for kind, version in packages):
            print("Pinned NDK and CMake verified", flush=True)
            return
        if attempt < 2:
            sleep(5 * (attempt + 1))
    raise RuntimeError("Pinned native SDK tools remain missing or incomplete after 3 attempts")


def main():
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not sdk:
        raise RuntimeError("ANDROID_HOME or ANDROID_SDK_ROOT must be set")
    build = Path(__file__).resolve().parents[2] / "app/build.gradle.kts"
    prepare(Path(sdk), required_packages(build))


if __name__ == "__main__":
    try:
        main()
    except (OSError, RuntimeError) as error:
        sys.exit(str(error))

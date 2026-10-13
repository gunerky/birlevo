"""Host-only independent decoding of synthetic files from the platform release harness.

Requires imageio_ffmpeg and NumPy on the development host; neither is an app dependency.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import imageio_ffmpeg
import numpy as np


def verify(folder, audio_only=False):
    ffmpeg = imageio_ffmpeg.get_ffmpeg_exe()
    results = []
    def sound(name, seconds, tones):
        path = folder / name
        decoded = subprocess.run([ffmpeg, "-hide_banner", "-i", str(path), "-vn",
                                  "-c:a", "pcm_s16le", "-f", "s16le", "-"], check=True, capture_output=True)
        metadata = decoded.stderr.decode("utf-8", errors="replace")
        match = re.search(r"Audio: [^\n]*?, (\d+) Hz, (mono|stereo)", metadata)
        assert match, metadata
        rate, channels = int(match.group(1)), 1 if match.group(2) == "mono" else 2
        assert (rate, channels) == (48000, 2), (name, rate, channels)
        pcm = np.frombuffer(decoded.stdout, dtype="<i2").reshape(-1, channels)[:, 0]
        duration = len(pcm) / rate
        assert abs(duration - seconds) < .12, (name, duration)
        if path.suffix == ".flac":
            data = path.read_bytes()
            assert data[:5] == b"fLaC\x80" and int.from_bytes(data[5:8], "big") == 34
            bits = int.from_bytes(data[18:26], "big")
            assert (bits >> 44, ((bits >> 41) & 7) + 1, ((bits >> 36) & 31) + 1) == (rate, channels, 16)
            assert (bits & ((1 << 36) - 1)) * channels * 2 == len(decoded.stdout)
            assert hashlib.md5(decoded.stdout).digest() == data[26:42], "FLAC PCM digest"
        measured = []
        for start, end, expected in tones:
            chunk = pcm[round(start * rate):round(end * rate)]
            spectrum = np.abs(np.fft.rfft(chunk.astype(float) * np.hanning(len(chunk))))
            frequency = np.argmax(spectrum) * rate / len(chunk)
            assert abs(frequency - expected) < 4, (name, frequency, expected)
            measured.append(float(frequency))
        results.append(dict(file=name, bytes=path.stat().st_size, rate=rate, channels=channels,
                            decoded_seconds=duration, measured_hz=measured))
    sound("native-abi.mp3", 1, [(.1, .9, 440)])
    for extension in ["mp3", "ogg", "flac"]:
        sound("release-joined." + extension, 3.6, [(.1, 2.8, 440), (3.1, 3.5, 660)])
    if not audio_only:
        path = folder / "release-joined-video.mp4"
        sound(path.name, 3, [(.1, 1.3, 660), (1.7, 2.8, 440)])
        frames = []
        for second, kind in [(.2, "blue"), (1., "yellow"), (1.7, "red"), (2.5, "green")]:
            raw = subprocess.run([ffmpeg, "-v", "error", "-ss", str(second), "-i", str(path),
                                  "-frames:v", "1", "-vf", "scale=1:1", "-f", "rawvideo",
                                  "-pix_fmt", "rgb24", "-"], check=True, capture_output=True).stdout
            assert len(raw) == 3
            r, g, b = raw
            assert {"blue": b > 180 and r < 70 and g < 70,
                    "yellow": r > 180 and g > 180 and b < 70,
                    "red": r > 180 and g < 70 and b < 70,
                    "green": g > 180 and r < 70 and b < 70}[kind], (second, kind, list(raw))
            frames.append(dict(seconds=second, color=kind, rgb=list(raw)))
        results[-1]["frames"] = frames
    return dict(passed=True, host_only=True, synthetic_files=len(results), files=results)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("folder", type=Path)
    parser.add_argument("--audio-only", action="store_true")
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()
    report = verify(args.folder, args.audio_only)
    args.report.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(f"{report['synthetic_files']} release outputs independently decoded; duration, profile, tone order/pitch verified.")

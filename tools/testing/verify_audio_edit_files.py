"""Independently decode synthetic QA edits; host FFmpeg is never bundled in the app."""
import argparse
import array
import json
from pathlib import Path
import subprocess
import sys
import imageio_ffmpeg


def check(folder):
    ffmpeg = imageio_ffmpeg.get_ffmpeg_exe()
    results = []
    for rate, channels in [(48000, 2), (44100, 1)]:
        for extension in ['wav', 'm4a', 'ogg', 'flac', 'mp3']:
            path = folder / f'{rate}-{channels}.{extension}'
            actual_rate = 48000 if extension == 'ogg' else rate
            raw = subprocess.run([ffmpeg, '-hide_banner', '-loglevel', 'error', '-i', str(path),
                                  '-f', 's16le', '-acodec', 'pcm_s16le', '-'], check=True, capture_output=True).stdout
            pcm = array.array('h'); pcm.frombytes(raw)
            if sys.byteorder != 'little':
                pcm.byteswap()
            duration = len(pcm) / channels / actual_rate
            assert abs(duration - 2.25) < 0.10, (path, duration)
            def frequency(start, end):
                samples = pcm[round(start * actual_rate) * channels:round(end * actual_rate) * channels:channels]
                crossings = sum(a <= 0 < b for a, b in zip(samples, samples[1:]))
                return crossings / (len(samples) / actual_rate)
            first = frequency(.1, .9); second = frequency(1.1, 2.1)
            assert abs(first - 660) < 4, (path, first)
            assert abs(second - 440) < 4, (path, second)
            results.append(dict(file=path.name, bytes=path.stat().st_size, decoded_seconds=duration,
                                first_hz=first, second_hz=second))
    return dict(passed=True, synthetic_outputs=len(results), results=results)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('folder', type=Path)
    parser.add_argument('--report', type=Path)
    args = parser.parse_args()
    report = check(args.folder)
    if args.report:
        args.report.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(report, indent=2))

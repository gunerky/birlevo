"""Independently decode complete video outputs, audio, full stills and oriented posters."""
import argparse
import csv
import json
from pathlib import Path
import subprocess
import numpy as np
from PIL import Image


def command(ffmpeg, path):
    return [ffmpeg, '-hide_banner', '-loglevel', 'error', '-threads', '2', '-i', str(path)]


def frame(ffmpeg, path, at):
    data = subprocess.check_output([ffmpeg, '-hide_banner', '-loglevel', 'error', '-ss', str(at),
                                   '-i', str(path), '-frames:v', '1', '-vf', 'scale=160:90', '-pix_fmt', 'rgb24', '-f', 'rawvideo', '-'])
    return np.frombuffer(data, np.uint8).reshape(90, 160, 3)


def decoded(ffmpeg, path, seconds, fps, size):
    # Stream every frame through an independent decoder; retain only a small reduced frame.
    args = command(ffmpeg, path) + ['-map', '0:v:0', '-an', '-vf', 'scale=160:90', '-pix_fmt', 'rgb24', '-f', 'rawvideo', '-']
    count = 0
    with subprocess.Popen(args, stdout=subprocess.PIPE) as process:
        while True:
            data = process.stdout.read(160 * 90 * 3)
            if not data:
                break
            assert len(data) == 160 * 90 * 3, 'Truncated independent frame'
            count += 1
        assert process.wait() == 0
    assert abs(count - round(seconds * fps)) <= 2, (path, count, seconds * fps)
    # Source dimensions come from the stream header, not the reduced QA decode.
    probe = subprocess.run(command(ffmpeg, path)[:-len(['-i', str(path)])] + ['-loglevel', 'info', '-i', str(path),
                           '-frames:v', '1', '-f', 'null', '-'], capture_output=True, text=True, check=True)
    assert f'{size[0]}x{size[1]}' in probe.stderr, probe.stderr
    audio_bytes = 0
    args = command(ffmpeg, path) + ['-map', '0:a:0', '-vn', '-ac', '1', '-ar', '48000', '-f', 's16le', '-']
    with subprocess.Popen(args, stdout=subprocess.PIPE) as process:
        while True:
            data = process.stdout.read(65536)
            if not data:
                break
            audio_bytes += len(data)
        assert process.wait() == 0
    audio_seconds = audio_bytes / 2 / 48000
    assert abs(audio_seconds - seconds) < .15, (path, audio_seconds)
    for at in [.2, seconds / 2, seconds - .5]:
        audio = subprocess.check_output([ffmpeg, '-hide_banner', '-loglevel', 'error', '-ss', str(at), '-i', str(path),
                                         '-t', '0.25', '-vn', '-ac', '1', '-ar', '48000', '-f', 's16le', '-'])
        samples = np.frombuffer(audio, '<i2').astype(float)
        peak = np.argmax(np.abs(np.fft.rfft(samples * np.hanning(len(samples))))) * 48000 / len(samples)
        assert abs(peak - 440) <= 5, (path, at, peak)
    return dict(frames=count, audio_seconds=audio_seconds, dimensions=size)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('evidence', type=Path)
    parser.add_argument('--fixtures', required=True, type=Path)
    parser.add_argument('--ffmpeg', required=True)
    parser.add_argument('--api', required=True, type=int, choices=[24, 28, 36])
    parser.add_argument('--report', required=True, type=Path)
    args = parser.parse_args()
    args.report.unlink(missing_ok=True)
    folder = args.evidence / 'video-resources'
    short = 'legacy.mp4' if args.api == 24 else 'medium.mp4' if args.api == 28 else 'high.mp4'
    dimensions = (1280, 720) if args.api == 24 else (1920, 1080)
    report = {}
    for name, seconds, size in [('high-output.mp4', 8, dimensions), ('long-output.mp4', 599, (640, 360)),
                                 ('retry-tail.mp4', 3, (640, 360)), ('restored-tail.mp4', 3, (640, 360))]:
        report[name] = decoded(args.ffmpeg, folder / name, seconds, 12, size)
        times = [.2, seconds / 2, seconds - .5]
        offset = 595 if 'tail' in name else 0
        source = args.fixtures / ('long.mp4' if name != 'high-output.mp4' else short)
        for at in times:
            expected = frame(args.ffmpeg, source, at + offset).astype(int)
            actual = frame(args.ffmpeg, folder / name, at).astype(int)
            # Stable center-free samples and orientation markers, allowing lossy video encoding.
            for y, x in [(5, 5), (5, 150), (83, 5), (83, 150), (25, 45)]:
                # Lossy output/legacy surface color conversion: verify marker identity, not pixel equality.
                assert np.max(np.abs(expected[y, x] - actual[y, x])) <= 48, (name, at, y, x, expected[y, x], actual[y, x])
    with Image.open(folder / 'full-source-frame.png') as image:
        expected = (1280, 720) if args.api == 24 else (1920, 1080) if args.api == 28 else (3840, 2160)
        assert image.size == expected, (image.size, expected)
        still = np.array(image.convert('RGB').resize((160, 90))).astype(int)
    reference = frame(args.ffmpeg, args.fixtures / short, 4.1).astype(int)
    for y, x in [(5, 5), (5, 150), (83, 5), (83, 150), (25, 45)]:
        assert np.max(np.abs(still[y, x] - reference[y, x])) <= 20
    for poster in folder.glob('*-poster-final.png'):
        with Image.open(poster) as image:
            assert max(image.size) == 512
            if 'rotated' in poster.name:
                assert image.height > image.width
            else:
                assert image.width > image.height
            pixels = np.array(image.convert('RGB').resize((160, 90))).astype(int)
        source_name = poster.name.removesuffix('-poster-final.png') + '.mp4'
        expected = frame(args.ffmpeg, args.fixtures / source_name, 0).astype(int)
        for y, x in [(5, 5), (5, 150), (83, 5), (83, 150)]:
            assert np.max(np.abs(pixels[y, x] - expected[y, x])) <= 25, (poster.name, y, x, pixels[y, x], expected[y, x])
    rows = list(csv.DictReader((folder / 'poster-final.csv').open()))
    report['poster_samples'] = {key: max(int(row[key]) for row in rows) for key in ['javaBytes', 'nativeBytes', 'pssKiB']}
    rows = list(csv.DictReader((folder / 'long-memory.csv').open()))
    report['long_samples'] = {key: max(int(row[key]) for row in rows) for key in ['javaBytes', 'nativeBytes', 'pssKiB']}
    report['long_time'] = (folder / 'long-time.txt').read_text()
    assert (folder / 'prepared-pid.txt').read_text() != (folder / 'verified-pid.txt').read_text()
    if args.api == 24 and any((folder / f'color-{phase}.mp4').exists() for phase in ['baseline', 'final']):
        assert all((folder / f'color-{phase}.mp4').is_file() for phase in ['baseline', 'final'])
        difference = 0
        for at in [.2, 4, 7.5]:
            before = frame(args.ffmpeg, folder / 'color-baseline.mp4', at).astype(int)
            after = frame(args.ffmpeg, folder / 'color-final.mp4', at).astype(int)
            difference = max(difference, int(np.max(np.abs(before - after))))
        assert difference <= 3, difference
        report['legacy_baseline_vs_final_color_max_difference'] = difference
    args.report.write_text(json.dumps(report, indent=2), encoding='utf-8')
    print('Complete video/audio decodes, timing, content, dimensions, still pixels, posters and restart evidence passed.')


if __name__ == '__main__':
    main()

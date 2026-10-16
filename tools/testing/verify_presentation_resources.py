"""Host-only checks for generated presentation PDF/video and retained-memory evidence."""

import argparse
import csv
import itertools
import json
from pathlib import Path
import re
import subprocess

import numpy as np
from pypdf import PdfReader
import pypdfium2 as pdfium


def metrics(file):
    with file.open(encoding='utf-8') as stream:
        rows = list(csv.DictReader(stream))
    return {**{key: max(int(row[key]) for row in rows)
               for key in ('nativeBytes', 'javaBytes', 'pssKiB', 'elapsedMs')},
            'nativeGrowthBytes': max(int(row['nativeBytes']) for row in rows) - int(rows[0]['nativeBytes'])}


def verify_pdf(file, count, baseline=None):
    reader = PdfReader(file)
    assert len(reader.pages) == count
    original = PdfReader(baseline) if baseline else None
    rendered = pdfium.PdfDocument(file) if baseline else None
    old_rendered = pdfium.PdfDocument(baseline) if baseline else None
    try:
        for index, page in enumerate(reader.pages):
            assert tuple(map(int, page.mediabox[2:])) == (960, 540)
            text = page.extract_text()
            normalized = ''.join(text.split())
            assert f'Slide{index+1}' in normalized and f'Vectorbody{index+1}' in normalized
            assert page['/Resources'].get('/Font'), 'Text must not become a page raster'
            image, = list(page.images)
            assert image.image.size == (1024, 683)
            assert image.image.convert('RGB').getpixel((512, 341)) == (20 + index, 80, 180)
            if original:
                old = original.pages[index]
                # An appended image form may add a final extractor newline, not slide content.
                assert old.extract_text().rstrip('\r\n') == text.rstrip('\r\n')
                old_image, = list(old.images)
                assert old_image.image.tobytes() == image.image.tobytes()
                first, second = rendered[index], old_rendered[index]
                current_bitmap = old_bitmap = None
                try:
                    current_bitmap, old_bitmap = first.render(scale=4/3), second.render(scale=4/3)
                    assert np.array_equal(np.asarray(current_bitmap.to_pil()), np.asarray(old_bitmap.to_pil())), index
                finally:
                    if current_bitmap: current_bitmap.close()
                    if old_bitmap: old_bitmap.close()
                    first.close(); second.close()
    finally:
        if rendered: rendered.close()
        if old_rendered: old_rendered.close()
    return {'pages': count, 'bytes': file.stat().st_size,
            'baseline_bytes': baseline.stat().st_size if baseline else None,
            'searchable_text': True, 'embedded_image_size': [1024, 683],
            'exact_baseline_render_and_image_comparison': bool(baseline)}


def frames(ffmpeg, file, frame_size):
    process = subprocess.Popen([str(ffmpeg), '-v', 'error', '-i', str(file), '-f', 'rawvideo',
                                '-pix_fmt', 'rgb24', '-'], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    try:
        while True:
            data = process.stdout.read(frame_size)
            if not data: break
            assert len(data) == frame_size, 'Incomplete decoded frame'
            yield data
        error = process.stderr.read()
        assert process.wait() == 0, error.decode(errors='replace')
    finally:
        if process.poll() is None: process.terminate()
        process.wait()
        process.stdout.close(); process.stderr.close()


def verify_video(ffmpeg, file, count, baseline=None):
    header = subprocess.run([str(ffmpeg), '-hide_banner', '-i', str(file)], capture_output=True).stderr.decode(errors='replace')
    assert 'Video: h264' in header and 'Audio:' not in header
    assert re.search(r'\b640x360\b', header) and re.search(r'\b30 fps\b', header)
    duration = re.search(r'Duration: (\d+):(\d+):(\d+\.\d+)', header)
    assert duration
    seconds = int(duration[1])*3600 + int(duration[2])*60 + float(duration[3])
    assert abs(seconds - count * .5) <= .1
    decoded = frames(ffmpeg, file, 640*360*3)
    previous = frames(ffmpeg, baseline, 640*360*3) if baseline else None
    total = 0
    try:
        for index, (current, old) in enumerate(itertools.zip_longest(decoded, previous or itertools.repeat(None))):
            if current is None:
                assert old is None
                break
            if previous: assert current == old, f'Decoded video changed at frame {index}'
            pixels = np.frombuffer(current, np.uint8).reshape(360, 640, 3).astype(np.int16)
            expected = np.array([20 + index // 15, 80, 180])
            assert np.all(np.abs(pixels - expected) <= 20, axis=2).sum() > 640*360//30, index
            total += 1
    finally:
        decoded.close()
        if previous: previous.close()
    assert total == count * 15, (total, count)
    return {'frames': total, 'size': [640, 360], 'fps': 30, 'seconds': seconds,
            'exact_baseline_frame_comparison': bool(baseline)}


def verify_photos(folder):
    reader = PdfReader(folder/'photo-final-24.pdf')
    assert len(reader.pages) == 24
    for index, page in enumerate(reader.pages):
        image, = list(page.images)
        assert image.image.size == (1536, 1024)
        assert image.image.convert('RGB').getpixel((768, 512)) == (20 + index, 80, 180)
    reader = PdfReader(folder/'photo-4096.pdf')
    assert len(reader.pages) == 2
    image, = list(reader.pages[0].images)
    assert image.image.size == (4096, 2048)
    assert image.image.convert('RGB').getpixel((100, 100)) == (255, 0, 0)
    assert image.image.convert('RGB').getpixel((3996, 100)) == (0, 0, 255)
    return {'ordered_photo_pages': 24, 'large_image_size': [4096, 2048]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('folder', type=Path)
    parser.add_argument('--pages', type=int, default=24)
    parser.add_argument('--baseline', type=Path)
    parser.add_argument('--ffmpeg', type=Path, required=True)
    parser.add_argument('--photos', type=Path)
    parser.add_argument('--report', type=Path, required=True)
    args = parser.parse_args()
    assert 1 <= args.pages <= 100
    count = args.pages
    result = {
        'pdf': verify_pdf(args.folder/f'presentation-final-{count}.pdf', count,
                          args.baseline/f'presentation-baseline-{count}.pdf' if args.baseline else None),
        'video': verify_video(args.ffmpeg, args.folder/f'slideshow-final-{count}.mp4', count,
                              args.baseline/f'slideshow-baseline-{count}.mp4' if args.baseline else None),
        'metrics': {name: metrics(args.folder/f'{name}-final-{count}.csv') for name in ('presentation', 'slideshow')},
    }
    if args.baseline:
        result['baseline_metrics'] = {name: metrics(args.baseline/f'{name}-baseline-{count}.csv')
                                      for name in ('presentation', 'slideshow')}
    if args.photos: result['photo_regression'] = verify_photos(args.photos)
    args.report.write_text(json.dumps(result, indent=2)+'\n', encoding='utf-8')
    print(f'{count} PDF pages and {result["video"]["frames"]} video frames independently verified.')


if __name__ == '__main__': main()

"""Independently decode all frames of the three app-generated timed-photo MP4s.

Host-only ffmpeg/numpy are development tools, never Android dependencies.
"""
import argparse
import json
from pathlib import Path
import subprocess

import imageio_ffmpeg
import numpy as np


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('folder', type=Path)
    parser.add_argument('--report', type=Path, required=True)
    args = parser.parse_args()
    results = []
    shapes = {'LANDSCAPE_16_9': (640, 360), 'PORTRAIT_9_16': (360, 640), 'SQUARE': (640, 640)}
    for name, shape in shapes.items():
        path = args.folder / (name + '.mp4')
        frames = imageio_ffmpeg.read_frames(str(path), pix_fmt='rgb24')
        metadata = next(frames)
        assert metadata['size'] == shape, (path, metadata)
        assert abs(metadata['fps'] - 30) < .01, (path, metadata)
        assert abs(metadata['duration'] - 2.5) < .05, (path, metadata)
        colors = []
        for data in frames:
            pixels = np.frombuffer(data, dtype=np.uint8).reshape(shape[1], shape[0], 3)
            colors.append(pixels[shape[1] // 2, shape[0] // 2].astype(int))
        assert len(colors) == 75, (path, len(colors))
        for index, actual in enumerate(colors):
            expected = (255, 0, 0) if index < 30 else (0, 0, 255)
            assert np.max(np.abs(actual - expected)) < 25, (path, index, actual)
        probe = subprocess.run([imageio_ffmpeg.get_ffmpeg_exe(), '-hide_banner', '-i', str(path)],
                               stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        text = probe.stderr.decode(errors='replace')
        assert 'Video: h264' in text and 'Audio:' not in text, (path, text)
        results.append({'file': path.name, 'size': shape, 'frames': len(colors), 'fps': metadata['fps'],
                        'seconds': metadata['duration'], 'red_frames': 30, 'blue_frames': 45, 'audio': False})
    args.report.write_text(json.dumps({'passed': True, 'files': results}, indent=2) + '\n', encoding='utf-8')
    print('Three independent full decodes passed: H.264, silent, dimensions, 75 frames, exact color order/timing.')


if __name__ == '__main__':
    main()

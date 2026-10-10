"""Host-only independent decoding of the two synthetic release presentation exports."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import subprocess
from zipfile import ZipFile
import imageio_ffmpeg
import numpy as np
from PIL import Image


def verify(folder):
    ffmpeg = imageio_ffmpeg.get_ffmpeg_exe()
    video = folder / 'release-slideshow.mp4'
    frames = subprocess.run([ffmpeg, '-v', 'error', '-i', str(video), '-vf', 'scale=160:90',
                             '-pix_fmt', 'rgb24', '-f', 'rawvideo', '-'], check=True, capture_output=True).stdout
    frames = np.frombuffer(frames, dtype=np.uint8).reshape(-1, 90, 160, 3)
    assert len(frames) == 180, ('Two 3-second slides at 30fps', len(frames))
    checks = []
    for notes in (False, True):
        file = folder / f'release-notes-{str(notes).lower()}.pptx'
        with ZipFile(file) as archive:
            names = archive.namelist()
            assert len([n for n in names if n.startswith('ppt/media/')]) == 2
            assert any(n.startswith('ppt/notesSlides/notesSlide') for n in names) == notes
            differences = []
            for index in range(2):
                image = Image.open(io.BytesIO(archive.read(f'ppt/media/image{index+1}.png'))).convert('RGB')
                assert image.size == (2048, 1152)
                reference = np.asarray(image.resize((160, 90), Image.Resampling.LANCZOS)).astype(float)
                actual = frames[15 + index * 90].astype(float)
                rmse = float(np.sqrt(np.mean((reference - actual) ** 2)))
                assert rmse < 8, ('Video/PPTX slide appearance', index, rmse)
                differences.append(rmse)
            checks.append(dict(file=file.name, sha256=hashlib.sha256(file.read_bytes()).hexdigest(),
                               notes=notes, slides=2, video_rgb_rmse_0_255=differences))
    return dict(passed=True, host_only=True, decoded_video_frames=len(frames),
                synthetic_video_seconds=6, pptx=checks, real_office_tested=False)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', type=Path)
    parser.add_argument('--report', required=True, type=Path)
    args = parser.parse_args()
    report = verify(args.directory)
    args.report.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print('Two release PPTX files and 180 slideshow frames independently verified.')

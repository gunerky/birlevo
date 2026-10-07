"""Host-only decode of six app outputs: image alpha, opacity, placement and output timing and unchanged audio pitch."""
import json
from pathlib import Path
import re
import subprocess
import sys
import imageio_ffmpeg
import numpy as np

folder = Path(sys.argv[1])
ffmpeg = imageio_ffmpeg.get_ffmpeg_exe()
report = []
for speed in (.5, 2.0):
    for target, extension in [('MP4_AVC', 'mp4'), ('MP4_HEVC', 'mp4'), ('WEBM_VP8', 'webm')]:
        path = folder / f'{speed}-{target}.{extension}'
        reader = imageio_ffmpeg.read_frames(str(path), pix_fmt='rgb24')
        metadata = next(reader); reader.close()
        assert metadata['size'] == (180, 320), (path, metadata)
        assert abs(metadata['duration'] - 2 / speed) < .18
        decoded = subprocess.run([ffmpeg, '-hide_banner', '-i', str(path), '-map', '0:v',
            '-vf', 'showinfo', '-pix_fmt', 'rgb24', '-fps_mode', 'passthrough', '-f', 'rawvideo', '-'], capture_output=True, check=True)
        frames = np.frombuffer(decoded.stdout, dtype=np.uint8).reshape(-1, 320, 180, 3)
        times = [float(value) for value in re.findall(r'\bn:\s*\d+.*?\bpts_time:\s*([\d.e+-]+)', decoded.stderr.decode(errors='replace'))]
        assert len(times) == len(frames)
        start, end = (1.5, 2.5) if speed == .5 else (.3, .7)
        checked = []
        for timestamp, frame in zip(times, frames):
            background = np.array((0, 255, 0) if timestamp < 1 / speed else (255, 255, 0))
            matches = np.all(np.abs(frame[:106, :135].astype(int) - np.array((255, 255, 255))) < 35, axis=2).sum()
            assert matches > 12, (path, timestamp, 'text missing')
            visible = start <= timestamp < end
            for x, y, alpha in ((110, 275, .5), (160, 275, .5 * 128 / 255), (135, 286, 0), (90, 160, 0)):
                expected = background * (1 - alpha) + np.array((0, 0, 255)) * alpha if visible else background
                actual = frame[y-2:y+3, x-2:x+3].astype(float).mean(axis=(0, 1))
                assert np.max(np.abs(actual - expected)) < 35, (path, timestamp, x, y, actual, expected)
            checked.append(dict(time=timestamp, visible=visible, text_pixels=int(matches)))
        audio = subprocess.run([ffmpeg, '-hide_banner', '-i', str(path), '-map', '0:a:0', '-f', 's16le',
            '-ar', '48000', '-ac', '2', '-'], capture_output=True, check=True)
        assert '48000 Hz, stereo' in audio.stderr.decode(errors='replace').split('Output #')[0]
        samples = np.frombuffer(audio.stdout, dtype='<i2').reshape(-1, 2)
        assert abs(len(samples) / 48000 - 2 / speed) < .18
        tones = []
        for t, expected in ((.25 / speed, 440), (1.25 / speed, 660)):
            data = samples[round(t * 48000):round((t + .2 / speed) * 48000), 0].astype(float)
            spectrum = np.abs(np.fft.rfft(data * np.hanning(len(data))))
            found = np.fft.rfftfreq(len(data), 1 / 48000)[np.argmax(spectrum)]
            assert abs(found - expected) < 20, (path, found)
            tones.append(float(found))
        report.append(dict(file=path.name, frames=len(frames), size=metadata['size'], duration=metadata['duration'],
            tones=tones, image_range=[start, end], timestamps=checked))
(folder.parent / 'independent-image-decode.json').write_text(json.dumps(dict(passed=True, outputs=report), indent=2) + '\n', encoding='utf-8')
print('Six independent decodes passed: every frame image timing/alpha/opacity/position, concurrent text, dimensions, clip order and audio pitch.')

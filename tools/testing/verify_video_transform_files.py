"""Independent host-only decode of app-produced speed/canvas files; no app FFmpeg dependency."""
import json
from pathlib import Path
import subprocess
import sys
import re
import imageio_ffmpeg
import numpy as np

folder = Path(sys.argv[1])
ffmpeg = imageio_ffmpeg.get_ffmpeg_exe()
report = []
def pcm(path):
    return np.frombuffer(subprocess.check_output([ffmpeg, '-v', 'error', '-i', str(path),
        '-vn', '-ac', '1', '-ar', '48000', '-f', 'f32le', '-']), dtype='<f4')
def tone(samples, start, duration):
    data = samples[round(start * 48000):round((start + duration) * 48000)]
    spectrum = np.abs(np.fft.rfft(data * np.hanning(len(data))))
    return float(np.fft.rfftfreq(len(data), 1 / 48000)[np.argmax(spectrum)])
def video(path):
    reader = imageio_ffmpeg.read_frames(str(path), pix_fmt='rgb24')
    metadata = next(reader); reader.close()
    decoded = subprocess.run([ffmpeg, '-hide_banner', '-i', str(path), '-map', '0:v',
        '-vf', 'showinfo', '-pix_fmt', 'rgb24', '-fps_mode', 'passthrough', '-f', 'rawvideo', '-'], capture_output=True, check=True)
    width, height = metadata['size']
    frames = np.frombuffer(decoded.stdout,dtype=np.uint8).reshape(-1,height,width,3)
    times = [float(v) for v in re.findall(r'\bn:\s*\d+.*?\bpts_time:\s*([\d.e+-]+)', decoded.stderr.decode(errors='replace'))]
    assert len(times) == len(frames), (path,len(times),len(frames))
    return metadata, frames, times
_, reference_frames, _ = video(folder/'reference-1x.mp4')
for speed in (0.5, 2.0):
    for format, extension in [('MP4_AVC','mp4'),('MP4_HEVC','mp4'),('WEBM_VP8','webm')]:
        path = folder / f'speed-{speed}-{format}.{extension}'
        metadata, frames, times = video(path)
        width, height = metadata['size']
        assert (width,height) == ((180,320) if speed == 0.5 else (320,320)), metadata
        assert abs(metadata['duration'] - 2 / speed) < .18, metadata
        assert len(frames) == len(reference_frames), (path,len(frames),len(reference_frames))
        for time, expected in ((.25 / speed, (0,255,0)),(1.25 / speed,(255,255,0))):
            frame = frames[min(range(len(times)), key=lambda i: abs(times[i]-time))]
            assert np.max(np.abs(frame[height//2,width//2].astype(int)-expected)) < 35, path
            top = frame[height//10,width//2].astype(int)
            if speed == 0.5 and expected == (0,255,0): assert top.sum() < 30, (path,top)
            if speed == 2: assert np.max(np.abs(top-expected)) < 35, (path,top)
        audio = pcm(path)
        frequencies = [tone(audio,start/speed,.3/speed) for start in (.25,1.25)]
        assert all(abs(a-b)<20 for a,b in zip(frequencies,[440,660])), (path,frequencies)
        assert abs(len(audio)/48000 - 2/speed) < .18, path
        report.append(dict(file=path.name,frames=len(frames),size=[width,height],duration=metadata['duration'],tones=frequencies))
    path = folder/f'silence-{speed}.mp4';audio = pcm(path)
    silent=audio[round(.2/speed*48000):round(.7/speed*48000)]
    sound=audio[round(1.2/speed*48000):round(1.7/speed*48000)]
    assert np.max(np.abs(silent)) < .01, path
    assert np.max(np.abs(sound)) > .03, path
    frequency=tone(audio,1.2/speed,.3/speed)
    assert abs(frequency-440)<20, (path,frequency)
    report.append(dict(file=path.name,silence_peak=float(np.max(np.abs(silent))),tone=frequency))
(folder.parent/'independent-decode.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
print(f'{len(report)} app outputs independently decoded: canvas, cropping, frame count, timing, audio pitch and silence verified.')

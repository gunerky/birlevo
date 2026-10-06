"""Generate deterministic, original video test assets; requires imageio-ffmpeg on the host."""
from pathlib import Path
import subprocess
import imageio_ffmpeg

root = Path(__file__).resolve().parents[2]
assets = root / 'app/src/androidTest/assets/videos'
assets.mkdir(parents=True, exist_ok=True)
ffmpeg = imageio_ffmpeg.get_ffmpeg_exe()

def generate(name, size, seconds, bitrate, sound=True):
    args = [ffmpeg, '-y', '-hide_banner', '-loglevel', 'error', '-f', 'lavfi', '-i', f'testsrc2=size={size}:rate=30']
    if sound:
        args += ['-f', 'lavfi', '-i', 'sine=frequency=440:sample_rate=44100']
    args += ['-t', str(seconds), '-c:v', 'libx264', '-threads', '2', '-pix_fmt', 'yuv420p',
             '-b:v', bitrate, '-minrate', bitrate, '-maxrate', bitrate, '-bufsize', '10M',
             '-x264-params', 'nal-hrd=cbr:force-cfr=1', '-colorspace', 'bt709', '-color_trc', 'bt709', '-color_primaries', 'bt709']
    if sound:
        args += ['-c:a', 'aac', '-b:a', '128k']
    args += ['-movflags', '+faststart', str(assets / name)]
    subprocess.run(args, check=True)

generate('audio.mp4', '1280x720', 6, '5M')
generate('silent.mp4', '320x240', 2, '2M', False)
generate('low-bitrate.mp4', '320x240', 4, '20k', False)
generate('portrait-base.mp4', '640x360', 3, '2M')
subprocess.run([ffmpeg, '-y', '-hide_banner', '-loglevel', 'error', '-display_rotation', '90',
                '-i', str(assets/'portrait-base.mp4'), '-c', 'copy', str(assets/'portrait.mp4')], check=True)
(assets/'portrait-base.mp4').unlink()
generate('cancel.mp4', '1280x720', 16, '5M')
(assets/'invalid.mp4').write_bytes(b'not an MP4 video')
print('Video fixtures:', ', '.join(f'{p.name}={p.stat().st_size}' for p in assets.glob('*.mp4')))

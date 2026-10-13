"""Original color/tone fixtures for independent two-clip order and trim verification."""
from pathlib import Path
import subprocess
import imageio_ffmpeg

def main():
    folder = Path(__file__).resolve().parents[2] / 'app/src/androidTest/assets/videos'
    folder.mkdir(parents=True, exist_ok=True)
    ffmpeg = imageio_ffmpeg.get_ffmpeg_exe()
    for name, size, initial, final, change, frequency, rate, channels in [
        ('edit-first.mp4', '320x180', 'red', 'lime', 1, 440, 44100, 1),
        ('edit-second.mp4', '180x320', 'blue', 'yellow', 1.5, 660, 48000, 2),
    ]:
        subprocess.run([ffmpeg, '-y', '-hide_banner', '-loglevel', 'error', '-f', 'lavfi', '-i',
            f'color=c={initial}:s={size}:r=30', '-f', 'lavfi', '-i', f'sine=frequency={frequency}:sample_rate={rate}',
            '-t', '3', '-vf', f"drawbox=x=0:y=0:w=iw:h=ih:color={final}:t=fill:enable='gte(t,{change})'",
            '-c:v', 'libx264', '-threads', '2', '-pix_fmt', 'yuv420p', '-b:v', '1M', '-minrate', '1M',
            '-maxrate', '1M', '-bufsize', '2M', '-x264-params', 'nal-hrd=cbr:force-cfr=1',
            '-c:a', 'aac', '-b:a', '128k', '-ac', str(channels), '-movflags', '+faststart',
            str(folder / name)], check=True)
    print('Two original color / rate / channel fixtures generated.')

if __name__ == '__main__': main()

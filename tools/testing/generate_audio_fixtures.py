"""Original deterministic tones, test APK only; host ffmpeg is never an app dependency."""
from pathlib import Path
import subprocess
import imageio_ffmpeg

root = Path(__file__).resolve().parents[2]
assets = root / 'app/src/androidTest/assets/audio'
assets.mkdir(parents=True, exist_ok=True)
ffmpeg = imageio_ffmpeg.get_ffmpeg_exe()
for name, codec, extra in [
    ('long.m4a', 'aac', ['-b:a', '128k']), ('tone.wav', 'pcm_s16le', []), ('tone.mp3', 'libmp3lame', ['-b:a', '96k']),
    ('tone.m4a', 'aac', ['-b:a', '192k']), ('tone.aac', 'aac', ['-b:a', '128k']),
    ('tone.ogg', 'libvorbis', []), ('tone.opus', 'libopus', []), ('tone.flac', 'flac', []),
]:
    subprocess.run([ffmpeg, '-y', '-hide_banner', '-loglevel', 'error', '-f', 'lavfi', '-i',
                    'sine=frequency=660:sample_rate=48000', '-t', '300' if name == 'long.m4a' else '3', '-ac', '2', '-c:a', codec,
                    *extra, str(assets / name)], check=True)
(assets / 'invalid.mp3').write_bytes(b'Not an audio file')
subprocess.run([ffmpeg, '-y', '-hide_banner', '-loglevel', 'error', '-f', 'lavfi', '-i',
                'sine=frequency=440:sample_rate=44100', '-t', '3', '-ac', '1', '-c:a', 'pcm_s16le',
                str(assets / 'mono44100.wav')], check=True)
print('Original audio fixtures generated.')

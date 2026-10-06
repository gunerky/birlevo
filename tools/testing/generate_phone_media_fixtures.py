"""Small synthetic fixtures only. Run with host Pillow >=12 and pillow-heif 1.8; not app dependencies."""
import argparse
import os
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'output/development/phone-media/host-tools'))
from PIL import Image, ImageDraw
import pillow_heif
import imageio_ffmpeg

def main():
    images = ROOT / 'app/src/androidTest/assets/images'
    videos = ROOT / 'app/src/androidTest/assets/videos'
    images.mkdir(parents=True, exist_ok=True)
    pillow_heif.register_heif_opener()
    image = Image.new('RGB', (120, 80))
    draw = ImageDraw.Draw(image)
    for box, color in [((0,0,59,39),'red'), ((60,0,119,39),'lime'), ((0,40,59,79),'blue'), ((60,40,119,79),'yellow')]:
        draw.rectangle(box, fill=color)
    image.save(images / 'modern.heic', quality=95)
    image.save(images / 'modern.avif', quality=95)
    exif = Image.Exif(); exif[274] = 6
    # Bytes are required here: a PIL.Exif object adds metadata but does not set libheif's irot.
    image.save(images / 'modern-irot.heic', quality=95, exif=exif.tobytes())
    # A valid brand header marked as a sequence is refused before any frame is decoded.
    sequence = bytearray((images / 'modern.avif').read_bytes())
    sequence[8:12] = b'avis'
    (images / 'modern-sequence.avif').write_bytes(sequence)
    ffmpeg = imageio_ffmpeg.get_ffmpeg_exe()
    def video(name, args):
        subprocess.run([ffmpeg, '-hide_banner', '-loglevel', 'error', '-y', '-f', 'lavfi', '-i',
            'testsrc2=size=320x240:rate=24', '-f', 'lavfi', '-i', 'sine=frequency=440:sample_rate=48000',
            '-t', '2', '-threads', '2', *args, str(videos / name)], check=True)
    video('phone.mov', ['-c:v','libx264','-pix_fmt','yuv420p','-c:a','aac'])
    video('phone.3gp', ['-c:v','libx264','-pix_fmt','yuv420p','-c:a','aac','-ac','1'])
    video('phone.webm', ['-c:v','libvpx','-pix_fmt','yuv420p','-b:v','400k','-c:a','libopus'])
    video('phone-vorbis.webm', ['-c:v','libvpx','-pix_fmt','yuv420p','-b:v','400k','-c:a','libvorbis'])
    video('phone-mp3.mkv', ['-c:v','libx264','-pix_fmt','yuv420p','-c:a','libmp3lame'])
    video('phone-10bit.mp4', ['-c:v','libx265','-pix_fmt','yuv420p10le','-x265-params','pools=1:frame-threads=1:log-level=error','-c:a','aac'])
    video('phone-mpeg4.mp4', ['-c:v','mpeg4','-c:a','aac'])
    print('Synthetic HEIC / AVIF and phone container fixtures generated.')

if __name__ == '__main__':
    main()

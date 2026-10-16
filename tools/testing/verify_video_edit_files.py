"""Independently decode all four targets in both clip orders, including VP9."""
import argparse
import json
from pathlib import Path
import subprocess
import imageio_ffmpeg
import numpy as np

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('folder',type=Path)
    parser.add_argument('--report',type=Path,required=True)
    args = parser.parse_args(); ffmpeg = imageio_ffmpeg.get_ffmpeg_exe(); results = []
    paths = sorted(args.folder.glob('*'))
    expected = {f'{order}-{target}.{extension}' for order in ('forward','reverse')
                for target,extension in [('MP4_AVC','mp4'),('MP4_HEVC','mp4'),('WEBM_VP8','webm'),('WEBM_VP9','webm')]}
    assert {path.name for path in paths} == expected, 'Expected all eight generated video files'
    for path in paths:
        reverse = path.name.startswith('reverse-'); width,height = (180,320) if reverse else (320,180)
        audio = subprocess.run([ffmpeg,'-hide_banner','-i',str(path),'-map','0:a:0','-f','s16le','-acodec','pcm_s16le','-ar','48000','-ac','2','-'],
                               stdout=subprocess.PIPE,stderr=subprocess.PIPE,check=True)
        assert '48000 Hz, stereo' in audio.stderr.decode(errors='replace').split('Output #')[0]
        samples = np.frombuffer(audio.stdout,dtype='<i2').reshape(-1,2)
        assert abs(len(samples)/48000-2) < .15, path
        measured = []
        for second,frequency in [(.2,660 if reverse else 440),(1.2,440 if reverse else 660)]:
            chunk = samples[int(second*48000):int((second+.2)*48000),0].astype(float)
            spectrum = abs(np.fft.rfft(chunk*np.hanning(len(chunk))))
            found = np.fft.rfftfreq(len(chunk),1/48000)[np.argmax(spectrum)]
            assert abs(found-frequency) < 6, (path,found,frequency)
            measured.append(float(found))
        for second,expected in [(.25,(255,255,0) if reverse else (0,255,0)),(1.25,(0,255,0) if reverse else (255,255,0))]:
            frame = subprocess.run([ffmpeg,'-hide_banner','-loglevel','error','-ss',str(second),'-i',str(path),'-frames:v','1',
                '-f','rawvideo','-pix_fmt','rgb24','-'],stdout=subprocess.PIPE,check=True).stdout
            pixels = np.frombuffer(frame,dtype=np.uint8).reshape(height,width,3)
            assert np.max(np.abs(pixels[height//2,width//2].astype(int)-expected)) < 30, (path,second)
            # Different aspect ratios must fit the entire second clip, not fill/crop it.
            if second > 1:
                border = pixels[2,width//2] if reverse else pixels[height//2,2]
                assert border.max() < 20, (path,'missing fit border',border)
        results.append({'file':path.name,'size':[width,height],'decoded_seconds':len(samples)/48000,'tone_order_hz':measured})
    args.report.write_text(json.dumps({'passed':True,'files':results},indent=2)+'\n',encoding='utf-8')
    print('Eight independent decodes passed: real colors/ranges/order, fit borders, tone order and duration.')

if __name__ == '__main__': main()

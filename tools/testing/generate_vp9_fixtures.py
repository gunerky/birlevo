"""Original synthetic clips; host FFmpeg is not an Android dependency."""
import imageio_ffmpeg
from pathlib import Path
import subprocess,json,hashlib
root=Path(__file__).resolve().parents[2]
folder=root/'app/src/androidTest/assets/videos'
ffmpeg=imageio_ffmpeg.get_ffmpeg_exe()
for name,pixels,profile,transfer,sound in [
 ('vp9.webm','yuv420p',0,'bt709',True),
 ('vp9.mkv','yuv420p',0,'bt709',False),
 ('vp9-10bit.webm','yuv420p10le',2,'bt709',False),
 ('vp9-hdr.webm','yuv420p10le',2,'smpte2084',False),
]:
 args=[ffmpeg,'-y','-v','error','-f','lavfi','-i','testsrc2=size=320x180:rate=30']
 if sound:args+=['-f','lavfi','-i','sine=frequency=660:sample_rate=48000']
 args+=['-t','2','-c:v','libvpx-vp9','-profile:v',str(profile),'-pix_fmt',pixels,'-b:v','500k',
        '-threads','2','-deadline','realtime','-cpu-used','8','-g','30','-color_trc',transfer,
        '-color_primaries','bt709' if transfer=='bt709' else 'bt2020','-colorspace','bt709' if transfer=='bt709' else 'bt2020nc']
 if sound:args+=['-c:a','libopus','-b:a','96k']
 subprocess.run(args+[str(folder/name)],check=True)
records={name:{'bytes':(folder/name).stat().st_size,'sha256':hashlib.sha256((folder/name).read_bytes()).hexdigest()}
         for name in ['vp9.webm','vp9.mkv','vp9-10bit.webm','vp9-hdr.webm']}
(folder/'vp9-manifest.json').write_text(json.dumps(records,indent=2)+'\n',encoding='utf-8')
print('Four synthetic VP9 fixtures generated.')

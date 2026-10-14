"""Independent host FFmpeg RGB reference for PNG frames and recorded decoder PTS. No runtime dependency."""
import json
import subprocess
import sys
from pathlib import Path
import imageio_ffmpeg
import numpy as np
from PIL import Image

folder=Path(sys.argv[1])
rows=[]
for name, extension, times in [('source','mp4',[0,1300,2999]),('portrait','mp4',[0,1300,2999]),
                               ('MP4_AVC','mp4',[500,1500]),('MP4_HEVC','mp4',[500,1500]),('WEBM_VP8','webm',[500,1500]),
                               ('WEBM_VP9','webm',[500,1500])]:
    for requested in times:
        png=folder/f'{name}-{requested}.png'
        actual=int((folder/f'{name}-{requested}.pts').read_text())
        got=np.asarray(Image.open(png).convert('RGB'))
        h,w,_=got.shape
        reference=subprocess.run([imageio_ffmpeg.get_ffmpeg_exe(),'-hide_banner','-loglevel','error','-i',str(folder/f'{name}.{extension}'),
            '-vf',f'select=gte(t\\,{actual/1e6-0.00001})','-frames:v','1','-pix_fmt','rgb24','-f','rawvideo','-'],capture_output=True,check=True).stdout
        assert len(reference)==h*w*3,(name,requested,'dimensions',len(reference),got.shape)
        expected=np.frombuffer(reference,dtype=np.uint8).reshape(h,w,3)
        delta=np.abs(got.astype(float)-expected.astype(float))
        assert delta.mean()<9 and np.median(delta)<4,(name,requested,delta.mean(),np.median(delta))
        rows.append(dict(name=name,requested_ms=requested,actual_us=actual,width=w,height=h,mean_rgb_error=float(delta.mean())))
assert len(rows)==14
(folder.parent/'independent-frame-decode.json').write_text(json.dumps(dict(passed=True,frames=rows),indent=2)+'\n',encoding='utf-8')
print(f'Independent frame verification passed: {len(rows)} PNG frames, actual PTS, rotation and RGB.')

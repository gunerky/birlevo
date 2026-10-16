"""Independently decode current synthetic VP9 evidence with host FFmpeg; never shipped."""
import json,subprocess,sys,re
from pathlib import Path
import imageio_ffmpeg
folder=Path(sys.argv[1]);ffmpeg=imageio_ffmpeg.get_ffmpeg_exe();rows=[]
names=[f'WEBM_VP9-{source}-BALANCED-640.webm' for source in
       ['audio_mp4','silent_mp4','portrait_mp4','vp9_webm','vp9_mkv']]
names += ['WEBM_VP9-audio_mp4-SMALL-640.webm','WEBM_VP9-audio_mp4-HIGH-1280.webm']
for name in names:
 path=folder/name
 reader=imageio_ffmpeg.read_frames(str(path),pix_fmt='rgb24');metadata=next(reader);reader.close()
 assert metadata['codec']=='vp9',(name,metadata)
 expected_size=(360,640) if 'portrait' in name else ((320,240) if 'silent' in name else ((320,180) if 'vp9_webm' in name or 'vp9_mkv' in name else ((1280,720) if 'HIGH' in name else (640,360))))
 assert metadata['size']==expected_size,(name,metadata)
 assert abs(metadata['duration']-1.5)<.3,(name,metadata)
 # Keep the demuxer's time base: a coarse null-sink time base can round distinct
 # WebM timestamps to the same DTS. Decode strictly and inspect actual frame PTS.
 decoded=subprocess.run([ffmpeg,'-hide_banner','-xerror','-i',str(path),'-vf','showinfo',
     '-fps_mode','passthrough','-enc_time_base','demux','-f','null','-'],capture_output=True,check=True)
 times=[float(t) for t in re.findall(r'\bn:\s*\d+.*?\bpts_time:\s*([\d.e+-]+)',decoded.stderr.decode(errors='replace'))]
 assert len(times)>1 and all(a<b for a,b in zip(times,times[1:])),(name,times)
 # Read the actual VP9 access unit, not the container's profile declaration.
 stream=subprocess.check_output([ffmpeg,'-v','error','-i',str(path),'-map','0:v:0','-c:v','copy','-f','ivf','-'])
 assert stream[:4]==b'DKIF' and len(stream)>44
 header=stream[44]; assert header>>6==2 and header&0x30==0,(name,header)
 info=subprocess.run([ffmpeg,'-hide_banner','-i',str(path)],capture_output=True).stderr.decode(errors='replace')
 has_audio='silent' not in name and 'vp9_mkv' not in name
 assert ('Audio: opus' in info)==has_audio,(name,info)
 tones=[]
 if has_audio:
  import numpy as np
  pcm=subprocess.check_output([ffmpeg,'-v','error','-i',str(path),'-vn','-ac','1','-ar','48000','-f','f32le','-'])
  data=np.frombuffer(pcm,dtype='<f4')[4800:14400]
  spectrum=np.abs(np.fft.rfft(data*np.hanning(len(data))))
  frequency=float(np.fft.rfftfreq(len(data),1/48000)[np.argmax(spectrum)])
  expected=660 if 'vp9_webm' in name else 440
  assert abs(frequency-expected)<20,(name,frequency)
  tones.append(frequency)
 rows.append({'file':name,'size':metadata['size'],'duration':metadata['duration'],'profile':0,'audio':has_audio,'tones':tones,'bytes':path.stat().st_size,'decoded_frames':len(times),'first_pts':times[0],'last_pts':times[-1]})
(folder.parent/'independent-vp9.json').write_text(json.dumps({'passed':True,'outputs':rows},indent=2)+'\n',encoding='utf-8')
print(f'{len(rows)} VP9 files independently decoded: profile, shape, timing, audio and pitch verified.')

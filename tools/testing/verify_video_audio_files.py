"""Host-only frame/audio decode of all four targets and replacement mixes."""
from pathlib import Path
import json,re,subprocess,sys
import imageio_ffmpeg
import numpy as np
folder=Path(sys.argv[1]); ffmpeg=imageio_ffmpeg.get_ffmpeg_exe(); report=[]
def pcm(path,channels):
    decoded=subprocess.run([ffmpeg,'-hide_banner','-i',str(path),'-map','0:a:0','-f','s16le','-ar','48000','-ac',str(channels),'-'],capture_output=True,check=True)
    return np.frombuffer(decoded.stdout,dtype='<i2').reshape(-1,channels), decoded.stderr.decode(errors='replace')
def amplitude(samples,time,duration,frequency):
    data=samples[round(time*48000):round((time+duration)*48000),0].astype(float)
    window=np.hanning(len(data)); t=np.arange(len(data))/48000
    return abs(np.sum(data*window*np.exp(-2j*np.pi*frequency*t)))*2/window.sum()
root=Path(__file__).resolve().parents[2]
first,_=pcm(root/'app/src/androidTest/assets/videos/edit-first.mp4',1)
second,_=pcm(root/'app/src/androidTest/assets/videos/edit-second.mp4',2)
added,_=pcm(root/'app/src/androidTest/assets/audio/mix.wav',1)
reference=[amplitude(first,1.3,.15,440),amplitude(second,1.9,.15,660),amplitude(added,.7,.15,880)]
for speed in (.5,2.0):
    for fmt,ext in [('MP4_AVC','mp4'),('MP4_HEVC','mp4'),('WEBM_VP8','webm'),('WEBM_VP9','webm')]:
        path=folder/f'{speed}-{fmt}.{ext}'
        reader=imageio_ffmpeg.read_frames(str(path),pix_fmt='rgb24');meta=next(reader);reader.close()
        assert meta['size']==(180,320);assert abs(meta['duration']-2/speed)<.18
        frames=subprocess.run([ffmpeg,'-hide_banner','-i',str(path),'-map','0:v','-vf','showinfo','-pix_fmt','rgb24','-fps_mode','passthrough','-f','rawvideo','-'],capture_output=True,check=True)
        pictures=np.frombuffer(frames.stdout,dtype=np.uint8).reshape(-1,320,180,3)
        times=[float(v) for v in re.findall(r'\bn:\s*\d+.*?\bpts_time:\s*([\d.e+-]+)',frames.stderr.decode(errors='replace'))]
        assert len(times)==len(pictures)
        for t,picture in zip(times,pictures):
            color=np.array((0,255,0) if t<1/speed else (255,255,0))
            assert np.max(np.abs(picture[160,90].astype(int)-color))<35
            assert np.all(np.abs(picture[213:,:].astype(int)-255)<35,axis=2).sum()>12
        samples,log=pcm(path,2);assert '48000 Hz, stereo' in log.split('Output #')[0]
        assert abs(len(samples)/48000-2/speed)<.18
        start,end=(1.5,2.5) if speed==.5 else (.3,.7)
        checks=[]
        for t,freq,index in ((.2/speed,440,0),(.85/speed,440,0),(1.15/speed,660,1),(1.75/speed,660,1)):
            span=.1 if speed==.5 else .05
            orig=amplitude(samples,t,span,freq)
            assert abs(orig/(reference[index]*.5)-1)<.3,(path,t,'original gain',orig,reference)
            add=amplitude(samples,t,span,880)
            visible=start<=t and t+span<end
            if visible: assert abs(add/(reference[2]*.5)-1)<.3,(path,t,'added gain',add,reference)
            else: assert add<reference[2]*.06,(path,t,'audio leaked',add)
            checks.append(dict(time=t,original_amplitude=float(orig),added_amplitude=float(add)))
        report.append(dict(file=path.name,frames=len(pictures),duration=meta['duration'],range=[start,end],checks=checks))
for name,ext in [('replace','mp4'),('silent','webm')]:
    path=folder/f'{name}.{ext}';samples,log=pcm(path,1)
    assert '48000 Hz, mono' in log.split('Output #')[0],log
    assert abs(len(samples)/48000-2)<.18
    for t,visible in ((.15,False),(.75,True),(1.25,True),(1.75,False)):
        a=amplitude(samples,t,.1,880)
        if visible: assert abs(a/(reference[2]*.75)-1)<.3,(name,t,a)
        else: assert np.sqrt(np.mean(samples[round(t*48000):round((t+.1)*48000),0].astype(float)**2))<40,(name,t,'silence missing')
        assert amplitude(samples,t,.1,440)<reference[0]*.04
    report.append(dict(file=path.name,channels=1,duration=len(samples)/48000,range=[.5,1.5],original_muted=True))
(folder.parent/'independent-audio-decode.json').write_text(json.dumps(dict(passed=True,reference=reference,outputs=report),indent=2)+'\n',encoding='utf-8')
print('Ten outputs independently passed: pitch, timing, per-track gain, source mute/silence and video/text/duration.')

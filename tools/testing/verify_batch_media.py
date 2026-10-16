"""Independent host ZIP/hash/full FFmpeg decode/shape/duration/channel/pitch checks."""
import argparse,hashlib,json,re,shutil,subprocess,zipfile
from pathlib import Path
import imageio_ffmpeg
import numpy as np

parser=argparse.ArgumentParser();parser.add_argument('folder',type=Path);args=parser.parse_args()
root=Path(__file__).resolve().parents[2];ffmpeg=imageio_ffmpeg.get_ffmpeg_exe()
decoded=args.folder/'decoded';decoded.mkdir(exist_ok=True)
codec={'MP4_AVC':'h264','MP4_HEVC':'hevc','WEBM_VP8':'vp8','WEBM_VP9':'vp9',
       'WAV':'pcm_s16le','M4A':'aac','OGG':'opus','FLAC':'flac','MP3':'mp3'}
def pitch(path):
 data=subprocess.check_output([ffmpeg,'-v','error','-i',str(path),'-t','0.5','-vn','-ac','1','-ar','48000','-f','f32le','-'])
 samples=np.frombuffer(data,dtype='<f4')[4800:19200];assert len(samples)>1000 and np.max(np.abs(samples))>.005
 spectrum=np.abs(np.fft.rfft(samples*np.hanning(len(samples))))
 return float(np.fft.rfftfreq(len(samples),1/48000)[np.argmax(spectrum)])
report=[]
for manifest in ['video-manifest.json','audio-manifest.json']:
 rows=json.loads((args.folder/manifest).read_text(encoding='utf-8'))['outputs']
 assert len(rows)==(4 if manifest.startswith('video') else 5)
 for row in rows:
  archive=args.folder/row['zip'];assert archive.parent.resolve()==args.folder.resolve()
  with zipfile.ZipFile(archive) as zip:
   assert zip.testzip() is None and zip.namelist()==[entry['name'] for entry in row['entries']]
   assert len(zip.namelist())==len(set(name.lower() for name in zip.namelist()))==3
   for entry in row['entries']:
    name=entry['name'];assert re.fullmatch(r'\d{2}_[A-Za-z0-9_-]+\.(mp4|webm|wav|m4a|ogg|flac|mp3)',name)
    info=zip.getinfo(name);assert info.file_size==entry['bytes'] and info.file_size<=512*1024*1024
    file=decoded/(archive.stem+'-'+name)
    with zip.open(name) as source,file.open('wb') as destination:shutil.copyfileobj(source,destination,65536)
    assert hashlib.sha256(file.read_bytes()).hexdigest()==entry['sha256']
    output=subprocess.run([ffmpeg,'-v','error','-i',str(file),'-f','null','-'],capture_output=True,check=True)
    assert not output.stderr,(name,output.stderr.decode(errors='replace'))
    text=subprocess.run([ffmpeg,'-hide_banner','-i',str(file)],capture_output=True).stderr.decode(errors='replace')
    timing=re.search(r'Duration: (\d+):(\d+):([\d.]+)',text);assert timing,text
    seconds=int(timing[1])*3600+int(timing[2])*60+float(timing[3]);assert abs(seconds-entry['sourceDurationMs']/1000)<.25,(name,seconds,entry)
    source=root/'app/src/androidTest/assets'/('videos' if entry['source'].endswith('.mp4') else 'audio')/entry['source']
    result={'zip':archive.name,'entry':name,'bytes':entry['bytes'],'duration':seconds,'sha256':entry['sha256']}
    if row['kind']=='VIDEO':
     reader=imageio_ffmpeg.read_frames(str(file),pix_fmt='rgb24');metadata=next(reader);reader.close()
     # imageio's FFmpeg text parser can retain a trailing comma for VP8/VP9 labels.
     assert metadata['codec'].rstrip(',')==codec[row['format']],(name,metadata)
     scale=min(1,row['edge']/max(entry['sourceWidth'],entry['sourceHeight']))
     size=tuple(max(2,int(entry[key]*scale)//2*2) for key in ['sourceWidth','sourceHeight'])
     assert metadata['size']==size,(name,metadata,size)
     has_audio=entry['source']!='silent.mp4'
     assert ('Audio:' in text)==has_audio
     if has_audio:
      assert ('Audio: opus' in text) if row['format'].startswith('WEBM') else ('Audio: aac' in text)
      assert abs(pitch(file)-pitch(source))<20
     result.update(codec=metadata['codec'].rstrip(','),size=size,hasAudio=has_audio)
    else:
     assert 'Video:' not in text,text
     audio=re.search(r'Audio: ([^ ,]+).*?, (\d+) Hz, ([^,\r\n]+)',text);assert audio,text
     assert audio[1]==codec[row['format']] and int(audio[2])==entry['sampleRate'],(name,audio.groups(),entry)
     assert audio[3].strip()==('mono' if entry['channels']==1 else 'stereo'),(name,audio.groups(),entry)
     assert abs(pitch(file)-pitch(source))<20
     result.update(codec=audio[1],sampleRate=int(audio[2]),channels=entry['channels'])
    report.append(result)
assert len(report)==27
(args.folder/'independent-media.json').write_text(json.dumps({'archives':9,'files':report},indent=2)+'\n',encoding='utf-8')
print('9 ZIP archives / 27 files: exact hash, full decode, codec, shape, timing, audio rate/channels and pitch passed.')

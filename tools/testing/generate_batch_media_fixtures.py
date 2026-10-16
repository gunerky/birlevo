"""Manual host-only synthetic fixtures; no Android/Gradle hook."""
import hashlib,json,math,struct,subprocess,wave
from pathlib import Path
import imageio_ffmpeg
root=Path(__file__).resolve().parents[2]; assets=root/'app/src/androidTest/assets'
audio=assets/'audio/batch-192000.wav';video=assets/'videos/batch-turn180.mp4'
with wave.open(str(audio),'wb') as output:
 output.setnchannels(1);output.setsampwidth(2);output.setframerate(192000)
 output.writeframes(b''.join(struct.pack('<h',int(8000*math.sin(2*math.pi*440*i/192000))) for i in range(96000)))
subprocess.run([imageio_ffmpeg.get_ffmpeg_exe(),'-v','error','-y','-display_rotation:v:0','180','-i',str(assets/'videos/audio.mp4'),
 '-c','copy',str(video)],check=True)
rows=[{'file':str(p.relative_to(assets)),'bytes':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in [audio,video]]
(assets/'batch-media-manifest.json').write_text(json.dumps({'provenance':'Synthetic 440 Hz PCM; synthetic audio.mp4 remux with 180-degree orientation, no recoding','files':rows},indent=2)+'\n',encoding='utf-8')

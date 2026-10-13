"""Independently decode the nine synthetic Android MP3 fixtures on the host only."""
import argparse
import json
from pathlib import Path
import subprocess
import imageio_ffmpeg

parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('folder',type=Path)
args=parser.parse_args()
rows=[]
for name in ['tone.wav','mono44100.wav','tone.mp3']:
    previous=0
    for bitrate in [64000,128000,256000]:
        path=args.folder/f'{name}-{bitrate}.mp3';data=path.read_bytes()
        assert previous<len(data)<1024*1024
        previous=len(data);pos=0;frames=0
        rate=44100 if name=='mono44100.wav' else 48000
        channels=1 if name=='mono44100.wav' else 2
        while pos<len(data):
            header=int.from_bytes(data[pos:pos+4],'big')
            assert header>>21==0x7ff and (header>>19)&3==3 and (header>>17)&3==1
            actual_rate=[44100,48000,32000][(header>>10)&3]
            actual_bitrate=[0,32,40,48,56,64,80,96,112,128,160,192,224,256,320,0][(header>>12)&15]*1000
            actual_channels=1 if (header>>6)&3==3 else 2
            assert (actual_rate,actual_bitrate,actual_channels)==(rate,bitrate,channels)
            pos+=144*bitrate//rate+((header>>9)&1);frames+=1
        assert pos==len(data) and frames>2
        decoded=subprocess.run([imageio_ffmpeg.get_ffmpeg_exe(),'-v','error','-err_detect','explode','-i',str(path),
            '-vn','-c:a','pcm_s16le','-f','s16le','-'],check=True,capture_output=True).stdout
        assert any(decoded)
        duration=len(decoded)/(rate*channels*2)
        assert abs(duration-3)<0.3
        rows.append({'file':path.name,'bytes':len(data),'sample_rate':rate,'channels':channels,
            'real_cbr_bitrate':bitrate,'decoded_seconds':duration,'passed':True})
report=args.folder.parent/'independent-mp3-decode.json'
report.write_text(json.dumps({'host_only':True,'files':rows,'passed':True},indent=2)+'\n')
print('9 synthetic MP3 files independently decoded; actual CBR, rate, channels and duration verified.')

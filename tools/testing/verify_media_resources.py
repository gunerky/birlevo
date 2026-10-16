"""Independent decode checks for actual emulator-exported images, PDF rasters and video."""
import argparse
import json
from pathlib import Path
import re
import subprocess
from PIL import Image

parser = argparse.ArgumentParser()
parser.add_argument('folder',type=Path)
parser.add_argument('--ffmpeg',type=Path,required=True)
args = parser.parse_args()
root = args.folder.resolve()
outputs=[]
for extension,format_name in [('jpg','JPEG'),('png','PNG'),('webp','WEBP'),('bmp','BMP')]:
    file = root/f'image-2560.{extension}'
    with Image.open(file) as image:
        image.load()
        assert image.format == format_name and image.size == (2560,2560)
        rgb = image.convert('RGB')
        assert rgb.getpixel((200,200))[0] > 240 and rgb.getpixel((2300,200))[2] > 240
        outputs.append({'file':file.name,'size':list(image.size),'format':image.format})
for extension,format_name in [('png','PNG'),('jpg','JPEG'),('webp','WEBP')]:
    file = root/f'pdf-4096.{extension}'
    with Image.open(file) as image:
        image.load()
        assert image.format == format_name and max(image.size) == 4096
        outputs.append({'file':file.name,'size':list(image.size),'format':image.format})
video = root/'video-retry.mp4'
result = subprocess.run([str(args.ffmpeg),'-hide_banner','-xerror','-i',str(video),'-f','null','-'],
                        capture_output=True,text=True,check=True)
description = result.stderr
assert 'Video: h264' in description and 'Audio: aac' in description
dimensions = re.search(r'Video: h264[^\n]*?\b(\d{2,5})x(\d{2,5})\b',description)
assert dimensions and max(int(dimensions[1]),int(dimensions[2])) == 640
outputs.append({'file':video.name,'width':int(dimensions[1]),'height':int(dimensions[2]),'fullDecode':True})
report = {'passed':True,'outputs':outputs}
(root.parent/'independent-check.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
print(json.dumps(report))

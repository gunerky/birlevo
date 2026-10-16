"""Extend existing translated codec copy, keeping source/output scope explicit."""
import json
from pathlib import Path
p=Path(__file__).with_name('catalog.json');c=json.loads(p.read_text(encoding='utf-8'))
key='video_vp9_losses'
if key not in c['keys']:
 c['keys'].append(key)
 for values in c['locales'].values():values.append('')
for locale,values in c['locales'].items():
 source=values[c['keys'].index('video_webm_losses')]
 assert 'VP8' in source,locale
 values[c['keys'].index(key)]=source.replace('VP8','VP9 (P0 · SDR)')
 i=c['keys'].index('files_video_decoders')
 # Idempotent and limited to the codec labels, preserving the locale's own sentences.
 if 'VP9' not in values[i]:values[i]=values[i].replace('VP8','VP8 / VP9 (P0 · SDR)')
p.write_text(json.dumps(c,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

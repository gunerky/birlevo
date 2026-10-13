"""Independent host decode of selected synthetic Android outputs; not an app dependency."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import imageio_ffmpeg

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('folder', type=Path)
args = parser.parse_args()
expected = ['tone.wav.flac', 'mono44100.wav.flac', 'tone.mp3.flac', 'tone.flac.flac', 'tone.opus.ogg', 'mono44100.wav.ogg']
report = []
for name in expected:
    path = args.folder / name
    data = path.read_bytes()
    # These small, explicitly selected original test tones are not arbitrary user files.
    assert 0 < len(data) < 1024 * 1024, name
    decoded = subprocess.run([imageio_ffmpeg.get_ffmpeg_exe(), '-hide_banner', '-loglevel', 'error',
        '-err_detect', 'explode', '-i', str(path), '-vn', '-c:a', 'pcm_s16le', '-f', 's16le', '-'],
        check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE).stdout
    assert decoded, name
    item = {'file': name, 'bytes': len(data), 'decoded_pcm16_bytes': len(decoded), 'status': 'passed'}
    if path.suffix == '.flac':
        assert data[:4] == b'fLaC' and data[4] == 0x80 and int.from_bytes(data[5:8], 'big') == 34
        bits = int.from_bytes(data[18:26], 'big')
        rate, channels, depth, samples = bits >> 44, ((bits >> 41) & 7) + 1, ((bits >> 36) & 31) + 1, bits & ((1 << 36) - 1)
        assert depth == 16 and len(decoded) == samples * channels * 2, name
        assert hashlib.md5(decoded).digest() == data[26:42], name
        item.update(sample_rate=rate, channels=channels, samples=samples, pcm_md5_matches=True)
    else:
        assert data[:5] == b'OggS\0' and b'OpusHead' in data[:256], name
        item['container'] = 'Ogg/Opus'
    report.append(item)
output = args.folder.parent / 'independent-decode.json'
output.write_text(json.dumps({'passed': True, 'host_only': True, 'files': report}, indent=2)+'\n', encoding='utf-8')
print(f'{len(report)} synthetic outputs independently decoded; 4 FLAC PCM digests verified. {output}')

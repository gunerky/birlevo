"""Verify the actual SAF-extracted large WAV independently with bounded host reads."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import wave
from zipfile import ZipFile

parser = argparse.ArgumentParser()
parser.add_argument('folder', type=Path)
parser.add_argument('--ffmpeg', type=Path, required=True)
args = parser.parse_args()
root = args.folder.resolve()


def digest(stream):
    with stream:
        hash_value = hashlib.sha256()
        count = 0
        while True:
            block = stream.read(65536)
            if not block:
                break
            hash_value.update(block)
            count += len(block)
        return count, hash_value.hexdigest()


manifest = json.loads((root / 'capacity-manifest.json').read_text(encoding='utf-8'))
record, = manifest['outputs']
archive = root / 'capacity-large-wav.zip'
assert digest(archive.open('rb'))[1] == record['zipSha256']
entry, = record['entries']
extracted, = record['extracted']
assert entry['name'] == extracted['name'] == '01_large.wav'
file = root / extracted['extracted']
assert file.parent == root and file.name == 'capacity-large-wav--01_large.wav'
expected = (entry['bytes'], entry['sha256'])
assert expected[0] > 128 * 1024 * 1024
assert digest(file.open('rb')) == expected
with ZipFile(archive) as zipped:
    assert zipped.namelist() == [entry['name']]
    assert zipped.testzip() is None
    assert digest(zipped.open(entry['name'])) == expected
with wave.open(str(file)) as wav:
    assert (wav.getframerate(), wav.getnchannels(), wav.getsampwidth()) == (96000, 2, 2)
    duration = wav.getnframes() / wav.getframerate()
    assert 349 < duration < 351
subprocess.run([str(args.ffmpeg), '-v', 'error', '-xerror', '-i', str(file), '-f', 'null', '-'], check=True)
result = {'passed': True, 'bytes': expected[0], 'sha256': expected[1], 'sampleRate': 96000,
          'channels': 2, 'durationSeconds': duration, 'fullDecode': True}
(root / 'independent-check.json').write_text(json.dumps(result, indent=2)+'\n', encoding='utf-8')
print(json.dumps(result))

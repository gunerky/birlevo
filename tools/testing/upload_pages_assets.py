"""Upload only the reviewed static pages. Temporary JWT arrives on stdin, never in arguments/logs."""
import base64
import hashlib
import json
import getpass
import mimetypes
from pathlib import Path
import sys
import urllib.request

token = (getpass.getpass('Temporary upload credential: ') if sys.stdin.isatty() else sys.stdin.readline()).strip()
if not token:
    raise SystemExit('Missing temporary upload credential')
root = Path(__file__).resolve().parents[2] / 'web'
files = [root / p for p in ('index.html', 'privacy/index.html', 'en/privacy/index.html',
    'favicon.ico', 'favicon-16.png', 'favicon-32.png', 'favicon-48.png', 'favicon-192.png', 'app-ads.txt')]
assets = []
manifest = {}
for path in files:
    content = path.read_bytes()
    key = hashlib.sha256(content).hexdigest()[:32]
    manifest['/' + path.relative_to(root).as_posix()] = key
    content_type = 'text/html; charset=utf-8' if path.suffix == '.html' else mimetypes.guess_type(path.name)[0]
    assets.append(dict(key=key, value=base64.b64encode(content).decode(), base64=True,
                       metadata={'contentType': content_type or 'application/octet-stream'}))
request = urllib.request.Request('https://api.cloudflare.com/client/v4/pages/assets/upload',
    data=json.dumps(assets).encode(), headers={'Authorization': 'Bearer ' + token,
    'Content-Type': 'application/json'}, method='POST')
try:
    with urllib.request.urlopen(request, timeout=45) as response:
        result = json.load(response)
    if not result.get('success'):
        raise SystemExit('Asset upload unsuccessful')
    print(json.dumps({'uploaded': len(assets), 'manifest': manifest}))
except urllib.error.HTTPError as error:
    print(json.dumps({'status': error.code, 'message': 'Asset upload failed'}))
    raise SystemExit(1)

"""Compare downloaded public HTML with sources, allowing only Cloudflare's known transforms."""
import hashlib
import json
import argparse
from datetime import date
from pathlib import Path
import re
import urllib.request

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('folder', type=Path)
parser.add_argument('deployment')
parser.add_argument('--base-url', default='https://birlevo.com')
parser.add_argument('--environment', choices=['preview', 'production'], default='production')
parser.add_argument('--fetch', action='store_true', help='Download these public site assets for verification')
args = parser.parse_args()
folder = args.folder
deployment = args.deployment
folder.mkdir(parents=True, exist_ok=True)
base = args.base_url.rstrip('/')
pages = [('index.html','home','/'),('privacy/index.html','privacy','/privacy/'),('en/privacy/index.html','en-privacy','/en/privacy/')]
raw_assets = ['app-ads.txt', 'favicon.ico', 'favicon-16.png', 'favicon-32.png', 'favicon-48.png', 'favicon-192.png']
responses = {}
if args.fetch:
    downloads = [(route, 'live-'+name+'.html') for _, name, route in pages]
    downloads += [('/'+name, 'live-'+name) for name in raw_assets]
    for route, name in downloads:
        request = urllib.request.Request(base+route, headers={'User-Agent':'BirlevoSiteVerification/1.0', 'Cache-Control':'no-cache'})
        with urllib.request.urlopen(request, timeout=30) as response:
            data = response.read()
            responses[route] = {'status': response.status, 'content_type': response.headers.get('Content-Type', ''), 'url': response.url}
        assert responses[route]['status'] == 200, responses[route]
        (folder/name).write_bytes(data)
def decode_email(value):
    data=bytes.fromhex(value)
    return bytes(c ^ data[0] for c in data[1:]).decode()
def normalize(s):
    s=re.sub(r'<script[^>]*(?:cloudflare-static/email-decode|static\.cloudflareinsights\.com)[^>]*>.*?</script>', '', s, flags=re.S)
    s=re.sub(r'<span class="__cf_email__" data-cfemail="([a-f0-9]+)">.*?</span>', lambda m:decode_email(m[1]), s)
    s=re.sub(r'href="/cdn-cgi/l/email-protection#([a-f0-9]+)"',lambda m:'href="mailto:'+decode_email(m[1])+'"',s)
    return [line.strip() for line in s.splitlines() if line.strip()]
checks=[]
for path, name, route in pages:
    expected=Path('web',path).read_text(encoding='utf-8')
    actual=(folder/('live-'+name+'.html')).read_text(encoding='utf-8')
    matches=normalize(expected)==normalize(actual)
    checks.append(dict(url=base+route,content_matches=matches,sha256=hashlib.sha256(actual.encode()).hexdigest()))
for name in raw_assets:
    expected = Path('web',name).read_bytes()
    actual = (folder/('live-'+name)).read_bytes()
    checks.append(dict(url=base+'/'+name,content_matches=actual == expected,sha256=hashlib.sha256(actual).hexdigest()))
    if args.fetch:
        mime = responses['/'+name]['content_type']
        assert mime.startswith('text/plain' if name == 'app-ads.txt' else 'image/'), (name, mime)
assert all(c['content_matches'] for c in checks), checks
report=dict(project='birlevo',deployment=deployment,date=date.today().isoformat(),environment=args.environment,checks=checks,responses=responses,
    normalization='Cloudflare email protection decoded; automatic email decoder and analytics scripts excluded; whitespace-only lines ignored; all other HTML exact.')
(folder/'site-deployment.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
print('Three HTML pages, publisher text and five icons match the reviewed site; blue header and homepage are preserved.')

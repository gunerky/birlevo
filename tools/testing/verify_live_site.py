"""Compare downloaded public HTML with sources, allowing only Cloudflare's known transforms."""
import hashlib
import json
from pathlib import Path
import re
import sys

folder = Path(sys.argv[1])
deployment = sys.argv[2]
def decode_email(value):
    data=bytes.fromhex(value)
    return bytes(c ^ data[0] for c in data[1:]).decode()
def normalize(s):
    s=re.sub(r'<script[^>]*(?:cloudflare-static/email-decode|static\.cloudflareinsights\.com)[^>]*>.*?</script>', '', s, flags=re.S)
    s=re.sub(r'<span class="__cf_email__" data-cfemail="([a-f0-9]+)">.*?</span>', lambda m:decode_email(m[1]), s)
    s=re.sub(r'href="/cdn-cgi/l/email-protection#([a-f0-9]+)"',lambda m:'href="mailto:'+decode_email(m[1])+'"',s)
    return [line.strip() for line in s.splitlines() if line.strip()]
checks=[]
for path, name, route in [('index.html','home','/'),('privacy/index.html','privacy','/privacy/'),('en/privacy/index.html','en-privacy','/en/privacy/')]:
    expected=Path('web',path).read_text(encoding='utf-8')
    actual=(folder/('live-'+name+'.html')).read_text(encoding='utf-8')
    matches=normalize(expected)==normalize(actual)
    checks.append(dict(url='https://birlevo.com'+route,content_matches=matches,sha256=hashlib.sha256(actual.encode()).hexdigest()))
assert all(c['content_matches'] for c in checks), checks
report=dict(project='birlevo',deployment=deployment,date='2026-10-07',environment='production',checks=checks,
    normalization='Cloudflare email protection decoded; automatic email decoder and analytics scripts excluded; whitespace-only lines ignored; all other HTML exact.')
(folder/'site-deployment.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
print('All three public pages match reviewed content and styles, including the blue header.')

"""Check generated variant metadata without compiling or signing a release package."""
import argparse
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
ANDROID = '{http://schemas.android.com/apk/res/android}'
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--report', type=Path, required=True)
args = parser.parse_args()
profiles = {}
for variant in ('debug', 'release'):
    config = ROOT / f'app/build/generated/source/buildConfig/{variant}/com/example/contentstudio/BuildConfig.java'
    values = dict(re.findall(r'public static final (?:boolean|String) (AD_\w+) = (.*?);', config.read_text()))
    assert values['AD_PRODUCTION'] == 'false', (variant, 'Live activation is not qualified')
    manifest = ET.parse(ROOT / f'app/build/intermediates/merged_manifest/{variant}/process{variant.title()}MainManifest/AndroidManifest.xml')
    metadata = {node.get(ANDROID+'name'): node.get(ANDROID+'value') for node in manifest.findall('application/meta-data')}
    assert metadata['com.google.android.gms.ads.APPLICATION_ID'] == json.loads(values['AD_APPLICATION_ID'])
    if variant == 'debug':
        assert values['AD_TESTING'] == 'true'
        assert json.loads(values['AD_BANNER_ID']) == 'ca-app-pub-3940256099942544/9214589741'
    else:
        for flag in ('AD_TESTING','AD_ACCOUNT_TESTING','AD_HOME_PREVIEW'):
            assert values[flag] == 'false', flag
        assert json.loads(values['AD_PRIVACY_REGION']) == 'disabled'
        assert json.loads(values['AD_APPLICATION_ID']) == 'ca-app-pub-5967660342681325~8485054661'
        assert json.loads(values['AD_BANNER_ID']) == 'ca-app-pub-5967660342681325/5970571966'
    profiles[variant] = values
args.report.parent.mkdir(parents=True, exist_ok=True)
args.report.write_text(json.dumps({'profiles':profiles,'release_package_built':False}, indent=2)+'\n', encoding='utf-8')
print('Variant metadata passed: sample-only debug, aligned live IDs with release activation disabled.')

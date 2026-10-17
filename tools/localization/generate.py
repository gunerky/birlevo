"""Generate and check the initial 22-locale Android resources. Translations need language QA."""
import argparse
import json
import re
from pathlib import Path
from xml.etree import ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "app/src/main/res"
CATALOG = json.loads((Path(__file__).parent / "catalog.json").read_text(encoding="utf-8"))


def qualifier(tag):
    if tag == "id":
        return "in"  # Android's legacy resource qualifier; public language tag stays "id".
    if "Hans" in tag or "Hant" in tag:
        return "b+" + tag.replace("-", "+")
    language, _, region = tag.partition("-")
    return language + ("-r" + region if region else "")


def document(root):
    ET.indent(root, space="    ")
    return '<?xml version="1.0" encoding="utf-8"?>\n' + ET.tostring(root, encoding="unicode") + "\n"


def generated_files():
    keys = CATALOG["keys"]
    locales = CATALOG["locales"]
    assert len(locales) == 22 and len(set(keys)) == len(keys)
    kotlin = (ROOT / "app/src/main/java/com/example/contentstudio/ui/AppLocales.kt").read_text(encoding="utf-8")
    assert set(re.findall(r'AppLocale\("([^"]+)"', kotlin)) == set(locales)
    config = ET.Element("locale-config", {"xmlns:android": "http://schemas.android.com/apk/res/android"})
    for tag, values in locales.items():
        assert len(values) == len(keys), (tag, len(values), len(keys))
        resources = ET.Element("resources")
        ET.SubElement(resources, "string", name="app_name").text = "Birlevo"
        for key, value in zip(keys, values):
            assert value.strip(), (tag, key)
            assert re.findall(r"%\d+\$[ds]", value) == re.findall(r"%\d+\$[ds]", locales["en-US"][keys.index(key)]), (tag, key)
            ET.SubElement(resources, "string", name=key).text = value.replace("'", "\\'")
        yield RES / f"values-{qualifier(tag)}/strings.xml", document(resources)
        if tag == "en-US":
            yield RES / "values/strings.xml", document(resources)
        # "in" is Android's legacy Indonesian alias, canonicalized to "id" by Locale.
        ET.SubElement(config, "locale", {"android:name": "in" if tag == "id" else tag})
    yield RES / "xml/locales_config.xml", document(config)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    options = parser.parse_args()
    for path, expected in generated_files():
        if options.check:
            assert path.read_text(encoding="utf-8") == expected, f"Regenerate {path}"
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(expected, encoding="utf-8", newline="\n")
    print("22 locales: complete keys, format placeholders and generated resources verified.")

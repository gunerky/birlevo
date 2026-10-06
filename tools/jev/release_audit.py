"""Manually review selected current development risks and UI samples; never execute Android tests.

Default is offline. Uses the existing bounded client and encrypted external credential.
No source code, user media or raw logs are sent to the API.
"""
import argparse
from collections import Counter
import json
from pathlib import Path
import re
from xml.etree import ElementTree as ET

from client import ReviewError, atomic_json
from profiles import FACTS
from review import ROOT, OUT, isolation_guard, run


SCENARIOS = {
    "original-preservation": "Verify original bytes remain identical after every supported conversion, compression, canceled job and failed export.",
    "copy-consent": "Verify equal or larger image/video results cannot be saved, opened, shared or transferred without consent tied to that exact result. Changing settings invalidates consent.",
    "draft-upgrade": "Open a saved 0.1.0 presentation in current development code and verify text, slide ordering and all image references are preserved. The frozen Play artifact does not contain newer development features.",
    "private-images": "Create two drafts referencing the same private image, duplicate a slide and delete another slide. Remaining drafts must reopen without lost images.",
    "photo-process-death": "Prepare video and photo-PDF sources/settings, remove original-provider files, age owned copies, force-stop app, then relaunch in another PID. Verify trim/quality/edge and all photo IDs/order/edge restore; outputs and copy approvals stay absent; both outputs can be regenerated.",
    "session-failure": "Simulate a failed metadata write while removing/replacing sources and clearing a session. Verify the durable previous checkpoint and its source copies remain until a successful retry; rapid revisions use one atomic writer; corrupt or missing sources do not become false success.",
    "locale-switch": "Switch each of the 22 locales, reopen the app and check localized home, editor, export controls and error messages. User project text must remain unchanged.",
    "device-locale": "Verify unsupported device-language fallback, device-language matching, Chinese scripts, Indonesian id/in alias and selected-language persistence on old and new Android versions.",
    "rtl-layout": "Verify Arabic and Persian long copy and enlarged fonts do not hide Save, consent, Cancel or import controls in narrow and tablet windows.",
    "mp4-codecs": "Current development accepts supported containers with AVC, 8-bit SDR HEVC or VP8 and compatible AAC/Opus/Vorbis/MP3 when the device can decode them. Test HEVC SDR succeeds; HDR, 10-bit HEVC, subtitles and unsupported codecs fail with a reason and no leftover snapshot. Do not claim universal container support.",
    "format-routing": "Development supports text/table TXT/plain Markdown/quoted CSV/TSV with selected UTF-8/PDF outputs, external PDF reading/page tools and still HEIC/HEIF/AVIF and compatible MOV input. DOCX/XLSX readers remain pending. Distinguish preview/input/output operations and device conditions; frozen 0.1.8 does not contain new readers or media inputs.",
    "quoted-tables": "Round-trip CSV/TSV containing quotes, delimiters, multiline cells, empty trailing cells, leading zeros and formula-like literal strings. Verify UTF-8/UTF-16 BOM and explicit legacy encoding; reject malformed/binary/oversized input without partial copies. PDF must retain the last record across pages and never shorten content to meet limits.",
    "document-handoff": "Transfer a selected text section/row into an existing presentation without changing its previous slides, images or theme. Verify CSV/TSV save and sharing MIME/extension match the selected result, and stale picker callbacks cannot overwrite or delete any original source.",
    "corrupt-media": "Verify corrupt JPEG/PNG/WebP, animated WebP and renamed incompatible input fail without partial export or overwritten originals.",
    "unknown-provider": "Verify a document provider with absent size or wrong MIME is inspected using actual bytes and bounded reads; unsupported input must not be accepted based only on extension.",
    "memory-budget": "Run 100-photo PDF generation at the offered 4096 setting on low-memory hardware. Memory failure must be explained without silently reducing final quality. This workload has not been measured on physical hardware.",
    "pdf-text": "Verify multilingual presentation PDF has readable Arabic, Persian, Hindi and CJK and inspect text selection/search separately. Visible glyphs do not establish correct Unicode extraction.",
    "background-export": "Video work currently belongs to the ViewModel and does not survive process death. Verify cancel/background interruptions never produce a false completed result or leaked partial file.",
    "external-share": "After app upgrade, open/share existing output using the unchanged FileProvider authority and read-only URI grants. Reject use of a previous size-consent grant for a new output.",
    "native-symbols": "Verify a release embeds available matching native symbols. A third-party library arriving pre-stripped cannot have missing debug symbols reconstructed from its packaged binary.",
    "ads-readiness": "Before adding an ad SDK, update actual advertising ID, ads declaration, data safety and privacy policy together; verify basic tasks still work when ads are unavailable.",
}


def prepare():
    catalog = json.loads((ROOT / "tools/localization/catalog.json").read_text(encoding="utf-8"))
    keys, locales = catalog["keys"], catalog["locales"]
    source = dict(zip(keys, locales["en-US"]))
    resources = {}
    for path in (ROOT / "app/src/main/res").glob("values*/strings.xml"):
        entries = [(n.attrib["name"], n.text or "") for n in ET.parse(path).getroot() if n.tag == "string"]
        resources[path.parent.name] = {
            "keys": len(entries), "missing": sorted(set(keys) - {k for k, v in entries}),
            "empty": [k for k, v in entries if not v.strip()],
            "duplicates": [k for k, n in Counter(k for k, v in entries).items() if n > 1],
        }
    identical = {tag: [k for k, a, b in zip(keys, locales["en-US"], values) if a == b]
                 for tag, values in locales.items() if tag != "en-US"}
    references = set()
    literal_ui = []
    for path in (ROOT / "app/src/main/java").rglob("*.kt"):
        text = path.read_text(encoding="utf-8")
        references.update(re.findall(r"R\.string\.([a-zA-Z0-9_]+)", text))
        for number, line in enumerate(text.splitlines(), 1):
            if re.search(r'Text\("|contentDescription\s*=\s*"', line):
                literal_ui.append({"file": str(path.relative_to(ROOT)), "line": number, "text": line.strip()})
    structure = {
        "locales": len(locales), "catalog_keys": len(keys), "resources": resources,
        "referenced_keys_missing_from_catalog": sorted(references - set(keys) - {"app_name"}),
        "identical_to_english_candidates": identical,
        "identical_note": "Shared format symbols, units and natural cognates are not missing translations. Manual semantic review required.",
        "literal_ui_candidates": literal_ui,
    }
    translation_cases = []
    # Four safety/session warnings per target locale, plus new-format instructions.
    for tag, values in locales.items():
        if tag == "en-US":
            continue
        target = dict(zip(keys, values))
        for key in ("image_not_smaller", "compression_save_warning", "photo_pdf_session", "webp_losses", "document_losses", "document_preview_hint"):
            translation_cases.append({"id": f"{tag}:{key}", "profile": "translations", "state": {
                "source": source[key], "target": target[key], "locale": tag, "key": key,
            }})
    # Same-looking words are candidates, not pre-labelled failures.
    for tag, key in [("id", "edit_video"), ("de-DE", "theme_warm"), ("fr-FR", "slide_actions"), ("ro", "theme_ocean")]:
        translation_cases.append({"id": f"{tag}:{key}", "profile": "translations", "state": {
            "source": source[key], "target": locales[tag][keys.index(key)], "locale": tag, "key": key,
        }})
    copies = [{"id": key, "profile": "copy", "state": {"text": source[key], "key": key, "facts": FACTS}}
              for key in ["photo_pdf_hint", "photo_pdf_session", "photo_pdf_import_error", "photo_pdf_generate_error",
                          "webp_losses", "png_losses", "image_error", "video_load_error", "document_limits", "document_losses", "document_bad_encoding"]]
    scenarios = [{"id": key, "profile": "tests", "state": {"text": text, "facts": FACTS}} for key, text in SCENARIOS.items()]
    feedback = [
        {"id": "reported-language", "profile": "feedback", "state": {"text": "Some languages appear untranslated. The affected language and screen were not specified."}},
        {"id": "reported-file-open", "profile": "feedback", "state": {"text": "Some files do not open and file type support is too limited. Actual extensions and codec details were not specified."}},
    ]
    return structure, [translation_cases[:30], translation_cases[30:60], translation_cases[60:], copies, scenarios, feedback]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--live", action="store_true")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--continue-from", type=Path, action="append", default=[], help="Previous manual runs; failed items are not retried")
    args = parser.parse_args()
    directory = args.output.resolve()
    if not directory.is_relative_to(OUT.resolve()) or directory == OUT.resolve() or directory.exists():
        parser.error("Use a new folder under output/development/jev.")
    structure, batches = prepare()
    if args.continue_from:
        attempted = set()
        for previous in args.continue_from:
            previous = previous.resolve()
            if not previous.is_relative_to(OUT.resolve()) or previous == OUT.resolve():
                parser.error("Previous reports must be under output/development/jev.")
            for path in previous.glob("batch-*/report.json"):
                report = json.loads(path.read_text(encoding="utf-8"))
                attempted.update(row["id"] for row in report["results"] if row["status"] != "not_evaluated")
        batches = [[case for case in batch if case["id"] not in attempted] for batch in batches]
        batches = [batch for batch in batches if batch]
        structure["continuation"] = {"previous": [str(p.resolve()) for p in args.continue_from], "not_retried_ids": sorted(attempted)}
    atomic_json(directory / "local-audit.json", structure)
    atomic_json(directory / "selected-cases.json", {"items": [case for batch in batches for case in batch]})
    debug_artifact = ROOT / "app/build/outputs/apk/debug/app-debug.apk"
    isolation_guard(ROOT, debug_artifact if debug_artifact.is_file() else None)
    reports = []
    for index, cases in enumerate(batches, 1):
        report = run(cases, args.live, directory / f"batch-{index}", max_requests=30, max_input_bytes=256 * 1024)
        reports.append(report)
        print(f"Batch {index}: {json.dumps(report['totals'])}", flush=True)
        if any(row["status"] == "error" for row in report["results"]):
            print("Stopped after an API error; inspect the local report.", flush=True)
            return 2
    atomic_json(directory / "summary.json", {
        "mode": "live" if args.live else "offline", "items": sum(r["totals"]["items"] for r in reports),
        "totals": {key: sum(r["totals"].get(key, 0) for r in reports)
                   for key in ["api_attempts", "cache_hits", "sent_bytes", "input_tokens", "output_tokens"]},
        "status_counts": dict(Counter(row["status"] for r in reports for row in r["results"])),
        "runtime_unchanged": all(r["runtime_unchanged"] for r in reports),
        "note": "Selected samples and risk classifications only. No Android test execution or full language certification.",
    })
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (ReviewError, OSError, ValueError):
        raise SystemExit("Audit did not complete; inspect local reports and bounded inputs.") from None

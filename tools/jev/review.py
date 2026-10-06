"""Manual development reviews. Default is an offline plan; --live enables API calls."""
from __future__ import annotations

import argparse
from collections import Counter
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import sys
import zipfile

from client import Client, MODEL, ReviewError, atomic_json, canonical, credential_status
from profiles import FACTS, PRIORITY_KEYS, QUESTIONS

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "output" / "development" / "jev"
VERSION = 1
CONFIDENCE = 0.75  # Provisional review threshold, not a measured probability of correctness.
NEEDLES = [b"api.typesafe.ai", b"TYPESAFE_API_KEY", b"typesafe-credential", b"typesafe_sdk", b"@typesafe-ai/sdk"]
PLACEHOLDER = re.compile(r"%(?:\d+\$)?[dsf]|%%")


def isolation_guard(root=ROOT, artifact: Path | None = None) -> dict:
    runtime = root / "app" / "src"
    build_files = [root / "settings.gradle.kts", root / "build.gradle.kts", root / "app" / "build.gradle.kts", root / "app" / "proguard-rules.pro"]
    failures = []
    digest = hashlib.sha256()
    files = sorted(p for p in runtime.rglob("*") if p.is_file()) + [p for p in build_files if p.is_file()]
    for path in files:
        data = path.read_bytes()
        digest.update(str(path.relative_to(root)).encode())
        digest.update(data)
        if any(needle.lower() in data.lower() for needle in NEEDLES):
            failures.append(str(path.relative_to(root)))
        if path in build_files and re.search(rb"typesafe|tools[\\/]+jev|TYPESAFE_API_KEY", data, re.I):
            failures.append(str(path.relative_to(root)))
    if failures:
        raise ReviewError("Development isolation failed in: " + ", ".join(sorted(set(failures))))
    result = {"passed": True, "checked_source_files": len(files), "runtime_source_sha256": digest.hexdigest()}
    if artifact:
        total = 0
        with zipfile.ZipFile(artifact) as archive:
            for info in archive.infolist():
                total += info.file_size
                if info.file_size > 64 * 1024 * 1024 or total > 256 * 1024 * 1024:
                    raise ReviewError("Artifact too large for this bounded inspection.")
                name = info.filename.lower()
                if "tools/jev" in name or "typesafe" in name or name.endswith(".clixml"):
                    raise ReviewError("Development item found in Android artifact.")
                data = archive.read(info)
                if any(needle.lower() in data.lower() for needle in NEEDLES):
                    raise ReviewError("TypeSafe marker found in Android artifact.")
        result["artifact"] = str(artifact)
        result["artifact_sha256"] = hashlib.sha256(artifact.read_bytes()).hexdigest()
        result["checked_artifact_entries"] = len(archive.infolist())
    return result


def redact(text: str) -> tuple[str, int]:
    # Best effort for explicitly selected feedback, not a guarantee of anonymity.
    patterns = [
        (r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}", "[REDACTED_EMAIL]"),
        (r"https?://[^\s]+", "[REDACTED_URL]"),
        (r"(?i)(?:bearer\s+\S+|(?:api[_ -]?key|token|password|secret)\s*[:=]\s*\S+)", "[REDACTED_SECRET]"),
        (r"(?i)[a-z]:[\\/][^\n,;]+", "[REDACTED_PATH]"),
        (r"(?<!\w)(?:\+?\d[ ()-]*){10,15}(?!\w)", "[REDACTED_NUMBER]"),
    ]
    count = 0
    for pattern, replacement in patterns:
        text, changes = re.subn(pattern, replacement, text)
        count += changes
    return text, count


def prechecks(case: dict) -> list[str]:
    state = case["state"]
    flags = []
    for value in state.values():
        if isinstance(value, str) and "\ufffd" in value:
            flags.append("replacement_character")
    if case["profile"] == "translations":
        if Counter(PLACEHOLDER.findall(state["source"])) != Counter(PLACEHOLDER.findall(state["target"])):
            flags.append("placeholder_mismatch")
        if not state["target"].strip():
            flags.append("empty_translation")
    return sorted(set(flags))


def catalog_cases(profile: str, locales: list[str], limit: int) -> list[dict]:
    catalog = json.loads((ROOT / "tools" / "localization" / "catalog.json").read_text(encoding="utf-8"))
    keys = catalog["keys"]
    ordered = [k for k in PRIORITY_KEYS if k in keys] + [k for k in keys if k not in PRIORITY_KEYS]
    for locale in locales:
        if locale not in catalog["locales"] or locale == "en-US":
            raise ReviewError("Choose existing target locales other than en-US.")
    cases = []
    for key in ordered:
        index = keys.index(key)
        source = catalog["locales"]["en-US"][index]
        if profile == "copy":
            cases.append({"id": key, "profile": profile, "state": {"text": source, "key": key, "facts": FACTS}})
        else:
            for locale in locales:
                cases.append({"id": f"{locale}:{key}", "profile": profile, "state": {
                    "source": source, "target": catalog["locales"][locale][index], "locale": locale, "key": key,
                }})
        if len(cases) >= limit:
            break
    return cases[:limit]


def selected_cases(path: Path, profile: str, limit: int) -> list[dict]:
    if path.stat().st_size > 1024 * 1024:
        raise ReviewError("Selected input exceeds 1 MiB; split it into small manual batches.")
    cases = []
    seen = set()
    for line in path.read_text(encoding="utf-8-sig").splitlines():
        if not line.strip():
            continue
        item = json.loads(line)
        if not isinstance(item, dict) or not isinstance(item.get("text"), str) or not item["text"].strip():
            raise ReviewError("Each JSONL item needs a non-empty text field.")
        identifier = str(item.get("id", len(cases) + 1))
        if not re.fullmatch(r"[\w.-]{1,80}", identifier) or identifier in seen:
            raise ReviewError("Use unique, non-personal short IDs.")
        seen.add(identifier)
        if len(item["text"]) > 6000:
            raise ReviewError("Each selected text must be at most 6000 characters.")
        text, changes = redact(item["text"])
        cases.append({"id": identifier, "profile": profile,
                      "state": {"text": text, **({"facts": FACTS} if profile == "tests" else {})},
                      "redactions": changes})
        if len(cases) >= limit:
            break
    if not cases:
        raise ReviewError("No selected items.")
    return cases


def benchmark_cases() -> list[dict]:
    # Hand-labelled synthetic probes. Labels are never included in API state.
    cases = []
    translations = [
        ("tr-kept", "Original files are kept.", "Orijinal dosyalar korunur.", "preserved"),
        ("tr-deleted", "Original files are kept.", "Orijinal dosyalar silinir.", "meaning_changed"),
        ("tr-size", "A smaller file is not guaranteed.", "Dosyanın küçüleceği garanti edilmez.", "preserved"),
        ("tr-guarantee", "A smaller file is not guaranteed.", "Dosya her zaman küçülür.", "meaning_changed"),
    ]
    for identifier, source, target, expected in translations:
        cases.append({"id": identifier, "profile": "translations", "state": {"source": source, "target": target, "locale": "tr-TR"}, "expected": {"meaning": expected}})
    feedback = [
        ("crash", "The app closes every time I open it; I cannot reach the home screen.", "crash", "urgent"),
        ("export", "Saving my generated PDF fails, but I can still edit the presentation.", "export", "normal"),
        ("loss", "My saved presentation reopens with all my last edits missing.", "project", "urgent"),
        ("request", "Please add more presentation themes.", "feature_request", "low"),
        ("language", "The Arabic translation of the Save button is wrong.", "localization", "normal"),
        ("cosmetic", "I would like a rounder shape for the home screen cards.", "interface", "low"),
    ]
    for identifier, text, category, priority in feedback:
        cases.append({"id": identifier, "profile": "feedback", "state": {"text": text}, "expected": {"category": category, "priority": priority}})
    for identifier, text, promise in [
        ("copy-kept", "Your original files are kept.", "supported"),
        ("copy-size", "Every video will become smaller with no quality loss.", "unsupported"),
    ]:
        cases.append({"id": identifier, "profile": "copy", "state": {"text": text, "facts": FACTS}, "expected": {"promise": promise}})
    for identifier, text, risk in [
        ("consent", "Verify that a larger compression result cannot be shared until the user approves that exact copy.", "critical"),
        ("corner", "Verify the static logo card corner radius matches the design.", "cosmetic"),
    ]:
        cases.append({"id": identifier, "profile": "tests", "state": {"text": text, "facts": FACTS}, "expected": {"risk": risk}})
    return cases


def review_needed(profile: str, answers: dict) -> bool:
    for name, answer in answers.items():
        if answer.get("confidence", 1) < CONFIDENCE:
            return True
        if answer.get("choice") in {"uncertain", "meaning_changed", "review", "unsupported", "urgent", "critical"}:
            return True
        if name == "clarity" and answer.get("type") == "score" and answer["score"] < 1.5:
            return True
    return False


def write_report(directory: Path, report: dict) -> None:
    atomic_json(directory / "report.json", report)
    lines = ["# Jev development review", "", f"Mode: **{report['mode']}**. No application files changed.", "",
             "Suggestions only. Confidence is not proof; language and runtime QA remain necessary.", "",
             "| Item | Profile | Status | Typed judgments |", "|---|---|---|---|"]
    for row in report["results"]:
        judgments = "; ".join(f"{name}={answer.get('choice', answer.get('score', answer.get('noul')))}" for name, answer in row.get("response", {}).get("answers", {}).items())
        detail = judgments or ", ".join(row.get("prechecks", [])) or row.get("error", "Not evaluated")
        lines.append(f"| {row['id']} | {row['profile']} | {row['status']} | {detail.replace('|', '/')} |")
    lines += ["", "Run totals: " + json.dumps(report["totals"]), "", "Benchmark: " + json.dumps(report.get("benchmark", {})),
              "", "Only successful responses have known token counts; failed requests may still be billed. Byte/request limits are not a dollar cap.",
              "", "Source copy and submitted states are in plan.json for local review. No automatic fixes, Play submission or user-file upload."]
    text = "\n".join(lines) + "\n"
    temp = directory / "report.md.tmp"
    temp.write_text(text, encoding="utf-8")
    temp.replace(directory / "report.md")


def run(cases: list[dict], live: bool, directory: Path, max_requests: int, max_input_bytes: int) -> dict:
    before = isolation_guard()
    plans = [{**case, "prechecks": prechecks(case), "payload": {
        "model": MODEL, "state": case["state"], "questions": QUESTIONS[case["profile"]],
    }} for case in cases]
    atomic_json(directory / "plan.json", {"version": VERSION, "mode": "live" if live else "offline", "items": plans})
    client = Client(OUT / "cache", max_requests=max_requests, max_input_bytes=max_input_bytes)
    report = {"version": VERSION, "mode": "live" if live else "offline", "created_utc": datetime.now(timezone.utc).isoformat(),
              "isolation": before, "model_requested": MODEL, "confidence_threshold": CONFIDENCE,
              "results": [], "totals": {}, "benchmark": {}}
    halted = None
    matched = judged = 0
    per_profile = {}
    for plan in plans:
        row = {"id": plan["id"], "profile": plan["profile"], "prechecks": plan["prechecks"], "status": "not_evaluated"}
        if plan["prechecks"]:
            row["status"] = "structural_issue"
        elif live and not halted:
            try:
                response, origin = client.evaluate(plan["payload"])
                row.update(response=response, origin=origin, status="review" if review_needed(plan["profile"], response["answers"]) else "suggestion")
                expected = plan.get("expected", {})
                if expected:
                    correct = all(response["answers"][name].get("choice") == value for name, value in expected.items())
                    row["expected"] = expected
                    row["benchmark_match"] = correct
                    judged += 1
                    matched += int(correct)
                    bucket = per_profile.setdefault(plan["profile"], {"evaluated": 0, "matched": 0})
                    bucket["evaluated"] += 1
                    bucket["matched"] += int(correct)
            except ReviewError as error:
                halted = str(error)
                row.update(status="error", error=halted)
        elif halted:
            row.update(status="not_evaluated", error="Run stopped after: " + halted)
        report["results"].append(row)
        report["totals"] = {"items": len(cases), "recorded": len(report["results"]), "api_attempts": client.attempts,
                            "cache_hits": client.cache_hits, "sent_bytes": client.sent_bytes, **client.usage}
        report["benchmark"] = {"evaluated": judged, "matched": matched, "per_profile": per_profile,
                               "note": "Small synthetic probes only; no superiority or 22-language quality claim."}
        write_report(directory, report)  # Preserve partial results if interrupted later.
    after = isolation_guard()
    if before["runtime_source_sha256"] != after["runtime_source_sha256"]:
        raise ReviewError("Application sources changed during review; investigate separately.")
    report["runtime_unchanged"] = True
    write_report(directory, report)
    return report


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("profile", choices=[*QUESTIONS, "benchmark", "status", "guard"])
    parser.add_argument("--live", action="store_true", help="Explicitly send selected texts to the official paid API")
    parser.add_argument("--locales", default="tr-TR,de-DE,fr-FR,ja-JP,ar")
    parser.add_argument("--limit", type=int, default=25)
    parser.add_argument("--input", type=Path, help="Explicitly selected anonymized JSONL for feedback/tests")
    parser.add_argument("--max-requests", type=int, default=30, help="All HTTP attempts including retries; per manual run")
    parser.add_argument("--max-input-bytes", type=int, default=256 * 1024)
    parser.add_argument("--output", type=Path, help="Report directory under output/development/jev only")
    parser.add_argument("--artifact", type=Path, help="Optional existing APK/AAB for offline isolation inspection")
    args = parser.parse_args()
    if not 1 <= args.limit <= 10000 or not 0 <= args.max_requests <= 2000 or not 1 <= args.max_input_bytes <= 16 * 1024 * 1024:
        parser.error("Invalid item/request/byte limits.")
    try:
        if args.profile in {"status", "guard"}:
            result = isolation_guard(artifact=args.artifact)
            if args.profile == "status":
                result["credential"] = credential_status()
            print(json.dumps(result, indent=2))
            return 0
        directory = (args.output or OUT / (datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ") + "-" + args.profile)).resolve()
        if not directory.is_relative_to(OUT.resolve()) or directory == OUT.resolve() or directory == (OUT / "cache").resolve():
            raise ReviewError("Reports must be in a separate folder under output/development/jev.")
        if (directory / "plan.json").exists() or (directory / "report.json").exists():
            raise ReviewError("Report already exists; use a new directory.")
        if args.profile in {"translations", "copy"}:
            cases = catalog_cases(args.profile, args.locales.split(","), args.limit)
        elif args.profile == "benchmark":
            cases = benchmark_cases()[:args.limit]
        else:
            if args.input is None:
                raise ReviewError("Feedback/tests require --input with explicitly selected anonymized JSONL.")
            cases = selected_cases(args.input, args.profile, args.limit)
        report = run(cases, args.live, directory, args.max_requests, args.max_input_bytes)
        print(f"{'LIVE' if args.live else 'OFFLINE'} report: {directory / 'report.md'}")
        print(json.dumps(report["totals"]))
        return 2 if any(row["status"] == "error" for row in report["results"]) else 0
    except (ReviewError, OSError, ValueError, zipfile.BadZipFile):
        # Do not accidentally print remote content, user input or credentials in tracebacks.
        print("Review could not complete. Check the local report, selected inputs, credentials and limits.", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())

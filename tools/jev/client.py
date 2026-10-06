"""Development-only TypeSafe HTTP client. No SDK or Android dependency."""
from __future__ import annotations

import hashlib
import json
import math
import os
from pathlib import Path
import re
import subprocess
import time
import urllib.error
import urllib.request

ENDPOINT = "https://api.typesafe.ai/v1/systemone"
MODEL = "jev-1.13.0"
MAX_PAYLOAD_BYTES = 48 * 1024
MAX_RESPONSE_BYTES = 2 * 1024 * 1024


class ReviewError(Exception):
    """Messages intentionally omit credentials, remote bodies and raw exceptions."""


def credential_path() -> Path | None:
    local = os.environ.get("LOCALAPPDATA")
    return Path(local) / "BirlevoDevelopment" / "typesafe-credential.clixml" if local else None


def credential_status() -> dict:
    path = credential_path()
    return {
        "environment_configured": bool(os.environ.get("TYPESAFE_API_KEY", "").strip()),
        "encrypted_windows_credential_present": bool(path and path.is_file()),
        "validity": "not_tested",
    }


def load_key() -> str:
    key = os.environ.get("TYPESAFE_API_KEY", "").strip()
    if not key:
        path = credential_path()
        if os.name != "nt" or not path or not path.is_file():
            raise ReviewError("Key missing. Run tools/jev/set-key.ps1 in your terminal first.")
        # Fixed code and path: the key is never passed in argv or printed in logs.
        code = (
            "$ErrorActionPreference='Stop';"
            "$p=Join-Path $env:LOCALAPPDATA 'BirlevoDevelopment\\typesafe-credential.clixml';"
            "$c=Import-Clixml -LiteralPath $p;"
            "if($c -isnot [Management.Automation.PSCredential]){exit 2};"
            "[Console]::Out.Write($c.GetNetworkCredential().Password)"
        )
        try:
            result = subprocess.run(
                ["powershell.exe", "-NoProfile", "-NonInteractive", "-Command", code],
                capture_output=True, timeout=15, check=False,
            )
            if result.returncode:
                raise ReviewError("Cannot decrypt the local key with this Windows account.")
            key = result.stdout.decode("utf-8").strip()
        except (OSError, subprocess.TimeoutExpired, UnicodeError):
            raise ReviewError("Could not read the encrypted Windows credential.") from None
    if not 8 <= len(key) <= 4096 or any(ch.isspace() for ch in key):
        raise ReviewError("The configured key has an invalid format.")
    return key


def canonical(value) -> bytes:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")


def atomic_json(path: Path, value) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + ".tmp")
    with temporary.open("w", encoding="utf-8") as stream:
        json.dump(value, stream, ensure_ascii=False, indent=2, allow_nan=False)
        stream.write("\n")
    temporary.replace(path)


def number(value, minimum=0, maximum=1) -> bool:
    return (isinstance(value, (float, int)) and not isinstance(value, bool)
            and math.isfinite(value) and minimum <= value <= maximum)


def validate_response(response: dict, questions: dict) -> dict:
    if not isinstance(response, dict) or not re.fullmatch(r"jev-\d+\.\d+\.\d+", str(response.get("model", ""))):
        raise ReviewError("Invalid model response; no decision accepted.")
    answers = response.get("answers")
    if not isinstance(answers, dict) or set(answers) != set(questions):
        raise ReviewError("Response questions do not match; no decision accepted.")
    cleaned = {}
    for name, question in questions.items():
        answer = answers[name]
        kind = question["type"]
        if not isinstance(answer, dict) or answer.get("type") != kind:
            raise ReviewError("Invalid answer type; no decision accepted.")
        if kind == "noul":
            if not number(answer.get("noul")):
                raise ReviewError("Invalid boolean probability.")
            cleaned[name] = {"type": kind, "noul": answer["noul"]}
            continue
        options = set(question["criteria"]) if kind == "choice" else {str(i) for i in range(len(question["criteria"]))}
        probabilities = answer.get("probabilities")
        if (not isinstance(probabilities, dict) or set(probabilities) != options
                or not all(number(p) for p in probabilities.values())
                or abs(sum(probabilities.values()) - 1) > 0.001
                or not number(answer.get("confidence"))):
            raise ReviewError("Invalid distribution; no decision accepted.")
        cleaned[name] = {"type": kind, "probabilities": probabilities, "confidence": answer["confidence"]}
        if kind == "choice":
            choice = answer.get("choice")
            if not isinstance(choice, str) or choice not in options or probabilities[choice] + 0.001 < max(probabilities.values()):
                raise ReviewError("Invalid selected option; no decision accepted.")
            cleaned[name]["choice"] = choice
        elif kind == "score":
            if not number(answer.get("score"), 0, len(options) - 1):
                raise ReviewError("Invalid score.")
            cleaned[name]["score"] = answer["score"]
        else:
            raise ReviewError("Unsupported question type.")
    usage = response.get("usage")
    if not isinstance(usage, dict):
        raise ReviewError("Invalid usage response.")
    counts = {}
    for name in ("input_tokens", "output_tokens"):
        value = usage.get(name)
        if not isinstance(value, int) or isinstance(value, bool) or value < 0:
            raise ReviewError("Invalid token usage.")
        counts[name] = value
    return {"model": response["model"], "answers": cleaned, "usage": counts}


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        # Never forward the Authorization header to another host.
        return None


def http_transport(payload: bytes, key: str, timeout: int) -> dict:
    request = urllib.request.Request(ENDPOINT, data=payload, method="POST", headers={
        "Authorization": "Bearer " + key,
        "Content-Type": "application/json",
        "User-Agent": "Birlevo-Development-Review/1",
    })
    with urllib.request.build_opener(NoRedirect()).open(request, timeout=timeout) as response:
        body = response.read(MAX_RESPONSE_BYTES + 1)
    if len(body) > MAX_RESPONSE_BYTES:
        raise ReviewError("Oversized response rejected.")
    try:
        return json.loads(body)
    except (ValueError, UnicodeError):
        raise ReviewError("Response was not valid JSON.") from None


class Client:
    def __init__(self, cache: Path, max_requests=30, max_input_bytes=256 * 1024,
                 timeout=30, transport=http_transport, key_loader=load_key, sleeper=time.sleep):
        self.cache = cache
        self.max_requests = max_requests
        self.max_input_bytes = max_input_bytes
        self.timeout = timeout
        self.transport = transport
        self.key_loader = key_loader
        self.sleeper = sleeper
        self.attempts = 0
        self.sent_bytes = 0
        self.cache_hits = 0
        self.usage = {"input_tokens": 0, "output_tokens": 0}
        self.key = None

    def evaluate(self, payload: dict) -> tuple[dict, str]:
        data = canonical(payload)
        if len(data) > MAX_PAYLOAD_BYTES:
            raise ReviewError("Selected text exceeds the per-request byte limit.")
        fingerprint = hashlib.sha256(data).hexdigest()
        path = self.cache / (fingerprint + ".json")
        if path.is_file():
            try:
                record = json.loads(path.read_text(encoding="utf-8"))
                if isinstance(record, dict) and record.get("fingerprint") == fingerprint:
                    response = validate_response(record["response"], payload["questions"])
                    if response["model"] == payload["model"]:
                        self.cache_hits += 1
                        return response, "cache"
            except (ValueError, KeyError, OSError, ReviewError):
                pass  # A stale or damaged cache is never considered a valid answer.
        if self.attempts >= self.max_requests or self.sent_bytes + len(data) > self.max_input_bytes:
            raise ReviewError("Run budget reached; remaining items need another manual run.")
        if self.key is None:
            self.key = self.key_loader()
        for attempt in range(2):
            if self.attempts >= self.max_requests or self.sent_bytes + len(data) > self.max_input_bytes:
                raise ReviewError("Run budget reached; retry was not sent.")
            self.attempts += 1
            self.sent_bytes += len(data)
            started = time.monotonic()
            try:
                response = validate_response(self.transport(data, self.key, self.timeout), payload["questions"])
                if response["model"] != payload["model"]:
                    raise ReviewError("Different model version returned; result rejected.")
                for name in self.usage:
                    self.usage[name] += response["usage"][name]
                atomic_json(path, {"fingerprint": fingerprint, "response": response})
                return response, f"api:{round((time.monotonic() - started) * 1000)}ms"
            except urllib.error.HTTPError as error:
                status = error.code
                if error.fp is not None:
                    error.close()
                if status in (429, 529) and attempt == 0:
                    self.sleeper(2)
                    continue
                message = "API key rejected (401)." if status == 401 else f"API request failed (HTTP {status}); response body omitted."
                raise ReviewError(message) from None
            except (urllib.error.URLError, TimeoutError, OSError):
                # Timeouts may already be billed: no blind automatic retry.
                raise ReviewError("Network request failed; no automatic timeout retry.") from None
        raise ReviewError("API unavailable.")

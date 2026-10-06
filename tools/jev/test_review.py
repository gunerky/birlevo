"""Offline contract checks: no API key, user files or paid requests needed."""
import json
import os
import subprocess
import tempfile
import unittest
from pathlib import Path
import urllib.error
from unittest.mock import patch

from client import Client, MODEL, NoRedirect, ReviewError, canonical, load_key, validate_response
from profiles import QUESTIONS
from review import benchmark_cases, catalog_cases, isolation_guard, prechecks, redact, review_needed, run, selected_cases


def payload():
    return {"model": MODEL, "state": {"text": "The app closes when I open it."}, "questions": QUESTIONS["feedback"]}


def response_for(questions):
    answers = {}
    for name, question in questions.items():
        if question["type"] == "choice":
            options = list(question["criteria"])
            answers[name] = {"type": "choice", "choice": options[0], "confidence": 1.0,
                             "probabilities": {key: float(key == options[0]) for key in options}}
        elif question["type"] == "score":
            answers[name] = {"type": "score", "score": 2, "confidence": 1,
                             "probabilities": {str(i): float(i == 2) for i in range(len(question["criteria"]))}}
    return {"model": MODEL, "answers": answers, "usage": {"input_tokens": 300, "output_tokens": 20}}


class ResponseTests(unittest.TestCase):
    def test_accepts_only_configured_choices(self):
        expected = QUESTIONS["feedback"]
        response = response_for(expected)
        response["irrelevant_secret"] = "never-copy-this"
        self.assertNotIn("irrelevant_secret", validate_response(response, expected))
        response["answers"]["category"]["choice"] = "execute_shell"
        with self.assertRaises(ReviewError):
            validate_response(response, expected)

    def test_rejects_invalid_distribution_and_confidence(self):
        for value in [float("nan"), -0.1, 1.1, True]:
            response = response_for(QUESTIONS["feedback"])
            response["answers"]["category"]["confidence"] = value
            with self.assertRaises(ReviewError):
                validate_response(response, QUESTIONS["feedback"])
        response = response_for(QUESTIONS["feedback"])
        response["answers"]["category"]["probabilities"]["crash"] = 0.5
        with self.assertRaises(ReviewError):
            validate_response(response, QUESTIONS["feedback"])

    def test_missing_answer_or_usage_never_becomes_success(self):
        for field in ["answers", "usage"]:
            response = response_for(QUESTIONS["feedback"])
            del response[field]
            with self.assertRaises(ReviewError):
                validate_response(response, QUESTIONS["feedback"])

    def test_low_confidence_goes_to_review(self):
        answers = response_for(QUESTIONS["feedback"])["answers"]
        answers["category"]["confidence"] = 0.1
        self.assertTrue(review_needed("feedback", answers))


class TransportTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.cache = Path(self.directory.name) / "cache"
        self.addCleanup(self.directory.cleanup)

    def test_repeat_uses_cache_without_loading_key(self):
        calls = []
        def transport(data, key, timeout):
            calls.append(data)
            return response_for(QUESTIONS["feedback"])
        first = Client(self.cache, transport=transport, key_loader=lambda: "fake-key-test")
        first.evaluate(payload())
        second = Client(self.cache, max_requests=0, transport=lambda *args: self.fail("Network forbidden"),
                        key_loader=lambda: self.fail("Key read forbidden"))
        answer, origin = second.evaluate(payload())
        self.assertEqual(origin, "cache")
        self.assertEqual(second.cache_hits, 1)
        self.assertEqual(second.attempts, 0)
        self.assertEqual(len(calls), 1)
        self.assertEqual(answer["model"], MODEL)

    def test_budget_counts_failed_attempts_and_retries(self):
        def limited(*args):
            raise urllib.error.HTTPError("https://api.typesafe.ai", 429, "limited", {}, None)
        client = Client(self.cache, max_requests=1, transport=limited, key_loader=lambda: "fake-key", sleeper=lambda seconds: None)
        with self.assertRaisesRegex(ReviewError, "budget"):
            client.evaluate(payload())
        self.assertEqual(client.attempts, 1)

    def test_single_rate_limit_retry_is_bounded(self):
        calls = []
        def flaky(*args):
            calls.append(1)
            if len(calls) == 1:
                raise urllib.error.HTTPError("https://api.typesafe.ai", 529, "overloaded", {}, None)
            return response_for(QUESTIONS["feedback"])
        client = Client(self.cache, transport=flaky, key_loader=lambda: "fake-key", sleeper=lambda seconds: None)
        client.evaluate(payload())
        self.assertEqual(client.attempts, 2)
        self.assertEqual(client.sent_bytes, len(canonical(payload())) * 2)

    def test_timeout_is_not_retried_or_echoed(self):
        def failing(*args):
            raise urllib.error.URLError("fake-secret-must-not-leak")
        client = Client(self.cache, transport=failing, key_loader=lambda: "fake-key")
        with self.assertRaises(ReviewError) as result:
            client.evaluate(payload())
        self.assertNotIn("fake-secret", str(result.exception))
        self.assertEqual(client.attempts, 1)

    def test_byte_budget_rejects_before_reading_key(self):
        client = Client(self.cache, max_input_bytes=1, key_loader=lambda: self.fail("Key read forbidden"))
        with self.assertRaisesRegex(ReviewError, "budget"):
            client.evaluate(payload())

    def test_corrupt_cache_is_revalidated(self):
        import hashlib
        self.cache.mkdir()
        path = self.cache / (hashlib.sha256(canonical(payload())).hexdigest() + ".json")
        path.write_text("[]", encoding="utf-8")
        client = Client(self.cache, max_requests=0, key_loader=lambda: self.fail("Key read forbidden"))
        with self.assertRaisesRegex(ReviewError, "budget"):
            client.evaluate(payload())

    def test_redirects_are_not_followed_with_authorization(self):
        self.assertIsNone(NoRedirect().redirect_request(None, None, 302, "", {}, "https://unrelated.example"))


class WorkflowTests(unittest.TestCase):
    def test_placeholder_order_may_change_but_types_and_counts_must_match(self):
        case = {"profile": "translations", "state": {"source": "%1$d pages in %2$s", "target": "%2$s: %1$d sayfa"}}
        self.assertEqual(prechecks(case), [])
        case["state"]["target"] = "%2$s: %1$s sayfa"
        self.assertIn("placeholder_mismatch", prechecks(case))

    def test_empty_and_encoding_damage_are_local_findings(self):
        self.assertIn("empty_translation", prechecks({"profile": "translations", "state": {"source": "Save", "target": " "}}))
        self.assertIn("replacement_character", prechecks({"profile": "copy", "state": {"text": "Save\ufffd"}}))

    def test_redacts_common_contact_and_secret_fields(self):
        result, count = redact("email me at test@example.com; token=secret-token https://example.com/path?secret=123")
        self.assertNotIn("test@example.com", result)
        self.assertNotIn("secret-token", result)
        self.assertNotIn("example.com", result)
        self.assertEqual(count, 3)

    def test_offline_run_never_loads_key_or_calls_network(self):
        with tempfile.TemporaryDirectory() as temp:
            cases = benchmark_cases()[:2]
            with patch("client.load_key", side_effect=AssertionError("No credentials")), patch("urllib.request.build_opener", side_effect=AssertionError("No network")):
                report = run(cases, False, Path(temp), 0, 1024)
            self.assertEqual(report["totals"]["api_attempts"], 0)
            self.assertTrue(report["runtime_unchanged"])
            plan = json.loads((Path(temp) / "plan.json").read_text(encoding="utf-8"))
            self.assertNotIn("expected", plan["items"][0]["payload"]["state"])
            self.assertNotIn("expected", plan["items"][0]["payload"]["questions"])

    def test_structural_issue_never_contacts_api_even_live(self):
        case = {"id": "invalid-format", "profile": "translations", "state": {"source": "%1$d pages", "target": "sayfalar"}}
        with tempfile.TemporaryDirectory() as temp, patch("urllib.request.build_opener", side_effect=AssertionError("No network")):
            report = run([case], True, Path(temp), 1, 1024)
        self.assertEqual(report["results"][0]["status"], "structural_issue")
        self.assertEqual(report["totals"]["api_attempts"], 0)

    def test_runtime_integration_is_detected(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            app = root / "app" / "src" / "main"
            app.mkdir(parents=True)
            (app / "Main.kt").write_text('val endpoint = "https://api.typesafe.ai/v1/systemone"')
            with self.assertRaisesRegex(ReviewError, "isolation"):
                isolation_guard(root)

    def test_only_explicit_selected_text_is_loaded(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "selected.jsonl"
            path.write_text(json.dumps({"id": "safe-id", "text": "PDF save failed.", "private_email": "test@example.com", "file_path": "C:/private/file"}))
            cases = selected_cases(path, "feedback", 1)
        self.assertEqual(cases[0]["state"], {"text": "PDF save failed."})

    def test_catalog_is_read_only_and_batch_spans_languages(self):
        cases = catalog_cases("translations", ["tr-TR", "de-DE", "ar"], 9)
        self.assertEqual(len(cases), 9)
        self.assertEqual({case["state"]["locale"] for case in cases}, {"tr-TR", "de-DE", "ar"})
        with self.assertRaises(ReviewError):
            catalog_cases("translations", ["unknown-locale"], 1)

    @unittest.skipUnless(os.name == "nt", "Windows DPAPI credential")
    def test_windows_credential_round_trip_outside_project(self):
        fake = "synthetic-test-only-not-a-real-api-key"
        with tempfile.TemporaryDirectory() as temp:
            environment = dict(os.environ, LOCALAPPDATA=temp, TYPESAFE_API_KEY=fake)
            setter = Path(__file__).with_name("set-key.ps1")
            saved = subprocess.run(["powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File", str(setter), "-FromEnvironment"],
                                   capture_output=True, env=environment, timeout=20)
            self.assertEqual(saved.returncode, 0, "Synthetic credential setup failed; raw output omitted.")
            blob = (Path(temp) / "BirlevoDevelopment" / "typesafe-credential.clixml").read_text(encoding="utf-8-sig")
            self.assertNotIn(fake, blob)
            self.assertNotIn(fake.encode(), saved.stdout)
            with patch.dict(os.environ, {"LOCALAPPDATA": temp, "TYPESAFE_API_KEY": ""}):
                self.assertEqual(load_key(), fake)


if __name__ == "__main__":
    unittest.main()

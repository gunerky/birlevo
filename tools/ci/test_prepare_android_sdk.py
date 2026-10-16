import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import Mock

from prepare_android_sdk import prepare, required_packages


class NativeSdkPreparationTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.packages = [("cmake", "3.22.1")]
        manager = self.root / "cmdline-tools/latest/bin" / ("sdkmanager.bat" if sys.platform == "win32" else "sdkmanager")
        manager.parent.mkdir(parents=True)
        manager.touch()
        self.sleep = Mock()

    def install(self, version="3.22.1"):
        directory = self.root / "cmake/3.22.1"
        (directory / "bin").mkdir(parents=True, exist_ok=True)
        (directory / "source.properties").write_text(f"Pkg.Revision = {version}\n", encoding="utf-8")
        binary = directory / "bin" / ("cmake.exe" if sys.platform == "win32" else "cmake")
        binary.touch()
        os.chmod(binary, 0o755)

    def test_verified_warm_sdk_does_not_access_network(self):
        self.install()
        run = Mock()
        prepare(self.root, self.packages, run, self.sleep)
        run.assert_not_called()

    def test_transient_failure_retries_and_verifies_installed_tools(self):
        def invoke(*args, **kwargs):
            if run.call_count == 2:
                self.install()
            return subprocess.CompletedProcess(args, 0 if run.call_count == 2 else 1)
        run = Mock(side_effect=invoke)
        prepare(self.root, self.packages, run, self.sleep)
        self.assertEqual(run.call_count, 2)
        self.sleep.assert_called_once_with(5)
        self.assertIn("cmake;3.22.1", run.call_args.args[0])

    def test_success_exit_with_wrong_version_is_not_accepted(self):
        self.install("3.18.1")
        run = Mock(return_value=subprocess.CompletedProcess([], 0))
        with self.assertRaisesRegex(RuntimeError, "after 3 attempts"):
            prepare(self.root, self.packages, run, self.sleep)
        self.assertEqual(run.call_count, 3)

    def test_timeouts_are_bounded(self):
        run = Mock(side_effect=subprocess.TimeoutExpired("sdkmanager", 300))
        with self.assertRaisesRegex(RuntimeError, "after 3 attempts"):
            prepare(self.root, self.packages, run, self.sleep)
        self.assertEqual(run.call_count, 3)

    def test_versions_come_from_actual_gradle_configuration(self):
        build = Path(__file__).resolve().parents[2] / "app/build.gradle.kts"
        self.assertEqual(required_packages(build), [("ndk", "27.1.12297006"), ("cmake", "3.22.1")])


if __name__ == "__main__":
    unittest.main()

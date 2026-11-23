import json
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

from diagnostics import IOS_DEBUG_BUNDLE, ios_kotlin_failure, summarize_ios_crash


class IosCrashSummaryTest(unittest.TestCase):
    device = "01A111AA-2222-3333-4444-ABCDE1234567"
    image = "98765432-1111-2222-3333-223344556677"

    def make_report(self, bundle=IOS_DEBUG_BUNDLE, device=None):
        device = self.device if device is None else device
        header = {"bundleID": bundle, "bug_type": "309", "platform": 7}
        report = {
            "bundleInfo": {"CFBundleIdentifier": bundle},
            "procPath": f"/CoreSimulator/Devices/{device}/data/Parlor.app/Parlor",
            "captureTime": "2026-11-23 22:25:31.987 +0000",
            "exception": {"type": "EXC_CRASH", "signal": "SIGABRT", "message": "Private details"},
            "termination": {"namespace": "SIGNAL", "code": 6, "indicator": "Private process data"},
            "asi": {"role": ["Private role"]},
            "faultingThread": 0,
            "threads": [{"frames": [{"imageIndex": 0, "imageOffset": 1040, "symbol": "Private symbol"}]}],
            "lastExceptionBacktrace": [{"imageIndex": 0, "imageOffset": 800, "symbol": "Private symbol"}],
            "usedImages": [{"uuid": self.image, "path": "Private path"}],
        }
        return json.dumps(header) + "\n" + json.dumps(report)

    def test_retains_only_debug_app_termination_and_image_offsets(self):
        self.assertEqual({
            "capture_utc": "2026-11-23T22:25:31.987000+00:00",
            "exception_type": "EXC_CRASH",
            "exception_signal": "SIGABRT",
            "termination_namespace": "SIGNAL",
            "termination_code": 6,
            "faulting_frames": [{"image_uuid": self.image, "image_offset": 1040}],
            "exception_frames": [{"image_uuid": self.image, "image_offset": 800}],
        }, summarize_ios_crash(self.make_report(), self.device))

    def test_rejects_other_apps_and_guests(self):
        self.assertIsNone(summarize_ios_crash(self.make_report(bundle="me.unrelated.app"), self.device))
        self.assertIsNone(summarize_ios_crash(self.make_report(device=self.image), self.device))

    def test_process_output_keeps_only_the_kotlin_type_and_parlor_symbols(self):
        with TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "stderr").write_text(
                "Uncaught Kotlin exception: kotlin.IllegalStateException: private message\n"
                "    at ComposeApp 0x100 kfun:com.parlor.example.Flow#advance(kotlin.String) + 48\n"
                "    at ComposeApp 0x101 kfun:kotlinx.coroutines.AbstractCoroutine#resumeWith\n"
                "room-or-role data\n"
            )
            self.assertEqual([{"exception_class": "kotlin.IllegalStateException", "stream": "stderr",
                               "app_symbols": ["com.parlor.example.Flow#advance"]}], ios_kotlin_failure(root))

    def test_reads_the_split_kotlin_native_exception_output(self):
        with TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "stdout").write_text("Uncaught Kotlin exception: ")
            (root / "stderr").write_text(
                "kotlin.IndexOutOfBoundsException: private message\n"
                "    at ComposeApp kfun:com.parlor.example.Flow#advance\n"
            )
            self.assertEqual([{"exception_class": "kotlin.IndexOutOfBoundsException", "stream": "stderr",
                               "app_symbols": ["com.parlor.example.Flow#advance"]}], ios_kotlin_failure(root))

import json
import unittest

from diagnostics import IOS_DEBUG_BUNDLE, summarize_ios_crash


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
        }, summarize_ios_crash(self.make_report(), self.device))

    def test_rejects_other_apps_and_guests(self):
        self.assertIsNone(summarize_ios_crash(self.make_report(bundle="me.unrelated.app"), self.device))
        self.assertIsNone(summarize_ios_crash(self.make_report(device=self.image), self.device))

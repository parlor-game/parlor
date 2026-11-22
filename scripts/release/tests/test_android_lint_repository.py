import contextlib
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from scripts.ci import android_lint_repository as subject

MASTER = b'<metadata><androidx.navigation3/><other/></metadata>'
GROUP = b'<androidx.navigation3><navigation3-runtime versions="1.0.0,1.0.1,1.2.0,1.3.0-alpha01"/></androidx.navigation3>'
CENTRAL_PATH = "app/cash/turbine/turbine/maven-metadata.xml"
CENTRAL = (b'<metadata><groupId>app.cash.turbine</groupId><artifactId>turbine</artifactId>'
           b'<versioning><versions><version>1.2.0</version><version>1.2.1</version></versions></versioning></metadata>')
URL_RESPONSES = {
    subject.GOOGLE_MAVEN + subject.GOOGLE_INDEXES[0]: MASTER,
    subject.GOOGLE_MAVEN + subject.GOOGLE_INDEXES[1]: GROUP,
    subject.MAVEN_CENTRAL + CENTRAL_PATH: CENTRAL,
}


class AndroidLintRepositoryTest(unittest.TestCase):
    def test_live_index_retains_current_version_and_requires_a_real_stable_update(self):
        self.assertEqual("1.2.0", subject.validate(MASTER, GROUP, "1.0.0"))
        with self.assertRaises(ValueError):
            subject.validate(MASTER, GROUP, "1.2.0")

    def test_bad_missing_duplicate_or_unsafe_indexes_fail_closed(self):
        fixtures = [
            (b'<metadata/>', GROUP),
            (b'<metadata><androidx.navigation3/><androidx.navigation3/></metadata>', GROUP),
            (MASTER, b'<wrong/>'),
            (MASTER, b'<androidx.navigation3/>'),
            (MASTER, GROUP.replace(b'1.0.0,', b'')),
            (MASTER, GROUP.replace(b'1.0.1', b'1.0.0')),
            (MASTER, GROUP.replace(b'1.0.1', b'BAD')),
            (MASTER, b'<!DOCTYPE x [<!ENTITY x "bad">]>' + GROUP),
            (b'x' * (subject.MAX_BYTES + 1), GROUP),
        ]
        for master, group in fixtures:
            with self.subTest(master=master[:50], group=group[:50]), self.assertRaises(ValueError):
                subject.validate(master, group, "1.0.0")

    def test_prepare_writes_only_live_inputs_and_retains_a_separate_receipt(self):
        with tempfile.TemporaryDirectory() as name:
            root = self.fixture(Path(name))
            calls = []
            def download(url):
                calls.append(url)
                return URL_RESPONSES[url]
            with contextlib.redirect_stdout(io.StringIO()):
                result = subject.prepare(root, download)
            self.assertCountEqual(URL_RESPONSES, calls)
            self.assertEqual("PASS_LIVE_REPOSITORY_INPUTS_NOT_LINT_QUALIFICATION", result["result"])
            inputs = root / "composeApp/build/lint-repository-inputs"
            self.assertEqual(GROUP, (inputs / "maven.google" / subject.GOOGLE_INDEXES[1]).read_bytes())
            self.assertEqual(CENTRAL, (inputs / (subject.CENTRAL_CACHE + CENTRAL_PATH)).read_bytes())
            self.assertEqual(
                [f"maven.google/{index}" for index in subject.GOOGLE_INDEXES] + [subject.CENTRAL_CACHE + CENTRAL_PATH],
                (inputs / "cache-files.txt").read_text().splitlines(),
            )
            reports = list((root / "composeApp/build/reports").glob("lint-repository-inputs-*.json"))
            self.assertEqual(1, len(reports))
            self.assertEqual(result, json.loads(reports[0].read_text()))
            self.assertFalse((root / "composeApp/build/reports/lint-results-release.xml").exists())

    def test_failed_fetch_or_validation_cannot_create_inputs_or_a_pass_receipt(self):
        with tempfile.TemporaryDirectory() as name:
            root = self.fixture(Path(name))
            with self.assertRaises(OSError):
                subject.prepare(root, lambda _: self.failed())
            self.assertFalse((root / "composeApp/build").exists())
            with self.assertRaises(ValueError):
                subject.prepare(root, lambda _: MASTER)
            self.assertFalse((root / "composeApp/build").exists())

    def test_wrong_central_coordinate_or_missing_reviewed_version_fails_closed(self):
        central_url = subject.MAVEN_CENTRAL + CENTRAL_PATH
        for wrong_data in (
            CENTRAL.replace(b"app.cash.turbine", b"other.group"),
            CENTRAL.replace(b"<artifactId>turbine", b"<artifactId>other"),
            CENTRAL.replace(b"<version>1.2.0", b"<version>1.2.2"),
        ):
            with self.subTest(data=wrong_data), tempfile.TemporaryDirectory() as name:
                root = self.fixture(Path(name))
                with self.assertRaises(ValueError):
                    subject.prepare(root, {**URL_RESPONSES, central_url: wrong_data}.__getitem__)
                self.assertFalse((root / "composeApp/build").exists())

    def test_symlinked_target_or_unsupported_pin_is_rejected(self):
        with tempfile.TemporaryDirectory() as name:
            root = self.fixture(Path(name))
            (root / "composeApp").mkdir()
            (root / "composeApp/build").symlink_to(root / "gradle", target_is_directory=True)
            with self.assertRaises(ValueError):
                subject.prepare(root, URL_RESPONSES.__getitem__)
            (root / "gradle/libs.versions.toml").write_text('[versions]\nnavigation3-runtime="1.0.0"\nagp="9.0.0"\n')
            with self.assertRaises(ValueError):
                subject.prepare(root, lambda _: self.failed())

    def test_alternate_encoding_versions_section_and_receipt_symlinks_fail_closed(self):
        with self.assertRaises(ValueError):
            subject.document(('<?xml version="1.0" encoding="utf-16"?>'
                              '<!DOCTYPE x [<!ENTITY x "bad">]><metadata/>').encode("utf-16"))
        with tempfile.TemporaryDirectory() as name:
            root = self.fixture(Path(name))
            catalog = root / "gradle/libs.versions.toml"
            original = catalog.read_text()
            for malformed in (original.replace("[versions]", "[libraries]"), original + original):
                catalog.write_text(malformed)
                with self.assertRaises(ValueError):
                    subject.prepare(root, lambda _: self.failed())
            catalog.write_text(original)
            build = root / "composeApp/build"
            build.mkdir(parents=True)
            (build / "reports").symlink_to(root / "gradle", target_is_directory=True)
            with self.assertRaises(ValueError):
                subject.prepare(root, URL_RESPONSES.__getitem__)
            self.assertFalse(list((root / "gradle").glob("lint-repository-inputs-*.json")))

    def test_fetch_is_single_bounded_official_request_without_redirect_or_retry(self):
        class Response:
            status = 200
            def __init__(self, url, raw=MASTER):
                self.url, self.raw = url, raw
            def __enter__(self):
                return self
            def __exit__(self, *args):
                pass
            def geturl(self):
                return self.url
            def read(self, count):
                self.count = count
                return self.raw
        url = subject.GOOGLE_MAVEN + subject.GOOGLE_INDEXES[0]
        response = Response(url)
        with patch.object(subject.urllib.request, "urlopen", return_value=response) as call:
            self.assertEqual(MASTER, subject.fetch(url))
            call.assert_called_once_with(url, timeout=20)
            self.assertEqual(subject.MAX_BYTES + 1, response.count)
        for invalid in (Response("https://example.com/index"), Response(url, b''), Response(url, b'x' * (subject.MAX_BYTES + 1))):
            with patch.object(subject.urllib.request, "urlopen", return_value=invalid), self.assertRaises(ValueError):
                subject.fetch(url)

    def fixture(self, root):
        (root / "gradle").mkdir()
        (root / "config").mkdir()
        (root / subject.INVENTORY).write_text(
            "DependencyUpdate|gradle/libs.versions.toml|A newer version of app.cash.turbine:turbine than 1.2.0\n"
            "DependencyUpdate|gradle/libs.versions.toml|A newer version of androidx.activity:activity-compose than 1.9.3\n"
            "DependencyUpdate|gradle/libs.versions.toml|A newer version of org.jetbrains.compose than 1.10.3\n"
        )
        (root / "gradle/libs.versions.toml").write_text('[versions]\nnavigation3-runtime="1.0.0"\nagp="8.13.2"\n')
        (root / "gradle.properties").write_text('android.experimental.lint.version=9.1.1\n')
        return root

    def failed(self):
        raise OSError("Observed network failure")


if __name__ == "__main__":
    unittest.main()

"""Regression guards for the required Android check and scoped job retries."""
import json
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import patch

from scripts.ci import android_release_gate as gate
from scripts.release import workflow_contract as contract


class AndroidReleaseGateTest(unittest.TestCase):
    def setUp(self):
        temp = TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.root = Path(temp.name)
        self.head, self.tree = "1" * 40, "2" * 40
        self.run = "70"
        self.receipts = {}
        for index, lane in enumerate(gate.LANES, 1):
            self.receipts[lane] = dict(lane=lane, source_sha=self.head, source_tree=self.tree,
                                      run_id=self.run, run_attempt="1", evidence_id=str(index),
                                      cleanup_id=str(index + 10), evidence_sha256="3" * 64,
                                      cleanup_sha256="4" * 64)
            self.write(self.receipts[lane])

    def write(self, receipt):
        art = self.root / f"android-lane-certificate-{self.run}-{receipt['run_attempt']}-{receipt['lane']}"
        art.mkdir(exist_ok=True)
        (art / "lane.json").write_text(json.dumps(receipt))

    def aggregate(self, result="success", attempt="1"):
        return gate.aggregate(self.root, result, self.head, self.tree, self.run, attempt)

    def test_accepts_three_current_certificates(self):
        self.assertEqual(self.aggregate()["lanes"], self.receipts)

    def test_only_changed_lane_is_replaced_on_same_run_retry(self):
        lane = "installed-runtime"
        newer = dict(self.receipts[lane], run_attempt="2", evidence_id="100", cleanup_id="101")
        self.write(newer)
        with self.assertRaisesRegex(RuntimeError, "foreign"):
            self.aggregate()
        self.assertEqual(self.aggregate(attempt="2")["lanes"], self.receipts | {lane: newer})
        for result in ("failure", "cancelled", "skipped"):
            with self.subTest(result=result), self.assertRaisesRegex(RuntimeError, "did not pass"):
                self.aggregate(result, attempt="2")

    def test_rejects_foreign_source_or_reused_evidence(self):
        receipt = self.receipts["process-restart"]
        for changes in (dict(source_sha="5" * 40), dict(source_tree="6" * 40), dict(run_id="71"),
                        dict(evidence_id=self.receipts["installed-runtime"]["cleanup_id"]),
                        dict(cleanup_sha256="incomplete")):
            self.write(dict(receipt, **changes))
            with self.subTest(changes=changes), self.assertRaises(RuntimeError):
                self.aggregate()
        self.write(receipt)

    def test_missing_or_redirected_receipt_cannot_pass(self):
        art = self.root / "android-lane-certificate-70-1-process-restart" / "lane.json"
        art.unlink()
        with self.assertRaises(RuntimeError):
            self.aggregate()
        art.symlink_to(self.root / "android-lane-certificate-70-1-installed-runtime" / "lane.json")
        with self.assertRaises(RuntimeError):
            self.aggregate()

    def test_lane_requires_exact_source_successful_cleanup_and_two_uploads(self):
        prefix = self.root / "producer"
        task = dict(GITHUB_JOB="desktop-android", GITHUB_RUN_ID=self.run, GITHUB_RUN_ATTEMPT="1")
        source = dict(root=str(self.root), head=self.head, tree=self.tree, outputs=["build"])
        reports = dict(outcome="success", artifact_id="1", artifact_digest="3" * 64)
        cleanup_upload = dict(outcome="success", artifact_id="11", artifact_digest="4" * 64)
        Path(str(prefix) + "-ownership.json").write_text(json.dumps(source | {"task": task}))
        path = Path(str(prefix) + "-cleanup.json")
        receipt = dict(task=task, result="PASS", preparation_outcome="success", errors=[], retained=[],
                       gradle_stop={"exit_code": 0}, upload=reports,
                       android_emulator_absence=dict(result="PASS", source=dict(head=self.head, tree=self.tree)))
        path.write_text(json.dumps(receipt))
        lane = "common-and-package"
        with patch.object(gate.hygiene, "source_identity", return_value=source), patch.object(gate.hygiene, "git", return_value=""):
            self.assertEqual(gate.lane_identity(self.root, prefix, task, lane, cleanup_upload), self.receipts[lane])
            with self.assertRaisesRegex(RuntimeError, "was not retained"):
                gate.lane_identity(self.root, prefix, task, lane, dict(cleanup_upload, outcome="failure"))
            path.write_text(json.dumps(dict(receipt, result="FAIL")))
            with self.assertRaisesRegex(RuntimeError, "not qualified"):
                gate.lane_identity(self.root, prefix, task, lane, cleanup_upload)

    def test_cancelled_individual_lane_overrides_old_successful_certificates(self):
        def job(lane, **changes):
            return dict(dict(name=f"Android verification ({lane})", run_id=int(self.run),
                             run_attempt=2, status="completed", conclusion="success"), **changes)

        jobs = [job(lane) for lane in gate.LANES]
        gate.verify_latest_jobs(dict(total_count=3, jobs=jobs), self.run, "2")
        for substitute in (job("process-restart", conclusion="cancelled"), job("process-restart", run_attempt=1),
                           job("process-restart", run_id=71), job("installed-runtime"), job("unreviewed")):
            with self.subTest(substitute=substitute), self.assertRaises(RuntimeError):
                gate.verify_latest_jobs(dict(total_count=3, jobs=jobs[:2] + [substitute]), self.run, "2")
        with self.assertRaises(RuntimeError):
            gate.verify_latest_jobs(dict(total_count=4, jobs=jobs), self.run, "2")

    def test_join_cannot_drop_a_lane_or_relax_failure_and_digest_guards(self):
        workflow = (contract.WORKFLOWS / "production-verification.yml").read_text()
        contract.verify_android_split(workflow)
        for before, after in ((',"installed-runtime"', ""),
                              ("needs: [desktop-android]", "needs: []"),
                              ("digest-mismatch: error", "digest-mismatch: warn"),
                              ("= success", "!= skipped")):
            broken = workflow.replace(before, after, 1)
            self.assertNotEqual(broken, workflow)
            with self.subTest(before=before), self.assertRaisesRegex(RuntimeError, "verification scope"):
                contract.verify_android_split(broken)

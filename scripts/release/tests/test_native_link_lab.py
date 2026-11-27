"""Guard the lab against counting a plan, cached output or another target as a link."""
from copy import deepcopy
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

from scripts.ci.native_link_lab import (
    KLIB_TASK, MATCHED_FIELDS, TASK, compare_reports, execution_evidence, kotlin_library_evidence, task_cache_evidence,
)


class NativeLinkLabTest(unittest.TestCase):
    def test_plan_records_the_single_selected_release_task(self):
        report = execution_evidence(f"{TASK} SKIPPED\nBUILD SUCCESSFUL\n", "plan")
        self.assertEqual(report["mode"], "plan")
        self.assertEqual(report["native_phase_ms"], {})

    def test_link_records_native_phases_and_task_cache_outcomes(self):
        log = (
            "> Task :other:compile UP-TO-DATE\n"
            f"> Task {TASK}\n"
            "Arguments = [\n\t-opt\n]\n"
            "RemoveRedundantCallsToStaticInitializersPhase: 1501692 msec\n"
            "Linker: 4088 msec\nCompiler: 2855198 msec\n"
        )
        report = execution_evidence(log, "link")
        self.assertEqual(report["native_phase_ms"]["Linker"], 4088)
        self.assertEqual(report["native_phase_ms"]["Compiler"], 2855198)
        self.assertEqual(report["gradle_task_outcomes"]["UP-TO-DATE"], 1)

    def test_skipped_cached_missing_or_extra_release_links_are_rejected(self):
        for log in (
            "",
            f"{TASK} SKIPPED\n",
            f"> Task {TASK} FROM-CACHE\n",
            f"> Task {TASK}\n",
            f"> Task {TASK}\nCompiler: 50 msec\nLinker: 5 msec\n",
            f"> Task :composeApp:linkReleaseFrameworkIosArm64\n> Task {TASK}\nCompiler: 50 msec\nLinker: 5 msec\n",
        ):
            with self.subTest(log=log), self.assertRaises(RuntimeError):
                execution_evidence(log, "link")


    def test_comparison_requires_matching_conditions_and_reports_signed_deltas(self):
        baseline = dict.fromkeys(MATCHED_FIELDS, "same")
        baseline.update(
            schema=1, result="PASS", role="baseline", run_id="1", run_attempt="1",
            source_sha="a" * 40, mode="link", gradle_wall_seconds=80,
            native_phase_ms={"Compiler": 65_000}, dependency_cache_step_window_seconds=10,
        )
        candidate = deepcopy(baseline)
        candidate.update(
            role="candidate", source_sha="b" * 40, gradle_wall_seconds=60,
            native_phase_ms={"Compiler": 45_000},
        )
        report = compare_reports(baseline, candidate)
        self.assertEqual(report["gradle_delta_seconds"], -20)
        self.assertEqual(report["native_phase_delta_ms"], {"Compiler": -20_000})
        for field in ("run_attempt", "mode", "jdk", "dependency_cache_match", "logical_cpus"):
            with self.subTest(field=field), self.assertRaises(RuntimeError):
                compare_reports(baseline, {**candidate, field: "other"})

    def test_cache_probe_requires_real_store_and_restore_of_the_library(self):
        key = "a" * 32
        stored = f"> Task {KLIB_TASK}\nStored cache entry for task '{KLIB_TASK}' with cache key {key}\n"
        restored = f"> Task {KLIB_TASK} FROM-CACHE\nLoaded cache entry for task '{KLIB_TASK}' with cache key {key}\n"
        self.assertEqual(task_cache_evidence(stored, "baseline")["task_cache"], {"key": key, "outcome": "stored"})
        self.assertEqual(task_cache_evidence(restored, "candidate")["task_cache"], {"key": key, "outcome": "loaded"})
        for wrong in (restored.replace("FROM-CACHE", "UP-TO-DATE"), stored, restored + f"> Task {TASK}\n"):
            with self.subTest(log=wrong), self.assertRaises(RuntimeError):
                task_cache_evidence(wrong, "candidate")

    def test_cache_probe_comparison_rejects_different_source_key_or_library(self):
        stored = dict.fromkeys(MATCHED_FIELDS, "same")
        stored.update(
            schema=1, result="PASS", role="baseline", run_id="1", run_attempt="1",
            source_sha="1" * 40, source_tree="2" * 40, mode="cache-probe", gradle_wall_seconds=80,
            native_phase_ms={}, dependency_cache_step_window_seconds=10, klib={"bytes": 10, "sha256": "3" * 64},
            task_cache={"key": "a" * 32, "outcome": "stored"},
        )
        restored = {**stored, "role": "candidate", "gradle_wall_seconds": 20, "task_cache_transfer_seconds": 2,
                    "task_cache": {"key": "a" * 32, "outcome": "loaded"}}
        comparison = compare_reports(stored, restored)
        self.assertEqual(comparison["gradle_delta_seconds"], -60)
        self.assertEqual(comparison["task_cache_transfer_seconds"], 2)
        for field in ("source_sha", "source_tree", "task_cache", "klib"):
            different = {"key": "b" * 32, "outcome": "loaded"} if field == "task_cache" else "different"
            with self.subTest(field=field), self.assertRaises(RuntimeError):
                compare_reports(stored, {**restored, field: different})

    def test_library_digest_ignores_checkout_paths_and_includes_each_file(self):
        with TemporaryDirectory() as temp:
            for role in ("baseline", "candidate"):
                library = Path(temp, role, "core", "default")
                library.mkdir(parents=True)
                (library / "manifest").write_text("native_targets=ios_simulator_arm64\n")
                (library / "ir").write_bytes(b"library IR")
            baseline = kotlin_library_evidence(Path(temp, "baseline"))
            candidate = Path(temp, "candidate")
            self.assertEqual(kotlin_library_evidence(candidate), baseline)
            (candidate / "core/default/ir").write_bytes(b"updated IR")
            self.assertNotEqual(kotlin_library_evidence(candidate), baseline)
            (candidate / "core/default/manifest").write_text("native_targets=ios_arm64\n")
            with self.assertRaises(RuntimeError):
                kotlin_library_evidence(candidate)

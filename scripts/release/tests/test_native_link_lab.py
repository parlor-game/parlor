"""Guard the lab against counting a plan, cached output or another target as a link."""
from copy import deepcopy
import unittest

from scripts.ci.native_link_lab import MATCHED_FIELDS, TASK, compare_reports, execution_evidence


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

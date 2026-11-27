"""Guard the lab against counting a plan, cached output or another target as a link."""
import unittest

from scripts.ci.native_link_lab import TASK, execution_evidence


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

"""Execute the real wrapper with synthetic tools, not an Android runtime."""
from __future__ import annotations

import json
import os
from pathlib import Path
import subprocess
import sys
from tempfile import TemporaryDirectory
import textwrap
import unittest


ROOT = Path(__file__).resolve().parents[3]
SCRIPT = "scripts/android/run_release_managed_device_smoke.sh"
LINUX_GPU = "-Pandroid.testoptions.manageddevices.emulator.gpu=lavapipe"
OTHER_GPU = "-Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader"
RELEASE = "productionAndroidRuntimeCheck"
NAVIGATION = "productionAndroidNavigationRuntimeCheck"
ASSEMBLE = ":composeApp:assembleDebug"
SETUP = ":composeApp:pixel2Api34Setup"


class AndroidManagedGpuTest(unittest.TestCase):
    def test_both_runtime_lanes_must_keep_private_avd_capacity_policy(self) -> None:
        from unittest.mock import patch
        from scripts.release import workflow_contract as contract
        original = (ROOT / SCRIPT).read_text()
        for source in (original.replace('    --isolated-avd \\\n', '', 1),
                       original.replace('    --isolated-avd \\\n', '')):
            with patch.object(Path, 'read_text', return_value=source), self.assertRaises(RuntimeError):
                contract.verify_android_runtime_script()
        contract.verify_android_runtime_script()

    def run_wrapper(
        self, failed_task: str = "", host_kernel: str = "Linux", suite: str | None = None,
    ) -> tuple[int, list[list[str]]]:
        with TemporaryDirectory(prefix="parlor-gpu-shell-control-") as temporary:
            root = Path(temporary)
            script = root / SCRIPT
            script.parent.mkdir(parents=True)
            script.write_bytes((ROOT / SCRIPT).read_bytes())
            tools = root / "tools"
            tools.mkdir()
            calls = root / "calls.jsonl"
            gradle = root / "gradlew"
            gradle.write_text(f"#!{sys.executable}\n" + textwrap.dedent('''\
                import json, os, pathlib, sys
                with pathlib.Path(os.environ['CALLS']).open('a') as out:
                    out.write(json.dumps(sys.argv[1:]) + '\\n')
                if sys.argv[1] == os.environ['FAILED_TASK']:
                    sys.exit(23)
                '''))
            gradle.chmod(0o700)
            # Exercise the Linux invocation shape without making a fake
            # subreaper, hardware, emulator or runtime-success claim.
            supervisor = tools / "python3"
            supervisor.write_text(f"#!{sys.executable}\n" + textwrap.dedent('''\
                import subprocess, sys
                assert sys.argv[1:4] == [
                    '-B', 'scripts/android/supervise_managed_device.py', '--receipt']
                assert sys.argv[4] in (
                    'build/ci-evidence/android-managed-device-supervision.json',
                    'build/ci-evidence/android-navigation-device-supervision.json')
                args = sys.argv[5:]
                setup = None
                if args[:1] == ['--gradle-setup-task']:
                    setup = args[1]
                    args = args[2:]
                assert args[:3] == ['--isolated-avd', '--', './gradlew']
                command = args[2:]
                if setup:
                    status = subprocess.run([command[0], setup, *command[2:]]).returncode
                    if status:
                        sys.exit(status)
                    command.extend(['-x', setup])
                sys.exit(subprocess.run(command).returncode)
                '''))
            supervisor.chmod(0o700)
            for name, content in (
                ("uname", "#!/bin/sh\nprintf '%s\\n' \"$TEST_HOST_KERNEL\"\n"),
                ("keytool", "#!/bin/sh\nexit 0\n"),
            ):
                tool = tools / name
                tool.write_text(content)
                tool.chmod(0o700)
            env = dict(os.environ, PATH=str(tools) + os.pathsep + os.environ["PATH"],
                       CALLS=str(calls), FAILED_TASK=failed_task, TMPDIR=str(root),
                       TEST_HOST_KERNEL=host_kernel)
            command = ["bash", str(script)] + ([suite] if suite is not None else [])
            result = subprocess.run(command, cwd=root, env=env,
                                    capture_output=True, text=True, timeout=15)
            rows = [json.loads(line) for line in calls.read_text().splitlines()]
            return result.returncode, rows

    def test_both_real_commands_use_supported_software_gpu_and_keep_gates(self) -> None:
        status, calls = self.run_wrapper()
        self.assertEqual(status, 0)
        self.assertEqual([args[0] for args in calls], [RELEASE, ASSEMBLE, SETUP, NAVIGATION, "--stop"])
        for args, variant in zip((calls[0], calls[3]), ("release", "debug"), strict=True):
            self.assertEqual(args.count(LINUX_GPU), 1)
            self.assertNotIn(OTHER_GPU, args)
            self.assertEqual(args.count("--info"), 1)
            self.assertIn(f"-Pparlor.androidRuntimeTestVariant={variant}", args)
            self.assertIn("--dependency-verification=strict", args)
            self.assertIn("--no-daemon", args)
            self.assertIn("--max-workers=2", args)
        self.assertTrue(any(arg.startswith("-Pandroid.injected.signing.store.file=")
                            for arg in calls[0]))
        self.assertIn(":composeApp:assembleDebugAndroidTest", calls[1])
        self.assertIn("--no-daemon", calls[1])
        self.assertIn("--dependency-verification=strict", calls[1])
        self.assertIn("-Pparlor.androidRuntimeTestVariant=debug", calls[1])
        self.assertEqual(calls[2][1:], calls[3][1:-2])
        self.assertEqual(calls[3][-2:], ["-x", SETUP])
        self.assertTrue(any(arg.startswith("-Dorg.gradle.jvmargs=-Xmx4g ") for arg in calls[3]))
        self.assertFalse(any("signing" in arg for arg in calls[3]))

    def test_non_linux_invocations_keep_the_existing_renderer(self) -> None:
        status, calls = self.run_wrapper(host_kernel="Darwin")
        self.assertEqual(status, 0)
        self.assertEqual([args[0] for args in calls], [RELEASE, NAVIGATION, "--stop"])
        for args in calls[:2]:
            self.assertEqual(args.count(OTHER_GPU), 1)
            self.assertNotIn(LINUX_GPU, args)

    def test_failed_release_propagates_without_replay_or_navigation(self) -> None:
        status, calls = self.run_wrapper(RELEASE)
        self.assertEqual(status, 23)
        self.assertEqual([args[0] for args in calls], [RELEASE, "--stop"])
        self.assertEqual(calls[0].count(LINUX_GPU), 1)
        self.assertEqual(calls[0].count("--info"), 1)

    def test_failed_debug_assembly_does_not_start_navigation_device(self) -> None:
        status, calls = self.run_wrapper(ASSEMBLE)
        self.assertEqual(status, 23)
        self.assertEqual([args[0] for args in calls], [RELEASE, ASSEMBLE, "--stop"])

    def test_failed_navigation_propagates_without_replaying_either_lane(self) -> None:
        status, calls = self.run_wrapper(NAVIGATION)
        self.assertEqual(status, 23)
        self.assertEqual([args[0] for args in calls], [RELEASE, ASSEMBLE, SETUP, NAVIGATION, "--stop"])
        self.assertEqual(calls[3].count(LINUX_GPU), 1)
        self.assertEqual(calls[3].count("--info"), 1)


    def test_explicit_preview_suites_execute_only_the_selected_original_commands(self) -> None:
        status, combined = self.run_wrapper()
        self.assertEqual(status, 0)
        for suite, selected in (("release-only", combined[:1]), ("navigation-only", combined[1:4])):
            with self.subTest(suite=suite):
                status, calls = self.run_wrapper(suite=suite)
                self.assertEqual(status, 0)
                # The disposable Release signing path changes with the sandbox.
                def normalize(command: list[str]) -> list[str]:
                    return [
                        arg.split("=")[0] if arg.startswith("-Pandroid.injected.signing.store.file=") else arg
                        for arg in command
                    ]
                self.assertEqual([normalize(args) for args in calls], [normalize(args) for args in selected] + [["--stop"]])

    def test_invalid_preview_suite_fails_before_building(self) -> None:
        status, calls = self.run_wrapper(suite="invalid")
        self.assertEqual(status, 2)
        self.assertEqual(calls, [["--stop"]])

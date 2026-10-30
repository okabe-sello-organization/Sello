"""The Quality workflow: one required `quality` result that judges every job against the plan."""
import os
import re
import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path

import gates

WORKFLOW = Path(__file__).resolve().parents[2] / '.github/workflows/quality.yml'
GRADLE_PROPERTIES = WORKFLOW.parents[2] / 'gradle.properties'
READ_ONLY_CACHE = "cache-read-only: ${{ github.event_name == 'pull_request' || github.ref != 'refs/heads/main' }}"


def jobs(text):
    body = text.split('jobs:\n', 1)[1]
    matches = list(re.finditer(r'(?m)^  ([a-z][a-z-]*):\n', body))
    return {match.group(1): body[match.end():matches[index + 1].start() if index + 1 < len(matches) else len(body)]
            for index, match in enumerate(matches)}


def steps(text):
    found = {}
    for block in re.split(r'\n      - ', text.split('    steps:\n', 1)[1]):
        name = re.search(r'^(?:\s*- )?name: (.+)$', block, re.MULTILINE)
        uses = re.search(r'uses: (\S+)', block)
        found[name.group(1) if name else uses.group(1).split('@')[0]] = block
    return found


def script(block):
    return textwrap.dedent(block.split('run: |\n', 1)[1])


class HostedWorkflowTests(unittest.TestCase):
    def setUp(self):
        self.text = WORKFLOW.read_text(encoding='utf-8')
        self.code = '\n'.join(line for line in self.text.splitlines() if not line.lstrip().startswith('#'))
        self.jobs = jobs(self.code)
        self.plan = steps(self.jobs['plan'])
        self.host = steps(self.jobs['host'])
        self.device = steps(self.jobs['device'])
        self.quality = steps(self.jobs['quality'])

    def test_no_event_is_filtered_by_path_so_the_required_check_always_reports(self):
        self.assertNotRegex(self.code, r'\bpaths(-ignore)?:')
        self.assertNotRegex(self.code, r'\bbranches-ignore:')
        self.assertRegex(self.code, r'(?m)^  pull_request:\s*$')
        self.assertRegex(self.code, r'(?m)^  workflow_dispatch:\s*$')
        self.assertRegex(self.code, r'schedule:\n    - cron: "\d+ \d+ \* \* \*"')
        self.assertNotIn('merge_group', self.code)
        self.assertIn('BASE: ${{ github.event.pull_request.base.sha || github.event.before || github.sha }}', self.code)

    def test_only_pull_request_runs_are_cancelled_by_a_newer_run(self):
        concurrency = self.code.split('concurrency:\n', 1)[1].split('\n\n', 1)[0]
        self.assertIn("group: quality-${{ github.event_name == 'pull_request' && github.ref || github.sha }}-${{ github.event_name }}", concurrency)
        self.assertIn("cancel-in-progress: ${{ github.event_name == 'pull_request' }}", concurrency)

    def test_exactly_one_required_quality_result_depends_on_every_job(self):
        self.assertEqual({'plan', 'host', 'device', 'quality'}, set(self.jobs))
        self.assertEqual(1, len(re.findall(r'(?m)^    name: quality$', self.code)))
        quality = self.jobs['quality']
        self.assertIn('needs: [plan, host, device]', quality)
        self.assertRegex(quality, r"(?m)^    if: always\(\) && needs\.plan\.outputs\.unchanged != 'true'$")
        self.assertNotIn('matrix:', quality)
        self.assertNotIn('continue-on-error:', self.code)

    def test_quality_judges_job_results_not_artifacts_so_failed_jobs_can_be_rerun(self):
        self.assertNotIn('download-artifact', self.code)
        self.assertNotIn('actions/checkout', self.jobs['quality'])
        block = self.quality['Judge every job against the plan']
        for job in ('plan', 'host', 'device'):
            self.assertIn(f'{job.upper()}_RESULT: ${{{{ needs.{job}.result }}}}', block)
        self.assertIn('HOST_SELECTED: ${{ needs.plan.outputs.host }}', block)
        self.assertIn('DEVICE_SELECTED: ${{ needs.plan.outputs.device }}', block)
        self.assertNotRegex(self.code, r'\$\{\{ github\.run_attempt \}\}-\*')

        with tempfile.TemporaryDirectory() as directory:
            summary = Path(directory) / 'summary'

            def judge(plan='success', host='success', device='success', host_selected='true', device_selected='true'):
                summary.write_text('', encoding='utf-8')
                environment = dict(os.environ, GITHUB_STEP_SUMMARY=str(summary), GATE='G2', DEVICE_GROUPS='["app"]',
                                   PLAN_RESULT=plan, HOST_RESULT=host, DEVICE_RESULT=device,
                                   HOST_SELECTED=host_selected, DEVICE_SELECTED=device_selected)
                return subprocess.run(['bash', '-e', '-c', script(block)], env=environment, capture_output=True,
                                      text=True).returncode

            self.assertEqual(0, judge())
            self.assertIn('| Device tests ["app"] | success | success |', summary.read_text(encoding='utf-8'))
            # A matrix job's result is "success" only when every part succeeded.
            self.assertIn('| Host checks (tests, lint, assemble) | success | success |', summary.read_text(encoding='utf-8'))
            self.assertEqual(0, judge(device='skipped', device_selected='false'))  # G1
            self.assertEqual(0, judge(host='skipped', device='skipped', host_selected='false', device_selected='false'))  # G0
            for case in (dict(device='failure'), dict(device='cancelled'), dict(device='skipped'),
                         dict(host='failure'), dict(host='skipped'), dict(plan='failure'),
                         dict(plan='failure', host='skipped', device='skipped'),
                         dict(device='success', device_selected='false'), dict(host_selected='')):
                with self.subTest(**case):
                    self.assertEqual(1, judge(**case))

    def test_a_nightly_skips_every_job_when_main_has_not_moved_since_the_last_successful_nightly(self):
        names = list(self.plan)
        skip = self.plan['Skip a nightly when main has not moved']
        self.assertEqual(0, names.index('Skip a nightly when main has not moved'))
        self.assertIn("if: github.event_name == 'schedule'", skip)
        self.assertIn('GH_TOKEN: ${{ github.token }}', skip)
        self.assertIn('actions/workflows/quality.yml/runs?branch=main&event=schedule&status=success&per_page=1', skip)
        self.assertIn('unchanged: ${{ steps.nightly.outputs.unchanged }}', self.jobs['plan'])
        for name in ('actions/checkout', 'Decide what this run verifies', 'Run the fast checks'):
            self.assertIn("if: steps.nightly.outputs.unchanged != 'true'", self.plan[name])

        with tempfile.TemporaryDirectory() as directory:
            fake = Path(directory) / 'bin'
            fake.mkdir()
            (fake / 'gh').write_text('#!/bin/sh\ncat "$FAKE_GH_RESPONSE"\n', encoding='utf-8')
            (fake / 'gh').chmod(0o755)
            response, output, summary = (Path(directory) / name for name in ('response', 'output', 'summary'))

            def decide(verified_sha, current_sha):
                response.write_text(verified_sha + '\n', encoding='utf-8')
                output.write_text('', encoding='utf-8')
                environment = dict(os.environ, PATH=f"{fake}{os.pathsep}{os.environ['PATH']}", FAKE_GH_RESPONSE=str(response),
                                   GITHUB_SHA=current_sha, GITHUB_REPOSITORY='okabe-sello-organization/Sello',
                                   GITHUB_OUTPUT=str(output), GITHUB_STEP_SUMMARY=str(summary))
                subprocess.run(['bash', '-eu', '-o', 'pipefail', '-c', script(skip)], env=environment, check=True, capture_output=True)
                return output.read_text(encoding='utf-8').strip()

            self.assertEqual('unchanged=true', decide('a' * 40, 'a' * 40))
            self.assertEqual('unchanged=false', decide('a' * 40, 'b' * 40))
            self.assertEqual('unchanged=false', decide('', 'b' * 40))

    def test_the_plan_decides_and_the_fast_checks_gate_every_expensive_job(self):
        names = list(self.plan)
        self.assertIn('./scripts/check --plan "$BASE" --event "$EVENT"', self.plan['Decide what this run verifies'])
        self.assertIn('./scripts/check --fast', self.plan['Run the fast checks'])
        self.assertLess(names.index('Decide what this run verifies'), names.index('Run the fast checks'))
        self.assertIn('fetch-depth: 0', self.plan['actions/checkout'])
        for output in ('gate', 'host', 'release', 'device', 'groups', 'modules', 'scope'):
            self.assertIn(f'{output}: ${{{{ steps.plan.outputs.{output} }}}}', self.jobs['plan'])
        for job in ('host', 'device'):
            self.assertIn('    needs: plan\n', self.jobs[job])
            self.assertNotRegex(self.jobs[job], r'(?m)^    if: .*always\(\)')
        self.assertEqual(["if: needs.plan.outputs.host == 'true'"], re.findall(r'(?m)^    (if: .+)$', self.jobs['host']))
        self.assertEqual(["if: needs.plan.outputs.device == 'true'"], re.findall(r'(?m)^    (if: .+)$', self.jobs['device']))

    def test_host_job_runs_each_part_of_the_host_gate_in_parallel_and_ci_release_checks_only_when_planned(self):
        host = self.jobs['host']
        # One job per part, every part once, and a failing part never cancels the other.
        parts = re.search(r'(?m)^        part: \[(.*)\]$', host).group(1).split(', ')
        self.assertEqual(list(gates.HOST_PARTS), parts)
        self.assertIn('fail-fast: false', host)
        self.assertIn('name: Host checks (${{ matrix.part }})', host)
        self.assertIn('HOST_PART: ${{ matrix.part }}', host)
        self.assertIn('RELEASE: ${{ needs.plan.outputs.release }}', host)
        block = self.host['Run the host gate']
        self.assertIn(f'if [ "$RELEASE" = true ] && [ "$HOST_PART" = {gates.RELEASE_PART} ]; then', block)
        self.assertIn('./scripts/check --host "$HOST_PART" --ci-release', block)
        self.assertIn('./scripts/check --host "$HOST_PART"\n', block)
        self.assertNotIn('production', self.code)

        with tempfile.TemporaryDirectory() as directory:
            fake = Path(directory) / 'scripts'
            fake.mkdir()
            (fake / 'check').write_text('#!/bin/sh\necho "$@"\n', encoding='utf-8')
            (fake / 'check').chmod(0o755)

            def run(part, release):
                environment = dict(os.environ, HOST_PART=part, RELEASE=release)
                return subprocess.run(['bash', '-e', '-c', script(block)], env=environment, cwd=directory,
                                      capture_output=True, text=True, check=True).stdout.strip()

            for part in gates.HOST_PARTS:
                with self.subTest(part=part):
                    release = ' --ci-release' if part == gates.RELEASE_PART else ''
                    self.assertEqual(f'--host {part}{release}', run(part, 'true'))
                    self.assertEqual(f'--host {part}', run(part, 'false'))

    def test_device_groups_build_before_the_emulator_and_never_cancel_a_sibling(self):
        device = self.jobs['device']
        self.assertIn('group: ${{ fromJSON(needs.plan.outputs.groups) }}', device)
        self.assertIn('fail-fast: false', device)
        self.assertIn('DEVICE_GROUP: ${{ matrix.group }}', device)
        self.assertIn('DEVICE_MODULES: ${{ needs.plan.outputs.modules }}', device)
        build = self.device['Check formatting and build the test APKs']
        self.assertIn('./scripts/check --device "$DEVICE_GROUP" --modules "$DEVICE_MODULES" --build-only', build)
        run = self.device['Run device tests on API 30']
        # The plan's scope: a pull request or push leaves out @NightlyOnly; the nightly and a
        # manual run execute every device test.
        self.assertIn('DEVICE_SCOPE: ${{ needs.plan.outputs.scope }}', device)
        self.assertIn('script: ./scripts/check --device "$DEVICE_GROUP" --modules "$DEVICE_MODULES" '
                      '--scope "$DEVICE_SCOPE"; status=$?;', run)
        self.assertNotIn('--scope', build)
        names = list(self.device)
        for later in ('Restore Android system image', 'Enable KVM for the emulator', 'Run device tests on API 30'):
            self.assertLess(names.index('Check formatting and build the test APKs'), names.index(later))

    def test_each_device_group_still_creates_a_fresh_avd_and_cold_boots(self):
        device = self.device['Run device tests on API 30']
        for setting in ('force-avd-creation: true', '-no-snapshot', 'api-level: 30', 'target: google_apis', 'arch: x86_64', 'cores: 2'):
            self.assertIn(setting, device)

    def test_the_emulator_cannot_hold_a_finished_device_job(self):
        run = self.device['Run device tests on API 30']
        self.assertIn('killall -INT crashpad_handler || true; exit $status', run)
        self.assertIn('timeout-minutes: 25', run)

    def test_reports_of_a_failure_are_kept_without_being_proof(self):
        for group, name, artifact in ((self.host, 'Keep host reports of a failure',
                                       'host-reports-${{ github.run_attempt }}-${{ matrix.part }}'),
                                      (self.device, 'Keep device reports of a failure',
                                       'device-reports-${{ github.run_attempt }}-${{ matrix.group }}')):
            with self.subTest(step=name):
                block = group[name]
                self.assertIn('if: failure()', block)
                self.assertIn('name: ' + artifact, block)
                self.assertIn('if-no-files-found: ignore', block)

    def test_every_action_is_pinned_to_a_full_commit(self):
        uses = re.findall(r'uses: (\S+)', self.code)
        self.assertTrue(uses)
        for action in uses:
            self.assertRegex(action, r'^[\w.-]+/[\w./-]+@[0-9a-f]{40}$')

    def test_gradle_caches_are_written_only_from_main_and_set_up_before_gradle_runs(self):
        for group, first_gradle in ((self.host, 'Run the host gate'), (self.device, 'Check formatting and build the test APKs')):
            block = group['Set up Gradle caching']
            self.assertRegex(block, r'uses: gradle/actions/setup-gradle@[0-9a-f]{40}\b')
            self.assertIn(READ_ONLY_CACHE, block)
            self.assertIn('add-job-summary-as-pr-comment: never', block)
            self.assertIn('build-scan-publish: false', block)
            self.assertIn('dependency-graph: disabled', block)
            self.assertNotIn('gradle-version:', block)
            self.assertLess(list(group).index('Set up Gradle caching'), list(group).index(first_gradle))
        self.assertEqual(2, self.code.count('cache-read-only:'))
        self.assertNotRegex(self.code, r'(?m)^\s+cache: gradle\s*$')
        self.assertNotRegex(self.code, r'uses: actions/cache@')

    def test_sdk_image_cache_is_restored_only_by_device_jobs_and_saved_only_from_main(self):
        self.assertNotIn('actions/cache', self.jobs['host'])
        self.assertIn('path=$ANDROID_HOME/system-images/android-30/google_apis/x86_64', self.device['Locate Android system image'])
        restore = self.device['Restore Android system image']
        self.assertRegex(restore, r'uses: actions/cache/restore@[0-9a-f]{40}\b')
        self.assertIn('path: ${{ steps.android-image.outputs.path }}', restore)
        identify = self.device['Identify installed Android system image']
        save = self.device['Save Android system image']
        trusted = "matrix.group == 'support' && github.event_name != 'pull_request' && github.ref == 'refs/heads/main'"
        self.assertIn('if: ' + trusted, identify)
        self.assertIn('if: ' + trusted, save)
        self.assertIn('steps.android-image-cache.outputs.cache-matched-key != steps.android-image-key.outputs.key', save)
        self.assertRegex(save, r'uses: actions/cache/save@[0-9a-f]{40}\b')
        names = list(self.device)
        self.assertLess(names.index('Run device tests on API 30'), names.index('Identify installed Android system image'))

        key_script = script(identify).replace('${{ runner.os }}', 'Linux').replace('${{ runner.arch }}', 'X64')
        with tempfile.TemporaryDirectory() as directory:
            metadata = Path(directory) / 'sdk/system-images/android-30/google_apis/x86_64/source.properties'
            metadata.parent.mkdir(parents=True)
            output = Path(directory) / 'output'
            environment = dict(os.environ, ANDROID_HOME=str(Path(directory) / 'sdk'), GITHUB_OUTPUT=str(output))

            def key(revision):
                metadata.write_text(f'Pkg.Revision={revision}\n', encoding='utf-8')
                output.write_text('', encoding='utf-8')
                subprocess.run(['bash', '-eu', '-c', key_script], env=environment, check=True, capture_output=True)
                return output.read_text(encoding='utf-8').strip()

            original = key(16)
            self.assertRegex(original, r'^key=android-image-v1-Linux-X64-api30-google_apis-x86_64-[0-9a-f]{64}$')
            self.assertEqual(original, key(16))
            self.assertNotEqual(original, key(17))

    def test_shared_cache_still_tracks_real_task_outputs(self):
        properties = GRADLE_PROPERTIES.read_text(encoding='utf-8')
        self.assertIn('org.gradle.caching=true', properties)
        self.assertIn('org.gradle.configuration-cache=true', properties)

    def test_no_new_github_permissions_or_untrusted_triggers(self):
        permissions = self.code.split('permissions:\n', 1)[1].split('\n\n', 1)[0]
        self.assertEqual('  contents: read', permissions)
        self.assertNotIn('pull_request_target:', self.code)
        # Only the planning job may read past workflow runs, to skip an unchanged nightly.
        self.assertIn('    permissions:\n      contents: read\n      actions: read\n', self.jobs['plan'])
        for name in ('host', 'device', 'quality'):
            self.assertNotIn('permissions:', self.jobs[name])
        self.assertEqual(1, self.code.count('actions: read'))
        self.assertNotIn('secrets.', self.code)


if __name__ == '__main__':
    unittest.main()

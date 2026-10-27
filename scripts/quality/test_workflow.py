"""Hosted job routing and the one required result must preserve the whole gate."""
import os
import re
import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path

WORKFLOW = Path(__file__).resolve().parents[2] / '.github/workflows/quality.yml'
GRADLE_PROPERTIES = WORKFLOW.parents[2] / 'gradle.properties'


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


class HostedWorkflowTests(unittest.TestCase):
    def setUp(self):
        self.text = WORKFLOW.read_text(encoding='utf-8')
        self.code = '\n'.join(line for line in self.text.splitlines() if not line.lstrip().startswith('#'))
        self.jobs = jobs(self.code)
        self.host = steps(self.jobs['host'])
        self.device = steps(self.jobs['device'])
        self.collector = steps(self.jobs['quality'])

    def test_no_event_is_filtered_by_path_so_the_required_check_always_reports(self):
        self.assertNotRegex(self.code, r'\bpaths(-ignore)?:')
        self.assertNotRegex(self.code, r'\bbranches-ignore:')
        self.assertRegex(self.code, r'(?m)^  pull_request:\s*$')

    def test_main_lands_straight_from_a_green_pull_request_without_a_merge_queue(self):
        self.assertNotIn('merge_group', self.code)
        self.assertIn('BASE: ${{ github.event.pull_request.base.sha || github.event.before || github.sha }}', self.code)

    def test_a_nightly_skips_every_job_when_main_has_not_moved_since_the_last_successful_nightly(self):
        plan = steps(self.jobs['plan'])
        names = list(plan)
        skip = plan['Skip a nightly when main has not moved']
        self.assertEqual(0, names.index('Skip a nightly when main has not moved'))
        self.assertIn("if: github.event_name == 'schedule'", skip)
        self.assertIn('id: nightly', skip)
        self.assertIn('GH_TOKEN: ${{ github.token }}', skip)
        self.assertIn('actions/workflows/quality.yml/runs?branch=main&event=schedule&status=success&per_page=1', skip)
        self.assertIn('unchanged: ${{ steps.nightly.outputs.unchanged }}', self.jobs['plan'])
        for name in ('actions/checkout', 'Decide what this run verifies'):
            self.assertIn("if: steps.nightly.outputs.unchanged != 'true'", plan[name])
        self.assertRegex(self.jobs['host'], r"(?m)^    if: needs\.plan\.outputs\.unchanged != 'true'$")
        self.assertRegex(self.jobs['quality'], r"(?m)^    if: always\(\) && needs\.plan\.outputs\.unchanged != 'true'$")

        script = textwrap.dedent(skip.split('run: |\n', 1)[1])
        with tempfile.TemporaryDirectory() as directory:
            fake = Path(directory) / 'bin'
            fake.mkdir()
            (fake / 'gh').write_text('#!/bin/sh\ncat "$FAKE_GH_RESPONSE"\n', encoding='utf-8')
            (fake / 'gh').chmod(0o755)
            response = Path(directory) / 'response.json'
            output = Path(directory) / 'output'
            summary = Path(directory) / 'summary'

            def decide(verified_sha, current_sha):
                response.write_text(verified_sha + '\n', encoding='utf-8')
                output.write_text('', encoding='utf-8')
                summary.write_text('', encoding='utf-8')
                environment = dict(os.environ, PATH=f"{fake}{os.pathsep}{os.environ['PATH']}", FAKE_GH_RESPONSE=str(response),
                                   GITHUB_SHA=current_sha, GITHUB_REPOSITORY='okabe-sello-organization/Sello',
                                   GITHUB_OUTPUT=str(output), GITHUB_STEP_SUMMARY=str(summary))
                subprocess.run(['bash', '-eu', '-o', 'pipefail', '-c', script], env=environment, check=True, capture_output=True)
                return output.read_text(encoding='utf-8').strip()

            self.assertEqual('unchanged=true', decide('a' * 40, 'a' * 40))
            self.assertEqual('unchanged=false', decide('a' * 40, 'b' * 40))
            self.assertEqual('unchanged=false', decide('', 'b' * 40))

    def test_exactly_one_stable_quality_result_collects_every_job(self):
        self.assertEqual({'plan', 'host', 'device', 'quality'}, set(self.jobs))
        self.assertEqual(1, len(re.findall(r'(?m)^    name: quality$', self.code)))
        quality = self.jobs['quality']
        self.assertIn('needs: [plan, host, device]', quality)
        self.assertIn('if: always()', quality)
        self.assertNotIn('matrix:', quality)
        self.assertNotIn('continue-on-error:', self.code)

    def test_a_pull_request_asks_the_runner_which_gate_it_needs(self):
        plan = steps(self.jobs['plan'])['Decide what this run verifies']
        self.assertIn('--ci-plan "$BASE" --ci-event "$EVENT"', plan)
        self.assertIn("plan['gate']", plan)
        self.assertIn("plan['groups']", plan)
        self.assertIn('gate: ${{ steps.plan.outputs.gate }}', self.jobs['plan'])
        self.assertIn('groups: ${{ steps.plan.outputs.groups }}', self.jobs['plan'])

    def test_device_groups_run_in_parallel_and_never_cancel_the_sibling(self):
        device = self.jobs['device']
        self.assertIn('needs: plan', device)
        self.assertNotIn('needs: host', device)
        self.assertIn("if: needs.plan.outputs.gate == 'G2' || needs.plan.outputs.gate == 'G3'", device)
        self.assertIn('group: ${{ fromJSON(needs.plan.outputs.groups) }}', device)
        self.assertIn('fail-fast: false', device)
        self.assertIn('DEVICE_GROUP: ${{ matrix.group }}', device)
        self.assertIn('--ci-part "$DEVICE_GROUP" --ci-event "$EVENT"', self.device['Verify device part on API 30'])

    def test_every_event_runs_the_host_part_independently(self):
        host = self.jobs['host']
        self.assertIn('needs: plan', host)
        self.assertEqual(["if: needs.plan.outputs.unchanged != 'true'"], re.findall(r'(?m)^    (if: .+)$', host))
        self.assertNotIn('needs: device', host)
        self.assertIn('--ci-part host --ci-event "$EVENT"', self.host['Verify host part'])
        self.assertNotIn('--gate', self.host['Verify host part'])

    def test_documentation_host_part_does_not_set_up_android_or_gradle(self):
        for name in ('Set up Gradle caching', 'Accept Android SDK licences'):
            self.assertIn("if: needs.plan.outputs.gate != 'G0'", self.host[name])
        self.assertIn("if: needs.plan.outputs.gate != 'G0'", self.jobs['host'].split('uses: actions/setup-java@',1)[0])

    def test_collector_checks_dependency_results_and_cannot_ignore_a_download_failure(self):
        collect = self.collector['Collect full gate evidence']
        self.assertIn("steps.checkout.outcome == 'success' && steps.parts.outcome == 'success'", collect)
        for job in ('plan', 'host', 'device'):
            self.assertIn(f'QUALITY_{job.upper()}_RESULT: ${{{{ needs.{job}.result }}}}', collect)
        self.assertIn('--ci-collect build/ci-parts --ci-event "$EVENT"', collect)
        self.assertNotIn('--gate', collect)

    def test_artifact_download_is_pinned_same_run_separate_and_attempt_bound(self):
        download = self.collector['Download execution parts']
        self.assertRegex(download, r'uses: actions/download-artifact@[0-9a-f]{40}\b')
        self.assertIn('pattern: quality-part-${{ github.run_attempt }}-*', download)
        self.assertIn('merge-multiple: false', download)
        self.assertIn('digest-mismatch: error', download)
        self.assertIn('path: build/ci-parts', download)
        self.assertNotIn('github-token:', download)
        self.assertNotIn('run-id:', download)

    def test_missing_part_or_gate_artifacts_fail_and_raw_evidence_is_retained(self):
        for group, step, name in (
            (self.host, 'Keep host part', 'quality-part-${{ github.run_attempt }}-host'),
            (self.device, 'Keep device part', 'quality-part-${{ github.run_attempt }}-${{ matrix.group }}'),
            (self.collector, 'Keep full gate reports', 'quality-reports'),
        ):
            with self.subTest(step=step):
                block = group[step]
                self.assertIn('if: always()', block)
                self.assertRegex(block, r'uses: actions/upload-artifact@[0-9a-f]{40}\b')
                self.assertIn('name: ' + name, block)
                self.assertIn('retention-days: 90', block)
                self.assertIn('if-no-files-found: error', block)
        self.assertIn('path: build/reports/quality-parts/quality-part-${{ github.run_attempt }}-host/', self.host['Keep host part'])
        self.assertIn('path: build/reports/quality-parts/quality-part-${{ github.run_attempt }}-${{ matrix.group }}/', self.device['Keep device part'])
        self.assertIn('path: build/reports/quality/', self.collector['Keep full gate reports'])

    def test_completion_evidence_is_validated_after_the_full_gate(self):
        block = self.collector['Validate completion evidence']
        self.assertNotIn('if:', block)
        self.assertIn('python3 scripts/quality/tickets.py check --base "$BASE"', block)
        names = list(self.collector)
        self.assertLess(names.index('Collect full gate evidence'), names.index('Validate completion evidence'))

    def test_main_still_gets_the_whole_gate_on_a_schedule(self):
        self.assertRegex(self.code, r'schedule:\n    - cron: "\d+ \d+ \* \* \*"')
        self.assertIn("if: needs.plan.outputs.gate == 'G2' || needs.plan.outputs.gate == 'G3'", self.jobs['device'])

    def test_gradle_cache_setup_is_pinned_read_only_for_prs_and_precedes_execution(self):
        for group, execute in ((self.host, 'Verify host part'), (self.device, 'Verify device part on API 30')):
            block = group['Set up Gradle caching']
            self.assertRegex(block, r'uses: gradle/actions/setup-gradle@[0-9a-f]{40}\b')
            self.assertIn("cache-read-only: ${{ github.event_name == 'pull_request' || github.ref != 'refs/heads/main' }}", block)
            self.assertIn('add-job-summary: always', block)
            self.assertIn('add-job-summary-as-pr-comment: never', block)
            self.assertIn('build-scan-publish: false', block)
            self.assertIn('dependency-graph: disabled', block)
            self.assertNotIn('gradle-version:', block)
            self.assertLess(list(group).index('Set up Gradle caching'), list(group).index(execute))
        self.assertNotRegex(self.code, r'(?m)^\s+cache: gradle\s*$')
        for step in self.device.values():
            self.assertNotRegex(step, r'uses: actions/cache@')
            if 'uses: actions/cache/' in step:
                self.assertIn('path: ${{ steps.android-image.outputs.path }}', step)

    def test_sdk_image_cache_is_separate_and_only_used_for_the_device_job(self):
        self.assertNotIn('actions/cache', self.jobs['host'])
        locate = self.device['Locate Android system image']
        restore = self.device['Restore Android system image']
        self.assertIn('path=$ANDROID_HOME/system-images/android-30/google_apis/x86_64', locate)
        self.assertRegex(restore, r'uses: actions/cache/restore@[0-9a-f]{40}\b')
        self.assertIn('path: ${{ steps.android-image.outputs.path }}', restore)
        names = list(self.device)
        self.assertLess(names.index('Restore Android system image'), names.index('Verify device part on API 30'))

    def test_sdk_image_publication_has_one_successful_main_writer(self):
        identify = self.device['Identify installed Android system image']
        save = self.device['Save Android system image']
        trusted = "matrix.group == 'support' && github.event_name != 'pull_request' && github.ref == 'refs/heads/main'"
        self.assertIn('if: ' + trusted, identify)
        self.assertIn('if: ' + trusted, save)
        self.assertIn('sha256sum "$ANDROID_HOME/system-images/android-30/google_apis/x86_64/source.properties"', identify)
        self.assertIn('steps.android-image-cache.outputs.cache-matched-key != steps.android-image-key.outputs.key', save)
        self.assertRegex(save, r'uses: actions/cache/save@[0-9a-f]{40}\b')
        self.assertIn('key: ${{ steps.android-image-key.outputs.key }}', save)
        names = list(self.device)
        self.assertLess(names.index('Verify device part on API 30'), names.index('Validate completion evidence before cache publication'))
        self.assertLess(names.index('Validate completion evidence before cache publication'), names.index('Identify installed Android system image'))

        script = textwrap.dedent(identify.split('run: |\n', 1)[1])
        script = script.replace('${{ runner.os }}', 'Linux').replace('${{ runner.arch }}', 'X64')
        with tempfile.TemporaryDirectory() as directory:
            sdk = Path(directory) / 'sdk'
            metadata = sdk / 'system-images/android-30/google_apis/x86_64/source.properties'
            metadata.parent.mkdir(parents=True)
            output = Path(directory) / 'output'
            environment = dict(os.environ, ANDROID_HOME=str(sdk), GITHUB_OUTPUT=str(output))

            def key(revision):
                metadata.write_text(f'Pkg.Revision={revision}\n', encoding='utf-8')
                output.write_text('', encoding='utf-8')
                subprocess.run(['bash', '-eu', '-c', script], env=environment, check=True, capture_output=True)
                return output.read_text(encoding='utf-8').strip()

            original = key(16)
            self.assertRegex(original, r'^key=android-image-v1-Linux-X64-api30-google_apis-x86_64-[0-9a-f]{64}$')
            self.assertEqual(original, key(16))
            self.assertNotEqual(original, key(17))

    def test_each_device_group_still_creates_a_fresh_avd_and_cold_boots(self):
        device = self.device['Verify device part on API 30']
        for setting in ('force-avd-creation: true', '-no-snapshot', 'api-level: 30', 'target: google_apis', 'arch: x86_64', 'cores: 2'):
            self.assertIn(setting, device)

    def test_shared_cache_still_tracks_real_task_outputs(self):
        properties = GRADLE_PROPERTIES.read_text(encoding='utf-8')
        self.assertIn('org.gradle.caching=true', properties)
        self.assertIn('org.gradle.configuration-cache=true', properties)

    def test_no_new_github_permissions_or_untrusted_cache_writes(self):
        permissions = self.code.split('permissions:\n', 1)[1].split('\n\n', 1)[0]
        self.assertEqual('  contents: read', permissions)
        self.assertNotIn('pull_request_target:', self.code)
        # Only the planning job may read past workflow runs, to skip an unchanged nightly.
        self.assertIn('    permissions:\n      contents: read\n      actions: read\n', self.jobs['plan'])
        for name in ('host', 'device', 'quality'):
            self.assertNotIn('permissions:', self.jobs[name])
        self.assertEqual(1, self.code.count('actions: read'))


if __name__ == '__main__':
    unittest.main()

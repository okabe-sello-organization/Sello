"""Distributed execution must prove the same complete gate as local execution."""
import io
import json
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import distributed
import evidence
import gates
import runner
import fingerprint
from support import epic, git, make_repo, ticket, write, write_epic
from test_runner import DEVICE, Harness


class DistributedGateTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = make_repo(temporary.name, epic(ticket('SELLO-003', status='Done'), ticket('SELLO-004')))
        self.base = git(self.root, 'rev-parse', 'HEAD').strip()
        self.parts = self.root / 'build/parts'
        self.harness = Harness(self.root)
        self.stream = io.StringIO()
        self.environment = lambda root: {'java': '25', 'gradle': '9.5.0', 'agp': '9.3.3', 'kotlin': '2.4.20'}
        environment = patch.dict(os.environ, {
            'QUALITY_EXECUTION_ID': 'local-test', 'GITHUB_ACTIONS': 'false',
            'GITHUB_RUN_ID': '', 'GITHUB_RUN_ATTEMPT': '', 'ANDROID_SERIAL': '',
        })
        environment.start()
        self.addCleanup(environment.stop)

    def part(self, name, event='workflow_dispatch', devices=None):
        return distributed.run_part(
            self.root, self.base, event, name, output=self.parts,
            execute=self.harness.execute, environment=self.environment,
            devices=lambda: [DEVICE] if devices is None else devices, stream=self.stream,
        )

    def all_parts(self):
        for part in ('host', 'app-1', 'app-2', 'support'):
            self.assertEqual(0, self.part(part)[0], self.stream.getvalue())

    def collect(self, event='workflow_dispatch', jobs=None):
        code, paths = distributed.collect(
            self.root, self.base, event, self.parts, stream=self.stream, job_results=jobs,
        )
        return code, json.loads(paths[0].read_text()) if paths else None

    def edit_part(self, name, edit):
        path = self.parts / f'quality-part-local-{name}/part.json'
        report = json.loads(path.read_text())
        edit(report)
        path.write_text(json.dumps(report))

    def test_app_only_candidate_does_not_require_unaffected_support_suites(self):
        write(self.root, 'app/src/Main.kt', 'fun main() = println(42)\n')
        self.assertEqual(0, self.part('host', event='pull_request')[0])
        self.assertEqual(0, self.part('app-1', event='pull_request')[0])
        self.assertEqual(0, self.part('app-2', event='pull_request')[0])
        code, report = self.collect(event='pull_request', jobs={'plan':'success','host':'success','device':'success'})
        self.assertEqual(0, code, self.stream.getvalue())
        self.assertEqual(['host','app-1','app-2'], [p['part'] for p in report['execution']['parts']])
        self.assertEqual('affected', report['coverage']['mode'])
        self.assertEqual(['app'], report['coverage']['device_modules'])

    def test_catalog_only_support_part_executes_no_data_or_design_system_tests(self):
        write(self.root, 'catalog/src/main/Screen.kt', 'class Screen\n')
        self.assertEqual(0, self.part('host', event='pull_request')[0])
        self.assertEqual(0, self.part('support', event='pull_request')[0])
        code, report = self.collect(event='pull_request', jobs={'plan':'success','host':'success','device':'success'})
        self.assertEqual(0, code, self.stream.getvalue())
        self.assertEqual(['catalog'], report['coverage']['device_modules'])
        self.assertEqual({'catalog'}, set(report['checks'][-1]['modules']))
        self.assertEqual(2, self.part('app-1', event='pull_request')[0])

    def test_device_selected_by_scope_cannot_be_replaced_with_an_unselected_part(self):
        write(self.root, 'data/src/main/Store.kt', 'class Store\n')
        self.part('host',event='pull_request'); self.part('app-1',event='pull_request'); self.part('app-2',event='pull_request')
        self.assertNotEqual(0,self.collect(event='pull_request')[0])

    def test_main_documentation_push_has_no_gradle_part_and_no_device(self):
        write(self.root,'docs/note.md','A note.\n')
        self.assertEqual(0,self.part('host',event='push')[0])
        code,report=self.collect(event='push',jobs={'plan':'success','host':'success','device':'skipped'})
        self.assertEqual(0,code)
        self.assertEqual('G0',report['gate']['executed'])
        self.assertNotIn('gradle-host',self.harness.executed)

    def test_complete_parts_produce_standard_full_evidence_with_every_module(self):
        self.all_parts()
        code, report = self.collect(jobs={'plan': 'success', 'host': 'success', 'device': 'success'})
        self.assertEqual(0, code, self.stream.getvalue())
        self.assertEqual('passed', report['outcome'])
        self.assertEqual('G3', report['gate']['executed'])
        self.assertIsNone(evidence.schema_problem(report, evidence.SCHEMA))
        self.assertEqual(['host', 'app-1', 'app-2', 'support'], [p['part'] for p in report['execution']['parts']])
        self.assertEqual({'data', 'design-system', 'app', 'catalog'}, {
            name for record in report['checks'] for name in record.get('modules', {})
        })
        self.assertEqual(5, sum(r['results']['tests'] for r in report['checks'] if r['name'].startswith('gradle-device')))
        self.assertEqual(3, len(report['checks'][-1]['modules']))
        shards = [(r['name'], r['shard']) for r in report['checks'] if 'shard' in r]
        self.assertEqual([('gradle-device-app-1', {'index': 0, 'count': 2}), ('gradle-device-app-2', {'index': 1, 'count': 2})], shards)

    def test_both_app_shards_are_needed_and_one_cannot_stand_in_for_the_other(self):
        self.all_parts()
        code, path = self.collect(jobs={'plan': 'success', 'host': 'success', 'device': 'success'})
        self.assertEqual(0, code, self.stream.getvalue())
        report = json.loads((self.root / 'build/reports/quality/UNTICKETED').glob('*/report.json').__next__().read_text())
        evidence.load(next((self.root / 'build/reports/quality/UNTICKETED').glob('*/report.json')))
        edited = self.root / 'build/edited-report.json'
        for name, edit in (
            ('one shard missing', lambda checks: [c for c in checks if c['name'] != 'gradle-device-app-2']),
            ('same shard twice', lambda checks: [dict(c, shard={'index': 0, 'count': 2}) if c['name'] == 'gradle-device-app-2' else c for c in checks]),
            ('a whole run beside a shard', lambda checks: [{k: v for k, v in c.items() if k != 'shard'} if c['name'] == 'gradle-device-app-2' else c for c in checks]),
        ):
            with self.subTest(name=name):
                edited.write_text(json.dumps(dict(report, checks=edit(report['checks']))))
                with self.assertRaises(evidence.EvidenceError):
                    evidence.load(edited)
        shutil.copy(self.parts / 'quality-part-local-app-1/part.json', self.parts / 'quality-part-local-app-2/part.json')
        self.assertNotEqual(0, self.collect(jobs={'plan': 'success', 'host': 'success', 'device': 'success'})[0])

    def test_partial_report_cannot_be_used_as_completion_evidence(self):
        code, path = self.part('app-1')
        self.assertEqual(0, code)
        with self.assertRaises(evidence.EvidenceError):
            evidence.load(path)
        self.assertIn('not full-gate completion evidence', self.stream.getvalue())

    def test_part_commands_are_disjoint_and_cover_the_complete_device_gate(self):
        self.all_parts()
        commands = {p: json.loads((self.parts / f'quality-part-local-{p}/part.json').read_text())['checks'][0]['command']
                    for p in ('app-1', 'app-2', 'support')}
        tasks = [word for command in commands.values() for word in command.split() if word.endswith(':connectedDebugAndroidTest')]
        self.assertEqual(sorted(gates.DEVICE_TASKS), sorted(set(tasks)))
        self.assertEqual([':app:connectedDebugAndroidTest'] * 2, [t for t in tasks if t.startswith(':app:')])
        for index, part in enumerate(('app-1', 'app-2')):
            self.assertIn(f'numShards=2 -Pandroid.testInstrumentationRunnerArguments.shardIndex={index}', commands[part])
            self.assertNotIn(':data:connectedDebugAndroidTest', commands[part])
        self.assertNotIn('numShards', commands['support'])

    def test_missing_group_fails_even_with_successful_job_status(self):
        self.part('host')
        self.part('app-1')
        self.part('app-2')
        code, report = self.collect(jobs={'plan': 'success', 'host': 'success', 'device': 'success'})
        self.assertNotEqual(0, code)
        self.assertEqual('failed', report['outcome'])
        self.assertTrue(any('missing' in failure for failure in report['failures']))

    def test_failed_cancelled_skipped_and_missing_jobs_are_not_success(self):
        self.all_parts()
        for name in ('plan', 'host', 'device'):
            for state in ('failure', 'cancelled', 'skipped', 'missing'):
                with self.subTest(name=name, state=state):
                    jobs = {'plan': 'success', 'host': 'success', 'device': 'success', name: state}
                    code, report = self.collect(jobs=jobs)
                    self.assertNotEqual(0, code)
                    self.assertIn(f'{name} job was {state}, expected success', report['failures'])

    def test_stale_revision_or_inputs_are_rejected(self):
        self.all_parts()
        self.edit_part('app-1', lambda r: r['snapshot'].update(head='0' * 40))
        code, report = self.collect()
        self.assertNotEqual(0, code)
        self.assertTrue(any('stale commit' in f for f in report['failures']))

    def test_changed_inputs_after_execution_are_rejected(self):
        self.all_parts()
        write(self.root, 'app/src/Main.kt', 'fun main() = println(2)\n')
        self.assertNotEqual(0, self.collect()[0])

    def test_wrong_workflow_attempt_is_rejected(self):
        self.all_parts()
        self.edit_part('app-1', lambda r: r['context']['session'].update(attempt='old'))
        code, report = self.collect()
        self.assertNotEqual(0, code)
        self.assertTrue(any('workflow run/attempt mismatch' in f for f in report['failures']))

    def test_relabelled_duplicate_part_is_rejected(self):
        self.all_parts()
        source = self.parts / 'quality-part-local-app-1/part.json'
        (self.parts / 'quality-part-local-support/part.json').write_bytes(source.read_bytes())
        self.assertNotEqual(0, self.collect()[0])

    def test_extra_part_artifacts_are_rejected(self):
        self.all_parts()
        (self.parts / 'quality-part-unexpected').mkdir()
        self.assertNotEqual(0, self.collect()[0])

    def test_missing_module_xml_cannot_be_hidden_by_passed_counters(self):
        self.all_parts()
        (self.parts / 'quality-part-local-support/raw/data/build/outputs/androidTest-results/connected/debug/TEST-d.xml').unlink()
        self.assertNotEqual(0, self.collect()[0])

    def test_missing_device_freshness_validation_is_rejected(self):
        self.all_parts()
        self.edit_part('app-1', lambda r: r['checks'][0].update(fresh_results=False))
        self.assertNotEqual(0, self.collect()[0])

    def test_changed_commands_and_failed_checks_cannot_be_labelled_passed(self):
        self.all_parts()
        self.edit_part('app-1', lambda r: r['checks'][0].update(exit_code=1))
        self.assertNotEqual(0, self.collect()[0])

    def test_skipped_device_tests_are_rejected(self):
        self.all_parts()
        xml = self.parts / 'quality-part-local-app-1/raw/app/build/outputs/androidTest-results/connected/debug/TEST-d.xml'
        xml.write_text('<testsuite tests="1" failures="0" errors="0" skipped="1"/>')
        self.assertNotEqual(0, self.collect()[0])

    def test_module_missing_during_execution_fails_the_part(self):
        self.harness.missing_device_modules = {'data'}
        code, path = self.part('support')
        self.assertNotEqual(0, code)
        report = json.loads(path.read_text())
        self.assertIn('gradle-device-support: data produced no fresh test results', report['failures'])

    def test_stale_xml_is_not_device_execution(self):
        self.harness.write_device_results = False
        write(self.root, 'app/build/outputs/androidTest-results/connected/debug/TEST-stale.xml',
                     '<testsuite tests="1" failures="0" errors="0" skipped="0"/>')
        os.utime(self.root / 'app/build/outputs/androidTest-results/connected/debug/TEST-stale.xml', (1, 1))
        self.assertNotEqual(0, self.part('app-1')[0])

    def test_ambiguous_or_wrong_api_devices_are_refused(self):
        self.assertEqual(2, self.part('app-1', devices=[DEVICE, dict(DEVICE, serial='other')])[0])
        self.assertEqual(2, self.part('app-1', devices=[dict(DEVICE, api='36')])[0])
        self.assertEqual([], self.harness.executed)

    def test_incomplete_partition_is_refused_before_execution(self):
        with patch.dict(gates.DEVICE_GROUPS, {'support': gates.Group((':data:connectedDebugAndroidTest',))}):
            self.assertEqual(2, self.part('app-1')[0])
        self.assertEqual([], self.harness.executed)

    def test_document_only_pr_needs_only_the_host_part(self):
        write(self.root, 'docs/change.md', 'A document.\n')
        self.assertEqual(0, self.part('host', event='pull_request')[0])
        code, report = self.collect(event='pull_request', jobs={'plan': 'success', 'host': 'success', 'device': 'skipped'})
        self.assertEqual(0, code)
        self.assertEqual('G0', report['gate']['executed'])
        self.assertEqual(2, self.part('app-1', event='pull_request')[0])

    def test_single_artifact_download_at_root_is_valid_only_for_g1_host(self):
        self.assertEqual(0, self.part('host', event='push')[0])
        flattened = self.root / 'build/downloaded'
        shutil.copytree(self.parts / 'quality-part-local-host', flattened)
        self.parts = flattened
        code, report = self.collect(event='push', jobs={'plan': 'success', 'host': 'success', 'device': 'skipped'})
        self.assertEqual(0, code, self.stream.getvalue())
        self.assertEqual('G0', report['gate']['executed'])

    def test_one_flat_artifact_cannot_stand_for_the_full_device_gate(self):
        self.assertEqual(0, self.part('host')[0])
        flattened = self.root / 'build/downloaded'
        shutil.copytree(self.parts / 'quality-part-local-host', flattened)
        self.parts = flattened
        self.assertNotEqual(0, self.collect()[0])

    def test_an_event_the_workflow_never_sends_is_refused(self):
        self.assertEqual(2, self.part('host', event='merge_group')[0])
        self.assertIn("Unknown CI event 'merge_group'", self.stream.getvalue())
        self.assertEqual([], self.harness.executed)

    def test_push_keeps_the_host_gate_even_for_a_code_change(self):
        write(self.root, 'app/src/Main.kt', 'fun main() = println(1)\n')
        self.assertEqual(0, self.part('host', event='push')[0])
        self.assertEqual(0, self.collect(event='push')[0])
        self.assertEqual(['tickets-check', 'quality-tests', 'performance-tests', 'architecture', 'links', 'gradle-host'], self.harness.executed)

    def test_incompatible_toolchains_are_rejected(self):
        self.all_parts()
        self.edit_part('support', lambda r: r['environment'].update(java='17'))
        self.assertNotEqual(0, self.collect()[0])

    def test_missing_toolchain_or_device_fields_are_rejected(self):
        self.all_parts()
        self.edit_part('support', lambda r: r['environment'].pop('gradle'))
        self.assertNotEqual(0, self.collect()[0])
        self.edit_part('support', lambda r: r['environment'].update(gradle='9.5.0'))
        self.edit_part('app-1', lambda r: r['devices'][0].pop('model'))
        self.assertNotEqual(0, self.collect()[0])

    def test_existing_output_is_not_overwritten(self):
        self.assertEqual(0, self.part('host')[0])
        self.assertEqual(2, self.part('host')[0])

    def test_all_ticket_prerequisites_are_checked_before_any_execution(self):
        write_epic(self.root, epic(ticket('SELLO-003', status='Done'), ticket('SELLO-004'),
                                   ticket('SELLO-005', status='In Progress', dependencies='SELLO-004', gate='G2')))
        self.assertEqual(2, self.part('host')[0])
        self.assertEqual([], self.harness.executed)
        self.assertIn('prerequisite SELLO-004 is Backlog', self.stream.getvalue())

    def test_multi_ticket_collection_binds_each_ticket_after_complete_candidate_execution(self):
        write_epic(self.root, epic(ticket('SELLO-003', status='Done'), ticket('SELLO-004', status='Done'),
                                   ticket('SELLO-005', status='In Progress', dependencies='SELLO-004', gate='G2')))
        self.all_parts()
        code, paths = distributed.collect(self.root, self.base, 'workflow_dispatch', self.parts, stream=self.stream)
        self.assertEqual(0, code)
        reports = [json.loads(path.read_text()) for path in paths]
        self.assertEqual(['SELLO-004', 'SELLO-005'], [report['ticket'] for report in reports])
        self.assertEqual(1, self.harness.executed.count('gradle-host'))
        for report in reports:
            self.assertEqual('G3', report['gate']['executed'])
            self.assertEqual(fingerprint.ticket_acceptance(self.root, report['ticket']), report['snapshot']['acceptance'])

    def release_ticket(self):
        write_epic(self.root, epic(ticket('SELLO-003', status='Done'),
                                   ticket('SELLO-004', status='In Progress', dependencies='SELLO-003', gate='G3')))
        write(self.root, 'app/src/Main.kt', 'fun main() = println(35)\n')

    def test_a_release_ticket_runs_g3_on_ci_with_throwaway_signing_and_every_device_suite(self):
        self.release_ticket()
        plan = distributed.context(self.root, self.base, 'pull_request')
        self.assertEqual('G3', plan['gate'])
        self.assertEqual({'SELLO-004': 'G3'}, plan['tickets'])
        self.assertEqual(['data', 'design-system', 'app', 'catalog'], plan['coverage']['device_modules'])
        host = [check.name for check in distributed.checks_for('host', plan)]
        self.assertEqual(['release-licenses', 'release-ephemeral'], host[-2:])
        self.assertNotIn('release-production', host)
        for part in ('host', 'app-1', 'app-2', 'support'):
            self.assertEqual(0, self.part(part, event='pull_request')[0], self.stream.getvalue())
        code, report = self.collect(event='pull_request', jobs={'plan': 'success', 'host': 'success', 'device': 'success'})
        self.assertEqual(0, code, self.stream.getvalue())
        self.assertEqual({'required': 'G3', 'executed': 'G3'}, report['gate'])

    def test_a_nightly_or_manual_run_verifies_the_whole_of_main_at_g3(self):
        for event in ('schedule', 'workflow_dispatch'):
            with self.subTest(event=event):
                plan = distributed.context(self.root, self.base, event)
                self.assertEqual(('G3', 'full', ['data', 'design-system', 'app', 'catalog'], ['app-1', 'app-2', 'support']),
                                 (plan['gate'], plan['coverage']['mode'], plan['coverage']['device_modules'], plan['groups']))
                self.assertEqual({runner.UNTICKETED: 'G1'}, plan['tickets'])
                host = [check.name for check in distributed.checks_for('host', plan)]
                self.assertEqual(['release-licenses', 'release-ephemeral'], host[-2:])
                self.assertNotIn('release-production', host)
        self.assertEqual(0, self.part('host', event='schedule')[0], self.stream.getvalue())
        self.assertEqual(['release-licenses', 'release-ephemeral'], self.harness.executed[-2:])

    def test_a_release_gate_needs_its_device_jobs_on_ci(self):
        self.assertEqual(['device job was skipped, expected success'],
                         distributed.job_failures('G3', {'plan': 'success', 'host': 'success', 'device': 'skipped'}))

    def test_input_change_during_collection_fails_the_command_and_report(self):
        self.all_parts()
        finish = runner.finish_run

        def edit_then_finish(*args, **kwargs):
            write(self.root, 'app/src/Main.kt', 'fun main() = println(3)\n')
            return finish(*args, **kwargs)

        with patch('distributed.runner.finish_run', side_effect=edit_then_finish):
            code, report = self.collect()
        self.assertNotEqual(0, code)
        self.assertEqual('failed', report['outcome'])
        self.assertIn('inputs changed while checks were running', report['failures'])

    def test_ci_cli_cannot_override_gate_or_retain_partial_execution(self):
        cli = Path(__file__).resolve().parents[1] / 'verify-ticket'
        for arguments in (
            ['--merge-candidate', 'HEAD', '--ci-part', 'host', '--gate', 'G1'],
            ['--merge-candidate', 'HEAD', '--ci-part', 'app-1', '--retain'],
            ['--merge-candidate', 'HEAD', '--ci-collect', 'build/parts', '--gate', 'G1'],
        ):
            with self.subTest(arguments=arguments):
                completed = subprocess.run([sys.executable, str(cli), *arguments], capture_output=True, text=True)
                self.assertEqual(2, completed.returncode)
                self.assertIn('error:', completed.stderr)


if __name__ == '__main__':
    unittest.main()

"""Selection must preserve reverse dependencies and fail closed on shared inputs."""
import io
import os
import json
import re
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import evidence
import gates
import runner
import selection
from support import EPIC, epic, git, make_repo, ticket, write
from test_runner import DEVICE, Harness


class SelectionTests(unittest.TestCase):
    def test_each_module_includes_its_consumers(self):
        expected = {'app':['app'], 'catalog':['catalog'], 'data':['data','app'],
                    'domain':['data','app'], 'design-system':['design-system','app','catalog']}
        for module, modules in expected.items():
            with self.subTest(module=module):
                scope = selection.coverage('a'*40, [f'{module}/src/main/Changed.kt'])
                self.assertEqual('affected', scope['mode'])
                self.assertEqual(modules, scope['device_modules'])

    def test_selected_modules_start_only_their_groups_and_app_starts_both_shards(self):
        def groups(*modules):
            return selection.groups({'device_modules': list(modules)})
        self.assertEqual(['app-1', 'app-2'], groups('app'))
        self.assertEqual(['support'], groups('catalog'))
        self.assertEqual(['support'], groups('data', 'design-system'))
        self.assertEqual(['app-1', 'app-2', 'support'], groups('data', 'app'))
        self.assertEqual([], groups())
        self.assertEqual(set(selection.MODULES), {m for covered in selection.GROUPS.values() for m in covered})

    def test_shared_build_tooling_unknown_paths_require_every_device_module(self):
        for path in ('gradle.properties','gradle/libs.versions.toml','data/build.gradle.kts',
                     'app/gradle.lockfile','scripts/verify-ticket','.github/workflows/quality.yml',
                     'docs/check.sh','new-module/src/main/X.kt','README.kt'):
            with self.subTest(path=path):
                scope=selection.coverage('a'*40,[path])
                self.assertEqual('full',scope['mode'])
                self.assertEqual(['data','design-system','app','catalog'],scope['device_modules'])

    def test_schemas_and_multiple_modules_union_their_consumers(self):
        self.assertEqual(['data','app','catalog'],selection.coverage('a'*40,
                         ['data/schemas/db/1.json','catalog/src/main/X.kt'])['device_modules'])

    def test_module_partition_changes_cannot_silently_outgrow_the_selection_graph(self):
        root=Path(__file__).resolve().parents[2]
        expected={'domain':set(),'data':{'domain'},'design-system':set(),
                  'app':{'domain','data','design-system'},'catalog':{'design-system'},
                  'prototype':{'design-system'}}
        settings=(root/'settings.gradle.kts').read_text()
        included=set(re.findall(r'"\:([\w-]+)"', '\n'.join(re.findall(r'include\((.*?)\)', settings, re.S))))
        self.assertEqual(set(expected),included)
        for module, dependencies in expected.items():
            actual=set(re.findall(r'project\("\:([\w-]+)"\)',(root/module/'build.gradle.kts').read_text()))
            self.assertEqual(dependencies,actual,f'Update/review the selection graph for {module}')

    def test_scope_cannot_omit_a_required_consumer_or_disguise_targeting_as_full(self):
        scope=selection.coverage('a'*40,['data/src/main/X.kt'])
        scope['device_modules']=['data']
        self.assertIsNotNone(selection.problem(scope,'G2','SELLO-004'))
        scope['mode']='full'
        self.assertIsNotNone(selection.problem(scope,'G2','SELLO-004'))

    def test_the_release_gate_always_covers_every_device_module(self):
        scope=selection.coverage('a'*40,['app/src/main/X.kt'],gate='G3')
        self.assertEqual(('full',['data','design-system','app','catalog']),(scope['mode'],scope['device_modules']))
        self.assertIsNone(selection.problem(scope,'G3','SELLO-004'))
        scope['device_modules']=['app']
        self.assertIsNotNone(selection.problem(scope,'G3','SELLO-004'))
        self.assertIsNotNone(selection.problem(dict(scope,device_modules=[]),'G3','SELLO-004'))


class LocalSelectionTests(unittest.TestCase):
    def setUp(self):
        device_env = patch.dict(os.environ, {"ANDROID_SERIAL": ""})
        device_env.start()
        self.addCleanup(device_env.stop)
        temp=tempfile.TemporaryDirectory(); self.addCleanup(temp.cleanup)
        self.root=make_repo(temp.name,epic(ticket('SELLO-003',status='Done'),
                               ticket('SELLO-004',dependencies='SELLO-003',gate='G2')))
        self.base=git(self.root,'rev-parse','HEAD').strip()
        self.harness=Harness(self.root)

    def run_ticket(self,**options):
        return runner.run('SELLO-004',self.root,execute=self.harness.execute,
                          environment=lambda root:{},devices=lambda:[DEVICE],stream=io.StringIO(),**options)

    def test_affected_local_ticket_runs_and_retains_only_the_dependency_closure(self):
        write(self.root,'app/src/Main.kt','fun main() = println(42)\n')
        code,path=self.run_ticket(affected_base=self.base)
        report=evidence.load(path)
        self.assertEqual(0,code)
        self.assertEqual(['app'],report['coverage']['device_modules'])
        self.assertEqual({'app'},set(report['checks'][-1]['modules']))
        self.assertIn('coverage affected (app)',evidence.retain(self.root,path))

    def test_default_ticket_command_runs_the_host_gate_without_devices(self):
        write(self.root,'app/src/Main.kt','fun main() = println(42)\n')
        code,path=self.run_ticket()
        report=evidence.load(path)
        self.assertEqual(0,code)
        self.assertEqual({'required':'G2','executed':'G1'},report['gate'])
        self.assertEqual('full',report['coverage']['mode'])
        self.assertEqual([],report['coverage']['device_modules'])
        self.assertNotIn('gradle-device',self.harness.executed)

    def test_explicit_g2_runs_every_device_module_even_with_a_base(self):
        write(self.root,'app/src/Main.kt','fun main() = println(42)\n')
        for options in ({'requested_gate':'G2'},{'affected_base':self.base,'requested_gate':'G2'}):
            code,path=self.run_ticket(**options)
            self.assertEqual(0,code)
            self.assertEqual('full',evidence.load(path)['coverage']['mode'])
            self.assertEqual(4,len(evidence.load(path)['checks'][-1]['modules']))

    def test_moved_and_deleted_paths_keep_the_original_module_in_scope(self):
        git(self.root,'mv','app/src/Main.kt','docs/Moved.md')
        plan=runner.candidate_plan(self.root,self.base)
        self.assertIn('app',plan['coverage']['device_modules'])
        self.assertIn('app/src/Main.kt',plan['coverage']['changed_paths'])
        self.assertEqual('G2',plan['gate'])

    def test_documentation_gate_needs_neither_java_nor_sdk(self):
        write(self.root,'docs/notes.md','A note.\n')
        def unavailable(root): raise AssertionError('Android environment must not be probed')
        code,paths=runner.run_merge_candidate(self.base,self.root,execute=self.harness.execute,
                     environment=unavailable,devices=lambda:[],stream=io.StringIO())
        self.assertEqual(0,code)
        report=evidence.load(paths[0])
        self.assertEqual('G0',report['gate']['executed'])
        self.assertNotIn('gradle-host',self.harness.executed)
        self.assertIn('architecture',self.harness.executed)

    def test_old_v1_full_reports_remain_readable(self):
        code,path=self.run_ticket()
        report=json.loads(path.read_text());report['schema_version']=1;report.pop('coverage')
        path.write_text(json.dumps(report))
        self.assertEqual(0,code)
        self.assertEqual(1,evidence.load(path)['schema_version'])

    def test_passing_targeted_evidence_cannot_drop_the_selected_modules(self):
        write(self.root,'app/src/Main.kt','fun main() = println(42)\n')
        _,path=self.run_ticket(affected_base=self.base)
        report=json.loads(path.read_text());report['checks'][-1]['modules']={}
        path.write_text(json.dumps(report))
        with self.assertRaisesRegex(evidence.EvidenceError,'executed modules differ'):
            evidence.load(path)

    def test_a_documentation_report_cannot_be_relabeled_as_ticket_acceptance(self):
        write(self.root,'docs/notes.md','A note.\n')
        _,paths=runner.run_merge_candidate(self.base,self.root,execute=self.harness.execute,stream=io.StringIO())
        report=json.loads(paths[0].read_text());report['ticket']='SELLO-004'
        paths[0].write_text(json.dumps(report))
        with self.assertRaisesRegex(evidence.EvidenceError,'documentation coverage'):
            evidence.retain(self.root,paths[0])

    def test_local_selection_matches_the_hosted_pr_plan(self):
        import distributed
        write(self.root,'data/src/main/Store.kt','class Store\n')
        with patch.dict(os.environ,{'GITHUB_ACTIONS':'false','GITHUB_RUN_ID':'','GITHUB_RUN_ATTEMPT':'','QUALITY_EXECUTION_ID':'parity'}):
            plan=distributed.context(self.root,self.base,'pull_request')
        code,path=self.run_ticket(affected_base=self.base)
        self.assertEqual(0,code)
        self.assertEqual(plan['coverage'],evidence.load(path)['coverage'])

    def test_local_gate_identifies_only_the_selected_device(self):
        with patch.dict(os.environ,{'ANDROID_SERIAL':DEVICE['serial']}):
            code,path=runner.run('SELLO-004',self.root,requested_gate='G2',execute=self.harness.execute,
                      environment=lambda root:{},devices=lambda:[DEVICE,dict(DEVICE,serial='other')],stream=io.StringIO())
        self.assertEqual(0,code)
        self.assertEqual([DEVICE],evidence.load(path)['devices'])

#!/usr/bin/env python3
"""Exercise release behavior against temporary repositories and local bare remotes."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).with_name('release.sh').resolve()

class ReleaseTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.repo = self.root / 'repo'
        self.remote = self.root / 'remote.git'
        self.env = dict(os.environ, GIT_CONFIG_NOSYSTEM='1', GIT_CONFIG_GLOBAL='/dev/null')
        self.run_command('git', 'init', '--bare', str(self.remote), cwd=self.root)
        self.run_command('git', 'init', '-b', 'main', str(self.repo), cwd=self.root)
        self.git('config', 'user.name', 'Release Test')
        self.git('config', 'user.email', 'release@example.invalid')
        self.git('remote', 'add', 'origin', str(self.remote))
        (self.repo / 'scripts').mkdir()
        shutil.copyfile(SCRIPT, self.repo / 'scripts/release.sh')
        (self.repo / 'scripts/test.sh').write_text('echo tests-ran\nexit "${TEST_EXIT:-0}"\n')
        (self.repo / 'app').mkdir()
        (self.repo / 'app/build.gradle.kts').write_text('versionCode = 7\nversionName = "1.2.3"\n')
        (self.repo / '.gitignore').write_text('dist/\n')
        self.git('add', '.')
        self.git('commit', '-m', 'Initial')
        self.original = self.git('rev-parse', 'HEAD').stdout.strip()
        self.git('push', 'origin', 'main')

    def run_command(self, *args, cwd=None, **kwargs):
        return subprocess.run(args, cwd=cwd or self.repo, env=self.env,
                              text=True, capture_output=True, check=True, **kwargs)

    def git(self, *args):
        return self.run_command('git', *args)

    def release(self, choice="", *args, **env):
        return subprocess.run(['bash', 'scripts/release.sh', *args], cwd=self.repo,
                              env=dict(self.env, **env), input=choice + '\n',
                              text=True, capture_output=True)

    def test_all_version_choices(self):
        for choice, old, version, code in [('patch', '1.2.3', '1.2.4', 8), ('minor', '1.2.4', '1.3.0', 9), ('major', '1.3.0', '2.0.0', 10)]:
            with self.subTest(choice=choice):
                result = self.release(choice)
                self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
                self.assertIn(f'{old} → {version}', result.stdout)
                self.assertIn(f'versionCode = {code}', (self.repo / 'app/build.gradle.kts').read_text())
                self.assertEqual(self.git('cat-file', '-t', f'v{version}').stdout.strip(), 'tag')
                self.assertIn(f'refs/tags/v{version}', self.git('ls-remote', '--tags', 'origin').stdout)
                head = self.git('rev-parse', 'HEAD').stdout.strip()
                self.assertEqual(self.git('rev-parse', f'v{version}^{{commit}}').stdout.strip(), head)
                self.assertIn(f'{head}\trefs/heads/main', self.git('ls-remote', '--heads', 'origin').stdout)
                self.assertIn(f'versionName = "{version}"', (self.repo / 'app/build.gradle.kts').read_text())
                self.assertEqual(self.git('status', '--porcelain').stdout, '')

    def test_failed_tests_do_not_change_branch_or_publish(self):
        result = self.release('patch', TEST_EXIT='1')
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(self.git('rev-parse', 'HEAD').stdout.strip(), self.original)
        self.assertEqual(self.git('tag').stdout, '')
        self.assertEqual(self.git('ls-remote', '--tags', 'origin').stdout, '')
        self.assertEqual(self.git('status', '--porcelain').stdout, '')

    def test_dirty_tree_is_rejected(self):
        (self.repo / 'uncommitted').touch()
        result = self.release('patch')
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn('tests-ran', result.stdout)
        self.assertEqual(self.git('tag').stdout, '')

    def test_existing_remote_tag_is_rejected(self):
        self.git('tag', 'v1.2.4')
        self.git('push', 'origin', 'refs/tags/v1.2.4')
        self.git('tag', '-d', 'v1.2.4')
        result = self.release('patch')
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn('tests-ran', result.stdout)
        self.assertEqual(self.git('rev-parse', 'HEAD').stdout.strip(), self.original)

    def test_failed_push_retains_tested_tag(self):
        hook = self.remote / 'hooks/update'
        hook.write_text('#!/bin/sh\ncase "$1" in refs/tags/*) exit 1;; esac\nexit 0\n')
        hook.chmod(0o755)
        result = self.release('patch')
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(self.git('tag').stdout.strip(), 'v1.2.4')
        self.assertEqual(self.git('ls-remote', '--tags', 'origin').stdout, '')
        self.assertIn('Local release commit and tag', result.stderr)
        self.assertIn(f'{self.original}\trefs/heads/main', self.git('ls-remote', '--heads', 'origin').stdout)


    def test_remote_branch_conflict_does_not_publish_tag(self):
        (self.repo / 'remote-change').write_text('Remote-only change')
        self.git('add', 'remote-change')
        self.git('commit', '-m', 'Remote advanced')
        remote_head = self.git('rev-parse', 'HEAD').stdout.strip()
        self.git('push', 'origin', 'main')
        self.git('reset', '--hard', self.original)
        result = self.release('patch')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn(f'{remote_head}\trefs/heads/main', self.git('ls-remote', '--heads', 'origin').stdout)
        self.assertEqual(self.git('ls-remote', '--tags', 'origin').stdout, '')


    def test_skip_tests_pushes_branch_and_tag_without_running_suite(self):
        result = self.release('patch', '--skip-tests', TEST_EXIT='99')
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertNotIn('tests-ran', result.stdout)
        head = self.git('rev-parse', 'HEAD').stdout.strip()
        self.assertNotEqual(head, self.original)
        self.assertEqual(self.git('rev-parse', 'v1.2.4^{commit}').stdout.strip(), head)
        self.assertIn(f'{head}\trefs/heads/main', self.git('ls-remote', '--heads', 'origin').stdout)
        self.assertIn('refs/tags/v1.2.4', self.git('ls-remote', '--tags', 'origin').stdout)

    def test_tests_only_needs_no_remote_identity_clean_tree_or_prompt(self):
        self.git('remote', 'remove', 'origin')
        self.git('config', '--unset', 'user.name')
        self.git('config', '--unset', 'user.email')
        (self.repo / 'uncommitted').touch()
        before = self.git('status', '--porcelain').stdout
        result = self.release('', '--tests-only')
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn('tests-ran', result.stdout)
        self.assertNotIn('Current version:', result.stdout)
        self.assertEqual(self.git('rev-parse', 'HEAD').stdout.strip(), self.original)
        self.assertEqual(self.git('tag').stdout, '')
        self.assertEqual(self.git('status', '--porcelain').stdout, before)

    def test_tests_only_propagates_failure(self):
        result = self.release('', '--tests-only', TEST_EXIT='23')
        self.assertEqual(result.returncode, 23)
        self.assertEqual(self.git('rev-parse', 'HEAD').stdout.strip(), self.original)
        self.assertEqual(self.git('tag').stdout, '')

    def test_invalid_modes_do_not_test_or_release(self):
        for args in [('--tests-only', '--skip-tests'), ('--skip-tests', '--tests-only'),
                     ('--tests-only', 'origin'), ('--unknown',), ('origin', 'extra')]:
            with self.subTest(args=args):
                result = self.release('', *args)
                self.assertNotEqual(result.returncode, 0)
                self.assertNotIn('tests-ran', result.stdout)
                self.assertEqual(self.git('rev-parse', 'HEAD').stdout.strip(), self.original)
                self.assertEqual(self.git('tag').stdout, '')

    def test_skip_tests_accepts_custom_remote(self):
        self.git('remote', 'rename', 'origin', 'publish')
        result = self.release('minor', 'publish', '--skip-tests')
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn('refs/tags/v1.3.0', self.git('ls-remote', '--tags', 'publish').stdout)

if __name__ == '__main__':
    unittest.main()

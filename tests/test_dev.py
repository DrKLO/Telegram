"""Workflow checks that never contact a device or use real credentials."""
import importlib.machinery
import importlib.util
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

loader = importlib.machinery.SourceFileLoader('lab_dev', str(Path(__file__).resolve().parents[1] / 'dev'))
spec = importlib.util.spec_from_loader(loader.name, loader)
lab = importlib.util.module_from_spec(spec)
loader.exec_module(lab)


class WorkflowTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.local = self.root / 'local.properties'
        self.root_patch = patch.object(lab, 'ROOT', self.root)
        self.local_patch = patch.object(lab, 'LOCAL', self.local)
        self.root_patch.start()
        self.local_patch.start()
        self.addCleanup(self.root_patch.stop)
        self.addCleanup(self.local_patch.stop)

    def test_local_updates_preserve_credentials_comments_and_spaces(self):
        self.local.write_text('# Keep this note\ntelegram.apiId=12345\ntelegram.apiHash=' + 'a' * 32 + '\nsdk.dir=/old/path\n')
        lab.save_properties({'sdk.dir': '/test/Android SDK', 'featuresLab.abi': 'arm64-v8a'})
        self.assertEqual(lab.properties()['sdk.dir'], '/test/Android SDK')
        self.assertEqual(lab.properties()['telegram.apiHash'], 'a' * 32)
        self.assertIn('# Keep this note', self.local.read_text())
        self.assertEqual(os.stat(self.local).st_mode & 0o777, 0o600)
        self.assertFalse(self.local.with_suffix('.properties.tmp').exists())

    def test_normal_build_cannot_use_missing_credentials(self):
        with patch.object(lab, 'bootstrap'), patch.object(lab, 'run') as invoke:
            with self.assertRaisesRegex(SystemExit, 'configure'):
                lab.build()
            invoke.assert_not_called()

    def test_credential_validation_rejects_overflow_and_placeholder(self):
        for value in ['0', '2147483648', 'not-a-number']:
            lab.save_properties({'telegram.apiId': value, 'telegram.apiHash': 'a' * 32})
            self.assertFalse(lab.credentials_valid())
        lab.save_properties({'telegram.apiId': '12345', 'telegram.apiHash': 'placeholder'})
        self.assertFalse(lab.credentials_valid())

    def test_multiple_devices_require_explicit_selection(self):
        response = subprocess.CompletedProcess([], 0, 'List of devices attached\nfirst\tdevice\nsecond\tdevice\n', '')
        with patch.dict(os.environ, {}, clear=True), patch.object(lab, 'adb', return_value=response):
            with self.assertRaisesRegex(SystemExit, 'Multiple devices'):
                lab.device(required=True)
        with patch.dict(os.environ, {'ANDROID_SERIAL': 'second'}, clear=True), patch.object(lab, 'adb', return_value=response):
            self.assertEqual(lab.device(required=True), 'second')

    def test_unauthorized_phone_is_not_selected(self):
        response = subprocess.CompletedProcess([], 0, 'List of devices attached\nphone\tunauthorized\n', '')
        with patch.dict(os.environ, {}, clear=True), patch.object(lab, 'adb', return_value=response):
            with self.assertRaisesRegex(SystemExit, 'No authorized device'):
                lab.device(required=True)

    def test_offline_build_needs_no_credentials_and_sets_explicit_mode(self):
        apk = self.root / 'TMessagesProj_App/build/outputs/apk/afat/debug/app.apk'
        apk.parent.mkdir(parents=True)
        apk.touch()
        with patch.object(lab, 'bootstrap'), patch.object(lab, 'device', return_value=None), \
                patch.object(lab, 'environment', return_value={}), patch.object(lab, 'run') as invoke:
            self.assertEqual(lab.build(offline=True), apk)
            command = invoke.call_args.args[0]
            self.assertIn('-PfeaturesLabOffline=true', command)
            self.assertIn('-PfeaturesLabCompileOnly=false', command)

    def test_offline_install_targets_separate_package_without_clearing_data(self):
        with patch.object(lab, 'device', return_value='emulator-5554'), \
                patch.object(lab, 'phone_abi', return_value='arm64-v8a'), \
                patch.object(lab, 'build', return_value='demo.apk') as build, patch.object(lab, 'adb') as invoke:
            lab.install(offline=True)
            build.assert_called_once_with(offline=True)
            self.assertEqual(invoke.call_args_list[0].args, ('install', '-r', 'demo.apk'))
            self.assertEqual(invoke.call_args_list[1].args[-1],
                             'org.telegram.features.offline.beta/org.telegram.features.FeaturesLabActivity')

    def test_prefers_supported_arm64_and_rejects_old_android(self):
        replies = [subprocess.CompletedProcess([], 0, '36\n', ''), subprocess.CompletedProcess([], 0, 'arm64-v8a,armeabi-v7a\n', '')]
        with patch.object(lab, 'adb', side_effect=replies):
            self.assertEqual(lab.phone_abi('phone'), 'arm64-v8a')
        with patch.object(lab, 'adb', return_value=subprocess.CompletedProcess([], 0, '22\n', '')):
            with self.assertRaisesRegex(SystemExit, 'API 23'):
                lab.phone_abi('phone')


if __name__ == '__main__':
    unittest.main()

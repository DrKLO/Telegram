import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('contract', Path(__file__).resolve().parents[1] / 'check_build_contract.py')
contract = importlib.util.module_from_spec(spec)
spec.loader.exec_module(contract)


class ConfigurationBoundaryTest(unittest.TestCase):
    def test_rejects_nested_and_case_varied_owner_configuration(self):
        for path in ['google-services.json', 'app/src/debug/google-services.json',
                     'app/Google-Services.JSON', 'huawei/agconnect-services.json', '.env', '.env.prod']:
            with self.subTest(path=path):
                self.assertTrue(contract.forbidden(path))

    def test_rejects_signing_and_private_key_formats(self):
        for path in ['release.jks', 'nested/key.keystore', 'certificate.p12', 'certificate.pfx', 'private.key']:
            with self.subTest(path=path):
                self.assertTrue(contract.forbidden(path))

    def test_allows_source_and_documented_environment_example(self):
        for path in ['.env.example', 'docs/BUILDING.md', 'gradle.properties', 'key.java']:
            with self.subTest(path=path):
                self.assertFalse(contract.forbidden(path))


if __name__ == '__main__':
    unittest.main()

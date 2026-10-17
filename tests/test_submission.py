"""The review package must not accidentally collect local account or build data."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

spec = importlib.util.spec_from_file_location('submission', Path(__file__).resolve().parents[1] / 'Tools/prepare_stars_submission.py')
submission = importlib.util.module_from_spec(spec)
spec.loader.exec_module(submission)


class SubmissionTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.revision = 'a' * 40
        self.artifacts = self.root / 'artifacts'
        images = self.artifacts / 'stars-privacy'
        images.mkdir(parents=True)
        for name in submission.SCREENSHOTS:
            (images / name).write_bytes(b'fixture image')
        (self.artifacts / 'stars-privacy-demo.mp4').write_bytes(b'fixture video')
        self.metadata = {'source_commit': self.revision, 'working_tree_dirty': False,
                         'video_sha256': submission.sha256(self.artifacts / 'stars-privacy-demo.mp4'),
                         'scenario_passed': True, 'duration_seconds': 110}
        self.write_metadata()
        self.root_patch = patch.object(submission, 'ROOT', self.root)
        self.root_patch.start()
        self.addCleanup(self.root_patch.stop)

    def write_metadata(self):
        (self.artifacts / 'stars-privacy-demo.json').write_text(json.dumps(self.metadata))

    def git(self, *args):
        if args[0] == 'status': return b''
        if args[0] == 'rev-parse': return self.revision.encode()
        if args[0] == 'diff': return b'reviewed source patch'
        if args[0] == 'show': return b'committed document'
        raise AssertionError(args)

    def test_dirty_checkout_is_rejected(self):
        with patch.object(submission, 'git', return_value=b' M source.java'):
            with self.assertRaisesRegex(SystemExit, 'Commit the reviewed source'):
                submission.main()

    def test_video_must_match_revision_and_hash(self):
        with patch.object(submission, 'git', side_effect=self.git):
            self.metadata['source_commit'] = 'b' * 40
            self.write_metadata()
            with self.assertRaisesRegex(SystemExit, 'Record this committed revision'):
                submission.main()
            self.metadata['source_commit'] = self.revision
            self.metadata['video_sha256'] = 'wrong'
            self.write_metadata()
            with self.assertRaisesRegex(SystemExit, 'Video does not match'):
                submission.main()

    def test_only_named_review_files_enter_zip(self):
        for name in ['local.properties', 'debug.keystore', 'account.session']:
            (self.root / name).write_text('DO NOT DISTRIBUTE')
        for name in ['private.log', 'app.apk', 'unrelated.png']:
            (self.artifacts / name).write_text('DO NOT DISTRIBUTE')
        with patch.object(submission, 'git', side_effect=self.git):
            submission.main()
        archive = next(self.artifacts.glob('submission-*.zip'))
        with zipfile.ZipFile(archive) as bundle:
            self.assertIsNone(bundle.testzip())
            for name in bundle.namelist():
                self.assertNotIn(b'DO NOT DISTRIBUTE', bundle.read(name))
                self.assertFalse(name.endswith(('.apk', '.log', '.keystore', '.session', '.properties')))
        destination = archive.with_suffix('')
        for line in (destination / 'SHA256SUMS').read_text().splitlines():
            digest, relative = line.split('  ', 1)
            self.assertEqual(digest, submission.sha256(destination / relative))
        self.assertIn(self.revision, (destination / 'MESSAGE.txt').read_text())


if __name__ == '__main__':
    unittest.main()

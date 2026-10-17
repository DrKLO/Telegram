"""An edit must use complete, in-bounds capture evidence without changing UI timing."""
import importlib.util
from pathlib import Path
import unittest
import struct

spec = importlib.util.spec_from_file_location('video_edit', Path(__file__).resolve().parents[1] / 'Tools/edit_stars_demo.py')
edit = importlib.util.module_from_spec(spec)
spec.loader.exec_module(edit)


def marker(name, edge, milliseconds):
    return f'INSTRUMENTATION_STATUS: demo_clip={name}\nINSTRUMENTATION_STATUS: demo_edge={edge}\nINSTRUMENTATION_STATUS: demo_time_ms={milliseconds}\nINSTRUMENTATION_STATUS_CODE: 2\n'


class VideoEditTests(unittest.TestCase):
    def recordings(self):
        return [{'file': 'native-demo-0.mp4', 'sha256': 'a' * 64, 'start_seconds': 10,
                 'duration_seconds': 200,
                 'marks': {name: (15 + i * 5, 18 + i * 5) for i, (name, _, _) in enumerate(edit.CUTS)}}]

    def test_markers_pair_without_changing_the_device_clock(self):
        self.assertEqual({'privacy': (12.5, 17.75)}, edit.parse_marks(marker('privacy', 'start', 12500) + marker('privacy', 'end', 17750)))
        for invalid in [marker('privacy', 'start', 1), marker('privacy', 'end', 2),
                        marker('privacy', 'start', 2) + marker('privacy', 'end', 1),
                        marker('a', 'start', 1) + marker('b', 'start', 2)]:
            with self.assertRaises(ValueError): edit.parse_marks(invalid)

    def test_every_required_clip_has_in_bounds_evidence(self):
        recordings = self.recordings()
        plan = edit.plan_cuts(recordings)
        self.assertEqual([name for name, _, _ in edit.CUTS], [c['id'] for c in plan])
        self.assertEqual(4.75, plan[0]['start_seconds'])
        self.assertEqual(8.15, plan[0]['end_seconds'])
        self.assertTrue(all(c['source_sha256'] == 'a' * 64 for c in plan))
        recordings[0]['marks'].pop('slider_percentile')
        with self.assertRaisesRegex(ValueError, 'not recorded'): edit.plan_cuts(recordings)
        recordings = self.recordings()
        recordings[0]['marks']['slider_percentile'] = (9, 11)
        with self.assertRaisesRegex(ValueError, 'outside'): edit.plan_cuts(recordings)

    def test_pacing_and_duplicate_evidence_are_rejected(self):
        recordings = self.recordings()
        with self.assertRaisesRegex(ValueError, 'Duplicate'): edit.plan_cuts(recordings * 2)
        recordings[0]['marks']['slider_percentile'] = (70, 195)
        with self.assertRaisesRegex(ValueError, 'pacing'): edit.plan_cuts(recordings)

    def test_frame_clock_uses_encoded_frame_time(self):
        metadata = b'#VV1NSC0PET1ME2#' + struct.pack('<IqIQQ', 2, 999, 2, 12300000000, 13500000000)
        clock = edit.frame_clock(b'mp4 prefix' + metadata + b'mp4 suffix')
        self.assertEqual(12.3, clock['start_seconds'])
        self.assertEqual(13.5, clock['last_frame_seconds'])
        self.assertEqual(2, clock['frame_count'])

    def test_missing_truncated_or_invalid_frame_clock_is_rejected(self):
        magic = b'#VV1NSC0PET1ME2#'
        valid = magic + struct.pack('<IqIQQ', 2, 0, 2, 100, 200)
        for invalid in [b'', magic, valid[:-1], valid + valid,
                        magic + struct.pack('<IqI', 1, 0, 0),
                        magic + struct.pack('<IqIQQ', 2, 0, 2, 200, 100)]:
            with self.assertRaises(ValueError): edit.frame_clock(invalid)

    def test_caption_band_has_separate_positions_and_escapes_overrides(self):
        self.assertEqual('0:01:00.00', edit.ass_time(59.999))
        captions = edit.subtitles('Native {caption}', 'Amounts stay visible', 4.5)
        self.assertIn('PlayResY: 1568', captions)
        self.assertIn('Native \\{caption\\}', captions)
        self.assertIn('0:00:04.50', captions)
        self.assertIn('Style: Title', captions)
        self.assertIn('Style: Caption', captions)


if __name__ == '__main__': unittest.main()

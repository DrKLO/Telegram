#!/usr/bin/env python3
"""Package a committed revision, reviewed recording and documentation; never publish or send."""
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
BASE = 'd1a1265'
REPOSITORY = 'https://github.com/yes-i-like-pina-coladas/Telegram'
DOCUMENTS = ['STARS_PRIVACY.md', 'SUBMISSION.md', 'DEVELOPMENT.md', 'OFFLINE.md']
SCREENSHOTS = ['stars-privacy-settings-light.png', 'stars-privacy-settings-dark.png',
               'stars-private-reaction-sheet.png', 'stars-private-transaction.png',
               'stars-channel-dark-polished.png', 'stars-privacy-settings-russian.png']


def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT)


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    if git('status', '--porcelain').strip():
        raise SystemExit('Commit the reviewed source and docs first; the package must match one clean revision.')
    revision = git('rev-parse', 'HEAD').decode().strip()
    artifacts = ROOT / 'artifacts'
    video = artifacts / 'stars-privacy-demo.mp4'
    metadata = json.loads((artifacts / 'stars-privacy-demo.json').read_text())
    if metadata['source_commit'] != revision or metadata['working_tree_dirty']:
        raise SystemExit('Record this committed revision with python3 Tools/record_stars_demo.py first.')
    if metadata['video_sha256'] != sha256(video) or not metadata['scenario_passed']:
        raise SystemExit('Video does not match its successful recording metadata.')
    destination = artifacts / ('submission-' + revision[:12])
    if destination.exists():
        raise SystemExit(f'Package already exists: {destination}. Preserving it; choose the existing package.')
    destination.mkdir()
    shutil.copy2(video, destination / video.name)
    shutil.copy2(artifacts / 'stars-privacy-demo.json', destination / 'recording.json')
    for name in DOCUMENTS:
        (destination / name).write_bytes(git('show', revision + ':docs/' + name))
    # A feature-only patch, applied on the published offline setup baseline.
    (destination / 'feature.patch').write_bytes(git('diff', '--binary', BASE, revision))
    (destination / 'LICENSE').write_bytes(git('show', revision + ':LICENSE'))
    images = destination / 'screenshots'
    images.mkdir()
    for name in SCREENSHOTS:
        shutil.copy2(artifacts / 'stars-privacy' / name, images / name)
    (destination / 'MESSAGE.txt').write_text(
        'Private Star Reactions — Telegram Android concept\n\n'
        'A private-by-default way to support channels: anonymous reactions, minimal receipts, '
        'a 20-second Undo, and past private activity that stays hidden after opting out. '
        'Built with Telegram\'s native components and animations, with channel controls, '
        'private donor percentiles and history anonymisation.\n\n'
        f'Source: {REPOSITORY}/tree/{revision}\n'
        f'Feature diff: {REPOSITORY}/compare/{BASE}...{revision}\n'
        'Demo: attach stars-privacy-demo.mp4\n\n'
        'The video runs in the offline Android emulator with sample data and no real payments. '
        'Ordinary anonymous paid reactions use the existing API in the live client. '
        'Channel enforcement, live anonymity, history redaction and separate future public '
        'contributions on previously private posts use local API mocks. '
        'STARS_PRIVACY.md describes the required server work. This is a working client prototype, '
        'not a claim of full payment anonymity or secure deletion.\n')
    (destination / 'REVIEW.txt').write_text(
        f'Private Star Reactions\nSource revision: {revision}\n'
        f'Offline setup baseline: {git("rev-parse", BASE).decode().strip()}\n'
        f'Video: {metadata["duration_seconds"]:.1f} seconds, actual emulator capture; no audio.\n\n'
        'Source is available at the pinned public URL in MESSAGE.txt. Clone recursively using '
        'DEVELOPMENT.md, check out the revision above, then run ./dev demo. The patch is for '
        'review/application on the offline setup baseline, not a standalone source archive.\n\n'
        'Includes only the named demo, selected fixture screenshots, committed documentation, '
        'feature patch and GPL license. No APK, credentials, signing keys, account data or raw logs. '
        'Local paths, emulator serial numbers and SDK paths are excluded from recording metadata.\n\n'
        'Read SUBMISSION.md for validation and limitations. Review MESSAGE.txt and the video '
        'before manually messaging @designers. This tool does not upload, publish or send anything.\n')
    files = sorted(path for path in destination.rglob('*') if path.is_file())
    (destination / 'SHA256SUMS').write_text(''.join(f'{sha256(path)}  {path.relative_to(destination).as_posix()}\n' for path in files))
    archive = destination.with_suffix('.zip')
    with zipfile.ZipFile(archive, 'w', compression=zipfile.ZIP_DEFLATED) as bundle:
        for path in sorted(destination.rglob('*')):
            if path.is_file():
                bundle.write(path, str(Path(destination.name) / path.relative_to(destination)))
    print(f'Review package: {destination}\nZIP: {archive}')


if __name__ == '__main__':
    main()

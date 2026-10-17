#!/usr/bin/env python3
"""Package a committed revision, reviewed recording and documentation; never publish or send."""
import hashlib
import html
import json
from pathlib import Path
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
BASE = 'd1a1265'
REPOSITORY = 'https://github.com/yes-i-like-pina-coladas/Telegram'
DOCUMENTS = ['STARS_PRIVACY.md', 'SUBMISSION.md', 'DEVELOPMENT.md', 'OFFLINE.md', 'NATIVE_UI.md', 'QA.md']
NATIVE_SCREENSHOT_CAPTIONS = {
    '02a-settings-stars-entry.png': ('Telegram Settings', 'The real Settings tab and Telegram Stars entry, with native rounded sections and bottom navigation.'),
    '03-my-stars.png': ('My Stars', 'Telegram’s production balance page, animated header and purchase actions.'),
    '03b-public-history.png': ('Before privacy', 'Illustrative transactions show public channel names and bundled avatars, with today’s date and signed amounts.'),
    '03c-public-receipt.png': ('Identifiable receipt', 'The native receipt includes the sample channel and post link before privacy is enabled. No real payment is represented.'),
    '03a-privacy-enabled.png': ('Make my Stars private', 'The gold Star and closing-lock motion sit directly above the real transaction history.'),
    '05a-transaction-history.png': ('Privacy beside history', 'The animated privacy control sits above the real transaction list. Amounts remain visible.'),
    '05-history-privacy-notice.png': ('New reactions only', 'Turning privacy off explains that previously protected activity stays private.'),
    '04-purchase.png': ('Purchase notice', 'The production package sheet explains visibility; offline prices are samples and no purchase is made.'),
    '04b-purchase-privacy-off.png': ('Visibility warning', 'With privacy off, the native purchase notice explains that reactions may reveal the profile in Top Senders.'),
    '04c-purchase-privacy-enabled.png': ('Enable privacy while buying', 'The inline link enables the same personal preference and updates the notice immediately.'),
    '06-private-receipt.png': ('Minimal receipt', 'A sample transaction opens a native receipt with its Stars amount, ID and date/time.'),
    '06b-anonymise-one-receipt.png': ('Anonymise one record', 'Long-pressing a row in the actual history opens the native irreversible-action confirmation.'),
    '07-privacy-security.png': ('Privacy & Security', 'The personal Stars Privacy entry is part of Telegram’s real privacy settings.'),
    '08-stars-privacy-settings.png': ('Personal preference', 'Native personal controls reached through Settings → Privacy & Security.'),
    '09-anonymise-history.png': ('Anonymise history', 'Native confirmation explains the irreversible local action and retained fields.'),
    '10-channel.png': ('Channel post', 'The real chat screen, message bubble and Star reaction use sample channel data.'),
    '11-post-reaction-sheet.png': ('Post reaction', 'Long-pressing the real Star opens Telegram’s production slider sheet.'),
    '12-post-undo.png': ('20-second Undo', 'A Star tap runs the native effect and pending-send countdown; a completed send creates a receipt.'),
    '17-channel-privacy.png': ('Channel admin controls', 'Keep Stars anonymous sits below Enable Paid Reactions, with native Update/Discard behavior. Enforcement remains a local API mock.'),
    '18-channel-percentile.png': ('Your donor percentile', 'Large animated numbers replace named rankings. Only the donor sees this coarse aggregate; personal privacy can remain enabled.'),
    '18a-percentile-preview-100.png': ('Projected rank · Top 100%', 'Moving the real Stars slider previews a donor-only percentile without sending a reaction.'),
    '18b-percentile-preview-10.png': ('Projected rank · Top 10%', 'As the amount increases, Telegram’s rolling digits animate through smaller percentile buckets. This is a labelled estimate.'),
    '20-live-story.png': ('Live-story viewer', 'The production story and Star controls use sample stream metadata; no video broadcast is connected.'),
    '21-live-stars-sheet.png': ('Live Stars', 'The native live sheet uses a subtle monochrome lock in its anonymous chip; sending is simulated.'),
    '22-live-undo.png': ('Live Undo', 'The production live Star animation and 20-second cancellation path.'),
    '24-live-percentile.png': ('Private live support', 'The live sheet pairs a native lock chip with the donor’s own percentile. No public ranking or identity is shown.'),
    '31-history-dark.png': ('Native dark theme', 'The real My Stars history, privacy control and signed amounts in Telegram’s dark palette.'),
}
SCREENSHOT_CAPTIONS = {
    'stars-history-privacy-light.png': ('Make my Stars private', 'The animated native privacy control sits directly above transaction history.'),
    'stars-history-privacy-dark.png': ('History control · dark', 'The same control and minimal receipt rows use Telegram’s dark theme.'),
    'stars-privacy-settings-light.png': ('Personal settings', 'Private reactions, history anonymisation and the prototype’s limitations in native settings cells.'),
    'stars-privacy-off-notice.png': ('Opt-out notice', 'A brief notice explains that only new reactions can become public.'),
    'stars-purchase-privacy-light.png': ('Before buying Stars', 'The purchase sheet explains profile visibility and offers a one-tap privacy action.'),
    'stars-purchase-privacy-enabled.png': ('Purchase privacy enabled', 'The notice updates immediately after enabling privacy; sample packages cannot purchase.'),
    'stars-private-reaction-sheet.png': ('Private post reaction', 'The native slider and send action remain, with identity and leaderboard controls hidden.'),
    'stars-twenty-second-undo.png': ('20-second Undo', 'The pending reaction keeps Telegram’s native countdown and Undo action.'),
    'stars-private-transaction.png': ('Private receipt', 'Only the signed Stars amount, transaction ID and date/time remain.'),
    'stars-private-amounts.png': ('Amounts retained', 'Private history retains signed Stars amounts beside each minimal record.'),
    'stars-private-live-sheet.png': ('Live Stars', 'Live reactions keep the native sheet and animation flow; unsupported anonymity uses a labelled mock.'),
    'stars-private-live-receipt.png': ('Live receipt', 'The completed simulated live reaction produces a minimal receipt with its actual Stars amount.'),
    'stars-channel-admin.png': ('Channel controls', 'Admins can require anonymous reactions and allow a donor-only percentile.'),
    'stars-channel-private-sheet.png': ('Private-channel reaction', 'The channel policy removes named rankings and uses an anonymous send preview.'),
    'stars-private-donor-percentile.png': ('Donor-only percentile', 'A large animated Top N% figure replaces named rankings in the private-channel mock.'),
    'stars-channel-expired.png': ('After five minutes', 'The donor percentile has expired while the sheet remains open. Selected Stars stay hidden from the start.'),
    'stars-anonymise-receipt-confirmation.png': ('Anonymise one receipt', 'A native confirmation explains the irreversible local action and retained fields.'),
    'stars-anonymise-history-confirmation.png': ('Anonymise history', 'The same native confirmation applies to existing history, including unloaded pages.'),
    'stars-retroactive-hidden.png': ('Past activity stays hidden', 'After opting out, other donors can appear; the user’s old public position stays hidden.'),
    'stars-privacy-settings-russian.png': ('Russian settings', 'Russian labels and explanatory copy fit the native settings layout.'),
    'stars-history-privacy-russian.png': ('Russian history control', 'The direct privacy control and amount-preserving history in Russian.'),
    'stars-channel-dark-polished.png': ('Channel controls · dark', 'Admin settings and dependent controls use Telegram’s dark theme.'),
    'stars-privacy-settings-dark.png': ('Personal settings · dark', 'Privacy and history actions share the native dark palette.'),
    'stars-purchase-privacy-dark.png': ('Purchase notice · dark', 'The visibility notice stays within the native dark purchase sheet.'),
}
SCREENSHOTS = list(SCREENSHOT_CAPTIONS)


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
    qa = json.loads((artifacts / 'stars-privacy-qa.json').read_text())
    if qa['source_commit'] != revision or qa['working_tree_dirty'] or not qa['scenario_passed']:
        raise SystemExit('Run ./dev privacy-test on this clean committed revision before packaging.')
    for name in SCREENSHOTS:
        if qa['screenshots'].get(name) != sha256(artifacts / 'stars-privacy' / name):
            raise SystemExit(f'Screenshot does not match its QA evidence: {name}')
    native_qa = json.loads((artifacts / 'native-ui-qa.json').read_text())
    if native_qa['source_commit'] != revision or native_qa['working_tree_dirty'] or not native_qa['scenario_passed']:
        raise SystemExit('Run ./dev native-test on this clean committed revision before packaging.')
    for name in NATIVE_SCREENSHOT_CAPTIONS:
        if native_qa['screenshots'].get(name) != sha256(artifacts / 'native-ui' / name):
            raise SystemExit(f'Native screenshot does not match its QA evidence: {name}')
    destination = artifacts / ('submission-' + revision[:12])
    if destination.exists():
        raise SystemExit(f'Package already exists: {destination}. Preserving it; choose the existing package.')
    destination.mkdir()
    shutil.copy2(video, destination / video.name)
    shutil.copy2(artifacts / 'stars-privacy-demo.json', destination / 'recording.json')
    shutil.copy2(artifacts / 'stars-privacy-qa.json', destination / 'qa.json')
    shutil.copy2(artifacts / 'native-ui-qa.json', destination / 'native-ui-qa.json')
    for name in DOCUMENTS:
        (destination / name).write_bytes(git('show', revision + ':docs/' + name))
    # A feature-only patch, applied on the published offline setup baseline.
    (destination / 'feature.patch').write_bytes(git('diff', '--binary', BASE, revision))
    (destination / 'LICENSE').write_bytes(git('show', revision + ':LICENSE'))
    images = destination / 'screenshots'
    images.mkdir()
    for name in SCREENSHOTS:
        shutil.copy2(artifacts / 'stars-privacy' / name, images / name)
    for name in NATIVE_SCREENSHOT_CAPTIONS:
        shutil.copy2(artifacts / 'native-ui' / name, images / name)
    def cards_for(captions):
        return ''.join(
        f'<figure><a href="screenshots/{html.escape(name)}"><img loading="lazy" src="screenshots/{html.escape(name)}" alt="{html.escape(title)}"></a>'
        f'<figcaption><h2>{html.escape(title)}</h2><p>{html.escape(caption)}</p></figcaption></figure>'
        for name, (title, caption) in captions.items())
    cards = cards_for(NATIVE_SCREENSHOT_CAPTIONS)
    cards += '<header style="grid-column:1/-1"><h1>Supplementary component checks</h1><p>Isolated fixtures for edge cases, Russian labels and theme checks. These are separate from the production navigation above.</p></header>'
    cards += cards_for(SCREENSHOT_CAPTIONS)
    (destination / 'SCREENSHOTS.html').write_text(
        '<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">'
        '<title>Private Star Reactions — screen review</title><style>'
        'body{font:16px/1.5 system-ui,sans-serif;margin:0;padding:32px;background:#eef2f6;color:#152536}'
        'header{max-width:960px;margin:0 auto 28px}h1{font-size:30px;margin:0 0 8px}h2{font-size:18px;margin:0 0 8px}'
        'main{display:grid;grid-template-columns:repeat(auto-fit,minmax(260px,340px));justify-content:center;gap:28px}'
        'figure{margin:0;background:white;border-radius:16px;overflow:hidden;box-shadow:0 4px 20px #15253612}'
        'img{width:100%;height:auto;display:block}figcaption{padding:18px}p{margin:0}small{color:#526274}'
        '</style><header><h1>Private Star Reactions · real Telegram screens</h1><p>Production navigation with brief descriptions. Actual Android emulator screenshots; sample data and local API mocks.</p>'
        f'<small>Source revision: {revision}. Select a screenshot to open its full resolution.</small></header><main>'
        + cards + '</main></html>')
    (destination / 'MESSAGE.txt').write_text(
        'Private Star Reactions — Telegram Android concept\n\n'
        'A private-by-default way to support channels: anonymous reactions, receipts showing only Stars amounts, IDs and dates, '
        'a 20-second Undo, and retroactive hiding of existing leaderboard positions and selected Stars. '
        'Past activity stays hidden after opting out. A native purchase notice explains public visibility. '
        'The Make my Stars private control sits beside transaction history, with Telegram\'s '
        'gold Star and closing-lock animation. Built with native components, with channel controls, '
        'private donor percentiles and history anonymisation.\n\n'
        f'Source: {REPOSITORY}/tree/{revision}\n'
        f'Feature diff: {REPOSITORY}/compare/{BASE}...{revision}\n'
        'Demo: attach stars-privacy-demo.mp4\n\n'
        'The video runs in the offline Android emulator with sample data and no real payments. '
        'Ordinary anonymous paid reactions use the existing API in the live client. '
        'Channel enforcement, live anonymity, history redaction, retroactive leaderboard protection and separate future public '
        'contributions on previously private posts use local API mocks. '
        'STARS_PRIVACY.md describes the required server work. This is a working client prototype, '
        'not a claim of full payment anonymity or secure deletion.\n')
    (destination / 'REVIEW.txt').write_text(
        f'Private Star Reactions\nSource revision: {revision}\n'
        f'Offline setup baseline: {git("rev-parse", BASE).decode().strip()}\n'
        f'Video: {metadata["duration_seconds"]:.1f} seconds, edited actual emulator capture; no audio. '
        'Native motion plays at normal speed. Launch screens and inactive intervals are cut; '
        'captions sit in a separate band above the app. The recording manifest includes the cut list and raw segment hashes.\n\n'
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

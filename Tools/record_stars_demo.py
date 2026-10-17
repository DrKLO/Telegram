#!/usr/bin/env python3
"""Build and record the native offline walkthrough. No account, microphone or real Stars."""
import hashlib
import importlib.machinery
import importlib.util
import json
from pathlib import Path
import re
import shutil
import subprocess
import time
import uuid

ROOT = Path(__file__).resolve().parents[1]
loader = importlib.machinery.SourceFileLoader('features_lab', str(ROOT / 'dev'))
spec = importlib.util.spec_from_loader(loader.name, loader)
lab = importlib.util.module_from_spec(spec)
loader.exec_module(lab)


def main():
    source_commit = lab.run(['git', 'rev-parse', 'HEAD'], capture=True).stdout.strip()
    dirty_at_start = bool(lab.run(['git', 'status', '--porcelain'], capture=True).stdout.strip())
    ffmpeg = shutil.which('ffmpeg')
    if not ffmpeg:
        raise SystemExit('Install FFmpeg first (brew install ffmpeg).')
    serial = lab.device(required=True)
    lab.install(offline=True)
    lab.run([ROOT / 'gradlew', ':TMessagesProj_App:assembleAfatDebugAndroidTest',
             '-PfeaturesLab=true', '-PfeaturesLabOffline=true', '--console=plain'], env=lab.environment())
    lab.adb('install', '-r', ROOT / 'TMessagesProj_App/build/outputs/apk/androidTest/afat/debug/TMessagesProj_App-afat-debug-androidTest.apk', serial=serial)
    out = ROOT / 'artifacts'
    out.mkdir(exist_ok=True)
    token = uuid.uuid4().hex
    remote = '/sdcard/stars-demo-' + token
    base = [str(lab.sdk_path() / 'platform-tools/adb'), '-s', serial]
    lab.adb('shell', 'am', 'force-stop', lab.OFFLINE_PACKAGE, serial=serial)
    with (out / 'stars-privacy-recording.log').open('w') as log:
        # Store only our recorder's PID; never interrupt unrelated recordings.
        command = f'screenrecord --time-limit 150 --bit-rate 4000000 {remote}.mp4 & recorder_pid=$!; echo "$recorder_pid" > {remote}.pid; wait "$recorder_pid"'
        recorder = subprocess.Popen(base + ['shell', command], stdout=log, stderr=subprocess.STDOUT)
        try:
            time.sleep(1)
            result = subprocess.run(base + ['shell', 'am', 'instrument', '-w', '-r', '-e', 'class',
                'org.telegram.features.StarsPrivacyTest#demoWalkthrough',
                lab.OFFLINE_PACKAGE + '.test/androidx.test.runner.AndroidJUnitRunner'],
                capture_output=True, text=True, timeout=145)
            (out / 'stars-privacy-demo-test.txt').write_text(result.stdout + result.stderr)
        finally:
            pid = lab.adb('shell', 'cat', remote + '.pid', serial=serial, capture=True, check=False).stdout.strip()
            if pid.isdigit():
                lab.adb('shell', 'kill', '-2', pid, serial=serial, check=False)
            try:
                recorder.wait(timeout=20)
            except subprocess.TimeoutExpired:
                recorder.terminate()
                recorder.wait(timeout=5)
    if result.returncode or not re.search(r'OK \(\d+ tests?\)', result.stdout):
        raise SystemExit('Walkthrough failed; inspect artifacts/stars-privacy-demo-test.txt. Previous video preserved.')
    raw = out / 'stars-privacy-demo-raw.mp4'
    lab.adb('pull', remote + '.mp4', raw, serial=serial)
    lab.adb('shell', 'rm', remote + '.mp4', remote + '.pid', serial=serial)
    video = out / 'stars-privacy-demo.mp4'
    temporary = out / 'stars-privacy-demo-next.mp4'
    # Keep the genuine timing, including both 20-second waits. No motion is fabricated.
    subprocess.run([ffmpeg, '-y', '-hide_banner', '-loglevel', 'error', '-i', str(raw),
                    '-vf', 'fps=30', '-c:v', 'libx264', '-preset', 'fast', '-crf', '22',
                    '-pix_fmt', 'yuv420p', '-an', '-movflags', '+faststart', str(temporary)], check=True)
    subprocess.run([ffmpeg, '-v', 'error', '-i', str(temporary), '-f', 'null', '-'], check=True)
    temporary.replace(video)
    # Standalone FFmpeg distributions do not always bundle ffprobe.
    probe = subprocess.run([ffmpeg, '-hide_banner', '-i', str(video)], capture_output=True, text=True)
    duration = re.search(r'Duration: (\d+):(\d+):(\d+\.\d+)', probe.stderr)
    if not duration:
        raise SystemExit('Could not read the exported video duration.')
    hours, minutes, seconds = map(float, duration.groups())
    metadata = {
        'source_commit': source_commit,
        'working_tree_dirty': dirty_at_start or bool(lab.run(['git', 'status', '--porcelain'], capture=True).stdout.strip())
            or source_commit != lab.run(['git', 'rev-parse', 'HEAD'], capture=True).stdout.strip(),
        'video_sha256': hashlib.sha256(video.read_bytes()).hexdigest(),
        'duration_seconds': hours * 3600 + minutes * 60 + seconds,
        'android_version': lab.adb('shell', 'getprop', 'ro.build.version.release', serial=serial, capture=True).stdout.strip(),
        'abi': lab.phone_abi(serial),
        'offline_package': lab.OFFLINE_PACKAGE,
        'scenario': 'StarsPrivacyTest#demoWalkthrough',
        'scenario_passed': True,
    }
    (out / 'stars-privacy-demo.json').write_text(json.dumps(metadata, indent=2) + '\n')
    print(f'Walkthrough: {video}\nDuration: {metadata["duration_seconds"]:.1f}s. Review the video before sharing.')


if __name__ == '__main__':
    main()

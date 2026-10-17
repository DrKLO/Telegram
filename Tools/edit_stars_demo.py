#!/usr/bin/env python3
"""Cut genuine UI captures at device-time markers and add a separate caption band."""
import hashlib
import re
import subprocess


CUTS = [
    ('public_history', 'Your Stars history, today', 'Illustrative transactions · No real payments'),
    ('public_receipt', 'Names, avatars and post links', 'A sample receipt links back to its channel and post'),
    ('privacy_enable', 'Make my Stars private', 'Channel identities disappear. Amounts stay visible.'),
    ('private_receipt', 'Only the essentials remain', 'Stars amount · Transaction ID · Date and time'),
    ('privacy_off', 'Past activity stays private', 'Turning privacy off affects new reactions only'),
    ('purchase_warning', 'A reminder before buying', 'Enable the same privacy setting from this notice'),
    ('anonymise_history', 'Anonymise earlier history', 'An irreversible local action in this prototype'),
    ('channel_privacy', 'Anonymous Stars for the channel', 'A native admin switch beside paid reactions'),
    ('slider_percentile', 'More support. A smaller Top %.', 'Projected rank follows the slider · Only you see it'),
    ('undo', '20 seconds to change your mind', 'The remaining wait is omitted from this edit'),
    ('live_privacy', 'The same privacy for live Stars', 'Telegram’s native lock, slider and Star animations'),
    ('live_percentile', 'Private support, without a named ranking', 'Channel enforcement and percentile data are API mocks'),
    ('dark_history', 'At home in Telegram', 'Native light and dark themes · Offline prototype'),
]


def parse_marks(output):
    """Instrumentation status 2 carries paired device-elapsed-time boundaries."""
    pending, complete, block = {}, {}, {}
    for line in output.splitlines():
        if line.startswith('INSTRUMENTATION_STATUS: '):
            key, _, value = line.removeprefix('INSTRUMENTATION_STATUS: ').partition('=')
            block[key] = value
        elif line.startswith('INSTRUMENTATION_STATUS_CODE:'):
            if 'demo_clip' in block:
                name = block['demo_clip']
                timestamp = int(block['demo_time_ms']) / 1000
                if block['demo_edge'] == 'start':
                    if pending or name in complete:
                        raise ValueError('Duplicate or overlapping demo clip: ' + name)
                    pending[name] = timestamp
                elif block['demo_edge'] == 'end':
                    if name not in pending or timestamp <= pending[name]:
                        raise ValueError('Invalid demo clip end: ' + name)
                    complete[name] = (pending.pop(name), timestamp)
                else:
                    raise ValueError('Unknown demo marker edge')
            block = {}
    if pending:
        raise ValueError('Unfinished demo clips: ' + ', '.join(pending))
    return complete


def plan_cuts(recordings):
    available = {}
    for recording in recordings:
        for name, (start, end) in recording['marks'].items():
            if name in available:
                raise ValueError('Duplicate captured clip: ' + name)
            relative_start = start - recording['start_seconds']
            relative_end = end - recording['start_seconds']
            if relative_start < 0 or relative_end > recording['duration_seconds'] + .5:
                raise ValueError('Marker falls outside its recording: ' + name)
            available[name] = {
                'source': recording['file'], 'source_sha256': recording['sha256'],
                # A small handle keeps gesture beginnings and native transitions intact.
                'start_seconds': max(0, relative_start - .25),
                'end_seconds': min(recording['duration_seconds'], relative_end + .15),
            }
    plan = []
    for name, title, caption in CUTS:
        if name not in available:
            raise ValueError('Required demo clip was not recorded: ' + name)
        plan.append({'id': name, 'title': title, 'caption': caption, **available[name]})
    duration = sum(clip['end_seconds'] - clip['start_seconds'] for clip in plan)
    if not 30 <= duration <= 120:
        raise ValueError(f'Review the edit pacing: {duration:.1f} seconds is outside 30–120 seconds')
    return plan


def duration(ffmpeg, video):
    probe = subprocess.run([ffmpeg, '-hide_banner', '-i', str(video)], capture_output=True, text=True)
    match = re.search(r'Duration: (\d+):(\d+):(\d+\.\d+)', probe.stderr)
    if not match:
        raise ValueError('Cannot read video duration: ' + str(video))
    hours, minutes, seconds = map(float, match.groups())
    return hours * 3600 + minutes * 60 + seconds


def ass_time(seconds):
    total = round(seconds * 100)
    return f'{total // 360000}:{total // 6000 % 60:02}:{total // 100 % 60:02}.{total % 100:02}'


def ass_text(text):
    return text.replace('\\', '\\\\').replace('{', '\\{').replace('}', '\\}').replace('\n', '\\N')


def subtitles(title, caption, seconds):
    end = ass_time(seconds)
    return '''[Script Info]
ScriptType: v4.00+
PlayResX: 720
PlayResY: 1568
WrapStyle: 2
[V4+ Styles]
Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
Style: Title,Arial,30,&H00FFFFFF,&H00FFFFFF,&H00000000,&H00000000,-1,0,0,0,100,100,0,0,1,0,0,8,16,16,22,1
Style: Caption,Arial,21,&H00D5DFE9,&H00D5DFE9,&H00000000,&H00000000,0,0,0,0,100,100,0,0,1,0,0,8,16,16,70,1
[Events]
Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
''' + f'Dialogue: 0,0:00:00.00,{end},Title,,0,0,0,,{{\\fad(120,100)}}{ass_text(title)}\n' \
        + f'Dialogue: 0,0:00:00.00,{end},Caption,,0,0,0,,{{\\fad(120,100)}}{ass_text(caption)}\n'


def render(ffmpeg, out, recordings):
    plan = plan_cuts(recordings)
    clips = []
    for index, clip in enumerate(plan):
        length = clip['end_seconds'] - clip['start_seconds']
        captions = out / f'demo-caption-{index}.ass'
        captions.write_text(subtitles(clip['title'], clip['caption'], length))
        target = out / f'demo-cut-{index}.mp4'
        subprocess.run([ffmpeg, '-y', '-hide_banner', '-loglevel', 'error',
            '-ss', str(clip['start_seconds']), '-i', str(out / clip['source']), '-t', str(length),
            '-vf', f'fps=30,pad=iw:ih+128:0:128:color=0x132333,ass={captions.name}',
            '-c:v', 'libx264', '-preset', 'fast', '-crf', '21', '-pix_fmt', 'yuv420p',
            '-an', str(target)], cwd=out, check=True)
        clips.append(target)
    playlist = out / 'demo-edit-segments.txt'
    playlist.write_text(''.join(f"file '{clip.name}'\n" for clip in clips))
    temporary = out / 'stars-privacy-demo-next.mp4'
    subprocess.run([ffmpeg, '-y', '-hide_banner', '-loglevel', 'error', '-f', 'concat', '-safe', '0',
        '-i', str(playlist), '-c', 'copy', '-movflags', '+faststart', str(temporary)], check=True)
    subprocess.run([ffmpeg, '-v', 'error', '-i', str(temporary), '-f', 'null', '-'], check=True)
    # Publish only a completely rendered, decodable edit. Keep failed exports separate.
    video = out / 'stars-privacy-demo.mp4'
    temporary.replace(video)
    return {
        'video_sha256': hashlib.sha256(video.read_bytes()).hexdigest(),
        'duration_seconds': duration(ffmpeg, video),
        'editing': {'timing': 'normal speed; inactive intervals and launches cut',
                    'caption_band_pixels': 128, 'cuts': plan},
    }

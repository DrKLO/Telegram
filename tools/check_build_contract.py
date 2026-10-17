#!/usr/bin/env python3
"""Check committed configuration boundaries without reading or printing owner secrets."""
from pathlib import Path
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
# This public upstream reproducible-build fixture predates AmeGram. Never replace it
# with a production key. Preserve it unchanged until distribution/signing is redesigned.
LEGACY_KEY = 'TMessagesProj/config/release.keystore'
LEGACY_KEY_BLOB = '4c32fd31eca1db3836f220b60b96c8bd2fcc3dba'


def forbidden(path):
    name = Path(path).name.lower()
    return name in {'google-services.json', 'agconnect-services.json', '.env'} or (
        name.startswith('.env.') and name != '.env.example'
    ) or Path(name).suffix in {'.keystore', '.jks', '.p12', '.pfx', '.key'}


def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT, stderr=subprocess.DEVNULL).decode().strip()


def check():
    tracked = git('ls-files', '-z').split('\0')
    for path in filter(None, tracked):
        if path == LEGACY_KEY:
            if git('hash-object', '--', path) != LEGACY_KEY_BLOB:
                raise ValueError('The upstream fixture key changed; do not commit owner signing material.')
        elif forbidden(path):
            raise ValueError('Forbidden tracked local configuration/signing path: ' + path)
    for path in ['TMessagesProj_App/src/debug/google-services.json',
                 'TMessagesProj_App/src/release/google-services.json',
                 'TMessagesProj_AppHuawei/agconnect-services.json',
                 'signing/owner.jks', 'signing/owner.keystore', '.env.local']:
        if subprocess.run(['git', 'check-ignore', '--quiet', '--no-index', '--', path], cwd=ROOT).returncode:
            raise ValueError('Missing ignore rule for local configuration: ' + path)
    android = '{http://schemas.android.com/apk/res/android}'
    for path in ['auth.xml', 'sync_contacts.xml']:
        element = ET.parse(ROOT / 'TMessagesProj/src/main/res/xml' / path).getroot()
        if element.get(android + 'accountType') != '@string/amegram_account_type':
            raise ValueError('Android account type must use the per-variant AmeGram resource: ' + path)
    build_vars = (ROOT / 'TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java').read_text()
    for field in ['public static int APP_ID = BuildConfig.AMEGRAM_API_ID;',
                  'public static String APP_HASH = BuildConfig.AMEGRAM_API_HASH;']:
        if field not in build_vars:
            raise ValueError('Telegram API values must come from generated build configuration.')
    print('PASS: tracked configuration/signing boundaries, ignore rules and independent Android account resources.')


if __name__ == '__main__':
    try:
        check()
    except (ValueError, OSError, subprocess.CalledProcessError, ET.ParseError) as error:
        print('FAIL:', error, file=sys.stderr)
        sys.exit(1)

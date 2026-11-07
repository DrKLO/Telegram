// VERSION.json (packages/core/scripts/version.ts): every file that repeats one of its numbers
// agrees with it, the platform constants are what build-native-bundles.ts writes from it, and the
// shipped bundles carry its stamp. A bump of one file without the others fails here.

import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { describe, it } from 'node:test';
import { PROFILE_VERSION } from '../../src/shared/profile-tables.ts';
import { assertVersionsAgree, bundleStamp, parseStamp, platformConstants, readVersion, repeatedVersions, root } from '../../scripts/version.ts';

const version = readVersion();

const SHIPPED = {
  ios: 'packages/ios/Sources/WidgetSandbox/WidgetJS',
  android: 'packages/android/widget-sandbox/src/main/assets/WidgetJS',
  desktop: 'packages/desktop/WidgetJS',
} as const;

describe('VERSION.json', () => {
  it('is a semver release and three positive integers', () => {
    assert.match(version.version, /^\d+\.\d+\.\d+/);
    for (const k of ['protocol', 'profile', 'sdk'] as const) assert.ok(Number.isInteger(version[k]) && version[k] >= 1, k);
  });

  it('package.json, packages/core/package.json, the Android library and profile.json repeat it', () => {
    for (const r of repeatedVersions()) assert.equal(r.value, version[r.field], `${r.file}: ${r.field}`);
    assertVersionsAgree(version);
  });

  it('a file left behind by a bump is named', () => {
    assert.throws(() => assertVersionsAgree({ ...version, version: '99.0.0' }), /package\.json has version.*packages\/core\/package\.json.*build\.gradle\.kts/);
    assert.throws(() => assertVersionsAgree({ ...version, profile: version.profile + 1 }), /profile\.json has profile/);
  });

  it('PROFILE_VERSION (profile-tables.ts, the shim and the web host) is its profile', () => {
    assert.equal(PROFILE_VERSION, version.profile);
  });

  it('refuses a malformed file', () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'tw-version-'));
    try {
      const bad = (o: unknown) => { const f = path.join(tmp, 'VERSION.json'); fs.writeFileSync(f, JSON.stringify(o)); return () => readVersion(f); };
      assert.throws(bad({ ...version, version: 'v0.6' }), /not semver/);
      assert.throws(bad({ ...version, protocol: 0 }), /protocol 0 is not a positive integer/);
      assert.throws(bad({ ...version, sdk: '1' }), /sdk "1" is not a positive integer/);
      assert.throws(bad({ ...version, extra: 1 }), /expected exactly/);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });
});

describe('the platform constants', () => {
  for (const [target, c] of Object.entries(platformConstants(version))) {
    it(`${c.path} is what build-native-bundles.ts writes (pnpm build:${target})`, () => {
      assert.equal(fs.readFileSync(path.join(root, c.path), 'utf8'), c.text);
    });
  }
});

describe('the bundle stamp', () => {
  it('round-trips through the parser the glue copies', () => {
    assert.deepEqual(parseStamp(bundleStamp(version) + '\n"use strict";'), version);
    assert.deepEqual(parseStamp('/* widget-sandbox version=1.2.3-rc.1 protocol=7 profile=2 sdk=3 later=x */'), { version: '1.2.3-rc.1', protocol: 7, profile: 2, sdk: 3 });
  });

  it('is invalid without the prefix, a key or an integer', () => {
    for (const line of [
      '"use strict";(()=>{',
      '/* widget-sandbox */',
      '/* widget-sandbox version=0.6.0 protocol=1 profile=1 */',
      '/* widget-sandbox version=0.6.0 protocol=x profile=1 sdk=1 */',
      '/* widget-sandbox version=0.6.0 protocol=-1 profile=1 sdk=1 */',
      '/* widget-sandbox version= protocol=1 profile=1 sdk=1 */',
      '/* widget-sandbox version=0.6.0  protocol=1 profile=1 sdk=1 */',
      '/* widget-sandbox version=0.6.0 protocol=1 profile=1 sdk=1',
      '\n/* widget-sandbox version=0.6.0 protocol=1 profile=1 sdk=1 */',
    ]) assert.equal(parseStamp(line), null, line);
  });

  for (const [target, dir] of Object.entries(SHIPPED)) {
    it(`${target}: every shipped bundle carries VERSION.json's stamp (pnpm build:${target})`, () => {
      for (const name of ['sandbox-bundle.js', 'validator-bundle.js', 'renderer-bundle.js']) {
        const source = fs.readFileSync(path.join(root, dir, name), 'utf8');
        assert.deepEqual(parseStamp(source), version, `${dir}/${name}: ${source.slice(0, 80)}`);
      }
    });
  }
});

import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { describe, it } from 'node:test';
import { fileURLToPath } from 'node:url';

const pkgRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
const root = path.resolve(pkgRoot, '..', '..');

const SHIPPED = {
  ios: 'packages/ios/Sources/WidgetSandbox/WidgetJS',
  android: 'packages/android/widget-sandbox/src/main/assets/WidgetJS',
  desktop: 'packages/desktop/WidgetJS',
} as const;

function files(dir: string, prefix = ''): string[] {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap((e) =>
    e.isDirectory() ? files(path.join(dir, e.name), path.join(prefix, e.name)) : [path.join(prefix, e.name)]).sort();
}

describe('shipped native bundles', () => {
  for (const [target, shipped] of Object.entries(SHIPPED)) {
    it(`${target} ships exactly what a fresh build produces (rebuild with pnpm build:${target})`, () => {
      const out = fs.mkdtempSync(path.join(os.tmpdir(), `tw-bundles-${target}-`));
      try {
        execFileSync(process.execPath, ['--import', 'tsx', path.join(pkgRoot, 'scripts', 'build-native-bundles.ts'), target, '--out', out], { cwd: pkgRoot, stdio: 'pipe' });
        const fresh = files(out);
        const actual = files(path.join(root, shipped));
        assert.deepEqual(actual, fresh, `${shipped}: file list differs from a fresh build`);
        const stale = fresh.filter((f) => !fs.readFileSync(path.join(out, f)).equals(fs.readFileSync(path.join(root, shipped, f))));
        assert.deepEqual(stale, [], `${shipped}: stale against a fresh build`);
      } finally {
        fs.rmSync(out, { recursive: true, force: true });
      }
    });
  }
});

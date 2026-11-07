// packages/quickjs-wasm/dist, the QuickJS the web worker and the Node harness run widget code
// in: what is committed is what dist.sha256 lists, and it was built from the submodule the
// native packages compile. The rebuild itself (emcc) is `packages/quickjs-wasm/build.sh --check`,
// a CI job of its own.

import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { describe, it } from 'node:test';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..', '..', '..');
const wasm = path.join(root, 'packages/quickjs-wasm');

describe('packages/quickjs-wasm/dist', () => {
  it('is exactly what dist.sha256 lists (rebuild with packages/quickjs-wasm/build.sh)', () => {
    const listed = fs.readFileSync(path.join(wasm, 'dist.sha256'), 'utf8').trim().split('\n').map((l) => {
      const m = /^([0-9a-f]{64}) {2}(\S+)$/.exec(l);
      assert.ok(m, `dist.sha256: ${l}`);
      return [m[2], m[1]] as const;
    });
    const files = fs.readdirSync(path.join(wasm, 'dist')).filter((f) => fs.statSync(path.join(wasm, 'dist', f)).isFile()).sort();
    assert.deepEqual(listed.map(([f]) => f), files);
    for (const [f, sum] of listed) {
      assert.equal(crypto.createHash('sha256').update(fs.readFileSync(path.join(wasm, 'dist', f))).digest('hex'), sum, f);
    }
  });

  it('was built from the QuickJS submodule the native packages compile (dist/QUICKJS_VERSION)', (t) => {
    let commit: string;
    try {
      commit = execFileSync('git', ['-C', path.join(root, 'packages/ios/Sources/CQuickJS/quickjs'), 'rev-parse', 'HEAD'], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim();
    } catch {
      t.skip('the submodule is not a git checkout');
      return;
    }
    assert.equal(fs.readFileSync(path.join(wasm, 'dist/QUICKJS_VERSION'), 'utf8').trim(), commit.slice(0, 7));
  });
});

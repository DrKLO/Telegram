// Binary DOM ops: every widget of the corpora renders the same validated ops
// whether the shim sends JSON or binary records (decoded by the harness), and
// interning survives table resets.

import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { describe, it } from 'node:test';
import { fileURLToPath } from 'node:url';
import { runWidget } from '../../scripts/sandbox-node.ts';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../../..');
const files = (dir: string): string[] => fs.readdirSync(dir).flatMap((f) => {
  const p = path.join(dir, f);
  return fs.statSync(p).isDirectory() ? files(p) : f.endsWith('.html') ? [p] : [];
});

describe('binary ops', () => {
  for (const file of files(path.join(root, 'corpus/compat'))) {
    it(`same render ops as JSON: ${path.basename(file)}`, async () => {
      const html = `<script>Date = class extends Date {
        constructor(...args) { super(...(args.length ? args : [1767225600000])); }
        static now() { return 1767225600000; }
      };</script>` + fs.readFileSync(file, 'utf8');
      const det = () => { let t = 0; return { now: () => (t += 4), random: (n: number) => new Array(n).fill(7) }; };
      const json = await runWidget(html, { timerRounds: 3, ...det() });
      const bin = await runWidget(html, { timerRounds: 3, binaryOps: true, ...det() });
      assert.ok(bin.binaryBatches > 0);
      assert.equal(json.binaryBatches, 0);
      assert.deepEqual(bin.renderOps, json.renderOps);
      assert.deepEqual(bin.diags.filter((d) => d.kind !== 'boot'), json.diags.filter((d) => d.kind !== 'boot'));
    });
  }

  it('interning survives 300 flushes (table resets) and odd strings', async () => {
    const widget = `<div id="d"></div><script>
      var d = document.getElementById('d'), n = 0;
      function tick() {
        d.className = 'c' + (n % 7); d.setAttribute('data-x' + (n % 300), 'v\\u0000' + n); d.style.setProperty('--k' + (n % 5), 'x', n % 2 ? 'important' : '');
        d.textContent = 'h\\u00e9 \\ud83d\\ude00 ' + n + (n % 50 === 0 ? '\\ud800' : '');
        var s = document.createElementNS('http://www.w3.org/2000/svg', 'svg'); s.setAttribute('viewBox', '0 0 1 1'); d.appendChild(s); if (n % 3) d.removeChild(s);
        if (++n < 300) setTimeout(tick, 1);
      }
      tick();</script>`;
    const json = await runWidget(widget, { timerRounds: 310 });
    const bin = await runWidget(widget, { timerRounds: 310, binaryOps: true });
    assert.deepEqual(bin.logs.filter((l) => l.level === 'error'), []);
    assert.ok(bin.binaryBatches >= 300, `${bin.binaryBatches} batches, ${bin.timers} timers ${JSON.stringify(bin.diags.slice(0, 3))}`);
    // Deliberate difference: ill-formed UTF-16 leaves the sandbox as U+FFFD on the binary path.
    const wellFormed = (v: any): any => (typeof v === 'string' ? (v as any).toWellFormed() : Array.isArray(v) ? v.map(wellFormed) : v && typeof v === 'object' ? Object.fromEntries(Object.entries(v).map(([k, x]) => [k, wellFormed(x)])) : v);
    assert.deepEqual(bin.renderOps, wellFormed(json.renderOps));
  });
});

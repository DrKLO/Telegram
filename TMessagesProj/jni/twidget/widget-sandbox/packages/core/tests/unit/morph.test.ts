// In-place document updates: the `morph` dispatch patches the live DOM to a
// newer version of the widget's HTML, keeping node identity so CSS transitions
// run, without re-running scripts, and only when the host advertises morphUpdates.

import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { describe, it } from 'node:test';
import { fileURLToPath } from 'node:url';
import { runWidget } from '../../scripts/sandbox-node.ts';

const fixtures = path.join(path.dirname(fileURLToPath(import.meta.url)), '..', 'fixtures', 'morph');
const stage1 = fs.readFileSync(path.join(fixtures, 'preparing-1.html'), 'utf8');
const stage2 = fs.readFileSync(path.join(fixtures, 'preparing-2.html'), 'utf8');

const tracker = `<script>
  window.__runs = (window.__runs || 0) + 1;
  window.__bar = document.getElementById('bar');
  window.__st1 = document.getElementById('st1');
  window.__updates = 0;
  document.addEventListener('widgetupdate', function () { window.__updates++; });
</script>`;

describe('morph', () => {
  it('keeps node identity, updates attributes and text in place and fires widgetupdate without re-running scripts', async () => {
    const res = await runWidget(stage1 + tracker, {
      morphUpdates: true,
      policy: 'strict',
      script: [
        { probe: "document.getElementById('bar').getAttribute('style')" },
        { morph: stage2 + tracker },
      ],
      probes: [
        "window.__bar === document.getElementById('bar')",
        "window.__st1 === document.getElementById('st1')",
        "document.getElementById('bar').getAttribute('style')",
        "document.getElementById('st1').className + '|' + document.getElementById('st2').className",
        'window.__runs + "|" + window.__updates',
        "document.querySelectorAll('#bar').length + '|' + document.querySelectorAll('#stages > *').length",
      ],
    });
    assert.deepEqual(res.morphed, [true]);
    assert.equal(res.probes[0].replace(/\s/g, ''), 'width:30%');
    assert.equal(res.probes[1], 'true');
    assert.equal(res.probes[2], 'true');
    assert.equal(res.probes[3].replace(/\s/g, ''), 'width:50%');
    assert.equal(res.probes[4], 'st done|st now');
    assert.equal(res.probes[5], '1|1');
    assert.equal(res.probes[6].split('|')[0], '1');
    const after = res.renderOps.slice(res.renderOps.length - 200).map((op: any) => op.op);
    assert.ok(!res.diags.some((d) => d.kind.startsWith('validator:') && /reject|invalid/i.test(d.detail)), JSON.stringify(res.diags.filter((d) => d.kind.startsWith('validator:'))));
    assert.ok(after.length > 0);
  });

  it('does nothing unless the host advertises morphUpdates', async () => {
    const res = await runWidget(stage1 + tracker, {
      script: [{ morph: stage2 + tracker }],
      probes: ["document.getElementById('bar').getAttribute('style')"],
    });
    assert.deepEqual(res.morphed, [false]);
    assert.equal(res.probes[0].replace(/\s/g, ''), 'width:30%');
  });
});

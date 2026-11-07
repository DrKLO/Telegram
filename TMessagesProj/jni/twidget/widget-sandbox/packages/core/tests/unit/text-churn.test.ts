import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { textWork, Validator } from '../../src/host/validator.ts';

const BASE = [
  { op: 'el', id: 2, tag: 'html' }, { op: 'ins', parent: 1, id: 2, before: null },
  { op: 'el', id: 3, tag: 'body' }, { op: 'ins', parent: 2, id: 3, before: null },
];

function rig(policy: 'compat' | 'strict' = 'strict') {
  const diags: string[] = [];
  const v = new Validator((kind, detail) => diags.push(`${kind}: ${detail}`));
  v.setPolicy(policy);
  let now = 1000;
  v.setClock(() => now);
  v.processOps(BASE);
  v.markLoaded();
  return { v, diags, advance: (s: number) => { now += s; } };
}

const cjk = (n: number, c = 0x4e2d) => String.fromCharCode(c).repeat(n);

describe('text churn', () => {
  it('counts words, not units', () => {
    assert.equal(textWork('BTC $60000 ▲'), 3);
    assert.equal(textWork('中文字 hello मेरा नाम'), 6);
    assert.equal(textWork('あいうえおかきくけこ。さしす'), 14);
    assert.equal(textWork(''), 0);
  });

  it('rate-limits complex-script changes and refills with time', () => {
    const r = rig();
    assert.equal(r.v.processOps([{ op: 'text', id: 20, data: cjk(3000) }, { op: 'ins', parent: 3, id: 20, before: null }]).length, 2);
    let accepted = 0;
    for (let i = 0; i < 4; i++) accepted += r.v.processOps([{ op: 'data', id: 20, data: cjk(2999) }, { op: 'data', id: 20, data: cjk(3000) }]).length;
    assert.equal(accepted, 1);
    assert.ok(r.diags.some((d) => d.startsWith('text-churn: ')));
    r.advance(6);
    assert.equal(r.v.processOps([{ op: 'data', id: 20, data: cjk(2999) }]).length, 1);
  });

  it('allows ordinary tickers and typewriters', () => {
    const r = rig();
    r.v.processOps([{ op: 'text', id: 20, data: 'x' }, { op: 'ins', parent: 3, id: 20, before: null }]);
    const line = 'मेरा नाम है सैम';
    for (let n = 0; n < 5000; n++) {
      r.advance(0.01);
      r.v.processOps([{ op: 'data', id: 20, data: `BTC $${60000 + n} ▲ ETH ${line.slice(0, n % line.length)}` }]);
    }
    assert.deepEqual(r.diags.filter((d) => d.startsWith('text-churn')), []);
  });

  it('never limits a 400-cell latin ticker updating every frame', () => {
    const r = rig();
    const cells = Array.from({ length: 400 }, (_, i) => 20 + i);
    r.v.processOps(cells.flatMap((id) => [{ op: 'text', id, data: '0.00' }, { op: 'ins', parent: 3, id, before: null }]));
    let accepted = 0;
    for (let frame = 0; frame < 300; frame++) {
      r.advance(1 / 60);
      accepted += r.v.processOps(cells.map((id, i) => ({ op: 'data', id, data: `${(100 + i + frame / 7).toFixed(2)} ${frame % 2 ? '\u25b2' : '\u25bc'} ${(frame % 9) - 4}.0%` }))).length;
    }
    assert.equal(accepted, 400 * 300);
    assert.deepEqual(r.diags.filter((d) => d.startsWith('text-churn')), []);
  });

  it('rejects a fresh complex text node over the churn budget even when the document budget allows it', () => {
    const r = rig();
    r.v.processOps([{ op: 'text', id: 20, data: cjk(4000) }, { op: 'ins', parent: 3, id: 20, before: null }, { op: 'rm', id: 20 }]);
    r.v.processOps([{ op: 'text', id: 21, data: cjk(4000, 0x6587) }, { op: 'ins', parent: 3, id: 21, before: null }, { op: 'rm', id: 21 }]);
    const out = r.v.processOps([{ op: 'text', id: 22, data: cjk(4000, 0x5b57) }, { op: 'ins', parent: 3, id: 22, before: null }]);
    assert.ok(!JSON.stringify(out).includes(cjk(4000, 0x5b57)));
    assert.ok(r.diags.some((d) => d.startsWith('text-churn: ')));
    assert.ok(!r.diags.some((d) => d.startsWith('text-budget')));
  });

  it('leaves complex-script churn alone under the compat policy', () => {
    const r = rig('compat');
    r.v.processOps([{ op: 'text', id: 20, data: cjk(3000) }, { op: 'ins', parent: 3, id: 20, before: null }]);
    let accepted = 0;
    for (let i = 0; i < 4; i++) accepted += r.v.processOps([{ op: 'data', id: 20, data: cjk(2999) }, { op: 'data', id: 20, data: cjk(3000) }]).length;
    assert.equal(accepted, 8);
    assert.deepEqual(r.diags, []);
  });
});

describe('hostile text floods', () => {
  for (const unit of ['a ', 'a-', 'x?', '\u3400', '\uac00', '\u30a2', '\uff21', '\u{20000}']) {
    it(`limits repeated ${JSON.stringify(unit)} text across updates and replacements`, () => {
      const r = rig();
      const data = unit.repeat(3000);
      let accepted = 0;
      for (let i = 0; i < 20; i++) {
        accepted += r.v.processOps([{ op: 'text', id: 20 + i, data }]).length;
        r.v.processOps([{ op: 'rm', id: 20 + i }]);
      }
      assert.equal(accepted, 2);
      r.advance(3);
      assert.equal(r.v.processOps([{ op: 'text', id: 100, data }]).length, 1);
      assert.equal(r.v.processOps([{ op: 'data', id: 100, data }]).length, 0);
    });
  }

  it('caps total text at 48 KB (past it the widget stops), refunds removals, and does not charge rejected duplicate ids', () => {
    const r = rig();
    const data = 'x'.repeat(16384);
    for (let id = 20; id < 23; id++) assert.equal(r.v.processOps([{ op: 'text', id, data }]).length, 1);
    r.v.processOps([{ op: 'rm', id: 20 }]);
    assert.equal(r.v.processOps([{ op: 'text', id: 23, data }]).length, 1);
    assert.equal(r.v.processOps([{ op: 'data', id: 23, data: '' }]).length, 1);
    for (let i = 0; i < 100; i++) r.v.processOps([{ op: 'text', id: 23, data }]);
    assert.equal(r.v.processOps([{ op: 'data', id: 23, data }]).length, 1);
    assert.equal(r.v.stopped, null);
    assert.equal(r.v.processOps([{ op: 'text', id: 30, data: 'a' }]).length, 0);
    assert.equal(r.v.stopped, "Text limit exceeded: the document's text is over 49152 characters");
  });
});

// The strict caps of docs/decisions.md, "Strict limits from the start": past a counted cap the widget stops
// (the validator's `stopped`, the host's `die`), compat keeps the caps hosts had before; the shim refuses at the
// same numbers (canvas, timers, script animations) so the author gets an error in the widget; the main-thread
// window (layout queries + the modelled cost of the ops) defers frames apart from the JS quota.

import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { runWidget } from '../../scripts/sandbox-node.ts';
import { LIMIT_DEFAULTS } from '../../src/shared/protocol.ts';
import { countCssRules, Validator } from '../../src/host/validator.ts';

const SKELETON = [
  { op: 'el', id: 2, tag: 'html' }, { op: 'ins', parent: 1, id: 2, before: null },
  { op: 'el', id: 3, tag: 'body' }, { op: 'ins', parent: 2, id: 3, before: null },
];

function validator(policy: 'strict' | 'compat' = 'strict', diags: string[] = []): Validator {
  const v = new Validator((kind, detail) => diags.push(`${kind}: ${detail}`));
  v.setPolicy(policy);
  v.processOps(SKELETON);
  return v;
}

/** A `<style>` element `id` with `css` as its text (text node `id + 1`). */
const style = (id: number, css: string) => [
  { op: 'el', id, tag: 'style' }, { op: 'ins', parent: 3, id, before: null },
  { op: 'text', id: id + 1, data: css }, { op: 'ins', parent: id, id: id + 1, before: null },
];
const rules = (n: number, from = 0) => Array.from({ length: n }, (_, i) => `.r${from + i}{color:red}`).join('');
const keyframes = (n: number) => Array.from({ length: n }, (_, i) => `@keyframes k${i}{from{opacity:0}50%{opacity:.5}to{opacity:1}}`).join('');

describe('strict limits: the defaults', () => {
  it('8 MB of QuickJS heap, 64 timers, 100 ms of main-thread work per second', () => {
    assert.equal(LIMIT_DEFAULTS.memoryLimitBytes, 8 * 1024 * 1024);
    assert.equal(LIMIT_DEFAULTS.maxTimers, 64);
    assert.equal(LIMIT_DEFAULTS.mainThreadQuotaMs, 100);
    assert.equal(LIMIT_DEFAULTS.cpuQuotaMs, 300);
  });
});

describe('strict limits: CSS rules and @keyframes', () => {
  it('counts style rules inside conditional groups, @keyframes apart, and ignores braces in strings and comments', () => {
    assert.deepEqual(countCssRules('a{color:red}@media (min-width:1px){b{color:red}c{color:red}}'), { rules: 3, keyframes: 0 });
    assert.deepEqual(countCssRules('@keyframes x{from{opacity:0}to{opacity:1}}@-webkit-keyframes y{0%{top:0}}'), { rules: 0, keyframes: 2 });
    assert.deepEqual(countCssRules('a::before{content:"}{"}/* x{y} */b{color:red}'), { rules: 2, keyframes: 0 });
    assert.deepEqual(countCssRules('@supports (display:grid){@container (min-width:1px){a{color:red}}}'), { rules: 1, keyframes: 0 });
  });

  it('an escaped brace in a selector or a @keyframes name is no brace (the tweb audit of v0.4.0)', () => {
    assert.deepEqual(countCssRules('.a\\{x0{color:red}.b\\}{color:red}'), { rules: 2, keyframes: 0 });
    assert.deepEqual(countCssRules('@keyframes k\\}0{to{opacity:0}}@keyframes k\\{1{to{opacity:0}}'), { rules: 0, keyframes: 2 });
    assert.deepEqual(countCssRules('a{content:"\\""}b{color:red}'), { rules: 2, keyframes: 0 }, 'an escape inside a string still ends with it');
  });

  it('stylesheets rewritten in one batch are compared with the cap together, in either order', () => {
    for (const order of [[11, 21], [21, 11]]) {
      const v = validator();
      v.processOps([...style(10, rules(200)), ...style(20, rules(200, 200))]);
      const data: Record<number, string> = { 11: rules(300), 21: rules(100, 300) };
      assert.equal(v.processOps(order.map((id) => ({ op: 'data', id, data: data[id] }))).length, 2, String(order));
      assert.equal(v.stopped, null, `400 rules after the batch (${order})`);
      assert.deepEqual(v.processOps([{ op: 'data', id: 21, data: rules(101, 300) }]), []);
      assert.equal(v.stopped, "CSS limit exceeded: 401 rules in the document's stylesheets (max 400)");
    }
  });

  it('400 rules over two stylesheets draw; one more stops the widget, and removing a sheet refunds its rules', () => {
    const v = validator();
    assert.equal(v.processOps([...style(10, rules(250)), ...style(20, rules(150, 250))]).filter((o) => o.op === 'css').length, 2);
    assert.equal(v.stopped, null);
    v.processOps([{ op: 'rm', id: 20 }]);
    v.processOps(style(30, rules(150, 400)));
    assert.equal(v.stopped, null, 'the removed sheet was refunded');
    assert.deepEqual(v.processOps(style(40, rules(1, 900))), []);
    assert.equal(v.stopped, "CSS limit exceeded: 401 rules in the document's stylesheets (max 400)");
  });

  it('32 @keyframes draw; the 33rd stops the widget', () => {
    const v = validator();
    v.processOps(style(10, keyframes(32)));
    assert.equal(v.stopped, null);
    assert.deepEqual(v.processOps([{ op: 'data', id: 11, data: keyframes(33) }]), []);
    assert.equal(v.stopped, "CSS limit exceeded: 33 @keyframes in the document's stylesheets (max 32)");
  });

  it('compat has no rule cap', () => {
    const v = validator('compat');
    assert.equal(v.processOps(style(10, rules(1000))).filter((o) => o.op === 'css').length, 1);
    assert.equal(v.stopped, null);
  });
});

describe("strict limits: the document's text after the whole batch", () => {
  /** A `<p>` `id` with `n` × `ch` as its text (text node `id + 1`), inserted into the body. */
  const p = (id: number, ch: string, n = 26_000) => [
    { op: 'el', id, tag: 'p' }, { op: 'text', id: id + 1, data: ch.repeat(n) },
    { op: 'ins', parent: id, id: id + 1, before: null }, { op: 'ins', parent: 3, id, before: null },
  ];
  it('a subtree replaced in one batch counts the document as it ends up, not the old and the new together', () => {
    const v = validator();
    v.processOps(p(10, 'x'));
    assert.equal(v.processOps([...p(20, 'y'), { op: 'rm', id: 10 }]).length, 5);
    assert.equal(v.stopped, null, '26 K before and after; 52 K only in the middle of the batch');
    assert.deepEqual(v.processOps(p(30, 'z')), []);
    assert.equal(v.stopped, "Text limit exceeded: the document's text is over 49152 characters");
  });

  it('innerHTML replaced by the widget: the shim and the validator together (the tweb audit of v0.4.0)', async () => {
    const r = await runWidget(`<div id="a"></div><script>
      var a = document.getElementById('a');
      a.innerHTML = '<p>' + 'x'.repeat(26000) + '</p>';
      setTimeout(function () { a.innerHTML = '<p>' + 'y'.repeat(26000) + '</p>'; }, 10);
    </script>`, { timerRounds: 3 });
    assert.equal(r.stopped, undefined);
    assert.ok(r.renderOps.some((o: any) => o.op === 'text' && o.data.startsWith('yyy')));
  });
});

describe('strict limits: ops per batch and canvas commands per send', () => {
  // One text node and its changes: n ops without passing the node cap.
  const texts = (n: number) => [{ op: 'text', id: 100, data: '' }, ...Array.from({ length: n - 1 }, (_, i) => ({ op: 'data', id: 100, data: String(i % 10) }))];
  it('10 000 ops in a batch pass; 10 001 stop the widget (compat: up to 200 000)', () => {
    const v = validator();
    assert.equal(v.processOps(texts(10_000)).length, 10_000);
    assert.deepEqual(v.processOps(texts(10_001).map((o) => ({ ...o, id: o.id + 20_000 }))), []);
    assert.equal(v.stopped, 'Ops limit exceeded: 10001 operations in one batch (max 10000)');
    const c = validator('compat');
    assert.equal(c.processOps(texts(10_001)).length, 10_001);
    assert.equal(c.stopped, null);
  });

  const canvas = [{ op: 'el', id: 9, tag: 'canvas' }, { op: 'ins', parent: 3, id: 9, before: null }];
  const cmds = (n: number) => Array.from({ length: n }, (_, i) => ['c', 'fillRect', i, 0, 1, 1]);
  it('4000 canvas commands in one send pass; 4001 stop the widget (compat: no stop)', () => {
    const v = validator();
    v.processOps(canvas);
    assert.equal((v.processOps([{ op: 'canvas', id: 9, cmds: cmds(4000) }])[0] as any).cmds.length, 4000);
    assert.deepEqual(v.processOps([{ op: 'canvas', id: 9, cmds: cmds(4001) }]), []);
    assert.equal(v.stopped, 'Canvas limit exceeded: 4001 commands in one send (max 4000)');
    assert.deepEqual(v.canvasCommands(cmds(1)), [], 'the native canvas-words path is refused too once stopped');
    const w = validator();
    assert.deepEqual(w.canvasCommands(cmds(4001)), []);
    assert.match(w.stopped ?? '', /^Canvas limit exceeded/);
    const c = validator('compat');
    c.processOps(canvas);
    assert.equal((c.processOps([{ op: 'canvas', id: 9, cmds: cmds(10_001) }])[0] as any).cmds.length, 10_001);
  });
});

describe('strict limits: canvas and data: image pixels', () => {
  const canvasEl = (id: number, attrs: Record<string, string> = {}) => [{ op: 'el', id, tag: 'canvas', attrs }, { op: 'ins', parent: 3, id, before: null }];
  it("2048² pixels over the widget's canvases draw; one more stops the widget, by attribute or by the shim's r; removing a canvas refunds it", () => {
    const v = validator();
    v.processOps([...canvasEl(10, { width: '2048', height: '1024' }), ...canvasEl(11)]);
    assert.equal(v.stopped, null);
    // 2048 × 1024 + 2048 × 1024 = 2048²: still fits (the second canvas was 300 × 150)
    assert.equal(v.processOps([{ op: 'canvas', id: 11, cmds: [['r', 2048, 1024]] }]).length, 1);
    assert.equal(v.stopped, null);
    v.processOps([{ op: 'rm', id: 11 }]);
    v.processOps(canvasEl(12, { width: '2048', height: '1024' }));
    assert.equal(v.stopped, null, 'the removed canvas was refunded');
    assert.deepEqual(v.processOps(canvasEl(13)), []);
    assert.equal(v.stopped, 'Canvas limit exceeded: canvases of 4239304 pixels together (max 4194304)');
    const w = validator();
    w.processOps(canvasEl(10));
    assert.deepEqual(w.processOps([{ op: 'attr', id: 10, name: 'width', value: '30000' }]), []);
    assert.match(w.stopped ?? '', /^Canvas limit exceeded: canvases of 4500000 pixels/);
    const c = validator('compat');
    c.processOps(canvasEl(10, { width: '8192', height: '8192' }));
    assert.equal(c.stopped, null, 'compat leaves canvas sizes to the renderer');
    // a size past 2147483647 is the default, so 0 × 1e310 cannot make the sum NaN and switch the cap off
    const n = validator();
    n.processOps(canvasEl(10, { width: '0', height: '1' + '0'.repeat(310) }));
    assert.equal(n.stopped, null);
    n.processOps(canvasEl(11, { width: '4096', height: '2048' }));
    assert.match(n.stopped ?? '', /^Canvas limit exceeded: canvases of 8388608 pixels together/);
  });

  it('a data: image past 1024² pixels becomes the placeholder, and the widget goes on', () => {
    const chunk = (type: string, data: Buffer) => Buffer.concat([Buffer.alloc(4), Buffer.from(type, 'latin1'), data, Buffer.alloc(4)]);
    const png = (w: number, h: number) => {
      const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6;
      return 'data:image/png;base64,' + Buffer.concat([Buffer.from('89504e470d0a1a0a', 'hex'), chunk('IHDR', ihdr)]).toString('base64');
    };
    const v = validator();
    const src = (w: number, h: number) => ((v.processOps([{ op: 'el', id: 20 + w, tag: 'img', attrs: { src: png(w, h) } }])[0] as any).attrs as [string, string][]).find(([n]) => n === 'src')![1];
    assert.equal(src(1024, 1024), png(1024, 1024));
    assert.notEqual(src(1025, 1024), png(1025, 1024));
    assert.equal(v.stopped, null);
  });
});

describe('strict limits: script animations', () => {
  const record = (aid: number) => ({
    aid, target: 7, keyframes: [{ offset: 0, easing: 'linear', composite: null, values: { opacity: '0' } }, { offset: 1, easing: 'linear', composite: null, values: { opacity: '1' } }],
    timing: { duration: 1000, delay: 0, endDelay: 0, iterations: null, iterationStart: 0, direction: 'normal', fill: 'none', easing: 'linear' },
    composite: 'replace', iterationComposite: 'replace', startTime: 1000, holdTime: null, playbackRate: 1,
  });
  it('64 live script animations under strict (dropped past it, as before), 1024 under compat', () => {
    for (const [policy, cap] of [['strict', 64], ['compat', 1024]] as const) {
      const diags: string[] = [];
      const v = validator(policy, diags);
      v.processOps([{ op: 'el', id: 7, tag: 'div' }, { op: 'ins', parent: 3, id: 7, before: null }]);
      assert.equal(v.validateAnim(Array.from({ length: cap + 10 }, (_, i) => record(i + 1))).length, cap, policy);
      assert.ok(diags.some((d) => d.startsWith('animation-limit')), policy);
      assert.equal(v.stopped, null);
    }
  });
});

describe('strict limits: CSS animations the script controls are not script animations', () => {
  it('80 CSS animations paused by the script all reach the renderer, and no animation-limit (the tweb audit of v0.4.0)', async () => {
    const html = `<style>@keyframes spin{to{opacity:.2}}.s{animation:spin 1s infinite}</style>${'<div class="s"></div>'.repeat(80)}
      <script>setTimeout(function () { document.getAnimations().forEach(function (a) { a.pause(); }); }, 10);</script>`;
    const first = await runWidget(html, { webAnimations: true });
    const ids = (first.renderOps as any[]).filter((o) => o.op === 'el' && o.tag === 'div').map((o) => o.id);
    assert.equal(ids.length, 80);
    const list = ids.map((id, index) => ({ kind: 'animation', id, name: 'spin', index: 0, origin: index, currentTime: 250, playbackRate: 1, paused: false, duration: 1000, delay: 0, iterations: null, direction: 'normal', fill: 'none', easing: 'linear' }));
    const r = await runWidget(html, { webAnimations: true, timerRounds: 3, answer: (q: any) => (q.q === 'anims' ? { list } : null) });
    const paused = (r.animOps as any[][]).flat().filter((a) => a.css && a.startTime === null && a.holdTime !== null);
    assert.equal(new Set(paused.map((a) => a.target)).size, 80, JSON.stringify(r.diags.slice(-3)));
    assert.ok(!r.diags.some((d) => d.kind === 'animation-limit'));
  });
});

describe('strict limits: the shim refuses at the same numbers', () => {
  it('a canvas takes 4000 commands in one task; the next throws RangeError in the widget (compat: no throw)', async () => {
    const html = `<canvas id=c></canvas><script>
      const x = document.getElementById('c').getContext('2d');
      try { for (let i = 0; i < 4001; i++) x.fillRect(i, 0, 1, 1); console.log('drew all'); }
      catch (e) { console.log(e.name + ': ' + e.message); }
    </script>`;
    const strict = await runWidget(html);
    assert.ok(strict.logs.some((l) => l.text === 'RangeError: Canvas limit exceeded: more than 4000 commands for one canvas in one task (max 4000)'), JSON.stringify(strict.logs));
    assert.equal(strict.stopped, undefined, 'the shim stopped short of the cap, so the host did not stop the widget');
    const compat = await runWidget(html, { policy: 'compat' });
    assert.ok(compat.logs.some((l) => l.text === 'drew all'));
  });

  it('the 65th live timer throws RangeError by default', async () => {
    const r = await runWidget(`<script>
      let n = 0;
      try { for (; n < 100; n++) setTimeout(() => {}, 60000); } catch (e) { console.log(e.name + ' at ' + n); }
    </script>`);
    assert.ok(r.logs.some((l) => l.text === 'RangeError at 64'), JSON.stringify(r.logs));
  });

  it('a text node over 32 KB made by the widget stops it: the harness reports it and runs nothing after', async () => {
    const r = await runWidget(`<p id=p></p><script>
      setTimeout(() => { document.getElementById('p').textContent = 'x'.repeat(40000); }, 10);
      setTimeout(() => { document.getElementById('p').textContent = 'never'; }, 20);
    </script>`, { timerRounds: 3 });
    assert.equal(r.stopped, 'Text limit exceeded: a text value of 40000 characters (max 32768)');
    assert.ok(!JSON.stringify(r.renderOps).includes('never'));
  });
});

describe('strict limits: the main-thread window', () => {
  it('charges 25 µs per op and 2 µs per canvas command, and defers frames once the window is spent', async () => {
    const html = `<div id=list></div><script>
      const list = document.getElementById('list');
      for (let i = 0; i < 200; i++) list.appendChild(document.createElement('span'));
      function frame() { for (const s of list.children) s.textContent = String(Math.random()); requestAnimationFrame(frame); }
      requestAnimationFrame(frame);
    </script>`;
    const open = await runWidget(html, { frames: 20 });
    assert.equal(open.throttled, 0);
    assert.ok(open.mainThreadMs > 20 * 200 * 0.025, `${open.mainThreadMs} ms`);
    const tight = await runWidget(html, { frames: 20, mainThreadQuotaMs: 20 });
    assert.ok(tight.throttled > 0, 'frames were deferred');
    assert.ok(tight.diags.some((d) => d.kind === 'cpu-throttled' && /main-thread window/.test(d.detail)), JSON.stringify(tight.diags.slice(-3)));
  });
});

describe('strict limits: nodes in the document', () => {
  const els = (n: number, from: number) => Array.from({ length: n }, (_, i) => [{ op: 'el', id: from + i, tag: 'div' }, { op: 'ins', parent: 3, id: from + i, before: null }]).flat();
  it('2000 nodes pass; one more stops the widget once the batch is applied (compat: no cap)', () => {
    const v = validator();
    // html and body are two of them.
    assert.equal(v.processOps(els(1998, 100)).length, 3996);
    assert.equal(v.stopped, null);
    assert.deepEqual(v.processOps(els(1, 5000)), []);
    assert.equal(v.stopped, 'Node limit exceeded: 2001 nodes in the document (max 2000)');
    const c = validator('compat');
    c.processOps(els(3000, 100));
    assert.equal(c.stopped, null);
  });

  it('a document rebuilt in one batch (old nodes removed after the new ones are made) is compared once it is applied', () => {
    const v = validator();
    v.processOps(els(1500, 100));
    const rebuilt = [...els(1500, 10_000), ...Array.from({ length: 1500 }, (_, i) => ({ op: 'rm', id: 100 + i }))];
    assert.equal(v.processOps(rebuilt).length, 4500);
    assert.equal(v.stopped, null);
  });
});

describe('strict limits: text inserted while the widget loads', () => {
  const para = (i: number) => `Paragraph ${i}: ` + 'lorem ipsum dolor sit amet '.repeat(17);
  const html = (n: number, later = '') => `<div id="t"></div><script>
    const t = document.getElementById('t');
    for (let i = 0; i < ${n}; i++) { const p = document.createElement('p'); p.textContent = ${JSON.stringify(para(0))}.replace('0', i); t.appendChild(p); }
    ${later}</script>`;

  it('a document of ~45 K characters of paragraphs is not charged to the churn bucket at load: no paragraph is dropped', async () => {
    const r = await runWidget(html(95));
    assert.equal(r.stopped, undefined);
    assert.ok(!r.diags.some((d) => d.kind === 'validator:text-churn'), JSON.stringify(r.diags.filter((d) => /churn/.test(d.kind))));
    assert.equal(r.renderOps.filter((o: any) => o.op === 'text' && o.data.startsWith('Paragraph')).length, 95);
  });

  it('after the load the same rewrite is rate-limited again', async () => {
    const r = await runWidget(html(95, `setTimeout(() => { for (const p of t.children) p.textContent = p.textContent + ' again'; }, 1);`), { timerRounds: 3 });
    assert.ok(r.diags.some((d) => d.kind === 'validator:text-churn'));
  });
});

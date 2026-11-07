// Reconciliation fast paths: a replaced sole text child keeps its id, the
// attribute removal scan, and the arity-specialized dirty-marking wrappers.

import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { runWidget } from '../../scripts/sandbox-node.ts';

const opsAfterBoot = async (html: string) => {
  const r = await runWidget(html, { timerRounds: 3, probes: ['window.__probe || ""'] });
  const boot = r.ops[0]?.length ?? 0;
  return { r, later: r.ops.slice(1).flat() as any[], boot };
};

describe('capture: sole text child', () => {
  it('textContent on an element with one text child sends one data op and keeps the node id', async () => {
    const { r, later } = await opsAfterBoot(`<span id="t">0</span><script>
      var t = document.getElementById('t'), n = 0;
      function tick() { t.textContent = String(++n); if (n < 3) setTimeout(tick, 1); }
      setTimeout(tick, 1);</script>`);
    assert.deepEqual(later.map((o) => `${o.op}:${o.data ?? ''}`), ['data:1', 'data:2', 'data:3']);
    const textId = (r.ops[0] as any[]).find((o: any) => o.op === 'text' && o.data === '0')?.id;
    assert.ok(later.every((o) => o.id === textId));
  });

  it('a MutationObserver still sees the replacement as a childList record', async () => {
    const r = await runWidget(`<span id="t">a</span><script>
      var t = document.getElementById('t'), seen = [];
      new MutationObserver(function (rs) { rs.forEach(function (x) { seen.push(x.type + ':' + x.addedNodes.length + ':' + x.removedNodes.length); }); }).observe(t, { childList: true });
      setTimeout(function () { t.textContent = 'b'; }, 1);</script>`, { timerRounds: 3, probes: ['seen.join()', 't.firstChild.data'] });
    assert.equal(r.probes[0], 'childList:1:1');
    assert.equal(r.probes[1], 'b');
  });

  it('falls back to rm and ins when the old text node was moved elsewhere', async () => {
    const { later } = await opsAfterBoot(`<span id="t">a</span><b id="o"></b><script>
      var t = document.getElementById('t'), o = document.getElementById('o');
      setTimeout(function () { var old = t.firstChild; o.appendChild(old); t.appendChild(document.createTextNode('b')); }, 1);</script>`);
    const kinds = later.map((o) => o.op).sort();
    assert.deepEqual(kinds, ['ins', 'ins', 'text']);
  });
});

describe('capture: attributes and wrappers', () => {
  it('removed attributes are still reported after the counting scan', async () => {
    const { later } = await opsAfterBoot(`<div id="d" a="1" b="2" c="3"></div><script>
      var d = document.getElementById('d');
      setTimeout(function () { d.removeAttribute('b'); d.setAttribute('a', '9'); }, 1);
      setTimeout(function () { d.setAttribute('e', '5'); d.removeAttribute('c'); }, 2);</script>`);
    assert.deepEqual(later.map((o) => `${o.op}:${o.name}${o.value !== undefined ? '=' + o.value : ''}`).sort(), ['attr:a=9', 'attr:e=5', 'rattr:b', 'rattr:c']);
  });

  it('wrapped DOM methods keep their results and argument counts', async () => {
    const r = await runWidget(`<ul id="l"></ul><script>
      var l = document.getElementById('l'), a = document.createElement('li'), b = document.createElement('li'), c = document.createElement('li');
      l.append(a, b, c); l.insertBefore(c, a); var removed = l.removeChild(b);
      l.prepend(); l.append('x', 'y', 'z');
      var out = [l.children.length, removed === b, l.firstChild === c, l.textContent].join();</script>`, { timerRounds: 0, probes: ['out'] });
    assert.equal(r.probes[0], '2,true,true,xyz');
  });

  it('a sloppy-mode caller of a wrapped method does not leak a mapped arguments object', async () => {
    const r = await runWidget(`<div id="d"></div><script>
      var d = document.getElementById('d'), k = 0;
      function add() { return d.appendChild.apply(d, arguments); }
      for (var i = 0; i < 2000; i++) { add(document.createElement('i')); if (d.childNodes.length > 50) d.textContent = ''; k++; }</script>`, { timerRounds: 0, probes: ['k'] });
    assert.equal(r.probes[0], '2000');
  });
});

describe('dom: className and textContent setters', () => {
  it('className keeps classList, the attribute and the host in step on every path', async () => {
    const r = await runWidget(`<p id="p" class="x y"></p><p id="q"></p><script>
      var p = document.getElementById('p'), q = document.getElementById('q'), out = [];
      var cl = p.classList; cl.contains('x');
      p.className = 'solo'; out.push(p.getAttribute('class'), cl.contains('solo'), cl.contains('x'), cl.length);
      p.className = 'a b'; out.push(p.getAttribute('class'), cl.contains('b'), cl.length);
      p.className = 'c'; out.push(p.className, p.classList.value);
      q.className = 'first'; out.push(q.getAttribute('class'));
      p.className = ''; out.push(JSON.stringify(p.getAttribute('class')));
      var res = out.join('|');</script>`, { timerRounds: 0, probes: ['res'] });
    assert.equal(r.probes[0], 'solo|true|false|1|a b|true|2|c|c|first|""');
    const ops = r.ops.flat() as any[];
    const pid = ops.find((o) => o.op === 'el' && o.attrs?.id === 'p').id;
    const told = ops.filter((o) => o.id === pid && (o.op === 'attr' || o.op === 'el')).map((o) => (o.op === 'el' ? o.attrs.class : o.value));
    assert.equal(told[told.length - 1], '');
  });

  it('textContent replaces a sole text child and still handles every other shape', async () => {
    const r = await runWidget(`<p id="a">old</p><p id="b"><i>x</i>y</p><p id="c">z</p><p id="d"></p><script>
      var g = function (id) { return document.getElementById(id); }, out = [];
      var old = g('a').firstChild; g('a').textContent = 'new'; out.push(g('a').innerHTML, old.parentNode === null, g('a').childNodes.length);
      g('b').textContent = 'flat'; out.push(g('b').innerHTML);
      g('c').textContent = ''; out.push(g('c').childNodes.length);
      g('d').textContent = 5; out.push(g('d').textContent);
      g('a').textContent = null; out.push(g('a').childNodes.length);
      var res = out.join('|');</script>`, { timerRounds: 0, probes: ['res'] });
    assert.equal(r.probes[0], 'new|true|1|flat|0|5|0');
  });
});

describe('capture: script contents', () => {
  it('script text is never mirrored to the host, at boot or after a change', async () => {
    const big = 'x'.repeat(70 * 1024);
    const r = await runWidget(`<p id="p">hi</p><script id="s">/*${big}*/ var s = document.getElementById('s');
      setTimeout(function () { s.textContent = 'var changed = 1;'; s.appendChild(document.createTextNode('//more')); }, 1);</script>`,
      { timerRounds: 3, probes: ['s.textContent.length', 'document.getElementById("p").textContent'] });
    const all = r.ops.flat() as any[];
    const script = all.find((o) => o.op === 'el' && o.tag === 'script');
    assert.ok(script);
    assert.ok(!all.some((o) => o.op === 'ins' && o.parent === script.id));
    assert.ok(!all.some((o) => (o.op === 'text' || o.op === 'data') && String(o.data).length > 1000));
    assert.ok(!all.some((o) => (o.op === 'text' || o.op === 'data') && String(o.data).includes('changed')));
    assert.equal(r.probes[0], String('var changed = 1;//more'.length));
    assert.equal(r.probes[1], 'hi');
  });
});

describe('capture: moving known nodes into a new subtree', () => {
  it('an existing element moved into a container created in the same flush is inserted, not created again', async () => {
    const r = await runWidget(`<div id="list"><section id="old"><button id="row" class="r"><b>Row</b></button></section></div><script>
      var list = document.getElementById('list'), row = document.getElementById('row');
      setTimeout(function () {
        var fresh = document.createElement('section');
        fresh.appendChild(row);
        list.replaceChildren(fresh);
        row.className = 'r moved';
      }, 1);
      setTimeout(function () {
        var again = document.createElement('section');
        again.appendChild(row);
        list.replaceChildren(again);
      }, 2);</script>`, { timerRounds: 4, policy: 'strict', probes: ['list.innerHTML'] });
    assert.deepEqual(r.diags.filter((d) => d.kind.includes('violation')), []);
    const all = r.ops.flat() as any[];
    const rowCreates = all.filter((o) => o.op === 'el' && o.attrs?.id === 'row');
    assert.equal(rowCreates.length, 1);
    const rowId = rowCreates[0].id;
    const inserts = all.filter((o) => o.op === 'ins' && o.id === rowId).map((o) => o.parent);
    assert.equal(new Set(inserts).size, 3);
    assert.ok(all.some((o) => o.op === 'attr' && o.id === rowId && o.value === 'r moved'));
    assert.match(r.probes[0], /^<section><button [^>]*id="row"[^>]*><b>Row<\/b><\/button><\/section>$/);
  });

  it('the same move after the old container left the document: its parent syncs first, the row is still not created again', async () => {
    const r = await runWidget(`<div id="list"><section id="old"><button id="row"><b>Row</b></button></section></div><script>
      var list = document.getElementById('list'), row = document.getElementById('row');
      setTimeout(function () {
        var fresh = document.createElement('section');
        list.replaceChildren(fresh);
        fresh.appendChild(row);
      }, 1);</script>`, { timerRounds: 3, policy: 'strict', probes: ['list.innerHTML'] });
    assert.deepEqual(r.diags.filter((d) => d.kind.includes('violation')), []);
    const all = r.ops.flat() as any[];
    assert.equal(all.filter((o) => o.op === 'el' && o.attrs?.id === 'row').length, 1);
    assert.match(r.probes[0], /^<section><button id="row"><b>Row<\/b><\/button><\/section>$/);
  });
});

describe('capture: a subtree replaced in one task', () => {
  it('removes the old children before it creates the new ones, so the validator never holds both texts', async () => {
    const r = await runWidget(`<div id="a"><p>old text</p></div><script>
      setTimeout(function () { document.getElementById('a').innerHTML = '<p>new text</p>'; }, 1);</script>`, { timerRounds: 3 });
    const batch = (r.ops as any[][]).find((b) => b.some((o) => o.op === 'text' && o.data === 'new text'))!;
    const rm = batch.findIndex((o) => o.op === 'rm');
    assert.ok(rm >= 0 && rm < batch.findIndex((o) => o.op === 'el'), JSON.stringify(batch));
    assert.deepEqual(r.diags.filter((d) => d.kind.includes('violation')), []);
  });
});

describe('capture: layout query cache', () => {
  for (const binary of [false, true]) it(`offset reads survive transform-only flushes but not layout changes or rect reads${binary ? ' (binary ops)' : ''}`, async () => {
    const r = await runWidget(`<section id="s" style="height:50px"></section><script>
      var s = document.getElementById('s'), h = [];
      for (var i = 0; i < 4; i++) { s.style.transform = 'translateY(' + i + 'px)'; s.style.opacity = String(1 - i / 10); h.push(s.offsetHeight); }
      s.getBoundingClientRect(); s.style.transform = 'none'; s.getBoundingClientRect();
      s.style.height = '60px'; h.push(s.offsetHeight);</script>`, { timerRounds: 0, probes: ['h.length'], binaryOps: binary });
    const kinds = r.queries.map((q: any) => q.q);
    assert.deepEqual(kinds, ['box', 'rect', 'rect', 'box']);
  });
});

describe('capture: a long flush goes out in parts', () => {
  const build = (n: number, extra = '') => `<div id="list"></div><script>
    const list = document.getElementById('list');
    for (let i = 0; i < ${n}; i++) { const d = document.createElement('div'); d.className = 'row'; d.textContent = 'row ' + i; list.appendChild(d); }
    ${extra}</script>`;

  it('hands the glue at most ~1000 ops at a time between nodes, and the renderer gets the whole document', async () => {
    const r = await runWidget(build(900), { policy: 'compat' });
    const sizes = r.ops.map((b) => b.length);
    assert.ok(sizes.length >= 3, `batches ${sizes.join(',')}`);
    assert.ok(sizes.every((n) => n < 1100), `batches ${sizes.join(',')}`);
    assert.equal(r.renderOps.filter((o: any) => o.op === 'el' && o.tag === 'div').length, 901);
    assert.equal(r.renderOps.filter((o: any) => o.op === 'text' && /^row \d+$/.test(o.data)).length, 900);
  });

  it('a subtree built in one createNode (innerHTML of a detached root) is split too', async () => {
    const r = await runWidget(`<div id="list"></div><script>
      const root = document.createElement('section');
      root.innerHTML = Array.from({ length: 900 }, (_, i) => '<p>p' + i + '</p>').join('');
      document.getElementById('list').appendChild(root);</script>`, { policy: 'compat' });
    assert.ok(r.ops.length >= 2, `batches ${r.ops.map((b) => b.length).join(',')}`);
    assert.equal(r.renderOps.filter((o: any) => o.op === 'el' && o.tag === 'p').length, 900);
  });

  it('canvas commands staged in the flush stay with its last batch (canvas words)', async () => {
    const r = await runWidget(build(900, `const c = document.createElement('canvas'); document.body.appendChild(c);
      const x = c.getContext('2d'); for (let i = 0; i < 50; i++) x.fillRect(i, 0, 1, 1);`), { policy: 'compat', canvasWords: true });
    const cmds = r.renderOps.filter((o: any) => o.op === 'canvas').flatMap((o: any) => o.cmds);
    assert.equal(cmds.filter((c: any) => c[1] === 'fillRect').length, 50);
  });

  it('a document of 2000 rows stops at the node cap, not at the 8 MB heap', async () => {
    const r = await runWidget(build(1000));
    assert.match(r.stopped ?? '', /^Node limit exceeded: \d+ nodes in the document \(max 2000\)$/);
    assert.ok(!r.logs.some((l) => /out of memory|Memory limit/i.test(l.text)), JSON.stringify(r.logs.slice(0, 3)));
  });
});

// Inline style: the declaration parser's fast path (no quotes or escapes) against
// the character loop it short-cuts, and the style ops the shim emits across
// flushes now that parsed style texts are reused from the previous flush.

import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { parseDeclarations, toKebab, type Decl } from '../../src/sandbox/style.ts';
import { runWidget } from '../../scripts/sandbox-node.ts';

function reference(text: string): Map<string, Decl> {
  const out = new Map<string, Decl>();
  let depth = 0;
  let quote = '';
  let start = 0;
  const push = (end: number) => {
    const decl = text.slice(start, end);
    const colon = decl.indexOf(':');
    if (colon > 0) {
      const name = decl.slice(0, colon).trim();
      let value = decl.slice(colon + 1).trim();
      let important = false;
      const m = /!\s*important\s*$/i.exec(value);
      if (m) {
        important = true;
        value = value.slice(0, m.index).trim();
      }
      if (name && value) {
        const key = name.startsWith('--') ? name : name.toLowerCase();
        out.delete(key);
        out.set(key, { value, important });
      }
    }
    start = end + 1;
  };
  for (let i = 0; i < text.length; i++) {
    const ch = text[i];
    if (ch === '\\') i++;
    else if (quote) {
      if (ch === quote) quote = '';
    } else if (ch === '"' || ch === "'") quote = ch;
    else if (ch === '(') depth++;
    else if (ch === ')') depth = Math.max(0, depth - 1);
    else if (ch === ';' && depth === 0) push(i);
  }
  push(text.length);
  return out;
}

const dump = (m: Map<string, Decl>) => JSON.stringify([...m]);

describe('style: declaration parser', () => {
  it('fast path matches the character loop on random declaration lists', () => {
    const pieces = ['color', 'Width', '--x', ':', ':', ';', ';', ' ', '(', ')', 'red', '1px', 'url(a;b)', '!important', '! IMPORTANT', '!', 'calc(1px + (2px))', '"q;"', "'", '\\;', 'a'];
    let seed = 1;
    const next = () => ((seed = (Math.imul(seed, 1103515245) + 12345) & 0x7fffffff) >>> 8);
    for (let n = 0; n < 20000; n++) {
      let text = '';
      const len = next() % 14;
      for (let i = 0; i < len; i++) text += pieces[next() % pieces.length];
      assert.equal(dump(parseDeclarations(text)), dump(reference(text)), JSON.stringify(text));
    }
  });

  it('keeps the CSSOM-facing results of real style texts', () => {
    const text = 'color: red; background: url(data:image/png;base64,AAAA) no-repeat; --gap: 4px; width: calc(100% - (2 * 4px)) !important';
    assert.equal(dump(parseDeclarations(text)), dump(reference(text)));
    assert.equal(parseDeclarations(text).get('width')?.important, true);
  });

  it('toKebab is stable through its cache', () => {
    for (let i = 0; i < 3; i++) {
      assert.equal(toKebab('backgroundColor'), 'background-color');
      assert.equal(toKebab('webkitTransform'), '-webkit-transform');
      assert.equal(toKebab('cssFloat'), 'float');
      assert.equal(toKebab('--myVar'), '--myVar');
    }
  });
});

describe('style: ops across flushes', () => {
  it('emits per-property diffs when a style text comes back from an earlier flush', async () => {
    const r = await runWidget(`<div id="b" style="color: red; width: 1px">x</div><script>
      var b = document.getElementById('b'), step = 0;
      var texts = ['color: blue; width: 1px', 'color: red; width: 1px', 'color: red', 'color: red; width: 1px; height: 2px'];
      function tick() { b.setAttribute('style', texts[step++]); if (step < texts.length) setTimeout(tick, 1); }
      setTimeout(tick, 1);</script>`, { timerRounds: 6 });
    const style = r.renderOps.filter((o: any) => o.op === 'style').map((o: any) => `${o.name}=${o.value}`);
    assert.deepEqual(style, ['color=blue', 'color=red', 'width=', 'width=1px', 'height=2px']);
  });

  it('element.style writes in a loop reach the host as the last value of each property', async () => {
    const r = await runWidget(`<div id="b">x</div><script>
      var b = document.getElementById('b'), n = 0;
      function tick() { for (var i = 0; i <= n; i++) { b.style.transform = 'translateX(' + i + 'px)'; b.style.opacity = String(i / 10); } if (++n < 4) setTimeout(tick, 1); }
      setTimeout(tick, 1);</script>`, { timerRounds: 6 });
    const style = r.renderOps.filter((o: any) => o.op === 'style').map((o: any) => `${o.name}=${o.value}`);
    assert.deepEqual(style.slice(-2), ['transform=translateX(3px)', 'opacity=0.3']);
  });
});

describe('style: element.style fast path', () => {
  // Random writes through every style API (property sets, setProperty with and
  // without priority, removeProperty, cssText, setAttribute/removeAttribute,
  // non-canonical values, detach/re-attach, clones) for N frames; the style the
  // host was told for each element (the raw sandbox ops, before validation)
  // must equal the document's at the end.
  const widget = '\n<div id="root"></div>\n<script>\nlet seed = __SEED__;\nconst rnd = (n) => { seed = (seed * 1103515245 + 12345) & 0x7fffffff; return seed % n; };\nconst pick = (a) => a[rnd(a.length)];\nconst root = document.getElementById(\'root\');\nconst all = [];\nfor (let i = 0; i < 12; i++) { const d = document.createElement(\'div\'); d.setAttribute(\'data-k\', \'k\' + i); if (i % 3 === 0) d.setAttribute(\'style\', \'left: 1px; top: 2px;\'); root.appendChild(d); all.push(d); }\nconst camel = [\'transform\', \'opacity\', \'left\', \'top\', \'backgroundColor\', \'width\', \'cssFloat\', \'webkitTransform\', \'fontSize\'];\nconst kebab = [\'transform\', \'opacity\', \'left\', \'background-color\', \'margin-top\', \'--custom\', \'--Mixed\', \'Foo-Bar\', \' color\', \'z-index\'];\nconst values = [\'translate(1px,2px)\', \'translate(\' + 3 + \'px,4px) rotate(5deg)\', \'0.5\', \'10px\', \'red\', \'rgb(1, 2, 3)\', \'calc(1px + 2px)\', \'red !important\', \'a;b: c\',\n  \'calc(1px\', \'x)\', \')(\', \'url("a;b")\', "it\'s", \'x\\\\\', \'BLUE\', \'  12px  \', \'\', \'linear-gradient(red, blue)\', \'var(--x)\', \'1px!ie\', \'IMPORTANT\', \'x !important y\'];\nconst texts = [\'left: 5px; top: 6px;\', \'transform: scale(2)\', \'color: red !important; width: 1px\', \'\', \'a: b; c\', \'top: 1px; top: 2px\', \'LEFT: 3px\', \'--x: 1; --X: 2\'];\nlet frame = 0;\nfunction mutate() {\n  const n = 1 + rnd(6);\n  for (let m = 0; m < n; m++) {\n    const el = pick(all);\n    switch (rnd(14)) {\n      case 0: case 1: case 2: case 3: el.style[pick(camel)] = pick(values); break;\n      case 4: case 5: el.style.setProperty(pick(kebab), pick(values), rnd(4) === 0 ? \'important\' : \'\'); break;\n      case 6: el.style.removeProperty(pick(kebab)); break;\n      case 7: el.style.cssText = pick(texts); break;\n      case 8: el.setAttribute(\'style\', pick(texts)); break;\n      case 9: el.removeAttribute(\'style\'); break;\n      case 10: el.style[pick(camel)] = \'\'; break;\n      case 11: if (el.parentNode) { el.remove(); } else { root.appendChild(el); } break;\n      case 12: el.style.transform = \'translate(\' + rnd(100) + \'px,\' + rnd(100) + \'px)\'; el.style.opacity = String(rnd(10) / 10); break;\n      case 13: { const c = el.cloneNode(false); el.replaceWith(c); all[all.indexOf(el)] = c; break; }\n    }\n  }\n  for (let k = 0; k < 4; k++) { const el = pick(all); el.style.transform = \'translate(\' + rnd(50) + \'px,\' + rnd(50) + \'px)\'; }\n  const truth = {};\n  for (const el of all) if (el.isConnected) truth[el.getAttribute(\'data-k\')] = el.getAttribute(\'style\');\n  console.log(\'TRUTH \' + JSON.stringify(truth));\n  if (++frame < 120) requestAnimationFrame(mutate);\n}\nrequestAnimationFrame(mutate);\n</script>\n';
  const stop = (n: number) => widget.replace('++frame < 120', '++frame < ' + n);
  const model = (ops: any[]) => {
    const key = new Map<number, string>();
    const style = new Map<number, Map<string, string>>();
    const parse = (t: string) => new Map([...parseDeclarations(t)].map(([k, d]) => [k, d.value + (d.important ? '!' : '')]));
    for (const op of ops) {
      if (op.op === 'el') {
        const attrs = new Map<string, string>(Object.entries(op.attrs ?? {}));
        if (attrs.has('data-k')) key.set(op.id, attrs.get('data-k')!);
        style.set(op.id, parse(attrs.get('style') ?? ''));
      } else if (op.op === 'attr' && op.name === 'style') style.set(op.id, parse(op.value));
      else if (op.op === 'rattr' && op.name === 'style') style.set(op.id, new Map());
      else if (op.op === 'rm') { key.delete(op.id); style.delete(op.id); }
      else if (op.op === 'style') {
        const m = style.get(op.id) ?? new Map();
        if (op.value === '') m.delete(op.name); else m.set(op.name, op.value + (op.important ? '!' : ''));
        style.set(op.id, m);
      }
    }
    return { key, style, parse };
  };
  for (const seed of [1, 7, 42, 1234, 99991]) {
    it(`host style state equals the document after random style writes (seed ${seed})`, async () => {
      for (const frames of [13, 50, 120]) {
        const r = await runWidget(stop(frames).replace('__SEED__', String(seed)), { timerRounds: frames + 5 });
        const truthLog = r.logs.filter((l) => l.text.startsWith('TRUTH ')).pop();
        assert.ok(truthLog, 'widget ran');
        const truth = JSON.parse(truthLog!.text.slice(6)) as Record<string, string | null>;
        const { key, style, parse } = model(r.ops.flat());
        let checked = 0;
        for (const [id, k] of key) {
          if (!(k in truth)) continue;
          assert.deepEqual([...(style.get(id) ?? new Map())].sort(), [...parse(truth[k] ?? '')].sort(), `seed ${seed} frames ${frames} ${k}`);
          checked++;
        }
        assert.ok(checked >= 8, `checked ${checked}`);
        assert.deepEqual(r.logs.filter((l) => l.level === 'error'), []);
      }
    });
  }
});

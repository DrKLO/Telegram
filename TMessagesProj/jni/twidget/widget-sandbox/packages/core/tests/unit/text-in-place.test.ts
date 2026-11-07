// textContent on an element whose sole child is a text node no script can reach
// updates that node in place. Everything script can observe and every op the host
// receives must match the replacing path exactly.

import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { runWidget } from '../../scripts/sandbox-node.ts';

const both = async (html: string, probes: string[], timerRounds = 4) => {
  const on = await runWidget(html, { timerRounds, probes });
  const off = await runWidget(html, { timerRounds, probes, inPlaceText: false });
  return { on, off };
};

const same = (on: any, off: any) => {
  assert.deepEqual(on.probes, off.probes);
  assert.deepEqual(on.ops, off.ops);
  assert.deepEqual(on.diags, off.diags);
};

describe('textContent in place', () => {
  it('a text node read through firstChild keeps the replaced node semantics', async () => {
    const { on, off } = await both(`<span id="t">a</span><script>
      var t = document.getElementById('t'), old = t.firstChild;
      setTimeout(function () { t.textContent = 'b'; window.r = [old.data, old.isConnected, old === t.firstChild, t.firstChild.data].join('|'); }, 1);</script>`, ['window.r']);
    same(on, off);
    assert.equal(on.probes[0], 'a|false|false|b');
  });

  it('a childNodes list obtained earlier behaves the same (linkedom lists are snapshots)', async () => {
    const { on, off } = await both(`<span id="t">a</span><script>
      var t = document.getElementById('t'), kids = t.childNodes, first = null;
      setTimeout(function () { t.textContent = 'b'; first = kids[0]; t.textContent = 'c'; window.r = [first.data, first.isConnected, kids[0].data, kids.length].join('|'); }, 1);</script>`, ['window.r']);
    same(on, off);
    assert.equal(on.probes[0], 'a|false|a|1');
  });

  it('siblings, lastChild and normalize expose the node', async () => {
    const { on, off } = await both(`<p id="p"><b id="b">x</b><i id="i">y</i></p><script>
      var b = document.getElementById('b'), i = document.getElementById('i');
      var viaLast = b.lastChild, viaSib = document.getElementById('p').firstChild.firstChild;
      i.normalize();
      setTimeout(function () { b.textContent = 'X'; i.textContent = 'Y'; window.r = [viaLast.isConnected, viaSib.isConnected, viaLast.data, b.textContent, i.textContent].join('|'); }, 1);</script>`, ['window.r']);
    same(on, off);
  });

  it('a MutationObserver still gets childList records', async () => {
    const { on, off } = await both(`<span id="t">a</span><script>
      var t = document.getElementById('t'), seen = [];
      new MutationObserver(function (rs) { rs.forEach(function (x) { seen.push(x.type + ':' + x.addedNodes.length + ':' + x.removedNodes.length); }); }).observe(t, { childList: true, characterData: true, subtree: true });
      setTimeout(function () { t.textContent = 'b'; }, 1);</script>`, ['seen.join()']);
    same(on, off);
    assert.equal(on.probes[0], 'childList:1:1');
  });

  it('a range or tree walker turns the in-place update off for the document', async () => {
    const { on, off } = await both(`<span id="t">a</span><script>
      var t = document.getElementById('t'), walker = document.createTreeWalker(document.body, 4), seen = [];
      setTimeout(function () { t.textContent = 'b'; var n; while ((n = walker.nextNode())) seen.push(n.data + ':' + n.isConnected); }, 1);</script>`, ['seen.join()']);
    same(on, off);
  });

  it('fuzz: random writes and exposing reads match the replacing path', async () => {
    let seed = 12345;
    const rnd = (n: number) => ((seed = (seed * 1103515245 + 12345) & 0x7fffffff) % n);
    for (let round = 0; round < 12; round++) {
      const steps: string[] = [];
      for (let k = 0; k < 40; k++) {
        const c = rnd(6);
        const kind = rnd(10);
        if (kind < 5) steps.push(`c[${c}].textContent = 'v${round}_${k}';`);
        else if (kind === 5) steps.push(`keep.push(c[${c}].firstChild);`);
        else if (kind === 6) steps.push(`lists.push(c[${c}].childNodes);`);
        else if (kind === 7) steps.push(`keep.push(c[${c}].lastChild);`);
        else if (kind === 8) steps.push(`c[${c}].textContent = '';`);
        else steps.push(`log.push(keep.map(function (n) { return n ? n.data + ':' + n.isConnected + ':' + (n.parentNode ? n.parentNode.id : '-') : 'null'; }).join(',') + '/' + lists.map(function (l) { return l.length + ':' + (l[0] ? l[0].data : '-'); }).join(','));`);
      }
      const html = `<div>${Array.from({ length: 6 }, (_, i) => `<span id="c${i}">s${i}</span>`).join('')}</div><script>
        var c = [], keep = [], lists = [], log = [];
        for (var i = 0; i < 6; i++) c.push(document.getElementById('c' + i));
        setTimeout(function () { ${steps.slice(0, 20).join('\n')} }, 1);
        setTimeout(function () { ${steps.slice(20).join('\n')} }, 2);</script>`;
      const { on, off } = await both(html, ['log.join("\\n")', 'document.body.innerHTML'], 5);
      same(on, off);
    }
  });

  it('fuzz: attribute reads and class writes match the full path', async () => {
    let seed = 4242;
    const rnd = (n: number) => ((seed = (seed * 1103515245 + 12345) & 0x7fffffff) % n);
    const values = ['up', 'down', 'up down', 'up  down', '', 'x', 'UP'];
    const names = ['class', 'CLASS', 'Class', 'id', 'data-x', 'nope', 'xlink:href', 'style'];
    for (let round = 0; round < 14; round++) {
      const steps: string[] = [];
      for (let k = 0; k < 50; k++) {
        const c = `c[${rnd(6)}]`;
        const v = JSON.stringify(values[rnd(values.length)]);
        switch (rnd(16)) {
          case 0: case 1: case 2: steps.push(`${c}.className = ${v};`); break;
          case 3: steps.push(`${c}.setAttribute('class', ${v});`); break;
          case 4: steps.push(`${c}.removeAttribute('class');`); break;
          case 5: steps.push(`${c}.classList.add('z');`); break;
          case 6: steps.push(`${c}.classList.remove('up');`); break;
          case 7: steps.push(`var an = ${c}.getAttributeNode('class'); if (an) an.value = ${v};`); break;
          case 8: steps.push(`${c}.setAttributeNS('http://www.w3.org/1999/xlink', 'xlink:href', '#a${k}');`); break;
          case 9: steps.push(`${c}.setAttribute('data-x', 'd${k}');`); break;
          case 10: steps.push(`${c}.removeAttribute('data-x');`); break;
          case 11: steps.push(`lists.push(${c}.classList);`); break;
          case 12: if (round % 4 === 0) steps.push(`if (!window.mo) { window.mo = new MutationObserver(function (rs) { rs.forEach(function (r) { log.push('mo:' + r.type + ':' + r.attributeName + ':' + r.oldValue); }); }); mo.observe(document.body, { attributes: true, attributeOldValue: true, subtree: true }); }`); break;
          default: steps.push(`log.push(c.map(function (e) { return ${JSON.stringify(names)}.map(function (n) { return n + '=' + e.getAttribute(n); }).join(';') + '|' + e.className + '|' + e.classList.length; }).join(',') + '/' + lists.map(function (l) { return l.value; }).join(','));`);
        }
      }
      const html = `<div><b id="c0" class="up">0</b><b id="c1">1</b><i id="c2" class="up down">2</i><svg><circle id="c3" class="up"/><rect id="c4"/></svg><b id="c5" class="UP" data-x="1">5</b></div><script>
        var c = [], lists = [], log = [];
        for (var i = 0; i < 6; i++) c.push(document.getElementById('c' + i));
        setTimeout(function () { ${steps.slice(0, 25).join('\n')} }, 1);
        setTimeout(function () { ${steps.slice(25).join('\n')} }, 2);</script>`;
      const on = await runWidget(html, { timerRounds: 5, probes: ['log.join("\\n")', 'document.body.innerHTML'] });
      const off = await runWidget(html, { timerRounds: 5, probes: ['log.join("\\n")', 'document.body.innerHTML'], attrFast: false });
      same(on, off);
    }
  });
});

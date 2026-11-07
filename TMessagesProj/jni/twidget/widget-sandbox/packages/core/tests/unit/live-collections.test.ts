// getElementsByClassName / getElementsByTagName answer live collections, as in browsers. They
// re-read their matches only after a mutation that can change them (an element inserted or
// removed, a class written): reads, data-* attributes, text and layout queries in a loop over a
// collection do not re-scan it, so the loop stays linear.

import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { runWidget } from '../../scripts/sandbox-node.ts';

describe('shim: live HTMLCollections', () => {
  it('follow inserts, removals and class changes', async () => {
    const r = await runWidget(`<div id="r"><p class="a b">1</p><p class="a">2</p><template><p class="a">t</p></template></div><script>
      var r = document.getElementById('r'), out = [];
      var a = document.getElementsByClassName('a'), ab = r.getElementsByClassName(' b  a '), ps = r.getElementsByTagName('P');
      out.push(a.length, ab.length, ps.length);
      var p = document.createElement('p'); p.className = 'a'; r.appendChild(p); out.push(a.length, ps.length);
      r.firstElementChild.classList.remove('a'); out.push(a.length, ab.length);
      p.setAttribute('class', 'z'); out.push(a.length);
      r.removeChild(p); out.push(ps.length);
      r.innerHTML = '<i class="a"></i>'; out.push(a.length, ps.length);
      var res = out.join(',');
    </script>`, { probes: ['res', 'a instanceof HTMLCollection'] });
    assert.deepEqual(r.probes, ['2,1,2,3,3,2,0,1,2,1,0', 'true']);
  });

  it('does not re-scan for reads, data-* attributes, text or layout queries in a loop', async () => {
    const r = await runWidget(`<div id="list"></div><script>
      var list = document.getElementById('list');
      for (var i = 0; i < 200; i++) { var d = document.createElement('div'); d.className = 'item'; d.textContent = 'x'; list.appendChild(d); }
      var items = document.getElementsByClassName('item'), scans = 0, get = Element.prototype.getAttributeNode;
      Element.prototype.getAttributeNode = function (n) { if (n === 'class') scans++; return get.call(this, n); };
      for (var i = 0; i < items.length; i++) {
        var el = items[i];
        if (el.classList.contains('item') && el.className && el.dataset) el.setAttribute('data-i', String(i));
        el.textContent = String(i);
        if (i % 20 === 0) el.getBoundingClientRect();
      }
      Element.prototype.getAttributeNode = get;
    </script>`, { probes: ['scans', 'items.length'] });
    assert.equal(r.probes[1], '200');
    assert.ok(Number(r.probes[0]) <= 2 * 200, `the collection was re-scanned: ${r.probes[0]} class reads for 200 items`);
  });
});

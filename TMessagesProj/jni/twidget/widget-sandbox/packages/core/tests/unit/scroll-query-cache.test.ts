import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { runWidget } from '../../scripts/sandbox-node.ts';

describe('host scroll geometry', () => {
  it('refreshes offsets and descendant rectangles before dispatching scroll', async () => {
    const html = `<div id=scroll><span id=child>Text</span></div><script>
      const scroller=document.getElementById('scroll'),child=document.getElementById('child');
      var seen=[];
      function read(){seen.push([scroller.scrollTop,child.getBoundingClientRect().top,scroller.scrollTop])}
      read();scroller.onscroll=read;
    </script>`;
    const first = await runWidget(html, { timerRounds: 0 });
    const target = first.renderOps.find((op: any) => op.op === 'el' && op.tag === 'div') as any;
    const reads = new Map<string, number>();
    const result = await runWidget(html, {
      timerRounds: 0,
      answer(q) {
        const key = JSON.stringify(q);
        const count = reads.get(key) ?? 0;
        reads.set(key, count + 1);
        if(q.q === 'box') return { scrollTop: count === 0 ? 0 : 288 };
        if(q.q === 'rect') return { x: 0, y: count === 0 ? 50 : -238, width: 100, height: 24 };
        return {};
      },
      events: [{ type: 'scroll', targetId: target.id, bubbles: false }],
      probes: ['JSON.stringify(seen)'],
    });
    assert.deepEqual(result.probes, ['[[0,50,0],[288,-238,288]]']);
    assert.deepEqual([...reads.values()], [2, 2]);
  });
});

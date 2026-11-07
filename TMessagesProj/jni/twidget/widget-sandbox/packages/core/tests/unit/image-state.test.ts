import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { runWidget } from '../../scripts/sandbox-node.ts';

const PNG = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAE0lEQVR4AWM8Y2z8nwEImBigAAAh4QI1lhEEowAAAABJRU5ErkJggg==';
const HTML = `<script>window.s = new Image(); s.src = '${PNG}';</script>`;

async function scriptImage(events: Record<string, unknown>[], probes: string[]) {
  const first = await runWidget(HTML, {});
  const el: any = first.renderOps.find((op: any) => op.op === 'el' && op.tag === 'img');
  assert.ok(el, 'the image reaches the renderer before it is inserted');
  assert.equal(el.attrs.find((a: string[]) => a[0] === 'src')?.[1], PNG);
  assert.ok(!first.renderOps.some((op: any) => op.op === 'ins' && op.id === el.id), 'and is not inserted');
  return (await runWidget(HTML, { events: events.map((e) => ({ targetId: el.id, ...e })), probes })).probes;
}

describe('a script image: state as a browser keeps it', () => {
  it('pending: not complete, no size', async () => {
    assert.deepEqual(await scriptImage([], ['s.complete', 's.naturalWidth', 's.width']), ['false', '0', '0']);
  });
  it('loaded: complete, its natural size, the width a detached image reports', async () => {
    assert.deepEqual(await scriptImage([{ type: 'load', naturalWidth: 2, naturalHeight: 3 }], ['s.complete', 's.naturalWidth', 's.width', 's.height']), ['true', '2', '2', '3']);
  });
  it('broken: complete, no natural size', async () => {
    assert.deepEqual(await scriptImage([{ type: 'error' }], ['s.complete', 's.naturalWidth']), ['true', '0']);
  });
  it('a new src is pending again; the width attribute wins over the natural size', async () => {
    const probes = ['(s.src = s.src + "#2", s.complete)', '(s.width = 7, s.width)', 's.getAttribute("width")'];
    assert.deepEqual(await scriptImage([{ type: 'load', naturalWidth: 2, naturalHeight: 2 }], probes), ['false', '7', '7']);
  });
});

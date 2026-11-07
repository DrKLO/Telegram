import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { runWidget } from '../../scripts/sandbox-node.ts';

/** The canvas commands the renderer gets for `script`, run on `x`, a 2D context. */
async function commands(script: string): Promise<any[][]> {
  const r = await runWidget(`<canvas id=c></canvas><script>var x = document.getElementById('c').getContext('2d');\n${script}</script>`, {});
  return r.renderOps.filter((op: any) => op.op === 'canvas').flatMap((op: any) => op.cmds);
}

describe('canvas arguments are converted as a real context converts them', () => {
  it('text is a string', async () => {
    const cmds = await commands(`x.fillText(4, 10, 20); x.strokeText(true, '5', 6); x.fillText(undefined, 1, 2); x.fillText({}, 1, 2); x.fillText(null, 1, 2, '30');`);
    assert.deepEqual(cmds, [
      ['c', 'fillText', '4', 10, 20],
      ['c', 'strokeText', 'true', 5, 6],
      ['c', 'fillText', 'undefined', 1, 2],
      ['c', 'fillText', '[object Object]', 1, 2],
      ['c', 'fillText', 'null', 1, 2, 30],
    ]);
  });

  it('coordinates are numbers, and a call with one that is not finite does nothing', async () => {
    const cmds = await commands(`x.fillRect('1', '2', 3, 4); x.fillRect(NaN, 1, 2, 3); x.clearRect(0, Infinity, 1, 1); x.moveTo({ valueOf() { return 7; } }, 8);
      x.lineTo(1, NaN); x.translate('250', '60'); x.fillText('a', NaN, 0); x.drawImage(x.canvas, 0, Infinity);`);
    assert.deepEqual(cmds, [
      ['c', 'fillRect', 1, 2, 3, 4],
      ['c', 'moveTo', 7, 8],
      ['c', 'translate', 250, 60],
    ]);
  });

  it('anticlockwise is a boolean, setTransform takes a matrix, roundRect keeps its radii', async () => {
    const cmds = await commands(`x.arc(10, 10, 5, 0, 1, 1); x.ellipse(1, 2, 3, 4, 0, 0, 1, 0);
      x.setTransform({ a: 2, b: 0, c: 0, d: 2, e: 5, f: 6 }); x.setTransform(); x.setTransform(new DOMMatrix([1, 0, 0, 1, 7, 8]));
      x.roundRect(0, 0, 10, 10, [2, 4]); var p = new Path2D(); p.roundRect(0, 0, 10, 10, [2, 4]); p.lineTo(NaN, 1); p.arc(1, 1, 1, 0, 1, true); x.fill(p);`);
    assert.deepEqual(cmds, [
      ['c', 'arc', 10, 10, 5, 0, 1, true],
      ['c', 'ellipse', 1, 2, 3, 4, 0, 0, 1, false],
      ['c', 'setTransform', 2, 0, 0, 2, 5, 6],
      ['c', 'setTransform', 1, 0, 0, 1, 0, 0],
      ['c', 'setTransform', 1, 0, 0, 1, 7, 8],
      ['c', 'roundRect', 0, 0, 10, 10, [2, 4]],
      ['pn', 1, null],
      ['pc', 1, 'roundRect', 0, 0, 10, 10, [2, 4]],
      ['pc', 1, 'arc', 1, 1, 1, 0, 1, 1],
      ['c', 'fill', { p: 1 }],
    ]);
  });
});

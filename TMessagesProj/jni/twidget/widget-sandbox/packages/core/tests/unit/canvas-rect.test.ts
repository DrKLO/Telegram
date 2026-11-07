import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { runWidget } from '../../scripts/sandbox-node.ts';

function widget(seed: number): string {
  let state = BigInt(seed);
  const next = (n: number) => {
    state = (state * 6364136223846793005n + 1442695040888963407n) & 0xffffffffffffffffn;
    return Number((state >> 33n) % BigInt(n));
  };
  const args = ['1, 2, 3, 4', '0.5, -2, 1e3, 7', '1, 2, 3', '1, 2, 3, 4, 5', '1, 2, 3, undefined', "'1', 2, 3, 4", '1, 2, 3, NaN', '-0, Infinity, 3, 4', 'null, 2, 3, 4', '1, 2, {}, 4', '1e308, -1e308, 5e-324, 4'];
  const methods = ['fillRect', 'strokeRect', 'clearRect'];
  const calls: string[] = [];
  for (let i = 0; i < 300; i++) {
    switch (next(6)) {
      case 0: calls.push(`x.fillStyle = 'hsl(${next(360)},90%,60%)';`); break;
      case 1: calls.push(`x.save(); x.translate(${next(9)}, 1); x.restore();`); break;
      default: calls.push(`x.${methods[next(3)]}(${args[next(args.length)]});`);
    }
  }
  return `<canvas id=c width=200 height=120></canvas><script>
var x = document.getElementById('c').getContext('2d'), frame = 0;
function f() { frame++; ${calls.join('\n')} if (frame < 3) requestAnimationFrame(f); }
requestAnimationFrame(f);
console.log('len:' + x.fillRect.length);
</script>`;
}

describe('canvas rect fast path', () => {
  for (const words of [true, false]) {
    it(`sends the same canvas commands as the generic path (canvasWords ${words})`, async () => {
      for (const seed of [99, 7, 12345]) {
        const html = widget(seed);
        const now = () => { let t = 1000; return () => (t += 16); };
        const fast = await runWidget(html, { canvasWords: words, now: now(), timerRounds: 4 });
        const generic = await runWidget(html, { canvasWords: words, canvasRect: false, now: now(), timerRounds: 4 });
        assert.ok(fast.ops.length > 1);
        assert.deepEqual(fast.ops, generic.ops, `seed ${seed}`);
        assert.deepEqual(fast.renderOps, generic.renderOps, `seed ${seed}`);
        if (words) assert.ok(fast.canvasWordBatches > 0);
      }
    });
  }
});

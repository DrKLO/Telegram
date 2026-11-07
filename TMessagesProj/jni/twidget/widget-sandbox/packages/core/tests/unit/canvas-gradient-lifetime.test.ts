import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { runWidget } from '../../scripts/sandbox-node.ts';

const html = `<canvas id=c width=332 height=72></canvas><script>
const x = document.getElementById('c').getContext('2d');
function gradient(color) {
  const g = x.createLinearGradient(0, 72, 0, 0);
  g.addColorStop(0, color); g.addColorStop(1, color);
  return g;
}
const retained = gradient('red');
x.fillStyle = retained;
x.save();
x.fillStyle = gradient('blue');
x.save();
let frame = 0;
function draw() {
  x.clearRect(0, 0, 332, 72);
  for (let i = 0; i < 150; i++) {
    x.fillStyle = gradient('green');
    x.fillRect(i % 24 * 14, 20, 10, 52);
  }
  if (++frame < 60) requestAnimationFrame(draw);
  else {
    x.restore(); x.fillRect(0, 0, 10, 10);
    x.restore(); x.fillRect(14, 0, 10, 10);
    retained.addColorStop(.5, 'yellow');
    x.fillRect(28, 0, 10, 10);
    console.log('complete');
  }
}
requestAnimationFrame(draw);
</script>`;

describe('canvas gradient lifetime', () => {
  for (const canvasWords of [false, true]) {
    it(`recycles discarded gradients without replacing saved or retained paints (words ${canvasWords})`, async () => {
      const result = await runWidget(html, { canvasWords, timerRounds: 62 });
      assert.ok(result.logs.some(log => log.text === 'complete'), JSON.stringify(result.logs));
      const commands = result.renderOps.flat().filter((op: any) => op.op === 'canvas').flatMap((op: any) => op.cmds);
      const creations = commands.filter(cmd => cmd[0] === 'g');
      assert.equal(creations.length, 9002);
      const ids = creations.map(cmd => cmd[1]);
      assert.ok(new Set(ids).size <= 4096);
      assert.equal(ids.filter(id => id === ids[0]).length, 1);
      assert.equal(ids.filter(id => id === ids[1]).length, 1);
      assert.deepEqual(commands.at(-2), ['gs', ids[0], .5, 'yellow']);
      assert.equal(result.diags.filter(d => d.kind === 'canvas' || d.kind === 'invalid-op').length, 0);
      if (canvasWords) assert.ok(result.canvasWordBatches > 0);
    });
  }

  it('keeps the live-gradient limit bounded and allows ordinary drawing after it is reached', async () => {
    const result = await runWidget(`<canvas id=c></canvas><script>
      const x = document.getElementById('c').getContext('2d'), keep = [];
      try { for (let i = 0; i < 4097; i++) keep.push(x.createLinearGradient(0, 0, 1, 1)); }
      catch (error) { console.log(error.name + ':' + keep.length); }
      x.fillStyle = 'red'; x.fillRect(0, 0, 1, 1);
    </script>`, { policy: 'compat' }); // compat: thousands of gradients in one task, apart from the strict cap on commands per send
    assert.ok(result.logs.some(log => log.text === 'RangeError:4096'));
    const commands = result.renderOps.flat().filter((op: any) => op.op === 'canvas').flatMap((op: any) => op.cmds);
    assert.equal(commands.filter(cmd => cmd[0] === 'g').length, 4096);
    assert.deepEqual(commands.at(-1), ['c', 'fillRect', 0, 0, 1, 1]);
  });
});

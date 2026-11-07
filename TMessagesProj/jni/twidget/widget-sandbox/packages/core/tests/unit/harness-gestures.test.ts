// The Node harness's presses (scripts/sandbox-node.ts `gestures`, scripts/gesture-run.ts): the renderer core in headless
// Chromium plays a phone's bubble on the sandbox's validated ops, and each press is reported with its claim, the shared
// policy's outcome, what the widget received and a hint. Needs Playwright's Chromium (the repo's dev dependency).

import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { runWidget } from '../../scripts/sandbox-node.ts';

const WIDGET = `<style>body{margin:0}button{width:120px;height:60px;margin:30px}#fire{touch-action:none}</style>
<button id="move">move</button><button id="fire">fire</button><p id="s"></p>
<script>
var s = document.getElementById('s'), n = 0, d = 0;
['pointerdown', 'pointerup', 'pointercancel'].forEach(function (t) { document.addEventListener(t, function (e) { s.textContent = e.target.id + ' ' + t; }); });
document.addEventListener('contextmenu', function () { n++; });
document.addEventListener('dblclick', function () { d++; });
</script>`;

describe('harness gestures', () => {
  it('a hold on auto is the message menu, on touch-action: none the widget\'s; a double tap is always the widget\'s; the width lays the widget out', async () => {
    const r = await runWidget(WIDGET, {
      width: 360,
      gestures: [{ kind: 'hold', x: 90, y: 60, ms: 700 }, { kind: 'hold', x: 270, y: 60, ms: 700 }, { kind: 'double-tap', x: 90, y: 60 }, { kind: 'swipe', x: 90, y: 60, dx: 80, dy: 0 }],
      probes: ['s.textContent', 'n', 'd'],
    });
    const [onAuto, onNone, dbl, swipe] = r.gestures!;
    assert.equal(onAuto.target, 'button#move');
    assert.deepEqual(onAuto.claim, { x: false, y: false, hold: false });
    assert.deepEqual(onAuto.outcome, { owner: 'bubble', gesture: 'hold', cancel: true, action: 'menu' });
    assert.deepEqual(onAuto.received, ['pointerdown button#move', 'pointercancel button#move']);
    assert.deepEqual(onAuto.bubble, ['menu']);
    assert.match(onAuto.hint!, /touch-action: none on button#move/);

    assert.equal(onNone.touchAction, 'none');
    assert.deepEqual(onNone.outcome, { owner: 'widget', gesture: 'hold', cancel: false, event: 'contextmenu' });
    assert.deepEqual(onNone.received, ['pointerdown button#fire', 'contextmenu button#fire', 'pointerup button#fire']);
    assert.equal(onNone.hint, null);

    // A double tap over the widget is the widget's on every element: both clicks, then dblclick.
    assert.deepEqual(dbl.outcome, { owner: 'widget', gesture: 'double-tap', cancel: false, event: 'dblclick' });
    assert.deepEqual(dbl.received.slice(-3), ['pointerup button#move', 'click button#move', 'dblclick button#move']);
    assert.equal(swipe.outcome?.action, 'swipe');
    assert.match(swipe.hint!, /pan-y/);

    assert.deepEqual(r.probes, ['move pointercancel', '1', '1']);
    assert.ok(r.height! > 100 && r.height! < 200, String(r.height));
    assert.deepEqual(r.listens.sort(), ['contextmenu', 'dblclick', 'pointercancel', 'pointerdown', 'pointerup']);
  });

  it('a vertical drag is the chat\'s even on touch-action: none, with the hint; a horizontal one there is the widget\'s (v0.1.3)', async () => {
    const r = await runWidget(WIDGET, {
      width: 360,
      gestures: [{ kind: 'swipe', x: 270, y: 60, dx: 0, dy: -80 }, { kind: 'swipe', x: 270, y: 60, dx: 80, dy: 10 }],
      probes: ['s.textContent'],
    });
    const [up, side] = r.gestures!;
    assert.deepEqual(up.claim, { x: true, y: false, hold: true });
    assert.deepEqual(up.outcome, { owner: 'bubble', gesture: 'drag-y', cancel: true, action: 'scroll' });
    assert.deepEqual(up.received, ['pointerdown button#fire', 'pointercancel button#fire']);
    assert.match(up.hint!, /^vertical drags always scroll the chat; a widget cannot claim them/);
    assert.deepEqual(side.outcome, { owner: 'widget', gesture: 'drag-x', cancel: false });
    assert.equal(side.hint, null);
  });
});

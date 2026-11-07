// User activation for privileged bridge calls: the host-side policy shared by the
// web host (src/host/user-activation.ts) and the Node harness.

import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { GESTURE_TTL_MS, GestureCounter, HostActivationGate, isUserActivation } from '../../src/host/user-activation.ts';
import { runWidget } from '../../scripts/sandbox-node.ts';

describe('user activation: policy', () => {
  it('counts pointer presses and pointer clicks, never a key, a keyboard click or a move', () => {
    assert.ok(isUserActivation({ type: 'click', detail: 1 }));
    assert.ok(isUserActivation({ type: 'pointerdown' }));
    for (const key of ['Enter', ' ', 'a', 'Escape', 'Tab', 'ArrowDown']) assert.ok(!isUserActivation({ type: 'keydown', key } as any), key);
    assert.ok(!isUserActivation({ type: 'click', detail: 0 }), 'the click Enter or Space activates');
    assert.ok(!isUserActivation({ type: 'click' }), 'no click count');
    assert.ok(!isUserActivation({ type: 'mousemove' }));
    assert.ok(!isUserActivation({ type: 'scroll' }));
  });

  it('numbers one physical press as one gesture', () => {
    const c = new GestureCounter();
    assert.deepEqual(['pointerdown', 'mousedown', 'pointerup', 'mouseup', 'click', 'dblclick'].map((type) => c.gestureFor({ type })), [1, 1, 1, 1, 1, 1]);
    assert.equal(c.gestureFor({ type: 'pointerdown' }), 2);
    assert.equal(c.gestureFor({ type: 'click', detail: 1 }), 2, 'the click that ends the press');
    assert.equal(c.gestureFor({ type: 'click', detail: 1 }), 3, 'a click forwarded alone is its own press');
    assert.equal(c.gestureFor({ type: 'mousemove' }), 0);
  });

  it("a press the renderer reports begins a new gesture: a finger's release after a drag is not the drag's", () => {
    const c = new GestureCounter();
    // a drag's release (its pointerdown not trusted: a finger), no click after it
    assert.equal(c.gestureFor({ type: 'pointerup' }), 1);
    // the next press: the claim, its pointerdown again not trusted, then its release and its click
    c.pressBegan();
    assert.equal(c.gestureFor({ type: 'pointerup' }), 2);
    assert.equal(c.gestureFor({ type: 'click', detail: 1 }), 2);
    // a mouse's press after a claim is one gesture, as before
    c.pressBegan();
    assert.deepEqual(['pointerdown', 'mousedown', 'pointerup', 'mouseup', 'click'].map((type) => c.gestureFor({ type, detail: 1 })), [3, 3, 3, 3, 3]);
  });

  it('keys and keyboard clicks are never a gesture', () => {
    const c = new GestureCounter();
    for (const key of ['Enter', ' ', 'Tab']) assert.equal(c.gestureFor({ type: 'keydown', key } as any), 0, key);
    assert.equal(c.gestureFor({ type: 'click', detail: 0 }), 0, 'the click Enter or Space activates');
    assert.equal(c.gestureFor({ type: 'dblclick', detail: 0 }), 0);
    assert.equal(c.gestureFor({ type: 'click', detail: 1 }), 1, 'a pointer click still is');
    assert.equal(c.gestureFor({ type: 'click', detail: 0 }), 0, 'a keyboard click after it is not');
  });

  it('accepts each issued gesture once, while visible and recent, and nothing the host did not issue', () => {
    const g = new HostActivationGate();
    assert.ok(!g.accept(1, 0, true), 'not issued');
    g.issue(1, 0);
    assert.ok(!g.accept(1, 0, false), 'hidden');
    assert.ok(g.accept(1, 10, true));
    assert.ok(!g.accept(1, 10, true), 'consumed');
    g.issue(2, 100);
    assert.ok(!g.accept(2, 100 + GESTURE_TTL_MS + 1, true), 'stale');
    g.issue(3, 200);
    assert.ok(!g.accept('3', 200, true), 'not a number');
    assert.ok(!g.accept(99, 200, true), 'forged number');
    assert.ok(g.accept(3, 200, true));
    g.issue(4, 300);
    g.issue(5, 300);
    assert.ok(g.accept(5, 300, true));
    assert.ok(!g.accept(4, 300, true), 'an older gesture cannot be used after a newer one');
  });
});

describe('user activation: harness', () => {
  const widget = `<button id="b">go</button><script>
    TelegramWidget.ready();
    setTimeout(function () { TelegramWidget.openLink('https://example.com/timer'); }, 1);
    Promise.resolve().then(function () { TelegramWidget.openLink('https://example.com/boot'); });
    document.getElementById('b').addEventListener('click', function () { TelegramWidget.openLink('https://example.com/clicked'); TelegramWidget.openTelegramLink('https://t.me/again'); });
  </script>`;

  it('privileged calls outside a gesture are blocked; one trusted click unlocks one call', async () => {
    const first = await runWidget(widget, { timerRounds: 2, policy: 'strict' });
    const button = first.renderOps.find((o: any) => o.op === 'el' && o.tag === 'button') as any;
    const r = await runWidget(widget, { timerRounds: 2, policy: 'strict', events: [{ type: 'click', targetId: button.id }, { type: 'click', targetId: button.id, gesture: 1 }, { type: 'click', targetId: button.id, gesture: 1 }] });
    assert.deepEqual(r.bridge.map((b: any) => b[0] + ':' + JSON.stringify(b[1])), ['ready:[]', 'openLink:["https://example.com/clicked"]'], 'one press unlocks one call, shared by both link methods');
    assert.ok(r.diags.filter((d) => d.kind === 'activation-blocked').length >= 4);
  });

  it('a hidden widget cannot use a gesture', async () => {
    const first = await runWidget(widget, { timerRounds: 2, policy: 'strict' });
    const button = first.renderOps.find((o: any) => o.op === 'el' && o.tag === 'button') as any;
    const hidden = await runWidget(widget, { timerRounds: 2, policy: 'strict', visible: false, events: [{ type: 'click', targetId: button.id, gesture: 1 }] });
    assert.deepEqual(hidden.bridge.map((b: any) => b[0]), ['ready']);
    assert.ok(hidden.diags.some((d) => d.kind === 'activation-blocked' && d.detail.startsWith('openLink')));
    const shown = await runWidget(widget, { timerRounds: 2, policy: 'strict', events: [{ type: 'click', targetId: button.id, gesture: 1 }] });
    assert.deepEqual(shown.bridge.map((b: any) => b[0]), ['ready', 'openLink']);
  });

  it('strict is the default, and anything but compat is strict', async () => {
    for (const policy of [undefined, 'Compat', 'compact'] as const) {
      const r = await runWidget(widget, { timerRounds: 2, policy: policy as 'compat' | undefined });
      assert.deepEqual(r.bridge.map((b: any) => b[0]), ['ready'], String(policy));
    }
  });

  it('the compat policy forwards every bridge call, as before the gate', async () => {
    const r = await runWidget(widget, { timerRounds: 2, policy: 'compat' });
    const calls = r.bridge.map((b: any) => b[0] + ':' + JSON.stringify(b[1]));
    assert.ok(calls.includes('openLink:["https://example.com/timer"]'), JSON.stringify(calls));
    assert.ok(calls.includes('openLink:["https://example.com/boot"]'), JSON.stringify(calls));
    assert.ok(!r.diags.some((d) => d.kind === 'activation-blocked'));
  });
});

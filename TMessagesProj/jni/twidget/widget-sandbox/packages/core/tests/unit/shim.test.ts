// Regression tests for the in-sandbox shim, run through the real QuickJS bundle
// with the Node harness (scripts/sandbox-node.ts). Each case is a bug found by
// the compat or attack suites and fixed generally in the shim.

import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { runWidget } from '../../scripts/sandbox-node.ts';

const elementsOf = (r: Awaited<ReturnType<typeof runWidget>>) => r.renderOps.filter((o) => o.op === 'el').length;
const errorsOf = (r: Awaited<ReturnType<typeof runWidget>>) => r.logs.filter((l) => l.level === 'error').map((l) => l.text);

describe('shim: scripts', () => {
  it('does not execute scripts inserted through innerHTML / insertAdjacentHTML (no runaway recursion)', async () => {
    const r = await runWidget(`<p>x</p><script>document.body.innerHTML += '<i>after</i>';</script>`, { probes: ['document.querySelectorAll("i").length', 'document.querySelectorAll("script").length'] });
    assert.deepEqual(errorsOf(r), []);
    assert.ok(elementsOf(r) < 20, `element explosion: ${elementsOf(r)} elements`);
    assert.equal(r.probes[0], '1');
    const r2 = await runWidget(`<div id="d"></div><script>var n = 0; document.getElementById('d').innerHTML = '<script>n = 1<\\/script>'; document.getElementById('d').insertAdjacentHTML('beforeend', '<script>n = 2<\\/script>');</script>`, { probes: ['n'] });
    assert.equal(r2.probes[0], '0');
  });
  it('executes scripts created with createElement and dispatches load/error for src scripts', async () => {
    const r = await runWidget(`<script>var ran = 0; var s = document.createElement('script'); s.textContent = 'ran = 1'; document.body.appendChild(s);
      var e = document.createElement('script'); e.src = 'https://example.invalid/x.js'; e.onerror = function () { ran += 10; }; document.body.appendChild(e);</script>`, { probes: ['ran'] });
    assert.equal(r.probes[0], '11');
    assert.ok(r.diags.some((d) => d.kind === 'script-skipped'));
  });
});

describe('shim: globals', () => {
  for (const binaryOps of [false, true]) {
    it(`keeps rendering when widget code replaces JSON.stringify (binary=${binaryOps})`, async () => {
      const r = await runWidget(`<div id="field"></div><div id="value">before</div><script>
        var original = JSON.stringify;
        JSON.stringify = function (value) {
          if (value && (value.op || Array.isArray(value) && value.some(function (item) { return item && item.op; }))) return '[]';
          return original.apply(JSON, arguments);
        };
        document.getElementById('value').textContent = 'after replacement';
        document.getElementById('field').scrollTo(0, 5);
      </script>`, { binaryOps, timerRounds: 0 });
      assert.deepEqual(errorsOf(r), []);
      assert.ok(r.renderOps.some((op) => (op.op === 'text' || op.op === 'data') && op.data === 'after replacement'));
      assert.ok(r.renderOps.some((op) => op.op === 'call' && op.method === 'scrollTo'));
    });
  }
  it('lets top-level var declarations replace window aliases (frames, top, parent)', async () => {
    const r = await runWidget(`<script>var frames = 0; frames++; var top = 'x';</script>`, { probes: ['frames', 'top'] });
    assert.deepEqual(r.probes, ['1', 'x']);
  });
  it('network APIs throw NetworkDisabledError and are recorded', async () => {
    const r = await runWidget(`<script>var names = []; try { new WebSocket('ws://x') } catch (e) { names.push(e.name) } try { new XMLHttpRequest() } catch (e) { names.push(e.name) }
      fetch('http://x').catch(function (e) { names.push(e.name) });</script>`, { probes: ['names.join(",")'] });
    assert.equal(r.probes[0], 'NetworkDisabledError,NetworkDisabledError,NetworkDisabledError');
    assert.ok(r.diags.filter((d) => d.kind === 'network-blocked').length >= 3);
  });
});

describe('shim: selectors and forms', () => {
  it(':checked reflects live state, including :not() and :has()', async () => {
    const r = await runWidget(`<label><input type="checkbox" id="a"></label><input type="radio" name="g" id="b"><input type="radio" name="g" id="c" checked>
      <script>document.getElementById('a').checked = true; document.getElementById('b').checked = true;</script>`, {
      probes: ['[...document.querySelectorAll(":checked")].map(e => e.id).join(",")', 'document.querySelectorAll("input:not(:checked)").length', 'document.querySelectorAll("label:has(:checked)").length', 'document.getElementById("c").checked'],
    });
    assert.deepEqual(r.probes, ['a,b', '1', '1', 'false']);
  });
  it('FormData and select/option state follow events from the renderer', async () => {
    const r = await runWidget(`<form id="f"><input name="q" value="init"><select name="s"><option value="1">a</option><option value="2">b</option></select></form>`, {
      events: [{ type: 'input', targetId: 0, value: 'typed' }],
      probes: ['new FormData(document.getElementById("f")).get("q")', 'document.querySelector("select").value'],
    });
    // targetId 0 is unknown and ignored; the default values must survive.
    assert.deepEqual(r.probes, ['init', '1']);
  });
});

describe('shim: runtime limits', () => {
  it('throws RangeError beyond maxTimers, reports timer-limit once, and earlier timers still fire', async () => {
    const r = await runWidget(`<script>var fired = 0, err = null; for (var i = 0; i < 8; i++) setTimeout(function () { fired++; }, 1);
      try { setTimeout(function () { fired += 100; }, 1); } catch (e) { err = e.name; }
      try { setInterval(function () {}, 10); } catch (e) { err += ',' + e.name; }</script>`, { maxTimers: 8, timerRounds: 1, probes: ['err', 'fired'] });
    assert.deepEqual(r.probes, ['RangeError,RangeError', '8']);
    assert.equal(r.diags.filter((d) => d.kind === 'timer-limit').length, 1);
    assert.equal(r.diags.filter((d) => d.kind === 'timer-limit-enforced').length, 0, 'the glue backstop must not be needed');
    assert.equal(r.timers, 8);
  });
  it('fires timers in the order of their delays, hidden ones too (1 Hz, still in order)', async () => {
    const widget = `<script>var order = []; [20, 10, 0].forEach(function (d) { setTimeout(function () { order.push(d); }, d); });</script>`;
    for (const visible of [true, false]) {
      const r = await runWidget(widget, { visible, timerRounds: 1, probes: ['order.join()'] });
      assert.equal(r.probes[0], '0,10,20', visible ? 'visible' : 'hidden');
    }
  });
  it('a timer id asked for again replaces its timer and is not refused at maxTimers', async () => {
    // The widget's JSON.stringify writes the shim's timer calls: two ids the shim never made fill the glue to maxTimers,
    // then one of them is asked for again, which replaces it (no backstop refusal); the shim's own timer fires once.
    const r = await runWidget(`<script>var fired = 0, ids = [1001, 1002, 1001];
      setTimeout(function () { fired++; }, 1);
      var orig = JSON.stringify;
      JSON.stringify = function (v) { return Array.isArray(v) && v.length === 2 && ids.length ? '[' + ids.shift() + ',1]' : orig(v); };
      for (var i = 0; i < 3; i++) clearTimeout(setTimeout(function () {}, 1));
      JSON.stringify = orig;</script>`, { maxTimers: 3, timerRounds: 1, probes: ['fired'] });
    assert.equal(r.diags.filter((d) => d.kind === 'timer-limit-enforced').length, 0, JSON.stringify(r.diags));
    assert.equal(r.probes[0], '1');
  });
  it('frees a slot when a timer fires or is cleared', async () => {
    const r = await runWidget(`<script>var ok = 0; var a = setTimeout(function () {}, 1); var b = setTimeout(function () {}, 1); clearTimeout(a);
      try { setTimeout(function () { ok++; }, 1); } catch (e) { ok = -1; }</script>`, { maxTimers: 2, timerRounds: 1, probes: ['ok'] });
    assert.equal(r.probes[0], '1');
  });
});

describe('shim: visibility', () => {
  it('flips document.hidden / visibilityState and fires visibilitychange on the host signal', async () => {
    const r = await runWidget(`<script>var n = 0; document.addEventListener('visibilitychange', function () { n++; });</script>`, {
      script: [{ probe: 'document.visibilityState + "," + document.hidden' }, { visible: false }, { probe: 'document.visibilityState + "," + document.hidden + "," + n' }, { visible: false }, { visible: true }, { probe: 'document.visibilityState + "," + n' }],
    });
    assert.deepEqual(r.probes, ['visible,false', 'hidden,true,1', 'visible,2']);
  });
});

describe('harness: cpu quota', () => {
  it('defers timers once the widget has burnt its quota, but still runs events', async () => {
    // Each timer tick burns ~40 ms; with a 100 ms/s quota the third overruns it (a debt) and the fourth tick and the frame are deferred.
    const r = await runWidget(`<script>var ticks = 0; function burn() { var t = performance.now(); while (performance.now() - t < 40) {} ticks++; }
      for (var i = 0; i < 4; i++) setTimeout(burn, 1);</script>`, { cpuQuotaMs: 100, timerRounds: 1, script: [{ probe: 'ticks' }] });
    assert.equal(r.probes[0], '3');
    assert.equal(r.throttled, 2, 'the fourth timer and the frame are deferred');
    assert.ok(r.diags.some((d) => d.kind === 'cpu-throttled'));
    const r2 = await runWidget(`<script>var t = performance.now(); while (performance.now() - t < 150) {} var clicks = 0; document.addEventListener('click', function () { clicks++; });</script>`, {
      cpuQuotaMs: 100, timerRounds: 0, events: [{ type: 'click', targetId: 2 }], probes: ['clicks'],
    });
    assert.equal(r2.probes[0], '1', 'events run even with the quota spent');
    assert.equal(r2.throttled, 0);
  });
  it('a timer task started with little quota left finishes: the overrun is a debt, not a stop', async () => {
    // 06-cat on a slow phone: the second 70 ms tick starts with 30 ms of a 100 ms/s quota left (it used to get a 50 ms slice and stop).
    const r = await runWidget(`<script>var ticks = 0; function burn() { var t = performance.now(); while (performance.now() - t < 70) {} ticks++; }
      for (var i = 0; i < 3; i++) setTimeout(burn, 1);</script>`, { cpuQuotaMs: 100, timerRounds: 1, script: [{ probe: 'ticks' }] });
    assert.deepEqual(errorsOf(r), []);
    assert.equal(r.probes[0], '2');
    assert.equal(r.throttled, 2, 'the third timer and the frame wait for the debt');
  });
});

describe('engine: quickjs-ng mapped arguments (issue #1400)', () => {
  // quickjs-ng < 0.13 frees a mapped arguments object's var refs too early; a
  // non-strict widget function that reads `arguments` and allocates enough to
  // run the GC tripped GC assertions and killed the wasm runtime. The wasm is now
  // built from the TelegramMessenger/quickjs fork (packages/quickjs-wasm).
  it('a sloppy-mode wrapper reading arguments survives garbage collection', async () => {
    const r = await runWidget(`<script>
      function wrap(c) { return function (a, b) { var n = arguments.length; return n === 0 ? c.call(this) : n === 1 ? c.call(this, a) : c.call(this, a, b); }; }
      var keep = [];
      var f = wrap(function (x, y) { var t = []; for (var k = 0; k < 2000; k++) { var o = { k: k, x: x, y: y }; o.self = o; t.push(o); } keep.push(t.length); return [x, y]; });
      for (var i = 0; i < 300; i++) { f({ i: i }); f({ i: i }, { j: i }); }
      var done = keep.length;</script>`, { timerRounds: 0, probes: ['done'] });
    assert.equal(r.probes[0], '600');
  });
});

describe('harness: fail closed', () => {
  it('a sandbox bundle that throws while loading never runs the widget', async () => {
    let ran = false;
    await assert.rejects(runWidget('<script>TelegramWidget.ready()</script>', { bundle: 'globalThis.__twDispatch = function () { return null; }; throw new Error("half-loaded shim");', unknownHost: () => { ran = true; } }), /sandbox bundle failed to load/);
    assert.equal(ran, false);
  });
});

describe('glue: host globals', () => {
  it('widget code cannot reach the host functions or the shim dispatcher, and the shim still works', async () => {
    const r = await runWidget(`<p id="p">x</p><script>
      var names = ['__host', '__hostBinary', '__twDispatch'];
      var seen = names.map(function (n) { return n + '=' + typeof globalThis[n] + '/' + (n in globalThis) + '/' + (Object.getOwnPropertyNames(globalThis).indexOf(n) >= 0); }).join(',');
      var redefined = (function () { try { Object.defineProperty(globalThis, '__twDispatch', { value: function () { return 'pwned'; } }); return 'ok'; } catch (e) { return 'threw'; } })();
      setTimeout(function () { document.getElementById('p').textContent = 'tick'; }, 1);</script>`, { timerRounds: 2, probes: ['seen', 'redefined', 'document.getElementById("p").textContent'] });
    assert.equal(r.probes[0], '__host=undefined/false/false,__hostBinary=undefined/false/false,__twDispatch=undefined/false/false');
    assert.equal(r.probes[2], 'tick', 'the glue still dispatches timers through its private reference after a widget redefines the global');
  });
});

describe('canvas words', () => {
  const widget = `<canvas id="c" width="200" height="100"></canvas><script>
    var c = document.getElementById('c').getContext('2d'), f = 0;
    var g = c.createLinearGradient(0, 0, 100, 0); g.addColorStop(0, 'red'); g.addColorStop(1, 'blue');
    var p = new Path2D('M0 0 L10 10'); p.lineTo(20, 5);
    function draw() {
      c.clearRect(0, 0, 200, 100); c.save(); c.fillStyle = g; c.fillRect(f, 0, 50, 50); c.fillStyle = 'rgba(0,0,0,0.5)';
      c.font = '12px sans-serif'; c.fillText('héllo ' + f, 5, 80); c.setLineDash([2, 3]); c.lineWidth = NaN; c.lineWidth = 2;
      c.stroke(p); c.arc(50, 50, 10, 0, Math.PI * 2, false); c.drawImage(document.getElementById('c'), 0, 0);
      c.imageSmoothingEnabled = false; c.restore();
      if (++f < 4) requestAnimationFrame(draw);
    }
    draw();
    var img = c.createImageData(2, 2); img.data[0] = 255;
    setTimeout(function () { c.putImageData(img, 0, 0); c.fillRect(0, 0, 1, 1); }, 1);
  </script>`;

  it('word payloads stand for exactly the JSON commands, and unexpressible commands fall back to JSON', async () => {
    const json = await runWidget(widget, { timerRounds: 6 });
    const words = await runWidget(widget, { timerRounds: 6, canvasWords: true });
    const canvasOps = (r: any) => r.renderOps.filter((o: any) => o.op === 'canvas');
    assert.ok(words.canvasWordBatches >= 3, `${words.canvasWordBatches} word batches`);
    assert.equal(json.canvasWordBatches, 0);
    assert.deepEqual(canvasOps(words), canvasOps(json));
    assert.deepEqual(words.diags.filter((d) => d.kind.startsWith('validator')), json.diags.filter((d) => d.kind.startsWith('validator')));
  });

  it('random calls and property sets give byte-identical commands on the word path', async () => {
    const fuzz = `<canvas id="c"></canvas><script>
      var c = document.getElementById('c').getContext('2d'), seed = 12345;
      function rnd(n) { seed = (Math.imul(seed, 1103515245) + 12345) & 0x7fffffff; return seed % n; }
      var nums = [0, -0, 1, -1.5, 1e21, 1e-7, 0.1 + 0.2, NaN, Infinity, -Infinity, 2147483648, 123456789.125];
      var strs = ['', 'a', 'héllo', '\u{1F600}', 'quote"back\\slash', 'nul\u0000x', '12px serif', 'rgba(1,2,3,0.5)'];
      var calls = ['fillRect', 'strokeRect', 'moveTo', 'lineTo', 'arc', 'fillText', 'strokeText', 'translate', 'rotate', 'scale', 'save', 'restore', 'beginPath', 'closePath', 'setTransform', 'clearRect'];
      var props = ['lineWidth', 'globalAlpha', 'font', 'fillStyle', 'strokeStyle', 'textAlign', 'imageSmoothingEnabled', 'shadowBlur', 'lineDashOffset'];
      function arg() { var k = rnd(4); return k === 0 ? strs[rnd(strs.length)] : k === 1 ? undefined : nums[rnd(nums.length)]; }
      function frame() {
        for (var i = 0; i < 300; i++) {
          if (rnd(3) === 0) { var p = props[rnd(props.length)]; var k = rnd(3); c[p] = k === 0 ? nums[rnd(nums.length)] : k === 1 ? strs[rnd(strs.length)] : !!rnd(2); }
          else { var m = calls[rnd(calls.length)], n = rnd(7), a = []; for (var j = 0; j < n; j++) a.push(arg()); c[m].apply(c, a); }
        }
        if (rnd(10) === 0) c.setLineDash([1, 2]);
      }
      frame(); setTimeout(frame, 1); setTimeout(frame, 2);
    </script>`;
    const canvas = (r: any) => JSON.stringify(r.ops.flat().filter((o: any) => o.op === 'canvas').map((o: any) => o.cmds));
    const json = await runWidget(fuzz, { timerRounds: 3 });
    const words = await runWidget(fuzz, { timerRounds: 3, canvasWords: true });
    assert.ok(words.canvasWordBatches >= 3, `${words.canvasWordBatches} word batches`);
    assert.equal(canvas(words), canvas(json));
  });

  it('without the feature every canvas op stays JSON', async () => {
    const r = await runWidget(widget, { timerRounds: 6, canvasWords: false });
    assert.equal(r.canvasWordBatches, 0);
  });
});

describe('glue: coarse clock', () => {
  it('performance.now() ticks in 100 µs steps', async () => {
    const r = await runWidget(`<script>var seen = {}, distinct = 0, t0 = performance.now(); for (var i = 0; i < 100000; i++) { var t = performance.now(); if (!seen[t]) { seen[t] = 1; distinct++; } } var elapsed = performance.now() - t0;</script>`, { timerRounds: 0, probes: ['distinct', 'elapsed'] });
    const distinct = Number(r.probes[0]);
    const elapsed = Number(r.probes[1]);
    assert.ok(distinct <= elapsed / 0.1 + 2, `${distinct} distinct values in ${elapsed} ms`);
  });
});

describe('shim: pointer capture', () => {
  it('retargets queued drag events and releases capture after pointerup', async () => {
    const html = `<div id="drag"></div><div id="outside"></div><script>
      var drag = document.getElementById('drag'), seen = [];
      drag.addEventListener('pointerdown', e => { drag.setPointerCapture(e.pointerId); seen.push(drag.hasPointerCapture(e.pointerId)); });
      drag.addEventListener('pointermove', e => seen.push(e.target.id + ':move'));
      drag.addEventListener('pointerup', e => seen.push(e.target.id + ':up'));
    </script>`;
    const initial = await runWidget(html);
    const id = (name: string) => initial.renderOps.find(o => o.op === 'el' && o.attrs?.some(([key, value]: string[]) => key === 'id' && value === name))?.id;
    assert.ok(id('drag'));
    assert.ok(id('outside'));
    const r = await runWidget(html, {
      events: [
        { type: 'pointerdown', targetId: id('drag'), pointerId: 1 },
        { type: 'pointermove', targetId: id('outside'), pointerId: 1 },
        { type: 'pointerup', targetId: id('outside'), pointerId: 1 },
        { type: 'pointermove', targetId: id('outside'), pointerId: 1 },
      ],
      probes: ['JSON.stringify(seen)', 'drag.hasPointerCapture(1)'],
    });
    assert.deepEqual(errorsOf(r), []);
    assert.deepEqual(r.probes, ['[true,"drag:move","drag:up"]', 'false']);
  });
  it('uses PointerEvent for native capture notifications', async () => {
    const html = `<div id="drag"></div><script>var seen=[];document.getElementById('drag').addEventListener('gotpointercapture',e=>seen.push(e instanceof PointerEvent,e.pointerId,e.cancelable));</script>`;
    const initial = await runWidget(html);
    const id = initial.renderOps.find(o => o.op === 'el' && o.attrs?.some(([key, value]: string[]) => key === 'id' && value === 'drag'))?.id;
    const r = await runWidget(html, { events: [{ type: 'gotpointercapture', targetId: id, pointerId: 7 }], probes: ['JSON.stringify(seen)'] });
    assert.deepEqual(r.probes, ['[true,7,false]']);
  });

});

describe('glue: stack traces', () => {
  it('CallSite.getFunction does not hand the shim\'s closures to widget code', async () => {
    const r = await runWidget(`<button id="b">x</button><script>
      var found = [], sites = 0;
      Error.prepareStackTrace = function (e, frames) { frames.forEach(function (f) { sites++; var fn = f.getFunction(); if (fn !== undefined) found.push(String(f.getFunctionName())); }); return 'trace'; };
      function look() { return new Error('probe').stack; }
      document.getElementById('b').addEventListener('click', look);
      setTimeout(look, 1);
      requestAnimationFrame(look);
      var direct = look();</script>`, { timerRounds: 2, events: [{ type: 'click', targetId: 5 }], probes: ['found.join(",")', 'sites > 6', 'direct', 'typeof Object.getOwnPropertyDescriptor(Object.getPrototypeOf((function () { var s; Error.prepareStackTrace = function (e, f) { s = f[0]; return ""; }; new Error().stack; return s; })()), "getFunction").value'] });
    assert.deepEqual(r.probes, ['', 'true', 'trace', 'function']);
  });
});

describe('harness: clocks', () => {
  it('frame times tick in 100 µs steps, like the worker', async () => {
    const r = await runWidget('<script>var ts = []; function f(t) { ts.push(t); if (ts.length < 4) requestAnimationFrame(f); } requestAnimationFrame(f);</script>', { frames: 6, probes: ['JSON.stringify(ts)'] });
    const ts = JSON.parse(r.probes[0]) as number[];
    assert.ok(ts.length >= 3, r.probes[0]);
    for (const t of ts) assert.ok(Math.abs(t * 10 - Math.round(t * 10)) < 1e-6, String(t));
  });

  it('a hidden widget gets no frames', async () => {
    const r = await runWidget('<script>var n = 0; requestAnimationFrame(function () { n++; });</script>', { frames: 4, visible: false, probes: ['n', 'document.hidden'] });
    assert.deepEqual(r.probes, ['0', 'true']);
  });
});

describe('shim: SVG names', () => {
  it('tagName and nodeName of SVG elements keep their case, as in a browser; HTML stays uppercase', async () => {
    const r = await runWidget('<svg><defs><linearGradient id="g"></linearGradient></defs><rect/></svg>', {
      probes: ['document.querySelector("linearGradient").tagName', 'document.querySelector("rect").nodeName', 'document.createElementNS("http://www.w3.org/2000/svg", "g").tagName', 'document.body.tagName'],
    });
    assert.deepEqual(r.probes, ['linearGradient', 'rect', 'g', 'BODY']);
  });
});

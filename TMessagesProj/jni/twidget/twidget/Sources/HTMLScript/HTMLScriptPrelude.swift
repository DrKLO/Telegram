let htmlScriptPrelude = #"""
(function (native, MAX_TIMERS, MAX_HTML, TRANSITIONS) {
  'use strict';
  const timers = new Map();
  let nextId = 0;
  const text = v => {
    if (typeof v === 'string') return v;
    if (v !== null && typeof v === 'object' && !(v instanceof Error)) { try { return JSON.stringify(v); } catch (e) {} }
    return String(v);
  };
  const logger = level => (...args) => native.log(level, args.map(text).join(' ').slice(0, 1000));
  const add = (fn, ms, args, repeat) => {
    if (typeof fn !== 'function') throw new TypeError('setTimeout/setInterval need a function');
    if (timers.size >= MAX_TIMERS) throw new RangeError('too many timers (max ' + MAX_TIMERS + ')');
    const id = ++nextId;
    timers.set(id, { fn, args, repeat });
    native.schedule(id, Math.min(Number(ms) || 0, 86400000), repeat);
    return id;
  };
  const clear = id => { if (timers.delete(id)) native.cancel(id); };

  const frames = new Map();
  let nextFrame = 0;
  const origin = Date.now();
  const now = () => Date.now() - origin;
  const requestFrame = fn => {
    if (typeof fn !== 'function') throw new TypeError('requestAnimationFrame needs a function');
    const id = ++nextFrame;
    frames.set(id, fn);
    native.requestFrame();
    return id;
  };
  const ease = Object.freeze({
    linear: t => t,
    inQuad: t => t * t,
    outQuad: t => t * (2 - t),
    inOutQuad: t => t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2,
    outCubic: t => 1 - Math.pow(1 - t, 3),
    inOutCubic: t => t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2,
    outQuint: t => 1 - Math.pow(1 - t, 5),
    outExpo: t => t === 1 ? 1 : 1 - Math.pow(2, -10 * t),
    inOutSine: t => -(Math.cos(Math.PI * t) - 1) / 2,
    outBack: t => 1 + 2.70158 * Math.pow(t - 1, 3) + 1.70158 * Math.pow(t - 1, 2),
    inOutBack: t => {
      const c = 1.70158 * 1.525;
      return t < 0.5 ? (Math.pow(2 * t, 2) * ((c + 1) * 2 * t - c)) / 2 : (Math.pow(2 * t - 2, 2) * ((c + 1) * (t * 2 - 2) + c) + 2) / 2;
    },
    spring: t => t === 0 || t === 1 ? t : 1 - Math.exp(-7 * t) * Math.cos(9.5 * t),
    outElastic: t => t === 0 || t === 1 ? t : Math.pow(2, -10 * t) * Math.sin((t * 10 - 0.75) * (2 * Math.PI) / 3) + 1,
    outBounce: t => {
      const n = 7.5625, d = 2.75;
      if (t < 1 / d) return n * t * t;
      if (t < 2 / d) return n * (t -= 1.5 / d) * t + 0.75;
      if (t < 2.5 / d) return n * (t -= 2.25 / d) * t + 0.9375;
      return n * (t -= 2.625 / d) * t + 0.984375;
    },
  });
  const animate = (duration, onFrame, easing = ease.inOutCubic) => new Promise(resolve => {
    let start = -1;
    const step = () => {
      const time = now();
      if (start < 0) start = time;
      const t = Math.min(1, (time - start) / Math.max(1, duration));
      onFrame(easing(t), t);
      if (t < 1) requestFrame(step); else resolve();
    };
    onFrame(easing(0), 0);
    requestFrame(step);
  });

  const tweens = new WeakMap();
  const tween = (target, props, duration = 300, easing = ease.outCubic) => {
    if (target === null || typeof target !== 'object') throw new TypeError('tween needs an object to animate');
    let owners = tweens.get(target);
    if (!owners) { owners = new Map(); tweens.set(target, owners); }
    const token = {}, from = {}, to = {};
    for (const key of Object.keys(props)) {
      from[key] = Number(target[key]) || 0;
      to[key] = Number(props[key]);
      owners.set(key, token);
    }
    return animate(duration, p => {
      for (const key in to) if (owners.get(key) === token) target[key] = from[key] + (to[key] - from[key]) * p;
    }, easing).then(() => {
      for (const key in to) if (owners.get(key) === token) { target[key] = to[key]; owners.delete(key); }
    });
  };

  const clamp = (x, lo, hi) => Math.min(hi, Math.max(lo, x));
  const rgba = c => {
    if (Array.isArray(c)) return [c[0], c[1], c[2], c.length > 3 ? c[3] : 1];
    const s = String(c).trim();
    let m = /^#([0-9a-f]{3,8})$/i.exec(s);
    if (m) {
      let h = m[1];
      if (h.length <= 4) h = h.split('').map(x => x + x).join('');
      const n = parseInt(h.slice(0, 6), 16);
      return [(n >> 16) & 255, (n >> 8) & 255, n & 255, h.length === 8 ? parseInt(h.slice(6), 16) / 255 : 1];
    }
    m = /^rgba?\(([^)]*)\)$/i.exec(s);
    if (m) {
      const p = m[1].split(',').map(Number);
      return [p[0] || 0, p[1] || 0, p[2] || 0, p.length > 3 ? p[3] : 1];
    }
    return [0, 0, 0, 1];
  };
  const mix = (a, b, t) => {
    const x = rgba(a), y = rgba(b);
    const v = x.map((q, i) => q + (y[i] - q) * t);
    return `rgba(${Math.round(clamp(v[0], 0, 255))},${Math.round(clamp(v[1], 0, 255))},${Math.round(clamp(v[2], 0, 255))},${clamp(v[3], 0, 1).toFixed(3)})`;
  };
  const alpha = (c, a) => { const v = rgba(c); return `rgba(${v[0]},${v[1]},${v[2]},${clamp(a * v[3], 0, 1).toFixed(3)})`; };
  const esc = v => String(v === undefined || v === null ? '' : v)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  const transition = (kind = 'fade') => {
    if (!TRANSITIONS.includes(kind)) throw new RangeError('transition() takes one of: ' + TRANSITIONS.join(', '));
    native.transition(kind);
  };

  Object.assign(globalThis, {
    setTimeout: (fn, ms, ...args) => add(fn, ms, args, false),
    setInterval: (fn, ms, ...args) => add(fn, ms, args, true),
    clearTimeout: clear,
    clearInterval: clear,
    requestAnimationFrame: requestFrame,
    cancelAnimationFrame: id => { frames.delete(id); },
    performance: Object.freeze({ now }),
    animate,
    tween,
    ease,
    sleep: ms => new Promise(resolve => add(resolve, ms, [], false)),
    lerp: (a, b, t) => a + (b - a) * t,
    clamp,
    mix,
    alpha,
    esc,
    transition,
    copyText: text => native.copy(String(text).slice(0, 10000)),
    update: () => native.update(),
    console: Object.freeze({ log: logger('log'), info: logger('log'), debug: logger('log'), warn: logger('warn'), error: logger('error') }),
  });
  delete globalThis.WebAssembly;
  return {
    fire(id) {
      const t = timers.get(id);
      if (!t) return;
      if (!t.repeat) timers.delete(id);
      t.fn(...t.args);
    },
    frame() {
      const due = [...frames.values()];
      frames.clear();
      const t = now();
      let error = null;
      for (const fn of due) { try { fn(t); } catch (e) { error = error || e; } }
      if (error) throw error;
      return frames.size > 0;
    },
    callRender() {
      if (typeof render !== 'function') return null;
      const html = render();
      if (typeof html !== 'string') throw new TypeError('render() must return a string, not ' + (html === null ? 'null' : typeof html));
      if (html.length > MAX_HTML) throw new RangeError('render() returned ' + html.length + ' characters (max ' + MAX_HTML + ')');
      return html;
    },
    callAction(name, value) {
      if (typeof onAction === 'function') onAction(name, value);
    },
    callInput(type, a, b) {
      if (typeof globalThis.__htmlInput === 'function') globalThis.__htmlInput(type, a, b);
      if (type === 'key' && typeof onKey === 'function') onKey(a);
      else if (type === 'pointer' && typeof onPointer === 'function') onPointer(a, b);
      else if (type === 'focus' && typeof onFocus === 'function') onFocus(!!a);
    },
    describe(e) {
      try {
        let s = e instanceof Error ? e.name + ': ' + e.message : 'Uncaught ' + text(e);
        if (e && typeof e.line === 'number') s += ' (line ' + e.line + ')';
        return String(s).slice(0, 300);
      } catch (_) { return 'Uncaught exception'; }
    },
  };
})
"""#

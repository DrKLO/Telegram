import { mirroredId, nodeById } from './capture';
import { Event, EventTarget } from './events';
import { clearHostTimer, hostTimer, reportError, timeOrigin } from './globals';
import { diag, host, invalidateLayout, query, unsupported } from './host';

export function createWebAnimations(api: any): any {
  const { host, diag, unsupported, reportError, Event, EventTarget, nodeId, nodeById, setTimer, clearTimer, timeOrigin, query, invalidateLayout } = api;
  const NativePromise = Promise;
  const resolved = NativePromise.resolve();
  const then = NativePromise.prototype.then;
  const microtask = (fn: any) => { then.call(resolved, fn); };
  /** Live script animations sent to the renderer (the validator's cap: 64 strict, 1024 compat); further ones are not rendered. */
  const MAX_ANIMATIONS = Number(api.maxAnimations) > 0 ? Number(api.maxAnimations) : 64;
  const MAX_CSS_CACHE = 1024;
  const MAX_KEYFRAMES = 64;
  const MAX_PROPERTIES = 64;
  const MAX_VALUE = 4096;
  const MAX_TIME = 86400000;
  const MAX_EVENTS_PER_FRAME = 4096;

  let seq = 0;
  let frameTime: any = null;
  let frameRequested = false;
  let frameWanted = false;
  let finishTimer = 0;
  let finishAt = Infinity;
  let limitReported = false;
  /** The renderer's CSS animation list does not depend on script animations: that query leaves the batch for the end of the task. */
  let listingCss = false;
  const all = new Set<any>();
  const pendingSet = new Set<any>();
  const dirty = new Set<any>();
  const sent = new Map<any, any>();
  /** The script animations among `sent`: only they count against MAX_ANIMATIONS (a CSS animation or transition the script controls is the stylesheet's, as in the validator). */
  const sentScript = new Set<any>();
  const pendingEvents: any[] = [];
  const byTarget = new WeakMap<any, any>();

  const domException = (name: any, message: any) => {
    const e = new Error(name + ': ' + message);
    e.name = name;
    return e;
  };

  function hostNow() {
    return Number(host('now'));
  }

  function documentTime() {
    if (frameTime !== null) return frameTime;
    return hostNow() - timeOrigin();
  }

  function requestFrame() {
    if (frameTime !== null) {
      frameWanted = true;
      return;
    }
    if (frameRequested) return;
    frameRequested = true;
    host('raf');
    armFallback();
  }

  let fallbackTimer = 0;
  function armFallback() {
    if (fallbackTimer) return;
    fallbackTimer = setTimer(() => {
      fallbackTimer = 0;
      if (!frameRequested || frameTime !== null) return;
      const run = frame(documentTime());
      if (run) run(null);
    }, 100);
  }

  function camelToCss(name: any) {
    if (name.startsWith('--')) return name;
    if (name === 'cssFloat') return 'float';
    if (name === 'cssOffset') return 'offset';
    let out = name.replace(/[A-Z]/g, (c: any) => '-' + c.toLowerCase());
    if (/^(webkit|moz|ms)-/.test(out)) out = '-' + out;
    return out;
  }

  function cssToCamel(name: any) {
    if (name.startsWith('--')) return name;
    if (name === 'float') return 'cssFloat';
    if (name === 'offset') return 'cssOffset';
    return name.replace(/^-(webkit|moz|ms)-/, '$1-').replace(/-([a-z])/g, (_: any, c: any) => c.toUpperCase());
  }

  function animatableProperty(name: any) {
    if (typeof name !== 'string' || name.length > 128) return null;
    if (name.startsWith('--')) return /^--[\w-]+$/.test(name) ? name : null;
    if (!/^[a-zA-Z]+$/.test(name)) return null;
    if (name === 'offset' || name === 'easing' || name === 'composite') return null;
    const css = camelToCss(name);
    if (/^(-webkit-)?(animation|transition)(-|$)/.test(css) || css === 'display' && false) return null;
    if (css === 'will-change' || css === 'direction' || css === 'unicode-bidi') return null;
    return css;
  }

  function bezier(x1: any, y1: any, x2: any, y2: any) {
    const cx = 3 * x1, bx = 3 * (x2 - x1) - cx, ax = 1 - cx - bx;
    const cy = 3 * y1, by = 3 * (y2 - y1) - cy, ay = 1 - cy - by;
    const sx = (t: any) => ((ax * t + bx) * t + cx) * t;
    const sy = (t: any) => ((ay * t + by) * t + cy) * t;
    const dx = (t: any) => (3 * ax * t + 2 * bx) * t + cx;
    return (x: any) => {
      if (x <= 0) {
        const slope = x1 > 0 ? y1 / x1 : (y1 === 0 && x2 > 0 ? y2 / x2 : 0);
        return slope * x;
      }
      if (x >= 1) {
        const slope = x2 < 1 ? (y2 - 1) / (x2 - 1) : (y2 === 1 && x1 < 1 ? (y1 - 1) / (x1 - 1) : 0);
        return 1 + slope * (x - 1);
      }
      let t = x;
      for (let i = 0; i < 8; i++) {
        const err = sx(t) - x;
        if (Math.abs(err) < 1e-7) return sy(t);
        const d = dx(t);
        if (Math.abs(d) < 1e-7) break;
        t -= err / d;
      }
      let lo = 0, hi = 1;
      t = x;
      for (let i = 0; i < 40; i++) {
        const v = sx(t);
        if (Math.abs(v - x) < 1e-7) break;
        if (v < x) lo = t; else hi = t;
        t = (lo + hi) / 2;
      }
      return sy(t);
    };
  }

  const NAMED: any = {
    linear: [null, 'linear'],
    ease: [[0.25, 0.1, 0.25, 1], 'ease'],
    'ease-in': [[0.42, 0, 1, 1], 'ease-in'],
    'ease-out': [[0, 0, 0.58, 1], 'ease-out'],
    'ease-in-out': [[0.42, 0, 0.58, 1], 'ease-in-out'],
  };
  const easingCache = new Map<any, any>();

  function number(text: any) {
    const t = text.trim();
    if (!/^[+-]?(\d+\.?\d*|\.\d+)(e[+-]?\d+)?$/i.test(t)) return NaN;
    return Number(t);
  }

  function parseEasing(value: any) {
    const text = String(value).trim().toLowerCase();
    const hit = easingCache.get(text);
    if (hit) return hit;
    let result = null;
    if (Object.prototype.hasOwnProperty.call(NAMED, text)) {
      const [points, name] = NAMED[text];
      result = { text: name, fn: points ? bezier(points[0], points[1], points[2], points[3]) : (x: any) => x };
    } else if (text === 'step-start' || text === 'step-end') {
      result = steps(1, text === 'step-start' ? 'start' : 'end', text === 'step-start' ? 'steps(1, start)' : 'steps(1)');
    } else {
      let m = /^cubic-bezier\(([^()]*)\)$/.exec(text);
      if (m) {
        const v = m[1].split(',').map(number);
        if (v.length === 4 && v.every(Number.isFinite) && v[0] >= 0 && v[0] <= 1 && v[2] >= 0 && v[2] <= 1) {
          result = { text: `cubic-bezier(${v.join(', ')})`, fn: bezier(v[0], v[1], v[2], v[3]) };
        }
      } else if ((m = /^steps\(([^()]*)\)$/.exec(text))) {
        const parts = m[1].split(',').map((s: any) => s.trim());
        const n = number(parts[0]);
        const pos = parts.length > 1 ? parts[1] : 'end';
        const valid = parts.length <= 2 && Number.isInteger(n) && n > 0 && n <= 100000 && ['start', 'end', 'jump-start', 'jump-end', 'jump-both', 'jump-none'].includes(pos) && !(pos === 'jump-none' && n < 2);
        if (valid) {
          const position = pos === 'jump-start' ? 'start' : pos === 'jump-end' ? 'end' : pos;
          result = steps(n, position, position === 'end' ? `steps(${n})` : `steps(${n}, ${pos === 'jump-start' ? 'jump-start' : pos})`);
        }
      } else if ((m = /^linear\(([^()]*)\)$/.exec(text))) {
        result = linearEasing(m[1]);
      }
    }
    if (!result) throw new TypeError(`'${String(value).slice(0, 100)}' is not a valid value for easing`);
    if (easingCache.size > 256) easingCache.clear();
    easingCache.set(text, result);
    return result;
  }

  function steps(n: any, position: any, text: any) {
    return {
      text,
      fn: (x: any, before: any) => {
        let step = Math.floor(x * n);
        if (position === 'start' || position === 'jump-both') step += 1;
        if (before && (x * n) % 1 === 0) step -= 1;
        if (x >= 0 && step < 0) step = 0;
        const jumps = position === 'jump-both' ? n + 1 : position === 'jump-none' ? n - 1 : n;
        if (x <= 1 && step > jumps) step = jumps;
        return step / jumps;
      },
    };
  }

  function linearEasing(body: any) {
    const items = body.split(',');
    if (items.length < 2 || items.length > 256) return null;
    const points: any[] = [];
    for (const item of items) {
      const parts = item.trim().split(/\s+/);
      const output = number(parts[0]);
      if (!Number.isFinite(output) || parts.length > 3) return null;
      const inputs = parts.slice(1).map((p: any) => (p.endsWith('%') ? number(p.slice(0, -1)) / 100 : NaN));
      if (inputs.some((v: any) => !Number.isFinite(v))) return null;
      if (!inputs.length) points.push({ output, input: null });
      for (const input of inputs) points.push({ output, input });
    }
    if (points[0].input === null) points[0].input = 0;
    const last = points[points.length - 1];
    if (last.input === null) last.input = Math.max(1, ...points.filter((p: any) => p.input !== null).map((p: any) => p.input));
    let largest = points[0].input;
    for (const p of points) if (p.input !== null) { largest = Math.max(largest, p.input); p.input = largest; }
    for (let i = 1; i < points.length; i++) {
      if (points[i].input !== null) continue;
      let j = i;
      while (points[j].input === null) j++;
      const a = points[i - 1].input, b = points[j].input;
      for (let k = i; k < j; k++) points[k].input = a + ((b - a) * (k - i + 1)) / (j - i + 1);
      i = j;
    }
    const text = 'linear(' + points.map((p: any) => `${p.output} ${+(p.input * 100).toFixed(6)}%`).join(', ') + ')';
    const segment = (a: any, b: any, x: any) => (b.input === a.input ? b.output : a.output + ((b.output - a.output) * (x - a.input)) / (b.input - a.input));
    return {
      text,
      fn: (x: any) => {
        if (x <= points[0].input) return segment(points[0], points[1], x);
        for (let k = 1; k < points.length; k++) if (x <= points[k].input) return segment(points[k - 1], points[k], x);
        return segment(points[points.length - 2], points[points.length - 1], x);
      },
    };
  }

  const LINEAR = parseEasing('linear');

  function timeValue(v: any, name: any, allowAuto: any) {
    if (allowAuto && v === 'auto') return 'auto';
    if (typeof v === 'string' && !allowAuto) throw new TypeError(`${name} must be a number`);
    const n = Number(v);
    if (typeof v !== 'number' && typeof v !== 'bigint' && !(v instanceof Number)) {
      if (typeof v === 'string') throw new TypeError(`'${v}' is not a valid ${name}`);
    }
    if (!Number.isFinite(n)) throw new TypeError(`${name} must be finite`);
    return n;
  }

  class EffectTiming {
    [key: string]: any;
    constructor() {
      this.delay = 0;
      this.endDelay = 0;
      this.fill = 'auto';
      this.iterationStart = 0;
      this.iterations = 1;
      this.duration = 'auto';
      this.direction = 'normal';
      this.easing = LINEAR;
    }
  }

  function applyTiming(timing: any, input: any, partial: any) {
    if (input === undefined || input === null) return timing;
    if (typeof input === 'number' || input instanceof Number) {
      const d = Number(input);
      if (Number.isNaN(d) || d < 0) throw new TypeError('duration must be a non-negative number');
      timing.duration = d;
      return timing;
    }
    if (typeof input !== 'object') {
      if (typeof input === 'string') throw new TypeError('duration must be a number or "auto"');
      return timing;
    }
    const next = Object.assign(new EffectTiming(), timing);
    if (input.delay !== undefined) next.delay = timeValue(input.delay, 'delay', false);
    if (input.endDelay !== undefined) next.endDelay = timeValue(input.endDelay, 'endDelay', false);
    if (input.fill !== undefined) {
      const fill = String(input.fill);
      if (!['none', 'forwards', 'backwards', 'both', 'auto'].includes(fill)) throw new TypeError(`'${fill}' is not a valid fill mode`);
      next.fill = fill;
    }
    if (input.iterationStart !== undefined) {
      const v = Number(input.iterationStart);
      if (!Number.isFinite(v) || v < 0) throw new TypeError('iterationStart must be a non-negative finite number');
      next.iterationStart = v;
    }
    if (input.iterations !== undefined) {
      const v = Number(input.iterations);
      if (Number.isNaN(v) || v < 0) throw new TypeError('iterations must be a non-negative number');
      next.iterations = v;
    }
    if (input.duration !== undefined) {
      const v = input.duration;
      if (v === 'auto') next.duration = 'auto';
      else {
        if (typeof v === 'string') throw new TypeError(`'${v}' is not a valid duration`);
        const n = Number(v);
        if (Number.isNaN(n) || n < 0) throw new TypeError('duration must be a non-negative number');
        next.duration = n;
      }
    }
    if (input.direction !== undefined) {
      const d = String(input.direction);
      if (!['normal', 'reverse', 'alternate', 'alternate-reverse'].includes(d)) throw new TypeError(`'${d}' is not a valid direction`);
      next.direction = d;
    }
    if (input.easing !== undefined) next.easing = parseEasing(input.easing);
    void partial;
    return next;
  }

  function computeTiming(t: any, localTime: any, forwards: any) {
    const duration = t.duration === 'auto' ? 0 : t.duration;
    const iterations = t.iterations;
    const active = duration === 0 || iterations === 0 ? 0 : duration * iterations;
    const end = Math.max(t.delay + active + t.endDelay, 0);
    const out: any = {
      delay: t.delay, endDelay: t.endDelay, fill: t.fill === 'auto' ? 'none' : t.fill, iterationStart: t.iterationStart, iterations,
      duration, direction: t.direction, easing: t.easing.text, endTime: end, activeDuration: active,
      localTime: localTime, progress: null, currentIteration: null, phase: null, activeTime: null,
    };
    if (localTime === null || localTime === undefined) return out;
    const fill = out.fill;
    const beforeActive = Math.max(Math.min(t.delay, end), 0);
    const activeAfter = Math.max(Math.min(t.delay + active, end), 0);
    let phase;
    if (localTime < beforeActive || (!forwards && localTime === beforeActive)) phase = 'before';
    else if (localTime > activeAfter || (forwards && localTime === activeAfter)) phase = 'after';
    else phase = 'active';
    out.phase = phase;
    let activeTime = null;
    if (phase === 'before') activeTime = fill === 'backwards' || fill === 'both' ? Math.max(localTime - t.delay, 0) : null;
    else if (phase === 'active') activeTime = localTime - t.delay;
    else activeTime = fill === 'forwards' || fill === 'both' ? Math.max(Math.min(localTime - t.delay, active), 0) : null;
    out.activeTime = activeTime;
    if (activeTime === null) return out;
    let overall = duration === 0 ? (phase === 'before' ? 0 : iterations) : activeTime / duration;
    overall += t.iterationStart;
    let simple = overall === Infinity ? t.iterationStart % 1 : overall % 1;
    if (simple === 0 && phase !== 'before' && activeTime === active && iterations !== 0) simple = 1;
    let iteration;
    if (phase === 'after' && iterations === Infinity) iteration = Infinity;
    else if (simple === 1) iteration = Math.floor(overall) - 1;
    else iteration = Math.floor(overall);
    let goingForwards = true;
    if (t.direction === 'reverse') goingForwards = false;
    else if (t.direction === 'alternate' || t.direction === 'alternate-reverse') {
      if (iteration !== Infinity) {
        let d = iteration;
        if (t.direction === 'alternate-reverse') d += 1;
        goingForwards = d % 2 === 0;
      }
    }
    const directed = goingForwards ? simple : 1 - simple;
    const before = (goingForwards && phase === 'before') || (!goingForwards && phase === 'after');
    out.progress = t.easing.fn(directed, before);
    out.currentIteration = iteration;
    return out;
  }

  function stringValue(v: any) {
    const s = String(v);
    if (s.length > MAX_VALUE) throw new TypeError('keyframe value is too long');
    return s;
  }

  function compositeValue(v: any) {
    if (v === null || v === undefined) return 'auto';
    const s = String(v);
    if (!['replace', 'add', 'accumulate', 'auto'].includes(s)) throw new TypeError(`'${s}' is not a valid composite operation`);
    return s;
  }

  function processKeyframes(input: any) {
    if (input === null || input === undefined) return [];
    if (typeof input !== 'object') throw new TypeError('keyframes must be an object or an array');
    const iteratorFn = input[Symbol.iterator];
    let frames: any[] = [];
    if (typeof iteratorFn === 'function') {
      let count = 0;
      for (const item of input) {
        if (++count > 10000) throw new RangeError('too many keyframes');
        if (item === null || item === undefined) { frames.push({ offset: null, easing: LINEAR, composite: 'auto', values: {} }); continue; }
        if (typeof item !== 'object') throw new TypeError('keyframes must be objects');
        const frame: any = { offset: null, easing: LINEAR, composite: 'auto', values: {} };
        if (item.offset !== undefined && item.offset !== null) {
          const o = Number(item.offset);
          if (!Number.isFinite(o)) throw new TypeError('keyframe offset must be a finite number');
          if (o < 0 || o > 1) throw new TypeError('keyframe offsets must be between 0 and 1');
          frame.offset = o;
        }
        if (item.easing !== undefined) frame.easing = parseEasing(item.easing);
        if (item.composite !== undefined) frame.composite = compositeValue(item.composite);
        let props = 0;
        for (const key of Object.keys(item).sort()) {
          const css = animatableProperty(key);
          if (!css) continue;
          const v = item[key];
          if (Array.isArray(v)) throw new TypeError('keyframe values in a keyframe list must not be arrays');
          if (++props > 512) break;
          frame.values[key] = stringValue(v);
        }
        frames.push(frame);
      }
      let last = -Infinity;
      for (const f of frames) {
        if (f.offset === null) continue;
        if (f.offset < last) throw new TypeError('keyframe offsets must be loosely sorted');
        last = f.offset;
      }
    } else {
      const offsets = input.offset === undefined ? [] : Array.isArray(input.offset) ? input.offset : [input.offset];
      const easings = input.easing === undefined ? [] : Array.isArray(input.easing) ? input.easing : [input.easing];
      const composites = input.composite === undefined ? [] : Array.isArray(input.composite) ? input.composite : [input.composite];
      const byOffset = new Map<any, any>();
      for (const key of Object.keys(input).sort()) {
        const css = animatableProperty(key);
        if (!css) continue;
        let values = input[key];
        values = Array.isArray(values) ? values : [values];
        if (values.length > 10000) throw new RangeError('too many keyframe values');
        const n = values.length;
        values.forEach((v: any, i: any) => {
          const offset = n === 1 ? 1 : i / (n - 1);
          let frame = byOffset.get(offset);
          if (!frame) byOffset.set(offset, (frame = { computed: offset, offset: null, easing: LINEAR, composite: 'auto', values: {} }));
          frame.values[key] = stringValue(v);
        });
      }
      frames = [...byOffset.values()].sort((a: any, b: any) => a.computed - b.computed);
      offsets.forEach((o: any, i: any) => {
        if (i >= frames.length || o === null || o === undefined) return;
        const v = Number(o);
        if (!Number.isFinite(v) || v < 0 || v > 1) throw new TypeError('keyframe offsets must be finite numbers between 0 and 1');
        frames[i].offset = v;
      });
      const parsedEasings = easings.map(parseEasing);
      if (parsedEasings.length) frames.forEach((f: any, i: any) => { f.easing = parsedEasings[i % parsedEasings.length]; });
      const parsedComposites = composites.map(compositeValue);
      if (parsedComposites.length) frames.forEach((f: any, i: any) => { f.composite = parsedComposites[i % parsedComposites.length]; });
      let last = -Infinity;
      for (const f of frames) {
        if (f.offset === null) continue;
        if (f.offset < last) throw new TypeError('keyframe offsets must be loosely sorted');
        last = f.offset;
      }
      for (const f of frames) delete f.computed;
    }
    computeOffsets(frames);
    return frames;
  }

  function computeOffsets(frames: any) {
    for (const f of frames) f.computedOffset = f.offset;
    if (!frames.length) return;
    if (frames.length > 1 && frames[0].computedOffset === null) frames[0].computedOffset = 0;
    if (frames[frames.length - 1].computedOffset === null) frames[frames.length - 1].computedOffset = 1;
    let i = 0;
    while (i < frames.length) {
      if (frames[i].computedOffset !== null) { i++; continue; }
      const start = i - 1;
      let end = i;
      while (frames[end].computedOffset === null) end++;
      const a = frames[start].computedOffset, b = frames[end].computedOffset;
      for (let k = start + 1; k < end; k++) frames[k].computedOffset = a + ((b - a) * (k - start)) / (end - start);
      i = end;
    }
  }

  const COLORS: any = { transparent: [0, 0, 0, 0], white: [255, 255, 255, 1], black: [0, 0, 0, 1], red: [255, 0, 0, 1], green: [0, 128, 0, 1], blue: [0, 0, 255, 1], gray: [128, 128, 128, 1], grey: [128, 128, 128, 1], orange: [255, 165, 0, 1], yellow: [255, 255, 0, 1] };

  function normalizeColors(text: any) {
    return text
      .replace(/#([0-9a-f]{3,8})\b/gi, (m: any, hex: any) => {
        let h = hex;
        if (h.length === 3 || h.length === 4) h = [...h].map((c: any) => c + c).join('');
        if (h.length !== 6 && h.length !== 8) return m;
        const v = [0, 2, 4].map((i: any) => parseInt(h.slice(i, i + 2), 16));
        const a = h.length === 8 ? parseInt(h.slice(6, 8), 16) / 255 : 1;
        return `rgba(${v[0]}, ${v[1]}, ${v[2]}, ${+a.toFixed(4)})`;
      })
      .replace(/\brgba?\(([^()]*)\)/gi, (m: any, inner: any) => {
        const parts = inner.split(/[\s,/]+/).filter(Boolean).map((p: any) => (p.endsWith('%') ? Number(p.slice(0, -1)) / 100 : Number(p)));
        if (parts.length < 3 || parts.some((p: any) => !Number.isFinite(p))) return m;
        return `rgba(${parts[0]}, ${parts[1]}, ${parts[2]}, ${parts.length > 3 ? parts[3] : 1})`;
      })
      .replace(/(^|[\s,(])([a-z]+)(?=$|[\s,)])/gi, (m: any, lead: any, word: any) => {
        const c = COLORS[word.toLowerCase()];
        return c ? `${lead}rgba(${c[0]}, ${c[1]}, ${c[2]}, ${c[3]})` : m;
      });
  }

  const NUMBER = /[+-]?(?:\d+\.?\d*|\.\d+)(?:e[+-]?\d+)?/gi;

  function tokens(text: any) {
    const out: any[] = [];
    let last = 0;
    NUMBER.lastIndex = 0;
    let m;
    while ((m = NUMBER.exec(text))) {
      const prev = text[m.index - 1];
      if (prev && /[a-z#_-]/i.test(prev) && !(prev === '-' && false)) continue;
      const unitMatch = /^[a-z%]*/i.exec(text.slice(m.index + m[0].length));
      const unit = unitMatch ? unitMatch[0] : '';
      out.push({ text: text.slice(last, m.index) });
      out.push({ value: Number(m[0]), unit });
      last = m.index + m[0].length + unit.length;
      NUMBER.lastIndex = last;
    }
    out.push({ text: text.slice(last) });
    return out;
  }

  function format(v: any) {
    if (Number.isInteger(v)) return String(v);
    return String(+v.toFixed(6));
  }

  function mix(a: any, b: any, t: any) {
    if (t === 0) return a;
    if (t === 1) return b;
    let from = normalizeColors(a), to = normalizeColors(b);
    if (from.trim() === 'none' && to.includes('(')) from = to.replace(NUMBER, (n: any, i: any, s: any) => (/scale/i.test(s.slice(0, i).split(/[\s)]/).pop()) ? '1' : '0'));
    if (to.trim() === 'none' && from.includes('(')) to = from.replace(NUMBER, (n: any, i: any, s: any) => (/scale/i.test(s.slice(0, i).split(/[\s)]/).pop()) ? '1' : '0'));
    const x = tokens(from), y = tokens(to);
    if (x.length !== y.length) return t < 0.5 ? a : b;
    let out = '';
    for (let i = 0; i < x.length; i++) {
      const p = x[i], q = y[i];
      if (p.text !== undefined && q.text !== undefined && p.text.trim().toLowerCase() === q.text.trim().toLowerCase()) out += p.text;
      else if (p.value !== undefined && q.value !== undefined && (p.unit === q.unit || p.value === 0 || q.value === 0)) out += format(p.value + (q.value - p.value) * t) + (p.unit || q.unit);
      else return t < 0.5 ? a : b;
    }
    return out;
  }

  function combine(a: any, b: any, property: any) {
    if (property === 'transform') {
      const u = a.trim(), v = b.trim();
      if (!u || u === 'none') return v;
      if (!v || v === 'none') return u;
      return u + ' ' + v;
    }
    const x = tokens(normalizeColors(a)), y = tokens(normalizeColors(b));
    if (x.length !== y.length) return b;
    let out = '';
    for (let i = 0; i < x.length; i++) {
      const p = x[i], q = y[i];
      if (p.text !== undefined && q.text !== undefined && p.text.trim() === q.text.trim()) out += p.text;
      else if (p.value !== undefined && q.value !== undefined && (p.unit === q.unit || p.value === 0 || q.value === 0)) out += format(p.value + q.value) + (p.unit || q.unit);
      else return b;
    }
    return out;
  }

  const INITIAL: any = { opacity: '1', transform: 'none', translate: 'none', scale: 'none', rotate: 'none', filter: 'none', 'letter-spacing': '0px', 'background-color': 'rgba(0, 0, 0, 0)', color: 'rgb(0, 0, 0)' };

  function inlineValue(target: any, property: any) {
    try {
      const v = target.style.getPropertyValue(property);
      if (v) return v;
    } catch {}
    return INITIAL[property] ?? '';
  }

  function effectValue(effect: any, property: any, progress: any, iteration: any, underlying: any) {
    const css = property;
    const frames = [];
    for (const f of effect._frames) {
      for (const key of Object.keys(f.values)) {
        if (animatableProperty(key) === css) frames.push({ offset: f.computedOffset, value: f.values[key], easing: f.easing, composite: f.composite === 'auto' ? effect._composite : f.composite });
      }
    }
    if (!frames.length) return null;
    if (!frames.some((f: any) => f.offset === 0)) frames.unshift({ offset: 0, value: null, easing: LINEAR, composite: 'replace' });
    if (!frames.some((f: any) => f.offset === 1)) frames.push({ offset: 1, value: null, easing: LINEAR, composite: 'replace' });
    const base = underlying(css);
    const resolve = (f: any) => (f.value === null ? base : f.composite === 'replace' ? f.value : combine(base, f.value, css));
    const zeros = frames.filter((f: any) => f.offset === 0), ones = frames.filter((f: any) => f.offset === 1);
    if (progress < 0 && zeros.length > 1) return resolve(zeros[0]);
    if (progress >= 1 && ones.length > 1) return resolve(ones[ones.length - 1]);
    let start;
    if (progress < 0) start = 0;
    else if (progress >= 1) start = frames.length - 2;
    else {
      start = 0;
      for (let i = 0; i < frames.length; i++) if (frames[i].offset <= progress && frames[i].offset < 1) start = i;
    }
    const a = frames[start], b = frames[Math.min(start + 1, frames.length - 1)];
    if (a === b) return resolve(a);
    const span = b.offset - a.offset;
    const local = span === 0 ? 0 : (progress - a.offset) / span;
    void iteration;
    return mix(resolve(a), resolve(b), a.easing.fn(local, false));
  }

  class AnimationPlaybackEvent extends Event {
    [key: string]: any;
    constructor(type: any, init: any = {}) {
      super(type, init);
      this.currentTime = init.currentTime ?? null;
      this.timelineTime = init.timelineTime ?? null;
    }
  }

  class AnimationTimeline {
    [key: string]: any;
    get currentTime(): any {
      return null;
    }
    get duration() {
      return null;
    }
  }

  class DocumentTimeline extends AnimationTimeline {
    [key: string]: any;
    constructor(options?: any) {
      super();
      const origin = options && options.originTime !== undefined ? Number(options.originTime) : 0;
      if (!Number.isFinite(origin)) throw new TypeError('originTime must be finite');
      Object.defineProperty(this, '_origin', { value: origin });
    }
    get currentTime() {
      return documentTime() - this._origin;
    }
  }

  const documentTimeline = new DocumentTimeline();

  function timelineTime(timeline: any) {
    if (!timeline) return null;
    if (timeline instanceof DocumentTimeline) return timeline.currentTime;
    return null;
  }

  function timelineOrigin(timeline: any) {
    return timeline instanceof DocumentTimeline ? timeline._origin : 0;
  }

  function checkTarget(target: any) {
    if (target === null || target === undefined) return null;
    if (typeof target !== 'object' || target.nodeType !== 1) throw new TypeError("Failed to construct 'KeyframeEffect': parameter 1 is not of type 'Element'");
    return target;
  }

  class AnimationEffect {
    [key: string]: any;
    constructor() {
      Object.defineProperty(this, '_timing', { value: new EffectTiming(), writable: true });
      Object.defineProperty(this, '_animation', { value: null, writable: true });
    }
    getTiming() {
      const t = this._timing;
      return { delay: t.delay, direction: t.direction, duration: t.duration, easing: t.easing.text, endDelay: t.endDelay, fill: t.fill, iterationStart: t.iterationStart, iterations: t.iterations };
    }
    getComputedTiming() {
      const a = this._animation;
      const local = a ? a.currentTime : null;
      const c = computeTiming(this._timing, local, !a || a._rate >= 0);
      return { delay: c.delay, direction: c.direction, duration: c.duration, easing: c.easing, endDelay: c.endDelay, fill: c.fill, iterationStart: c.iterationStart, iterations: c.iterations, activeDuration: c.activeDuration, currentIteration: c.currentIteration, endTime: c.endTime, localTime: c.localTime, progress: c.progress };
    }
    updateTiming(timing: any) {
      if (timing === undefined) return;
      if (timing === null || typeof timing !== 'object') throw new TypeError('updateTiming expects an object');
      this._timing = applyTiming(this._timing, timing, true);
      if (this._animation) {
        markDirty(this._animation);
        updateFinished(this._animation, false, false);
      }
    }
  }

  class KeyframeEffect extends AnimationEffect {
    [key: string]: any;
    constructor(target: any, keyframes: any, options: any) {
      super();
      if (target instanceof KeyframeEffect && keyframes === undefined) {
        const source = target;
        Object.defineProperty(this, '_target', { value: source._target, writable: true });
        Object.defineProperty(this, '_frames', { value: source._frames.map((f: any) => ({ ...f, values: { ...f.values } })), writable: true });
        Object.defineProperty(this, '_composite', { value: source._composite, writable: true });
        Object.defineProperty(this, '_iterationComposite', { value: source._iterationComposite, writable: true });
        this._timing = Object.assign(new EffectTiming(), source._timing);
        return;
      }
      Object.defineProperty(this, '_target', { value: checkTarget(target), writable: true });
      Object.defineProperty(this, '_frames', { value: processKeyframes(keyframes), writable: true });
      let composite = 'replace', iterationComposite = 'replace';
      if (options !== null && typeof options === 'object') {
        if (options.composite !== undefined) {
          composite = String(options.composite);
          if (!['replace', 'add', 'accumulate'].includes(composite)) throw new TypeError(`'${composite}' is not a valid composite operation`);
        }
        if (options.iterationComposite !== undefined) {
          iterationComposite = String(options.iterationComposite);
          if (!['replace', 'accumulate'].includes(iterationComposite)) throw new TypeError(`'${iterationComposite}' is not a valid iteration composite operation`);
        }
        if (options.pseudoElement !== undefined && options.pseudoElement !== null) unsupported('KeyframeEffect pseudoElement');
      }
      Object.defineProperty(this, '_composite', { value: composite, writable: true });
      Object.defineProperty(this, '_iterationComposite', { value: iterationComposite, writable: true });
      this._timing = applyTiming(this._timing, options, false);
    }
    get target() {
      return this._target;
    }
    set target(value: any) {
      const next = checkTarget(value);
      if (next === this._target) return;
      if (this._animation) untrack(this._animation);
      this._target = next;
      if (this._animation) {
        track(this._animation);
        markDirty(this._animation);
      }
    }
    get pseudoElement() {
      return null;
    }
    set pseudoElement(v: any) {
      if (v !== null && v !== undefined) unsupported('KeyframeEffect pseudoElement');
    }
    get composite() {
      return this._composite;
    }
    set composite(v: any) {
      const s = String(v);
      if (!['replace', 'add', 'accumulate'].includes(s)) throw new TypeError(`'${s}' is not a valid composite operation`);
      this._composite = s;
      if (this._animation) markDirty(this._animation);
    }
    get iterationComposite() {
      return this._iterationComposite;
    }
    set iterationComposite(v: any) {
      const s = String(v);
      if (!['replace', 'accumulate'].includes(s)) throw new TypeError(`'${s}' is not a valid iteration composite operation`);
      this._iterationComposite = s;
      if (this._animation) markDirty(this._animation);
    }
    getKeyframes() {
      return this._frames.map((f: any) => {
        const out: any = { composite: f.composite, computedOffset: f.computedOffset, easing: f.easing.text, offset: f.offset };
        for (const key of Object.keys(f.values)) out[cssToCamel(animatableProperty(key) || key)] = f.values[key];
        return out;
      });
    }
    setKeyframes(keyframes: any) {
      this._frames = processKeyframes(keyframes);
      if (this._animation) markDirty(this._animation);
    }
  }

  function settle(promise: any, ok: any, value: any) {
    if (promise._state !== 'pending') return;
    promise._state = ok ? 'fulfilled' : 'rejected';
    if (ok) promise._resolve(value);
    else promise._reject(value);
  }

  function makePromise(handled: any) {
    let res, rej;
    const p = new NativePromise((a: any, b: any) => { res = a; rej = b; });
    Object.defineProperty(p, '_resolve', { value: res });
    Object.defineProperty(p, '_reject', { value: rej });
    Object.defineProperty(p, '_state', { value: 'pending', writable: true });
    if (handled) then.call(p, undefined, () => {});
    return p;
  }

  function resolvedPromise(value: any) {
    const p = makePromise(false);
    settle(p, true, value);
    return p;
  }

  class Animation extends EventTarget {
    [key: string]: any;
    constructor(effect: any = null, timeline: any) {
      super();
      if (effect !== null && effect !== undefined && !(effect instanceof AnimationEffect)) throw new TypeError("Failed to construct 'Animation': parameter 1 is not of type 'AnimationEffect'");
      const hidden: any = { _aid: ++seq, _effect: null, _timeline: timeline === undefined ? documentTimeline : timeline, _start: null, _hold: null, _rate: 1, _pendingRate: null, _pending: null, _previous: null, _replace: 'active', _finishQueued: false, _id: '', _handlers: { finish: null, cancel: null, remove: null } };
      for (const key of Object.keys(hidden)) Object.defineProperty(this, key, { value: hidden[key], writable: true });
      Object.defineProperty(this, '_ready', { value: resolvedPromise(this), writable: true });
      Object.defineProperty(this, '_finished', { value: makePromise(true), writable: true });
      all.add(this);
      if (effect) this.effect = effect;
    }
    get id() {
      return this._id;
    }
    set id(v: any) {
      this._id = String(v);
    }
    get effect() {
      return this._effect;
    }
    set effect(effect: any) {
      if (effect !== null && effect !== undefined && !(effect instanceof AnimationEffect)) throw new TypeError('effect must be an AnimationEffect');
      if (effect === this._effect) return;
      if (this._effect) {
        untrack(this);
        this._effect._animation = null;
      }
      if (effect && effect._animation) effect._animation.effect = null;
      this._effect = effect || null;
      if (this._effect) {
        this._effect._animation = this;
        track(this);
      }
      markDirty(this);
      updateFinished(this, false, false);
    }
    get timeline() {
      return this._timeline;
    }
    set timeline(timeline: any) {
      if (timeline === this._timeline) return;
      const previous = this.currentTime;
      this._timeline = timeline;
      if (previous !== null) silentlySetCurrentTime(this, previous);
      markDirty(this);
      updateFinished(this, false, false);
    }
    get startTime() {
      return this._start;
    }
    set startTime(value: any) {
      if (this._css) this._cssControl = true;
      const newStart = value === null || value === undefined ? null : Number(value);
      if (newStart !== null && !Number.isFinite(newStart)) throw new TypeError('startTime must be finite');
      const tt = timelineTime(this._timeline);
      if (tt === null && newStart !== null) this._hold = null;
      const previous = this.currentTime;
      applyPendingRate(this);
      this._start = newStart;
      if (newStart !== null) this._hold = this._rate !== 0 ? null : previous;
      else this._hold = previous;
      if (this._pending) {
        this._pending = null;
        settle(this._ready, true, this);
      }
      updateFinished(this, true, false);
      markDirty(this);
    }
    get currentTime() {
      if (this._hold !== null) return this._hold;
      const tt = timelineTime(this._timeline);
      if (tt === null || this._start === null) return null;
      return (tt - this._start) * this._rate;
    }
    set currentTime(value: any) {
      if (this._css) this._cssControl = true;
      if (value === null || value === undefined) {
        if (this.currentTime !== null) throw new TypeError('currentTime cannot be set to null');
        return;
      }
      const seek = Number(value);
      if (!Number.isFinite(seek)) throw new TypeError('currentTime must be finite');
      silentlySetCurrentTime(this, seek);
      if (this._pending === 'pause') {
        this._hold = seek;
        applyPendingRate(this);
        this._start = null;
        this._pending = null;
        settle(this._ready, true, this);
      }
      updateFinished(this, true, false);
      markDirty(this);
    }
    get playbackRate() {
      return this._rate;
    }
    set playbackRate(value: any) {
      if (this._css) this._cssControl = true;
      const rate = Number(value);
      if (!Number.isFinite(rate)) throw new TypeError('playbackRate must be finite');
      this._pendingRate = null;
      const previous = this.currentTime;
      this._rate = rate;
      if (previous !== null) this.currentTime = previous;
      markDirty(this);
    }
    get playState() {
      const ct = this.currentTime;
      if (ct === null && !this._pending) return 'idle';
      if (this._pending === 'pause' || (this._start === null && this._pending !== 'play')) return 'paused';
      const rate = this._rate;
      const end = effectEnd(this);
      if (ct !== null && ((rate > 0 && ct >= end) || (rate < 0 && ct <= 0))) return 'finished';
      return 'running';
    }
    get replaceState() {
      return this._replace;
    }
    get pending() {
      return this._pending !== null;
    }
    get ready() {
      return this._ready;
    }
    get finished() {
      return this._finished;
    }
    get onfinish() {
      return this._handlers.finish;
    }
    set onfinish(fn: any) {
      this._handlers.finish = typeof fn === 'function' ? fn : null;
    }
    get oncancel() {
      return this._handlers.cancel;
    }
    set oncancel(fn: any) {
      this._handlers.cancel = typeof fn === 'function' ? fn : null;
    }
    get onremove() {
      return this._handlers.remove;
    }
    set onremove(fn: any) {
      this._handlers.remove = typeof fn === 'function' ? fn : null;
    }
    play() {
      if (this._css) this._cssControl = true;
      playAnimation(this, true);
    }
    pause() {
      if (this._css) this._cssControl = true;
      if (this._pending === 'pause') return;
      if (this.playState === 'paused') return;
      let seek = null;
      if (this.currentTime === null) {
        if (this._rate >= 0) seek = 0;
        else {
          const end = effectEnd(this);
          if (end === Infinity) throw domException('InvalidStateError', 'cannot pause a reversed animation with infinite duration');
          seek = end;
        }
      }
      if (seek !== null) {
        this._hold = seek;
        this._start = null;
      }
      let hasPendingReady = false;
      if (this._pending === 'play') {
        this._pending = null;
        hasPendingReady = true;
      }
      if (!hasPendingReady) this._ready = makePromise(false);
      this._pending = 'pause';
      pendingSet.add(this);
      requestFrame();
      updateFinished(this, false, false);
      markDirty(this);
    }
    reverse() {
      if (this._css) this._cssControl = true;
      if (timelineTime(this._timeline) === null) throw domException('InvalidStateError', 'cannot reverse an animation with no active timeline');
      const original = this._pendingRate;
      this._pendingRate = -(this._pendingRate ?? this._rate);
      try {
        playAnimation(this, true);
      } catch (e) {
        this._pendingRate = original;
        throw e;
      }
    }
    updatePlaybackRate(value: any) {
      if (this._css) this._cssControl = true;
      const rate = Number(value);
      if (!Number.isFinite(rate)) throw new TypeError('playbackRate must be finite');
      const state = this.playState;
      this._pendingRate = rate;
      if (this._pending) return;
      if (state === 'idle' || state === 'paused') applyPendingRate(this);
      else if (state === 'finished') {
        const tt = timelineTime(this._timeline);
        const unconstrained = this._start !== null && tt !== null ? (tt - this._start) * this._rate : null;
        const time = rate !== 0 && unconstrained !== null ? (tt as number) - unconstrained / rate : tt;
        applyPendingRate(this);
        this._start = time;
        updateFinished(this, false, false);
      } else playAnimation(this, false);
      markDirty(this);
    }
    finish() {
      if (this._css) this._cssControl = true;
      const rate = this._pendingRate ?? this._rate;
      const end = effectEnd(this);
      if (rate === 0 || (rate > 0 && end === Infinity)) throw domException('InvalidStateError', 'cannot finish an animation with a zero playback rate or infinite duration');
      applyPendingRate(this);
      const limit = this._rate > 0 ? end : 0;
      silentlySetCurrentTime(this, limit);
      const tt = timelineTime(this._timeline);
      if (this._start === null && tt !== null) this._start = tt - limit / this._rate;
      if (this._pending === 'pause' && this._start !== null) {
        this._hold = null;
        this._pending = null;
        settle(this._ready, true, this);
      }
      if (this._pending === 'play' && this._start !== null) {
        this._pending = null;
        settle(this._ready, true, this);
      }
      updateFinished(this, true, true);
      markDirty(this);
    }
    cancel() {
      if (this._css) this._cssControl = true;
      if (this.playState !== 'idle') {
        resetPending(this);
        if (this._finished._state === 'pending') {
          settle(this._finished, false, domException('AbortError', 'The animation was cancelled'));
        }
        this._finished = makePromise(true);
        const tt = timelineTime(this._timeline);
        if (tt !== null) queueEvent(this, 'cancel', null, tt);
      }
      this._hold = null;
      this._start = null;
      markDirty(this);
    }
    persist() {
      const was = this._replace;
      this._replace = 'persisted';
      if (was === 'removed') markDirty(this);
    }
    commitStyles() {
      const effect = this._effect;
      const target = effect && effect._target;
      if (!target) return;
      if (!target.isConnected) throw domException('InvalidModificationError', 'target is not being rendered');
      const c = computeTiming(effect._timing, this.currentTime, this._rate >= 0);
      if (c.progress === null) return;
      const props = new Set<any>();
      for (const f of effect._frames) for (const key of Object.keys(f.values)) { const css = animatableProperty(key); if (css) props.add(css); }
      for (const css of props) {
        const value = effectValue(effect, css, c.progress, c.currentIteration, (p: any) => inlineValue(target, p));
        if (value !== null) target.style.setProperty(css, value);
      }
    }
    addEventListener(type: any, fn: any, options: any) {
      super.addEventListener(type, fn, options);
    }
  }

  const cssCache = new Map<any, any>();

  class CSSAnimation extends Animation {
    [key: string]: any;
    get animationName() {
      return this._css.name;
    }
  }

  class CSSTransition extends Animation {
    [key: string]: any;
    get transitionProperty() {
      return this._css.name;
    }
  }

  function cssObject(r: any) {
    const target = nodeById(r.id);
    if (!target || typeof r.name !== 'string') return null;
    const key = r.id + '|' + r.kind + '|' + r.name;
    let a = cssCache.get(key);
    if (a && a._css.origin !== r.origin) {
      cssCache.delete(key);
      a = null;
    }
    const timing: any = { duration: Math.max(0, Number(r.duration) || 0), delay: Number(r.delay) || 0, iterations: r.iterations === null ? Infinity : Math.max(0, Number(r.iterations) || 0), direction: r.direction, fill: r.fill, easing: r.easing };
    if (!a) {
      if (cssCache.size >= MAX_CSS_CACHE) cssCache.clear();
      let effect;
      try {
        effect = new KeyframeEffect(target, null, timing);
      } catch {
        effect = new KeyframeEffect(target, null, { duration: timing.duration, delay: timing.delay, iterations: timing.iterations });
      }
      a = r.kind === 'transition' ? new CSSTransition(effect, documentTimeline) : new CSSAnimation(effect, documentTimeline);
      Object.defineProperty(a, '_css', { value: { kind: r.kind, name: r.name, origin: r.origin, id: r.id }, writable: true });
      Object.defineProperty(a, '_cssControl', { value: false, writable: true });
      cssCache.set(key, a);
    }
    a._css.position = r.position;
    if (!a._cssControl) {
      const ct = Number(r.currentTime);
      const rate = Number.isFinite(Number(r.playbackRate)) ? Number(r.playbackRate) : 1;
      a._rate = rate;
      if (r.paused) {
        a._hold = ct;
        a._start = null;
      } else {
        a._hold = null;
        a._start = documentTime() - (rate === 0 ? 0 : ct / rate);
      }
      a._previous = a.currentTime;
      if (!all.has(a)) {
        all.add(a);
        track(a);
      }
      updateFinished(a, false, false);
    }
    return a;
  }

  function cssAnimations(element: any, subtree: any) {
    const id = element ? nodeId(element) : 0;
    if (!id) return [];
    let result;
    listingCss = true;
    try {
      result = query({ q: 'anims', id, subtree }, false);
    } catch {
      return [];
    } finally {
      listingCss = false;
    }
    const list = result && Array.isArray(result.list) ? result.list : [];
    const out: any[] = [];
    list.forEach((r: any, i: any) => {
      if (!r || typeof r !== 'object') return;
      r.position = i;
      const a = cssObject(r);
      if (a && isRelevant(a)) out.push(a);
    });
    return out;
  }

  function effectEnd(animation: any) {
    const e = animation._effect;
    if (!e) return 0;
    const t = e._timing;
    const duration = t.duration === 'auto' ? 0 : t.duration;
    const active = duration === 0 || t.iterations === 0 ? 0 : duration * t.iterations;
    return Math.max(t.delay + active + t.endDelay, 0);
  }

  function applyPendingRate(animation: any) {
    if (animation._pendingRate === null) return;
    animation._rate = animation._pendingRate;
    animation._pendingRate = null;
  }

  function silentlySetCurrentTime(animation: any, seek: any) {
    const tt = timelineTime(animation._timeline);
    if (animation._hold !== null || animation._start === null || tt === null || animation._rate === 0) animation._hold = seek;
    else animation._start = tt - seek / animation._rate;
    if (tt === null) animation._start = null;
    animation._previous = null;
  }

  function resetPending(animation: any) {
    if (!animation._pending) return;
    animation._pending = null;
    pendingSet.delete(animation);
    applyPendingRate(animation);
    settle(animation._ready, false, domException('AbortError', 'The animation was cancelled'));
    then.call(animation._ready, undefined, () => {});
    animation._ready = resolvedPromise(animation);
  }

  function playAnimation(animation: any, autoRewind: any) {
    const abortedPause = animation._pending === 'pause';
    let hasPendingReady = false;
    let seek = null;
    const rate = animation._pendingRate ?? animation._rate;
    const ct = animation.currentTime;
    const end = effectEnd(animation);
    if (rate > 0 && autoRewind && (ct === null || ct < 0 || ct >= end)) seek = 0;
    else if (rate < 0 && autoRewind && (ct === null || ct <= 0 || ct > end)) {
      if (end === Infinity) throw domException('InvalidStateError', 'cannot play a reversed animation with infinite duration');
      seek = end;
    } else if (rate === 0 && ct === null) seek = 0;
    if (seek !== null) {
      animation._hold = seek;
      animation._start = null;
    }
    if (animation._hold !== null) animation._start = null;
    if (animation._pending) {
      animation._pending = null;
      hasPendingReady = true;
    }
    if (animation._hold === null && seek === null && !abortedPause && animation._pendingRate === null) return;
    if (!hasPendingReady) animation._ready = makePromise(false);
    animation._pending = 'play';
    pendingSet.add(animation);
    if (animation._replace === 'removed') animation._replace = 'active';
    requestFrame();
    updateFinished(animation, false, false);
    markDirty(animation);
  }

  function commitPending(animation: any, readyTime: any) {
    if (animation._pending === 'play') {
      if (animation._hold !== null) {
        applyPendingRate(animation);
        animation._start = animation._rate === 0 ? readyTime : readyTime - animation._hold / animation._rate;
        if (animation._rate !== 0) animation._hold = null;
      } else if (animation._start !== null && animation._pendingRate !== null) {
        const match = (readyTime - animation._start) * animation._rate;
        applyPendingRate(animation);
        animation._start = animation._rate === 0 ? readyTime : readyTime - match / animation._rate;
      }
    } else if (animation._pending === 'pause') {
      if (animation._start !== null && animation._hold === null) animation._hold = (readyTime - animation._start) * animation._rate;
      applyPendingRate(animation);
      animation._start = null;
    }
    animation._pending = null;
    settle(animation._ready, true, animation);
    updateFinished(animation, false, false);
    markDirty(animation);
  }

  function updateFinished(animation: any, didSeek: any, sync: any) {
    const tt = timelineTime(animation._timeline);
    const unconstrained = didSeek ? animation.currentTime : animation._start !== null && tt !== null ? (tt - animation._start) * animation._rate : null;
    if (unconstrained !== null && animation._start !== null && !animation._pending) {
      const end = effectEnd(animation);
      const rate = animation._rate;
      if (rate > 0 && unconstrained >= end) {
        animation._hold = didSeek ? unconstrained : animation._previous === null ? end : Math.max(animation._previous, end);
      } else if (rate < 0 && unconstrained <= 0) {
        animation._hold = didSeek ? unconstrained : animation._previous === null ? 0 : Math.min(animation._previous, 0);
      } else if (rate !== 0 && tt !== null) {
        if (didSeek && animation._hold !== null) animation._start = tt - animation._hold / rate;
        animation._hold = null;
      }
    }
    animation._previous = animation.currentTime;
    const finished = animation.playState === 'finished';
    if (finished && animation._finished._state === 'pending') {
      if (sync || frameTime !== null) {
        animation._finishQueued = false;
        finishNotify(animation);
      } else if (!animation._finishQueued) {
        animation._finishQueued = true;
        microtask(() => {
          if (!animation._finishQueued) return;
          animation._finishQueued = false;
          finishNotify(animation);
        });
      }
    }
    if (!finished && animation._finished._state !== 'pending') animation._finished = makePromise(true);
    scheduleFinish();
  }

  function finishNotify(animation: any) {
    if (animation.playState !== 'finished') return;
    settle(animation._finished, true, animation);
    queueEvent(animation, 'finish', animation.currentTime, timelineTime(animation._timeline));
  }

  function queueEvent(animation: any, type: any, currentTime: any, timelineValue: any) {
    if (pendingEvents.length >= MAX_EVENTS_PER_FRAME) return;
    pendingEvents.push({ animation, type, currentTime, timelineTime: timelineValue, order: pendingEvents.length });
    requestFrame();
  }

  function scheduleFinish() {
    let earliest = Infinity;
    const now = documentTime();
    for (const a of all) {
      if (a._pending || a._start === null || a._hold !== null || a._rate === 0 || !a._effect) continue;
      if (a._finished._state !== 'pending' && a._replace !== 'active') continue;
      const end = effectEnd(a);
      let at;
      if (a._rate > 0) {
        if (end === Infinity) continue;
        at = a._start + end / a._rate;
      } else at = a._start;
      at += timelineOrigin(a._timeline);
      if (at < earliest) earliest = at;
    }
    if (earliest === Infinity) {
      if (finishTimer) { clearTimer(finishTimer); finishTimer = 0; }
      finishAt = Infinity;
      return;
    }
    if (earliest <= now) {
      if (finishTimer) { clearTimer(finishTimer); finishTimer = 0; }
      finishAt = earliest;
      if (frameTime === null) requestFrame();
      return;
    }
    if (finishTimer && Math.abs(finishAt - earliest) < 1) return;
    if (finishTimer) clearTimer(finishTimer);
    finishAt = earliest;
    finishTimer = setTimer(() => { finishTimer = 0; requestFrameNext(); }, Math.max(0, earliest - now - 4));
  }

  function track(animation: any) {
    const target = animation._effect && animation._effect._target;
    if (!target) return;
    let set = byTarget.get(target);
    if (!set) byTarget.set(target, (set = new Set<any>()));
    set.add(animation);
  }

  function untrack(animation: any) {
    const target = animation._effect && animation._effect._target;
    if (target) byTarget.get(target)?.delete(animation);
    if (sent.has(animation._aid)) {
      dirty.add(animation);
      animation._sendRemove = true;
    }
  }

  function markDirty(animation: any) {
    dirty.add(animation);
    if (!all.has(animation)) {
      all.add(animation);
      track(animation);
    }
  }

  function retire(a: any) {
    if (a._pending || a._finishQueued) return false;
    const state = a.playState;
    if (a._replace !== 'removed' && state !== 'idle') {
      if (state !== 'finished' && state !== 'paused') return false;
      if (state === 'paused' && a.currentTime !== null) return false;
      const e = a._effect;
      if (e && computeTiming(e._timing, a.currentTime, a._rate >= 0).activeTime !== null) return false;
      if (a._finished._state === 'pending' && state === 'finished') return false;
    }
    all.delete(a);
    const target = a._effect && a._effect._target;
    if (target) byTarget.get(target)?.delete(a);
    return true;
  }

  function isRelevant(a: any) {
    const e = a._effect;
    if (!e || a._replace === 'removed') return false;
    if (a._pending === 'pause') return true;
    const c = computeTiming(e._timing, a.currentTime, a._rate >= 0);
    if (c.phase === null) return false;
    if (c.activeTime !== null) return true;
    if (c.phase === 'active') return true;
    if (a._rate > 0 && c.phase === 'before') return true;
    if (a._rate < 0 && c.phase === 'after') return true;
    return false;
  }

  function sortByOrder(list: any) {
    const group = (a: any) => (a._css ? (a._css.kind === 'transition' ? 0 : 1) : 2);
    return list.sort((a: any, b: any) => group(a) - group(b) || (a._css && b._css ? a._css.position - b._css.position : a._aid - b._aid));
  }

  function elementAnimations(element: any, options: any) {
    const subtree = !!(options && options.subtree);
    const out: any[] = [];
    const visit = (el: any) => {
      const set = byTarget.get(el);
      if (set) for (const a of set) if (isRelevant(a)) out.push(a);
    };
    if (subtree) {
      for (const a of all) {
        const t = a._effect && a._effect._target;
        if (t && (t === element || (typeof element.contains === 'function' && element.contains(t))) && isRelevant(a)) out.push(a);
      }
    } else visit(element);
    const css = cssAnimations(element, subtree);
    return sortByOrder(out.filter((a: any) => !a._css).concat(css));
  }

  function documentAnimations() {
    const out: any[] = [];
    for (const a of all) {
      const t = a._effect && a._effect._target;
      if (t && t.isConnected && isRelevant(a) && !a._css) out.push(a);
    }
    const root = globalThis.document && globalThis.document.documentElement;
    return sortByOrder(out.concat(root ? cssAnimations(root, true) : []));
  }

  function animate(element: any, keyframes: any, options: any) {
    const effect = new KeyframeEffect(element, keyframes, options);
    const timeline = options && typeof options === 'object' && 'timeline' in options ? options.timeline : documentTimeline;
    const animation = new Animation(effect, timeline);
    if (options && typeof options === 'object' && options.id !== undefined) animation.id = options.id;
    animation.play();
    return animation;
  }

  function removeReplaced() {
    const replaceable = [];
    for (const a of all) {
      if (a._replace !== 'active' || !a._effect || !a._effect._target || !(a._timeline instanceof DocumentTimeline)) continue;
      if (a.playState !== 'finished') continue;
      const c = computeTiming(a._effect._timing, a.currentTime, a._rate >= 0);
      if (c.activeTime === null) continue;
      replaceable.push(a);
    }
    if (replaceable.length < 2) return;
    sortByOrder(replaceable);
    const covered = new Map<any, any>();
    for (let i = replaceable.length - 1; i >= 0; i--) {
      const a = replaceable[i];
      const target = a._effect._target;
      let props = covered.get(target);
      if (!props) covered.set(target, (props = new Set<any>()));
      const mine = new Set<any>();
      for (const f of a._effect._frames) for (const key of Object.keys(f.values)) { const css = animatableProperty(key); if (css) mine.add(css); }
      if (mine.size && [...mine].every((p: any) => props.has(p))) {
        a._replace = 'removed';
        markDirty(a);
        queueEvent(a, 'remove', a.currentTime, timelineTime(a._timeline));
      }
      for (const p of mine) props.add(p);
    }
  }

  function dispatch(entry: any) {
    const a = entry.animation;
    const event = new AnimationPlaybackEvent(entry.type, { currentTime: entry.currentTime, timelineTime: entry.timelineTime });
    const handler = a._handlers[entry.type];
    if (handler) {
      try {
        event.currentTarget = a;
        event.target = a;
        handler.call(a, event);
      } catch (e) {
        reportError(e);
      }
    }
    try {
      a.dispatchEvent(event);
    } catch (e) {
      reportError(e);
    }
  }

  function frame(time: any) {
    frameRequested = false;
    frameTime = time;
    if (!all.size && !pendingEvents.length) return null;
    const due = finishAt !== Infinity && finishAt <= time + 1;
    if (!pendingSet.size && !due && !pendingEvents.length) {
      if (finishAt !== Infinity && !finishTimer) requestFrameNext();
      return null;
    }
    const pending = [...pendingSet];
    pendingSet.clear();
    for (const a of pending) {
      if (!a._pending) continue;
      const tt = timelineTime(a._timeline);
      if (tt === null) {
        pendingSet.add(a);
        continue;
      }
      commitPending(a, tt);
    }
    if (due) {
      finishAt = Infinity;
      for (const a of [...all]) if (a._start !== null && !a._pending) updateFinished(a, false, false);
    }
    removeReplaced();
    scheduleFinish();
    const batch = pendingEvents.splice(0, pendingEvents.length);
    return (done: any) => flushEvents(batch, done);
  }

  function requestFrameNext() {
    frameRequested = true;
    host('raf');
    armFallback();
  }

  function flushEvents(events: any, done: any) {
    events.sort((x: any, y: any) => {
      const a = x.timelineTime ?? -Infinity, b = y.timelineTime ?? -Infinity;
      return a !== b ? a - b : x.order - y.order;
    });
    let index = 0;
    const next = () => {
      if (index < events.length) {
        dispatch(events[index++]);
        microtask(next);
        return;
      }
      for (const a of [...all]) if (retire(a) && sent.has(a._aid)) dirty.add(a);
      if (done) done();
    };
    next();
  }

  function record(a: any) {
    const effect = a._effect;
    const target = effect && effect._target;
    const id = target ? nodeId(target) : 0;
    if (a._css) {
      if (!a._cssControl || !id) return { skip: true };
      const origin = timeOrigin() + timelineOrigin(a._timeline);
      return {
        aid: a._aid, target: id, css: { kind: a._css.kind, name: a._css.name, origin: a._css.origin },
        startTime: a._hold === null && a._start !== null ? origin + a._start : null,
        holdTime: a._hold !== null ? a._hold : a._start === null ? a.currentTime : null,
        playbackRate: Math.max(-1000, Math.min(a._rate, 1000)),
        cancelled: a.playState === 'idle',
      };
    }
    if (!id) return { pendingTarget: !!target };
    const state = a.playState;
    const idle = state === 'idle' || a._replace === 'removed' || (!a._pending && state === 'finished' && computeTiming(effect._timing, a.currentTime, a._rate >= 0).activeTime === null);
    if (idle) return null;
    const t = effect._timing;
    const frames = [];
    for (const f of effect._frames.slice(0, MAX_KEYFRAMES)) {
      const values: any = {};
      let count = 0;
      for (const key of Object.keys(f.values)) {
        const css = animatableProperty(key);
        if (!css || ++count > MAX_PROPERTIES) continue;
        values[css] = f.values[key];
      }
      frames.push({ offset: f.computedOffset, easing: f.easing.text, composite: f.composite === 'auto' ? null : f.composite, values });
    }
    const origin = timeOrigin() + timelineOrigin(a._timeline);
    const out: any = {
      aid: a._aid,
      target: id,
      keyframes: frames,
      timing: {
        duration: Math.min(t.duration === 'auto' ? 0 : t.duration, MAX_TIME),
        delay: Math.max(-MAX_TIME, Math.min(t.delay, MAX_TIME)),
        endDelay: Math.max(-MAX_TIME, Math.min(t.endDelay, MAX_TIME)),
        iterations: t.iterations === Infinity ? null : Math.min(t.iterations, 1e9),
        iterationStart: Math.min(t.iterationStart, 1e9),
        direction: t.direction,
        fill: t.fill === 'auto' ? 'none' : t.fill,
        easing: t.easing.text,
      },
      composite: effect._composite,
      iterationComposite: effect._iterationComposite,
      startTime: a._hold === null && a._start !== null ? origin + a._start : null,
      holdTime: a._hold !== null ? a._hold : a._start === null ? a.currentTime ?? 0 : null,
      playbackRate: Math.max(-1000, Math.min(a._rate, 1000)),
    };
    if (out.startTime === null && out.holdTime === null) return null;
    return out;
  }

  function flush() {
    if (!dirty.size) return;
    const out: any[] = [];
    const keep = [];
    for (const a of dirty) {
      if (a._sendRemove) {
        a._sendRemove = false;
        if (sent.has(a._aid)) {
          out.push({ aid: a._aid, remove: true });
          sent.delete(a._aid);
          sentScript.delete(a._aid);
        }
      }
      const r = record(a);
      if (r && r.skip) continue;
      if (r && r.pendingTarget) { keep.push(a); continue; }
      if (!r) {
        if (sent.has(a._aid)) {
          out.push({ aid: a._aid, remove: true });
          sent.delete(a._aid);
          sentScript.delete(a._aid);
        }
        continue;
      }
      const json = JSON.stringify(r);
      if (sent.get(a._aid) === json) continue;
      if (!r.css && !sent.has(a._aid) && sentScript.size >= MAX_ANIMATIONS) {
        if (!limitReported) { limitReported = true; diag('animation-limit', `more than ${MAX_ANIMATIONS} live script animations; further ones are not rendered`); }
        continue;
      }
      sent.set(a._aid, json);
      if (!r.css) sentScript.add(a._aid);
      out.push(r);
    }
    dirty.clear();
    for (const a of keep) dirty.add(a);
    if (!out.length) return;
    host('anim', JSON.stringify(out));
    invalidateLayout?.();
  }

  /** A synchronous layout query in the middle of a task: the renderer must see the animation state the script just set. */
  function flushPending() {
    if (dirty.size && !listingCss) flush();
  }

  function endTick() {
    frameTime = null;
    if (frameWanted) {
      frameWanted = false;
      if (pendingSet.size || pendingEvents.length || finishAt !== Infinity) requestFrame();
    }
    flush();
  }

  const globals: Record<string, Function> = { Animation, KeyframeEffect, AnimationEffect, AnimationTimeline, DocumentTimeline, AnimationPlaybackEvent, CSSAnimation, CSSTransition };
  for (const [name, cls] of Object.entries(globals)) Object.defineProperty(cls, 'name', { value: name });

  return {
    animate,
    elementAnimations,
    documentAnimations,
    timeline: documentTimeline,
    frame,
    endTick,
    flushPending,
    defer: microtask,
    globals,
  };
}

let enabled = false;
let instance: any = null;
let maxAnimations = 64;

export function setWebAnimations(on: boolean): void {
  enabled = on;
}

/** Strict (the default): 64 live script animations, as the validator; compat: 1024. */
export function setAnimationLimit(strict: boolean): void {
  maxAnimations = strict ? 64 : 1024;
}

export function webAnimationsEnabled(): boolean {
  return enabled;
}

export function webAnimations(): any {
  return instance ??= createWebAnimations({
    host, diag, unsupported, reportError: (e: unknown) => reportError(e), Event, EventTarget, nodeId: mirroredId, nodeById,
    setTimer: hostTimer, clearTimer: clearHostTimer, timeOrigin, query, invalidateLayout, maxAnimations,
  });
}

/** Sends script animation state changed in this task; a no-op until a widget animates. */
export function flushPendingAnimations(): void {
  if (enabled && instance) instance.flushPending();
}

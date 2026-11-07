// Conformance harness. scripts/conformance/build.ts bundles, per widget, only
// the exports the widget uses (T.ok, T.run, …), minifies them and inlines the
// result as `var T` between the conformance:harness markers, so that every
// widget fits a v1 mini-widget (4 KB compressed). It runs in QuickJS (sandbox),
// a browser (native reference) and any renderer's host. The result is JSON in
// the `data-conformance` attribute of <html> (run.schema.json), which every
// host reads from its validated ops:
//
//   {"v":1,"id":"auto/04-values-units","mode":"sandbox","status":"running|awaiting|done",
//    "n":{"pass":3,"fail":1,"xfail":0,"xpass":0,"skip":0,"pending":0},
//    "checks":[["css.value.units","em, rem and %","pass",""], …],
//    "targets":{"knob":[x,y,w,h]}, "stages":{"flex":[x,y,w,h]}}
//
// A check names one catalogue ID. Status: pass, fail, xfail (failed as
// expected), xpass (expected to fail but passed), skip (not applicable),
// pending (waiting for input or time). In reference mode (`ref`, the native
// rendering of the expected images) build.ts swaps every stage that has a
// <template data-ref="name"> for the template's markup, and stage() leaves
// those stages alone.

const D = document, root = D.documentElement;
export const sandbox = typeof TelegramWidget != 'undefined';
export const mode = window.__conformanceMode || (sandbox ? 'sandbox' : 'native');
const checks = [], byKey = {}, queue = [], targets = {};
let tRects, sRects, started, finished, awaiting, deadline, expire, timer;
const $ = (el) => (typeof el == 'string' ? D.querySelector(el) : el);
const fmt = (v) => JSON.stringify(typeof v == 'number' ? Math.round(v * 100) / 100 : v);
const later = () => { timer = timer || setTimeout(publish, 0); };

function record(id, name, ok, detail, o) {
  o = o || {};
  const st = ok == null ? 'skip' : ok == 'pending' ? ok : (o.xfail ? 'x' : '') + (ok ? 'pass' : 'fail');
  const c = byKey[id + '|' + name] || (byKey[id + '|' + name] = checks[checks.length] = [id, name]);
  c[2] = st;
  c[3] = (detail ? ('' + detail).slice(0, 160) : '') + (o.xfail && st[0] == 'x' ? ' (expected to fail: ' + o.xfail + ')' : '');
  later();
  return st;
}
/** fn (or a value) gives true/false, [bool, detail], or null/undefined to skip; a throw fails the check. */
export function check(id, name, fn, o) {
  if (o && o.sandboxOnly && !sandbox) return record(id, name, null, 'sandbox only', o);
  let r;
  try { r = typeof fn == 'function' ? fn() : fn; } catch (e) { return record(id, name, false, 'threw ' + e, o); }
  return r == null ? record(id, name, null, 'not applicable', o) : record(id, name, r instanceof Array ? !!r[0] : !!r, r[1], o);
}
export const ok = (id, name, cond, detail, o) => check(id, name, [cond, detail], o);
export const eq = (id, name, a, b, o) => check(id, name, [a === b, 'got ' + fmt(a) + ', want ' + fmt(b)], o);
export const near = (id, name, a, b, tol, o) => check(id, name, [typeof a == 'number' && Math.abs(a - b) <= tol, 'got ' + fmt(a) + ', want ' + fmt(b) + ' ±' + tol], o);
export const skip = (id, name, why) => record(id, name, null, why);
/** A check resolved later (an event, a timer): pending until pass()/fail() or the deadline. */
export function wait(id, name, o) {
  // At the deadline (run's awaitInput or deadline) what still waits fails.
  expire = () => { for (const c of checks) if (c[2] == 'pending') c[2] = 'fail', c[3] += ' (timed out)'; finished = 1; publish(); };
  const end = (ok, d) => { if (!h.done) h.done = record(id, name, ok, d, o); };
  const h = { done: o && o.sandboxOnly && !sandbox, pass: (d) => end(true, d), fail: (d) => end(false, d), skip: (d) => end(null, d), result: (ok, d) => end(!!ok, d) };
  record(id, name, h.done ? null : 'pending', h.done ? 'sandbox only' : o && o.hint, o);
  return h;
}
export const cs = (el, prop, pseudo) => getComputedStyle($(el), pseudo).getPropertyValue(prop).trim();
export function rect(el) { const r = $(el).getBoundingClientRect(); return { x: r.left, y: r.top, w: r.width, h: r.height, r: r.right, b: r.bottom }; }
/** Normalised computed colour "r,g,b,a" (a 0–1, two decimals). */
export function color(el, prop, pseudo) {
  const v = cs(el, prop || 'background-color', pseudo), m = /rgba?\(\s*([\d.]+)\D+([\d.]+)\D+([\d.]+)(?:\D+([\d.]+)(%?))?/.exec(v);
  return m ? [Math.round(m[1]), Math.round(m[2]), Math.round(m[3]), m[4] ? Math.round(m[4] * (m[5] ? 1 : 100)) / 100 : 1].join() : v;
}
/** Steps run in order after the first frames; a step may return a delay in ms before the next one. */
export const step = (fn) => { queue.push(fn); };
/** Draw into a stage from script; skipped in reference mode when the stage has a template. */
export function stage(name, draw) {
  const el = $('[data-stage="' + name + '"]');
  if (el && !(mode == 'ref' && $('template[data-ref="' + name + '"]'))) try { draw(el); } catch (e) { record('harness', 'stage ' + name, false, e); }
}
/** A named element the runner may tap or drag (targets[name] in the result, re-measured on every publish so a runner taps where things are now). */
export const target = (name, el) => {
  targets[name] = el;
  measureTargets = () => { tRects = {}; for (const k in targets) if ($(targets[k])) tRects[k] = r4($(targets[k])); };
};
let measureTargets = () => {};

const r4 = (el) => { const r = el.getBoundingClientRect(); return [r.left, r.top, r.width, r.height].map((v) => Math.round(v * 10) / 10); };
function publish() {
  timer = 0;
  if (started && !finished) measureTargets();
  const n = { pass: 0, fail: 0, xfail: 0, xpass: 0, skip: 0, pending: 0 };
  for (const c of checks) n[c[2]]++;
  const idle = started && !queue.length, st = finished || idle && !n.pending ? 'done' : idle && awaiting ? 'awaiting' : 'running';
  finished = st == 'done';
  root.setAttribute('data-conformance', JSON.stringify({ v: 1, id: root.getAttribute('data-test'), mode, status: st, n, checks, targets: tRects, stages: sRects }));
  if (show) show(st, n);
}
let show;
/** For a person looking at the message (#conf-list): the counts, then what failed or still waits. build.ts calls it for widgets that have the list. */
export function list() {
  show = (st, n) => {
    const lines = [st];
    for (const k in n) if (n[k]) lines.push(k + ' ' + n[k]);
    D.getElementById('conf-list').textContent = [lines.join(' · ')].concat(checks.filter((c) => /^(fail|xpass|pending)/.test(c[2])).map((c) => c.join(' · '))).join('\n');
  };
}
function next() {
  // The stage rects (for the runners' crops) are measured once, before the first step.
  if (!sRects) { sRects = {}; [].forEach.call(D.querySelectorAll('[data-stage]'), (s) => { sRects[s.getAttribute('data-stage')] = r4(s); }); }
  if (!queue.length) {
    started = 1;
    if (deadline && expire) setTimeout(expire, deadline);
    return later();
  }
  let d;
  try { d = queue.shift()(); } catch (e) { record('harness', 'step', false, e); }
  setTimeout(next, d || 0);
}
/** Start after the renderer laid out (two frames, or a fallback timer); awaitInput: ms keeps it `awaiting` until its pending checks resolve. */
export function run(o) {
  o = o || {};
  awaiting = o.awaitInput;
  deadline = o.awaitInput || o.deadline;
  publish();
  let go = () => { go = () => {}; setTimeout(next, o.settle || 60); };
  try { requestAnimationFrame(() => requestAnimationFrame(() => go())); } catch (e) {}
  setTimeout(() => go(), 400);
}

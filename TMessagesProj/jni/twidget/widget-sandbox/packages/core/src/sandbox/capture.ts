// Mutation capture: turns changes to the in-sandbox DOM into protocol ops.
//
// Approach: reconciliation against a mirror of what the host has been told.
//   - DOM mutators are wrapped only to MARK nodes dirty (cheap, no semantics).
//   - flush() re-reads each dirty node (attributes, child list, text) and emits
//     the difference as ops. Because ops are derived from the actual tree, they
//     are correct no matter which linkedom code path performed the mutation.
//   - A periodic full walk catches mutations made through paths that are not
//     wrapped; such misses are counted and shown in diagnostics.
// linkedom's own MutationObserver is not used (it misses text nodes, style
// writes and several insertion paths); the widget-facing MutationObserver below
// is implemented on top of the same reconciliation.

import { DOCUMENT_ID } from '../shared/protocol';
import { document, domHooks, exposeNode, quietly, SVG_NS } from './dom';
import { OpWriter } from './binary-ops';
import { boxNeutral } from './box-neutral';
import { counters, host, hostOps, invalidateLayout, sendStagedCanvas } from './host';
import { canonicalKey, canonicalMap, canonicalValue, forgetSentStyle, parseDeclarations, pendingStyles, setRawAttrValueSetter, styleImplOf, type Decl } from './style';
import { PROFILE_METHODS } from '../shared/profile-tables';

const RENDERER_METHODS = new Set<string>(PROFILE_METHODS);

export const ID = Symbol('tw.id');
const stringifyOps = JSON.stringify;

interface Mirror {
  parent: number;
  kids: number[];
  attrs: Map<string, string> | null;
  data: string | null;
}

let nextId = DOCUMENT_ID + 1;
const byId = new Map<number, any>();
const mirror = new Map<number, Mirror>();
/**
 * Nodes that left their parent for another one in the document whose sync has not run yet: their mirror parent (and the
 * validator's) stays the old one until that insert, so an old ancestor of one must not be removed before it.
 */
const strays = new Set<number>();
let generation = 0;
/**
 * Bumped by the mutations that can change which elements a live collection holds: an element
 * inserted or removed, a `class` attribute written. Reads (classList, dataset), styles, text and
 * flushes leave it alone, so a loop that reads or styles the items of a collection stays linear.
 */
export const domGeneration = (): number => generation;
const dirty = new Set<any>();
let ops: any[] | OpWriter = [];
/**
 * Ops after which a long flush hands what it has to the glue between two nodes: a document built in one task (2000
 * elements, ~4000 ops) stopped at the 8 MB heap on a 64-bit host while it held ~4.4 MB, its op buffer and JSON being the
 * boot's peak, not the document (the client teams' stress-kit runs, 2026-11).
 */
const PARTIAL_BATCH_OPS = 1000;
/** Ops handed to the glue by `sendIfLarge` so far: the full walk counts what it found across them. */
let partialSent = 0;

/** Switch the op stream to binary records (`binaryOps` feature); a no-op unless the glue installed `__hostOps`. */
export function enableBinaryOps(on: boolean) {
  if (on && hostOps && Array.isArray(ops) && !ops.length) ops = new OpWriter(hostOps);
}
/** Ops that must follow the DOM ops of the same flush (method calls, property sets on new nodes). */
let postOps: any[] = [];

export interface Hooks {
  /** A new element entered the mirror (first time the host hears about it). */
  elementCreated(el: any, id: number): void;
  attributeChanged(el: any, name: string, value: string | null): void;
  /** Extra ops for an element being synced (form control properties). */
  elementSynced(el: any, id: number, push: (op: any) => void, isNew: boolean): void;
  /** Called at the end of flush to append canvas command batches. */
  flushExtra(push: (op: any) => void): void;
}
let hooks: Hooks;
export function setHooks(h: Hooks) {
  hooks = h;
}

document[ID] = DOCUMENT_ID;
byId.set(DOCUMENT_ID, document);
mirror.set(DOCUMENT_ID, { parent: 0, kids: [], attrs: null, data: null });

export function markDirty(node: any): void {
  if (node) dirty.add(node);
}
export function idOf(node: any): number {
  return node[ID] ?? (node[ID] = nextId++);
}
export function nodeById(id: number): any {
  return exposeNode(byId.get(id) ?? null);
}
/** Id of a node the host knows about, or 0. */
export function mirroredId(node: any): number {
  const id = node?.[ID];
  return id !== undefined && mirror.has(id) ? id : 0;
}

const renderable = (n: any) => n.nodeType === 1 || n.nodeType === 3 || n.nodeType === 8;

/**
 * Renderable children of a node. The Document is special-cased: linkedom's
 * doctype node is not linked to <html> through nextSibling, and the doctype is
 * never mirrored anyway.
 */
const opaque = (n: any) => n.nodeType === 1 && n.localName === 'script';

function* childrenOf(node: any): Generator<any> {
  if (node.nodeType === 9) {
    if (node.documentElement) yield node.documentElement;
    return;
  }
  if (opaque(node)) return;
  for (let c = node.firstChild; c; c = c.nextSibling) if (renderable(c)) yield c;
}

function readAttrs(el: any): Map<string, string> {
  const out = new Map<string, string>();
  for (const a of el.attributes) out.set(a.name, String(a.value ?? ''));
  return out;
}

export function hasPendingRecords(): boolean {
  return records.length > 0;
}

/** Queue a renderer-side property set (scrollTop, …) for an element. */
export function pushProp(node: any, name: string, value: string | number | boolean) {
  dirty.add(node);
  postOps.push({ op: 'prop', node, name, value });
}

// ───────────────────────── widget-facing MutationObserver ─────────────────────────

interface Observation { target: any; options: any }
const observers = new Set<MutationObserverImpl>();
domHooks.observing = () => observers.size > 0;
domHooks.markDirty = (node) => dirty.add(node);
let records: any[] = [];

function record(rec: any) {
  if (observers.size) records.push({ addedNodes: [], removedNodes: [], attributeName: null, oldValue: null, previousSibling: null, nextSibling: null, ...rec });
}

class MutationObserverImpl {
  _cb: (records: any[], observer: any) => void;
  _obs: Observation[] = [];
  _queue: any[] = [];
  constructor(cb: (records: any[], observer: any) => void) {
    this._cb = cb;
  }
  observe(target: any, options: any = {}) {
    const o = { ...options };
    if (o.attributeFilter || o.attributeOldValue) o.attributes = o.attributes ?? true;
    this._obs = this._obs.filter((x) => x.target !== target);
    this._obs.push({ target, options: o });
    observers.add(this);
  }
  disconnect() {
    this._obs = [];
    this._queue = [];
    observers.delete(this);
  }
  takeRecords() {
    return this._queue.splice(0);
  }
}
export { MutationObserverImpl as MutationObserver };

function wants(o: Observation, rec: any): boolean {
  const { target, options } = o;
  if (rec.target !== target && !(options.subtree && target.contains?.(rec.target))) return false;
  if (rec.type === 'childList') return !!options.childList;
  if (rec.type === 'attributes') return !!options.attributes && (!options.attributeFilter || options.attributeFilter.includes(rec.attributeName));
  return !!options.characterData;
}

/** Deliver queued records. Returns true if any callback ran (it may have mutated the DOM). */
export function deliverMutationRecords(report: (e: unknown) => void): boolean {
  if (!records.length) return false;
  const batch = records;
  records = [];
  let ran = false;
  for (const obs of [...observers]) {
    const mine = batch.filter((rec) => obs._obs.some((o) => wants(o, rec)));
    if (!mine.length) continue;
    ran = true;
    try {
      obs._cb(mine, obs);
    } catch (e) {
      report(e);
    }
  }
  return ran;
}

// ───────────────────────── reconciliation ─────────────────────────

function createNode(node: any): number {
  const id = idOf(node);
  byId.set(id, node);
  if (node.nodeType === 1) {
    forgetSentStyle(node);
    const attrs = readAttrs(node);
    const m: Mirror = { parent: 0, kids: [], attrs, data: null };
    mirror.set(id, m);
    const op: any = { op: 'el', id, tag: node.localName };
    if (node.namespaceURI === SVG_NS) op.ns = 'svg';
    if (attrs.size) op.attrs = Object.fromEntries(attrs);
    ops.push(op);
    hooks.elementCreated(node, id);
    for (const [name, value] of attrs) hooks.attributeChanged(node, name, value);
    hooks.elementSynced(node, id, (o) => postOps.push(o), true);
    for (const c of childrenOf(node)) {
      const known = c[ID];
      const cid = known !== undefined && mirror.has(known) ? known : createNode(c);
      mirror.get(cid)!.parent = id;
      strays.delete(cid);
      m.kids.push(cid);
      ops.push({ op: 'ins', parent: id, id: cid, before: null });
      sendIfLarge();
    }
  } else {
    const data = String(node.data ?? '');
    mirror.set(id, { parent: 0, kids: [], attrs: null, data });
    ops.push({ op: node.nodeType === 3 ? 'text' : 'comment', id, data });
  }
  return id;
}

/** Make sure the host knows a (possibly detached) element, e.g. an offscreen canvas. */
export function ensureCreated(node: any): number {
  return mirroredId(node) || createNode(node);
}

/** Has a node under `id` in the mirror moved to another parent since the last sync (into the document or a new subtree)? */
function holdsMoved(id: number): boolean {
  const node = byId.get(id);
  for (const k of mirror.get(id)?.kids ?? []) {
    if (mirror.get(k)?.parent !== id) continue;
    if (byId.get(k)?.parentNode !== node || holdsMoved(k)) return true;
  }
  return false;
}

/** Is a stray still under `id` by the mirror's parents (bounded: a parent written mid-flush may close a loop)? */
function strayUnder(id: number): boolean {
  for (const s of strays) {
    let p = mirror.get(s)?.parent;
    for (let n = 0; p && n < mirror.size; n++, p = mirror.get(p)?.parent) if (p === id) return true;
  }
  return false;
}

function purge(id: number) {
  const m = mirror.get(id);
  if (!m) return;
  strays.delete(id);
  mirror.delete(id);
  byId.delete(id);
  for (const k of m.kids) if (mirror.get(k)?.parent === id) purge(k);
}

function syncAttrs(el: any, id: number, m: Mirror) {
  const attrs = m.attrs!;
  let count = 0;
  for (const a of el.attributes) {
    const name: string = a.name;
    const value = String(a.value ?? '');
    count++;
    const old = attrs.get(name);
    if (old === value) continue;
    attrs.set(name, value);
    if (observers.size) record({ type: 'attributes', target: el, attributeName: name, oldValue: old ?? null });
    if (name === 'style' && old !== undefined) emitStyleDiff(id, old, value, el);
    else {
      if (name === 'style') forgetSentStyle(el);
      ops.push({ op: 'attr', id, name, value });
    }
    hooks.attributeChanged(el, name, value);
  }
  if (attrs.size <= count) return;
  const seen = new Set<string>();
  for (const a of el.attributes) seen.add(a.name);
  for (const [name, old] of attrs) {
    if (seen.has(name)) continue;
    attrs.delete(name);
    if (name === 'style') forgetSentStyle(el);
    if (observers.size) record({ type: 'attributes', target: el, attributeName: name, oldValue: old });
    ops.push({ op: 'rattr', id, name });
    hooks.attributeChanged(el, name, null);
  }
}

// Parsed style attributes of this flush and the previous one: an animated
// element's new text is next flush's old text, so each text is parsed once.
let parsedNow = new Map<string, Map<string, Decl>>();
let parsedBefore = new Map<string, Map<string, Decl>>();

function parsedStyle(text: string): Map<string, Decl> {
  return parsedNow.get(text) ?? parsedBefore.get(text) ?? parseDeclarations(text);
}

/** Express a change of the style attribute as individual property ops (CSSOM-like). */
function emitStyleDiff(id: number, oldText: string, newText: string, el: any) {
  const impl = styleImplOf(el);
  const before = impl && impl._sent !== null && impl._sentText === oldText ? impl._sent : parsedStyle(oldText);
  const after = parsedStyle(newText);
  parsedNow.set(newText, after);
  for (const [name, d] of after) {
    const o = before.get(name);
    if (!o || o.value !== d.value || o.important !== d.important) ops.push({ op: 'style', id, name, value: d.value, important: d.important || undefined });
  }
  for (const name of before.keys()) if (!after.has(name)) ops.push({ op: 'style', id, name, value: '' });
  if (impl) {
    impl._keys = null;
    if (impl._text === newText && !impl._reparsed && canonicalMap(impl._map)) {
      impl._sent = new Map(after);
      impl._sentText = newText;
    } else {
      impl._sent = null;
      impl._sentText = null;
      impl._reparsed = false;
    }
  }
}

function keyStillCanonical(name: string, map: Map<string, Decl>): boolean {
  const d = map.get(name);
  return d === undefined || (canonicalKey(name) && canonicalValue(d.value));
}

function sendStyleKey(id: number, name: string, map: Map<string, Decl>, sent: Map<string, Decl>) {
  const d = map.get(name);
  const was = sent.get(name);
  if (d !== undefined) {
    if (was === undefined || was.value !== d.value || was.important !== d.important) {
      ops.push({ op: 'style', id, name, value: d.value, important: d.important || undefined });
      sent.set(name, d);
    }
  } else if (was !== undefined) {
    ops.push({ op: 'style', id, name, value: '' });
    sent.delete(name);
  }
}

/** Style properties written through element.style's fast path: one op per changed property, or the attribute path when anything is off. */
function syncPendingStyles() {
  const pending = [...pendingStyles];
  pendingStyles.clear();
  for (const impl of pending) {
    const el = impl._el;
    const keys = impl._keys;
    impl._keys = null;
    if (keys === null || dirty.has(el)) continue;
    const id = el[ID];
    const m = id === undefined ? undefined : mirror.get(id);
    const text = impl._attrText();
    const told = m?.attrs?.get('style');
    let fallback = !m || text === null || text !== impl._text || told !== impl._sentText || impl._reparsed || impl._sent === null;
    if (!fallback) {
      if (typeof keys === 'string') fallback = !keyStillCanonical(keys, impl._map);
      else for (const k of keys) if (!keyStillCanonical(k, impl._map)) { fallback = true; break; }
    }
    if (fallback) {
      impl._sentText = null;
      if (m) dirty.add(el);
      continue;
    }
    if (told === text) continue;
    m!.attrs!.set('style', text!);
    if (observers.size) record({ type: 'attributes', target: el, attributeName: 'style', oldValue: told ?? null });
    const sent = impl._sent!;
    if (typeof keys === 'string') sendStyleKey(id, keys, impl._map, sent);
    else for (const k of keys) sendStyleKey(id, k, impl._map, sent);
    impl._sentText = text;
    hooks.attributeChanged(el, 'style', text);
    hooks.elementSynced(el, id, (o) => postOps.push(o), false);
  }
}

/**
 * The only child, a text node, was replaced by a new text node (textContent,
 * innerText, replaceChildren with a string): the new node takes over the old
 * one's id and the host gets one `data` op instead of `text`, `rm` and `ins`.
 */
function reuseSoleText(node: any, id: number, oldId: number): boolean {
  const old = byId.get(oldId);
  const next = node.firstChild;
  const m = mirror.get(oldId);
  if (!old || !next || old === next || old.nodeType !== 3 || next.nodeType !== 3 || next.nextSibling || old.isConnected) return false;
  if (!m || m.parent !== id) return false;
  if (next[ID] !== undefined && mirror.has(next[ID])) return false;
  delete old[ID];
  next[ID] = oldId;
  byId.set(oldId, next);
  record({ type: 'childList', target: node, addedNodes: [next], removedNodes: [old] });
  const data = String(next.data ?? '');
  if (data !== m.data) {
    m.data = data;
    ops.push({ op: 'data', id: oldId, data });
  }
  return true;
}

function syncChildren(node: any, id: number, m: Mirror) {
  if (node.nodeType !== 9) {
    let i = 0;
    let c = opaque(node) ? null : node.firstChild;
    for (; c; c = c.nextSibling) {
      if (!renderable(c)) continue;
      const cid = c[ID];
      if (cid === undefined || cid !== m.kids[i] || mirror.get(cid)?.parent !== id) break;
      i++;
    }
    if (!c && i === m.kids.length) return;
    if (m.kids.length === 1 && reuseSoleText(node, id, m.kids[0])) return;
  }
  // One pass over the children: the known ones' ids, 0 where a node is still to be created (after the removals below).
  const children: any[] = [];
  const live: number[] = [];
  const liveSet = new Set<number>();
  for (const c of childrenOf(node)) {
    const cid = c[ID];
    const known = cid !== undefined && mirror.has(cid) ? cid : 0;
    children.push(c);
    live.push(known);
    if (known) liveSet.add(known);
  }
  const old = m.kids.filter((k) => mirror.get(k)?.parent === id);
  const kept: number[] = [];
  const gone: [number, any, boolean][] = [];
  for (const k of old) {
    if (liveSet.has(k)) {
      kept.push(k);
      continue;
    }
    const kn = byId.get(k);
    // Removed for good (no new parent, nothing under it moved away): its rm goes before the new children are created, so
    // a subtree replaced in one task (innerHTML, replaceChildren) never has the validator hold the old and the new text
    // together (the complex-script budget drops what passes it). Anything else keeps the order below.
    const early = !!kn && kn.parentNode === null && !holdsMoved(k) && !strayUnder(k);
    if (early) {
      ops.push({ op: 'rm', id: k });
      purge(k);
    }
    gone.push([k, kn, early]);
  }
  const added: any[] = [];
  for (let i = 0; i < live.length; i++) {
    if (live[i] && mirror.has(live[i])) continue;
    live[i] = createNode(children[i]);
    added.push(children[i]);
    sendIfLarge();
  }
  const removed: any[] = [];
  for (const [k, kn, early] of gone) {
    if (!early) {
      // Taken into one of the new children above: not removed.
      if (mirror.get(k)?.parent !== id) continue;
      // Still in the document → it moved; its new parent emits the insert.
      if (!kn || !kn.isConnected) {
        ops.push({ op: 'rm', id: k });
        purge(k);
        sendIfLarge();
      } else strays.add(k);
    }
    removed.push(kn);
  }
  let j = kept.length - 1;
  const moved = new Set<number>();
  let before: number | null = null;
  for (let i = live.length - 1; i >= 0; i--) {
    const cid = live[i];
    while (j >= 0 && moved.has(kept[j])) j--;
    if (j >= 0 && kept[j] === cid) j--;
    else {
      ops.push({ op: 'ins', parent: id, id: cid, before });
      const cm = mirror.get(cid)!;
      if (cm.parent === id) moved.add(cid);
      else if (cm.parent !== 0 && !added.includes(byId.get(cid))) added.push(byId.get(cid));
      cm.parent = id;
      strays.delete(cid);
      sendIfLarge();
    }
    before = cid;
  }
  m.kids = live;
  if (added.length || removed.length) record({ type: 'childList', target: node, addedNodes: added, removedNodes: removed });
}

function syncNode(node: any) {
  const id = node[ID];
  const m = id === undefined ? undefined : mirror.get(id);
  if (!m) {
    // A script's image (new Image()) loads before it is inserted, as in a browser: the renderer gets it with its src.
    if (node?.nodeType === 1 && node.localName === 'img' && !node.isConnected && node.getAttribute('src')) ensureCreated(node);
    return;
  }
  if (node.nodeType === 1) {
    syncAttrs(node, id, m);
    syncChildren(node, id, m);
    hooks.elementSynced(node, id, (o) => postOps.push(o), false);
  } else if (id === DOCUMENT_ID) {
    syncChildren(node, id, m);
  } else {
    const data = String(node.data ?? '');
    if (data !== m.data) {
      record({ type: 'characterData', target: node, oldValue: m.data });
      m.data = data;
      ops.push({ op: 'data', id, data });
    }
  }
}

function walkAll(node: any) {
  syncNode(node);
  sendIfLarge();
  for (const c of childrenOf(node)) walkAll(c);
}

/**
 * Queue a renderer-side method call (scrollTo, setPointerCapture, …) for an
 * element. Methods outside the widget profile (focus, blur, scrollIntoView,
 * dialogs, popovers, text selection: docs/profile-v1.md) do nothing.
 */
export function callMethod(node: any, method: string, args: unknown[] = []) {
  if (!RENDERER_METHODS.has(method)) return;
  dirty.add(node);
  postOps.push({ op: 'call', node, method, args });
}

let lastFullSync = 0;

/** Emit ops for everything that changed since the last flush. */
export function flush(full = false): void {
  quietly(() => flushNow(full));
}

function flushNow(full: boolean): void {
  if (!dirty.size && !postOps.length && !full && !pendingStyles.size) {
    hooks.flushExtra((o) => ops.push(o));
    if (ops.length) send();
    return;
  }
  if (pendingStyles.size) syncPendingStyles();
  // New dirty nodes can appear while syncing (hooks); loop until stable.
  for (let i = 0; i < 8 && dirty.size; i++) {
    const batch = [...dirty];
    dirty.clear();
    for (const node of batch) if (node) { syncNode(node); sendIfLarge(); }
  }
  if (full) {
    const before = partialSent + ops.length;
    walkAll(document);
    counters.missed += partialSent + ops.length - before;
    dirty.clear();
  }
  for (const o of postOps) {
    if (o.node) {
      const id = mirroredId(o.node);
      if (id) {
        const { node: _n, ...rest } = o;
        ops.push({ ...rest, id });
      }
    } else ops.push(o);
  }
  postOps = [];
  hooks.flushExtra((o) => ops.push(o));
  if (parsedNow.size) {
    parsedBefore = parsedNow;
    parsedNow = new Map();
  }
  if (ops.length) send();
}

function send() {
  if (ops instanceof OpWriter) {
    invalidateLayout(ops.length > 0 && ops.boxNeutral);
    sendStagedCanvas();
    ops.flush();
    return;
  }
  const batch = ops;
  ops = [];
  invalidateLayout(batch.length > 0 && batch.every(boxNeutral));
  const json = stringifyOps(batch);
  sendStagedCanvas();
  host('ops', json);
}

/**
 * Between two nodes of a flush: once the buffer holds `PARTIAL_BATCH_OPS`, hand it to the glue, so the op buffer and its
 * JSON never hold a whole large document. Only DOM ops go: the method calls, canvases and animations of the flush follow
 * in its last batch, as before, and the staged canvas payloads stay with it.
 */
function sendIfLarge() {
  if (ops.length < PARTIAL_BATCH_OPS) return;
  partialSent += ops.length;
  if (ops instanceof OpWriter) {
    invalidateLayout(ops.boxNeutral);
    ops.flush();
    return;
  }
  const batch = ops;
  ops = [];
  invalidateLayout(batch.every(boxNeutral));
  host('ops', stringifyOps(batch));
}

/** End-of-task flush, with the periodic full walk as a safety net. */
export function flushTick(): void {
  // Once per task: a mutation the wrappers do not see (an Attr's value) reaches live collections by the next task.
  generation++;
  const now = Date.now();
  const full = now - lastFullSync > 500;
  if (full) lastFullSync = now;
  flush(full);
}

// ───────────────────────── dirty marking (prototype wrappers) ─────────────────────────

const SELF_METHODS = [
  'appendChild', 'insertBefore', 'removeChild', 'replaceChild', 'append', 'prepend', 'replaceChildren', 'normalize',
  'setAttribute', 'setAttributeNS', 'removeAttribute', 'removeAttributeNS', 'toggleAttribute', 'setAttributeNode',
  'removeAttributeNode', 'insertAdjacentElement', 'insertAdjacentText', 'insertAdjacentHTML',
];
const PARENT_METHODS = ['before', 'after', 'replaceWith', 'remove', 'insertAdjacentElement', 'insertAdjacentText', 'insertAdjacentHTML'];
/** Accessors that hand out live objects through which the element can be mutated later. */
const LIVE_GETTERS = ['classList', 'dataset', 'relList', 'part'];
const SKIP_SETTERS = new Set(['scrollTop', 'scrollLeft', 'value', 'checked', 'selectedIndex', 'selected', 'indeterminate', 'onclick']);
/** Setters that can insert or remove elements or change the class (domGeneration); textContent only over element children. */
const TREE_SETTERS = new Set(['className', 'innerHTML', 'outerHTML', 'textContent', 'innerText', 'outerText']);
const elementLike = (x: any) => !!x && typeof x === 'object' && (x.nodeType === 1 || x.nodeType === 11);

/**
 * How a wrapped method decides whether it moves domGeneration: 0 always, 1 the `class` attribute,
 * 2 an element argument, 3 an element `this` or argument, 4 never, 5 an element child or argument.
 */
function treeKind(name: string): number {
  if (name === 'setAttribute' || name === 'removeAttribute' || name === 'toggleAttribute') return 1;
  if (name === 'appendChild' || name === 'insertBefore' || name === 'removeChild' || name === 'replaceChild') return 2;
  if (name === 'append' || name === 'prepend' || name === 'before' || name === 'after') return 2;
  if (name === 'remove' || name === 'replaceWith') return 3;
  if (name === 'normalize' || name === 'insertAdjacentText') return 4;
  if (name === 'replaceChildren') return 5;
  return 0;
}

function changesTree(kind: number, self: any, a: any, b: any): boolean {
  switch (kind) {
    case 0: return true;
    case 1: return String(a).toLowerCase() === 'class';
    case 2: return elementLike(a) || elementLike(b);
    case 3: return self.nodeType === 1 || elementLike(a) || elementLike(b);
    case 5: return !!self.firstElementChild || elementLike(a) || elementLike(b);
    default: return false;
  }
}

function markArgs(args: any[]) {
  for (let i = 0; i < args.length; i++) {
    const a = args[i];
    if (a && typeof a === 'object' && a.nodeType) dirty.add(a.parentNode);
  }
}

export function collectPrototypes(samples: any[]): any[] {
  const protos = new Set<any>();
  for (const s of samples) {
    for (let p = Object.getPrototypeOf(s); p && p !== Object.prototype; p = Object.getPrototypeOf(p)) protos.add(p);
  }
  return [...protos];
}

export function installDirtyTracking(protos: any[], tokenListProto: any) {
  const tokenOwner = new WeakMap<object, any>();
  const attrProto = Object.getPrototypeOf(document.createAttribute('x'));
  const attrValue = Object.getOwnPropertyDescriptor(attrProto, 'value');
  if (attrValue?.set) {
    const set = attrValue.set;
    setRawAttrValueSetter((attr, value) => set.call(attr, value));
  }
  for (const proto of protos) {
    for (const name of Object.getOwnPropertyNames(proto)) {
      const d = Object.getOwnPropertyDescriptor(proto, name)!;
      if (!d.configurable) continue;
      if (typeof d.value === 'function') {
        const self = SELF_METHODS.includes(name);
        const parent = PARENT_METHODS.includes(name);
        if (!self && !parent) continue;
        const orig = d.value;
        const kind = treeKind(name);
        Object.defineProperty(proto, name, {
          ...d,
          // Arity-specialized: most DOM calls take one or two arguments, so no
          // rest array is allocated for them. Strict, so `arguments` is not a
          // mapped object (quickjs-ng < 0.13 frees mapped arguments' var refs
          // too early, issue #1400).
          value: function (this: any, a?: any, b?: any) {
            'use strict';
            const n = arguments.length;
            if (n > 2 || changesTree(kind, this, a, b)) generation++;
            if (self) dirty.add(this);
            if (parent) dirty.add(this.parentNode);
            if (n > 2) {
              markArgs(arguments as unknown as any[]);
              return orig.apply(this, arguments);
            }
            if (n > 0 && a && typeof a === 'object' && a.nodeType) dirty.add(a.parentNode);
            if (n > 1 && b && typeof b === 'object' && b.nodeType) dirty.add(b.parentNode);
            return n === 0 ? orig.call(this) : n === 1 ? orig.call(this, a) : orig.call(this, a, b);
          },
        });
      } else if (d.set && !SKIP_SETTERS.has(name) && !name.startsWith('on')) {
        const set = d.set;
        const live = LIVE_GETTERS.includes(name) && d.get;
        const get = d.get;
        const tree = TREE_SETTERS.has(name);
        const text = name === 'textContent' || name === 'innerText';
        Object.defineProperty(proto, name, {
          ...d,
          get: live
            ? function (this: any) {
                dirty.add(this);
                const v = get!.call(this);
                if (v && typeof v === 'object') tokenOwner.set(v, this);
                return v;
              }
            : get,
          set: function (this: any, v: any) {
            if (tree && (!text || this.firstElementChild)) generation++;
            dirty.add(this);
            // CharacterData and Attr setters affect what their owner renders.
            if (this.nodeType === 2) dirty.add(this.ownerElement);
            if (name === 'outerHTML') dirty.add(this.parentNode);
            set.call(this, v);
          },
        });
      } else if (d.get && LIVE_GETTERS.includes(name)) {
        const get = d.get;
        Object.defineProperty(proto, name, {
          ...d,
          get: function (this: any) {
            dirty.add(this);
            const v = get.call(this);
            if (v && typeof v === 'object') tokenOwner.set(v, this);
            return v;
          },
        });
      }
    }
  }
  // A classList reference kept across ticks still marks its element.
  if (tokenListProto) {
    for (const name of ['add', 'remove', 'toggle', 'replace']) {
      const orig = tokenListProto[name];
      if (typeof orig !== 'function') continue;
      tokenListProto[name] = function (this: any, ...args: any[]) {
        generation++;
        dirty.add(tokenOwner.get(this));
        return orig.apply(this, args);
      };
    }
  }
}

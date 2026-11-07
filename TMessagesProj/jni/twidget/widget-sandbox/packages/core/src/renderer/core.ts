// Renderer core (security-critical, invariants 3 and 6). One instance per
// widget, created by `createRenderer(env)`: inside the sandboxed srcdoc iframe
// (renderer.ts, the only script that ever executes there) `env` is that frame's
// document; the ShadowDomRenderer prototype (src/host/shadow-renderer.ts) runs
// the same core in the chat page itself, on a shadow root.
//
// It applies already-validated RenderOps to the real DOM using DOM APIs only
// (never innerHTML), answers layout queries, forwards user events and cancels
// every navigation.
//
// DOM clobbering: widget markup may contain elements whose id/name shadow
// properties of `document` and of <form> elements (e.g. <input name="setAttribute">).
// Therefore every DOM method/accessor used below is captured from its prototype
// and invoked explicitly ("primordials"); nothing is looked up on `document` or
// on a widget element after start-up.

import { EVENT_TYPES, type CanvasCmd, type HostToRenderer, type Query, type RenderOp, type RendererToHost, type WidgetEvent } from '../shared/protocol';
import { PROFILE_UA_STYLESHEET } from '../shared/profile-tables';
import { THEME_VARIABLES, contentSafeAreaVariables, themeVariables } from '../shared/sdk-policy';
import { GestureArbiter, claimOf, type GestureClaim, type GestureVerdict, type PointerKind } from '../shared/gesture-policy';
import { PaintCounter, animationPaint, boxPaint, boxShadowBlurred, boxShadowWithoutBlur, hidesSubtree, type BoxPaint, type PaintKeyframe, type PaintVerdict } from '../shared/paint-policy';
import { AnimationState, applyAnims, cssAnimationList } from './web-animations';
import { TOUCH_ACTION_CLAIM, engineTouchAction } from './touch-action';

/** Where the core renders: a whole document (iframe, native WebViews) or a shadow root inside the host page. */
export interface RendererEnv {
  /** Element factory. */
  doc: Document;
  /** Listener target and `elementFromPoint`: the document or the shadow root. */
  root: Document | ShadowRoot;
  /** The widget's <html>, <head>, <body>: the document's own, or three elements the shadow renderer created. Read at `init()` (a document's body does not exist while its <head> script runs). */
  roots(): { html: HTMLElement; head: HTMLElement; body: HTMLElement };
  /** Origin of the widget's coordinate space in the root's viewport, or null when they coincide (a document). */
  origin(): { x: number; y: number } | null;
  viewport(): { innerWidth: number; innerHeight: number; dpr: number };
  /** Height of the laid-out widget, used to auto-size the bubble. */
  docHeight(): number;
  matchMedia(query: string): boolean;
  /** Fires when the widget's viewport changes size; returns the unsubscribe. */
  onViewportResize(cb: () => void): () => void;
  /** Fires when the OS colour scheme changes under the renderer (the host re-asserts its theme); returns the unsubscribe. */
  onColorSchemeChange(cb: () => void): () => void;
  /**
   * The element that stands for the canvas when the widget's <html> is not a document root (the shadow renderer's
   * host): the core paints on it what CSS paints on a document's canvas, the root's background or else the body's
   * (root background propagation). A document has a canvas of its own: undefined.
   */
  canvas?: HTMLElement;
  /**
   * A frame inside a page (the web's IframeRenderer, whose element carries the theme's scheme): give the frame's root the
   * same scheme, or the frame gets an opaque canvas in the root's scheme over the bubble behind the transparent renderer.
   * Not in a WebView: a dark root there paints a dark canvas of its own.
   */
  frameColorScheme?(scheme: 'light' | 'dark'): void;
}
export type Send = (msg: RendererToHost, transfer: Transferable[]) => void;
export interface RendererCore {
  /** Starts listening and reporting; `send` carries renderer → host messages. */
  init(send: Send): void;
  onMessage(msg: HostToRenderer): void;
  destroy(): void;
}

const SVG_NS = 'http://www.w3.org/2000/svg';

type AnyFn = (...a: any[]) => any;
const uncurry = <F extends AnyFn>(f: F) => (self: unknown, ...args: Parameters<F>): ReturnType<F> => f.apply(self, args);
const getter = (proto: object, name: string): ((self: unknown) => any) => {
  const g = Object.getOwnPropertyDescriptor(proto, name)!.get!;
  return (self) => g.call(self);
};
const setter = (proto: object, name: string): ((self: unknown, v: any) => void) => {
  const s = Object.getOwnPropertyDescriptor(proto, name)!.set!;
  return (self, v) => s.call(self, v);
};

const createElement = uncurry(Document.prototype.createElement);
const createElementNS = uncurry(Document.prototype.createElementNS);
const createTextNode = uncurry(Document.prototype.createTextNode);
const setAttribute = uncurry(Element.prototype.setAttribute);
const getAttribute = uncurry(Element.prototype.getAttribute);
const removeAttribute = uncurry(Element.prototype.removeAttribute);
const closest = uncurry(Element.prototype.closest);
const getBoundingClientRect = uncurry(Element.prototype.getBoundingClientRect);
const getClientRects = uncurry(Element.prototype.getClientRects);
const insertBefore = uncurry(Node.prototype.insertBefore);
const removeChild = uncurry(Node.prototype.removeChild);
const parentNodeOf = getter(Node.prototype, 'parentNode');
const firstChildOf = getter(Node.prototype, 'firstChild');
const nextSiblingOf = getter(Node.prototype, 'nextSibling');
const localNameOf = getter(Element.prototype, 'localName');
const setTextContent = setter(Node.prototype, 'textContent');
const setCharData = setter(CharacterData.prototype, 'data');
const htmlStyleOf = getter(HTMLElement.prototype, 'style');
const svgStyleOf = getter(SVGElement.prototype, 'style');
const styleOf = (el: Element): CSSStyleDeclaration => (el instanceof SVGElement ? svgStyleOf(el) : htmlStyleOf(el));
const sheetOf = getter(HTMLStyleElement.prototype, 'sheet');
const isConnectedOf = getter(Node.prototype, 'isConnected');
const matches = uncurry(Element.prototype.matches);
const getAnimationsOf = uncurry(Element.prototype.getAnimations);

/** Defense in depth: the validator never emits these, but the renderer refuses them regardless. */
const FORBIDDEN_TAGS = new Set(['script', 'iframe', 'frame', 'frameset', 'object', 'embed', 'link', 'meta', 'base', 'portal', 'noscript', 'set', 'template', 'audio', 'video', 'source', 'track',
  // Outside the profile (form controls, dialogs): the validator drops or replaces them.
  'input', 'select', 'option', 'optgroup', 'textarea', 'datalist', 'fieldset', 'progress', 'meter', 'form', 'dialog']);
const FORBIDDEN_ATTRS = new Set(['srcdoc', 'srcset', 'action', 'formaction', 'ping', 'background', 'nonce', 'target', 'http-equiv']);

const scrollTopSet = setter(Element.prototype, 'scrollTop');
const scrollLeftSet = setter(Element.prototype, 'scrollLeft');

const rectJson = (r: DOMRect, o: { x: number; y: number } | null) => {
  const dx = o?.x ?? 0, dy = o?.y ?? 0;
  return { x: r.x - dx, y: r.y - dy, width: r.width, height: r.height, top: r.top - dy, left: r.left - dx, right: r.right - dx, bottom: r.bottom - dy };
};
const matrixJson = (m: DOMMatrix | null) => (m ? { a: m.a, b: m.b, c: m.c, d: m.d, e: m.e, f: m.f } : null);

const ALWAYS_FORWARD = new Set(['click', 'load', 'error', 'toggle']);
const THROTTLED = new Set(['pointermove', 'mousemove']);
/** What the widget can get: the profile's events (no keyboard, focus, touch, wheel or scroll; dblclick and contextmenu only the renderer's own). */
const ALL_EVENTS = [...EVENT_TYPES];
/** The engine's double click and context menu: never forwarded, the arbiter decides (gesture-policy.ts) and the renderer sends its own. */
const ENGINE_GESTURES = new Set(['dblclick', 'contextmenu']);
/** Compatibility mouse events: they carry no pointerId, so they follow the press the bubble took. */
const MOUSE_COMPAT = new Set(['mousedown', 'mouseup', 'mousemove', 'click']);
const ENTER_LEAVE = ['pointerenter', 'pointerleave', 'mouseenter', 'mouseleave'];
/** Rule 5 of the heavy-box count (paint-policy.ts): a CSS animation or transition from its start event to its end or cancel. */
const PAINT_ANIMATION_EVENTS = ['animationstart', 'animationend', 'animationcancel', 'transitionstart', 'transitionend', 'transitioncancel'];
/** What moves :hover / :active (and a details element's `open`) without an op. */
const PAINT_INPUT_EVENTS = ['pointerover', 'pointerout', 'pointerdown', 'pointerup', 'pointercancel', 'toggle'];
const noop = () => {};
/**
 * Input that belongs to the chat, never to the widget (D4, docs/profile-v1.md):
 * keys and editing are swallowed (no Enter or Space activation), the platform's
 * context menu is cancelled (the bubble's own menu wins), a mouse press neither
 * moves the focus into the widget nor starts a text selection unless it lands on
 * text the widget made selectable (`user-select: text`, D18), and the focus
 * never stays in it.
 */
const SWALLOWED = ['keydown', 'keyup', 'keypress', 'beforeinput', 'input', 'contextmenu', 'mousedown', 'focusin', 'submit', 'selectstart'];

function decodeBase64(b64: string): Uint8ClampedArray<ArrayBuffer> {
  const bin = atob(b64);
  const out = new Uint8ClampedArray(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

export function toBase64(buf: ArrayBuffer): string {
  const bytes = new Uint8Array(buf);
  let bin = '';
  for (let i = 0; i < bytes.length; i += 0x8000) bin += String.fromCharCode.apply(null, Array.from(bytes.subarray(i, i + 0x8000)));
  return btoa(bin);
}

export function createRenderer(env: RendererEnv): RendererCore {
const doc = env.doc;
const root = env.root;
// `DocumentOrShadowRoot` members are separate functions on each prototype: the document's copy throws "Illegal invocation" on a shadow root.
const elementFromPoint = root instanceof Document ? uncurry(Document.prototype.elementFromPoint) : uncurry(ShadowRoot.prototype.elementFromPoint);
let send: Send = () => {};
const post = (msg: RendererToHost, transfer: Transferable[] = []) => send(msg, transfer);
const violation = (detail: string) => post({ type: 'violation', detail });

let HTML: HTMLElement;
let HEAD: HTMLElement;
let BODY: HTMLElement;

const nodes = new Map<number, Node>();
const ids = new WeakMap<Node, number>();
const animations = new AnimationState();

function register(id: number, node: Node) {
  nodes.set(id, node);
  ids.set(node, id);
}

function forget(node: Node) {
  const id = ids.get(node);
  if (id !== undefined) {
    nodes.delete(id);
    ids.delete(node);
    canvases.delete(id);
    if (node instanceof Element) resizeObserver.unobserve(node);
    if (node instanceof HTMLCanvasElement) { canvasPixelsTotal -= canvasPixels.get(node) ?? 0; canvasPixels.delete(node); }
    if (node instanceof HTMLStyleElement) widgetStyles.delete(node);
  }
  for (let c = firstChildOf(node); c; c = nextSiblingOf(c)) forget(c);
}

// ───────────────────────── canvas sizes ─────────────────────────

/**
 * A canvas's bitmap is the page's memory, as large as the widget makes it (four canvases of 16384² grew a browser by
 * ~16 GB): a widget's canvases hold at most this many pixels together (64 MB as RGBA; a canvas over a whole bubble at
 * 3× is ~2.5 M). A size past it is not applied: the canvas keeps the one it had, a new one is 0 × 0.
 */
const CANVAS_PIXELS_MAX = 4096 * 4096;
const canvasPixels = new Map<HTMLCanvasElement, number>();
let canvasPixelsTotal = 0;

/** Charges `el` the size `w` × `h` if the widget's canvases can hold it together; false, and nothing charged, if not. */
function fitCanvas(el: HTMLCanvasElement, w: number, h: number): boolean {
  const others = canvasPixelsTotal - (canvasPixels.get(el) ?? 0);
  if (!(others + w * h <= CANVAS_PIXELS_MAX)) {
    violation(`canvas ${w}×${h}: a widget's canvases hold at most ${CANVAS_PIXELS_MAX} pixels together`);
    return false;
  }
  canvasPixels.set(el, w * h);
  canvasPixelsTotal = others + w * h;
  return true;
}

/** Whether `el` may take `value` (null: none) as its `name`: anything but a canvas's width or height may. */
function canvasSizeFits(el: Element, name: string, value: string | null): boolean {
  if (!(el instanceof HTMLCanvasElement) || (name !== 'width' && name !== 'height')) return true;
  // HTML's rules for non-negative integers; a value they refuse, or none, is the default
  const parsed = /^[\t\n\f\r ]*\+?(\d+)/.exec(value ?? '');
  const n = parsed ? Number(parsed[1]) : name === 'width' ? 300 : 150;
  return fitCanvas(el, name === 'width' ? n : el.width, name === 'height' ? n : el.height);
}

function idOf(node: Node | null): number {
  for (let n = node; n; n = parentNodeOf(n)) {
    const id = ids.get(n);
    if (id !== undefined) return id;
  }
  return 0;
}

// ───────────────────────── ops ─────────────────────────

function applyAttr(el: Element, name: string, value: string) {
  const lower = name.toLowerCase();
  if (lower.startsWith('on') || FORBIDDEN_ATTRS.has(lower)) return violation(`attribute ${name}`);
  // Assigning canvas width/height clears the bitmap even if unchanged; the
  // canvas command stream ('r') is authoritative for resets.
  if (el instanceof HTMLCanvasElement && (lower === 'width' || lower === 'height') && getAttribute(el, lower) === value) return;
  if (!canvasSizeFits(el, lower, value)) return;
  setAttribute(el, name, value);
  if (lower === 'style') widenTouchAction(styleOf(el));
}

// ───────────────────────── touch-action: the engine always pans vertically (touch-action.ts) ─────────────────────────

/** One declaration block: the engine gets the widened `touch-action`, the claim keeps the widget's own (same priority). */
function widenTouchAction(style: CSSStyleDeclaration) {
  const value = style.getPropertyValue('touch-action');
  if (!value) return;
  const priority = style.getPropertyPriority('touch-action');
  style.setProperty(TOUCH_ACTION_CLAIM, value, priority);
  const engine = engineTouchAction(value);
  if (engine !== value) style.setProperty('touch-action', engine, priority);
}

/**
 * The element's own touch-action as the widget declared it: the claim property when the engine's value is that
 * value widened (the element declares it), else the engine's (`auto`: the claim property inherited from an ancestor).
 */
function ownTouchAction(cs: CSSStyleDeclaration): string {
  const own = cs.getPropertyValue(TOUCH_ACTION_CLAIM).trim();
  const words = (v: string) => v.trim().toLowerCase().split(/\s+/).sort().join(' ');
  return own && words(cs.touchAction) === words(engineTouchAction(own)) ? own : cs.touchAction;
}

/** Every rule of a `<style>` element's sheet (none until it is connected), nested rules included. */
function widenSheet(el: Node) {
  if (!(el instanceof HTMLStyleElement)) return;
  const sheet = sheetOf(el) as CSSStyleSheet | null;
  if (!sheet) return;
  const walk = (rules: CSSRuleList) => {
    for (let i = 0; i < rules.length; i++) {
      const r = rules[i] as CSSRule & { style?: CSSStyleDeclaration; cssRules?: CSSRuleList };
      if (r.style) widenTouchAction(r.style);
      if (r.cssRules) walk(r.cssRules);
    }
  };
  walk(sheet.cssRules);
}

function applyOp(op: RenderOp) {
  switch (op.op) {
    case 'el': {
      const tag = op.tag.toLowerCase();
      if (FORBIDDEN_TAGS.has(tag)) return violation(`element <${op.tag}>`);
      let el: Element;
      if (op.adopt) canvasDirty = true;
      if (op.adopt === 'html') el = HTML;
      else if (op.adopt === 'head') el = HEAD;
      else if (op.adopt === 'body') el = BODY;
      else el = op.ns === 'svg' ? createElementNS(doc, SVG_NS, op.tag) : createElement(doc, op.tag);
      // A script's image loads before it is inserted (new Image()), where the root's listeners do not see it.
      if (tag === 'img' && !op.adopt) for (const type of ['load', 'error']) el.addEventListener(type, onDetachedImage);
      register(op.id, el);
      // a canvas is born 300 × 150
      if (el instanceof HTMLCanvasElement && !fitCanvas(el, 300, 150)) { el.width = 0; el.height = 0; }
      for (const [name, value] of op.attrs) applyAttr(el, name, value);
      return;
    }
    case 'text':
      return register(op.id, createTextNode(doc, op.data));
    case 'comment':
      return;
  }
  const node = nodes.get(op.id);
  if (!node) return;
  // What can change the root's or the body's background: their own attributes and styles, any stylesheet.
  if (node === HTML || node === BODY || op.op === 'css' || ((op.op === 'ins' || op.op === 'rm') && node instanceof HTMLStyleElement)) canvasDirty = true;
  switch (op.op) {
    case 'attr':
      applyAttr(node as Element, op.name, op.value);
      if (node === HTML && op.name.toLowerCase() === 'style') applyThemeVariables();
      return;
    case 'rattr':
      if (!canvasSizeFits(node as Element, op.name.toLowerCase(), null)) return;
      removeAttribute(node as Element, op.name);
      if (node === HTML && op.name.toLowerCase() === 'style') applyThemeVariables();
      return;
    case 'data':
      return setCharData(node, op.data);
    case 'ins': {
      if (node === HTML || node === HEAD || node === BODY) return;
      if (op.parent === 0) {
        const p = parentNodeOf(node);
        if (p) removeChild(p, node);
        return;
      }
      const parent = nodes.get(op.parent);
      if (!parent) return;
      const ref = op.before === null ? null : nodes.get(op.before) ?? null;
      insertBefore(parent, node, ref && parentNodeOf(ref) === parent ? ref : null);
      // A <style> gets its sheet once connected.
      widenSheet(node);
      return;
    }
    case 'rm': {
      if (node === HTML || node === HEAD || node === BODY) return;
      const p = parentNodeOf(node);
      if (p) removeChild(p, node);
      forget(node);
      return;
    }
    case 'style': {
      // The host's theme variables on the root are not the widget's to change (it can shadow them on its own elements).
      if (node === HTML && THEME_VARIABLES.includes(op.name)) return;
      const style = styleOf(node as Element);
      if (op.name === 'touch-action') style.removeProperty(TOUCH_ACTION_CLAIM);
      if (op.value === '') style.removeProperty(op.name);
      else style.setProperty(op.name, op.value, op.important ? 'important' : '');
      if (op.name === 'touch-action') widenTouchAction(style);
      return;
    }
    case 'css':
      // Sanitized stylesheet text for a <style> element.
      if (localNameOf(node) === 'style') { setTextContent(node, op.text); widenSheet(node); }
      return;
    case 'prop':
      return applyProp(node as any, op.name, op.value);
    case 'call':
      return applyCall(node as any, op.method, op.args);
    case 'canvas':
      if (node instanceof HTMLCanvasElement) replay(op.id, node, op.cmds);
      return;
  }
}

function applyProp(el: Element, name: string, value: string | number | boolean) {
  switch (name) {
    case 'scrollTop':
      return scrollTopSet(el, value);
    case 'scrollLeft':
      return scrollLeftSet(el, value);
  }
}

/** The profile's element methods (no focus, selection, dialogs, popovers or scrollIntoView: docs/profile-v1.md). */
const METHOD_IMPL: Record<string, (el: any, a: any[]) => void> = {
  scrollTo: (el, a) => Element.prototype.scrollTo.call(el, a[0], a[1]),
  scrollBy: (el, a) => Element.prototype.scrollBy.call(el, a[0], a[1]),
  setPointerCapture: (el, a) => {
    // Only a pointer whose press began on the widget (`ownPointers`): the shim's check runs in the VM, which the widget
    // can lie to, and on the Shadow DOM renderer a pointer pressed on the chat would be taken from the control under it.
    if (!ownPointers.has(a[0])) return;
    try { Element.prototype.setPointerCapture.call(el, a[0]); } catch {}
  },
  releasePointerCapture: (el, a) => {
    try { Element.prototype.releasePointerCapture.call(el, a[0]); } catch {}
  },
  observeResize: (el) => resizeObserver.observe(el),
  unobserveResize: (el) => resizeObserver.unobserve(el),
};

function applyCall(el: Element, method: string, args: unknown[]) {
  const impl = Object.prototype.hasOwnProperty.call(METHOD_IMPL, method) ? METHOD_IMPL[method] : null;
  if (impl) impl(el, args);
}

// ───────────────────────── canvas replay ─────────────────────────

interface CanvasState {
  ctx: CanvasRenderingContext2D;
  objects: Map<number, CanvasGradient | CanvasPattern | null>;
  paths: Map<number, Path2D>;
}
const canvases = new Map<number, CanvasState>();

function canvasState(id: number, el: HTMLCanvasElement): CanvasState | null {
  let st = canvases.get(id);
  if (!st) {
    const ctx = HTMLCanvasElement.prototype.getContext.call(el, '2d') as CanvasRenderingContext2D | null;
    if (!ctx) return null;
    st = { ctx, objects: new Map(), paths: new Map() };
    canvases.set(id, st);
  }
  return st;
}

function resolveArg(st: CanvasState, a: any): any {
  if (a && typeof a === 'object' && !Array.isArray(a)) {
    if ('g' in a) return st.objects.get(a.g) ?? undefined;
    if ('p' in a) return st.paths.get(a.p) ?? undefined;
    if ('n' in a) return nodes.get(a.n);
    if ('b64' in a) {
      try { return new ImageData(decodeBase64(a.b64), a.w, a.h); } catch { return undefined; }
    }
  }
  return a;
}

function replay(id: number, el: HTMLCanvasElement, cmds: CanvasCmd[]) {
  const st = canvasState(id, el);
  if (!st) return;
  const ctx = st.ctx as any;
  for (const cmd of cmds) {
    try {
      const [kind, name, ...rest] = cmd as [string, any, ...any[]];
      switch (kind) {
        case 'c': {
          const args = rest.map((a) => resolveArg(st, a));
          if (args.includes(undefined)) break;
          ctx[name](...args);
          break;
        }
        case 's':
          ctx[name] = resolveArg(st, rest[0]);
          break;
        case 'r':
          // a size past the widget's canvas pixels still clears the bitmap
          if (!fitCanvas(el, name, rest[0])) { el.width = el.width; break; }
          el.width = name;
          el.height = rest[0];
          break;
        case 'g': {
          const [type, ...args] = rest;
          let obj: CanvasGradient | CanvasPattern | null = null;
          if (type === 'linear') obj = ctx.createLinearGradient(...args);
          else if (type === 'radial') obj = ctx.createRadialGradient(...args);
          else if (type === 'conic') obj = ctx.createConicGradient(...args);
          else if (type === 'pattern') {
            const src = resolveArg(st, args[0]);
            if (src) obj = ctx.createPattern(src, args[1] ?? 'repeat');
          }
          st.objects.set(name, obj);
          break;
        }
        case 'gs':
          (st.objects.get(name) as CanvasGradient | undefined)?.addColorStop(rest[0], rest[1]);
          break;
        case 'pn':
          st.paths.set(name, typeof rest[0] === 'string' ? new Path2D(rest[0]) : new Path2D());
          break;
        case 'pc': {
          const path = st.paths.get(name) as any;
          const [method, ...args] = rest;
          if (path) path[method](...args.map((a: any) => resolveArg(st, a)));
          break;
        }
      }
    } catch {
      // A failing draw call (bad argument) must not abort the rest of the frame.
    }
  }
}

// ───────────────────────── queries ─────────────────────────

let scratch: CanvasRenderingContext2D | null = null;

function answer(q: Query): { result: unknown; binary?: ArrayBuffer } {
  const el = 'id' in q ? (nodes.get(q.id) as any) : null;
  if ('id' in q && !(el instanceof Element)) return { result: { error: 'no such element' } };
  switch (q.q) {
    case 'rect':
      return { result: rectJson(getBoundingClientRect(el), env.origin()) };
    case 'rects': {
      const o = env.origin();
      return { result: Array.from(getClientRects(el), (r) => rectJson(r, o)) };
    }
    case 'box': {
      const g = (name: string) => getter(Element.prototype, name)(el);
      const html = el instanceof HTMLElement;
      const h = (name: string) => (html ? getter(HTMLElement.prototype, name)(el) : 0);
      return {
        result: {
          offsetWidth: h('offsetWidth'), offsetHeight: h('offsetHeight'), offsetTop: h('offsetTop'), offsetLeft: h('offsetLeft'),
          offsetParent: html ? idOf(h('offsetParent')) : 0,
          clientWidth: g('clientWidth'), clientHeight: g('clientHeight'), clientTop: g('clientTop'), clientLeft: g('clientLeft'),
          scrollWidth: g('scrollWidth'), scrollHeight: g('scrollHeight'), scrollTop: g('scrollTop'), scrollLeft: g('scrollLeft'),
        },
      };
    }
    case 'cs': {
      const cs = getComputedStyle(el, q.pseudo ?? null);
      // The widget sees its own touch-action, not the engine's widened one.
      const touchAction = () => ownTouchAction(cs);
      if (q.prop) return { result: q.prop === 'touch-action' ? touchAction() : cs.getPropertyValue(q.prop) };
      const out: Record<string, string> = {};
      for (let i = 0; i < cs.length; i++) if (cs[i] !== TOUCH_ACTION_CLAIM) out[cs[i]] = cs.getPropertyValue(cs[i]);
      out['touch-action'] = touchAction();
      return { result: out };
    }
    case 'svg': {
      try {
        const args = q.args ?? [];
        switch (q.method) {
          case 'getBBox': {
            const b = (el as SVGGraphicsElement).getBBox();
            return { result: { x: b.x, y: b.y, width: b.width, height: b.height } };
          }
          case 'getCTM':
            return { result: matrixJson((el as any)[q.method]()) };
          case 'getScreenCTM': {
            const m = matrixJson((el as SVGGraphicsElement).getScreenCTM());
            const o = env.origin();
            if (m && o) { m.e -= o.x; m.f -= o.y; }
            return { result: m };
          }
          case 'getPointAtLength': {
            const p = (el as SVGGeometryElement).getPointAtLength(args[0] ?? 0);
            return { result: { x: p.x, y: p.y } };
          }
          case 'getComputedTextLength': case 'getTotalLength': case 'getNumberOfChars':
            return { result: (el as any)[q.method]() };
          case 'getSubStringLength':
            return { result: (el as SVGTextContentElement).getSubStringLength(args[0] ?? 0, args[1] ?? 0) };
        }
      } catch (e) {
        return { result: { error: String(e) } };
      }
      return { result: { error: 'unsupported' } };
    }
    case 'measureText': {
      scratch ??= HTMLCanvasElement.prototype.getContext.call(createElement(doc, 'canvas'), '2d') as CanvasRenderingContext2D;
      scratch.font = '10px sans-serif';
      scratch.font = q.font;
      const m = scratch.measureText(q.text);
      return {
        result: {
          width: m.width, actualBoundingBoxLeft: m.actualBoundingBoxLeft, actualBoundingBoxRight: m.actualBoundingBoxRight,
          actualBoundingBoxAscent: m.actualBoundingBoxAscent, actualBoundingBoxDescent: m.actualBoundingBoxDescent,
          fontBoundingBoxAscent: m.fontBoundingBoxAscent, fontBoundingBoxDescent: m.fontBoundingBoxDescent,
        },
      };
    }
    case 'imageData': {
      const st = canvasState(q.id, el);
      if (!st) return { result: { error: 'no context' } };
      const data = st.ctx.getImageData(q.x, q.y, q.w, q.h);
      const buf = data.data.buffer.slice(0);
      return { result: { width: data.width, height: data.height }, binary: buf };
    }
    case 'canvasCall': {
      const st = canvasState(q.id, el);
      if (!st) return { result: { error: 'no context' } };
      const args = (q.args ?? []).map((a) => resolveArg(st, a));
      try {
        switch (q.method) {
          case 'isPointInPath': case 'isPointInStroke':
            return { result: (st.ctx as any)[q.method](...args) };
          case 'getTransform':
            return { result: matrixJson(st.ctx.getTransform()) };
          case 'getLineDash':
            return { result: st.ctx.getLineDash() };
          case 'toDataURL':
            return { result: HTMLCanvasElement.prototype.toDataURL.call(el, ...(args as [string?, number?])) };
        }
      } catch (e) {
        return { result: { error: String(e) } };
      }
      return { result: { error: 'unsupported' } };
    }
    case 'elementFromPoint': {
      const o = env.origin();
      return { result: idOf(elementFromPoint(root, q.x + (o?.x ?? 0), q.y + (o?.y ?? 0))) };
    }
    case 'validity': {
      const v = (el as HTMLInputElement).validity;
      if (!v) return { result: { valid: true, message: '' } };
      const out: Record<string, unknown> = { message: (el as HTMLInputElement).validationMessage };
      for (const k of ['valid', 'valueMissing', 'typeMismatch', 'patternMismatch', 'tooLong', 'tooShort', 'rangeUnderflow', 'rangeOverflow', 'stepMismatch', 'badInput', 'customError'] as const) out[k] = v[k];
      return { result: out };
    }
    case 'matchMedia':
      return { result: { matches: env.matchMedia(q.query) } };
    case 'viewport':
      return { result: env.viewport() };
    case 'anims':
      return { result: { list: cssAnimationList(animations, el, q.subtree === true, idOf) } };
  }
}

// ───────────────────────── events ─────────────────────────

let interest = new Set<string>();
/** "Learned preventDefault": keys reported by the sandbox for which the default is cancelled synchronously. */
let preventKeys = new Set<string>();
const pendingMoves = new Map<string, WidgetEvent>();
let moveFrame = 0;
/** Pending `requestFrame` from the host (one at a time: the shim coalesces its callbacks into one request). */
let frameReq = 0;
/** Set by the `frozen` op: the runtime is gone, the last render stays as a static snapshot. */
let frozen = false;

function serialize(e: Event, targetId: number): WidgetEvent {
  const ev: WidgetEvent = { type: e.type, targetId };
  const t = e.target as any;
  const o = env.origin();
  if (e instanceof MouseEvent) {
    ev.clientX = e.clientX; ev.clientY = e.clientY; ev.offsetX = e.offsetX; ev.offsetY = e.offsetY;
    ev.pageX = e.pageX; ev.pageY = e.pageY; ev.movementX = e.movementX; ev.movementY = e.movementY;
    // Shadow root in the chat page: client coordinates relative to the widget, and its "page" never scrolls.
    if (o) { ev.clientX -= o.x; ev.clientY -= o.y; ev.pageX = ev.clientX; ev.pageY = ev.clientY; }
    ev.button = e.button; ev.buttons = e.buttons; ev.detail = e.detail;
    if (e.relatedTarget) ev.relatedId = idOf(e.relatedTarget as Node);
  }
  if (e instanceof PointerEvent) {
    ev.pointerId = e.pointerId; ev.pointerType = e.pointerType; ev.isPrimary = e.isPrimary; ev.pressure = e.pressure;
  }
  if (e instanceof MouseEvent) {
    const m = e as MouseEvent;
    ev.modifiers = (m.altKey ? 1 : 0) | (m.ctrlKey ? 2 : 0) | (m.metaKey ? 4 : 0) | (m.shiftKey ? 8 : 0);
  }
  if (t instanceof HTMLImageElement && e.type === 'load') { ev.naturalWidth = t.naturalWidth; ev.naturalHeight = t.naturalHeight; }
  if (t instanceof HTMLDetailsElement && e.type === 'toggle') ev.open = t.open;
  return ev;
}

/**
 * What of the press that starts on `el` the widget claims (gesture-policy.ts
 * `claimOf`, docs/sdk-v1.md §5.5): from the `touch-action` the widget gave `el`
 * and its ancestors (`none` claims horizontal drags and holds, a value without a
 * horizontal pan claims horizontal drags; vertical drags are never claimed) and
 * a pointerdown / touchstart the sandbox has learned to prevent on this target
 * (horizontal drags). The host tells the client (`onGestureClaim`).
 */
function gestureClaim(el: Element | null, targetId: number): GestureClaim {
  const values: string[] = [];
  // Up to the widget's <html> itself: touch-action on an ancestor covers its descendants, as in CSS. The widget's own
  // values (TOUCH_ACTION_CLAIM inherits, so an element repeats its nearest declaring ancestor's: the same claim).
  for (let n: Element | null = el ?? HTML; n; n = n === HTML ? null : (parentNodeOf(n) as Element | null)) {
    if (!(n instanceof Element)) break;
    const cs = getComputedStyle(n);
    values.push(cs.getPropertyValue(TOUCH_ACTION_CLAIM).trim() || cs.touchAction);
  }
  return claimOf(values, preventKeys.has(`${targetId}:pointerdown`) || preventKeys.has(`${targetId}:touchstart`));
}

// ───────────────────────── presses: the widget or the bubble (gesture-policy.ts) ─────────────────────────

const arbiter = new GestureArbiter();
/** `arbitrate` from the host: null while the client's own recognizers take the bubble's presses (the native WebViews). */
let bubbleMode: { touch: boolean; rtl: boolean } | null = null;
let rtl = false;
/** The primary pointer the arbiter follows, and its last event as the widget saw it (the base of what the renderer sends for it). */
let pressPointer = -1;
let pressEv: WidgetEvent | null = null;
let holdTimer = 0;
/** A pointer the bubble took: nothing more of it reaches the widget. */
let takenPointer = -1;
/** After a taken press's pointerup: its compatibility mouseup / click are not the widget's either. */
let dropClick = false;
/** A double tap the widget won: its `dblclick` follows the second tap's click. */
let pendingDblclick: WidgetEvent | null = null;
/** Reproducing a phone (`touch`), a mouse press is a finger's for the widget too: `pointerType` 'touch', also on its click. */
let pressAsTouch = false;
let clickAsTouch = false;

function pressPoint(e: PointerEvent): { x: number; y: number; cx: number; cy: number } {
  const o = env.origin();
  const cx = e.clientX - (o?.x ?? 0), cy = e.clientY - (o?.y ?? 0);
  return { x: rtl ? HTML.clientWidth - cx : cx, y: cy, cx, cy };
}

function clearHold() {
  if (holdTimer) { clearTimeout(holdTimer); holdTimer = 0; }
}

function scheduleHold() {
  clearHold();
  const at = arbiter.holdDeadline();
  if (at === null) return;
  // The arbiter's clock is the events' timeStamp, which shares performance.now()'s origin.
  holdTimer = setTimeout(() => {
    holdTimer = 0;
    if (frozen) return;
    const v = arbiter.tick(Math.max(performance.now(), at));
    if (v && pressEv) settle(v, pressEv);
  }, Math.max(0, at - performance.now()) + 1) as unknown as number;
}

/**
 * Feeds a primary pointer's down / move / up / cancel to the arbiter (posting the claim at pointerdown) and answers
 * its verdict, or null for an event the arbiter does not follow.
 */
function track(e: PointerEvent, targetEl: Element | null, targetId: number, ev: WidgetEvent): GestureVerdict | null {
  const type = e.type;
  if (type === 'pointerdown') {
    if (!e.isPrimary) return null;
    dropClick = false;
    pendingDblclick = null;
    const claim = gestureClaim(targetEl, targetId);
    post({ type: 'gesture', wants: claim.y, x: claim.x, y: claim.y, hold: claim.hold });
    // Reproducing a phone, the mouse's primary button is a finger; its secondary button stays the menu click.
    const kind: PointerKind = bubbleMode?.touch && e.button !== 2 ? 'touch' : e.pointerType === 'mouse' ? 'mouse' : e.pointerType === 'pen' ? 'pen' : 'touch';
    pressAsTouch = clickAsTouch = kind === 'touch' && e.pointerType === 'mouse';
    if (pressAsTouch) ev.pointerType = 'touch';
    const p = pressPoint(e);
    pressPointer = e.pointerId;
    pressEv = ev;
    const v = arbiter.down(e.timeStamp, p.x, p.y, claim, kind, e.button);
    scheduleHold();
    return v;
  }
  if (e.pointerId !== pressPointer) return null;
  const p = pressPoint(e);
  if (pressAsTouch) ev.pointerType = 'touch';
  pressEv = ev;
  if (type === 'pointermove') return arbiter.move(e.timeStamp, p.x, p.y);
  clearHold();
  pressPointer = -1;
  pressAsTouch = false;
  if (type === 'pointerup') return arbiter.up(e.timeStamp, p.x, p.y);
  arbiter.cancel();
  return null;
}

/**
 * Acts on a verdict once the event that produced it has gone to the widget: the widget's own `contextmenu` / `dblclick`
 * for a hold / double tap it claims, and with the renderer playing the bubble, the end of a press the bubble takes.
 */
function settle(v: GestureVerdict, base: WidgetEvent) {
  if (v.event === 'contextmenu') forward({ ...base, type: 'contextmenu' }, true);
  else if (v.event === 'dblclick') pendingDblclick = { ...base, type: 'dblclick', detail: 2 };
  if (!v.cancel || !bubbleMode || base.pointerId === undefined) return;
  forward({ ...base, type: 'pointercancel', button: -1, buttons: 0 }, true);
  takenPointer = base.pointerId;
  ownPointers.delete(base.pointerId);
  clearHold();
  pressPointer = -1;
  pressAsTouch = clickAsTouch = false;
  arbiter.cancel();
  post({ type: 'bubble', action: v.action ?? 'menu', gesture: v.gesture, x: base.clientX ?? 0, y: base.clientY ?? 0, edge: v.edge === true });
}

/** Queues or posts one event for the widget, if it listens for that type (`ALWAYS_FORWARD` aside). */
function forward(ev: WidgetEvent, trusted: boolean) {
  if (!ALWAYS_FORWARD.has(ev.type) && !interest.has(ev.type)) return;
  if (THROTTLED.has(ev.type)) {
    const key = ev.type + ':' + (ev.pointerId ?? 0);
    const previous = pendingMoves.get(key);
    if (previous) {
      ev.movementX = (previous.movementX ?? 0) + (ev.movementX ?? 0);
      ev.movementY = (previous.movementY ?? 0) + (ev.movementY ?? 0);
    }
    pendingMoves.set(key, ev);
    if (!moveFrame) {
      moveFrame = requestAnimationFrame(() => {
        moveFrame = 0;
        for (const m of pendingMoves.values()) post({ type: 'event', ev: m, trusted: false });
        pendingMoves.clear();
      });
    }
    return;
  }
  // Keep ordering: a queued move must not arrive after the up/click that followed it.
  if (pendingMoves.size) {
    for (const m of pendingMoves.values()) post({ type: 'event', ev: m, trusted: false });
    pendingMoves.clear();
  }
  post({ type: 'event', ev, trusted });
}

function onDetachedImage(e: Event) {
  if (!root.contains(e.currentTarget as Node)) onEvent(e);
}

function onEvent(e: Event) {
  const type = e.type;
  const target = e.target instanceof Node ? e.target : null;
  const targetEl = target instanceof Element ? target : null;

  // Unconditional navigation cancelling. Nothing the validator emits can
  // navigate, but the renderer does not rely on that. A `#fragment` link does
  // not scroll either: the widget does not scroll, and on the Shadow DOM
  // renderer the page that would scroll is the chat.
  if (type === 'click' && targetEl && closest(targetEl, 'a, area')) e.preventDefault();
  // A click without a pointer press (detail 0: Enter or Space on a focused button, assistive technology) is not input the widget gets.
  if (type === 'click' && (e as MouseEvent).detail === 0 && e.isTrusted) {
    e.preventDefault();
    return;
  }

  // A frozen widget still cancels navigation (above) but forwards nothing: there is no runtime to deliver to.
  if (frozen) return;
  // Only events dispatched to (or through) widget nodes are of interest.
  const targetId = target === root || target === null ? ids.get(HTML) ?? 0 : idOf(target);
  if (!targetId) return;
  if (preventKeys.size && e.cancelable && preventKeys.has(`${targetId}:${type}`)) e.preventDefault();
  if (ENGINE_GESTURES.has(type)) return;
  let verdict: GestureVerdict | null = null;
  let ev: WidgetEvent | null = null;
  if (e instanceof PointerEvent && (type === 'pointerdown' || type === 'pointermove' || type === 'pointerup' || type === 'pointercancel')) {
    // The end of a press for the host's haptic gate (the start is the claim at pointerdown), listened to or not.
    if ((type === 'pointerup' || type === 'pointercancel') && e.isPrimary && e.isTrusted) post({ type: 'release' });
    if (e.pointerId === takenPointer) {
      // The bubble's press: nothing more of it, and not the click its release would make.
      if (type === 'pointerup' || type === 'pointercancel') { takenPointer = -1; dropClick = type === 'pointerup'; }
      return;
    }
    ev = serialize(e, targetId);
    verdict = track(e, targetEl, targetId, ev);
    // The bubble takes it at the release (a double tap's second tap): the widget gets pointercancel instead of pointerup.
    if (verdict?.cancel && bubbleMode && type === 'pointerup') {
      settle(verdict, ev);
      takenPointer = -1;
      dropClick = true;
      return;
    }
    // A finger makes a click only for a tap: reproducing a phone with a mouse, a drag or a hold ends without one.
    if (type === 'pointerup' && bubbleMode?.touch && verdict && (verdict.gesture === 'drag-x' || verdict.gesture === 'drag-y' || verdict.gesture === 'hold')) dropClick = true;
  } else if (MOUSE_COMPAT.has(type) && (takenPointer !== -1 || (dropClick && type !== 'mousemove'))) return;
  // A press begun off the widget and let go over it (on the Shadow DOM renderer, a drag from the chat) is no press of
  // the widget's: its release reaches it, but is no gesture. A release is that of the pointer whose buttons changed last.
  // A finger's or a pen's press may still become the chat's scroll, which ends it in pointercancel: its pointerdown is
  // no gesture either, its release is (HTML's activation-triggering input events), and neither is a mouse event of a
  // finger or a pen still down: the mousedown a pen's contact makes at once, its barrel button pressed or let go with the
  // tip down (a mouse pressed while a finger is held makes its own). The type is the input's own: a mouse playing a
  // finger (`pressAsTouch`) scrolls nothing.
  const mayScroll = type === 'pointerdown' ? e instanceof PointerEvent && isFingerOrPen(e) : (type === 'mousedown' || type === 'mouseup') && changedFingerDown;
  const trusted = e.isTrusted && !mayScroll && (changedOwn || (type !== 'pointerup' && type !== 'mouseup'));
  if (ev) forward(ev, trusted);
  else if (ALWAYS_FORWARD.has(type) || interest.has(type)) {
    const out = serialize(e, targetId);
    if (type === 'click' && clickAsTouch && out.pointerType === 'mouse') { out.pointerType = 'touch'; clickAsTouch = false; }
    forward(out, trusted);
  }
  if (verdict && ev) settle(verdict, ev);
  if (type === 'click' && pendingDblclick) {
    const dbl = pendingDblclick;
    pendingDblclick = null;
    forward({ ...serialize(e, targetId), type: 'dblclick', detail: 2, pointerId: dbl.pointerId, pointerType: dbl.pointerType }, e.isTrusted);
  }
}

// ───────────────────────── size reporting ─────────────────────────

let lastSize = '';
let lastViewport = '';
function reportSize() {
  const viewport = env.viewport();
  const msg: RendererToHost = { type: 'size', width: HTML.clientWidth, height: env.docHeight(), ...viewport };
  const key = JSON.stringify(msg);
  if (key !== lastSize) {
    lastSize = key;
    post(msg);
  }
  // @media, @container and viewport units follow the bubble's size: every box is counted again
  const vp = viewport.innerWidth + 'x' + viewport.innerHeight;
  if (vp !== lastViewport) { lastViewport = vp; paintEverything(); }
  if (paintPending && isConnectedOf(HTML)) { paintPending = false; schedulePaint(); }
}

const resizeObserver = new ResizeObserver((entries) => {
  const out: { id: number; width: number; height: number }[] = [];
  for (const entry of entries) {
    const id = ids.get(entry.target);
    if (id !== undefined) out.push({ id, width: entry.contentRect.width, height: entry.contentRect.height });
  }
  if (out.length) post({ type: 'resize', entries: out });
});

// ───────────────────────── heavy boxes (paint-policy.ts, docs/decisions.md "Heavy boxes") ─────────────────────────
//
// Before a frame is drawn the renderer knows how many of the widget's boxes are heavy (a blurred shadow, a filter, a
// backdrop-filter, opacity below 1, or an animation of one of them): past 16 (2 with backdrop-filter) it draws the
// widget without those effects and reports `limit`; the host stops it. The count is kept incrementally: an applied
// batch, a pointer that moves :hover / :active and an animation event mark the boxes they can reach, and one count per
// frame (requestAnimationFrame, before the frame is drawn) recomputes those boxes from their computed values; a
// stylesheet, the theme, the safe area and the viewport recompute every box. Under `compat` nothing is counted.

/** Counted unless the host says `compat` (fail closed: before the host's `policy`, and for any other value). */
let paintOn = true;
/** `policy.paintReports` (tooling): post the counts whenever they change. */
let paintReports = false;
let lastPaintReport = '';
const paint = new PaintCounter<number>();
/** Elements whose computed values, and their subtrees', may have changed since the last count. */
const paintDirty = new Set<Element>();
/** Elements whose own values may have changed (the chain a press goes through, when only the UA layer's `button:active` can match). */
const paintDirtySelf = new Set<Element>();
/** Every box is recomputed at the next count (a stylesheet, the theme, the safe area, the viewport; the first count). */
let paintAll = true;
let paintFrame = 0;
/** Changes wait for a count while the tree is off the page (a Shadow DOM renderer built before it is attached). */
let paintPending = false;
/** Past a limit: the widget is drawn without effects, takes no more ops, and waits for the host's `frozen`. */
let paintStopped = false;
/** What the widget's stylesheets use: how far a change reaches beyond the element and its subtree; `styleAttr`: a selector on the style attribute. */
let paintSheets = { siblings: false, structural: false, hover: false, active: false, styleAttr: false };
/**
 * Inline properties that can change whether a box is heavy, on the element or below it (a custom property can feed
 * `opacity: var(--x)`; prefixed aliases match too); any other inline property (transform, left, background, …)
 * cannot, unless a selector reads the style attribute. Animations and transitions it starts are rule 5, their events.
 */
const PAINT_STYLE_PROPS = /^--|opacity|filter|box-shadow|^display$|^all$/i;
/** The same in a whole style attribute (a declaration of one of them; an escape may spell one). */
const PAINT_STYLE_TEXT = /--|\\|(?:opacity|filter|box-shadow|display|all)\s*:/i;
/** Elements whose inline style may declare one of them (an over-estimate: set by an op that wrote one, cleared when the attribute is replaced). */
const inlineHeavy = new WeakSet<Element>();
/** The elements a `::before` / `::after` rule may match (its selectors without the pseudo-element): only theirs are computed. */
let pseudoSelector = '';
let paintSheetsDirty = true;
const widgetStyles = new Set<HTMLStyleElement>();
const styleConnected = new WeakMap<HTMLStyleElement, boolean>();
/** The renderer's Web Animations counted under rule 5: animation id → the box key. */
const scriptedCounted = new Map<number, number>();
let scriptedPaint = new WeakMap<Animation, BoxPaint | null>();
const scriptedListened = new WeakSet<Animation>();
/** Box keys: the element, its ::before, its ::after. */
const BEFORE = 1, AFTER = 2;
const backdropOf = (cs: CSSStyleDeclaration) => cs.getPropertyValue('backdrop-filter') || cs.getPropertyValue('-webkit-backdrop-filter');

function schedulePaint() {
  if (paintFrame || !paintOn || paintStopped || frozen) return;
  paintFrame = requestAnimationFrame(countPaint);
}

/** `el` (and everything under it) may have changed; with a sibling combinator or a structural pseudo-class in the widget's sheets, its siblings too. */
function paintChanged(el: Node | null | undefined, siblings = false) {
  if (!paintOn || paintStopped || !(el instanceof Element)) return;
  const parent = siblings && (paintSheets.siblings || paintSheets.structural) ? parentNodeOf(el) : null;
  paintDirty.add(parent instanceof Element && ids.has(parent) ? parent : el);
  schedulePaint();
}

function paintEverything() {
  if (!paintOn || paintStopped) return;
  paintAll = true;
  schedulePaint();
}

/** Before an op: a node about to leave its place (rm, or ins of a node already in the tree) takes its boxes with it. */
function paintBefore(op: RenderOp) {
  if (op.op !== 'rm' && op.op !== 'ins') return;
  const node = nodes.get(op.id);
  if (!node || node === HTML || node === HEAD || node === BODY) return;
  const parent = parentNodeOf(node);
  if (!parent) return;
  if (node instanceof Element) clearBoxes(node);
  // its old siblings may match differently (`+`, `~`, :nth-child(), :last-child, :empty)
  if (paintSheets.siblings || paintSheets.structural) paintChanged(parent);
}

/** After an op: what it can have changed (the subtree of an element whose attribute or style changed, an inserted subtree). */
function paintAfter(op: RenderOp) {
  switch (op.op) {
    case 'el': {
      const el = nodes.get(op.id);
      if (el instanceof HTMLStyleElement) widgetStyles.add(el);
      if (el instanceof Element) for (const [name, value] of op.attrs) if (name.toLowerCase() === 'style' && PAINT_STYLE_TEXT.test(value)) inlineHeavy.add(el);
      if (op.adopt) paintChanged(el);
      return;
    }
    case 'style': {
      if (!paintSheets.styleAttr && !PAINT_STYLE_PROPS.test(op.name)) return;
      const el = nodes.get(op.id);
      if (el instanceof Element) inlineHeavy.add(el);
      paintChanged(el, true);
      return;
    }
    case 'attr': case 'rattr': {
      const el = nodes.get(op.id);
      if (!(el instanceof Element)) return;
      if (el instanceof HTMLStyleElement) { paintSheetsDirty = true; paintEverything(); return; }
      // a style attribute that neither had nor has a declaration of a heavy property changes no box (as a style op)
      if (op.name.toLowerCase() === 'style') {
        const had = inlineHeavy.has(el), has = op.op === 'attr' && PAINT_STYLE_TEXT.test(op.value);
        if (has) inlineHeavy.add(el);
        else inlineHeavy.delete(el);
        if (!had && !has && !paintSheets.styleAttr) return;
      }
      paintChanged(el, true);
      return;
    }
    case 'ins': {
      if (op.parent === 0) return;
      const node = nodes.get(op.id);
      if (node instanceof Element) paintChanged(node, true);
      else if (node && paintSheets.structural) paintChanged(parentNodeOf(node), true);
      return;
    }
    case 'data':
      // :empty
      if (paintSheets.structural) paintChanged(parentNodeOf(nodes.get(op.id) ?? null), true);
      return;
    case 'css':
      paintSheetsDirty = true;
      paintEverything();
      return;
  }
}

/** The widget's stylesheets, read again when one changed or was connected or removed: what a change reaches, and which elements may have pseudo-element boxes. */
function refreshSheets() {
  let changed = paintSheetsDirty;
  for (const el of widgetStyles) {
    const on = isConnectedOf(el) as boolean;
    if (styleConnected.get(el) !== on) { styleConnected.set(el, on); changed = true; }
  }
  if (!changed) return;
  paintSheetsDirty = false;
  paintAll = true;
  const flags = { siblings: false, structural: false, hover: false, active: false, styleAttr: false };
  const pseudo: string[] = [];
  const inspect = (selector: string) => {
    const bare = selector.replace(/"(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*'/g, '""').replace(/\[[^\]]*\]/g, '[]');
    if (/[+~]/.test(bare.replace(/:nth-[a-z-]+\([^)]*\)/gi, ''))) flags.siblings = true;
    if (/:(nth-|first-child|last-child|only-child|first-of-type|last-of-type|only-of-type|empty)/i.test(bare)) flags.structural = true;
    if (/:hover/i.test(bare)) flags.hover = true;
    if (/:active/i.test(bare)) flags.active = true;
    if (/\[\s*style\b/i.test(selector)) flags.styleAttr = true;
    if (!/::?(before|after)\b/i.test(bare)) return;
    // the selectors of the list that name a pseudo-element, without it: a superset of the elements it can match
    let depth = 0, from = 0;
    const parts: string[] = [];
    for (let i = 0; i <= selector.length; i++) {
      const c = selector[i];
      if (c === '(') depth++;
      else if (c === ')') depth--;
      else if ((c === ',' && depth === 0) || c === undefined) { parts.push(selector.slice(from, i)); from = i + 1; }
    }
    for (const part of parts) {
      if (!/::?(before|after)\b/i.test(part)) continue;
      pseudo.push(part.replace(/(^|[\s>+~(,])::?(before|after)\b/gi, '$1*').replace(/::?(before|after)\b/gi, '').trim() || '*');
    }
  };
  const walk = (rules: CSSRuleList) => {
    for (let i = 0; i < rules.length; i++) {
      const r = rules[i] as CSSRule & { selectorText?: string; cssRules?: CSSRuleList };
      if (typeof r.selectorText === 'string') inspect(r.selectorText);
      if (r.cssRules) walk(r.cssRules);
    }
  };
  for (const el of widgetStyles) {
    const sheet = sheetOf(el) as CSSStyleSheet | null;
    if (sheet) try { walk(sheet.cssRules); } catch {}
  }
  paintSheets = flags;
  pseudoSelector = pseudo.join(',');
  if (pseudoSelector) try { matches(HTML, pseudoSelector); } catch { pseudoSelector = '*'; }
}

/** The boxes of `el` and of everything under it are gone (display: none, removed, moved away). */
function clearBoxes(el: Element) {
  const id = ids.get(el);
  if (id !== undefined) { paint.removeBox(id * 4); paint.removeBox(id * 4 + BEFORE); paint.removeBox(id * 4 + AFTER); }
  for (let c = firstChildOf(el); c; c = nextSiblingOf(c)) if (c instanceof Element) clearBoxes(c);
}

/** A `<button>` that is disabled or held down: its opacity is the button's own dimming, not a heavy effect (paint-policy.ts). */
function buttonState(el: Element): 'disabled' | 'pressed' | undefined {
  if (!(el instanceof HTMLButtonElement)) return undefined;
  if (getAttribute(el, 'disabled') !== null) return 'disabled';
  return matches(el, ':active') ? 'pressed' : undefined;
}

/** What a box adds, from its computed values; `el` for an element's own box (a pseudo-element's has none). */
function stylePaint(cs: CSSStyleDeclaration, display: string, pseudo: boolean, el?: Element): BoxPaint | null {
  return boxPaint({ display, content: pseudo ? cs.content : undefined, boxShadow: cs.boxShadow, filter: cs.filter, backdropFilter: backdropOf(cs), opacity: cs.opacity, button: el ? buttonState(el) : undefined }, pseudo);
}

function pseudoPaint(el: Element, key: number, which: '::before' | '::after') {
  const cs = getComputedStyle(el, which);
  paint.setBox(key, stylePaint(cs, cs.display, true));
}

/** Recomputes the boxes of `el` and of its subtree (`hidden`: an ancestor is display: none). */
function scanBoxes(el: Element, hidden: boolean) {
  const id = ids.get(el);
  // the renderer's own elements (the stopped badge) are no boxes of the widget's
  if (id === undefined) return;
  if (hidden) return clearBoxes(el);
  const cs = getComputedStyle(el);
  const display = cs.display;
  if (hidesSubtree(display)) return clearBoxes(el);
  const key = id * 4;
  paint.setBox(key, stylePaint(cs, display, false, el));
  if (pseudoSelector && matches(el, pseudoSelector)) {
    pseudoPaint(el, key + BEFORE, '::before');
    pseudoPaint(el, key + AFTER, '::after');
  } else {
    paint.setBox(key + BEFORE, null);
    paint.setBox(key + AFTER, null);
  }
  for (let c = firstChildOf(el); c; c = nextSiblingOf(c)) if (c instanceof Element) scanBoxes(c, false);
}

/** Whether `n` or an element above it (up to the widget's root) is display: none; remembered for one count. */
function hiddenAt(n: Node | null, memo: Map<Node, boolean>): boolean {
  if (!(n instanceof Element)) return false;
  let v = memo.get(n);
  if (v !== undefined) return v;
  v = hidesSubtree(getComputedStyle(n).display) || (n !== HTML && hiddenAt(parentNodeOf(n), memo));
  memo.set(n, v);
  return v;
}

function coveredByDirty(el: Element): boolean {
  for (let p = el === HTML ? null : parentNodeOf(el); p instanceof Element; p = p === HTML ? null : parentNodeOf(p)) if (paintDirty.has(p)) return true;
  return false;
}

/** Rule 5 for the renderer's own Web Animations (the shim's `webAnimations`): counted while `running`. */
function syncScriptedPaint() {
  for (const [aid, key] of scriptedCounted) {
    if (animations.scripted.has(aid)) continue;
    paint.endAnimation(key, 'w:' + aid);
    scriptedCounted.delete(aid);
  }
  for (const [aid, a] of animations.scripted) {
    if (!scriptedListened.has(a)) {
      scriptedListened.add(a);
      a.addEventListener('finish', schedulePaint);
      a.addEventListener('cancel', schedulePaint);
    }
    const name = 'w:' + aid;
    let key = -1;
    let p: BoxPaint | null | undefined = null;
    const effect = a.effect as KeyframeEffect | null;
    const id = a.playState === 'running' && effect?.target ? ids.get(effect.target) : undefined;
    if (id !== undefined) {
      p = scriptedPaint.get(a);
      if (p === undefined) {
        let frames: ComputedKeyframe[] = [];
        try { frames = effect!.getKeyframes(); } catch {}
        p = animationPaint(frames as unknown as PaintKeyframe[]);
        scriptedPaint.set(a, p);
      }
      if (p) key = id * 4;
    }
    const was = scriptedCounted.get(aid);
    if (was !== undefined && (was !== key || !paint.animating(was, name))) {
      paint.endAnimation(was, name);
      scriptedCounted.delete(aid);
      const target = nodes.get(Math.floor(was / 4));
      if (target instanceof Element) { paintDirtySelf.add(target); schedulePaint(); }
    }
    if (p && key >= 0 && !paint.animating(key, name)) { paint.startAnimation(key, name, p); scriptedCounted.set(aid, key); }
  }
}

function countPaint() {
  paintFrame = 0;
  if (!paintOn || paintStopped || frozen || !HTML || !ids.has(HTML)) return;
  // no computed values off the page: counted once the tree is laid out (reportSize)
  if (!isConnectedOf(HTML)) { paintPending = true; return; }
  refreshSheets();
  if (paintAll) {
    paintAll = false;
    paintDirty.clear();
    paintDirtySelf.clear();
    scanBoxes(HTML, false);
  } else {
    const memo = new Map<Node, boolean>();
    for (const el of paintDirty) {
      if (!ids.has(el) || !isConnectedOf(el) || coveredByDirty(el)) continue;
      scanBoxes(el, el !== HTML && hiddenAt(parentNodeOf(el), memo));
    }
    for (const el of paintDirtySelf) {
      const id = ids.get(el);
      if (id === undefined || paintDirty.has(el) || !isConnectedOf(el) || coveredByDirty(el) || hiddenAt(el, memo)) continue;
      const cs = getComputedStyle(el);
      paint.setBox(id * 4, stylePaint(cs, cs.display, false, el));
      if (pseudoSelector && matches(el, pseudoSelector)) {
        pseudoPaint(el, id * 4 + BEFORE, '::before');
        pseudoPaint(el, id * 4 + AFTER, '::after');
      }
    }
    paintDirty.clear();
    paintDirtySelf.clear();
  }
  if (animations.scripted.size || scriptedCounted.size) syncScriptedPaint();
  if (paintReports) {
    const key = paint.heavy + ':' + paint.backdrop;
    if (key !== lastPaintReport) { lastPaintReport = key; post({ type: 'paint', heavy: paint.heavy, backdrop: paint.backdrop }); }
  }
  const verdict = paint.check();
  if (verdict) stopPaint(verdict);
}

/** A CSS animation or transition starts or ends: rule 5 (from its start event until its end or cancel event). */
function onPaintAnimation(e: Event) {
  if (!paintOn || paintStopped || frozen) return;
  const el = e.target;
  const id = el instanceof Element ? ids.get(el) : undefined;
  if (id === undefined) return;
  const ev = e as AnimationEvent & TransitionEvent;
  const pseudo = ev.pseudoElement || '';
  const slot = pseudo === '' ? 0 : pseudo === '::before' ? BEFORE : pseudo === '::after' ? AFTER : -1;
  if (slot < 0) return;
  const key = id * 4 + slot;
  const transition = e.type.startsWith('transition');
  const name = transition ? 't:' + ev.propertyName : 'a:' + ev.animationName;
  if (e.type === 'animationstart' || e.type === 'transitionstart') {
    const p = cssAnimationPaint(el as Element, transition, transition ? ev.propertyName : ev.animationName, pseudo);
    if (!p) return;
    paint.startAnimation(key, name, p);
  } else {
    if (!paint.animating(key, name)) return;
    paint.endAnimation(key, name);
    // its values were last read while the animation applied: read them again
    paintDirtySelf.add(el as Element);
  }
  schedulePaint();
}

/**
 * What the CSS animation `name` (or the transition of property `name`) running on `el` or its pseudo-element adds,
 * from its keyframes (a transition's: its two ends). A transition the engine no longer lists counts by its property
 * alone (its values unknown); an animation it no longer lists has ended (its end event follows).
 */
function cssAnimationPaint(el: Element, transition: boolean, name: string, pseudo: string): BoxPaint | null {
  const Kind = transition ? (typeof CSSTransition === 'undefined' ? null : CSSTransition) : (typeof CSSAnimation === 'undefined' ? null : CSSAnimation);
  if (Kind) {
    for (const a of getAnimationsOf(el, pseudo ? { subtree: true } : undefined) as Animation[]) {
      if (!(a instanceof Kind) || (transition ? (a as CSSTransition).transitionProperty : (a as CSSAnimation).animationName) !== name) continue;
      const effect = a.effect as KeyframeEffect | null;
      if (!effect || effect.target !== el || (effect.pseudoElement ?? '') !== pseudo) continue;
      try { return animationPaint(effect.getKeyframes() as unknown as PaintKeyframe[]); } catch { break; }
    }
  }
  return transition ? animationPaint([{ offset: 0, [name]: null }, { offset: 1, [name]: null }]) : null;
}

/** :hover and :active move with the pointer, without ops: the elements whose state changed (and their subtrees) are counted again. */
function onPaintInput(e: Event) {
  if (!paintOn || paintStopped || frozen) return;
  const type = e.type;
  if (type === 'toggle') return paintChanged(e.target as Node, true);
  if (type === 'pointerover' || type === 'pointerout') {
    if (!paintSheets.hover) return;
    // the target's chain and the related target's, without their common part
    const a = chainOf(e.target), b = chainOf((e as PointerEvent).relatedTarget);
    const inA = new Set(a), inB = new Set(b);
    for (const el of a) if (!inB.has(el)) paintChanged(el, true);
    for (const el of b) if (!inA.has(el)) paintChanged(el, true);
    return;
  }
  // a press: :active on the target and every element above it; with no :active rule of the widget's only the UA
  // layer's `button:active` can match, which changes the element itself
  for (const el of chainOf(e.target)) {
    if (paintSheets.active) paintChanged(el, true);
    else { paintDirtySelf.add(el); schedulePaint(); }
  }
}

/** The widget's elements from `t` up to its root. */
function chainOf(t: EventTarget | null): Element[] {
  const out: Element[] = [];
  for (let n: Node | null = t instanceof Element ? t : null; n instanceof Element; n = n === HTML ? null : parentNodeOf(n)) if (ids.has(n)) out.push(n);
  return out;
}

/**
 * Past a limit (paint-policy.ts): the frame is drawn without the effects that cost (blurred shadows, filters,
 * backdrop-filter, opacity), animations stop where they are, and the host hears it (`limit`) and stops the widget;
 * until its `frozen` the renderer takes no more ops. Trusted renderer content only.
 */
function stopPaint(v: PaintVerdict) {
  paintStopped = true;
  if (paintFrame) { cancelAnimationFrame(paintFrame); paintFrame = 0; }
  drawWithoutEffects();
  post({ type: 'limit', code: v.code, count: v.count });
}

/**
 * A layer declared before every other one: its !important declarations beat every declaration of the widget's
 * stylesheets, layered or not, and the animations. Pseudo-elements lose their shadows; elements keep their shadows
 * without blur (set inline below, as are what their inline styles would otherwise keep). A disabled button keeps its
 * dimming: it is no heavy effect.
 */
const NO_EFFECTS_CSS = '@layer tw-no-effects{*,::before,::after{filter:none!important;-webkit-backdrop-filter:none!important;backdrop-filter:none!important;transition:none!important}' +
  ':not(button[disabled]),::before,::after{opacity:1!important}::before,::after{box-shadow:none!important}}';

function drawWithoutEffects() {
  // Transitions would beat any declaration: they end; animations (CSS and the renderer's Web Animations) pause.
  try {
    for (const a of getAnimationsOf(HTML, { subtree: true }) as Animation[]) {
      try { if (typeof CSSTransition !== 'undefined' && a instanceof CSSTransition) a.cancel(); else a.pause(); } catch {}
    }
  } catch {}
  // Read before the layer goes in: a box that was fully transparent stays invisible (hidden) rather than turning opaque.
  const fixes: [CSSStyleDeclaration, string, string][] = [];
  const visit = (el: Element) => {
    if (!ids.has(el)) return;
    const cs = getComputedStyle(el);
    if (hidesSubtree(cs.display)) return;
    const style = styleOf(el);
    if (parseFloat(cs.opacity) === 0) fixes.push([style, 'visibility', 'hidden']);
    if (boxShadowBlurred(cs.boxShadow)) fixes.push([style, 'box-shadow', boxShadowWithoutBlur(cs.boxShadow)]);
    // the element's own declarations would beat the layer: they go
    for (const prop of ['filter', 'backdrop-filter', '-webkit-backdrop-filter', 'opacity']) {
      if (style.getPropertyValue(prop) && !(prop === 'opacity' && buttonState(el) === 'disabled')) fixes.push([style, prop, '']);
    }
    for (let c = firstChildOf(el); c; c = nextSiblingOf(c)) if (c instanceof Element) visit(c);
  };
  visit(HTML);
  const sheet = createElement(doc, 'style');
  setTextContent(sheet, NO_EFFECTS_CSS);
  insertBefore(HEAD, sheet, firstChildOf(HEAD));
  for (const [style, prop, value] of fixes) {
    if (value) style.setProperty(prop, value, 'important');
    else style.removeProperty(prop);
  }
}

// ───────────────────────── wiring ─────────────────────────

function onMessage(msg: HostToRenderer) {
  switch (msg.type) {
    case 'ops':
      if (paintStopped) break;
      for (const op of msg.ops) {
        try { if (paintOn) paintBefore(op); applyOp(op); } catch (e) { violation('op failed: ' + String(e)); }
        if (paintOn) try { paintAfter(op); } catch {}
      }
      syncCanvas();
      reportSize();
      break;
    case 'query': {
      let res: { result: unknown; binary?: ArrayBuffer };
      try { res = answer(msg.query); } catch (e) { res = { result: { error: String(e) } }; }
      post({ type: 'result', qid: msg.qid, result: res.result, binary: res.binary }, res.binary ? [res.binary] : []);
      break;
    }
    case 'listen':
      interest = new Set(msg.types);
      break;
    case 'preventDefault':
      preventKeys = new Set(msg.keys);
      break;
    case 'anim':
      if (paintStopped) break;
      try { applyAnims(animations, msg.now, msg.anims, (id) => nodes.get(id)); } catch (e) { violation('anim failed: ' + String(e)); }
      // rule 5 for the renderer's Web Animations: their keyframes may have changed, and whether they run
      scriptedPaint = new WeakMap();
      schedulePaint();
      break;
    case 'requestFrame':
      if (frozen || paintStopped || frameReq) break;
      frameReq = requestAnimationFrame((time) => { frameReq = 0; post({ type: 'frame', time }); });
      break;
    case 'frozen':
      freeze(String(msg.reason), !!msg.fatal);
      break;
    case 'arbitrate':
      rtl = msg.rtl === true;
      bubbleMode = msg.bubble === true ? { touch: msg.touch === true, rtl } : null;
      break;
    case 'theme':
      themeVars = themeVariables(msg.theme);
      applyThemeVariables();
      env.frameColorScheme?.(themeVars['--tg-color-scheme'] === 'dark' ? 'dark' : 'light');
      canvasDirty = true;
      syncCanvas();
      paintEverything();
      break;
    case 'safeArea':
      // The UA layer's body padding follows the band; the size observers report the new height.
      safeAreaVars = contentSafeAreaVariables(msg.inset);
      applyThemeVariables();
      paintEverything();
      break;
    case 'policy': {
      // compat counts nothing (the stress kit's measuring runs); anything else is strict
      const on = msg.policy !== 'compat';
      paintReports = msg.paintReports === true;
      lastPaintReport = '';
      if (on && !paintOn) paintAll = true;
      paintOn = on;
      if (!on && paintFrame) { cancelAnimationFrame(paintFrame); paintFrame = 0; }
      schedulePaint();
      break;
    }
  }
}

// ───────────────────────── theme and safe-area variables (docs/sdk-v1.md §2.3, §2.4, §2.17, §3.4) ─────────────────────────

let themeVars: Record<string, string> = {};
/** The client's chrome over the widget (docs/sdk-v1.md §2.17): always set, 0 until the host says otherwise. */
let safeAreaVars: Record<string, string> = contentSafeAreaVariables(null);

/** The host's variables, inline and important on the widget's root: no widget stylesheet or root style op overrides them. */
function applyThemeVariables() {
  if (!HTML) return;
  const style = styleOf(HTML);
  for (const name of THEME_VARIABLES) {
    const v = themeVars[name] ?? safeAreaVars[name];
    if (v === undefined) style.removeProperty(name);
    else style.setProperty(name, v, 'important');
  }
}

// ───────────────────────── the canvas of a widget that is not a document (shadow renderer) ─────────────────────────

let canvasDirty = true;
/** Painted on the canvas element; `background-attachment` stays `scroll` (fixed would follow the chat page's viewport). */
const CANVAS_PROPS = ['background-color', 'background-image', 'background-position', 'background-size', 'background-repeat', 'background-origin', 'background-clip'];
const transparentColor = (c: string) => c === 'transparent' || /^rgba\(\s*0,\s*0,\s*0,\s*0\s*\)$/.test(c);

/**
 * CSS paints a document's canvas with the root element's background, or with the body's when the root has none
 * (and a body background then reaches the whole bubble, its margins included). A shadow root has no canvas, so the
 * core does it on `env.canvas` after every change that can move either background. Widget content only: the values
 * are the widget's own computed, validated ones; the bubble's own colour is behind the renderer, never painted here.
 */
function syncCanvas() {
  const canvas = env.canvas;
  if (!canvas || !canvasDirty || !HTML || !BODY) return;
  canvasDirty = false;
  const root = getComputedStyle(HTML);
  const src = root.backgroundImage !== 'none' || !transparentColor(root.backgroundColor) ? root : getComputedStyle(BODY);
  const style = styleOf(canvas);
  for (const p of CANVAS_PROPS) style.setProperty(p, src.getPropertyValue(p));
}

/** Whether a press on `t` may start a text selection: the nearest `user-select` that is not `auto`, from the target up to the root, where the UA layer says none (D18). */
function selectable(t: EventTarget | null): boolean {
  let el: Element | null = t instanceof Element ? t : t instanceof Node ? t.parentElement : null;
  while (el) {
    const cs = getComputedStyle(el);
    const v = cs.userSelect || (cs as unknown as { webkitUserSelect?: string }).webkitUserSelect;
    if (v && v !== 'auto') return v !== 'none';
    if (el === HTML) return false;
    el = el.parentElement;
  }
  return false;
}

// ───────────────────────── frozen state (last render as fallback) ─────────────────────────

/**
 * The host reports that the widget's runtime died (fatal) or was stopped. The
 * DOM stays exactly as it was (the shim flushes ops only at the end of a task,
 * so a kill mid-task leaves the previous consistent state), events and frames
 * stop, and a small badge — a renderer element, never part of the widget's
 * node table — tells the user. Both the stylesheet and the badge are trusted
 * renderer content; nothing here comes from the widget.
 */
function freeze(reason: string, fatal: boolean) {
  if (frozen) return;
  frozen = true;
  if (frameReq) { cancelAnimationFrame(frameReq); frameReq = 0; }
  if (moveFrame) { cancelAnimationFrame(moveFrame); moveFrame = 0; }
  pendingMoves.clear();
  setAttribute(HTML, 'data-tw-frozen', fatal ? 'fatal' : 'paused');
  const style = createElement(doc, 'style');
  setTextContent(style, '[data-tw-frozen]{pointer-events:none}[data-tw-frozen=fatal]{filter:grayscale(.3)}' +
    '[data-tw-badge]{position:fixed;right:6px;bottom:6px;z-index:2147483647;font:11px/1.3 system-ui,sans-serif;color:#fff;background:rgba(60,60,60,.75);padding:2px 7px;border-radius:9px;pointer-events:none;user-select:none}');
  insertBefore(HEAD, style, null);
  const badge = createElement(doc, 'div');
  setAttribute(badge, 'data-tw-badge', fatal ? 'fatal' : 'paused');
  setTextContent(badge, reason);
  insertBefore(BODY, badge, null);
}

let sizeObservers: ResizeObserver[] = [];
let offViewport: (() => void) | null = null;

/** Input the chat keeps (SWALLOWED): never forwarded to the widget, and without its default action inside it. */
function swallow(e: Event) {
  if (e.type === 'mousedown' || e.type === 'selectstart') {
    // No focus move and no selection from a press, unless it lands on text the widget made selectable (user-select: text,
    // D18); the press itself still reaches the widget (pointerdown, mousedown, :active).
    if (e.cancelable && !selectable(e.target)) e.preventDefault();
    return;
  }
  if (e.type === 'focusin') {
    const t = e.target;
    if (t instanceof HTMLElement || t instanceof SVGElement) (t instanceof SVGElement ? SVGElement.prototype.blur : HTMLElement.prototype.blur).call(t);
    return;
  }
  // Default actions only: on the Shadow DOM renderer the chat page's own handlers (its message menu) still see the event.
  if (e.cancelable) e.preventDefault();
}

/**
 * The pointers whose trusted press began on the widget, until they are let go anywhere in the window: the only ones it
 * may capture. And whether the pointer whose buttons changed last was one of them: pressed, let go (the window hears a
 * release before the root does) or chorded (a button pressed or let go while another is held: a pointermove and its
 * mouse event, no pointerdown or pointerup). A compatibility mouse event follows its pointer's change at once.
 */
const ownPointers = new Set<number>();
let changedOwn = false;
/** Of those, the fingers' and pens' still down (a press of theirs is no gesture before its release), and whether the pointer whose buttons changed last is one. */
const fingersDown = new Set<number>();
let changedFingerDown = false;
const isFingerOrPen = (e: PointerEvent) => e.pointerType === 'touch' || e.pointerType === 'pen';
function pressedHere(e: Event) {
  if (!e.isTrusted) return;
  const id = (e as PointerEvent).pointerId;
  ownPointers.add(id);
  if (isFingerOrPen(e as PointerEvent)) fingersDown.add(id);
  changedOwn = true;
  changedFingerDown = fingersDown.has(id);
}
function buttonsChanged(e: Event) {
  if (!e.isTrusted || (e as PointerEvent).button === -1) return;
  const id = (e as PointerEvent).pointerId;
  changedOwn = ownPointers.has(id);
  changedFingerDown = fingersDown.has(id);
}
function letGo(e: Event) {
  const id = (e as PointerEvent).pointerId;
  changedOwn = ownPointers.delete(id);
  fingersDown.delete(id);
  changedFingerDown = false;
}

function init(s: Send) {
  send = s;
  ({ html: HTML, head: HEAD, body: BODY } = env.roots());
  // The renderer's own stylesheet (trusted, from the profile): the root and text defaults, text not selectable unless the
  // widget says user-select: text, the client-styled <button>.
  const ua = createElement(doc, 'style');
  setTextContent(ua, PROFILE_UA_STYLESHEET);
  insertBefore(HEAD, ua, firstChildOf(HEAD));
  root.addEventListener('pointerdown', pressedHere, true);
  root.addEventListener('pointermove', buttonsChanged, true);
  window.addEventListener('pointerup', letGo, true);
  window.addEventListener('pointercancel', letGo, true);
  for (const type of SWALLOWED) root.addEventListener(type, swallow, { capture: true, passive: false });
  for (const type of ALL_EVENTS) root.addEventListener(type, onEvent, { capture: true, passive: false });
  // Chromium dispatches enter/leave events only to nodes that listen for them or have an ancestor element with a
  // capturing listener; a shadow root is no element, so without this the widget never gets pointerenter / mouseenter.
  if (root instanceof ShadowRoot) for (const type of ENTER_LEAVE) root.host.addEventListener(type, noop, true);
  for (const type of ['auxclick', 'dragstart', 'dblclick']) root.addEventListener(type, (e) => e.preventDefault(), true);
  for (const type of PAINT_ANIMATION_EVENTS) root.addEventListener(type, onPaintAnimation, true);
  for (const type of PAINT_INPUT_EVENTS) root.addEventListener(type, onPaintInput, true);
  sizeObservers = [new ResizeObserver(reportSize), new ResizeObserver(reportSize)];
  sizeObservers[0].observe(HTML);
  sizeObservers[1].observe(BODY);
  const offResize = env.onViewportResize(reportSize);
  const offScheme = env.onColorSchemeChange(() => post({ type: 'media' }));
  offViewport = () => { offResize(); offScheme(); };
  reportSize();
}

function destroy() {
  for (const type of ALL_EVENTS) root.removeEventListener(type, onEvent, { capture: true });
  for (const type of SWALLOWED) root.removeEventListener(type, swallow, { capture: true });
  root.removeEventListener('pointerdown', pressedHere, true);
  root.removeEventListener('pointermove', buttonsChanged, true);
  window.removeEventListener('pointerup', letGo, true);
  window.removeEventListener('pointercancel', letGo, true);
  ownPointers.clear();
  fingersDown.clear();
  if (root instanceof ShadowRoot) for (const type of ENTER_LEAVE) root.host.removeEventListener(type, noop, true);
  for (const type of PAINT_ANIMATION_EVENTS) root.removeEventListener(type, onPaintAnimation, true);
  for (const type of PAINT_INPUT_EVENTS) root.removeEventListener(type, onPaintInput, true);
  if (paintFrame) { cancelAnimationFrame(paintFrame); paintFrame = 0; }
  for (const o of sizeObservers) o.disconnect();
  sizeObservers = [];
  offViewport?.();
  offViewport = null;
  resizeObserver.disconnect();
  if (frameReq) { cancelAnimationFrame(frameReq); frameReq = 0; }
  if (moveFrame) { cancelAnimationFrame(moveFrame); moveFrame = 0; }
  clearHold();
  send = () => {};
}

return { init, onMessage, destroy };
}

// Event system for the sandbox DOM: Event classes, capture/target/bubble
// dispatch, on* attribute and property handlers. Replaces linkedom's simplified
// EventTarget (no capture phase, no inline handlers).

import { host } from './host';

const LISTENERS = Symbol('tw.listeners');
const HANDLERS = Symbol('tw.handlers');

interface Listener { fn: any; capture: boolean; once: boolean }

let win: any = null;
let doc: any = null;
export function setEventRoots(window: any, document: any) {
  win = window;
  doc = document;
}

let reportError: (e: unknown) => void = () => {};
export function setErrorReporter(fn: (e: unknown) => void) {
  reportError = fn;
}

// ── which event types does the widget listen to? (so the renderer forwards only those) ──
const interest = new Set<string>();
let interestDirty = false;
export function noteInterest(type: string) {
  if (!interest.has(type)) {
    interest.add(type);
    interestDirty = true;
  }
}
export function flushInterest() {
  if (!interestDirty) return;
  interestDirty = false;
  host('listen', JSON.stringify([...interest]));
}

// ───────────────────────── Event classes ─────────────────────────

export class Event {
  static NONE = 0;
  static CAPTURING_PHASE = 1;
  static AT_TARGET = 2;
  static BUBBLING_PHASE = 3;
  NONE = 0;
  CAPTURING_PHASE = 1;
  AT_TARGET = 2;
  BUBBLING_PHASE = 3;
  type: string;
  bubbles: boolean;
  cancelable: boolean;
  composed: boolean;
  target: any = null;
  srcElement: any = null;
  currentTarget: any = null;
  eventPhase = 0;
  defaultPrevented = false;
  isTrusted = false;
  timeStamp: number;
  _stop = false;
  _stopNow = false;
  _path: any[] = [];
  _dispatching = false;
  constructor(type: string, init: any = {}) {
    this.type = String(type);
    this.bubbles = !!init.bubbles;
    this.cancelable = !!init.cancelable;
    this.composed = !!init.composed;
    this.timeStamp = Number(host('now'));
  }
  preventDefault() {
    if (this.cancelable) this.defaultPrevented = true;
  }
  stopPropagation() {
    this._stop = true;
  }
  stopImmediatePropagation() {
    this._stop = this._stopNow = true;
  }
  composedPath() {
    return [...this._path];
  }
  get cancelBubble() {
    return this._stop;
  }
  set cancelBubble(v: boolean) {
    if (v) this._stop = true;
  }
  get returnValue() {
    return !this.defaultPrevented;
  }
  set returnValue(v: boolean) {
    if (!v) this.preventDefault();
  }
  initEvent(type: string, bubbles?: boolean, cancelable?: boolean) {
    this.type = type;
    this.bubbles = !!bubbles;
    this.cancelable = !!cancelable;
  }
}

export class CustomEvent extends Event {
  detail: any;
  constructor(type: string, init: any = {}) {
    super(type, init);
    this.detail = init.detail ?? null;
  }
  initCustomEvent(type: string, bubbles?: boolean, cancelable?: boolean, detail?: any) {
    this.initEvent(type, bubbles, cancelable);
    this.detail = detail;
  }
}

export class UIEvent extends Event {
  view: any;
  detail: number;
  constructor(type: string, init: any = {}) {
    super(type, init);
    this.view = init.view ?? win;
    this.detail = init.detail ?? 0;
  }
}

const MOUSE_FIELDS = ['clientX', 'clientY', 'screenX', 'screenY', 'pageX', 'pageY', 'offsetX', 'offsetY', 'movementX', 'movementY', 'button', 'buttons'];
export class MouseEvent extends UIEvent {
  altKey: boolean;
  ctrlKey: boolean;
  metaKey: boolean;
  shiftKey: boolean;
  relatedTarget: any;
  [k: string]: any;
  constructor(type: string, init: any = {}) {
    super(type, init);
    for (const f of MOUSE_FIELDS) this[f] = Number(init[f] ?? 0);
    if (init.pageX === undefined) this.pageX = this.clientX;
    if (init.pageY === undefined) this.pageY = this.clientY;
    if (init.screenX === undefined) this.screenX = this.clientX;
    if (init.screenY === undefined) this.screenY = this.clientY;
    this.altKey = !!init.altKey;
    this.ctrlKey = !!init.ctrlKey;
    this.metaKey = !!init.metaKey;
    this.shiftKey = !!init.shiftKey;
    this.relatedTarget = init.relatedTarget ?? null;
  }
  get x() { return this.clientX; }
  get y() { return this.clientY; }
  get layerX() { return this.offsetX; }
  get layerY() { return this.offsetY; }
  get which() { return this.button + 1; }
  getModifierState(key: string) {
    return !!({ Alt: this.altKey, Control: this.ctrlKey, Meta: this.metaKey, Shift: this.shiftKey } as any)[key];
  }
}

export class PointerEvent extends MouseEvent {
  constructor(type: string, init: any = {}) {
    super(type, init);
    this.pointerId = init.pointerId ?? 1;
    this.pointerType = init.pointerType ?? 'mouse';
    this.isPrimary = init.isPrimary ?? true;
    this.pressure = init.pressure ?? 0;
    this.width = init.width ?? 1;
    this.height = init.height ?? 1;
  }
  getCoalescedEvents() { return [this]; }
}

export class WheelEvent extends MouseEvent {
  constructor(type: string, init: any = {}) {
    super(type, init);
    this.deltaX = init.deltaX ?? 0;
    this.deltaY = init.deltaY ?? 0;
    this.deltaZ = init.deltaZ ?? 0;
    this.deltaMode = init.deltaMode ?? 0;
  }
}

export class KeyboardEvent extends UIEvent {
  [k: string]: any;
  constructor(type: string, init: any = {}) {
    super(type, init);
    this.key = init.key ?? '';
    this.code = init.code ?? '';
    this.repeat = !!init.repeat;
    this.altKey = !!init.altKey;
    this.ctrlKey = !!init.ctrlKey;
    this.metaKey = !!init.metaKey;
    this.shiftKey = !!init.shiftKey;
    this.isComposing = false;
    this.location = 0;
    const legacy: Record<string, number> = { Enter: 13, Escape: 27, ' ': 32, ArrowLeft: 37, ArrowUp: 38, ArrowRight: 39, ArrowDown: 40, Backspace: 8, Tab: 9, Delete: 46 };
    this.keyCode = this.which = init.keyCode ?? legacy[this.key] ?? (this.key.length === 1 ? this.key.toUpperCase().charCodeAt(0) : 0);
  }
  getModifierState(key: string) {
    return !!({ Alt: this.altKey, Control: this.ctrlKey, Meta: this.metaKey, Shift: this.shiftKey } as any)[key];
  }
}

export class InputEvent extends UIEvent {
  data: string | null;
  inputType: string;
  isComposing = false;
  constructor(type: string, init: any = {}) {
    super(type, init);
    this.data = init.data ?? null;
    this.inputType = init.inputType ?? '';
  }
}

export class FocusEvent extends UIEvent {
  relatedTarget: any;
  constructor(type: string, init: any = {}) {
    super(type, init);
    this.relatedTarget = init.relatedTarget ?? null;
  }
}

export class TouchEvent extends UIEvent {
  [k: string]: any;
  constructor(type: string, init: any = {}) {
    super(type, init);
    this.touches = init.touches ?? [];
    this.targetTouches = init.targetTouches ?? this.touches;
    this.changedTouches = init.changedTouches ?? this.touches;
    this.altKey = !!init.altKey;
    this.ctrlKey = !!init.ctrlKey;
    this.metaKey = !!init.metaKey;
    this.shiftKey = !!init.shiftKey;
  }
}

export class SubmitEvent extends Event {
  submitter: any;
  constructor(type: string, init: any = {}) {
    super(type, init);
    this.submitter = init.submitter ?? null;
  }
}

export class ErrorEvent extends Event {
  [k: string]: any;
  constructor(type: string, init: any = {}) {
    super(type, init);
    this.message = init.message ?? '';
    this.filename = init.filename ?? '';
    this.lineno = init.lineno ?? 0;
    this.colno = init.colno ?? 0;
    this.error = init.error ?? null;
  }
}

export class AnimationEvent extends Event {
  animationName: string;
  elapsedTime: number;
  constructor(type: string, init: any = {}) {
    super(type, init);
    this.animationName = init.animationName ?? '';
    this.elapsedTime = init.elapsedTime ?? 0;
  }
}
export class TransitionEvent extends Event {
  propertyName: string;
  elapsedTime: number;
  constructor(type: string, init: any = {}) {
    super(type, init);
    this.propertyName = init.propertyName ?? '';
    this.elapsedTime = init.elapsedTime ?? 0;
  }
}

// ───────────────────────── EventTarget ─────────────────────────

function listenersOf(target: any, create: boolean): Map<string, Listener[]> | undefined {
  let map = target[LISTENERS];
  if (!map && create) {
    map = new Map();
    Object.defineProperty(target, LISTENERS, { value: map, enumerable: false, configurable: true });
  }
  return map;
}

function captureFlag(options: any): boolean {
  return typeof options === 'boolean' ? options : !!options?.capture;
}

export function addEventListener(this: any, type: string, fn: any, options?: any) {
  if (!fn) return;
  const target = this ?? win;
  type = String(type);
  const map = listenersOf(target, true)!;
  let list = map.get(type);
  if (!list) map.set(type, (list = []));
  const capture = captureFlag(options);
  if (list.some((l) => l.fn === fn && l.capture === capture)) return;
  const entry: Listener = { fn, capture, once: !!options?.once };
  list.push(entry);
  noteInterest(type);
  const signal = options?.signal;
  if (signal && typeof signal.addEventListener === 'function') {
    signal.addEventListener('abort', () => removeEventListener.call(target, type, fn, options), { once: true });
  }
}

export function removeEventListener(this: any, type: string, fn: any, options?: any) {
  const target = this ?? win;
  const list = listenersOf(target, false)?.get(String(type));
  if (!list) return;
  const capture = captureFlag(options);
  const i = list.findIndex((l) => l.fn === fn && l.capture === capture);
  if (i >= 0) list.splice(i, 1);
}

const compiled = new Map<string, Function | null>();

/** The on<type> handler of a target: property handler first, else the content attribute. */
function inlineHandler(target: any, type: string): Function | null {
  const prop = target[HANDLERS]?.[type];
  if (prop !== undefined) return typeof prop === 'function' ? prop : null;
  if (target.nodeType !== 1) return null;
  const code = target.getAttribute('on' + type);
  if (code === null || code === undefined) return null;
  let fn = compiled.get(code);
  if (fn === undefined) {
    try {
      // Same scope chain as a real inline handler: element, then document, then globals.
      fn = new Function('event', `with(this.ownerDocument||{}){with(this){${code}\n}}`);
    } catch (e) {
      reportError(e);
      fn = null;
    }
    compiled.set(code, fn);
  }
  return fn;
}

function invoke(target: any, event: Event, phase: number) {
  if (event._stop && phase !== 0) return;
  event.currentTarget = target;
  event.eventPhase = phase === 1 ? 1 : event.target === target ? 2 : 3;
  // on* handler runs in the bubble/target phase.
  if (phase !== 1) {
    let h = inlineHandler(target, event.type);
    // <body onload/onresize/…> are window handlers.
    if (!h && target === win && doc?.body) h = inlineHandler(doc.body, event.type);
    if (h) {
      try {
        const r = h.call(target === win && doc?.body && !target[HANDLERS]?.[event.type] ? doc.body : target, event);
        if (r === false) event.preventDefault();
      } catch (e) {
        reportError(e);
      }
    }
  }
  const list = listenersOf(target, false)?.get(event.type);
  if (!list) return;
  for (const l of [...list]) {
    if (event._stopNow) break;
    if (phase === 1 ? !l.capture : phase === 3 && l.capture) continue;
    if (l.once) {
      const i = list.indexOf(l);
      if (i >= 0) list.splice(i, 1);
    }
    try {
      if (typeof l.fn === 'function') l.fn.call(target, event);
      else if (typeof l.fn?.handleEvent === 'function') l.fn.handleEvent(event);
    } catch (e) {
      reportError(e);
    }
  }
}

export function dispatchEvent(this: any, event: Event): boolean {
  const target = this ?? win;
  if (!event || typeof event !== 'object') throw new TypeError('dispatchEvent: not an Event');
  if (event._dispatching) throw new Error('InvalidStateError: event is already being dispatched');
  event._dispatching = true;
  event._stop = event._stopNow = false;
  event.target = event.srcElement = target;
  const path: any[] = [target];
  if (target !== win) {
    for (let n = target.parentNode; n; n = n.parentNode) path.push(n);
    // Events reach window only through the document (not for detached trees); 'load' does not propagate to window.
    if (path[path.length - 1] === doc && event.type !== 'load') path.push(win);
  }
  event._path = path;
  for (let i = path.length - 1; i >= 1 && !event._stop; i--) invoke(path[i], event, 1);
  if (!event._stop) invoke(path[0], event, 2);
  if (event.bubbles) for (let i = 1; i < path.length && !event._stop; i++) invoke(path[i], event, 3);
  event.currentTarget = null;
  event.eventPhase = 0;
  event._dispatching = false;
  return !event.defaultPrevented;
}

const HANDLER_NAMES = [
  'click', 'dblclick', 'contextmenu', 'input', 'change', 'submit', 'reset', 'keydown', 'keyup', 'keypress',
  'pointerdown', 'pointerup', 'pointermove', 'pointerenter', 'pointerleave', 'pointerover', 'pointerout', 'pointercancel',
  'mousedown', 'mouseup', 'mousemove', 'mouseenter', 'mouseleave', 'mouseover', 'mouseout',
  'touchstart', 'touchmove', 'touchend', 'touchcancel', 'focus', 'blur', 'focusin', 'focusout', 'wheel', 'scroll',
  'load', 'error', 'toggle', 'resize', 'animationstart', 'animationend', 'animationiteration', 'transitionend',
  'DOMContentLoaded', 'readystatechange', 'visibilitychange', 'beforeunload', 'unload', 'hashchange', 'popstate',
  'message', 'storage', 'online', 'offline', 'select', 'drag', 'dragstart', 'dragend', 'dragover', 'drop',
];

/** Install addEventListener & friends plus on<event> accessors on a prototype or object. */
export function installEventTarget(obj: any) {
  const def = (name: string, value: any) => Object.defineProperty(obj, name, { value, writable: true, configurable: true, enumerable: false });
  def('addEventListener', addEventListener);
  def('removeEventListener', removeEventListener);
  def('dispatchEvent', dispatchEvent);
  for (const type of HANDLER_NAMES) {
    Object.defineProperty(obj, 'on' + type.toLowerCase(), {
      configurable: true,
      enumerable: false,
      get(this: any) {
        const t = this ?? win;
        const prop = t[HANDLERS]?.[type];
        return prop !== undefined ? prop : inlineHandler(t, type);
      },
      set(this: any, fn: any) {
        const t = this ?? win;
        if (!t[HANDLERS]) Object.defineProperty(t, HANDLERS, { value: {}, enumerable: false, configurable: true });
        t[HANDLERS][type] = typeof fn === 'function' ? fn : null;
        if (typeof fn === 'function') noteInterest(type);
      },
    });
  }
}

/** Standalone EventTarget (for `new EventTarget()` and internal objects such as MediaQueryList). */
export class EventTarget {
  addEventListener(type: string, fn: any, options?: any) { addEventListener.call(this, type, fn, options); }
  removeEventListener(type: string, fn: any, options?: any) { removeEventListener.call(this, type, fn, options); }
  dispatchEvent(event: Event) { return dispatchEvent.call(this, event); }
}

/** Build the right Event subclass for an event forwarded by the renderer. */
export function eventFromHost(ev: any, lookup: (id: number) => any): Event {
  const t = ev.type as string;
  const mods = ev.modifiers ?? 0;
  const init: any = {
    ...ev,
    bubbles: !['mouseenter', 'mouseleave', 'pointerenter', 'pointerleave', 'focus', 'blur', 'load', 'error', 'scroll', 'toggle'].includes(t),
    cancelable: !['gotpointercapture', 'lostpointercapture', 'input', 'change', 'scroll', 'load', 'error', 'focus', 'blur', 'focusin', 'focusout', 'toggle', 'mouseenter', 'mouseleave', 'pointerenter', 'pointerleave'].includes(t),
    altKey: !!(mods & 1), ctrlKey: !!(mods & 2), metaKey: !!(mods & 4), shiftKey: !!(mods & 8),
    relatedTarget: ev.relatedId ? lookup(ev.relatedId) : null,
  };
  let e: Event;
  if (t.startsWith('pointer') || t === 'gotpointercapture' || t === 'lostpointercapture') e = new PointerEvent(t, init);
  else if (t === 'wheel') e = new WheelEvent(t, init);
  else if (t === 'click' || t === 'dblclick' || t === 'contextmenu') e = new PointerEvent(t, init);
  else if (t.startsWith('mouse')) e = new MouseEvent(t, init);
  else if (t.startsWith('key')) e = new KeyboardEvent(t, init);
  else if (t.startsWith('touch')) {
    const target = lookup(ev.targetId);
    const touch = { identifier: 0, target, clientX: ev.clientX ?? 0, clientY: ev.clientY ?? 0, pageX: ev.pageX ?? 0, pageY: ev.pageY ?? 0, screenX: ev.clientX ?? 0, screenY: ev.clientY ?? 0 };
    const active = t === 'touchend' || t === 'touchcancel' ? [] : [touch];
    e = new TouchEvent(t, { ...init, touches: active, targetTouches: active, changedTouches: [touch] });
  } else if (t === 'input') e = new InputEvent(t, init);
  else if (t.startsWith('focus') || t === 'blur') e = new FocusEvent(t, init);
  else if (t === 'submit') e = new SubmitEvent(t, { ...init, submitter: ev.submitterId ? lookup(ev.submitterId) : null });
  else if (t.startsWith('animation')) e = new AnimationEvent(t, init);
  else if (t.startsWith('transition')) e = new TransitionEvent(t, init);
  else e = new Event(t, init);
  e.isTrusted = true;
  return e;
}

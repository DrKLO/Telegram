// Window-level environment: timers, console, storage, Intl, matchMedia,
// observers, network stubs, the TelegramWidget bridge and assorted polyfills.

import { callMethod } from './capture';
import { addEventListener, dispatchEvent, Event, EventTarget, removeEventListener } from './events';
import { diag, host, invalidateLayout, query, unsupported } from './host';
import { PROFILE_VERSION } from '../shared/profile-tables';
import { cssSupports } from '../shared/css-supports';
import {
  CONTENT_SAFE_AREA_FIELDS, DEFAULT_LOCALE, DEFAULT_RESIZE_ANCHOR, HAPTIC_IMPACT_STYLES, HAPTIC_NOTIFICATION_TYPES, MAX_HANDLERS_PER_EVENT, NO_CONTENT_SAFE_AREA, REQUEST_ERRORS,
  STORAGE_QUOTA_BYTES, checkChatType, checkContentSafeArea, checkCopyText, checkLink, checkLocale, checkRequestData, checkRequestOptions, checkResizeAnchor, checkSignDataPayload,
  checkViewer, checkWalletTransaction, checkWidgetDataValue, completeThemeParams, utf8Length,
  type ChatType, type ContentSafeArea, type RequestError, type ResizeAnchor, type ThemeParams, type WidgetUser,
} from '../shared/sdk-policy';

const G: any = globalThis;

export let reportError: (e: unknown) => void = (e) => {
  // Fatal engine conditions must reach the host glue, not be swallowed here.
  if (e instanceof Error && e.name === 'InternalError' && /out of memory|interrupted/.test(e.message)) throw e;
  const text = e instanceof Error ? `${e.name}: ${e.message}${e.stack ? '\n' + e.stack : ''}` : String(e);
  host('log', 'error:Uncaught ' + text);
  try {
    const ev: any = new G.ErrorEvent('error', { message: String((e as any)?.message ?? e), error: e, cancelable: true });
    dispatchEvent.call(G, ev);
  } catch {}
};

// ───────────────────────── console ─────────────────────────

function fmt(v: any, depth = 0): string {
  if (typeof v === 'string') return depth ? JSON.stringify(v) : v;
  if (v === null || v === undefined || typeof v === 'number' || typeof v === 'boolean' || typeof v === 'bigint') return String(v);
  if (typeof v === 'function') return `[Function ${v.name || 'anonymous'}]`;
  if (typeof v === 'symbol') return v.toString();
  if (v instanceof Error) return `${v.name}: ${v.message}${v.stack ? '\n' + v.stack : ''}`;
  if (v.nodeType === 1) return `<${v.localName}${v.id ? '#' + v.id : ''}${v.getAttribute?.('class') ? '.' + String(v.getAttribute('class')).trim().replace(/\s+/g, '.') : ''}>`;
  if (v.nodeType) return v.nodeName ?? '[Node]';
  if (depth > 3) return Array.isArray(v) ? '[…]' : '{…}';
  try {
    if (Array.isArray(v)) return '[' + v.slice(0, 50).map((x) => fmt(x, depth + 1)).join(', ') + (v.length > 50 ? ', …' : '') + ']';
    if (v instanceof Map) return 'Map(' + v.size + ') ' + fmt(Object.fromEntries([...v].slice(0, 20)), depth + 1);
    if (v instanceof Set) return 'Set(' + v.size + ') ' + fmt([...v], depth + 1);
    const keys = Object.keys(v);
    return '{' + keys.slice(0, 30).map((k) => `${k}: ${fmt(v[k], depth + 1)}`).join(', ') + (keys.length > 30 ? ', …' : '') + '}';
  } catch {
    return '[Object]';
  }
}

function installConsole() {
  const out = (level: string) => (...args: any[]) => host('log', level + ':' + args.map((a) => fmt(a)).join(' '));
  const timers = new Map<string, number>();
  G.console = {
    log: out('log'), info: out('info'), warn: out('warn'), error: out('error'), debug: out('debug'), trace: out('debug'),
    table: out('log'), dir: out('log'), group: out('log'), groupCollapsed: out('log'), groupEnd() {}, clear() {},
    assert(cond: any, ...args: any[]) { if (!cond) out('error')('Assertion failed:', ...args); },
    count() {}, countReset() {},
    time(label = 'default') { timers.set(label, Number(host('now'))); },
    timeEnd(label = 'default') { const t = timers.get(label); if (t !== undefined) out('log')(`${label}: ${(Number(host('now')) - t).toFixed(2)} ms`); timers.delete(label); },
    timeLog(label = 'default') { const t = timers.get(label); if (t !== undefined) out('log')(`${label}: ${(Number(host('now')) - t).toFixed(2)} ms`); },
  };
}

// ───────────────────────── timers & frames ─────────────────────────

interface Timer { fn: any; args: any[]; interval: boolean; delay: number }
const timers = new Map<number, Timer>();
let timerSeq = 1;
/** Live timers allowed (from `config.maxTimers` at boot). The glue enforces the same cap as a backstop; this one gives the widget an error instead of a silent hang. */
let maxTimers = 64;
let timerLimitReported = false;

export function configureLimits(limits: { maxTimers?: number }) {
  if (Number.isFinite(limits.maxTimers) && (limits.maxTimers as number) > 0) maxTimers = Math.floor(limits.maxTimers as number);
}

function setTimer(fn: any, delay: any, args: any[], interval: boolean): number {
  if (timers.size >= maxTimers) {
    if (!timerLimitReported) { timerLimitReported = true; diag('timer-limit', `widget holds ${timers.size} live timers (max ${maxTimers}); further ${interval ? 'setInterval' : 'setTimeout'} calls throw RangeError`); }
    throw new RangeError(`too many timers (max ${maxTimers})`);
  }
  const id = timerSeq++;
  const ms = Math.max(Number(delay) || 0, interval ? 4 : 0);
  timers.set(id, { fn, args, interval, delay: ms });
  host('timer', JSON.stringify([id, ms]));
  return id;
}
function clearTimer(id: any) {
  if (timers.delete(Number(id))) host('clearTimer', String(Number(id)));
}
export function hostTimer(fn: () => void, ms: number): number {
  const id = timerSeq++;
  timers.set(id, { fn, args: [], interval: false, delay: ms });
  host('timer', JSON.stringify([id, ms]));
  return id;
}
export const clearHostTimer = clearTimer;
export function fireTimer(id: number) {
  const t = timers.get(id);
  if (!t) return;
  if (t.interval) host('timer', JSON.stringify([id, t.delay]));
  else timers.delete(id);
  try {
    if (typeof t.fn === 'function') t.fn(...t.args);
    else (0, eval)(String(t.fn));
  } catch (e) {
    reportError(e);
  }
}

// ───────────────────────── visibility (told by the host, see HostToWorker `visibility`) ─────────────────────────

let hidden = false;
export const isHidden = () => hidden;
/** Returns true when the state changed (the caller dispatches `visibilitychange`). */
export function setHidden(h: boolean): boolean {
  if (hidden === h) return false;
  hidden = h;
  return true;
}

let frameCallbacks = new Map<number, (t: number) => void>();
let frameSeq = 1;
let frameHook: ((now: number) => ((run: () => void) => void) | null) | null = null;
export function setFrameHook(hook: typeof frameHook) {
  frameHook = hook;
}
export function fireFrame() {
  const batch = frameCallbacks;
  frameCallbacks = new Map();
  const now = G.performance.now();
  const run = () => {
    for (const cb of batch.values()) {
      try { cb(now); } catch (e) { reportError(e); }
    }
  };
  const hooked = frameHook ? frameHook(now) : null;
  if (hooked) { hooked(run); return; }
  run();
}

let originTime = 0;
export const timeOrigin = () => originTime;

// ───────────────────────── storage (docs/sdk-v1.md §2.8) ─────────────────────────

/** Minimal DOMException: `name` carries the kind (QuotaExceededError, NotAllowedError, …), `code` the legacy number. */
const DOM_EXCEPTION_CODES: Record<string, number> = { IndexSizeError: 1, HierarchyRequestError: 3, NotFoundError: 8, NotSupportedError: 9, InvalidStateError: 11, SyntaxError: 12, SecurityError: 18, NetworkError: 19, AbortError: 20, QuotaExceededError: 22, TimeoutError: 23, DataCloneError: 25 };
export class DOMException extends Error {
  constructor(message = '', name = 'Error') {
    super(String(message));
    Object.defineProperty(this, 'name', { value: String(name), writable: true, configurable: true });
  }
  get code() { return DOM_EXCEPTION_CODES[this.name] ?? 0; }
}

type AreaName = 'local' | 'session';
const storageData: Record<AreaName, Map<string, string>> = { local: new Map(), session: new Map() };
const storageUsed: Record<AreaName, number> = { local: 0, session: 0 };
const storageDirty = new Set<AreaName>();

function areaBytes(m: Map<string, string>) {
  let n = 0;
  for (const [k, v] of m) n += utf8Length(k) + utf8Length(v);
  return n;
}

/** The client's preload (already checked by the glue); called once at boot. */
export function preloadStorage(storage: unknown) {
  const s = (typeof storage === 'object' && storage !== null ? storage : {}) as Record<string, unknown>;
  for (const area of ['local', 'session'] as AreaName[]) {
    const m = storageData[area];
    m.clear();
    const src = s[area];
    if (typeof src === 'object' && src !== null) for (const k of Object.keys(src)) { const v = (src as any)[k]; if (typeof v === 'string') m.set(k, v); }
    storageUsed[area] = areaBytes(m);
    if (storageUsed[area] > STORAGE_QUOTA_BYTES) { m.clear(); storageUsed[area] = 0; }
  }
  storageDirty.clear();
}

/** The areas the task changed, in full, or null; the caller sends them to the glue (`__host('storage', …)`) once per task. */
export function takeStorageCommit(): Record<string, Record<string, string>> | null {
  if (!storageDirty.size) return null;
  const commit: Record<string, Record<string, string>> = {};
  for (const area of storageDirty) commit[area] = Object.fromEntries(storageData[area]);
  storageDirty.clear();
  return commit;
}

function makeStorage(area: AreaName): any {
  const data = storageData[area];
  const setItem = (key: unknown, value: unknown) => {
    const k = String(key), v = String(value);
    const old = data.get(k);
    if (old === v) return;
    const used = storageUsed[area] - (old === undefined ? 0 : utf8Length(k) + utf8Length(old)) + utf8Length(k) + utf8Length(v);
    if (used > STORAGE_QUOTA_BYTES) throw new DOMException(`Setting the value of '${k.slice(0, 64)}' exceeded the quota (${STORAGE_QUOTA_BYTES} bytes per area)`, 'QuotaExceededError');
    data.set(k, v);
    storageUsed[area] = used;
    storageDirty.add(area);
  };
  const removeItem = (key: unknown) => {
    const k = String(key);
    const old = data.get(k);
    if (old === undefined) return;
    data.delete(k);
    storageUsed[area] -= utf8Length(k) + utf8Length(old);
    storageDirty.add(area);
  };
  const api: any = {
    getItem: (k: unknown) => (data.has(String(k)) ? data.get(String(k))! : null),
    setItem,
    removeItem,
    clear: () => { if (!data.size) return; data.clear(); storageUsed[area] = 0; storageDirty.add(area); },
    key: (i: number) => [...data.keys()][i] ?? null,
  };
  return new Proxy(api, {
    get: (t, p) => (p === 'length' ? data.size : typeof p === 'string' && !(p in t) ? data.get(p) : t[p as any]),
    set: (t, p, v) => { if (typeof p === 'string') setItem(p, v); return true; },
    has: (t, p) => p in t || (typeof p === 'string' && data.has(p)),
    deleteProperty: (t, p) => { if (typeof p === 'string') removeItem(p); return true; },
    ownKeys: () => [...data.keys()],
    getOwnPropertyDescriptor: (t, p) => (typeof p === 'string' && data.has(p) ? { value: data.get(p), writable: true, enumerable: true, configurable: true } : undefined),
  });
}

// ───────────────────────── locale (docs/sdk-v1.md §2.9) ─────────────────────────

let locale = DEFAULT_LOCALE;
/** The client's UI language (checked by the glue; anything else is the default). */
export function setLocale(l: unknown) {
  locale = checkLocale(l) ?? DEFAULT_LOCALE;
}

// ───────────────────────── Intl (delegated to the host's ICU) ─────────────────────────

const intlCache = new Map<string, string>();
function intl(req: any): string {
  // No locale argument means the client's language, as in a browser whose UI language it is.
  if (req.locale === undefined || req.locale === null) req.locale = locale;
  const key = JSON.stringify(req);
  let v = intlCache.get(key);
  if (v === undefined) {
    v = String(host('intl', key) ?? '');
    if (intlCache.size > 5000) intlCache.clear();
    intlCache.set(key, v);
  }
  return v;
}
const parse = (s: string, fallback: any) => { try { return JSON.parse(s); } catch { return fallback; } };

const DATE_FIELDS = ['weekday', 'year', 'month', 'day'];
const TIME_FIELDS = ['dayPeriod', 'hour', 'minute', 'second', 'fractionalSecondDigits'];
/**
 * ECMA-402 ToDateTimeOptions, once for every host: without a date or time field and without a
 * style, a format gets numeric year, month and day (DateTimeFormat, toLocaleDateString), hour,
 * minute and second (toLocaleTimeString), or both (toLocaleString). Option handling only, no
 * locale data, so the host's formatter always receives explicit fields. A copy: the widget's
 * object is left alone.
 */
function dateTimeOptions(opts: any, required: 'date' | 'time' | 'any', defaults: 'date' | 'time' | 'all') {
  const o: any = opts !== null && (typeof opts === 'object' || typeof opts === 'function') ? { ...opts } : {};
  const has = (keys: string[]) => keys.some((k) => o[k] !== undefined);
  if (o.dateStyle !== undefined || o.timeStyle !== undefined) return o;
  if (required !== 'time' && has(DATE_FIELDS)) return o;
  if (required !== 'date' && has(TIME_FIELDS)) return o;
  if (defaults !== 'time') o.year = o.month = o.day = 'numeric';
  if (defaults !== 'date') o.hour = o.minute = o.second = 'numeric';
  return o;
}
const DATE_METHODS = { toLocaleString: ['any', 'all'], toLocaleDateString: ['date', 'date'], toLocaleTimeString: ['time', 'time'] } as const;

function installIntl() {
  class NumberFormat {
    _l: any; _o: any;
    constructor(locale?: any, opts?: any) { this._l = locale; this._o = opts; this.format = this.format.bind(this); }
    format(value: any) { return intl({ kind: 'number', locale: this._l, opts: this._o, value: Number(value) }); }
    formatToParts(value: any) { return parse(intl({ kind: 'numberParts', locale: this._l, opts: this._o, value: Number(value) }), []); }
    resolvedOptions() { return parse(intl({ kind: 'numberOptions', locale: this._l, opts: this._o }), {}); }
    static supportedLocalesOf(l: any) { return [].concat(l ?? []); }
  }
  class DateTimeFormat {
    _l: any; _o: any;
    constructor(locale?: any, opts?: any) { this._l = locale; this._o = dateTimeOptions(opts, 'any', 'date'); this.format = this.format.bind(this); }
    format(value?: any) { return intl({ kind: 'date', locale: this._l, opts: this._o, value: value === undefined ? Date.now() : +new Date(value) }); }
    formatToParts(value?: any) { return parse(intl({ kind: 'dateParts', locale: this._l, opts: this._o, value: value === undefined ? Date.now() : +new Date(value) }), []); }
    resolvedOptions() { return parse(intl({ kind: 'dateOptions', locale: this._l, opts: this._o }), {}); }
    static supportedLocalesOf(l: any) { return [].concat(l ?? []); }
  }
  class PluralRules {
    _l: any; _o: any;
    constructor(locale?: any, opts?: any) { this._l = locale; this._o = opts; }
    select(n: number) { return intl({ kind: 'plural', locale: this._l, opts: this._o, value: Number(n) }); }
  }
  class RelativeTimeFormat {
    _l: any; _o: any;
    constructor(locale?: any, opts?: any) { this._l = locale; this._o = opts; }
    format(value: number, unit: string) { return intl({ kind: 'relative', locale: this._l, opts: this._o, value: Number(value), unit: String(unit) }); }
  }
  class ListFormat {
    _l: any; _o: any;
    constructor(locale?: any, opts?: any) { this._l = locale; this._o = opts; }
    format(list: any[]) { return intl({ kind: 'list', locale: this._l, opts: this._o, value: [...list].map(String) }); }
  }
  class Collator {
    _l: any; _o: any;
    constructor(locale?: any, opts?: any) { this._l = locale; this._o = opts; this.compare = this.compare.bind(this); }
    compare(a: string, b: string) { return Number(intl({ kind: 'compare', locale: this._l, opts: this._o, a: String(a), b: String(b) })); }
  }
  G.Intl = { NumberFormat, DateTimeFormat, PluralRules, RelativeTimeFormat, ListFormat, Collator, getCanonicalLocales: (l: any) => [].concat(l ?? []) };
  const def = (proto: any, name: string, value: any) => Object.defineProperty(proto, name, { value, writable: true, configurable: true });
  def(Number.prototype, 'toLocaleString', function (this: number, l?: any, o?: any) { return intl({ kind: 'number', locale: l, opts: o, value: Number(this) }); });
  for (const [method, [required, defaults]] of Object.entries(DATE_METHODS)) {
    def(Date.prototype, method, function (this: Date, l?: any, o?: any) { return intl({ kind: 'dateMethod', method, locale: l, opts: dateTimeOptions(o, required, defaults), value: this.getTime() }); });
  }
  def(String.prototype, 'localeCompare', function (this: string, other: string, l?: any, o?: any) { return Number(intl({ kind: 'compare', locale: l, opts: o, a: String(this), b: String(other) })); });
}

// ───────────────────────── matchMedia / observers ─────────────────────────

const mediaLists = new Set<any>();

function matchMedia(media: string) {
  const mql: any = new EventTarget();
  mql.media = String(media);
  mql._last = undefined;
  Object.defineProperty(mql, 'matches', { get: () => (mql._last = query({ q: 'matchMedia', query: mql.media }).matches === true) });
  mql.onchange = null;
  mql.addListener = (fn: any) => mql.addEventListener('change', fn);
  mql.removeListener = (fn: any) => mql.removeEventListener('change', fn);
  const add = mql.addEventListener.bind(mql);
  mql.addEventListener = (type: string, fn: any, o?: any) => { mediaLists.add(mql); mql._last ??= mql.matches; add(type, fn, o); };
  return mql;
}

/** Viewport or color scheme changed: re-evaluate media queries that have listeners. */
export function mediaChanged() {
  invalidateLayout();
  for (const mql of mediaLists) {
    const before = mql._last;
    const now = mql.matches;
    if (before !== undefined && before !== now) {
      const ev: any = new Event('change');
      ev.matches = now;
      ev.media = mql.media;
      try {
        if (typeof mql.onchange === 'function') mql.onchange(ev);
        mql.dispatchEvent(ev);
      } catch (e) { reportError(e); }
    }
  }
}

const resizeObservers = new Set<any>();
class ResizeObserver {
  _cb: any; _targets = new Set<any>();
  constructor(cb: any) { this._cb = cb; }
  observe(el: any) { if (!el || this._targets.has(el)) return; this._targets.add(el); resizeObservers.add(this); callMethod(el, 'observeResize'); }
  unobserve(el: any) { if (this._targets.delete(el)) callMethod(el, 'unobserveResize'); }
  disconnect() { for (const el of this._targets) callMethod(el, 'unobserveResize'); this._targets.clear(); resizeObservers.delete(this); }
}
export function fireResize(entries: { id: number; width: number; height: number }[], lookup: (id: number) => any) {
  invalidateLayout();
  for (const ro of [...resizeObservers]) {
    const mine: any[] = [];
    for (const e of entries) {
      const target = lookup(e.id);
      if (!target || !ro._targets.has(target)) continue;
      const rect = { x: 0, y: 0, top: 0, left: 0, width: e.width, height: e.height, right: e.width, bottom: e.height };
      const size = [{ inlineSize: e.width, blockSize: e.height }];
      mine.push({ target, contentRect: rect, contentBoxSize: size, borderBoxSize: size, devicePixelContentBoxSize: [{ inlineSize: e.width * G.devicePixelRatio, blockSize: e.height * G.devicePixelRatio }] });
    }
    if (mine.length) { try { ro._cb(mine, ro); } catch (err) { reportError(err); } }
  }
}

class IntersectionObserver {
  _cb: any; root = null; rootMargin = '0px'; thresholds = [0];
  constructor(cb: any) { this._cb = cb; unsupported('IntersectionObserver (emulated: every observed element reports as visible)'); }
  observe(target: any) {
    G.setTimeout(() => {
      try {
        const rect = target.getBoundingClientRect();
        this._cb([{ target, isIntersecting: true, intersectionRatio: 1, boundingClientRect: rect, intersectionRect: rect, rootBounds: null, time: G.performance.now() }], this);
      } catch (e) { reportError(e); }
    }, 0);
  }
  unobserve() {}
  disconnect() {}
  takeRecords() { return []; }
}

// ───────────────────────── network stubs (invariant 5) ─────────────────────────

export class NetworkDisabledError extends Error {
  constructor(api: string) {
    super(`${api} is not available: widgets have no network access`);
    this.name = 'NetworkDisabledError';
  }
}
function blocked(api: string, detail?: unknown): NetworkDisabledError {
  diag('network-blocked', detail === undefined ? api : `${api} ${String(detail).slice(0, 200)}`);
  return new NetworkDisabledError(api);
}

function installNetworkStubs() {
  const ctor = (name: string) => {
    const C: any = function (this: any, arg?: unknown) { throw blocked(name, typeof arg === 'string' ? arg : undefined); };
    Object.defineProperty(C, 'name', { value: name });
    return C;
  };
  // fetch reports the failure the way network errors surface in real code: as a rejection.
  G.fetch = (input: any) => Promise.reject(blocked('fetch', typeof input === 'string' ? input : input?.url));
  for (const name of ['XMLHttpRequest', 'WebSocket', 'EventSource', 'RTCPeerConnection', 'webkitRTCPeerConnection', 'RTCDataChannel', 'Worker', 'SharedWorker', 'WebTransport', 'BroadcastChannel']) G[name] = ctor(name);
  G.importScripts = (...urls: string[]) => { throw blocked('importScripts', urls[0]); };
  G.Request = ctor('Request');
}

// ───────────────────────── bridge: TelegramWidget (docs/sdk-v1.md §2) ─────────────────────────

let colorScheme: 'light' | 'dark' = 'light';
let themeParams: Readonly<ThemeParams> = Object.freeze(completeThemeParams({}, 'light'));
const WIDGET_EVENTS = ['themeChanged', 'activated', 'deactivated', 'chatDataChanged', 'publicDataChanged', 'contentSafeAreaChanged'];
const widgetHandlers = new Map<string, Function[]>(WIDGET_EVENTS.map((t) => [t, []]));
const themeCallbacks: Function[] = [];

function fireWidgetEvent(type: string) {
  for (const cb of [...widgetHandlers.get(type)!]) { try { cb.call(G.TelegramWidget); } catch (e) { reportError(e); } }
}

/**
 * The host's theme: at boot (`notify` false) and on every change. A change of the
 * scheme or of any colour gives the widget a fresh frozen `themeParams`, then
 * `themeChanged` and the legacy `onTheme` callbacks, in one task.
 */
export function setTheme(theme: unknown, notify: boolean) {
  const t = (typeof theme === 'object' && theme !== null ? theme : { theme }) as Record<string, unknown>;
  const scheme = t.theme === 'dark' || t.colorScheme === 'dark' ? 'dark' : 'light';
  const params = completeThemeParams(t.themeParams ?? t.params, scheme);
  const changed = scheme !== colorScheme || Object.keys(params).some((k) => (params as any)[k] !== (themeParams as any)[k]);
  colorScheme = scheme;
  if (changed) themeParams = Object.freeze(params);
  if (!notify || !changed) return;
  fireWidgetEvent('themeChanged');
  for (const cb of [...themeCallbacks]) { try { cb(colorScheme); } catch (e) { reportError(e); } }
}

let contentSafeArea: Readonly<ContentSafeArea> = NO_CONTENT_SAFE_AREA;

/**
 * What the client draws over the widget (docs/sdk-v1.md §2.17): at boot (`notify` false) and on every change. A
 * change gives the widget a fresh frozen `contentSafeAreaInset`, then `contentSafeAreaChanged`, in one task (the
 * renderer has the root variables from the same call of the host).
 */
export function setContentSafeArea(area: unknown, notify: boolean) {
  const next = checkContentSafeArea(area);
  if (CONTENT_SAFE_AREA_FIELDS.every((k) => next[k] === contentSafeArea[k])) return;
  contentSafeArea = Object.freeze(next);
  // The variables moved boxes in the renderer: no cached layout answer survives.
  invalidateLayout();
  if (notify) fireWidgetEvent('contentSafeAreaChanged');
}

/** Visibility changed (the caller has flipped `document.hidden`): `activated` / `deactivated`. */
export function fireActivation() {
  fireWidgetEvent(hidden ? 'deactivated' : 'activated');
}

const bridge = (method: string, ...args: unknown[]) => host('bridge', JSON.stringify([method, args]));

function link(method: 'openLink' | 'openTelegramLink', url: unknown) {
  const checked = checkLink(method, url);
  if (checked === null) {
    throw new TypeError(method === 'openLink'
      ? 'openLink: expected an absolute https: URL of at most 256 characters, without a user name, not on t.me (use openTelegramLink)'
      : 'openTelegramLink: expected https://t.me/… (or telegram.me, telegram.dog) of at most 256 characters');
  }
  bridge(method, checked);
}

/**
 * A link the widget did not open through the SDK (`<a href>`, `window.open`):
 * Telegram hosts go to openTelegramLink, others to openLink; what neither
 * accepts is ignored with a diagnostic, never an exception.
 */
export function followLink(href: unknown) {
  const url = String(href ?? '').trim();
  if (/^javascript:/i.test(url)) return diag('navigation-blocked', 'javascript: link ignored');
  if (checkLink('openTelegramLink', url) !== null) return link('openTelegramLink', url);
  if (checkLink('openLink', url) !== null) return link('openLink', url);
  diag('navigation-blocked', `link ignored (openLink takes https: URLs of at most 256 characters): ${url.slice(0, 200)}`);
}

function haptic(kind: string, value?: unknown) {
  if (kind === 'impact' && !HAPTIC_IMPACT_STYLES.includes(value as string)) throw new TypeError(`HapticFeedback.impactOccurred: style must be one of ${HAPTIC_IMPACT_STYLES.join(', ')}`);
  if (kind === 'notification' && !HAPTIC_NOTIFICATION_TYPES.includes(value as string)) throw new TypeError(`HapticFeedback.notificationOccurred: type must be one of ${HAPTIC_NOTIFICATION_TYPES.join(', ')}`);
  if (kind === 'selection') bridge('haptic', 'selection');
  else bridge('haptic', kind, value);
  return HapticFeedback;
}
const HapticFeedback = Object.freeze({
  impactOccurred: (style: unknown) => haptic('impact', style),
  notificationOccurred: (type: unknown) => haptic('notification', type),
  selectionChanged: () => haptic('selection'),
});

/** The anchor the glue has: only a change goes over the bridge, before the ops of the change it anchors. */
let resizeAnchor: ResizeAnchor = DEFAULT_RESIZE_ANCHOR;
let watchingPublicData = false;
/** `watchPublicData` / `unwatchPublicData`: the host keeps the latest; only changes go out. */
function watchPublicData(on: boolean) {
  if (on === watchingPublicData) return;
  watchingPublicData = on;
  bridge('watchPublicData', on);
}
function setResizeAnchor(edge: unknown) {
  const checked = checkResizeAnchor(edge);
  if (checked === null) throw new TypeError("setResizeAnchor: edge must be 'top' or 'bottom'");
  if (checked === resizeAnchor) return;
  resizeAnchor = checked;
  bridge('setResizeAnchor', checked);
}

// ── the bot's data, where the widget is, requests (docs/sdk-v1.md §2.13–§2.15) ──

let chatData: string | null = null;
let publicData: string | null = null;
let isCopy = false;
let canQuery = false;
let isPreview = false;
let chatType: ChatType | null = null;
let viewer: Readonly<WidgetUser> | null = null;
/** The client has the wallet for this user and the widget may ask the bot (docs/sdk-v1.md §2.18). */
let walletAvailable = false;

/**
 * The glue's context at boot (already decided there from the server's flags: a copy never has chatData, and has no
 * chatType or viewer unless it may request); checked again, the shim trusts nothing.
 */
export function configureWidget(config: Record<string, unknown>) {
  isCopy = config.isCopy === true;
  isPreview = config.isPreview === true;
  canQuery = config.canQuery === true && !isPreview;
  chatData = isCopy ? null : checkWidgetDataValue(config.chatData);
  publicData = checkWidgetDataValue(config.publicData);
  chatType = checkChatType(config.chatType);
  const v = checkViewer(config.viewer).viewer;
  viewer = v && Object.freeze(v);
  walletAvailable = config.wallet === true && canQuery;
}

/** The client's `setData`: the full state of both strings; `chatDataChanged`, then `publicDataChanged`, for each that differs, in this task. */
function applyData(data: unknown) {
  const d = (typeof data === 'object' && data !== null ? data : {}) as Record<string, unknown>;
  const c = isCopy ? null : checkWidgetDataValue(d.chatData);
  const p = checkWidgetDataValue(d.publicData);
  const chatChanged = c !== chatData, publicChanged = p !== publicData;
  chatData = c;
  publicData = p;
  if (chatChanged) fireWidgetEvent('chatDataChanged');
  if (publicChanged) fireWidgetEvent('publicDataChanged');
}

const REQUEST_MESSAGES: Record<string, string> = {
  preview: 'a preview (an inline result not sent yet) cannot ask the bot',
  'no-wallet': 'the client has no wallet for this user (TelegramWidget.wallet is null)',
  disabled: 'requests are off for this message (the bot did not allow them here)',
  'no-activation': 'it needs a tap on the widget and runs in that tap\'s handler, one call per tap; background requests only where the client allows them (the private chat with the bot)',
  unsupported: 'this client does not deliver requests',
  BUSY: 'a request is already in flight; wait for its answer',
  RATE_LIMITED: 'too many requests (background requests at most once per 5 s); try again later',
  TIMEOUT: 'the bot did not answer in time',
  FAILED: 'the client could not deliver the request',
  INVALID_ANSWER: "the bot's answer broke the limits (a result over 1024 bytes, or not text)",
};
/** What differs for a wallet call. */
const WALLET_MESSAGES: Record<string, string> = {
  'no-activation': 'it needs a tap on the widget and runs in that tap\'s handler (one call per tap), or follows within 10 s the answer to a call made by a tap',
  REJECTED: 'the user declined in the wallet window',
  BAD_REQUEST: 'the wallet refused it (the network, the sender, the expiry or the cells)',
  FAILED: 'the client could not complete the call',
};

/** `label`: the call (`request`, `wallet.sendTransaction`, `wallet.signData`). */
function requestError(code: RequestError, reason: unknown, label: string): Error {
  const key = typeof reason === 'string' && code === 'NOT_ALLOWED' ? reason : code;
  const wallet = label !== 'request' ? WALLET_MESSAGES[key] : undefined;
  const e = new Error(label + ': ' + (wallet ?? REQUEST_MESSAGES[key] ?? REQUEST_MESSAGES['no-activation']));
  e.name = 'WidgetRequestError';
  (e as any).code = code;
  return e;
}

let requestSeq = 0;
/**
 * Requests the glue has not answered yet. The glue's gate alone decides which one is in flight (`BUSY` for the
 * others): a refusal comes back as a task of its own, so the shim cannot tell a refused request from one in flight.
 */
const pendingRequests = new Map<number, { resolve: (v: unknown) => void; reject: (e: unknown) => void; label: string }>();

function pending(label: string, send: (id: number) => void): Promise<unknown> {
  const id = ++requestSeq;
  return new Promise((resolve, reject) => {
    pendingRequests.set(id, { resolve, reject, label });
    send(id);
  });
}

function request(data: unknown, options?: unknown): Promise<unknown> {
  const checked = checkRequestData(data);
  if (checked === null) throw new TypeError('request: data must be a string of 1 to 64 bytes (UTF-8), without lone surrogates');
  const opts = options === undefined ? { wallet: false } : checkRequestOptions(options);
  if (opts === null) throw new TypeError('request: options must be an object with at most `wallet` (a boolean)');
  if (!canQuery) return Promise.reject(requestError('NOT_ALLOWED', isPreview ? 'preview' : 'disabled', 'request'));
  if (opts.wallet && !walletAvailable) return Promise.reject(requestError('NOT_ALLOWED', 'no-wallet', 'request'));
  return pending('request', (id) => (opts.wallet ? bridge('request', id, checked, { wallet: true }) : bridge('request', id, checked)));
}

/**
 * A wallet call (docs/sdk-v1.md §2.18): the argument in TON Connect's format, checked here for a clear error and
 * again in the glue; `data` (1–64 bytes) tells the bot what the outcome is for. Resolves with the bot's answer to it.
 */
function walletCall(method: 'sendTransaction' | 'signData', arg: unknown, data: unknown): Promise<unknown> {
  const label = 'wallet.' + method;
  const checked = method === 'sendTransaction' ? checkWalletTransaction(arg) : checkSignDataPayload(arg);
  if (checked === null) {
    throw new TypeError(method === 'sendTransaction'
      ? label + ": expected TON Connect's transaction: { valid_until?, network?, from?, messages: [{ address, amount, payload?, stateInit? }] }, 1 to 255 messages, user-friendly addresses, amounts as decimal strings of nanotons, payloads in base64 (at most 8192 characters each, 65536 together)"
      : label + ": expected TON Connect's payload: { type: 'text', text } (at most 4096 bytes), { type: 'binary', bytes } or { type: 'cell', schema, cell } (base64 of at most 4096 characters); no domain");
  }
  const label64 = checkRequestData(data);
  if (label64 === null) throw new TypeError(label + ': data must be a string of 1 to 64 bytes (UTF-8), without lone surrogates');
  if (!walletAvailable) return Promise.reject(requestError('NOT_ALLOWED', 'no-wallet', label));
  return pending(label, (id) => bridge('wallet', id, method, checked, label64));
}

const walletApi = Object.freeze({
  /** Ask the user to send a transaction in the client's wallet window (TON Connect's `sendTransaction` request); resolves with the bot's answer `{ result }` once it is sent. */
  sendTransaction: (transaction: unknown, data: unknown) => walletCall('sendTransaction', transaction, data),
  /** Ask the user to sign data in the client's wallet window (TON Connect's `signData` payload); the signature goes to the bot, which resolves this with its answer `{ result }`. */
  signData: (payload: unknown, data: unknown) => walletCall('signData', payload, data),
});

/** A `bridge` dispatch from the glue: the bot's data, or the answer to a request. */
export function bridgeEvent(event: unknown) {
  const e = (typeof event === 'object' && event !== null ? event : {}) as Record<string, unknown>;
  if (e.type === 'data') return applyData(e);
  if (e.type !== 'answer') return;
  const p = typeof e.id === 'number' ? pendingRequests.get(e.id) : undefined;
  if (!p) return;
  pendingRequests.delete(e.id as number);
  if (typeof e.error === 'string') {
    p.reject(requestError((REQUEST_ERRORS as readonly string[]).includes(e.error) ? (e.error as RequestError) : 'FAILED', e.reason, p.label));
    return;
  }
  const result = typeof e.result === 'string' ? e.result : null;
  p.resolve(Object.freeze({ result }));
}

function copyText(text: unknown) {
  const checked = checkCopyText(text);
  if (checked === null) throw new TypeError('copyText: text must be a string of 1 to 256 characters (UTF-16), without lone surrogates');
  bridge('copyText', checked);
}

function installBridge() {
  const widget = {
    /** The widget profile this client enforces (docs/profile-v1.md); a later profile only adds features. */
    get profile() { return PROFILE_VERSION; },
    isProfileAtLeast(n: unknown) { return typeof n === 'number' && PROFILE_VERSION >= n; },
    get colorScheme() { return colorScheme; },
    /** The chat's palette as `#rrggbb`, Mini Apps keys; a fresh frozen object per change. */
    get themeParams() { return themeParams; },
    get isActive() { return !hidden; },
    /**
     * What the client draws over the widget, in CSS px (the `--tg-content-safe-area-*` root variables, `env(safe-area-inset-*)`):
     * `bottom` is the band of the message time over a widget with no caption below it, `cornerLeft` / `cornerRight` the
     * client's corner in it; 0 with a caption below. A fresh frozen object per change, then `contentSafeAreaChanged`.
     */
    get contentSafeAreaInset() { return contentSafeArea; },
    onEvent(type: unknown, handler: unknown) {
      const list = widgetHandlers.get(String(type));
      if (!list || typeof handler !== 'function' || list.includes(handler) || list.length >= MAX_HANDLERS_PER_EVENT) return;
      list.push(handler);
    },
    offEvent(type: unknown, handler: unknown) {
      const list = widgetHandlers.get(String(type));
      const i = list ? list.indexOf(handler as Function) : -1;
      if (i >= 0) list!.splice(i, 1);
    },
    /** Ask the client to open an external https: page (the user confirms in a native dialog); needs a tap. */
    openLink(url: unknown) { link('openLink', url); },
    /** Ask the client to open a t.me link through its own handling; needs a tap. */
    openTelegramLink(url: unknown) { link('openTelegramLink', url); },
    HapticFeedback,
    /** Signal that the widget has rendered its initial state. */
    ready() { bridge('ready'); },
    /**
     * The edge of the bubble that stays put on screen when the widget's height changes, from now on: `'top'` (the
     * default: the bubble grows and shrinks downward) or `'bottom'` (it grows upward, its bottom stays).
     */
    setResizeAnchor(edge: unknown) { setResizeAnchor(edge); },
    /**
     * Ask for live shared data: while the widget is on screen the client polls its `publicData` (a shared widget's,
     * at the server's period) and `publicDataChanged` brings the new value. Without it the widget gets `publicData`
     * with the message and the bot's pushed edits only. No tap needed; calling it again changes nothing.
     */
    watchPublicData() { watchPublicData(true); },
    /** Stop asking for live shared data (`watchPublicData`). */
    unwatchPublicData() { watchPublicData(false); },
    /** What the bot keeps for this message (in a group every member sees it; a forward never gets it); null for none or in a copy. `chatDataChanged` when the bot edits it. */
    get chatData() { return chatData; },
    /** What the bot keeps for everyone, a forward included (a snapshot, or a value shared with the original); null for none. `publicDataChanged` when it changes. */
    get publicData() { return publicData; },
    /** This message is a forwarded copy: no `chatData`; requests only if the bot sent the widget live, on a tap. */
    get isForward() { return isCopy; },
    /** An inline result shown before it is sent: data only, no input, no requests, nothing about the user. */
    get isPreview() { return isPreview; },
    /** The kind of chat the widget is in (`private`, `group`, `supergroup`, `channel`); null in a copy that cannot request, or when the client does not say. */
    get chatType() { return chatType; },
    /** Who looks at the widget, `{ id }` (the Telegram user id the bot gets with a request); null in a copy that cannot request. */
    get viewer() { return viewer; },
    /**
     * Ask the bot (a callback button inside the widget): `data` of 1–64 bytes; resolves with `{ result }`, rejects with `error.code`. Needs a tap, except in the private chat with the bot.
     * `{ wallet: true }` (on a tap only, where `wallet` is not null): the bot gets the user's wallet with it, created if the user had none.
     */
    request(data: unknown, options?: unknown) { return request(data, options); },
    /**
     * Gram Wallet (docs/sdk-v1.md §2.18): `sendTransaction(transaction, data)` and `signData(payload, data)` in TON Connect's
     * formats, each confirmed by the user in the client's own window; on a tap, or within 10 s of the answer to a call made by a tap.
     * Null where the client has no wallet for this user, where the widget cannot ask the bot, and in a preview.
     */
    get wallet() { return walletAvailable ? walletApi : null; },
    /** Copy text to the clipboard (the client shows what it copied); 1–256 characters; needs a tap. */
    copyText(text: unknown) { copyText(text); },
    /** Deprecated alias of `colorScheme`. */
    get theme() { return colorScheme; },
    /** Deprecated: `onEvent('themeChanged', …)`; called at once with the colour scheme and on every change. */
    onTheme(cb: unknown) {
      if (typeof cb !== 'function' || themeCallbacks.length >= MAX_HANDLERS_PER_EVENT) return;
      themeCallbacks.push(cb);
      try { cb(colorScheme); } catch (e) { reportError(e); }
    },
  };
  G.TelegramWidget = widget;
}

// ───────────────────────── misc polyfills ─────────────────────────

function installPolyfills() {
  // atob/btoa live in prelude.ts (needed before linkedom loads).
  G.TextEncoder = class {
    encoding = 'utf-8';
    encode(s = '') {
      const bin = unescape(encodeURIComponent(String(s)));
      const out = new Uint8Array(bin.length);
      for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
      return out;
    }
  };
  G.TextDecoder = class {
    encoding = 'utf-8';
    decode(buf?: any) {
      if (!buf) return '';
      const bytes = buf instanceof Uint8Array ? buf : new Uint8Array(buf.buffer ?? buf);
      let bin = '';
      for (let i = 0; i < bytes.length; i++) bin += String.fromCharCode(bytes[i]);
      try { return decodeURIComponent(escape(bin)); } catch { return bin; }
    }
  };
  G.queueMicrotask ??= (fn: () => void) => { Promise.resolve().then(fn).catch(reportError); };
  G.structuredClone ??= (v: any) => (v === undefined ? undefined : JSON.parse(JSON.stringify(v)));
  G.crypto = {
    getRandomValues(arr: any) {
      const bytes: number[] = JSON.parse(host('random', String(arr.byteLength)));
      new Uint8Array(arr.buffer, arr.byteOffset, arr.byteLength).set(bytes);
      return arr;
    },
    randomUUID() {
      const b: number[] = JSON.parse(host('random', '16'));
      b[6] = (b[6] & 15) | 64;
      b[8] = (b[8] & 63) | 128;
      const h = b.map((x) => x.toString(16).padStart(2, '0')).join('');
      return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`;
    },
  };
  class URLSearchParams {
    _e: [string, string][] = [];
    constructor(init: any = '') {
      if (typeof init === 'string') {
        for (const part of init.replace(/^\?/, '').split('&')) {
          if (!part) continue;
          const [k, v = ''] = part.split('=');
          this._e.push([decodeURIComponent(k.replace(/\+/g, ' ')), decodeURIComponent(v.replace(/\+/g, ' '))]);
        }
      } else if (init && typeof init === 'object') for (const [k, v] of typeof init[Symbol.iterator] === 'function' ? init : Object.entries(init)) this._e.push([String(k), String(v)]);
    }
    get(k: string) { return this._e.find(([n]) => n === k)?.[1] ?? null; }
    getAll(k: string) { return this._e.filter(([n]) => n === k).map(([, v]) => v); }
    has(k: string) { return this._e.some(([n]) => n === k); }
    set(k: string, v: string) { this.delete(k); this._e.push([String(k), String(v)]); }
    append(k: string, v: string) { this._e.push([String(k), String(v)]); }
    delete(k: string) { this._e = this._e.filter(([n]) => n !== k); }
    forEach(cb: any) { for (const [k, v] of this._e) cb(v, k, this); }
    entries() { return this._e[Symbol.iterator](); }
    keys() { return this._e.map(([k]) => k)[Symbol.iterator](); }
    values() { return this._e.map(([, v]) => v)[Symbol.iterator](); }
    [Symbol.iterator]() { return this.entries(); }
    toString() { return this._e.map(([k, v]) => encodeURIComponent(k) + '=' + encodeURIComponent(v)).join('&'); }
  }
  G.URLSearchParams = URLSearchParams;
  /** String-only URL: parsing never touches the network. */
  class URL {
    [k: string]: any;
    constructor(url: any, base?: any) {
      let s = String(url);
      if (!/^[a-z][a-z0-9+.-]*:/i.test(s) && base !== undefined) s = String(base).replace(/[^/]*$/, '') + s;
      const m = /^([a-z][a-z0-9+.-]*:)(?:\/\/([^/?#]*))?([^?#]*)(\?[^#]*)?(#.*)?$/i.exec(s);
      if (!m) throw new TypeError('Invalid URL: ' + s);
      this.protocol = m[1].toLowerCase();
      this.host = m[2] ?? '';
      const [hostname, port = ''] = this.host.replace(/^.*@/, '').split(':');
      this.hostname = hostname;
      this.port = port;
      this.pathname = m[3] || (m[2] !== undefined ? '/' : '');
      this.search = m[4] ?? '';
      this.hash = m[5] ?? '';
      this.searchParams = new URLSearchParams(this.search);
      this.origin = m[2] !== undefined ? `${this.protocol}//${this.host}` : 'null';
    }
    get href() { return `${this.protocol}${this.host || this.protocol === 'file:' ? '//' + this.host : ''}${this.pathname}${this.search}${this.hash}`; }
    toString() { return this.href; }
    toJSON() { return this.href; }
    static createObjectURL() { unsupported('URL.createObjectURL'); return ''; }
    static revokeObjectURL() {}
    static canParse(u: any, b?: any) { try { new URL(u, b); return true; } catch { return false; } }
  }
  G.URL = URL;
  class AbortSignal extends EventTarget { aborted = false; reason: any = undefined; throwIfAborted() { if (this.aborted) throw this.reason; } }
  G.AbortSignal = AbortSignal;
  G.AbortController = class {
    signal = new AbortSignal();
    abort(reason?: any) {
      if (this.signal.aborted) return;
      this.signal.aborted = true;
      this.signal.reason = reason ?? new Error('AbortError');
      this.signal.dispatchEvent(new Event('abort'));
    }
  };
  G.Blob = class { size = 0; type = ''; constructor(parts: any[] = [], opts: any = {}) { this.type = opts.type ?? ''; this.size = parts.reduce((n, p) => n + (p?.length ?? p?.byteLength ?? 0), 0); unsupported('Blob'); } };
  G.FileReader = class { constructor() { unsupported('FileReader'); } readAsText() {} readAsDataURL() {} readAsArrayBuffer() {} addEventListener() {} };
  G.CSS = { supports: cssSupports, escape: (s: string) => String(s).replace(/[^\w-]/g, (c) => '\\' + c) };
}

// ───────────────────────── window ─────────────────────────

export interface WindowDeps {
  document: any;
  getComputedStyle: (el: any, pseudo?: string | null) => any;
  viewport: { innerWidth: number; innerHeight: number; dpr: number };
}

export function setViewport(v: { innerWidth: number; innerHeight: number; dpr: number }) {
  G.innerWidth = G.outerWidth = v.innerWidth;
  G.innerHeight = G.outerHeight = v.innerHeight;
  G.devicePixelRatio = v.dpr;
  G.screen = { width: v.innerWidth, height: v.innerHeight, availWidth: v.innerWidth, availHeight: v.innerHeight, colorDepth: 24, pixelDepth: 24, orientation: { type: 'portrait-primary', angle: 0, addEventListener() {} } };
}

export function installWindow(deps: WindowDeps) {
  installConsole();
  installPolyfills();
  installIntl();
  installNetworkStubs();
  installBridge();

  // These are [Replaceable] in browsers: `var frames = 0` / `top = …` must not
  // fail silently and leave the window object in a numeric variable.
  for (const name of ['window', 'self', 'top', 'parent', 'frames']) {
    try { Object.defineProperty(G, name, { value: G, writable: true, configurable: true, enumerable: false }); } catch {}
  }
  G.document = deps.document;
  setViewport(deps.viewport);
  G.scrollX = G.scrollY = G.pageXOffset = G.pageYOffset = 0;
  G.length = 0;
  G.name = '';
  G.closed = false;
  G.isSecureContext = true;
  G.origin = 'null';

  const t0 = Number(host('now'));
  originTime = t0;
  G.performance = {
    now: () => Number(host('now')) - t0,
    timeOrigin: Date.now(),
    mark() {}, measure() {}, getEntriesByName: () => [], getEntriesByType: () => [], clearMarks() {}, clearMeasures() {},
  };
  G.setTimeout = (fn: any, delay?: any, ...args: any[]) => setTimer(fn, delay, args, false);
  G.setInterval = (fn: any, delay?: any, ...args: any[]) => setTimer(fn, delay, args, true);
  G.clearTimeout = G.clearInterval = clearTimer;
  G.requestAnimationFrame = (cb: (t: number) => void) => {
    const id = frameSeq++;
    if (!frameCallbacks.size) host('raf');
    frameCallbacks.set(id, cb);
    return id;
  };
  G.cancelAnimationFrame = (id: number) => { frameCallbacks.delete(id); };
  G.requestIdleCallback = (cb: any) => G.setTimeout(() => cb({ didTimeout: false, timeRemaining: () => 10 }), 1);
  G.cancelIdleCallback = clearTimer;

  G.localStorage = makeStorage('local');
  G.sessionStorage = makeStorage('session');
  G.DOMException = DOMException;
  G.matchMedia = matchMedia;
  G.ResizeObserver = ResizeObserver;
  G.IntersectionObserver = IntersectionObserver;
  G.getComputedStyle = deps.getComputedStyle;
  G.reportError = (e: unknown) => reportError(e);

  // Navigation never happens. Reads return an inert URL; writes are recorded and dropped.
  const navBlocked = (what: string, url?: unknown) => diag('navigation-blocked', url === undefined ? what : `${what} → ${String(url).slice(0, 200)}`);
  const location: any = {
    protocol: 'about:', host: '', hostname: '', port: '', pathname: 'srcdoc', search: '', hash: '', origin: 'null',
    assign: (u: unknown) => navBlocked('location.assign', u),
    replace: (u: unknown) => navBlocked('location.replace', u),
    reload: () => navBlocked('location.reload'),
    toString: () => 'about:srcdoc',
  };
  Object.defineProperty(location, 'href', { get: () => 'about:srcdoc', set: (u) => navBlocked('location.href', u), enumerable: true });
  Object.defineProperty(G, 'location', { get: () => location, set: (u) => navBlocked('location', u), configurable: true });
  Object.defineProperty(deps.document, 'location', { get: () => location, set: (u) => navBlocked('document.location', u), configurable: true });
  G.history = { length: 1, state: null, pushState() {}, replaceState() {}, back() {}, forward() {}, go() {}, scrollRestoration: 'auto' };
  // window.open goes through the same user-confirmed flow as a link tap.
  G.open = (url?: unknown) => { if (url) followLink(url); return null; };
  G.close = () => {};
  G.print = () => unsupported('window.print');
  G.postMessage = () => unsupported('window.postMessage');
  // No dialog and nothing for the host (docs/sdk-v1.md §2.10).
  G.alert = () => { unsupported('alert/confirm/prompt (blocking dialogs)'); };
  G.confirm = () => { unsupported('alert/confirm/prompt (blocking dialogs)'); return false; };
  G.prompt = () => { unsupported('alert/confirm/prompt (blocking dialogs)'); return null; };
  G.scrollTo = G.scroll = G.scrollBy = () => {};
  G.focus = G.blur = () => {};
  G.getSelection = () => ({ rangeCount: 0, isCollapsed: true, toString: () => '', removeAllRanges() {}, addRange() {}, getRangeAt: () => null, anchorNode: null, focusNode: null });
  const notAllowed = (api: string) => { unsupported(api); return Promise.reject(new DOMException(`${api} is not available in a widget`, 'NotAllowedError')); };
  G.navigator = {
    userAgent: 'Mozilla/5.0 TelegramWidget/1.0', get language() { return locale; }, get languages() { return Object.freeze([locale]); }, platform: 'TelegramWidget', vendor: '',
    onLine: false, cookieEnabled: false, hardwareConcurrency: 1, maxTouchPoints: 0, deviceMemory: 1,
    sendBeacon: (url?: unknown) => { throw blocked('navigator.sendBeacon', url); },
    clipboard: {
      writeText: () => notAllowed('navigator.clipboard'),
      readText: () => notAllowed('navigator.clipboard'),
      write: () => notAllowed('navigator.clipboard'),
      read: () => notAllowed('navigator.clipboard'),
    },
    vibrate: () => false,
    share: () => notAllowed('navigator.share'),
    canShare: () => false,
    mediaDevices: undefined, geolocation: undefined, serviceWorker: undefined,
  };
  G.addEventListener = addEventListener;
  G.removeEventListener = removeEventListener;
  G.dispatchEvent = dispatchEvent;
}

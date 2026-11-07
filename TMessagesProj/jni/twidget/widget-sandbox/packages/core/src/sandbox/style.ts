// CSSStyleDeclaration for element.style. Replaces linkedom's, which splits on
// ';' and therefore breaks on data: URLs. The style attribute stays the single
// source of truth: every write re-serializes into it, and capture.ts turns the
// attribute change into per-property ops.

import { document } from './dom';

export interface Decl { value: string; important: boolean }

/** Split a declaration list, respecting quotes, parentheses and escapes. */
export function parseDeclarations(text: string): Map<string, Decl> {
  const out = new Map<string, Decl>();
  let depth = 0;
  let quote = '';
  let start = 0;
  const push = (end: number) => {
    const decl = text.slice(start, end);
    const colon = decl.indexOf(':');
    if (colon > 0) {
      const name = decl.slice(0, colon).trim();
      let value = decl.slice(colon + 1).trim();
      let important = false;
      const m = value.includes('!') ? /!\s*important\s*$/i.exec(value) : null;
      if (m) {
        important = true;
        value = value.slice(0, m.index).trim();
      }
      if (name && value) {
        const key = name.startsWith('--') ? name : name.toLowerCase();
        out.delete(key);
        out.set(key, { value, important });
      }
    }
    start = end + 1;
  };
  if (text.indexOf('"') < 0 && text.indexOf("'") < 0 && text.indexOf('\\') < 0) {
    // No quotes or escapes: jump between the only characters that matter.
    let open = text.indexOf('('), close = text.indexOf(')'), semi = text.indexOf(';');
    for (;;) {
      let at = open < 0 ? close : close < 0 ? open : Math.min(open, close);
      if (semi >= 0 && (at < 0 || semi < at)) at = semi;
      if (at < 0) break;
      if (at === open) {
        depth++;
        open = text.indexOf('(', at + 1);
      } else if (at === close) {
        depth = Math.max(0, depth - 1);
        close = text.indexOf(')', at + 1);
      } else {
        if (depth === 0) push(at);
        semi = text.indexOf(';', at + 1);
      }
    }
    push(text.length);
    return out;
  }
  for (let i = 0; i < text.length; i++) {
    const ch = text[i];
    if (ch === '\\') i++;
    else if (quote) {
      if (ch === quote) quote = '';
    } else if (ch === '"' || ch === "'") quote = ch;
    else if (ch === '(') depth++;
    else if (ch === ')') depth = Math.max(0, depth - 1);
    else if (ch === ';' && depth === 0) push(i);
  }
  push(text.length);
  return out;
}

export function serializeDeclarations(map: Map<string, Decl>): string {
  let out = '';
  for (const [name, d] of map) out += `${out ? ' ' : ''}${name}: ${d.value}${d.important ? ' !important' : ''};`;
  return out;
}

const kebabCache = new Map<string, string>();

export function toKebab(prop: string): string {
  let k = kebabCache.get(prop);
  if (k === undefined) {
    if (kebabCache.size > 1024) kebabCache.clear();
    kebabCache.set(prop, (k = kebabOf(prop)));
  }
  return k;
}

function kebabOf(prop: string): string {
  if (prop.startsWith('--') || prop.includes('-')) return prop;
  if (prop === 'cssFloat') return 'float';
  const k = prop.replace(/[A-Z]/g, (c) => '-' + c.toLowerCase());
  return /^(webkit|moz|ms|o)-/.test(k) ? '-' + k : k;
}

const styles = new WeakMap<object, any>();
const impls = new WeakMap<object, StyleImpl>();

// Fast path for element.style writes (capture.ts `syncPendingStyles`): once the
// host has been told an element's style as a canonical property map, a write
// through element.style updates the attribute value in place, without marking
// the element dirty, and records which properties changed; the next flush sends
// one `style` op per changed property from that record instead of re-parsing
// and diffing the attribute. Anything unusual takes the attribute path.

/** Style declarations written through the fast path since the last flush. */
export const pendingStyles = new Set<StyleImpl>();

/** Attr.prototype.value's own setter, handed over before dirty tracking wraps it. */
let setAttrValue: ((attr: any, value: string) => void) | null = null;
export function setRawAttrValueSetter(set: (attr: any, value: string) => void) {
  setAttrValue = set;
}

const SVG = 'http://www.w3.org/2000/svg';
const canonicalKeys = new Map<string, boolean>();
const KEY = /^(--[A-Za-z0-9_-]+|[a-z][a-z0-9-]*)$/;
const VALUE_FLAT = /[;!\\"']/;
const VALUE_PARENS = /^[^;!\\"'()]*(\([^;!\\"'()]*\)[^;!\\"'()]*)*$/;

/** A property name the parser returns unchanged. */
export function canonicalKey(name: string): boolean {
  let ok = canonicalKeys.get(name);
  if (ok === undefined) {
    if (canonicalKeys.size > 1024) canonicalKeys.clear();
    canonicalKeys.set(name, (ok = KEY.test(name)));
  }
  return ok;
}

/** A value that serializes and re-parses to itself: no `;`, `!`, quotes or escapes, flat balanced parentheses. */
export function canonicalValue(value: string): boolean {
  return value.indexOf('(') < 0 && value.indexOf(')') < 0 ? !VALUE_FLAT.test(value) : VALUE_PARENS.test(value);
}

export function canonicalMap(map: Map<string, Decl>): boolean {
  for (const [name, d] of map) if (!canonicalKey(name) || !canonicalValue(d.value)) return false;
  return true;
}

export function styleImplOf(el: any): StyleImpl | undefined {
  return impls.get(el);
}

/** The host's copy of this element's style no longer comes from a fast-path record. */
export function forgetSentStyle(el: any) {
  const impl = impls.get(el);
  if (impl) impl._sentText = null;
}

export class StyleImpl {
  _el: any;
  _text: string | null = null;
  _map = new Map<string, Decl>();
  /** The element's style Attr while it is owned by the element. */
  _attr: any = null;
  /** The property map and attribute text the host was last told, when canonical. */
  _sent: Map<string, Decl> | null = null;
  _sentText: string | null = null;
  /** Properties written through the fast path since the last flush. */
  _keys: string | Set<string> | null = null;
  /** The map was re-parsed from a text written some other way since the host was told. */
  _reparsed = false;
  constructor(el: any) {
    this._el = el;
  }
  _attrText(): string | null {
    const a = this._attr;
    return a !== null && a.ownerElement === this._el ? String(a.value) : null;
  }
  _read(): Map<string, Decl> {
    let text = this._attrText();
    if (text === null) {
      text = this._el.getAttribute('style') ?? '';
      this._attr = this._el.ownerDocument === document ? this._el.getAttributeNode('style') : null;
    }
    if (text !== this._text) {
      this._text = text;
      this._map = parseDeclarations(text!);
      this._reparsed = true;
    }
    return this._map;
  }
  _write(key?: string) {
    const el = this._el;
    const a = this._attr;
    this._text = serializeDeclarations(this._map);
    if (key !== undefined && setAttrValue !== null && this._sentText !== null && !this._reparsed && a !== null && a.ownerElement === el && el.namespaceURI !== SVG) {
      setAttrValue(a, this._text);
      const k = this._keys;
      if (k === null) {
        this._keys = key;
        pendingStyles.add(this);
      } else if (typeof k === 'string') {
        if (k !== key) this._keys = new Set([k, key]);
      } else k.add(key);
      return;
    }
    this._keys = null;
    el.setAttribute('style', this._text);
  }
  getPropertyValue(name: string): string {
    return this._read().get(toKebab(String(name)))?.value ?? '';
  }
  getPropertyPriority(name: string): string {
    return this._read().get(toKebab(String(name)))?.important ? 'important' : '';
  }
  setProperty(name: string, value: unknown, priority?: string) {
    const key = toKebab(String(name));
    const map = this._read();
    if (value === null || value === undefined || value === '') {
      if (!map.delete(key)) return;
    } else {
      const v = String(value).trim();
      const old = map.get(key);
      const important = priority === 'important';
      if (old && old.value === v && old.important === important) return;
      map.delete(key); // re-append: the latest declaration wins, like CSSOM
      map.set(key, { value: v, important });
    }
    this._write(key);
  }
  removeProperty(name: string): string {
    const key = toKebab(String(name));
    const map = this._read();
    const old = map.get(key)?.value ?? '';
    if (map.delete(key)) this._write(key);
    return old;
  }
  item(i: number): string {
    return [...this._read().keys()][i] ?? '';
  }
  get length(): number {
    return this._read().size;
  }
  get cssText(): string {
    return this._el.getAttribute('style') ?? '';
  }
  set cssText(v: string) {
    this._el.setAttribute('style', String(v));
  }
  [Symbol.iterator]() {
    return this._read().keys();
  }
}

const handler: ProxyHandler<StyleImpl> = {
  get(t, prop) {
    if (typeof prop === 'symbol' || prop in t) {
      const v = (t as any)[prop];
      return typeof v === 'function' ? v.bind(t) : v;
    }
    if (/^\d+$/.test(prop)) return t.item(Number(prop));
    return t.getPropertyValue(prop);
  },
  set(t, prop, value) {
    if (typeof prop === 'symbol') return true;
    if (prop === 'cssText') t.cssText = value;
    else if (!prop.startsWith('_')) t.setProperty(prop, value);
    else (t as any)[prop] = value;
    return true;
  },
  has: () => true,
};

export function styleOf(el: any): any {
  let s = styles.get(el);
  if (!s) {
    const impl = new StyleImpl(el);
    impls.set(el, impl);
    styles.set(el, (s = new Proxy(impl, handler)));
  }
  return s;
}

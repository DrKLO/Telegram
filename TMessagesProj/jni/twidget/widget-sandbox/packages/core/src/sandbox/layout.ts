// Layout and geometry APIs. The sandbox has no layout engine: every answer comes
// from the renderer through the synchronous query bridge (host.ts), cached until
// the next flush.

import { flush, markDirty, mirroredId, nodeById } from './capture';
import { document, SVG_NS } from './dom';
import { query } from './host';
import { toKebab } from './style';

export class DOMRect {
  x: number; y: number; width: number; height: number;
  constructor(x = 0, y = 0, width = 0, height = 0) {
    this.x = x; this.y = y; this.width = width; this.height = height;
  }
  get top() { return Math.min(this.y, this.y + this.height); }
  get left() { return Math.min(this.x, this.x + this.width); }
  get right() { return Math.max(this.x, this.x + this.width); }
  get bottom() { return Math.max(this.y, this.y + this.height); }
  toJSON() { return { x: this.x, y: this.y, width: this.width, height: this.height, top: this.top, left: this.left, right: this.right, bottom: this.bottom }; }
  static fromRect(r: any = {}) { return new DOMRect(r.x, r.y, r.width, r.height); }
}

export class DOMPoint {
  x: number; y: number; z: number; w: number;
  constructor(x = 0, y = 0, z = 0, w = 1) { this.x = x; this.y = y; this.z = z; this.w = w; }
  matrixTransform(m: DOMMatrix) { return m.transformPoint(this); }
}

/** 2D-only DOMMatrix (enough for d3-interpolate, d3-zoom and SVG CTMs). */
export class DOMMatrix {
  a = 1; b = 0; c = 0; d = 1; e = 0; f = 0;
  constructor(init?: any) {
    if (typeof init === 'string') Object.assign(this, parseTransform(init));
    else if (Array.isArray(init) && init.length >= 6) [this.a, this.b, this.c, this.d, this.e, this.f] = init;
    else if (init && typeof init === 'object') for (const k of ['a', 'b', 'c', 'd', 'e', 'f'] as const) if (typeof init[k] === 'number') this[k] = init[k];
  }
  get m11() { return this.a; } get m12() { return this.b; } get m21() { return this.c; } get m22() { return this.d; } get m41() { return this.e; } get m42() { return this.f; }
  get is2D() { return true; }
  get isIdentity() { return this.a === 1 && this.b === 0 && this.c === 0 && this.d === 1 && this.e === 0 && this.f === 0; }
  multiply(o: any) {
    return new DOMMatrix([
      this.a * o.a + this.c * o.b, this.b * o.a + this.d * o.b, this.a * o.c + this.c * o.d, this.b * o.c + this.d * o.d,
      this.a * o.e + this.c * o.f + this.e, this.b * o.e + this.d * o.f + this.f,
    ]);
  }
  inverse() {
    const det = this.a * this.d - this.b * this.c;
    if (!det) return new DOMMatrix([NaN, NaN, NaN, NaN, NaN, NaN]);
    return new DOMMatrix([this.d / det, -this.b / det, -this.c / det, this.a / det, (this.c * this.f - this.d * this.e) / det, (this.b * this.e - this.a * this.f) / det]);
  }
  translate(x = 0, y = 0) { return this.multiply({ a: 1, b: 0, c: 0, d: 1, e: x, f: y }); }
  scale(x = 1, y = x) { return this.multiply({ a: x, b: 0, c: 0, d: y, e: 0, f: 0 }); }
  rotate(deg = 0) {
    const r = (deg * Math.PI) / 180;
    return this.multiply({ a: Math.cos(r), b: Math.sin(r), c: -Math.sin(r), d: Math.cos(r), e: 0, f: 0 });
  }
  transformPoint(p: any = {}) {
    const x = p.x ?? 0, y = p.y ?? 0;
    return new DOMPoint(this.a * x + this.c * y + this.e, this.b * x + this.d * y + this.f);
  }
  toString() { return `matrix(${this.a}, ${this.b}, ${this.c}, ${this.d}, ${this.e}, ${this.f})`; }
}

/** Parse an SVG / CSS 2D transform list into matrix components. */
export function parseTransform(text: string): { a: number; b: number; c: number; d: number; e: number; f: number } {
  let m = new DOMMatrix();
  const re = /(\w+)\s*\(([^)]*)\)/g;
  let match: RegExpExecArray | null;
  while ((match = re.exec(String(text)))) {
    const n = match[2].split(/[\s,]+/).filter(Boolean).map((v) => {
      const num = parseFloat(v);
      return /rad$/.test(v) ? (num * 180) / Math.PI : /turn$/.test(v) ? num * 360 : num;
    });
    switch (match[1]) {
      case 'matrix': m = m.multiply({ a: n[0], b: n[1], c: n[2], d: n[3], e: n[4], f: n[5] }); break;
      case 'translate': m = m.translate(n[0] ?? 0, n[1] ?? 0); break;
      case 'translateX': m = m.translate(n[0] ?? 0, 0); break;
      case 'translateY': m = m.translate(0, n[0] ?? 0); break;
      case 'scale': m = m.scale(n[0] ?? 1, n[1] ?? n[0] ?? 1); break;
      case 'scaleX': m = m.scale(n[0] ?? 1, 1); break;
      case 'scaleY': m = m.scale(1, n[0] ?? 1); break;
      case 'rotate':
        if (n.length >= 3) m = m.translate(n[1], n[2]).rotate(n[0]).translate(-n[1], -n[2]);
        else m = m.rotate(n[0] ?? 0);
        break;
      case 'skewX': m = m.multiply({ a: 1, b: 0, c: Math.tan(((n[0] ?? 0) * Math.PI) / 180), d: 1, e: 0, f: 0 }); break;
      case 'skewY': m = m.multiply({ a: 1, b: Math.tan(((n[0] ?? 0) * Math.PI) / 180), c: 0, d: 1, e: 0, f: 0 }); break;
    }
  }
  return { a: m.a, b: m.b, c: m.c, d: m.d, e: m.e, f: m.f };
}

const ZERO_BOX = { offsetWidth: 0, offsetHeight: 0, offsetTop: 0, offsetLeft: 0, offsetParent: 0, clientWidth: 0, clientHeight: 0, clientTop: 0, clientLeft: 0, scrollWidth: 0, scrollHeight: 0, scrollTop: 0, scrollLeft: 0 };

/** Query about an element; detached / unknown elements get `fallback` without a round trip. */
export function elQuery(el: any, q: Record<string, unknown>, fallback: any): any {
  // Nodes appended in this tick are not mirrored yet: mark and let query() flush.
  let id = mirroredId(el);
  if (!id) {
    if (!el.isConnected) return fallback;
    markDirty(el.parentNode);
    flush();
    id = mirroredId(el);
    if (!id) return fallback;
  }
  const r = query({ ...q, id });
  return r && !r.error ? r : fallback;
}

const box = (el: any) => elQuery(el, { q: 'box' }, ZERO_BOX);

const computedHandler: ProxyHandler<any> = {
  get(t, prop) {
    if (typeof prop === 'symbol') return undefined;
    if (prop === 'getPropertyValue') return (name: string) => t.get(String(name));
    if (prop === 'getPropertyPriority') return () => '';
    if (prop === 'length') return Object.keys(t.all()).length;
    if (prop === 'item') return (i: number) => Object.keys(t.all())[i] ?? '';
    if (prop === 'cssText') return '';
    if (prop === 'setProperty' || prop === 'removeProperty') return () => { throw new Error('NoModificationAllowedError: computed style is read-only'); };
    if (/^\d+$/.test(prop)) return Object.keys(t.all())[Number(prop)];
    return t.get(toKebab(prop));
  },
  set: () => true,
  has: () => true,
};

export function getComputedStyle(el: any, pseudo?: string | null): any {
  const base: Record<string, unknown> = { q: 'cs' };
  if (pseudo) base.pseudo = String(pseudo);
  const all = () => (el && el.nodeType === 1 ? elQuery(el, base, {}) : {});
  const get = (name: string): string => {
    if (name.startsWith('--')) {
      const r = el && el.nodeType === 1 ? elQuery(el, { ...base, prop: name }, '') : '';
      return typeof r === 'string' ? r : '';
    }
    const v = all()[name];
    return typeof v === 'string' ? v : '';
  };
  return new Proxy({ all, get }, computedHandler);
}

function define(proto: any, props: Record<string, PropertyDescriptor | Function>) {
  for (const [name, d] of Object.entries(props)) {
    Object.defineProperty(proto, name, typeof d === 'function' ? { value: d, writable: true, configurable: true } : { configurable: true, ...d });
  }
}

export function installLayout(ElementProto: any, HTMLElementProto: any, SVGElementProto: any, pushProp: (el: any, name: string, value: number) => void) {
  const boxGetter = (name: string): PropertyDescriptor => ({ get(this: any) { return box(this)[name] ?? 0; } });
  define(ElementProto, {
    getBoundingClientRect(this: any) {
      const r = elQuery(this, { q: 'rect' }, { x: 0, y: 0, width: 0, height: 0 });
      return new DOMRect(r.x, r.y, r.width, r.height);
    },
    getClientRects(this: any) {
      const r = elQuery(this, { q: 'rects' }, []);
      return Array.isArray(r) ? r.map((x: any) => new DOMRect(x.x, x.y, x.width, x.height)) : [];
    },
    clientWidth: boxGetter('clientWidth'), clientHeight: boxGetter('clientHeight'), clientTop: boxGetter('clientTop'), clientLeft: boxGetter('clientLeft'),
    scrollWidth: boxGetter('scrollWidth'), scrollHeight: boxGetter('scrollHeight'),
    scrollTop: {
      get(this: any) { return box(this).scrollTop; },
      set(this: any, v: any) { pushProp(this, 'scrollTop', Number(v) || 0); },
    },
    scrollLeft: {
      get(this: any) { return box(this).scrollLeft; },
      set(this: any, v: any) { pushProp(this, 'scrollLeft', Number(v) || 0); },
    },
    checkVisibility(this: any) { return this.isConnected; },
  });
  define(HTMLElementProto, {
    offsetWidth: boxGetter('offsetWidth'), offsetHeight: boxGetter('offsetHeight'), offsetTop: boxGetter('offsetTop'), offsetLeft: boxGetter('offsetLeft'),
    offsetParent: { get(this: any) { const id = box(this).offsetParent; return id ? nodeById(id) : this.isConnected ? document.body : null; } },
    innerText: {
      get(this: any) { return this.textContent; },
      set(this: any, v: any) {
        // innerText turns line breaks into <br>.
        const parts = String(v ?? '').split(/\r?\n/);
        this.textContent = '';
        parts.forEach((p, i) => {
          if (i) this.appendChild(document.createElement('br'));
          if (p) this.appendChild(document.createTextNode(p));
        });
      },
    },
  });

  const svgQuery = (method: string, fallback: any) => function (this: any, ...args: number[]) {
    return elQuery(this, { q: 'svg', method, args: args.map(Number).filter((n) => Number.isFinite(n)) }, fallback);
  };
  const animated = (get: (el: any) => number) => ({ get(this: any) { const el = this; return { get baseVal() { return { get value() { return get(el); }, valueAsString: String(get(el)), unitType: 1 }; }, get animVal() { return { value: get(el) }; } }; } });
  const lengthAttr = (attr: string, dim: 'width' | 'height' | null) => animated((el) => {
    const raw = el.getAttribute(attr);
    if (raw !== null && /^[-+]?[\d.]+(e[-+]?\d+)?(px)?$/i.test(raw.trim())) return parseFloat(raw);
    if (dim) return elQuery(el, { q: 'rect' }, { width: 0, height: 0 })[dim];
    return parseFloat(raw ?? '0') || 0;
  });
  define(SVGElementProto, {
    getBBox(this: any) { const b = elQuery(this, { q: 'svg', method: 'getBBox' }, { x: 0, y: 0, width: 0, height: 0 }); return new DOMRect(b.x, b.y, b.width, b.height); },
    getComputedTextLength: svgQuery('getComputedTextLength', 0),
    getSubStringLength: svgQuery('getSubStringLength', 0),
    getNumberOfChars: svgQuery('getNumberOfChars', 0),
    getTotalLength: svgQuery('getTotalLength', 0),
    getPointAtLength(this: any, d: number) { const p = elQuery(this, { q: 'svg', method: 'getPointAtLength', args: [Number(d) || 0] }, { x: 0, y: 0 }); return new DOMPoint(p.x, p.y); },
    getCTM(this: any) { const m = elQuery(this, { q: 'svg', method: 'getCTM' }, null); return m ? new DOMMatrix(m) : null; },
    getScreenCTM(this: any) { const m = elQuery(this, { q: 'svg', method: 'getScreenCTM' }, null); return m ? new DOMMatrix(m) : null; },
    // linkedom's SVGElement constructor assigns this property: keep a (no-op) setter.
    ownerSVGElement: { get(this: any) { for (let p = this.parentNode; p; p = p.parentNode) if (p.localName === 'svg' && p.namespaceURI === SVG_NS) return p; return null; }, set() {} },
    viewportElement: { get(this: any) { return this.ownerSVGElement; } },
    transform: {
      get(this: any) {
        const el = this;
        const consolidate = () => {
          const t = el.getAttribute('transform');
          return t ? { matrix: new DOMMatrix(parseTransform(t)), type: 1 } : null;
        };
        return { baseVal: { consolidate, get numberOfItems() { return el.getAttribute('transform') ? 1 : 0; } }, animVal: { consolidate } };
      },
    },
    viewBox: {
      get(this: any) {
        const el = this;
        const read = () => {
          const n = (el.getAttribute('viewBox') ?? '').split(/[\s,]+/).map(Number);
          return n.length === 4 && n.every((v: number) => !isNaN(v)) ? new DOMRect(n[0], n[1], n[2], n[3]) : new DOMRect();
        };
        return { get baseVal() { return read(); }, get animVal() { return read(); } };
      },
    },
    width: lengthAttr('width', 'width'), height: lengthAttr('height', 'height'),
    x: lengthAttr('x', null), y: lengthAttr('y', null), cx: lengthAttr('cx', null), cy: lengthAttr('cy', null), r: lengthAttr('r', null),
    createSVGMatrix() { return new DOMMatrix(); },
    // No createSVGPoint on purpose: d3.pointer then uses getBoundingClientRect (one cached query).
  });
}

export function elementFromPoint(x: number, y: number): any {
  const id = query({ q: 'elementFromPoint', x: Number(x) || 0, y: Number(y) || 0 });
  return typeof id === 'number' && id ? nodeById(id) : null;
}

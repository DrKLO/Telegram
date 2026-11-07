// Document construction. linkedom provides the DOM object model; parse5 does
// the HTML parsing so the tree has the same shape a browser would build
// (implied <tbody>, <head>/<body> for bare fragments, SVG name casing, …).

import { parseHTML } from 'linkedom';
import { parse, parseFragment } from 'parse5';

export const SVG_NS = 'http://www.w3.org/2000/svg';
const HTML_NS = 'http://www.w3.org/1999/xhtml';

/**
 * Scripts created by fragment parsing (innerHTML, outerHTML,
 * insertAdjacentHTML) never execute, exactly like in browsers. Without this a
 * script doing `document.body.innerHTML += …` would re-create and re-run
 * itself forever.
 */
export const SCRIPT_INERT = Symbol('tw.scriptInert');

const lw: any = parseHTML('<!DOCTYPE html><html><head></head><body></body></html>');
export const document: any = lw.document;
export const linkedomWindow: any = lw;

function ownerOf(proto: any, name: string): any {
  for (let p = proto; p; p = Object.getPrototypeOf(p)) if (Object.prototype.hasOwnProperty.call(p, name)) return p;
  return null;
}

/** Set on a node once script could have obtained a reference to it. */
export const EXPOSED = Symbol('tw.exposed');
/** Set on a parent once script holds a live childNodes list of it: every child counts as exposed. */
const KIDS_EXPOSED = Symbol('tw.kidsExposed');
/** Hooks the capture layer fills in. */
export const domHooks = { observing: (): boolean => false, markDirty: (_node: any): void => {} };
let inPlaceText = true;
let inPlaceKilled = false;
let rawFirstChild: () => any;
let rawNextSibling: () => any;

let quiet = 0;

/** Runs `fn` as the shim's own bookkeeping: node reads inside it do not count as script exposure. */
export function quietly<T>(fn: () => T): T {
  quiet++;
  try {
    return fn();
  } finally {
    quiet--;
  }
}

export function exposeNode<T>(n: T): T {
  if (quiet === 0 && n && typeof n === 'object' && (n as any).nodeType === 3) (n as any)[EXPOSED] = true;
  return n;
}

/** A Range, TreeWalker, NodeIterator or Selection can reach any node: stop updating text nodes in place for good. */
export function exposeAll(): void {
  inPlaceKilled = true;
  inPlaceText = false;
}

export function setInPlaceText(enabled: boolean): void {
  inPlaceText = enabled && !inPlaceKilled;
}

// Exposure tracking: every getter that can hand a text node to script marks it.
{
  const samples = [document.createElement('div'), document.createTextNode('x'), document.createComment('x'), document.createDocumentFragment(), document];
  const wrapped = new Set<any>();
  rawFirstChild = Object.getOwnPropertyDescriptor(ownerOf(samples[0], 'firstChild'), 'firstChild')!.get!;
  rawNextSibling = Object.getOwnPropertyDescriptor(ownerOf(samples[1], 'nextSibling'), 'nextSibling')!.get!;
  for (const name of ['firstChild', 'lastChild', 'nextSibling', 'previousSibling']) {
    for (const sample of samples) {
      const proto = ownerOf(sample, name);
      const d = proto && Object.getOwnPropertyDescriptor(proto, name);
      if (!d?.get || wrapped.has(d.get)) continue;
      const get = d.get;
      const wrapper = function (this: any) {
        return exposeNode(get.call(this));
      };
      wrapped.add(wrapper);
      Object.defineProperty(proto, name, { ...d, get: wrapper });
    }
  }
  for (const sample of samples) {
    const proto = ownerOf(sample, 'childNodes');
    const d = proto && Object.getOwnPropertyDescriptor(proto, 'childNodes');
    if (!d?.get || wrapped.has(d.get)) continue;
    const get = d.get;
    const wrapper = function (this: any) {
      if (quiet === 0) this[KIDS_EXPOSED] = true;
      return get.call(this);
    };
    wrapped.add(wrapper);
    Object.defineProperty(proto, 'childNodes', { ...d, get: wrapper });
  }
  for (const sample of samples) {
    const proto = ownerOf(sample, 'normalize');
    if (!proto || wrapped.has(proto.normalize)) continue;
    const normalize = proto.normalize;
    const wrapper = function (this: any) {
      if (quiet === 0) this[KIDS_EXPOSED] = true;
      return normalize.call(this);
    };
    wrapped.add(wrapper);
    proto.normalize = wrapper;
  }
  const docProto = ownerOf(document, 'createRange');
  for (const name of ['createRange', 'createTreeWalker', 'createNodeIterator', 'evaluate']) {
    const owner = ownerOf(document, name);
    if (!owner || typeof owner[name] !== 'function') continue;
    const orig = owner[name];
    owner[name] = function (this: any, ...args: any[]) {
      exposeAll();
      return orig.apply(this, args);
    };
  }
  void docProto;
}

// Fast setters for the two writes tickers make every frame. Both keep linkedom's
// semantics; capture.ts's dirty tracking wraps them like any other setter.
{
  const sample = document.createElement('div');
  const tokens = sample.classList;
  const classListKey = Object.getOwnPropertySymbols(sample).find((k) => sample[k] === tokens);
  const addToken = Set.prototype.add;
  const classProto = ownerOf(sample, 'className');
  const className = classProto && Object.getOwnPropertyDescriptor(classProto, 'className');
  if (className?.set && classListKey) {
    const set = className.set;
    const SINGLE = /^\S+$/;
    // One class token on an element that already has a class attribute: set
    // the attribute and the classList cache directly instead of clear + split + add.
    Object.defineProperty(classProto, 'className', {
      ...className,
      set(this: any, v: any) {
        const s = String(v);
        const a = s.length <= 256 && SINGLE.test(s) ? this.getAttributeNode('class') : null;
        if (!a) return set.call(this, v);
        const list = this[classListKey];
        if (list && a.value !== s) {
          list.clear();
          addToken.call(list, s);
        }
        a.value = s;
      },
    });
  }
  const firstChildOf = rawFirstChild;
  const nextSiblingOf = rawNextSibling;
  const textProto = ownerOf(sample, 'textContent');
  const textContent = textProto && Object.getOwnPropertyDescriptor(textProto, 'textContent');
  if (textContent?.set) {
    const set = textContent.set;
    // Replacing an element's sole text child: one replaceChild instead of
    // replaceChildren + appendChild (capture.ts then keeps the node id).
    Object.defineProperty(textProto, 'textContent', {
      ...textContent,
      set(this: any, v: any) {
        const first = this.nodeType === 1 ? firstChildOf.call(this) : null;
        if (typeof v === 'string' && v !== '' && first && first.nodeType === 3 && !nextSiblingOf.call(first)) {
          if (inPlaceText && !first[EXPOSED] && !this[KIDS_EXPOSED] && !domHooks.observing()) {
            first.data = v;
            return;
          }
          this.replaceChild(document.createTextNode(v), first);
          return;
        }
        set.call(this, v);
      },
    });
  }
}

function setAttrs(el: any, attrs: { name: string; value: string; prefix?: string }[]) {
  for (const a of attrs) {
    try {
      el.setAttribute(a.prefix ? `${a.prefix}:${a.name}` : a.name, a.value);
    } catch {
      /* invalid attribute name: skip */
    }
  }
}

/** Append linkedom nodes for the children of a parse5 node. */
export function build(p5: any, parent: any, inertScripts = false): void {
  for (const c of p5.childNodes ?? []) {
    if (c.nodeName === '#text') parent.appendChild(document.createTextNode(c.value));
    else if (c.nodeName === '#comment') parent.appendChild(document.createComment(c.data));
    else if (c.tagName) {
      let el: any;
      try {
        el = c.namespaceURI === SVG_NS ? document.createElementNS(SVG_NS, c.tagName) : document.createElement(c.tagName);
      } catch {
        continue;
      }
      setAttrs(el, c.attrs);
      if (inertScripts && c.tagName === 'script') el[SCRIPT_INERT] = true;
      parent.appendChild(el);
      if (c.tagName === 'template' && c.content && el.content) build(c.content, el.content, inertScripts);
      else build(c, el, inertScripts);
    }
  }
}

/** Parse a full document or a bare fragment into the (single) widget document. */
export function loadDocument(html: string): void {
  const p5: any = parse(html);
  const root = p5.childNodes.find((n: any) => n.tagName === 'html');
  if (!root) return;
  setAttrs(document.documentElement, root.attrs);
  for (const c of root.childNodes) {
    if (c.tagName === 'head') {
      setAttrs(document.head, c.attrs);
      build(c, document.head);
    } else if (c.tagName === 'body') {
      setAttrs(document.body, c.attrs);
      build(c, document.body);
    }
  }
}

/** Parse `html` as the children of `context` (innerHTML semantics). */
export function parseInto(context: any, html: string, target: any = context): void {
  const svg = context.namespaceURI === SVG_NS && context.localName !== 'foreignObject';
  const mock = { nodeName: context.localName, tagName: context.localName, attrs: [], namespaceURI: svg ? SVG_NS : HTML_NS, childNodes: [], parentNode: null };
  const frag = (parseFragment as (ctx: unknown, html: string) => any)(mock, String(html));
  build(frag, target, true);
}

let attrFast = true;
const SINGLE_TOKEN = /^\S+$/;

export function setAttrFast(enabled: boolean): void {
  attrFast = enabled;
}

/**
 * Installed after dirty tracking. getAttribute('class') normally goes through the
 * className getter and the dirty-marking classList wrapper; when the class
 * attribute and the cached classList hold the same single token it returns the
 * attribute value directly. Writing the class an element already has returns
 * before dirty marking when nothing could observe the write (no MutationObserver,
 * no attributeChangedCallback).
 */
export function installAttributeFastPaths(): void {
  const sample = document.createElement('div');
  const tokens = sample.classList;
  const classListKey = Object.getOwnPropertySymbols(sample).find((k) => sample[k] === tokens);
  if (!classListKey) return;
  const nodeProto = ownerOf(sample, 'getAttributeNode');
  const getNode = nodeProto && nodeProto.getAttributeNode;
  const getProto = ownerOf(sample, 'getAttribute');
  const getAttribute = getProto && getProto.getAttribute;
  if (typeof getNode !== 'function' || typeof getAttribute !== 'function') return;
  getProto.getAttribute = function (this: any, name: any) {
    'use strict';
    if (attrFast && name === 'class') {
      const list = this[classListKey];
      if (list && list.size === 1) {
        const a = getNode.call(this, 'class');
        if (a) {
          const v = a.value;
          if (list.has(v)) {
            domHooks.markDirty(this);
            return v;
          }
        }
      }
    }
    return getAttribute.call(this, name);
  };
  const classProto = ownerOf(sample, 'className');
  const d = classProto && Object.getOwnPropertyDescriptor(classProto, 'className');
  if (!d?.set) return;
  const set = d.set;
  Object.defineProperty(classProto, 'className', {
    ...d,
    set(this: any, v: any) {
      'use strict';
      if (attrFast && typeof v === 'string' && SINGLE_TOKEN.test(v) && !domHooks.observing() && !this.attributeChangedCallback) {
        const a = getNode.call(this, 'class');
        if (a && a.value === v) {
          domHooks.markDirty(this);
          domHooks.markDirty(a);
          return;
        }
      }
      set.call(this, v);
    },
  });
}

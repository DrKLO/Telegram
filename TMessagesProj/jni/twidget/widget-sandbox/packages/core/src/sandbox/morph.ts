// In-place document update ("morph"): the host hands the sandbox a newer
// version of the widget's HTML and the live DOM is patched to match it, so
// nodes keep their identity and CSS transitions run from the old state to the
// new one. Runs entirely inside QuickJS on the shim's own DOM: every change is
// an ordinary mutation that the capture layer turns into ops, and those ops go
// through the host's validator like any others. Scripts in the new HTML never
// run; the widget hears about the update through a `widgetupdate` event on
// document. Elements marked `data-morph-keep` keep their children as they are
// (content the widget's script built itself).

import { parse } from 'parse5';
import { build, document } from './dom';

const KEEP = 'data-morph-keep';

function isScript(n: any): boolean {
  return n.nodeType === 1 && n.localName === 'script';
}

function sameKind(a: any, b: any): boolean {
  if (a.nodeType !== b.nodeType) return false;
  if (a.nodeType !== 1) return true;
  return a.localName === b.localName && a.namespaceURI === b.namespaceURI;
}

function syncAttributes(live: any, next: any): void {
  const wanted = new Map<string, string>();
  for (const attr of Array.from(next.attributes ?? []) as any[]) wanted.set(attr.name, attr.value);
  for (const attr of Array.from(live.attributes ?? []) as any[]) {
    if (!wanted.has(attr.name)) live.removeAttribute(attr.name);
  }
  for (const [name, value] of wanted) {
    if (live.getAttribute(name) !== value) live.setAttribute(name, value);
  }
}

function morphNode(live: any, next: any): void {
  if (live.nodeType === 3 || live.nodeType === 8) {
    if (live.data !== next.data) live.data = next.data;
    return;
  }
  syncAttributes(live, next);
  if (isScript(live) || live.hasAttribute(KEEP)) return;
  if (live.localName === 'template' && live.content && next.content) {
    morphChildren(live.content, next.content);
    return;
  }
  morphChildren(live, next);
}

function morphChildren(live: any, next: any): void {
  const wanted = (Array.from(next.childNodes) as any[]).filter((n) => !isScript(n));
  const pool = (Array.from(live.childNodes) as any[]).filter((n) => !isScript(n));
  const byId = new Map<string, any>();
  for (const n of pool) {
    const id = n.nodeType === 1 ? n.getAttribute('id') : null;
    if (id && !byId.has(id)) byId.set(id, n);
  }
  const used = new Set<any>();
  const matched: any[] = [];
  for (const n of wanted) {
    const id = n.nodeType === 1 ? n.getAttribute('id') : null;
    let hit = id ? byId.get(id) : undefined;
    if (hit && (used.has(hit) || !sameKind(hit, n))) hit = undefined;
    if (!hit) {
      hit = pool.find((c) => !used.has(c) && sameKind(c, n) && !(c.nodeType === 1 && c.getAttribute('id') && c.getAttribute('id') !== id));
    }
    if (hit) used.add(hit);
    matched.push(hit);
  }
  for (const n of pool) if (!used.has(n)) live.removeChild(n);
  let cursor: any = live.firstChild;
  const advance = () => {
    while (cursor && isScript(cursor)) cursor = cursor.nextSibling;
  };
  advance();
  for (let i = 0; i < wanted.length; i++) {
    const hit = matched[i];
    if (hit) {
      morphNode(hit, wanted[i]);
      if (hit !== cursor) live.insertBefore(hit, cursor);
      else cursor = cursor.nextSibling;
    } else {
      live.insertBefore(wanted[i], cursor);
    }
    advance();
  }
}

/** Patches the live document to match `html`. Returns false when `html` is not a document the morph can read. */
export function morphDocument(html: string): boolean {
  const p5: any = parse(html);
  const root = p5.childNodes.find((n: any) => n.tagName === 'html');
  if (!root) return false;
  const head = document.createElement('div');
  const body = document.createElement('div');
  const bodyAttrs = document.createElement('div');
  for (const c of root.childNodes) {
    if (c.tagName === 'head') {
      build(c, head, true);
    } else if (c.tagName === 'body') {
      build(c, body, true);
      for (const a of c.attrs ?? []) bodyAttrs.setAttribute(a.name, a.value);
    }
  }
  morphChildren(document.head, head);
  syncAttributes(document.body, bodyAttrs);
  morphChildren(document.body, body);
  return true;
}

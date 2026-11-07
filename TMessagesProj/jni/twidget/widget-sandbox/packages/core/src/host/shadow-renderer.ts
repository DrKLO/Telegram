// Prototype renderer without an iframe: the renderer core (src/renderer/core.ts,
// the same code the sandboxed iframe runs) applied to a shadow root inside the
// chat page. Trusted code, validated ops only — the sandbox and the validator
// are untouched; what changes is invariant 6: no opaque origin, no per-widget
// CSP, no frame boundary. The shadow tree gives selector isolation both ways
// and keeps widget ids/names out of `document` and `window` named lookups;
// `contain: layout paint` keeps `position: fixed`, stacking and overflow
// inside the bubble. See docs/shadow-dom-renderer.md for what is lost and what
// this buys in a feed of many bubbles (no per-bubble document, no boot
// round-trip, a tree that can be built before it is attached).

import { createRenderer, type RendererCore, type RendererEnv } from '../renderer/core';
import type { HostToRenderer, RendererToHost } from '../shared/protocol';
import type { Renderer, RendererFactory } from './renderer';

/**
 * Base styles of the shadow tree. `:host { all: initial }` cuts inheritance
 * from the chat page so the widget starts from UA defaults like in a frame;
 * the width/height media queries and viewport units the sanitizer turned into
 * `@container tw` queries / `cq*` units resolve against the host
 * (`container-type: size`: the host's height is explicit, set by WidgetHost
 * from the size reports measured on the inner <html>, so `100cqh` is the
 * bubble height exactly as `100vh` was the frame's — the same fixed point).
 * `:where()` keeps the specificity at zero: the widget's own `html { }` /
 * `body { }` rules always win. `flow-root` on <html> keeps body margins inside
 * the measured height (our <html> is not a root element, its margins would
 * otherwise collapse through). The body's margin and padding, the font and the
 * colours come from the renderer's UA layer (profile.json `uaStylesheet`), as in
 * the iframe: an unlayered rule here would beat that layer. The host element is
 * the canvas: the renderer core paints the root's or the body's background on it
 * (`RendererEnv.canvas`), so a body background covers the body's margins too.
 */
const BASE_CSS =
  ':host{all:initial;display:block;width:100%;position:relative;contain:layout paint style;container-type:size;container-name:tw}' +
  ':where(html){display:flow-root;position:relative;min-height:0}:where(head){display:none}:where(body){display:block}';

/** `(min-width: 400px)`-style queries on the widget's own box; anything else goes to the window. */
const SIZE_QUERY = /^\s*\(\s*(min|max)-(width|height)\s*:\s*([\d.]+)px\s*\)\s*$/i;

export class ShadowDomRenderer implements Renderer {
  static readonly factory: RendererFactory = (container, theme) => new ShadowDomRenderer(container, theme);
  /** Tells WidgetHost to run the validator in shadow scope (`:root`, `:host`, `@media`, viewport units). */
  readonly cssScope = 'shadow' as const;

  readonly element: HTMLDivElement;
  onMessage: ((msg: RendererToHost) => void) | null = null;
  onDiag: ((kind: string, detail: string) => void) | null = null;
  readonly shadow: ShadowRoot;
  private readonly html: HTMLElement;
  private core: RendererCore;
  private queued: HostToRenderer[] | null = [];
  private destroyed = false;

  constructor(container: HTMLElement, theme: 'light' | 'dark') {
    const el = document.createElement('div');
    el.className = 'widget-frame widget-shadow';
    el.style.colorScheme = theme;
    this.element = el;
    this.shadow = el.attachShadow({ mode: 'open' });
    const base = document.createElement('style');
    base.textContent = BASE_CSS;
    // Real <html>/<head>/<body> elements: the widget's `html {}` / `body {}` rules and the UA defaults match them as in a document.
    const html = document.createElement('html');
    html.setAttribute('data-tw-root', '');
    const head = document.createElement('head');
    const body = document.createElement('body');
    html.append(head, body);
    this.shadow.append(base, html);
    this.html = html;
    this.core = createRenderer(this.env(head, body));
    container.appendChild(el);
  }

  private env(head: HTMLElement, body: HTMLElement): RendererEnv {
    const html = this.html;
    const el = this.element;
    return {
      doc: document,
      root: this.shadow,
      roots: () => ({ html, head, body }),
      origin: () => { const r = html.getBoundingClientRect(); return { x: r.left, y: r.top }; },
      viewport: () => ({ innerWidth: el.clientWidth, innerHeight: el.clientHeight, dpr: window.devicePixelRatio }),
      docHeight: () => Math.ceil(html.getBoundingClientRect().height),
      matchMedia: (query) => {
        const m = SIZE_QUERY.exec(query);
        if (!m) return matchMedia(query).matches;
        const actual = m[2].toLowerCase() === 'width' ? el.clientWidth : el.clientHeight;
        const limit = parseFloat(m[3]);
        return m[1].toLowerCase() === 'min' ? actual >= limit : actual <= limit;
      },
      onViewportResize: (cb) => {
        const ro = new ResizeObserver(cb);
        ro.observe(el);
        return () => ro.disconnect();
      },
      onColorSchemeChange: (cb) => {
        const mq = matchMedia('(prefers-color-scheme: dark)');
        mq.addEventListener('change', cb);
        return () => mq.removeEventListener('change', cb);
      },
      // The host element is the bubble's canvas: a body background reaches the whole bubble, as in a frame.
      canvas: el,
    };
  }

  load() {
    if (this.destroyed || !this.queued) return;
    this.core.init((msg) => { if (!this.destroyed) this.onMessage?.(msg); });
    const q = this.queued;
    this.queued = null;
    for (const msg of q) this.core.onMessage(msg);
  }

  send(msg: HostToRenderer) {
    if (this.destroyed) return;
    if (this.queued) this.queued.push(msg);
    else this.core.onMessage(msg);
  }

  setTheme(theme: 'light' | 'dark') {
    this.element.style.colorScheme = theme;
  }

  destroy() {
    if (this.destroyed) return;
    this.destroyed = true;
    this.core.destroy();
    this.element.remove();
  }
}

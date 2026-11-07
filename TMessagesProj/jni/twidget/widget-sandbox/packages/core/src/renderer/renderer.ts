// Renderer bundle entry (security-critical, invariant 6). Runs inside the
// sandboxed srcdoc iframe on the web and inside the native WebViews; it is the
// ONLY script that ever executes there (CSP nonce). The DOM work lives in
// core.ts; this file binds it to the frame's document and to the transport.

import { createRenderer, toBase64, type RendererEnv } from './core';
import { measureDocHeight } from '../shared/doc-height';
import type { HostToRenderer } from '../shared/protocol';

// Transport is chosen at build time: 'port' (web: MessageChannel from the host
// page), 'webkit' (WKScriptMessageHandler in, evaluateJavaScript out) or 'android'
// (@JavascriptInterface in, evaluateJavascript out).
declare const __TW_TRANSPORT__: 'port' | 'webkit' | 'android';

const doc = document;

/** The frame's root scheme, in the UA layer so a widget's own `color-scheme` still wins (core.ts `frameColorScheme`). */
let schemeStyle: HTMLStyleElement | null = null;
function frameColorScheme(scheme: 'light' | 'dark') {
  if (!schemeStyle) {
    schemeStyle = doc.createElement('style');
    doc.head.appendChild(schemeStyle);
  }
  schemeStyle.textContent = `@layer tw-ua{:where(html){color-scheme:${scheme}}}`;
}

/** The whole document is the widget's: coordinates coincide, the window is the viewport. */
function documentEnv(): RendererEnv {
  return {
    doc,
    root: doc,
    roots: () => ({ html: doc.documentElement, head: doc.head, body: doc.body }),
    origin: () => null,
    viewport: () => ({ innerWidth: window.innerWidth, innerHeight: window.innerHeight, dpr: window.devicePixelRatio }),
    docHeight: () => measureDocHeight(doc),
    matchMedia: (query) => matchMedia(query).matches,
    onViewportResize: (cb) => {
      window.addEventListener('resize', cb);
      return () => window.removeEventListener('resize', cb);
    },
    onColorSchemeChange: (cb) => {
      const mq = matchMedia('(prefers-color-scheme: dark)');
      mq.addEventListener('change', cb);
      return () => mq.removeEventListener('change', cb);
    },
    // The web's frame only: WebViews are top-level documents over the bubble.
    ...(__TW_TRANSPORT__ === 'port' ? { frameColorScheme } : {}),
  };
}

document.currentScript?.remove();

const core = createRenderer(documentEnv());
const onMessage = (msg: HostToRenderer) => core.onMessage(msg);

if (__TW_TRANSPORT__ === 'webkit' || __TW_TRANSPORT__ === 'android') {
  // Native host: messages in through `__twReceive(json)` (evaluateJavaScript),
  // out through the `tw` script message handler (WebKit) or the `tw`
  // @JavascriptInterface object (Android). Binary payloads go as base64.
  const handler = __TW_TRANSPORT__ === 'webkit' ? (window as any).webkit?.messageHandlers?.tw : (window as any).tw;
  const start = () => {
    (window as any).__twReceive = (json: string) => onMessage(JSON.parse(json) as HostToRenderer);
    core.init((msg) => {
      if (msg.type === 'result' && msg.binary) {
        handler.postMessage(JSON.stringify({ ...msg, binary: undefined, binaryBase64: toBase64(msg.binary) }));
        return;
      }
      handler.postMessage(JSON.stringify(msg));
    });
    handler.postMessage(JSON.stringify({ type: 'ready' }));
  };
  if (doc.readyState === 'loading') doc.addEventListener('DOMContentLoaded', start, { once: true });
  else start();
} else {
  // Web host: it hands over a private MessagePort. Only the embedding window may do so.
  const onWindowMessage = (e: MessageEvent) => {
    if (e.source !== window.parent || !e.data || e.data.type !== 'tw-init' || !e.ports[0]) return;
    window.removeEventListener('message', onWindowMessage);
    const port = e.ports[0];
    core.init((msg, transfer) => port.postMessage(msg, transfer));
    port.onmessage = (ev) => onMessage(ev.data as HostToRenderer);
  };
  window.addEventListener('message', onWindowMessage);
  const announce = () => window.parent.postMessage({ type: 'tw-renderer-ready' }, '*');
  if (doc.readyState === 'loading') doc.addEventListener('DOMContentLoaded', announce, { once: true });
  else announce();
}

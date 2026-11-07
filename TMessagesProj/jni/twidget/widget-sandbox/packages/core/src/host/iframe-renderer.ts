// The product renderer: renderer-bundle.js in a sandboxed, opaque-origin
// iframe (srcdoc with a nonce CSP), talking to the host over a MessagePort.
// Trusted code, validated ops only; see docs/decisions.md for the sandbox flags.

import rendererBundle from 'virtual:renderer-bundle';
import { buildSrcdoc, makeNonce } from '../renderer/srcdoc';
import type { HostToRenderer, RendererToHost } from '../shared/protocol';
import type { Renderer, RendererFactory } from './renderer';

export class IframeRenderer implements Renderer {
  static readonly factory: RendererFactory = (container, theme) => new IframeRenderer(container, theme);

  readonly element: HTMLIFrameElement;
  onMessage: ((msg: RendererToHost) => void) | null = null;
  onDiag: ((kind: string, detail: string) => void) | null = null;
  private port: MessagePort;
  private channel = new MessageChannel();
  private onWindowMessage: (e: MessageEvent) => void;
  private destroyed = false;

  constructor(container: HTMLElement, theme: 'light' | 'dark') {
    const nonce = makeNonce();
    const iframe = document.createElement('iframe');
    // Opaque origin, no popups, no top navigation, no same-origin access.
    // allow-forms is needed for `submit` events to be dispatched at all; the
    // submission itself can never navigate (renderer preventDefault + CSP
    // form-action 'none' + action attributes are never forwarded). See docs/decisions.md.
    iframe.setAttribute('sandbox', 'allow-scripts allow-forms');
    iframe.setAttribute('referrerpolicy', 'no-referrer');
    iframe.className = 'widget-frame';
    iframe.style.colorScheme = theme;
    iframe.srcdoc = buildSrcdoc(rendererBundle, nonce);
    this.element = iframe;
    this.port = this.channel.port1;
    // Messages posted before the port is handed over queue inside the port.
    this.port.onmessage = (e) => this.onMessage?.(e.data as RendererToHost);
    this.onWindowMessage = (e) => {
      if (e.source !== iframe.contentWindow || e.data?.type !== 'tw-renderer-ready') return;
      window.removeEventListener('message', this.onWindowMessage);
      iframe.contentWindow!.postMessage({ type: 'tw-init' }, '*', [this.channel.port2]);
    };
    // Listening before the frame is attached: its announcement cannot be missed.
    window.addEventListener('message', this.onWindowMessage);
    container.appendChild(iframe);
  }

  /** The frame is already loading; the renderer bundle announces itself through the port once initialised. */
  load() {}

  send(msg: HostToRenderer) {
    if (!this.destroyed) this.port.postMessage(msg);
  }

  setTheme(theme: 'light' | 'dark') {
    this.element.style.colorScheme = theme;
  }

  destroy() {
    if (this.destroyed) return;
    this.destroyed = true;
    window.removeEventListener('message', this.onWindowMessage);
    this.port.close();
    this.element.remove();
  }
}

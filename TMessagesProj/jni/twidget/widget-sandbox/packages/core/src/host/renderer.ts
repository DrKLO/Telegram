// The renderer as WidgetHost sees it: an element for the bubble plus the message
// contract of docs/protocol.md (host → renderer: ops / query / listen /
// preventDefault / requestFrame / frozen; renderer → host: ready / event /
// gesture / result / size / resize / frame / media / violation). The sandboxed
// iframe (iframe-renderer.ts) is the product renderer; other implementations
// (the Blitz wasm prototype in apps/web/src/renderers/blitz-renderer.ts) plug in through WidgetHost's
// `rendererFactory`. The web copy of Renderer.swift / Renderer.kt /
// desktop's twidget/renderer.h.

import type { HostToRenderer, RendererToHost } from '../shared/protocol';

export interface Renderer {
  /** What sits in the bubble; the host sets its CSS height from `size` reports. */
  readonly element: HTMLElement;
  /** 'shadow' when the renderer is a shadow root in the host page: the validator scopes CSS accordingly (css-sanitizer.ts). */
  readonly cssScope?: 'document' | 'shadow';
  /** Renderer → host messages. The host sets this before `load()`. */
  onMessage: ((msg: RendererToHost) => void) | null;
  onDiag: ((kind: string, detail: string) => void) | null;
  /** Starts the renderer; it announces itself with `{type:'ready'}`. Messages sent before that are the renderer's to queue. */
  load(): void;
  /** Host → renderer message. */
  send(msg: HostToRenderer): void;
  /** The chat theme changed (`prefers-color-scheme` inside the renderer). */
  setTheme(theme: 'light' | 'dark'): void;
  destroy(): void;
}

/** Builds a renderer for one widget: the container it will live in and the chat theme. */
export type RendererFactory = (container: HTMLElement, theme: 'light' | 'dark') => Renderer;

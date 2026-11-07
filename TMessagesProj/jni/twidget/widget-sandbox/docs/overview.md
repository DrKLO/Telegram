# HTML widgets in Telegram messages without leaking the IP

> **Background, October 2026.** This is the record of the problem and the options
> considered before the framework existed. The current state is in `README.md`,
> `docs/integration.md`, `docs/profile-v1.md` and `docs/sdk-v1.md`: v1 is mini-HTML
> widgets (at most 4 KB compressed, 32 KB uncompressed, delivered in the message),
> no bundled libraries, Shadow DOM as the web renderer, QuickJS everywhere.

Overview of the discussion: requirements, architecture options, widget formats in the ecosystem, the "bot + Claude API" scenario and the risks.

---

## 1. The problem

Besides Markdown, AI agents send HTML widgets in their replies: charts, calculators, quizzes, forms. They have to be shown inside Telegram messages on every client.

**Hard requirement:** a widget must have no way to reveal the user's IP, i.e. no external network calls from the device. Requests to Telegram's servers are fine — Telegram knows the IP anyway.

**Target scenario:** the bot calls the Claude API → gets a reply with HTML → puts a single self-contained HTML file (inline JS + CSS) into the message → the client shows and executes it.

---

## 2. Why CSP alone is not enough

CSP closes `fetch`, XHR, WebSocket and subresources, but does not close:

- **Navigation of the frame itself.** `location = 'https://x/?…'` is not caught by `connect-src`, and `navigate-to` was removed from the specification.
- **WebRTC.** `RTCPeerConnection` with STUN/TURN on a foreign server reveals the public IP. The `webrtc` CSP directive is poorly supported.
- **Fresh copies of the API.** JS patches (deleting `RTCPeerConnection` and the like) are bypassed through an `about:blank`/`srcdoc` iframe.
- **DNS prefetch.** `<link rel=dns-prefetch|preconnect>` and link prefetch send a request through the resolver (with ECS — the user's subnet).

**Key observation:** every hard vector requires executing JS in the page context.

---

## 3. Option A: hardened WebView

Three layers: the server makes the widget self-contained, the client cuts the network at the engine level, CSP and JS hardening are the second line.

### 3.1. Server, on send

- External images, fonts and CSS are downloaded and re-uploaded to Telegram's CDN (as in Instant View).
- Scripts from popular CDNs (Chart.js, D3, Tailwind, Mermaid) are not bundled (widget profile 1, `docs/profile-v1.md`): an external script never runs, and widgets draw charts with inline SVG or canvas. (An earlier prototype bundled Chart.js 4 and D3 7 under their CDN names; they left the product with profile 1.)
- Everything that cannot be localised is cut out. The widget is stored as a bundle: HTML + assets.

### 3.2. Native clients

- The bundle is served from a custom scheme `tgwidget://<id>/` via `WKURLSchemeHandler`; on Android — a fake https host via `WebViewAssetLoader`. Every widget gets its own origin.
- CSP is delivered as an HTTP header, not a meta tag:

  ```
  default-src 'none'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline';
  img-src 'self' data: blob:; font-src 'self' data:; connect-src 'none'; frame-src 'none';
  form-action 'none'; base-uri 'none'
  ```

- A network kill switch underneath CSP:

  | Platform | Mechanism |
  |---|---|
  | iOS / macOS | `WKContentRuleList` blocking all http(s) and ws(s) URLs; non-persistent `WKWebsiteDataStore`; `decidePolicyFor` cancels everything not on our scheme |
  | Android | `shouldInterceptRequest` returns an empty response for any foreign host; `shouldOverrideUrlLoading` cancels navigation. WebSocket does not pass through it — only `connect-src` holds it |
  | Desktop (WebView2) | A separate environment with the flags `--host-resolver-rules="MAP * ~NOTFOUND"`, `--proxy-server` pointing at a dead address, `--webrtc-ip-handling-policy=disable_non_proxied_udp` — closes everything at once, DNS included |

- **WebRTC is the weakest spot.** On mobile: strip the RTC APIs with a document-start script (`WKUserScript`, `addDocumentStartJavaScript` from androidx.webkit) in every frame and forbid nested frames. Needs a dedicated pentest.
- Reject all permission requests, forbid `window.open`, disable DNS prefetch, strip `<link rel=preconnect|dns-prefetch|prefetch>`.
- A tap on a link goes through the standard "Open link?" dialog: a deliberate user action, like a link in text.

### 3.3. Web clients

- `<iframe sandbox="allow-scripts">` without `allow-same-origin`: opaque origin, no popups, forms or top-window navigation.
- **Self-navigation of the frame:** an intermediate frame on a separate domain with a `frame-src 'none'` header, the widget inside it via `srcdoc`. The parent's `frame-src` cuts the child frame's navigations. Works in Chromium; Safari and Firefox need checking.
- **WebRTC cannot be reliably closed in the browser.** Options: a static mode without JS, or option B.

---

## 4. Option B: QuickJS sandbox + rendering through a remote DOM

This is **not a custom browser**. The system WebView stays the renderer but stops executing foreign code. What changes is where the widget's JS runs.

```
 Sandbox (QuickJS)            Host (our code)           WebView
 widget JS + DOM shim   ──▶   mutation validator   ──▶  renderer: only
 no fetch/WS/RTC/...          + sanitizer               our JS, strict CSP
                        ◀──   events (click, input) ◀──
```

- **Sandbox.** A separate interpreter; we assemble the global object ourselves (essentially an isolate with an allowlist of bindings). Inside: ECMAScript, the DOM shim, timers from the host, the bridge to the bot. `fetch`, XHR, `WebSocket`, `RTCPeerConnection` are not stripped — they simply do not exist. `import()` resolves only the built-in libraries.
- **DOM shim.** The widget works with `document`; every mutation becomes a protocol message. The idea is Shopify's remote-dom.
  - *Refining the analogy:* in Figma, QuickJS-wasm executes the plugin logic without a DOM (working on the scene), while the plugin UI is an ordinary iframe with network access. From Figma we take the isolation principle, from Shopify the DOM mirroring.
- **Host.** Treats everything inside the sandbox as hostile, the shim included. Applies mutations by allowlist: permitted tags and attributes, URLs only on our scheme / `data:` / `blob:`, a `url()` filter in CSS. `<script>`, `<iframe>`, `<meta>`, `<base>`, `<link>` and `on*` attributes do not pass.
- **WebView.** A dumb renderer: executes only our bundle, CSP with `script-src 'nonce-…'`. Even an `onerror=` that slipped past the sanitizer will not execute.

### 4.1. Why this closes the holes

Once JS is taken out of the page, only declarative leaks remain — URLs in markup and CSS. Their list is finite and is closed by the sanitizer, CSP and native request interception. Deny by default instead of hunting for holes.

### 4.2. Universality

The security boundary is the same on every platform.

| Platform | Sandbox | Renderer |
|---|---|---|
| iOS / macOS | QuickJS (C) or a bare `JSContext` without web APIs | WKWebView |
| Android | QuickJS via JNI or `androidx.javascriptengine` (V8 in an isolated process, no network and no DOM) | Android WebView |
| Desktop | QuickJS | WebView2 / WKWebView / WebKitGTK |
| Web | QuickJS-wasm in a Worker | sandboxed iframe |

Recommendation: QuickJS everywhere (e.g. the quickjs-ng fork) — one engine, one shim, one protocol, one test suite. It has a memory limit and an interrupt handler for killing infinite loops.

### 4.3. Why not a "lightweight webview" wholesale

The JS sandbox is light and portable; the heavy part is HTML/CSS rendering. There is litehtml (no JS, incomplete CSS), the experimental Blitz in Rust, the commercial Ultralight and Sciter. LLM widgets make heavy use of grid, SVG, animations, canvas — the fidelity gap would keep surfacing. The system WebView as the renderer is the better deal.

Native rendering is justified only if the format is constrained rather than the runtime: our own vocabulary of components (like Instant View blocks or A2UI).

---

## 5. Widget formats in the ecosystem

The main distinction is who writes the HTML: the model itself or the MCP server developer.

### 5.1. Raw HTML from the model

Claude's artifacts and inline visualisations, canvas in ChatGPT, generative UI in consumer Gemini, or simply an ```html block in the text. There is no single standard: every client wraps it its own way (a tag, a tool-call parameter, a code fence).

The content is typical: one file or fragment, inline CSS and JS, libraries from cdnjs/jsdelivr, often Tailwind CDN and Google Fonts.

### 5.2. MCP Apps — the de facto standard for interactive widgets

- A merger of MCP-UI and OpenAI's Apps SDK; the specification was written by Anthropic, OpenAI and MCP-UI. Version 2026-01-26 is Stable. Supported by ChatGPT, VS Code Copilot Chat, Goose.
- A tool declares its UI via `_meta.ui.resourceUri` → a `ui://…` resource with MIME `text/html;profile=mcp-app`.
- Rendered in a sandboxed iframe, communication with the host is JSON-RPC 2.0 over `postMessage` (`ui/initialize`, `ui/notifications/tool-input`, `ui/notifications/tool-result`, `tools/call`).
- **Deny-by-default CSP:** if the resource did not declare `_meta.ui.csp`, the host applies `default-src 'none'` and lets no undeclared domain through. External access is declared explicitly: `connectDomains`, `resourceDomains`, `frameDomains`, `baseUriDomains`.
- The specification allows hosts to preload and cache UI resources.

Older formats are still around:
- OpenAI's Apps SDK: `text/html+skybridge`, data via `window.openai`.
- MCP-UI: `text/html`, `text/uri-list`, `application/vnd.mcp-ui.remote-dom`. Remote DOM was removed from `createUIResource` in `@mcp-ui/server` v6 — agents will not send remote-dom, so the sandbox will have to execute ordinary HTML.

### 5.3. Declarative JSON — A2UI

Google's format (December 2025): the agent sends JSON with component names from the client's catalogue, the client draws them with its own code. No executable code comes from the agent. Renderers: Lit, Flutter, React.

### 5.4. Conclusions for Telegram

- **MCP Apps as the primary Bot API format.** The ecosystem already writes for it, and its security model almost matches the requirement. `connectDomains` are never honoured; `resourceDomains` are closed by re-uploading on send; live data goes through `tools/call` → the bot via Telegram's servers.
- **"Template + data".** The template is uploaded once as a file, the message carries a reference to it plus JSON with the data. Cheaper, cacheable, the template is checked once.
- **Raw HTML from the model** — the same container without a template: the whole HTML is in the message, the bridge is the same.
- **A2UI** — a second format, secure by construction: native rendering without JS, close to Instant View blocks. Suits forms, cards, tables; expressiveness is limited by the catalogue.

---

## 6. The "bot + Claude API" scenario on option B

The Claude API has no dedicated "widget" type: the bot declares a tool, e.g. `show_widget(html)`, and receives the HTML as ordinary tool use. A single self-contained HTML file is the most convenient format for the sandbox.

### 6.1. How the client executes the file

1. **Parsing.** The HTML is parsed by a DOM implementation inside QuickJS (something like linkedom) — this is the widget's initial tree.
2. **First render.** The host strips `<script>`, `on*` attributes and external URLs and hands the tree to the WebView. The layout is visible before any JS runs.
3. **Scripts.** Inline `<script>`s execute in QuickJS in document order, then `DOMContentLoaded` and `load` are emulated. `<script src>` resolves only to the built-in libraries (precompiled to bytecode).
4. **Mutations.** Accumulated and sent to the WebView in a batch at the end of the tick or on `requestAnimationFrame`.
5. **Events.** A click/input is serialised (node id, type, coordinates, value) and dispatched in the sandbox, including handlers from `onclick="..."`.

### 6.2. Compatibility

**Works almost for free:** layout, all of CSS, CSS animations, SVG, forms, tabs, calculators, quizzes, sortable tables — the bulk of Claude's widgets.

**Needs engineering:**
- **Synchronous measurements** (`getBoundingClientRect`, `offsetWidth`, `getComputedStyle`, `getBBox`). QuickJS on its own thread blocks while the main thread asks the WebView. Every call is a round trip; D3 in a loop can make hundreds. A layout cache valid until the next mutation is needed.
- **Canvas 2D** (Chart.js is one of the most common libraries). The context is proxied as a command stream replayed on a real canvas; `measureText` — synchronously, with a cache. The biggest chunk of work.
- **Input fields.** The value lives in the WebView; the sandbox has to mirror it on every `input` event.

**Does not work:** WebGL / Three.js, Web Audio, workers, the network (by design). `localStorage` is emulated in memory; links and `window.open` go through the Telegram dialog.

### 6.3. Performance

- QuickJS is an interpreter; precompile the built-in libraries to bytecode.
- The per-widget runtime is cheap, the WebView is more expensive. Execute only visible widgets; kill those that left the screen, keeping a static snapshot of the DOM; restart on return or on tap.

### 6.4. The main lever — a runtime profile

The bot puts the Telegram runtime profile into the description of the `show_widget` tool:
- one file, no network;
- the list of available libraries and their path;
- no WebGL;
- a bridge like `Telegram.Widget.sendMessage()` for user actions.

The model follows such constraints well. The client then has to execute not an arbitrary widget but a widget written for a known profile — compatibility rises sharply. The profile can be published as part of the Bot API documentation.

### 6.5. Validation and fallback

- **Validation:** run a few hundred typical requests through the API with and without the profile, collect the widgets, run them through the prototype, count the share rendered correctly.
- **Fallback:** for the unsupported (WebGL) — a static snapshot with an "Open" button into a full-screen WebView with the native kill switch from option A.

---

## 7. Open questions and risks

- **The synchronous bridge on the web** requires cross-origin isolation (COOP/COEP) — may break other embeddings in the web client.
- **`preventDefault` from the sandbox is asynchronous** — it cannot cancel the native default action in the renderer. The renderer always cancels navigation and submit; the other cases (checkboxes, selection) need to be described and worked around.
- **Focus, caret, IME** in input fields when mirroring the value.
- **D3 / Mermaid performance** with hundreds of `getBBox` calls.
- **Memory:** a WebView per visible widget.
- **WebRTC** in option A on mobile — API removal only, needs a pentest.
- **App Store Review Guidelines:** check executing downloaded JS in our own interpreter.
- **Self-navigation of the frame in the web client** (option A) — check the behaviour of the parent's `frame-src` in Safari and Firefox.

---

## 8. Next step

A prototype of option B in the browser: QuickJS-wasm in a Worker + a sandboxed iframe as the renderer + automated tests for leaks and compatibility. The browser version mirrors the native architecture (JS on a separate thread, the renderer separate) and immediately answers the question for the web client.

---

## Sources

- [MCP Apps: Interactive UIs Become an Official Part of MCP](https://rits.shanghai.nyu.edu/ai/mcp-apps-interactive-uis-become-an-official-part-of-mcp)
- [MCP Apps: The iframe Is the Agent](https://dev.to/rcsxplatform/mcp-apps-the-iframe-is-the-agent-40k9)
- [What Are MCP Apps](https://www.morphllm.com/mcp-apps)
- [Building MCP Apps (hexdocs)](https://hexdocs.pm/mcp_server/mcp_apps.html)
- [MCP Apps reference (nanohub)](https://nanohub-mcp.readthedocs.io/en/latest/skill/references/mcp-apps.html)
- [MCP-UI on GitHub](https://github.com/MCP-UI-Org/mcp-ui)
- [mcp-use widgets API reference](https://docs.mcp-use.com/typescript/api-reference/server/widgets)
- [Apps SDK Resources (mcp-use)](https://mcp-use.com/docs/typescript/server/apps-sdk-resources)
- [The call for an agentic standard (UX Collective)](https://uxdesign.cc/the-call-for-an-agentic-standard-we-need-to-stop-shipping-the-same-form-four-different-times-be5b9d40e370)
- [Google launches A2UI (SD Times)](https://sdtimes.com/ai/google-launches-a2ui-project-to-enable-agents-to-build-contextually-relevant-uis/)
- [A2UI (landscape)](https://landscape.jimmysong.io/projects/a2ui/)

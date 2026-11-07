# Shadow DOM instead of iframe: a frameless renderer for the web

**Decided 2026-10-16 (release plan D2): this is the web product renderer.**
Invariant 6 is reworded and the web client's page CSP requirements are in
`docs/decisions.md`, "The web renders through Shadow DOM; invariant 6 reworded".
The text below is the prototype's record.

Date: 2026-10-12. The question: which renderer to take as the product path on the
web if the feed holds hundreds of bubbles. A srcdoc iframe per bubble (`IframeRenderer`,
the product path so far) pays for every bubble with a separate document: asynchronous
start (loading the document, executing the renderer bundle, the handshake over
MessagePort), its own style context, its own observers, and nothing can be
built before insertion into the DOM. Blitz and litehtml on the web (wasm → canvas) do not
solve this: 8 MB and 1.5 MB of wasm per tab, no grid/forms/text selection, re-parsing
in litehtml on every batch.

The prototype: **the same renderer core as in the iframe, on a shadow root inside the chat
page.** The sandbox (QuickJS worker), the validator and the protocol are unchanged; the iframe
was never the primary security boundary, it is the sixth invariant
(defense in depth). This relaxes invariant 6, hence a prototype, not a product:
the decision goes by the numbers below.

## What was done

| Part | File | What |
|---|---|---|
| renderer core | `packages/core/src/renderer/core.ts` | the former `renderer.ts` became the factory `createRenderer(env)`: one instance per widget, an environment `RendererEnv` (document or shadow root: element factory, root for listeners and `elementFromPoint`, `html/head/body`, coordinate origin, viewport, document height, `matchMedia`, subscriptions to resize and theme change). The coordinates of `rect`/`rects`/`getScreenCTM`/`elementFromPoint` and event `clientX/Y` are translated into the widget's system if the origin is non-zero |
| bundle entry | `packages/core/src/renderer/renderer.ts` | only the transport (`port` / `webkit` / `android`) and `documentEnv()`; the bundles for the iframe and the native WebViews are unchanged in behaviour |
| renderer | `packages/core/src/host/shadow-renderer.ts` | `ShadowDomRenderer` behind the same `Renderer` seam: `<div class="widget-frame widget-shadow">` with an open shadow root, inside it a base `<style>` and real `<html data-tw-root><head><body>` (UA rules and the widget's `html {}` / `body {}` match as in a document). Messages are applied synchronously; before `load()` they are queued |
| base styles | same file, `BASE_CSS` | `:host { all: initial; display: block; contain: layout paint style; container-type: inline-size; container-name: tw }` — cuts inheritance from the chat page (the widget starts from UA defaults, as in a frame), `position: fixed`, stacking contexts and overflow stay inside the bubble; `:where(html) { display: flow-root }` so that `body` margins do not collapse outward and are included in the measured height; the body's margin, the font and the text colour come from the profile's UA layer as in the iframe (since v0.1.1 no `body { margin: 8px }` here: an unlayered rule would beat the layer); the host element is the canvas: the renderer core copies the root's background, or the body's when the root has none, onto it (`RendererEnv.canvas`), as CSS propagates it to a frame's canvas |
| validator | `packages/core/src/host/css-sanitizer.ts`, `CssEnv.scope: 'shadow'` | selectors able to escape the widget tree (`:host`, `:host()`, `:host-context()`, `::slotted()`, `::part()`, `:scope`) are dropped from the list; `:root` → `[data-tw-root]` (same specificity); `@media` on width/height/orientation only → `@container tw …` on the bubble (`screen and (max-width: 480px), (orientation: portrait)` → `tw (max-width: 480px) or (orientation: portrait)`), the rest (`hover`, `print`…) are outside widget profile 1 and dropped on every renderer; `vw/vmin/vmax` and `sv*/lv*/dv*` → `cq*` (`vh` and the other height units are outside profile 1). Since profile 1 the host-reaching selectors, popovers, `showModal()` and `@property` are out on every renderer, so the Shadow DOM renderer and the iframe agree (`docs/profile-v1.md`). The default scope is `document`: the iframe and the native platforms are untouched. `Validator.setTheme` preserves the scope |
| host | `packages/core/src/host/widget-host.ts`, `packages/core/src/host/renderer.ts` | the renderer is created before the validator and reports `cssScope`; a synchronous size report before the worker is created no longer crashes the host |
| dev UI | the **shadow** toggle / `?shadow=1`, a column in "compare all" | |
| runs | `TW_RENDERER=shadow pnpm test:attacks` / `test:generated` / `test:common` | in attacks the widget DOM is now in the application page itself, so "0 requests out and 0 canary hits" measure exactly what the frame used to add; in generated the clicks and the 3 % threshold apply as for the iframe (same engine), report `docs/compat-report-generated-shadow.md` |
| feed bench | `apps/web/bench/feed.html`, `apps/web/bench/feed.ts`, `scripts/feed-bench.ts` | N bubbles, iframe against shadow: `mode=ops` replays the validated ops of five generated widgets (from the Node harness, `apps/web/bench/ops/`, gitignored) without the sandbox — the pure renderer cost; `mode=host` — a full `WidgetHost` per bubble. Measures build / ready / painted, long tasks, memory (`measureUserAgentSpecificMemory`: the page is cross-origin isolated, frames are included) and frames while scrolling |
| unit | `packages/core/tests/unit/validator.test.ts` | 5 tests for the scoped sanitizer |

## What is lost without the iframe and how it is covered

| What the iframe gave | Without the iframe | How the prototype covers it |
|---|---|---|
| opaque origin: the widget has no cookies/storage/origin of the application | the widget DOM is in the chat's origin | there is no widget JS in the renderer (invariant 1), the ops are validated; the shadow root keeps widget elements out of the named properties of `document`/`window` (clobbering the page) and out of the application's `document.getElementById` |
| CSP of the subtree: `img-src data:`, `connect-src 'none'`, `form-action 'none'` | the application page's CSP applies | the validator's url-policy (only `data:` and `#`), the renderer rejects forbidden tags/attributes itself (`FORBIDDEN_TAGS/ATTRS`), `submit` and clicks on `<a>` are cancelled unconditionally; the attack suite catches any outbound request from the page |
| style isolation | the shadow root closes selectors in both directions, but inherited properties flow in, `position: fixed` and `z-index` escape, `:host` reaches the wrapper | `:host { all: initial }`, `contain: layout paint style`, the sanitizer cuts the `:host` family |
| viewport = widget size (`vw/vh`, `@media (max-width)`, `innerWidth`, `matchMedia`) | viewport = chat window | `@container tw`, `cq*` units, `viewport()` and `matchMedia` on width/height answer from the bubble size (`ShadowDomRenderer.env`) |
| event coordinates and `getBoundingClientRect` relative to the widget | relative to the window | `env.origin()` is subtracted in the core |
| a separate document: `offsetParent` ends at `body`, window `scrollTo` | one document | the widget's `html` is `position: relative` (the `offsetParent` chain ends at it); scrolling the window from the widget is not supported (and was meaningless in a frame too) |
| crash isolation | there was none in the frame either: a srcdoc frame lives in the same renderer process | — |

Known holes of the prototype: `@media` with `hover`/`pointer` stays against the window (in the chat it is the same thing); `light-dark()` in `style=""` — as before.

## Results (2026-10-12, headless Chromium, dev server)

Runs after the core refactoring (all five back to back in one chain):

| Run | Result |
|---|---|
| `pnpm test:compat` (iframe, the product path) | 22 / 22 |
| `pnpm test:attacks` (iframe) | 100 / 100, 0 canary hits, 0 requests out |
| `TW_RENDERER=shadow pnpm test:attacks` | 100 / 100, 0 hits, 0 requests out: the widget DOM is in the page itself, the validator holds the selection without a frame |
| `TW_RENDERER=shadow pnpm test:generated` | 40 / 40 within 3 % of the reference, with clicks (`docs/compat-report-generated-shadow.md`; mean diff 1.6 %) |
| `pnpm test:generated` (iframe) | 40 / 40 |

Two bugs along the way, both fixed: `Document.prototype.getElementById` /
`elementFromPoint` on a ShadowRoot throw "Illegal invocation" (
`DocumentOrShadowRoot` has separate copies of the methods on each prototype; the core
picks them by the root's type), and `min-height: 100vh` on `body` (02, 12 without
a profile): `100cqh` without a size container is the window, so `:host` became
`container-type: size` and the same fixed point as the frame.

Feed bench, `scripts/feed-bench.ts` → `docs/feed-bench-web.md` (900×900, five
generated widgets round-robin, bubble heights clamped to 24–900 px (the dev app's clamp at the time; 50–600 since v0.1.0) as in
`WidgetHost`; `ready` — all bubbles have applied their ops, `task` — all main-thread
time per `Performance.getMetrics`, heap/documents — deltas of the same counters):

| Mode | Bubbles | Renderer | ready, ms | per bubble, ms | main thread, ms | layout + style, ms | JS heap, MB | documents | frames while scrolling |
|---|---|---|---|---|---|---|---|---|---|
| ops (renderer only) | 50 | iframe | 252 | 5.0 | 369 | 70 | +22.2 | +49 | 16.7 avg, 0 slow |
| | | **shadow** | **66** | **1.3** | **140** | 60 | **+0.7** | 0 | 16.7 avg, 0 slow |
| ops | 200 | iframe | 880 | 4.4 | 1069 | 168 | +99.9 | +237 | 16.7 avg, 0 slow |
| | | **shadow** | **153** | **0.8** | **315** | 169 | **+1.5** | 0 | 16.7 avg, 0 slow |
| host (sandbox + validator + renderer, 4 in parallel) | 50 | iframe | 1575 | 31.5 | 603 | 100 | +27.0 | +63 | no slow frames |
| | | shadow | 1405 | 28.1 | 300 | 91 | +4.1 | 0 | no slow frames |
| host | 200 | iframe | 4064 | 20.3 | 2088 | 245 | +97.4 | +267 | no slow frames |
| | | shadow | 3628 | 18.1 | 928 | 234 | +8.2 | 0 | no slow frames |

What follows from this:

- **The cost of the renderer itself drops 5–6×.** 200 bubbles: 153 ms against
  880 ms to ready, 315 ms against 1069 ms of main thread. Layout and style
  are the same (same engine, same nodes); the whole difference is the document per
  bubble: executing the renderer bundle 200 times, 200 handshakes over
  MessagePort, 237 documents.
- **Memory: ~0.5 MB of JS heap per bubble goes away.** +100 MB against +1.5 MB for
  200 bubbles — these are the copies of the renderer bundle and the globals in every frame; DOM memory
  is not part of the heap, but the node count is the same.
- **Synchronicity.** For shadow `build = ready`: the ops are applied by the time
  `send` returns, the tree can be assembled before insertion into the feed (what the iframe lacked).
- **With the sandbox the difference drowns in the QuickJS start** (18–30 ms per bubble with four
  parallel boots): 3.6 s against 4.1 s for 200 bubbles. The next reserve on the
  web is not the renderer but engine/validator pools and lazy boot, as in the iOS feed
  (`docs/chat-feed-ios.md`).
- `content-visibility: auto` on the bubbles (the CV columns in the full report) at 200
  bubbles changes nothing on either side: scrolling is 60 fps in headless anyway.
  To be checked on a phone.

## Worker pool (the web twin of `EnginePool` from the iOS feed)

Booting the sandbox on the web means creating a worker, instantiating QuickJS (wasm) and
executing the shim bundle; after that only `boot` with the widget HTML follows. `packages/core/src/host/worker-pool.ts`
keeps `size` workers that already have the first half done: the `init`
protocol is split into `warm` (SAB, limits, QuickJS, bundle → reply `warmed`) and
`start` (HTML and the real config → `loaded`); `init` stays as `warm` + `start`
in one message, the native platforms are untouched. The host takes a worker from the pool
(`WidgetHost.Options.workerPool`), the pool refills immediately (`canRefill` can
defer this while the feed is scrolling), `prewarm()` fills it at application
start-up. A subtlety: while executing the bundle the shim immediately sends the ops of the document
skeleton (`html`/`head`/`body`), so everything the worker said while idle is
buffered by the pool (`PooledWorker.pending`), and the host replays it to the validator before
`start` — otherwise insertions into a non-existent `body` are rejected. Dev UI: `?pool=N`
(`__tw.pool` — counters), the run `TW_POOL=8 pnpm test:compat`.

Bench (`--pool 8,32`, the pool is prewarmed before the clock starts and refills along the way;
`hits` — bubbles that got a worker from the pool, including one still warming):

| Bubbles | Renderer | no pool | pool 8 | pool 32 |
|---|---|---|---|---|
| 50 | iframe | 1533 ms (30.7 per bubble) | 856 ms (17.1) | 766 ms (15.3) |
| 50 | shadow | 1405 ms (28.1) | 793 ms (15.9) | **623 ms (12.5)** |
| 200 | iframe | 4074 ms (20.4) | 2854 ms (14.3) | 2574 ms (12.9) |
| 200 | shadow | 3643 ms (18.2) | 2271 ms (11.4) | **1963 ms (9.8)** |

- **The pool takes off another half.** 200 bubbles on shadow: 3.6 s → 2.0 s; 50 bubbles
  with a prewarmed pool of 32 — 0.6 s, i.e. the first screen of the feed, fifty widgets,
  comes down to 10 ms per bubble. The remainder is the widget's own scripts, the validator and the first
  layout.
- **The pool and shadow add up.** iframe with a pool of 32 (2.6 s) is still slower than
  shadow with a pool of 8 (2.3 s): the frame per bubble remains the second line item.
- Warming 8 workers — 220 ms, 32 — 500 ms (in the background, at application start-up).
  Pool memory: one QuickJS runtime with the bundle per worker, in the bench ≈ 1 MB of the page's JS
  heap per worker is not visible (workers are separate isolates); count it by
  `docs/android-renderer-bench.md` (6.3 MB per QuickJS + validator there).
- The bench creates bubbles back to back, 4 in parallel, so at 200 bubbles the pool
  mostly overlaps the boots with each other rather than removing them; in a live feed with
  refilling while idle the share of "already warm" is higher than here.

## What to decide (decided: Shadow DOM, D2)

The prototype shows: without the iframe on the web only what the validator holds
anyway is lost (attacks 100/100), compatibility is the same (generated 40/40), and the renderer
cost per bubble drops from ~4–5 ms and ~0.5 MB to ~1 ms and ~0. What remains is the
decision on invariant 6: declare the shadow renderer the web's product path (and
then rewrite the invariant as "a sandboxed frame or a shadow root with a scoped
validator; the application page's CSP must forbid `connect-src` and
`form-action` for the widget subtree — or rather, for the whole page") or
keep the iframe and pay for a document per bubble.

## Enter and leave events (profile 1)

Chromium dispatches `pointerenter`/`pointerleave`/`mouseenter`/`mouseleave` only to
nodes that listen for them or that have an ancestor element with a capturing listener.
The renderer listens on the shadow root, which is not an element, so the widget never
got enter or leave events on this renderer (conformance `input.hover-events`). The core
adds a capturing no-op listener for the four types on the shadow host
(`src/renderer/core.ts`); the events then reach the shadow root's listener as in a frame.


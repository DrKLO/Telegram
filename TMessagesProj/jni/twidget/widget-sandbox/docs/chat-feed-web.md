# Chat feed on the web: the dev app's `?feed=1` demo

Date: 2026-10-12. The web twin of the iOS chat feed (`docs/chat-feed-ios.md`):
a vertical feed of 60–80 bubbles in the dev app, each an ordinary `WidgetHost`
on `corpus/showcase` (the interactive, animated demos; `corpus/common` on the
**corpus** select for the flat renderer-comparison set), driven by the list's own lifecycle. It exists to show the
architecture under load and in contrast, not by clicking one bubble: the
warmed worker pool against cold boots, the renderer swapped live under the
same feed, one hostile bubble among the normal ones, and a metrics overlay.
Dev tooling only (`apps/web/src/feed.ts`, wired from `apps/web/src/main.ts`); nothing ships in a
native build. The sandbox, the validator and the renderers are untouched, every
bubble gets validated ops only, and the attack suite is unchanged.

Open it: `corepack pnpm dev`, then `http://127.0.0.1:5173/?feed=1` (or the
**feed** toggle in the toolbar). The canvas renderers need
`corepack pnpm build:blitz-web` / `build:litehtml-web` once. The canary line
in the overlay needs `corepack pnpm canary` (the attack suite's server on
127.0.0.1:9999); without it the line says so.

## What the feed does

| Part | Where | What |
|---|---|---|
| content | `corpus/showcase` (default), `corpus/common`, `corpus/gestures` or `corpus/resize` | **corpus** select: the 21 showcase demos (`corpus/showcase/README.md`: boarding pass, ride tracking, smart home, kanban, live poll …, one of each batch in turn — travel, commerce, life, work), the 32 common-profile widgets (cards, interactive, live, heavy), or the gesture set (`corpus/gestures/README.md`: what the horizontal-only rule removed, a joystick game and 2048 on `touch-action: none` and on `pan-y`, now alike, a wheel picker that no longer turns, and a paddle game, a card carousel and a range slider; on a touch device a vertical drag scrolls the feed over every widget); repeated to `count` (72 by default), a text bubble before every other one. or the resize anchors (`corpus/resize/README.md`: an FAQ, a support thread that grows upward, results that grow downward). Switching rebuilds the feed (`reset`) |
| lifecycle | three `IntersectionObserver`s on the scroll container | a host is created when a bubble is within `ahead` screens (0.5) of the viewport, hidden with `setVisible(false)` when it leaves it (no frames, timers at 1 Hz), dropped when it is `keep` screens (2) away; the height is remembered so the layout holds still (the iOS feed keeps a snapshot too; here a dashed placeholder) and a new host replaces it when the bubble comes back. At most `lanes` (4) sandboxes boot at once, nearest to the viewport first |
| pool | `packages/core/src/host/worker-pool.ts` | **pool ×6** on: hosts take a warmed worker (QuickJS + the sandbox bundle evaluated, waiting for `start`); off: every bubble boots its own. The overlay keeps two columns, one per setting, so a scroll with the pool off and one with it on compare on the same screen |
| renderer | `packages/core/src/host/renderer.ts` | **iframe / shadow / litehtml / blitz**: switching re-creates every live host behind the new factory, in place, nearest first; parked bubbles come back on the new renderer when scrolled to. Same HTML, same sandbox, same validator |
| hostile bubble | `corpus/attacks` | the third bubble; `fallback-at-boot` by default (an infinite loop in the first script: the boot CPU slice kills it, the static markup stays as the frozen fallback, the bubble gets the stop line). Others on the list: `cpu-raf-burn` (throttled, "CPU deferred ×N" in the head line), `timer-flood` ("timers capped"), `api-fetch` / `markup-img-src` ("blocked ×N", canary at 0). A killed widget is never re-run: after a renderer switch it shows "Widget stopped" |
| overlay | bottom right | renderer, live / booting / parked / stopped counts, pool counters (warm, hits, misses), per pool setting: bubbles booted, create → content and shown → content (median / p90), memory (`measureUserAgentSpecificMemory` where the browser has it — headless Chromium does, the desktop app's pane only gives `performance.memory`, the page heap — plus the sum of the live widgets' QuickJS heaps from their stats), canary hits + what the validator and shim blocked, scroll frame p95 |
| resize anchor | a `ResizeObserver` on the bubbles | the edge a bubble's widget chose (`setResizeAnchor`, `onResize(height, anchor)`) stays put on screen when its height changes: `bottom` adds the difference to the scroll position, `top` changes nothing; a bubble above the viewport keeps the visible content in place; the browser's own scroll anchoring is off (`overflow-anchor: none`). The same for the boot placeholder (the cached height) giving way and a re-created bubble (v0.1.3, `docs/sdk-v1.md` §5.6) |
| scripted scroll | **▶ auto-scroll** | one screen (90 %), pause 0.9 s, repeat to the bottom and back, like the iOS `step` run |

"create → content" is host creation to the first size report with content
(taller than the 24 px empty document, or any report after `loaded`).
"shown → content" is from the bubble entering the viewport; negative means the
widget was ready before the reader got there (prefetch did its job).

| bench | **📊 bench** / `apps/web/src/feed-bench.ts` | the measurement panel: N passes of the same feed per renderer × pool cell, two live tables, a JSON report — see "Measuring" below |

URL: `?feed=1&corpus=common&renderer=shadow&pool=0&poolSize=6&hostile=cpu-raf-burn&count=72&lanes=4&ahead=0.5&keep=2&theme=dark`;
`window.__tw.feed` has the same switches for scripts (`setCorpus`, `setRenderer`, `setPool`,
`setHostile`, `reset`, `autoScroll`, `summary`, `bubbles`).

## The four scenes

1. **Pool on/off while scrolling.** Untick **pool ×6**, press **reset**, then
   **▶ auto-scroll** (or scroll by hand): every bubble that comes near sits on
   the striped placeholder while its own QuickJS boots and the sandbox bundle
   evaluates — the placeholder counts the milliseconds. Tick the pool back on,
   **reset**, scroll again: the bubbles arrive with content, the placeholder
   barely flashes. The overlay's two columns hold both runs.
2. **Renderer switched live.** On a scrolled feed pick **shadow**, then
   **litehtml** or **blitz**: every bubble on screen re-renders behind the new
   seam, the feed does not move (heights are remembered), the parked ones come
   back on the new renderer. The pool is unaffected: the renderer is not on
   the sandbox's clock.
3. **One hostile bubble.** The third bubble is `fallback-at-boot`: it shows
   its static markup, gets the renderer's "Widget stopped" badge and the red
   stop line, and the rest of the feed scrolls on; the canary line stays at 0
   hits. Pick another attack from **hostile** to see throttling, the timer cap
   or blocked URLs instead.
4. **The overlay.** Booted counts, create → content and shown → content per
   pool setting, memory, canary, scroll frames.

## Measuring: the bench panel (`📊 bench`, `apps/web/src/feed-bench.ts`)

The overlay shows the feed as it runs; the bench panel answers "how much,
measured, with the noise known". It is the web twin of the iOS
`--autorun feed` + `feed-report.json`: the same feed, run N passes per
renderer × pool setting with the same scripted scroll, every boot recorded,
and the two tables rendered from the collected object — nothing in them is
typed in. **📊 bench** opens the panel (`?bench=open`); **▶ run** runs the
matrix ticked in it; `?bench=1` opens and runs with the URL's options
(`passes`, `benchCount`, `combos=iframe:on,shadow:on,litehtml:on,blitz:on,iframe:off,blitz:off`,
`prewarm=0`, `settle`); `window.__tw.feed.bench.run(opts)` returns the report
object, `.json()` / `.markdown()` serialise it, **⬇ JSON** downloads it.

A pass: fresh pool (prewarmed before the feed opens, as a messenger does at
launch — untick **prewarm** to let it fill while the first screen boots) →
`reset` on corpus/common with the chosen bubble count and no hostile bubble →
1.5 s for the first screen → the scripted `step` scroll down and back (90 %
of a screen, pause 0.9 s, the iOS run's rhythm) → 1.5 s settle → read out.
The demo's own periodic work (overlay refresh, memory and canary polls) is
paused meanwhile. Cells are **median / p90**: per-bubble rows over the
bubbles of a pass, then the median of those across the passes; per-pass rows
across the passes. Cold = the first boot of a pass, warm = every other.

Each row belongs to one claim of the demo:

| claim | rows | how it is measured |
|---|---|---|
| **starts instantly** (the pool) | time to first content cold / warm; blank while visible; bubbles blank > 100 ms; renderer create → first report; host constructor | host creation → the first size report with content; the time a bubble sat inside the viewport without content (0 = ready before the reader got there); the renderer alone, construction → its first message (the iframe's document load, a canvas renderer's wasm init); the synchronous `new WidgetHost` on the main thread |
| **cheap at scale** (the renderer) | memory per live bubble: browser processes, page JS heap; QuickJS heap per live bubble; live bubbles at the memory reading | the memory ramp at the end of each pass: the drop distance is lifted and the feed is scrolled through once more so every bubble boots and stays live; (memory with all live − memory just before, with the keep window live) ÷ the bubbles added — the RSS of every Chromium process (the Playwright driver only, through `__twProcessMemory`), `performance.memory` after `gc()` where exposed; the engine's own `memory_used_size` summed over the live widgets ÷ live |
| **feels native under the finger** (the main thread) | long tasks total, per second of scroll, longest; longest frame; dropped frames (estimate) | `PerformanceObserver` `longtask` during the scroll; a `requestAnimationFrame` loop: the longest interval and Σ round(interval ÷ expected) − 1, expected = the pass's 20th-percentile interval snapped to 60/90/120 Hz |

Under the tables the panel prints what the cells depend on (source of the
memory row, boots that got no content, a tab hidden during a scroll — Chromium
throttles frames and timers there, which the desktop app's pane does whenever
it is not in front) and the "not measured" line: `performance.memory` is the
main thread's heap only (no workers, no isolated frame), and
`measureUserAgentSpecificMemory` is refused by this Chromium (both the
desktop pane and headless 153 throw `SecurityError`); the pool's prewarm is
outside every clock; wasm modules and the dev server's files are fetched once
per page load; CPU frequency, thermal state, the unminified dev build; long
tasks start at 50 ms, so shorter stalls show only in the frame rows.

The driver writes the report from headless Chromium (one browser, one
window, the whole matrix back to back) — the numbers below come from it:

```bash
corepack pnpm tsx scripts/feed-report.ts --passes 5 --count 60 --origin http://127.0.0.1:5173   # → docs/feed-report-web.json + .md
```

## Numbers from the bench (headless Chromium 153, 10-core M-series Mac, 2026-10-12)

`docs/feed-report-web.md` is the driver's output (the JSON next to it has
every boot and sample): corpus/common, 60 bubbles, 5 passes per cell, the
step scroll, pool ×6 prewarmed, scroller 680×822 css px. The cells below are
copied from it (median / p90):

| | iframe, pool off | iframe, pool on | shadow, on | litehtml, on | blitz, off | blitz, on |
|---|---:|---:|---:|---:|---:|---:|
| time to first content, cold | 206 / 211 ms | 45 / 58 ms | 35 / 37 ms | 43 / 65 ms | 190 / 191 ms | 29 / 80 ms |
| time to first content, warm | 105 / 119 ms | 25 / 36 ms | 23 / 31 ms | 27 / 38 ms | 100 / 116 ms | 22 / 31 ms |
| blank while visible | 0 / 28 ms | 0 / 0 ms | 0 / 0 ms | 0 / 0 ms | 0 / 28 ms | 0 / 0 ms |
| bubbles blank > 100 ms (per pass) | 7 / 7 | 1 / 1 | 0 / 1 | 1 / 1 | 7 / 8 | 1 / 1 |
| renderer create → first report | 7.4 / 16 ms | 7.9 / 15 ms | 0.6 / 1.4 ms | 1.8 / 3.6 ms | 1.3 / 2.2 ms | 1.2 / 2.6 ms |
| memory per live bubble, browser processes | 11.2 / 12.9 MB | 11.0 / 13.1 MB | 8.7 / 11.5 MB | 15.1 / 15.4 MB | 14.1 / 16.5 MB | 13.5 / 15.0 MB |
| QuickJS heap per live bubble | 0.6 MB | 1.6 MB | 1.6 MB | 1.6 MB | 0.5 MB | 1.6 MB |
| long tasks during the scroll, total | 0 / 133 ms | 0 / 0 ms | 0 / 0 ms | 0 / 0 ms | 0 / 0 ms | 0 / 0 ms |
| longest frame | 17 / 367 ms | 17 / 33 ms | 17 / 33 ms | 17 / 50 ms | 17 / 17 ms | 17 / 33 ms |
| dropped frames (estimate) | 0 / 28 | 0 / 1 | 0 / 2 | 0 / 2 | 0 / 0 | 0 / 1 |

What they say, and what they do not:

- **The pool is the start.** Cold 206 → 45 ms and warm 105 → 25 ms on the
  iframe, 190 → 29 and 100 → 22 on Blitz: the QuickJS boot and the bundle
  evaluation are what a bubble waits for, and the pool takes them off the
  critical path; the renderer barely moves the number (the four pooled
  renderers are within 5 ms warm). The reader's view at the step rhythm is
  "blank while visible 0 ms" for every cell — prefetch half a screen ahead
  plus a 0.9 s pause per screen means the bubble is ready before it is
  seen; the difference shows in "bubbles blank > 100 ms": the first screen
  (7 cold boots per pass without the pool, 1 with it). Scroll faster than the
  script to turn that into visible blanks.
- **Memory per bubble is the engine, not the renderer.** 9–15 MB per live
  bubble of browser RSS with every renderer, of which the widget's own QuickJS
  heap is 0.5–1.6 MB: the rest is the worker (wasm memory, the sandbox
  bundle) and the frame or canvas. The iframe is not the expensive one here
  (Chromium puts the sandboxed srcdoc frames of one page into one process);
  the canvas renderers cost more per bubble than the DOM ones (a bitmap each).
  The pool's warm workers are outside the per-bubble figure.
- **The main thread is quiet in headless Chromium.** Long tasks appear only in
  the cold iframe cell (p90 133 ms, a 367 ms frame, 28 dropped frames in the
  worst pass); every pooled cell has no long task and one or two dropped
  frames per pass. Headless Chromium paces frames on a virtual vsync and the
  Mac has 10 cores: this row is where a phone will differ most, and the pane
  numbers below are the better illustration of cold-boot jank.
- Not measured, from the report's own line: `performance.memory` is the main
  thread's heap only and `measureUserAgentSpecificMemory` is refused
  (`SecurityError`) by this Chromium, so the memory row is process RSS over a
  one-minute ramp with the browser's drift in it; the pool's prewarm is outside
  every clock; wasm modules and the dev server's files are fetched once per
  page load; CPU frequency, thermal state, the unminified dev build; long
  tasks start at 50 ms.

## Numbers (desktop app's browser pane, 10-core M-series Mac, 2026-10-12)

72 bubbles, iframe renderer, the scripted scroll down and back, one run per
pool setting (≈150 boots each: parked bubbles are re-created on the way up):

| | pool off | pool ×6 |
|---|---|---|
| create → content, median / p90 | 111 / 131 ms | 28 / 279 ms |
| loaded (creation → scripts done), median / p90 | 115 / 165 ms | 30 / 94 ms |
| scroll frames over 32 ms (last 180 frames of scrolling), p95 frame | 62 / 180, 84 ms | 1 / 180, 17 ms |
| pool taken while empty | — | 0 (hits 153, the p90 is workers taken before their bundle finished evaluating during a burst of re-creations) |

A cold boot on this Mac is ~110 ms (QuickJS wasm + the sandbox bundle), a
fraction of the iOS simulator's 380 ms, so the blank-bubble contrast is milder
here than on a phone; what the eye sees first is the scroll: cold boots jank
the main thread (a third of the frames over 32 ms), pooled boots do not. With
`ahead` at half a screen and a 0.9 s pause per step, "shown → content" is
negative for both settings (median −810 ms with the pool, −1.4 s without: the
reader pauses long enough); scroll faster than the script to see the blanks.

Renderer switch on the same screen (three live bubbles, pooled): shadow
content in 35–55 ms, litehtml 69–112 ms (wasm init 69 ms on the first bubble),
Blitz 125–156 ms (wasm init 155 ms); after the switch the iframe bubbles that
were parked come back on the new renderer as they scroll in.

## Known gaps

- `mem-steady-growth` on the list does not get killed in the web sandbox: the
  widget keeps allocating typed arrays past the 64 MB limit (the overlay's
  QuickJS-heap line shows it climbing past a gigabyte). That is a sandbox gap
  in the web QuickJS build, not a feed one; it is on the list so the heap line
  can show it, with a note in its tooltip.
- No snapshot of a dropped bubble: the iframe is opaque and the canvas
  renderers could be copied, but the placeholder is a dashed box for all four.
- `measureUserAgentSpecificMemory` is refused (`SecurityError`) by the
  desktop app's pane and by headless Chromium 153 alike; the overlay and the
  bench say which source they have. The bench's process-memory row needs the
  Playwright driver.

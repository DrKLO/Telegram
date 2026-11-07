# Stress kit

Each Telegram client draws widgets with its own renderer: Shadow DOM on the web, WKWebView or Android WebView, or a
native engine (litehtml, TwidgetCore). The widget limits keep a widget from slowing the chat down on any of them, and
they are only right if they hold on the slowest renderer on a weak phone. This kit measures what each kind of load
costs on a renderer, level by level, on both sides of every strict limit, so that the limits and the renderer cost
model are set from real devices rather than from one fast desktop.

**Client teams: start with [CLIENTS.md](CLIENTS.md).** It covers the runner you write in your client, the devices, the
host configuration and the run file. This README is for the maintainers of the kit.

| | |
|---|---|
| [`widgets/`](widgets) | One widget per level of each load: `widgets/<load>/<value>.html` (the value zero-padded). Generated, never edited by hand. |
| [`manifest.json`](manifest.json) | The loads and their levels, and per widget: the heavy boxes on screen, the boxes changed every frame, rough counts of what the strict limits measure, the limits the level crosses, and the nodes, largest validated batch and height measured through the sandbox. Also the strict limits and the default warm-up and window. |
| [`run.schema.json`](run.schema.json) | The run file every runner writes. |
| [`tools/report.mjs`](tools/report.mjs) | Run files → `summary.md` + `summary.json`. No dependencies, Node ≥ 18. |

## How a widget works

Every widget is the same frame around one load: a header line (the load, the level and the widget's own frame rate)
and a 300 px field, clipped, where the load runs. The field fits a 320 px bubble, so every widget is 325 px tall. A
measuring loop on `requestAnimationFrame` counts the widget's frames and publishes them as JSON in the `data-stress`
attribute of `<html>` every 500 ms (`v`, `id`, `level`, `value`, `status`, `t`, `frames`, `fps`, `maxGap`, `jank`;
CLIENTS.md has the fields). A runner reads it from its validated ops, the way the conformance set's runners read
`data-conformance`: no renderer hook. The widget never finishes; the runner owns the timing (`manifest.run`: 2 s of
warm-up, a 10 s window).

The moving loads move, turn and recolour every box every frame (the ARS stress test's motion), so a renderer can reuse
nothing from the previous frame. Static loads keep only the measuring loop and the header running.

## The loads

<!-- stress:loads -->
| load | what it stresses | levels (over a strict limit in bold) | fit |
|---|---|---|---|
| `shadow-static` | Blurred shadows, static: boxes with a blurred box-shadow that never change: what an idle heavy widget costs per frame | 0, 8, 16, **32**, **64**, **128**, **256** boxes |  |
| `shadow-moving` | Blurred shadows, moving: boxes with a blurred box-shadow that move and change colour every frame | 0, 4, 8, 16, **32**, **64**, **128**, **256**, **512** boxes | A |
| `ars-cell` | The ARS cell, moving: the ARS stress test: each cell has a gradient, a blurred and an inset shadow, a border, opacity .85 and a label, and moves every frame | 0, 16, **64**, **128**, **256**, **512**, **1000** cells | A |
| `opacity-moving` | Opacity, moving: boxes with opacity .85 (and nothing else heavy) that move and change colour every frame | 0, 8, 16, **32**, **64**, **128**, **256**, **512** boxes | A |
| `filter-moving` | Filter, moving: boxes with filter: blur(1px) brightness(1.15) that move and change colour every frame | 0, 4, 8, 16, **32**, **64**, **128**, **256** boxes | A |
| `backdrop-count` | Backdrop filter, count: fixed 64×44 px panels with backdrop-filter: blur(6px) over stripes that move every frame | 0, 1, 2, **3**, **4**, **8**, **16** panels |  |
| `backdrop-area` | Backdrop filter, area: one panel with backdrop-filter: blur(8px) covering a share of the 300 px field, over stripes that move every frame | 0, 10, 25, 50, 100 % of the field |  |
| `gradient-moving` | Gradients, moving: boxes whose linear-gradient background turns and changes colour every frame while they move | 0, 16, 64, 128, 256, 512, 1000 boxes | B |
| `clip-moving` | Rounded clip, moving: boxes with overflow: hidden and border-radius clipping a child, moving and changing colour every frame | 0, 16, 64, 128, 256, 512, **1000** boxes | B |
| `dom-static` | Document size, static: a static document of N small elements: the boot batch, the idle cost of a large document | 100, 250, 500, 1000, **2000**, **3000**, **4000** elements |  |
| `dom-tick` | Document size, a few changing: N small static elements and 8 labels whose text changes every frame: the cost of a small change in a large document (a renderer that rebuilds the whole document pays for all N) | 100, 500, 1000, **2000**, **3000**, **4000** elements |  |
| `style-churn` | Style and text churn: plain boxes (nothing heavy) whose position, colour and label change every frame: three ops per box per frame | 16, 64, 256, **1000**, **2000**, **3000**, **4000** boxes | B |
| `text-volume` | Text volume: N characters of Latin text in paragraphs, one counter changing every frame | 1024, 4096, 16384, 49152, **65536**, **131072** chars |  |
| `complex-text` | Complex scripts: N characters of Arabic, Hebrew, Devanagari, Thai and CJK text; one 128-character paragraph is rewritten every frame (shaping on change) | 256, 1024, 2048, 4096, 8192, **16384** chars |  |
| `emoji` | Emoji: N colour emoji in rows of 32, eight of them cycling every frame | 50, 200, 500, 1000, 2000, 4000 emoji |  |
| `canvas-cmds` | Canvas commands per frame: a 600×600 canvas (300 px on screen) redrawn every frame with N commands (fillStyle + fillRect pairs) | 200, 1000, 2000, **4000**, **8000**, **16000** commands per frame |  |
| `svg-path` | SVG path, morphing: one SVG path of N line segments whose d attribute is rewritten every frame | 50, 200, 500, **1000**, **2000**, **4000** segments |  |
| `layout-queries` | Layout queries per frame: N pairs of a style write and a getBoundingClientRect read per frame on 40 bars: each read forces a layout, answered synchronously by the renderer | 0, 1, 4, 16, 64, **256** queries per frame |  |
| `css-rules` | CSS rules: N descendant style rules in stylesheets of 200 rules each; 8 containers change class every frame, so 64 boxes match against all N again | 50, 200, 400, **800**, **1600**, **3200** rules |  |
<!-- /stress:loads -->

`over` marks the levels the manifest expects over a strict limit, in force or proposed (`limits` in the manifest: 16 heavy boxes, 2 with
backdrop-filter, 2000 nodes, 10 000 ops per batch, 400 CSS rules, 10 000 canvas commands per send (provisional), 48 K of document text,
32 K per text value, 8 K per attribute, 8192 units of complex-script text). The counts are estimates from the load's
shape; `measured` in the manifest has the real nodes and largest batch.

## Building and running here

1. Rebuild the kit after editing a load (`scripts/stress/loads.ts`) or the widget frame (`scripts/stress/lib.ts`).
   This writes the widgets, this README's table and the manifest, and fails when a widget is over the size limit
   (3800 B after zlib at level 9, the conformance set's margin under 4096) or, through the sandbox (the dev app, the
   Shadow DOM renderer, the measuring configuration), does not boot, stops, throws, uses anything outside widget
   profile 1 (a `profile`, dropped-CSS, dropped-tag or blocked-URL diagnostic) or is taller than 560 px at 320 px
   wide. `--sizes` checks only the sizes, in a second, and keeps the measured values of the last full build:

   ```bash
   corepack pnpm tsx scripts/stress/build.ts
   corepack pnpm tsx scripts/stress/build.ts --sizes
   ```

   Without `--origin` the build starts a dev server of this checkout on a free port.

2. Start the dev server (from a worktree, on a port no other checkout uses) and run the web renderers, the reference
   runner:

   ```bash
   corepack pnpm dev --port 5178
   corepack pnpm tsx scripts/stress/run-web.ts --origin http://127.0.0.1:5178
   ```

   Options: `--renderers iframe,shadow` (the default; the frozen blitz and litehtml wasm prototypes when named and built), `--only shadow-moving,ars-cell`,
   `--warmup 2000 --measure 10000`, `--pass production`, `--width 390`, `--device "…"`. It writes
   `reports/stress/<pass>-<renderer>.json`, which is gitignored.

3. Turn the run files into the summary, the same tool the clients run:

   ```bash
   node packages/stress/tools/report.mjs --out reports/stress/summary reports/stress/*.json
   ```

`run-web.ts` loads each widget through `window.__tw.load` (the real worker → validator → renderer pipeline) and wraps
the renderer's `send` to read `data-stress` and count the validated ops. Client frames are `requestAnimationFrame`
timestamps in the frame that hosts the renderer (the page for Shadow DOM, the sandboxed iframe for the iframe
renderer), with the long tasks there; the wasm renderers' paint time is their own work per frame. The measuring
configuration goes to the dev app as URL parameters (`policy=compat&cpuQuotaMs=10000&…`); the run file records what
the host applied, `null` for an option the dev app does not pass on (it then runs on the host's default).

On a desktop the web renderers hold the full frame rate on most levels: the page's own frames stay at 60 Hz, and the
cost shows in the widget's own frame rate (QuickJS and the validator in the worker, the batches on the page). That is
the expected picture for a fast machine; the numbers that set the limits come from the clients' weak devices.

## Writing a load

A load is an entry of `LOADS` in `scripts/stress/loads.ts`: its CSS, a setup script and a per-frame `step(t)` (`t`: ms
since the first frame), with `N` = the level's value, `F` = the field, `W` = its width and `mk(n, class)` = n divs in
the field. Keep each load to one kind of work, keep everything inside widget profile 1, and choose levels that cross
the strict limit the load is about on both sides. `heavy`, `backdrop`, `changed` and `est` describe a level for the
manifest; `fit` marks the moving loads the report fits a cost per changed box on (`heavy` for the cost model's `A`,
`plain` for `B`).

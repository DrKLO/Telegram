# Web feed report (2026-10-12)

corpus/common, 60 bubbles, 5 passes per cell, `step` scroll (90 % of a screen, pause 900 ms, down and back), pool ×6 prewarmed before the feed opens, lanes 4, prefetch 0.5 screens, keep 2 screens, hostile bubble: none. Scroller 680×822 css px, dpr 1, 10 cores, light theme. Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/153.0.8010.12 Safari/537.36. Total 2822 s.

Cells are median / p90. Per-bubble rows (warm time to content, blank, renderer ready, host constructor): over the bubbles of a pass, then the median across passes. Per-pass rows: across passes.

## Renderers, pool on

| | iframe | shadow | litehtml | blitz |
|---|---:|---:|---:|---:|
| **starts instantly** — the pool: a bubble shows content as it scrolls in, not after its QuickJS boots | | | | |
| time to first content, cold (first bubble of a pass) | 45 / 58 ms | 35 / 37 ms | 43 / 65 ms | 29 / 80 ms |
| time to first content, warm | 25 / 36 ms | 23 / 31 ms | 27 / 38 ms | 22 / 31 ms |
| blank while visible | 0 / 0 ms | 0 / 0 ms | 0 / 0 ms | 0 / 0 ms |
| bubbles blank > 100 ms | 1 / 1 | 0 / 1 | 1 / 1 | 1 / 1 |
| renderer create → first report | 7.9 / 15 ms | 0.6 / 1.4 ms | 1.8 / 3.6 ms | 1.2 / 2.6 ms |
| host constructor (main thread) | 1.3 / 2.4 ms | 0.8 / 1.7 ms | 0.5 / 1.0 ms | 0.5 / 0.8 ms |
| **cheap at scale** — the renderer: what one more live bubble costs the page | | | | |
| memory per live bubble, browser processes | 11.0 / 13.1 MB | 8.7 / 11.5 MB | 15.1 / 15.4 MB | 13.5 / 15.0 MB |
| memory per live bubble, page JS heap | 0.0 / 0.0 MB | 0.0 / 0.0 MB | 0.0 / 0.0 MB | 0.0 / 0.0 MB |
| QuickJS heap per live bubble | 1.6 / 1.6 MB | 1.6 / 1.6 MB | 1.6 / 1.6 MB | 1.6 / 1.6 MB |
| live bubbles at the memory reading | 60 / 60 | 60 / 60 | 60 / 60 | 60 / 60 |
| **feels native under the finger** — the main thread during the scripted scroll | | | | |
| long tasks, total | 0 / 0 ms | 0 / 0 ms | 0 / 0 ms | 0 / 0 ms |
| long-task time per second of scroll | 0.0 / 0.0 ms/s | 0.0 / 0.0 ms/s | 0.0 / 0.0 ms/s | 0.0 / 0.0 ms/s |
| longest task | 0 / 0 ms | 0 / 0 ms | 0 / 0 ms | 0 / 0 ms |
| longest frame | 17 / 33 ms | 17 / 33 ms | 17 / 50 ms | 17 / 33 ms |
| dropped frames (estimate) | 0 / 1 | 0 / 2 | 0 / 2 | 0 / 1 |

- memory per live bubble, browser processes: the ramp's delta in the RSS of every Chromium process (renderer, the isolated frames, GPU, utility; read by the driver) ÷ the bubbles that went live in it; the QuickJS workers and the renderer are both in it
- page JS heap: performance.memory after a forced gc(); the QuickJS workers' wasm memory and an isolated iframe's heap are not in it
- measureUserAgentSpecificMemory: SecurityError: Failed to execute 'measureUserAgentSpecificMemory' on 'Performance': performance.measureUserAgentSpecificMemory is not available.

## Pool off vs on

| | iframe, pool off | iframe, pool on | blitz, pool off | blitz, pool on |
|---|---:|---:|---:|---:|
| **starts instantly** — the pool: a bubble shows content as it scrolls in, not after its QuickJS boots | | | | |
| time to first content, cold (first bubble of a pass) | 206 / 211 ms | 45 / 58 ms | 190 / 191 ms | 29 / 80 ms |
| time to first content, warm | 105 / 119 ms | 25 / 36 ms | 100 / 116 ms | 22 / 31 ms |
| blank while visible | 0 / 28 ms | 0 / 0 ms | 0 / 28 ms | 0 / 0 ms |
| bubbles blank > 100 ms | 7 / 7 | 1 / 1 | 7 / 8 | 1 / 1 |
| renderer create → first report | 7.4 / 16 ms | 7.9 / 15 ms | 1.3 / 2.2 ms | 1.2 / 2.6 ms |
| host constructor (main thread) | 1.3 / 2.5 ms | 1.3 / 2.4 ms | 0.5 / 1.0 ms | 0.5 / 0.8 ms |
| **cheap at scale** — the renderer: what one more live bubble costs the page | | | | |
| memory per live bubble, browser processes | 11.2 / 12.9 MB | 11.0 / 13.1 MB | 14.1 / 16.5 MB | 13.5 / 15.0 MB |
| memory per live bubble, page JS heap | 0.0 / 0.0 MB | 0.0 / 0.0 MB | 0.0 / 0.0 MB | 0.0 / 0.0 MB |
| QuickJS heap per live bubble | 0.6 / 0.6 MB | 1.6 / 1.6 MB | 0.5 / 0.5 MB | 1.6 / 1.6 MB |
| live bubbles at the memory reading | 60 / 60 | 60 / 60 | 60 / 60 | 60 / 60 |
| **feels native under the finger** — the main thread during the scripted scroll | | | | |
| long tasks, total | 0 / 133 ms | 0 / 0 ms | 0 / 0 ms | 0 / 0 ms |
| long-task time per second of scroll | 0.0 / 1.6 ms/s | 0.0 / 0.0 ms/s | 0.0 / 0.0 ms/s | 0.0 / 0.0 ms/s |
| longest task | 0 / 133 ms | 0 / 0 ms | 0 / 0 ms | 0 / 0 ms |
| longest frame | 17 / 367 ms | 17 / 33 ms | 17 / 17 ms | 17 / 33 ms |
| dropped frames (estimate) | 0 / 28 | 0 / 1 | 0 / 0 | 0 / 1 |

- memory per live bubble, browser processes: the ramp's delta in the RSS of every Chromium process (renderer, the isolated frames, GPU, utility; read by the driver) ÷ the bubbles that went live in it; the QuickJS workers and the renderer are both in it
- page JS heap: performance.memory after a forced gc(); the QuickJS workers' wasm memory and an isolated iframe's heap are not in it
- measureUserAgentSpecificMemory: SecurityError: Failed to execute 'measureUserAgentSpecificMemory' on 'Performance': performance.measureUserAgentSpecificMemory is not available.

Not measured: memory: performance.memory is the main thread's JS heap only (no workers, no isolated frame); measureUserAgentSpecificMemory is refused by this Chromium (the note under the table says so when it is); the process row is resident memory (shared pages counted once per process) over a ramp of ~1 minute: the browser's own drift over that minute is in it, the pool's warm workers are not; the pool's prewarm itself (N worker boots before the feed opens) is outside every clock; cold cache: wasm modules and the dev server's files are fetched once per page load, so only the first pass of a canvas renderer pays the fetch + compile; a background tab: Chromium throttles timers and frames there; run with the tab in front; CPU frequency, thermal state, other tabs and processes; the dev build (Vite, unminified); the iOS report's hitch rate: long tasks start at 50 ms, so shorter main-thread stalls show only in the frame rows.

# Running the stress kit in your client

For client teams (iOS, Android, macOS, desktop, web). The widget limits are strict from the start (`docs/decisions.md`,
"Strict limits from the start"). In force since v0.6.0, set from your runs of this kit's version 1 (2026-11): 2000 nodes,
10 000 ops per batch, 400 CSS rules, 4000 canvas commands per canvas in one task, 128 layout queries per task, 48 K of
document text, 2048² pixels of canvases together and 1024² per data: image, at most 16 boxes with a blurred shadow, a
filter, a backdrop filter or transparency (2 of them with a backdrop filter), and the main-thread window (layout queries
plus 25 µs per op and 2 µs per canvas command, at most 100 ms per second). Still open: a renderer's own frame cost charged
to the widget. The numbers have to hold on **your renderer on a weak phone**, and only you can run your renderer. This kit measures what each kind of load costs on it, level by level, on both sides of every
limit. You write a small runner in a debug build of your client, run it on the devices below, and send the run files
back. The maintainers turn them into the numbers in the framework: the renderer cost model, and the limits themselves.
A limit only goes down from here, never up (relaxing later is possible, tightening breaks widgets that already work).

You need: the framework tag, your own host and renderer as in production, and Node ≥ 18 for the report tool, which has
no dependencies. The same seam as the conformance set: if you have its runner, most of this one is already written.

## What comes with the framework tag

`packages/stress/` in the widget-sandbox checkout (SwiftPM, the Gradle `includeBuild` and the tdesktop submodule all have it):

| | |
|---|---|
| `widgets/<load>/<level>.html` | The stress widgets: 19 loads, one widget per level of each (127 in all). Each is an ordinary mini-widget (under 4 KB compressed, at most 560 px tall at 320 px wide), inside widget profile 1. Load them exactly as you load a widget from a message. |
| `manifest.json` | The run order and what each widget is: its load, the level's value, the heavy boxes on screen, the boxes that change every frame, rough counts of what the strict limits measure and which limits the level crosses, and the nodes and largest batch the reference build measured. Also the default warm-up and measuring window. |
| `run.schema.json` | The run file your runner writes. |
| `tools/report.mjs` | Turns run files into `summary.md` and `summary.json`. |
| `README.md` | The loads and their levels, for the maintainers. |

## The loads

Each load is one kind of work, in a ladder of levels; every other part of the widget is the same. Paint effects
(`shadow-static`, `shadow-moving`, the ARS cell, `opacity-moving`, `filter-moving`, `backdrop-count`, `backdrop-area`,
`gradient-moving`, `clip-moving`), document size (`dom-static`, `dom-tick`), change per frame (`style-churn`), text
(`text-volume`, `complex-text`, `emoji`), `canvas-cmds`, `svg-path`, `layout-queries` and `css-rules`. The moving loads
change every box every frame, so nothing can be reused from the previous frame; `dom-tick` changes 8 labels in a large
static document, which is cheap for a browser engine and expensive for a renderer that rebuilds the whole document.
`README.md` lists every level.

## What a widget tells you

Each widget runs its own `requestAnimationFrame` loop (what the sandbox sees of your frames) and publishes it as JSON
in the `data-stress` attribute of its `<html>` element, at start and then every 500 ms:

```json
{"v":1,"id":"shadow-moving/16","level":3,"value":16,"status":"running","t":2500,"frames":151,"fps":60,"maxGap":18,"jank":0}
```

- `status`: `setup` (published by the script before the first frame), then `running`. The widget never finishes: your
  runner owns the timing.
- `t`: ms since its first frame. `frames`: its frames so far. `fps`: its frames in the last whole second.
- `maxGap`: the longest gap between two of its frames since the previous publish, ms. `jank`: its gaps over 50 ms so far.

Your host sees this in the **validated ops**, exactly like the conformance set's `data-conformance`: the `el` op
carrying `"adopt": "html"` gives the html element's id, every later `{"op":"attr","id":<that id>,"name":"data-stress",…}`
replaces the value. Keep the last value. See "Reading the ops" in `packages/conformance/CLIENTS.md` for the wrapper
on each platform (TS, Swift, Kotlin, C++); count the ops of each `ops` message on the way, you need that too.

## Devices

Run on at least one device of each class. Each class gets its own run files.

| class | what | why |
|---|---|---|
| `weak-android` | a Galaxy A55 or a phone of that class or slower (mid-range SoC, 60–120 Hz) | the device the limits are chosen for |
| `iphone` | a recent iPhone | WKWebView on the platform with the most widgets |
| `desktop` | Telegram Desktop on a mid-range laptop; the macOS client on any Mac | the desktop renderers |

An older iPhone or a second Android model is welcome too.

Set the device up the same way every time:
- an optimised build with your debug screen (Release on iOS, a release-like build type on Android, an optimised
  desktop build), no debugger or profiler attached;
- charged or on the charger, low power mode off, brightness fixed, other apps closed;
- the device cool at the start; record the thermal state at the end of each level (`ProcessInfo.thermalState`,
  `PowerManager.getCurrentThermalStatus`). The runner pauses between loads to let it cool (see below).

## Host configuration

Two passes. Run the **measure** pass on every device; the **production** pass is short and wanted too.

**Measure pass.** The quotas and caps out of the way, so that every level runs and shows its cost, past the strict
limits too:

| option | value | why |
|---|---|---|
| `policy` | `compat` | the caps from before the strict profile and no stops: the levels past a strict limit keep running |
| `cpuLimitMs` | 500 (the default) | one task's slice; no stress widget comes near it |
| `cpuQuotaMs` / `cpuQuotaWindowMs` | 10 000 / 1000 | far above the window: the JS quota never defers a task (up to v0.5.0 a quota equal to the window also cut a busy widget's slice to 50 ms and stopped it; since then every task gets the whole `cpuLimitMs`) |
| `mainThreadQuotaMs` | 10 000 | the main-thread window (layout queries + 25 µs per validated op + 2 µs per canvas command; default 100 per second), out of the way the same way. It arrives with the strict limits; on a tag without it, leave it out and record `null` |
| `maxTimers` | 256 | |
| `memoryLimitBytes` | 64 MB | the strict default is 8 MB |
| `features` | `["webAnimations"]` | as the profile sets them |
| theme | light, your default palette | |
| width | 390 points / dp (record what you use) | the reference width |

**Production pass.** Your product configuration: `strict`, every limit at its default. It records where each ladder
stops; a widget that stops earlier on your client than the manifest's `crosses` says is a finding.

Both passes: the widget in your real bubble in a chat list (or a stand-in view with the same layout and clipping),
visible, one widget alive at a time (destroy the previous host first), the list idle (no scrolling), no other
animation on screen.

## The runner, step by step

Build it as a debug screen in your client, next to the conformance runner. For every entry of `manifest.widgets`, in
order:

1. **Create the host** with the configuration of the pass and load the widget's HTML. Note the time.
2. **Wait for `loaded`** (or `dead`), at most 60 s. `bootMs` is the time from creating the host to `loaded`.
3. **Warm up** for `manifest.run.warmupMs` (2 s).
4. **Start the window.** Take a snapshot: the host's stats, the count of validated ops so far, the last `data-stress`.
   Start recording your frames (below) and the renderer's own work.
5. **Measure** for `manifest.run.measureMs` (10 s). Every 500 ms read `data-stress` and keep the largest `maxGap`;
   note when the host reports `dead`, if it does.
6. **End the window.** Take the same snapshot, stop recording, write the run entry (next section).
7. **Destroy the host.** Wait 1 s; after the last level of a load, wait 20 s so the device cools down.

The whole kit takes about 30 minutes per device and pass. `manifest.run` gives the defaults; a shorter smoke run is
fine while you build the runner, but send runs with the defaults.

**Your frames.** The intervals between consecutive frames of your client during the window, from the platform:

- **iOS:** `CADisplayLink` on the main run loop (`targetTimestamp`/`timestamp` of consecutive callbacks);
  `UIScreen.maximumFramesPerSecond` is the refresh rate. WKWebView draws in its own process, so its slow frames show
  in the widget's own `fps` rather than on your main thread: record both.
- **macOS:** `NSView.displayLink(target:selector:)` (macOS 14) or `CVDisplayLink`.
- **Android:** `Choreographer.FrameCallback` (`frameTimeNanos` of consecutive frames); `Display.getRefreshRate()`.
  Optionally `Window.addOnFrameMetricsAvailableListener` for `mainThreadMsPerSec` (sum of the frames' durations).
- **Desktop:** your frame clock (the frame-swapped signal of the window that shows the widget).
- **Web:** `requestAnimationFrame` in the document that hosts the renderer.

From the intervals: `p50`, `p95`, `max`, and `dropped` = Σ(round(interval / frame period) − 1) / Σ round(interval /
frame period), with the frame period = 1000 / refresh rate. `longTaskMs` and `mainThreadMsPerSec` where the platform
gives them, else `null`.

**The renderer's own work.** An own renderer (TwidgetCore, litehtml, Blitz, your native engine) times its work on this
widget for every frame it works on: applying the batch, style, layout, building the display list and replaying it
(rasterising, where that happens on the CPU). `msPerFrame` is the mean over those frames, `p95Ms` the 95th percentile,
and `displayListOps` the size of the last frame's display list if you count it. A WebView renderer cannot time its
engine from outside: write `renderer: null`.

**The host.** From the stats your host already reports (`SandboxStats`: web `onStats`, Swift and Kotlin
`WidgetDiagnostics.stats`, C++ `onStats`), as deltas over the window divided by its length in seconds: `jsMsPerSec`
(`jsMs`), `queriesPerSec` (`queries`), `queryMsPerSec` (`queryMs`); `throttledTasks` as a delta; `memoryBytes` and the
node count (`WidgetDiagnostics.nodes`, the web's `stats.nodes`) at the end. `opsPerSec` is the validated ops your
renderer received during the window, per second, and `maxBatchOps` the largest `ops` message since the host was
created.

**The widget.** `fps` = (`frames` at the end − at the start) × 1000 / (`t` at the end − at the start); `frames` the
same difference; `maxGap` the largest you saw during the window; `jank` the difference; `last` the last value verbatim.

## The run file

One file per renderer, device and pass, following `run.schema.json`. Use `null` for anything you cannot measure,
never a guess:

```json
{
  "kit": { "version": 1, "commit": "v0.3.0" },
  "platform": "android", "client": "Telegram for Android", "clientBuild": "12.2.0 (6011)", "framework": "v0.3.0",
  "renderer": "twidget-core", "rendererKind": "own", "engine": "TwidgetCore 0.4",
  "device": { "model": "SM-A556B", "os": "Android 16", "class": "weak-android", "refreshHz": 120, "dpr": 2.625 },
  "pass": "measure",
  "config": {
    "requested": { "policy": "compat", "cpuQuotaMs": 10000, "cpuQuotaWindowMs": 1000, "mainThreadQuotaMs": 10000, "maxTimers": 256, "memoryLimitBytes": 67108864, "width": 390, "theme": "light", "warmupMs": 2000, "measureMs": 10000 },
    "applied": { "policy": "compat", "cpuQuotaMs": 10000, "cpuQuotaWindowMs": 1000, "mainThreadQuotaMs": 10000, "maxTimers": 256, "memoryLimitBytes": 67108864 }
  },
  "date": "2026-11-03T10:00:00Z",
  "runs": [{
    "id": "shadow-moving/16", "load": "shadow-moving", "value": 16,
    "loaded": true, "bootMs": 84, "dead": null, "diedAtMs": null,
    "frames": { "count": 1196, "p50": 8.3, "p95": 9.1, "max": 25.0, "dropped": 0.01, "longTaskMs": null, "mainThreadMsPerSec": null },
    "renderer": { "msPerFrame": 2.4, "p95Ms": 3.1, "displayListOps": 410 },
    "host": { "jsMsPerSec": 41, "opsPerSec": 3900, "queriesPerSec": 0, "queryMsPerSec": 0, "nodes": 35, "memoryBytes": 2900000, "throttledTasks": 0, "maxBatchOps": 34 },
    "widget": { "fps": 118, "frames": 1180, "maxGap": 25, "jank": 0, "last": { "v": 1, "id": "shadow-moving/16", "status": "running", "frames": 1420, "fps": 119 } },
    "thermal": "nominal", "errors": [], "diags": {}
  }]
}
```

## The report

```bash
node packages/stress/tools/report.mjs --out stress-results path/to/*.json
```

It writes `summary.md` and `summary.json`: per load a table of its levels against every run (your frame time p95, the
dropped share, the widget's own frame rate, the renderer's own time), then per run the cost of one changed box on the
moving loads and the highest level each load holds at the full frame rate, and for a production pass where each ladder
stops. Pass the run files of all your devices at once to get them side by side.

Send the run files and `summary.md` to the widget-sandbox maintainers, or open a PR adding them to
`packages/stress/results/<client>/`.

## What the numbers are for

- The renderer's cost in the CPU quota: an own renderer reports its time per frame, a WebView renderer is charged by a
  model, `A` µs per changed heavy box and `B` µs per other changed box. The heavy moving loads give `A`, `style-churn`,
  `gradient-moving` and `clip-moving` give `B`, on your weakest device.
- The limits: 2000 nodes, 10 000 ops per batch, 400 CSS rules, 4000 canvas commands, 128 layout queries per task, 16
  heavy boxes and 2 with a backdrop filter, and the main-thread window. If your weak device cannot hold the full frame
  rate at a level that is within a limit, tell us: the numbers were checked on strong and middle devices only.
- The text, canvas, SVG and query loads tell whether those limits are about right on your renderer, or need a cost of
  their own.

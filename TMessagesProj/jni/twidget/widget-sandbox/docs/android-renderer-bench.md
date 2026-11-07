# Android: WebView vs Blitz and litehtml in a bubble — start-up and memory

Date: 2026-10-10. The question: how much heavier is WebView than a native
renderer when a widget lives in every chat bubble? Only the renderer is
measured: the sandbox (QuickJS) and the validator are the same, and `none` has
no renderer at all (`NullRenderer` in `apps/android/demo/.../Bench.kt` answers
`ready` and one fixed size), so the cost of the renderer reads as a difference.

## Method

`--es autorun bench` in the Android demo (`Bench.kt`) creates 20 hosts one
after another, like a chat scrolled to the last message: the next one after
the previous one's `loaded` plus 200 ms, the list scrolled to the new one. All
20 live at the same time, attached to the window (Column, not LazyColumn: the
worst case without snapshot-and-drop), are held for 8 seconds, then destroyed.
The corpus is the same as in the iOS feed: `compat`, then the beginning of
`generated/with-profile`.

Per widget: the `WidgetHost` constructor time on the main thread (for the
first WebView in the process this is loading the Chromium provider), the
`rendererReadyMs` / `firstContentMs` / `loadedMs` clocks (the same definitions
as on iOS), late time and the worst main-thread frame during start-up
(Choreographer, re-posted every vsync). Per phase (before the first, after the
first, 20 live, after destroy): process PSS via `Debug.getMemoryInfo`. The
`scripts/android-bench.sh` script captures `dumpsys meminfo` during the hold:
the WebView renderer lives in `com.google.android.webview:sandboxed_process0`
under an isolated uid, one per application, and the application does not see it.

Machine: the `twidget-35` emulator (API 35, arm64 on Apple Silicon, `-gpu host`,
started with `-memory 4096`; the stock 2 GB went into swap), WebView 124, debug
build of the demo (native code -O2, Blitz and litehtml — release). Two rounds,
the numbers are from the second; the first matches, except for the outliers of
the first launch after installation (the `.so` pages not yet in the cache: first
Blitz 126 ms instead of 53, first litehtml 237 instead of 60) and one Blitz run
when the emulator was busy with a neighbouring session.

```bash
cd apps/android && ./gradlew :demo:installDebug
scripts/android-bench.sh webview 20            # cold process
scripts/android-bench.sh webview 20 prewarm    # a spare WebView created and loaded at start-up, as a messenger would do
scripts/android-bench.sh blitz 20 ; scripts/android-bench.sh litehtml 20 ; scripts/android-bench.sh none 20
python3 -I scripts/android-bench-print.py --table webview-20 webview-prewarm-20 none-20 blitz-20 litehtml-20   # or the name of a single report
```

## Start-up

Widgets 2–20, median / p90, ms from host creation:

| | constructor (main thread) | `ready` | first content | `loaded` |
|---|---|---|---|---|
| WebView | 23 / 28 | 71 / 87 | 121 / 153 | 121 / 216 |
| WebView, prewarmed | 27 / 35 | 76 / 94 | 133 / 158 | 116 / 242 |
| Blitz | 13 / 17 | 30 / 40 | 82 / 89 | 80 / 120 |
| litehtml | 19 / 24 | 37 / 42 | 107 / 128 | 92 / 188 |
| none (engine floor) | 14 / 17 | 30 / 47 | — | 76 / 105 |

First widget of the process, constructor / content: WebView cold **274 / 474**
(after installation 426 / 675), WebView prewarmed 38 / 189 plus **229 ms** of
the spare WebView's constructor at application start, Blitz 53 / 125,
litehtml 60 / 142, none 59 / 115.

Main thread over the creation of 20 widgets (sum of late time / worst frame /
freezes > 100 ms): WebView 700 ms / 83 / 0, prewarmed 733 / 83 / 0, Blitz
183 / 33 / 0, litehtml 667 / 50 / 0, none 200 / 50 / 0.

## Memory

PSS in MiB with 20 live widgets (dumpsys KB / 1024); the process before the first widget — 66 MiB.

| | application | renderer process | total | after destroy |
|---|---|---|---|---|
| none | 194 | — | 194 | 78 |
| WebView | 262 | 78 | 340 | 153 (Chromium stays in the process) |
| Blitz | 270 | — | 270 | 96 |
| litehtml | 216 | — | 216 | 90 |

Breakdown per widget:

- **Engine without a renderer: 6.3 MB** per widget ((194 − 74) / 19). The
  widget's own QuickJS heap — 1.7 MB (median `stats.memoryBytes`); the rest is
  the runtime, the validator context (css-tree) and two threads. This is the
  largest item for any renderer; iOS cuts it with the validator pool
  (`docs/chat-feed-ios.md`), Android — not yet.
- **WebView: ≈ 2.9 MB per widget plus ≈ 90 MB once.** In the application
  (262 − 194) = 68 MB, of which 41 is the one-off Chromium initialisation (the
  `prewarmed` phase: 107 vs 66), the remainder 1.4 MB per widget; in the
  renderer process 1.5 MB per widget ((78 − 55) / 15 from the run with 5) on a
  base of ≈ 50 MB.
- **Blitz: 3.8 MB per widget** ((270 − 194) / 20), of which ≈ 2.3 MB is the
  raster Bitmap (945 px × average height 236 dp × 2.625 × 4 bytes), the
  document itself ≈ 1.5 MB; code 12 MB once. Before this measurement the
  prototype also held a direct buffer of the same size per widget (317 MB at
  20); now one scratch buffer per process.
- **litehtml: 1.1 MB per widget**; draws the display list straight into the
  Canvas, holds no raster; code 6 MB.

What is missing here: `graphics` = 0 in every phase — on the emulator with the
host GPU the memory of WebView tiles and HWUI textures (where Blitz's Bitmaps
go too) is not counted. On a device both columns will grow by roughly the
bubble area in pixels, for WebView — in the application (the `VizWebView`
thread).

## Phone: Nothing A142, Android 16

The same APK and the same series on the phone (Android 16, WebView 154, 8 cores,
11.7 GB, 420 dpi, `ANDROID_SERIAL=… TAG=phone scripts/android-bench.sh …`). The
main difference from the emulator: here `graphics` is counted — WebView tiles
and HWUI textures — and the demo's base process is already 157 MiB, of which
78 is graphics.

Start-up, widgets 2–20, median / p90, ms:

| | constructor | `ready` | first content | `loaded` |
|---|---|---|---|---|
| WebView | 27 / 32 | 74 / 80 | 129 / 134 | 122 / 182 |
| WebView, prewarmed | 27 / 33 | 78 / 81 | 130 / 141 | 124 / 214 |
| Blitz | 20 / 22 | 40 / 47 | 104 / 107 | 92 / 181 |
| litehtml | 32 / 35 | 52 / 57 | 158 / 164 | 99 / 346 |
| none | 19 / 21 | 39 / 44 | — | 88 / 106 |

First widget of the process, constructor / content: WebView cold **168 / 354**,
prewarmed 42 / 163 with **114 ms** of the spare WebView's constructor at
start-up, Blitz 35 / 102, litehtml 43 / 114, none 28 / 94. Main-thread late
time over 20 widgets / worst frame: WebView 759 / 78, Blitz 345 / 33, litehtml
1468 / 84 (layout on the CPU at 2.625×, see the per-widget windows in the report), none 314 / 34.

Memory, PSS in MiB with 20 live (`live-held`, after GC):

| | application | of which `graphics` | renderer process | total | after destroy |
|---|---|---|---|---|---|
| none | 286 | 80 | — | 286 | 281 * |
| WebView | 593 | 295 | 89 | 682 | 377 (graphics 202 not released) |
| Blitz | 425 | 141 | — | 425 | 187 |
| litehtml | 312 | 83 | — | 312 | 303 * |

\* in the none and litehtml runs the native heap (137 / 152 MiB) was not
released within 2.5 s after destroy, in the WebView and Blitz runs it was; the
cause was not found (the order in which the engines are stopped?), a separate item.

Per bubble on top of `none`:

- **WebView: ≈ 14 MiB plus ≈ 120 MiB once.** In the application 307 MiB, of
  which 71 is the one-off Chromium initialisation (first widget 237 vs 166 for
  none), the remainder ≈ 12 MiB per widget, and almost all of it is `graphics`
  (≈ 10 MiB of tiles per 945 × ~600 px bubble); plus the renderer process
  89 MiB (≈ 50 base, ≈ 2 per widget). After destroying 20 WebViews the
  application holds 377 MiB vs 281.
- **Blitz: ≈ 7 MiB**: the Bitmap ≈ 2.3 in the native heap, its texture ≈ 3–4
  in `graphics`, the document < 1. After destroy everything is released.
- **litehtml: ≈ 1.3 MiB**, display list into the Canvas, no raster.

The conclusions of the "What this means" section are reinforced on the phone,
not changed: WebView's one-off price is 168 ms and 120 MiB, per bubble it is
twice as expensive as Blitz and ten times as expensive as litehtml, and the
difference is graphics memory, which the emulator does not show.

## Validator pool

Half of the iOS pools is ported: `ValidatorPool` (`packages/android/widget-sandbox/.../Pools.kt`),
`WidgetHost.Options.pools` / `sharedValidatorThread`, `TrustedValidator.background` /
`reset` / `sharedThread`; the test `TrustedValidatorTest.poolRecyclesAValidatorWithAFreshInstance`.
Validators are built ahead of time on one shared thread, the host takes a ready
one and hands it back with a fresh instance on destroy. Bench: `POOLS=1`
(4 validators at start-up, the pool refills itself between widgets; 20/20 hits).

Phone, widgets 2–20, median / p90, ms, without the pool → with the pool:

| | constructor | `ready` | first content | `loaded` | late over 20 |
|---|---|---|---|---|---|
| none | 19 → **2** | 39 → **5** | — | 88 → 63 | 314 → 155 |
| Blitz | 20 → **3** | 40 → **20** | 104 → **77** | 92 → 76 | 345 → 240 |
| WebView | 27 → 19 | 74 → 67 | 129 → 118 | 122 → 117 | 759 → 779 |

First widget of the process: none 28 / 94 → 7 / 75, Blitz 35 / 102 → 14 / 88,
WebView unchanged (its first widget is the Chromium initialisation).

Memory: the pool saves nothing, as was said up front. A live widget needs a
validator of its own; 4 idle validators cost +14 MiB (≈ 3.7 MiB each, which is
what building the css-tree bundle in a context costs). What it does give —
building the validator (~17 ms on the phone, 10–20 on the emulator) leaves the
main thread and the bubble's critical path, and one thread instead of a thread
per bubble. For the native renderers this is noticeable (content a quarter
earlier), for WebView it drowns in its own 50 ms to `ready`.

For the per-bubble memory to drop, the validators need **one context per
process with an instance per widget**: the css-tree bundle (≈ 3 MiB) is then
shared by all, and an instance keeps only the node model. That is a change to
`validator-bundle.ts` (`create` → instances by id) with a rebuild of the bundles
and `.qbc` on every platform, so it is not done here.

## What this means

1. **WebView's one-off price is real and large**: 270–430 ms of main-thread
   blocking at the first WebView in the process plus ≈ 90 MB across two
   processes, which do not come back after destroy (153 MB vs 78). Prewarming
   at start-up hides the pause from the chat but pays the same 229 ms and 90 MB
   at launch.
2. **Per bubble, WebView is more expensive than a native renderer in time, not
   in memory**: +40 ms to `ready` and +40 ms to content relative to the engine
   floor, 4 times more main-thread late time. Blitz is indistinguishable from
   `none` on start-up: the renderer adds nothing.
3. **Per-bubble memory is decided by the engine, not the renderer**: 6.3 MB
   of QuickJS + validator against 1–4 MB of renderer. The biggest lever on
   Android is pooling and reusing validators, as on iOS, regardless of the
   renderer choice.
4. Blitz with a Bitmap per widget and WebView are roughly equal in PSS per
   bubble; litehtml is three times lighter because it holds no raster. Blitz
   can draw the same way (display list or hardware Bitmap) — that is glue,
   not the engine.
5. 20 live WebViews on the emulator did not crash and did not stutter after
   creation; this is not an argument for "a WebView in every bubble is
   impossible", but an argument about "the start-up pause, the one-off 90 MB
   and a janky main thread while bubbles appear".
   On the phone graphics memory is added to that: ≈ 10 MiB of tiles per
   bubble, which the emulator does not show (the "Phone" section).

## Unverified

- A weak phone. The Nothing A142 is mid-market with 8 cores; the "second" on
  the first WebView is known from budget devices, here it is 168 ms.
- The native heap after destroy in the none and litehtml runs on the phone (see *).
- Snapshot-and-drop and the pools (iOS `WidgetHost.Options`) on Android: the
  worst case is measured here — all 20 live.
- Blitz and litehtml on a device with 3× rasterisation on the CPU (here 2.625×).
- What happens to the WebView renderer process after the last WebView is
  destroyed (is it alive, how much does it hold).

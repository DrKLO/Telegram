# Compat report

Generated 2026-11-02T12:19:59.971Z by `pnpm test:compat`.

**18 / 18 supported widgets pass (100%).** 2 known-unsupported widgets checked for graceful fallback: 2 ok.

Pass = sandbox loaded without JS errors, the scripted interaction produced the expected result, and the screenshot differs from the native reference by at most the tolerance.

| Widget | Pass | Behaviour (sandbox) | Pixel diff | Tolerance | JS ms | Ops | Sync queries (cache hits) | Diagnostics | Note |
|---|---|---|---|---|---|---|---|---|---|
| tabs.html | ✅ | ok | 0.00% | 2% | 17 | 132 | 0 (0) |  |  |
| fragment.html | ✅ | ok | 0.00% | 2% | 9 | 92 | 0 (0) |  |  |
| calculator.html | ✅ | ok | 0.00% | 2% | 11 | 123 | 0 (0) |  |  |
| table-sort.html | ✅ | ok | 0.00% | 2% | 19 | 259 | 0 (0) |  |  |
| quiz-buttons.html | ✅ | ok | 0.00% | 2% | 17 | 128 | 0 (0) |  |  |
| svg-chart.html | ✅ | ok | 0.00% | 2% | 14 | 125 | 6 (0) |  |  |
| svg-bar-chart.html | ✅ | ok | 0.00% | 2% | 18 | 156 | 1 (0) |  |  |
| svg-line-chart.html | ✅ | ok | 0.00% | 2% | 22 | 202 | 3 (0) |  |  |
| canvas-line-chart.html | ✅ | ok | 0.00% | 2% | 27 | 62 | 15 (0) |  |  |
| canvas-bar-chart.html | ✅ | ok | 0.00% | 2% | 24 | 53 | 16 (2) |  |  |
| css-animation.html | ✅ | ok | 0.00% | 100% | 10 | 51 | 0 (0) |  | spinner frame differs by timing |
| clock.html | ✅ | ok | 0.37% | 100% | 15 | 42 | 0 (0) |  | time of day differs |
| raf-animation.html | ✅ | ok | 2.93% | 100% | 28 | 220 | 3 (3) |  | ball position depends on frame timing |
| drag.html | ✅ | ok | 0.00% | 2% | 8 | 32 | 0 (0) |  |  |
| notes-storage.html | ✅ | ok | 0.08% | 2% | 16 | 115 | 0 (0) |  |  |
| dark-mode.html | ✅ | ok | 0.53% | 6% | 8 | 49 | 5 (2) |  | bridge line differs by design (reference has no TelegramWidget) |
| dark-mode.html (dark theme) | ✅ | ok | 0.55% | 6% | 9 | 49 | 5 (2) |  | host-owned colour scheme; bridge line differs by design |
| stepper.html | ✅ | ok | 0.00% | 2% | 15 | 98 | 0 (0) |  |  |
| three-js.html | ✅ | ok | 0.00% | 100% | 6 | 38 | 0 (0) | script-skipped×1: external script not available: https://cdn.jsdelivr.net/npm/three@0.160.0/build/three.min.js | expected unsupported: Three.js/WebGL → fallback text |
| webgl.html | ✅ | ok | 56.74% | 100% | 6 | 38 | 0 (0) | unsupported×1: WebGL | expected unsupported: WebGL → 2D fallback |

## Failures and top causes

- none

Screenshots and diffs: `reports/compat/<widget>.{sandbox,reference,diff}.png`.

## Notes (hand-written; appended to every generated report)

### What the numbers mean

- The 20 "supported" widgets are the kind an LLM produces for a chat: DOM
  toggling with `onclick`, forms, tables, SVG drawn by hand, D3 (bar + line
  with axes, `getBBox`, `d3.pointer`), Chart.js (line + bar with tooltips,
  loaded from jsDelivr / cdnjs URLs), CSS and rAF animation, pointer drag,
  range/color inputs, `localStorage`, `prefers-color-scheme`, a bare fragment.
  Each one is driven by a scripted interaction and compared pixel-wise with
  the same file rendered natively (reference mode, `connect-src 'none'`).
- Time-dependent widgets (clock, rAF, CSS animation) are checked behaviourally
  and screenshot-compared with a 100 % tolerance; everything else uses 2–6 %.
- The two "known-unsupported" widgets (Three.js, raw WebGL) are expected to
  fall back; they pass if the fallback renders and no error escapes.

### Top failure causes found while building the harness (all fixed generally)

1. **Missing form `submit` events** — the sandboxed renderer frame lacked
   `allow-forms`, so Chrome never dispatched `submit`. Affected every form widget.
2. **`:checked` resolved from attributes** — linkedom's css-select ignores
   runtime checkedness (and its aliases beat user pseudo-classes); quiz scored
   0/3. Fixed with a selector rewrite + state-backed pseudo-classes.
3. **Read-only `window`/`frames`/`top` aliases** — `var frames = 0` silently
   kept the window object; counters became `NaN`.
4. **Scripts from `innerHTML` were executed** — unbounded self re-execution,
   ~100 k elements; browsers keep such scripts inert.
5. **Quirks mode in reference** — doctype-less fragments rendered 1–2 px
   differently natively; the product always renders fragments in standards mode.
6. **Colour scheme** — `color-scheme` on the iframe element does not change
   `prefers-color-scheme` inside a sandboxed frame; the host now owns the theme.
7. **Harness artefacts** — Playwright element screenshots can fire hover on a
   shifted layout (park the mouse), worker restarts after a failure lose
   in-memory results (persist per test), `test-results/` is wiped per run.

### Known remaining gaps (not covered by the corpus)

- `light-dark()` inside `style=""` attributes is resolved once.
- `preventDefault()` for the *first* keypress / checkbox toggle cannot cancel
  the native default (the renderer learns from the second occurrence on).
- `IntersectionObserver` reports everything as visible; Web Animations API,
  Shadow DOM and custom elements are not rendered.
- Sync layout queries cost a worker round trip each (~0.1–0.3 ms here);
  D3 axes issue only a handful, Chart.js ~20 `measureText` calls per render
  thanks to the per-flush cache.

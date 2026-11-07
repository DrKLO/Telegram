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

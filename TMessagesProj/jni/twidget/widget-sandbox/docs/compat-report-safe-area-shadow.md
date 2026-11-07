# Compat report — safe-area on the Shadow DOM renderer (no iframe)

Generated 2026-10-30T01:14:41.550Z by `TW_RENDERER=shadow CORPUS_GROUP=safe-area pnpm test:generated`. Pass = loaded without JS errors and screenshot within 3% of the native reference after clicking up to 4 buttons.

Both sides are drawn as a client without a caption draws them: a 24 px band with the time over a 64 px right corner (the dev app's `?time=corner`; Chromium's env() override for the reference frame).

## 1-diagnostic: 1 / 1 pass (100%)

| Widget | Pass | Pixel diff | Clicks | Errors | Unsupported / blocked | Other diagnostics |
|---|---|---|---|---|---|---|
| 01-what-the-client-passed.html | ✅ | 0.00% | 0 |  |  |  |

## 2-cards: 2 / 2 pass (100%)

| Widget | Pass | Pixel diff | Clicks | Errors | Unsupported / blocked | Other diagnostics |
|---|---|---|---|---|---|---|
| 02-full-bleed-card.html | ✅ | 0.00% | 2 |  |  |  |
| 03-rtl-card.html | ✅ | 0.00% | 1 |  |  |  |

## 3-css: 2 / 2 pass (100%)

| Widget | Pass | Pixel diff | Clicks | Errors | Unsupported / blocked | Other diagnostics |
|---|---|---|---|---|---|---|
| 04-unstyled-note.html | ✅ | 0.00% | 0 |  |  |  |
| 05-model-css.html | ✅ | 0.00% | 1 |  |  | profile×1 |

## 4-script: 1 / 1 pass (100%)

| Widget | Pass | Pixel diff | Clicks | Errors | Unsupported / blocked | Other diagnostics |
|---|---|---|---|---|---|---|
| 06-canvas-chart.html | ✅ | 0.00% | 0 |  |  |  |

## 5-caption: 1 / 1 pass (100%)

| Widget | Pass | Pixel diff | Clicks | Errors | Unsupported / blocked | Other diagnostics |
|---|---|---|---|---|---|---|
| 07-caption-pair.html | ✅ | 0.00% | 0 |  |  |  |

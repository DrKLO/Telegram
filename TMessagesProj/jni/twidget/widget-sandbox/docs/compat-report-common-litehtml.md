# Compat report — common on the litehtml wasm renderer

Generated 2026-10-10T11:47:17.954Z by `TW_RENDERER=litehtml CORPUS_GROUP=common pnpm test:generated`. Pass = loaded without JS errors on the litehtml renderer (no clicks: a canvas). The pixel diff against the native reference is informational — another rasterizer, see docs/blitz-experiment.md and docs/litehtml-renderer.md; judge by reports/common-litehtml/.

## cards: 8 / 8 pass (100%)

| Widget | Pass | Pixel diff | Clicks | Errors | Unsupported / blocked | Other diagnostics |
|---|---|---|---|---|---|---|
| 01-profile-card.html | ✅ | 6.22% | 0 |  |  | litehtml-init×1 |
| 02-product-carousel.html | ✅ | 28.85% | 0 |  |  | litehtml-init×1 |
| 03-article-digest.html | ✅ | 3.37% | 0 |  |  | litehtml-init×1 |
| 04-pricing-tiers.html | ✅ | 3.45% | 0 |  |  | litehtml-init×1 |
| 05-receipt.html | ✅ | 10.44% | 0 |  |  | litehtml-init×1 |
| 06-timetable.html | ✅ | 2.94% | 0 |  |  | litehtml-init×1 |
| 07-notifications.html | ✅ | 10.14% | 0 |  |  | litehtml-init×1 |
| 08-kpi-bars.html | ✅ | 1.15% | 0 |  |  | litehtml-init×1 |

## heavy: 7 / 7 pass (100%)

| Widget | Pass | Pixel diff | Clicks | Errors | Unsupported / blocked | Other diagnostics |
|---|---|---|---|---|---|---|
| 01-long-table.html | ✅ | 7.76% | 0 |  |  | litehtml-init×1 |
| 02-chip-cloud.html | ✅ | 3.49% | 0 |  |  | litehtml-init×1 |
| 03-nested-tree.html | ✅ | 5.09% | 0 |  |  | litehtml-init×1 |
| 04-long-article.html | ✅ | 4.24% | 0 |  |  | litehtml-init×1 |
| 05-swatch-grid.html | ✅ | 1.69% | 0 |  |  | litehtml-init×1 |
| 06-chat-transcript.html | ✅ | 7.76% | 0 |  |  | litehtml-init×1 |
| 07-dense-dashboard.html | ✅ | 5.79% | 0 |  |  | litehtml-init×1 |

## interactive: 9 / 9 pass (100%)

| Widget | Pass | Pixel diff | Clicks | Errors | Unsupported / blocked | Other diagnostics |
|---|---|---|---|---|---|---|
| 01-tabs.html | ✅ | 38.56% | 0 |  |  | litehtml-init×1 |
| 02-accordion.html | ✅ | 11.23% | 0 |  |  | litehtml-init×1 |
| 03-poll.html | ✅ | 8.11% | 0 |  |  | litehtml-init×1 |
| 04-quiz.html | ✅ | 5.10% | 0 |  |  | litehtml-init×1 |
| 05-cart.html | ✅ | 1.32% | 0 |  |  | litehtml-init×1 |
| 06-paged-list.html | ✅ | 6.09% | 0 |  |  | litehtml-init×1 |
| 07-calendar.html | ✅ | 3.45% | 0 |  |  | litehtml-init×1 |
| 08-tic-tac-toe.html | ✅ | 4.03% | 0 |  |  | litehtml-init×1 |
| 09-filter-chips.html | ✅ | 5.97% | 0 |  |  | litehtml-init×1 |

## live: 8 / 8 pass (100%)

| Widget | Pass | Pixel diff | Clicks | Errors | Unsupported / blocked | Other diagnostics |
|---|---|---|---|---|---|---|
| 01-clock-countdown.html | ✅ | 2.23% | 0 |  |  | litehtml-init×1, litehtml-build×1, litehtml-paint×1 |
| 02-progress-bars.html | ✅ | 5.17% | 0 |  |  | litehtml-init×1, litehtml-build×1, litehtml-paint×1 |
| 03-counters.html | ✅ | 7.25% | 0 |  |  | litehtml-init×1, litehtml-build×1, litehtml-paint×1 |
| 04-deploy-log.html | ✅ | 3.28% | 0 |  |  | litehtml-init×1, litehtml-build×1, litehtml-paint×1 |
| 05-ticker.html | ✅ | 3.23% | 0 |  |  | litehtml-init×1, litehtml-build×1, litehtml-paint×1 |
| 06-typewriter.html | ✅ | 2.70% | 0 |  |  | litehtml-init×1, litehtml-build×1, litehtml-paint×1 |
| 07-slideshow.html | ✅ | 2.70% | 0 |  |  | litehtml-init×1, litehtml-build×1, litehtml-paint×1 |
| 08-skeleton.html | ✅ | 32.28% | 0 |  |  | litehtml-init×1, litehtml-build×1, litehtml-paint×1 |

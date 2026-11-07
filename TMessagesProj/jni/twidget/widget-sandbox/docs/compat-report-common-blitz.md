# Compat report — common on the blitz wasm renderer

Generated 2026-10-10T11:49:47.464Z by `TW_RENDERER=blitz CORPUS_GROUP=common pnpm test:generated`. Pass = loaded without JS errors on the blitz renderer (no clicks: a canvas). The pixel diff against the native reference is informational — another rasterizer, see docs/blitz-experiment.md and docs/litehtml-renderer.md; judge by reports/common-blitz/.

## cards: 8 / 8 pass (100%)

| Widget | Pass | Pixel diff | Clicks | Errors | Unsupported / blocked | Other diagnostics |
|---|---|---|---|---|---|---|
| 01-profile-card.html | ✅ | 8.77% | 0 |  |  | blitz-init×1, blitz-fonts×1 |
| 02-product-carousel.html | ✅ | 29.68% | 0 |  |  | blitz-init×1, blitz-fonts×1 |
| 03-article-digest.html | ✅ | 15.64% | 0 |  |  | blitz-init×1, blitz-fonts×1 |
| 04-pricing-tiers.html | ✅ | 6.60% | 0 |  |  | blitz-init×1, blitz-fonts×1 |
| 05-receipt.html | ✅ | 9.58% | 0 |  |  | blitz-init×1, blitz-fonts×1 |
| 06-timetable.html | ✅ | 6.66% | 0 |  |  | blitz-init×1, blitz-fonts×1 |
| 07-notifications.html | ✅ | 9.88% | 0 |  |  | blitz-init×1, blitz-fonts×1 |
| 08-kpi-bars.html | ✅ | 4.41% | 0 |  |  | blitz-init×1, blitz-fonts×1 |

## heavy: 7 / 7 pass (100%)

| Widget | Pass | Pixel diff | Clicks | Errors | Unsupported / blocked | Other diagnostics |
|---|---|---|---|---|---|---|
| 01-long-table.html | ✅ | 10.47% | 0 |  |  | blitz-init×1, blitz-fonts×1 |
| 02-chip-cloud.html | ✅ | 6.66% | 0 |  |  | blitz-init×1, blitz-fonts×1 |
| 03-nested-tree.html | ✅ | 5.33% | 0 |  |  | blitz-init×1, blitz-fonts×1 |
| 04-long-article.html | ✅ | 14.11% | 0 |  |  | blitz-init×1, blitz-fonts×1 |
| 05-swatch-grid.html | ✅ | 2.19% | 0 |  |  | blitz-init×1, blitz-fonts×1 |
| 06-chat-transcript.html | ✅ | 7.96% | 0 |  |  | blitz-init×1, blitz-fonts×1 |
| 07-dense-dashboard.html | ✅ | 7.34% | 0 |  |  | blitz-init×1, blitz-fonts×1 |

## interactive: 9 / 9 pass (100%)

| Widget | Pass | Pixel diff | Clicks | Errors | Unsupported / blocked | Other diagnostics |
|---|---|---|---|---|---|---|
| 01-tabs.html | ✅ | 39.49% | 0 |  |  | blitz-init×1, blitz-fonts×1 |
| 02-accordion.html | ✅ | 11.47% | 0 |  |  | blitz-init×1, blitz-fonts×1 |
| 03-poll.html | ✅ | 11.01% | 0 |  |  | blitz-init×1, blitz-fonts×1 |
| 04-quiz.html | ✅ | 7.00% | 0 |  |  | blitz-init×1, blitz-fonts×1 |
| 05-cart.html | ✅ | 4.45% | 0 |  |  | blitz-init×1, blitz-fonts×1 |
| 06-paged-list.html | ✅ | 7.86% | 0 |  |  | blitz-init×1, blitz-fonts×1 |
| 07-calendar.html | ✅ | 3.46% | 0 |  |  | blitz-init×1, blitz-fonts×1 |
| 08-tic-tac-toe.html | ✅ | 4.67% | 0 |  |  | blitz-init×1, blitz-fonts×1 |
| 09-filter-chips.html | ✅ | 8.76% | 0 |  |  | blitz-init×1, blitz-fonts×1 |

## live: 8 / 8 pass (100%)

| Widget | Pass | Pixel diff | Clicks | Errors | Unsupported / blocked | Other diagnostics |
|---|---|---|---|---|---|---|
| 01-clock-countdown.html | ✅ | 6.12% | 0 |  |  | blitz-init×1, blitz-fonts×1 |
| 02-progress-bars.html | ✅ | 7.15% | 0 |  |  | blitz-init×1, blitz-fonts×1 |
| 03-counters.html | ✅ | 10.13% | 0 |  |  | blitz-init×1, blitz-fonts×1 |
| 04-deploy-log.html | ✅ | 4.80% | 0 |  |  | blitz-init×1, blitz-fonts×1 |
| 05-ticker.html | ✅ | 6.44% | 0 |  |  | blitz-init×1, blitz-fonts×1 |
| 06-typewriter.html | ✅ | 4.99% | 0 |  |  | blitz-init×1, blitz-fonts×1 |
| 07-slideshow.html | ✅ | 4.01% | 0 |  |  | blitz-init×1, blitz-fonts×1 |
| 08-skeleton.html | ✅ | 31.40% | 0 |  |  | blitz-init×1, blitz-fonts×1 |

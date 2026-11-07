# Compat report — generated widgets on the Blitz wasm renderer

Generated 2026-10-10T09:24:51.331Z by `TW_RENDERER=blitz pnpm test:generated`. Pass = loaded without JS errors on the Blitz renderer (no clicks: a canvas). The pixel diff against the native reference is informational — another rasterizer, see docs/blitz-experiment.md; judge by reports/generated-blitz/.

## with-profile: 20 / 20 pass (100%)

| Widget | Pass | Pixel diff | Clicks | Errors | Unsupported / blocked | Other diagnostics |
|---|---|---|---|---|---|---|
| 01-show-me-a-mortgage-calculator-loan-amount.html | ✅ | 25.18% | 0 |  |  | blitz-init×1, blitz-fonts×1 |
| 02-make-an-interactive-quiz-about-the-solar.html | ✅ | 8.17% | 0 |  |  | blitz-init×1, blitz-fonts×1 |
| 03-visualize-this-weekly-budget-as-a-chart.html | ✅ | 20.33% | 0 |  |  | blitz-init×1, blitz-fonts×1 |
| 04-build-a-tip-calculator-with-a-slider-for.html | ✅ | 18.70% | 0 |  |  | blitz-init×1, blitz-fonts×1 |
| 05-create-a-pomodoro-timer-with-start-pause.html | ✅ | 23.52% | 0 |  |  | blitz-init×1, blitz-fonts×1 |
| 06-show-a-comparison-table-of-three-laptops.html | ✅ | 15.84% | 0 |  |  | blitz-init×1, blitz-fonts×1 |
| 07-make-a-bmi-calculator-with-metric-and.html | ✅ | 13.57% | 0 |  |  | blitz-init×1, blitz-fonts×1 |
| 08-draw-a-line-chart-of-monthly-temperatures.html | ✅ | 22.13% | 0 |  |  | blitz-init×1, blitz-fonts×1 |
| 09-create-a-unit-converter-for-length-weight.html | ✅ | 13.52% | 0 |  |  | blitz-init×1, blitz-fonts×1 |
| 10-show-me-an-interactive-multiplication.html | ✅ | 11.53% | 0 |  |  | blitz-init×1, blitz-fonts×1 |
| 11-make-a-to-do-list-widget-where-i-can-add.html | ✅ | 6.25% | 0 |  |  | blitz-init×1, blitz-fonts×1 |
| 12-create-a-countdown-to-new-year-with-days.html | ✅ | 10.94% | 0 |  |  | blitz-init×1, blitz-fonts×1 |
| 13-visualize-a-simple-project-timeline-gantt.html | ✅ | 11.08% | 0 |  |  | blitz-init×1, blitz-fonts×1 |
| 14-build-a-color-palette-generator-pick-a.html | ✅ | 14.91% | 0 |  |  | blitz-init×1, blitz-fonts×1 |
| 15-create-a-flashcard-widget-for-8-spanish.html | ✅ | 57.24% | 0 |  |  | blitz-init×1, blitz-fonts×1 |
| 16-make-a-bar-chart-of-the-top-6-programming.html | ✅ | 21.42% | 0 |  |  | blitz-init×1, blitz-fonts×1 |
| 17-show-a-tic-tac-toe-game-against-a-simple.html | ✅ | 5.57% | 0 |  |  | blitz-init×1, blitz-fonts×1 |
| 18-create-a-currency-converter-for-usd-eur.html | ✅ | 7.78% | 0 |  |  | blitz-init×1, blitz-fonts×1 |
| 19-make-a-step-by-step-recipe-card-for.html | ✅ | 21.44% | 0 |  |  | blitz-init×1, blitz-fonts×1 |
| 20-build-a-habit-tracker-grid-for-one-month.html | ✅ | 10.41% | 0 |  |  | blitz-init×1, blitz-fonts×1 |

## without-profile: 20 / 20 pass (100%)

| Widget | Pass | Pixel diff | Clicks | Errors | Unsupported / blocked | Other diagnostics |
|---|---|---|---|---|---|---|
| 01-show-me-a-mortgage-calculator-loan-amount.html | ✅ | 9.24% | 0 |  | tag-dropped: <link> | blitz-init×1, blitz-fonts×1, tag-dropped×1 |
| 02-make-an-interactive-quiz-about-the-solar.html | ✅ | 31.01% | 0 |  |  | blitz-init×1, blitz-fonts×1 |
| 03-visualize-this-weekly-budget-as-a-chart.html | ✅ | 32.66% | 0 |  |  | blitz-init×1, blitz-fonts×1 |
| 04-build-a-tip-calculator-with-a-slider-for.html | ✅ | 16.28% | 0 |  |  | blitz-init×1, blitz-fonts×1 |
| 05-create-a-pomodoro-timer-with-start-pause.html | ✅ | 27.06% | 0 |  |  | blitz-init×1, blitz-fonts×1 |
| 06-show-a-comparison-table-of-three-laptops.html | ✅ | 13.07% | 0 |  |  | blitz-init×1, blitz-fonts×1 |
| 07-make-a-bmi-calculator-with-metric-and.html | ✅ | 12.94% | 0 |  |  | blitz-init×1, blitz-fonts×1 |
| 08-draw-a-line-chart-of-monthly-temperatures.html | ✅ | 22.23% | 0 |  |  | blitz-init×1, blitz-fonts×1 |
| 09-create-a-unit-converter-for-length-weight.html | ✅ | 10.24% | 0 |  |  | blitz-init×1, blitz-fonts×1 |
| 10-show-me-an-interactive-multiplication.html | ✅ | 11.47% | 0 |  |  | blitz-init×1, blitz-fonts×1 |
| 11-make-a-to-do-list-widget-where-i-can-add.html | ✅ | 6.35% | 0 |  |  | blitz-init×1, blitz-fonts×1 |
| 12-create-a-countdown-to-new-year-with-days.html | ✅ | 12.60% | 0 |  | tag-dropped: <link>; <link> | blitz-init×1, blitz-fonts×1, tag-dropped×2 |
| 13-visualize-a-simple-project-timeline-gantt.html | ✅ | 9.70% | 0 |  |  | blitz-init×1, blitz-fonts×1 |
| 14-build-a-color-palette-generator-pick-a.html | ✅ | 16.58% | 0 |  |  | blitz-init×1, blitz-fonts×1 |
| 15-create-a-flashcard-widget-for-8-spanish.html | ✅ | 51.48% | 0 |  |  | blitz-init×1, blitz-fonts×1 |
| 16-make-a-bar-chart-of-the-top-6-programming.html | ✅ | 22.17% | 0 |  |  | blitz-init×1, blitz-fonts×1 |
| 17-show-a-tic-tac-toe-game-against-a-simple.html | ✅ | 4.72% | 0 |  |  | blitz-init×1, blitz-fonts×1 |
| 18-create-a-currency-converter-for-usd-eur.html | ✅ | 6.17% | 0 |  | network-blocked: fetch https://api.exchangerate.host/latest?base=USD&symbols=EUR,GBP,JPY | blitz-init×1, blitz-fonts×1, network-blocked×1 |
| 19-make-a-step-by-step-recipe-card-for.html | ✅ | 8.94% | 0 |  | url-blocked: background: url(https://images.unsplash.com/photo-1528207776546-365bb710ee93?w=800) | blitz-init×1, blitz-fonts×1, url-blocked×1 |
| 20-build-a-habit-tracker-grid-for-one-month.html | ✅ | 4.45% | 0 |  |  | blitz-init×1, blitz-fonts×1 |

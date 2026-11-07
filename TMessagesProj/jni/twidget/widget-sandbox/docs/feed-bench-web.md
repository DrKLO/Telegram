# Feed bench — iframe vs Shadow DOM renderer on the web

Generated 2026-10-11T23:55:39.783Z by `corepack pnpm tsx scripts/feed-bench.ts --counts 50,200 --mode ops,host --cv` (headless Chromium 153.0.8010.12, dev server, 900×900). ops = renderer only, replaying the validated ops of five generated widgets (apps/web/bench/ops/, Node harness) round-robin; host = a WidgetHost per bubble (QuickJS worker + validator + renderer), four booting at a time. build = creating the renderers and sending the ops (main thread, synchronous); ready = every bubble applied its ops; painted = the frame after that; long tasks = main-thread tasks over 50 ms until painted; heap / documents / frames / nodes / layout / style / task = Chromium `Performance.getMetrics` deltas for the whole page, iframes included (JS heap of the renderer process, documents and frames created, DOM nodes, time in layout, style recalc and all tasks); scroll = 60 scroll steps through the feed, frame intervals. Bubble heights are clamped to 24–900 px like in WidgetHost. Pool = host mode with a WorkerPool of that many warmed sandbox workers (QuickJS + bundle evaluated), prewarmed before the clock starts and refilled as taken; hits = bubbles that got a warm worker.

| Renderer | Mode | Bubbles | CV | Pool (prewarm ms, hits) | Build ms | Ready ms | Painted ms | ms / bubble | Long tasks | JS heap MB | Documents | Frames | Nodes | Layout ms | Style ms | Task ms | Scroll avg / p95 ms | Slow frames | Feed height |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| iframe | ops | 50 |  |  | 21 | 248 | 262 | 5.0 | 0 (0 ms) | 22.2 | 49 | 49 | 3974 | 55 | 17 | 335 | 16.7 / 16.7 | 0 / 60 | 27596 |
| shadow | ops | 50 |  |  | 65 | 65 | 66 | 1.3 | 0 (0 ms) | 0.7 | 0 | 0 | 4081 | 45 | 15 | 138 | 16.7 / 16.7 | 0 / 60 | 27596 |
| iframe | ops | 50 | yes |  | 20 | 217 | 233 | 4.3 | 0 (0 ms) | 22.1 | 49 | 49 | 3974 | 45 | 19 | 321 | 16.7 / 16.7 | 0 / 60 | 21905 |
| shadow | ops | 50 | yes |  | 54 | 54 | 55 | 1.1 | 0 (0 ms) | 0.7 | 0 | 0 | 4081 | 51 | 16 | 174 | 16.7 / 16.7 | 0 / 60 | 21804 |
| iframe | ops | 200 |  |  | 71 | 861 | 865 | 4.3 | 1 (70 ms) | 99.9 | 238 | 199 | 17014 | 114 | 48 | 1075 | 16.7 / 16.7 | 0 / 60 | 110336 |
| shadow | ops | 200 |  |  | 153 | 153 | 154 | 0.8 | 0 (0 ms) | 1.4 | -1 | -1 | 15714 | 136 | 35 | 329 | 16.7 / 16.7 | 0 / 60 | 110336 |
| iframe | ops | 200 | yes |  | 67 | 734 | 735 | 3.7 | 1 (66 ms) | 100.4 | 261 | 199 | 17114 | 95 | 39 | 965 | 16.7 / 16.7 | 0 / 60 | 76349 |
| shadow | ops | 200 | yes |  | 156 | 156 | 156 | 0.8 | 0 (0 ms) | 2.9 | 0 | 0 | 16321 | 137 | 44 | 366 | 16.7 / 16.7 | 0 / 60 | 81540 |
| iframe | host | 50 |  |  | 0 | 1533 | 1534 | 30.7 | 0 (0 ms) | 27.4 | 63 | 49 | 4106 | 71 | 33 | 616 | 16.7 / 16.7 | 0 / 60 | 27596 |
| shadow | host | 50 |  |  | 0 | 1405 | 1411 | 28.1 | 0 (0 ms) | 4.2 | 0 | 0 | 4081 | 62 | 27 | 288 | 16.7 / 16.7 | 0 / 60 | 27596 |
| iframe | host | 50 |  | 8 (218 ms, 50/50) | 0 | 856 | 858 | 17.1 | 0 (0 ms) | 23.1 | 49 | 49 | 3974 | 66 | 28 | 967 | 16.7 / 16.7 | 0 / 60 | 27596 |
| shadow | host | 50 |  | 8 (227 ms, 50/50) | 0 | 793 | 804 | 15.9 | 0 (0 ms) | 4.0 | 0 | 0 | 4081 | 66 | 27 | 647 | 17.2 / 16.7 | 1 / 60 | 27596 |
| iframe | host | 50 |  | 32 (512 ms, 50/50) | 0 | 766 | 799 | 15.3 | 0 (0 ms) | 23.1 | 49 | 49 | 3974 | 64 | 26 | 1100 | 16.7 / 16.7 | 0 / 60 | 27596 |
| shadow | host | 50 |  | 32 (503 ms, 50/50) | 0 | 623 | 624 | 12.5 | 0 (0 ms) | 3.5 | 0 | 0 | 4081 | 65 | 23 | 853 | 16.9 / 16.7 | 1 / 60 | 27596 |
| iframe | host | 50 | yes |  | 0 | 1467 | 1482 | 29.3 | 0 (0 ms) | 27.5 | 66 | 49 | 4114 | 70 | 37 | 581 | 16.7 / 16.7 | 0 / 60 | 21113 |
| shadow | host | 50 | yes |  | 0 | 1400 | 1411 | 28.0 | 0 (0 ms) | 4.0 | 0 | 0 | 4081 | 65 | 31 | 293 | 16.7 / 16.7 | 0 / 60 | 21804 |
| iframe | host | 50 | yes | 8 (224 ms, 50/50) | 0 | 866 | 871 | 17.3 | 0 (0 ms) | 23.1 | 49 | 49 | 3974 | 67 | 32 | 910 | 16.7 / 16.7 | 0 / 60 | 20592 |
| shadow | host | 50 | yes | 8 (213 ms, 50/50) | 0 | 778 | 787 | 15.6 | 0 (0 ms) | 4.4 | 0 | 0 | 4081 | 70 | 31 | 597 | 16.7 / 16.7 | 0 / 60 | 21144 |
| iframe | host | 50 | yes | 32 (548 ms, 50/50) | 0 | 693 | 725 | 13.9 | 0 (0 ms) | 23.2 | 49 | 49 | 3974 | 68 | 29 | 962 | 16.7 / 16.7 | 0 / 60 | 20305 |
| shadow | host | 50 | yes | 32 (510 ms, 50/50) | 0 | 605 | 607 | 12.1 | 0 (0 ms) | 3.7 | 0 | 0 | 4081 | 73 | 28 | 901 | 17.5 / 16.7 | 1 / 60 | 21804 |
| iframe | host | 200 |  |  | 0 | 4074 | 4083 | 20.4 | 0 (0 ms) | 98.9 | 272 | 199 | 11434 | 162 | 90 | 2076 | 16.7 / 16.7 | 0 / 60 | 72416 |
| shadow | host | 200 |  |  | 0 | 3643 | 3653 | 18.2 | 0 (0 ms) | 3.7 | -1 | -1 | 10794 | 140 | 94 | 942 | 16.7 / 16.7 | 0 / 60 | 72416 |
| iframe | host | 200 |  | 8 (213 ms, 200/200) | 0 | 2854 | 2855 | 14.3 | 0 (0 ms) | 120.0 | 315 | 199 | 11794 | 152 | 83 | 2885 | 16.7 / 16.7 | 0 / 60 | 71540 |
| shadow | host | 200 |  | 8 (217 ms, 200/200) | 0 | 2271 | 2278 | 11.4 | 0 (0 ms) | 3.8 | -1 | -1 | 10762 | 141 | 94 | 1504 | 16.7 / 16.7 | 0 / 60 | 71540 |
| iframe | host | 200 |  | 32 (493 ms, 200/200) | 0 | 2574 | 2587 | 12.9 | 0 (0 ms) | 86.9 | 199 | 199 | 10994 | 146 | 76 | 2949 | 16.7 / 16.7 | 0 / 60 | 72416 |
| shadow | host | 200 |  | 32 (492 ms, 200/200) | 0 | 1963 | 1976 | 9.8 | 0 (0 ms) | 3.9 | -1 | -1 | 10794 | 138 | 80 | 2137 | 16.7 / 16.7 | 0 / 60 | 72416 |
| iframe | host | 200 | yes |  | 0 | 4047 | 4052 | 20.2 | 0 (0 ms) | 93.7 | 242 | 199 | 11220 | 150 | 103 | 2103 | 16.7 / 16.7 | 0 / 60 | 80243 |
| shadow | host | 200 | yes |  | 0 | 3584 | 3595 | 17.9 | 0 (0 ms) | 8.3 | 0 | 0 | 11176 | 147 | 98 | 901 | 16.7 / 16.7 | 0 / 60 | 81376 |
| iframe | host | 200 | yes | 8 (218 ms, 200/200) | 0 | 2909 | 2919 | 14.5 | 0 (0 ms) | 120.1 | 315 | 199 | 11867 | 142 | 88 | 2966 | 16.7 / 16.7 | 0 / 60 | 76466 |
| shadow | host | 200 | yes | 8 (220 ms, 200/200) | 0 | 2324 | 2335 | 11.6 | 0 (0 ms) | 9.8 | 0 | 0 | 11143 | 161 | 96 | 1903 | 16.7 / 16.7 | 0 / 60 | 81121 |
| iframe | host | 200 | yes | 32 (498 ms, 200/200) | 0 | 2508 | 2516 | 12.5 | 0 (0 ms) | 137.3 | 325 | 199 | 11954 | 149 | 81 | 2920 | 16.7 / 16.7 | 0 / 60 | 75986 |
| shadow | host | 200 | yes | 32 (547 ms, 200/200) | 0 | 1984 | 1996 | 9.9 | 0 (0 ms) | 9.8 | 0 | 0 | 11143 | 171 | 93 | 2163 | 18.3 / 33.3 | 3 / 60 | 81121 |

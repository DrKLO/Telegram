# Widget profile conformance set

Each Telegram client draws widgets with its own renderer: Shadow DOM on the web,
WKWebView/WebView or a native engine (litehtml, TwidgetCore) on the others. A
widget looks the same everywhere only if every renderer draws the same profile
and the validator strips everything else the same way. This set checks that.
For any client it answers the question: which profile features does it support?

**Client teams: start with [CLIENTS.md](CLIENTS.md).** It covers how to run the set in your client on your renderer and read the report. This README is for the maintainers of the set.

| | |
|---|---|
| [`catalogue.json`](catalogue.json) / [`catalogue.md`](catalogue.md) | 267 profile features with stable IDs (`css.display.grid`, `canvas.filter`, `tw.profile`, …). Each is marked in / out / candidate, with the decision it comes from (D3a, D3b, D4, D18, D23, the invariants, or `proposed` for the catalogue's own proposals), what the pipeline of the tag does with it (`today`: kept, stripped, replaced, resolved, … with profile 1 enforced and SDK v1; the legend is at the end of `catalogue.md`), and the keys a validator table would match. Profile 1 is frozen in `packages/core/profile.json` (2026-10-17), which is the source: the catalogue takes its statuses, decisions and neutralisations, so profile 1 has no candidates. `profile.json` lists the same 267 IDs, the SDK v1 limits and client behaviours included. The source is `scripts/conformance/catalogue.ts`; `emit-catalogue.ts` overlays `profile.json` when the branch has it. |
| [`auto/`](auto) | **Automatic set**, 60 dense widgets for runners and CI (a topic too large for one mini-widget is split into parts: `03-layout-a`, `-b`, …). Each one asserts layout, computed style, APIs and events itself and publishes the result. Pixel stages are diffed against reference PNGs. `28`–`30` are patterns of real widgets that clients drew wrong while Chromium draws them right (see "Patterns from real widgets"); `31`–`35` must be stopped by the host; `36` is the client's time over a widget with no caption below it. |
| [`visual/`](visual) | **Visual set**, 21 readable widgets for people (the shared Telegram test channel). Each row shows a group of features drawn live, with a one-line caption; the reference picture of the whole widget (`refs/visual/<widget>.png`, `.dark.png` for theme-dependent ones) is posted as a photo next to it. Interactive rows carry a one-line instruction. Numbered in reading order; a topic too tall for one bubble is split into parts (`03-text-a`, `-b`). |
| [`visual/sdk/`](visual/sdk) | **Client SDK group**, 26 widgets for what each client implements of SDK v1, the widget's state and its requests to the bot included. Their reference pictures are `refs/visual/sdk/<widget>.png`, posted next to them; [`visual/sdk/README.md`](visual/sdk/README.md) is the post list. They use the mini harness (`harness/mini.js`). |
| [`channel.json`](channel.json) | The test channel in posting order: per message the widget, its size anchors, the caption and the reference photos. A test bot sends them as layer-233 messages. |
| [`manifest.json`](manifest.json) | Per test: catalogue IDs, kind (self / pixel / interactive), stages with rects and reference files, scripted actions, host checks, themes, and what native Chromium answers to each check. Also coverage per set. |
| [`refs/`](refs) | Reference PNGs for every stage (`refs/<set>/<widget>/<stage>.chromium[.dark].png`, for the automatic diff) and the pictures posted next to the visual and SDK widgets (`refs/visual/<widget>[.dark].png`): native headless Chromium (and WebKit when installed), DPR 2, the reference width 390. |
| [`run.schema.json`](run.schema.json) | The run file every runner writes. |
| [`tools/`](tools) | `report.mjs`, the report (run files → per-renderer JSON + `summary.md`); `diff.mjs`, the pixel rule; `png.mjs`. No dependencies, Node ≥ 18. |
| [`harness/`](harness) | The harness inlined into every widget: `harness.js` (checks, the result attribute; the build bundles only the functions a widget uses), `harness.css` + `auto.css` / `visual.css`, and `mini.js` / `mini.css` for the SDK group. |
| [`results/`](results) | Reports of the renderers run so far, plus [`summary.md`](results/summary.md). |

Coverage: the automatic set covers 253 of the 267 catalogue IDs. The rest are listed under `coverage.external` in the manifest: four limits the attack suite and the server check, and the client behaviours the client SDK group tests (storage, restarts, gestures, sizes, the bot's state edits, forwards, previews, the network marker). The visual set covers the 126 IDs that a person should also see: gradients, shadows, filters, text, canvas, animations, SVG, images, interaction, theme, the client SDK, the renderer's defaults for an unstyled page and opt-in text selection.

## How a widget reports

Every widget is one self-contained HTML file: no external resources, `data:` images only. Every widget is a mini-widget, like a product widget (at most 4 KB compressed and 32 KB uncompressed, `docs/tgw-format.md`): the build keeps each one under 3800 bytes of gzip at levels 6 and 9, since the server's level is unknown, and fails otherwise. A test bot sends every widget as a layer-233 message with its reference picture posted next to it, in the order of `channel.json`.

The harness writes the result as JSON into the `data-conformance` attribute of `<html>`. Any host reads it from its own validated ops: it is the `attr` op on the element that the `el` op adopted as `html`.

Every check names a catalogue ID and gets one of six statuses:

| Status | Meaning |
|---|---|
| `pass` | The check passed. |
| `fail` | The check failed. |
| `pending` | Input never came; counts as a fail. |
| `skip` | Not applicable on this renderer. |
| `xfail` | Expected to fail: the framework does not implement it yet. |
| `xpass` | Expected to fail but passed, so the expectation is out of date. |

Interactive widgets publish `targets`, the rects of what to tap or drag, measured in their own layout. See CLIENTS.md for the full format.

## Maintainers: building and running here

The steps are in this order:

1. Rebuild the set after editing a widget, the harness or the catalogue. This regenerates the catalogue, inlines the harness, renders the references and the posted pictures, records the native answers in the manifest and writes `channel.json`. It fails when a widget is over the size limit or taller than the height limit, or when a picture of the visual set differs from the widget drawn through the sandbox (see "Writing a test"; the automatic set's heights and the pictures are checked on a dev server the build starts for them); `--sizes` checks only the sizes, in a second, without a browser:

   ```bash
   corepack pnpm tsx scripts/conformance/build.ts
   corepack pnpm tsx scripts/conformance/build.ts --sizes
   ```

2. Start the dev server. From a worktree, use a port no other checkout uses:

   ```bash
   corepack pnpm dev --port 5175
   ```

3. Run the web renderers: iframe and Shadow DOM (the frozen Blitz and litehtml wasm prototypes only when named in `--renderers` and built):

   ```bash
   corepack pnpm tsx scripts/conformance/run-web.ts --origin http://127.0.0.1:5175
   ```

   The defaults are the product configuration: `strict`, `webAnimations`, width 390, DPR 2. Options: `--renderers shadow`, `--set auto`, `--only 12-canvas-a`, `--themes light,dark`, `--widths 360,390,430`.

4. Judge the run files into the reports. This is the same tool the clients run:

   ```bash
   node packages/conformance/tools/report.mjs --out packages/conformance/results reports/conformance/{iframe,shadow}.json
   ```

`run-web.ts` is the reference runner. It loads each widget through `window.__tw.load` (the real worker → validator → renderer pipeline) and wraps the renderer's `send` to read the result from the validated ops. It focuses a stand-in chat input first and performs the manifest actions with real mouse input (the canvas renderers get taps only, through `__tw.tap`). It crops the stages from a DPR 2 viewport screenshot at the widget's snapped device-pixel origin and writes the run files to `reports/conformance/`, which is gitignored. The dev app lists the set as the `conformance/` group in its corpus panel.

## The test group

`scripts/conformance/publish-channel.ts` fills a Telegram forum supergroup with the set through a test bot, in the order of `channel.json`. Each part gets its own topic: Visual, Client SDK and Auto, and Defaults for the example widgets of `corpus/defaults` (v0.1.1, no pictures; their description is the file's `idea:` line). The corpora that fit a mini-widget (`showcase`, `common`, `generated`, `gestures`: what breaks if widgets cannot claim vertical drags, `resize`, and `safe-area`: the client's time over a widget, every post without a caption so the client draws it, `--topic safe-area`) are optional topics, posted only with `--topic`. A bot may send a widget only into its private chat with a user, so each post puts three things into its topic, in this order:

1. The description, from the caption.
2. The widget, sent as an HTML document. A bot may send a widget only into its private chat with a user, so by default the widget goes to the tester's private chat and is forwarded from there. A copy would be a new send, which the server refuses in a group. A test bot allowed to post widgets into any chat (`TW_BOT_DIRECT=1`) sends it straight into the topic.
3. The reference picture or pictures. Automatic widgets have no picture.

The test bot takes the size anchors from the document's file name: `<name>-WxH.html`, where 0 means auto on that axis. A post without anchors goes as `<name>.html`. So `17-size-hint` goes out four times, as in `channel.json`.

The config lives in `~/.config/widget-sandbox/test-bot.env`. The script refuses a config inside a git working tree, and environment variables override the file. It holds:

- `TW_BOT_TOKEN`
- `TW_BOT_USER`: the tester, who must have started the bot. Not needed with `TW_BOT_DIRECT=1`.
- `TW_BOT_GROUP`: the group. The bot needs admin rights there, with Manage topics and Delete messages.
- Optional: `TW_BOT_ID`, `TW_BOT_TEST_ENV=1` for Telegram's test environment, `TW_BOT_API`.

```bash
corepack pnpm tsx scripts/conformance/publish-channel.ts --dry-run   # the plan; with a config, also read-only checks of the bot, the group and the tester's chat
corepack pnpm tsx scripts/conformance/publish-channel.ts --only visual/01-boxes
corepack pnpm tsx scripts/conformance/publish-channel.ts             # everything not posted yet; also --topic, --limit N
corepack pnpm tsx scripts/conformance/publish-channel.ts --reset     # deletes the messages it sent; --delete-topics also deletes the topics, replies included
corepack pnpm tsx scripts/conformance/publish-channel.ts --rebuild --topic Visual   # deletes the topic with everything in it and posts it again
```

The script records, per group, the topics and every message it sent, in `~/.config/widget-sandbox/conformance-channel.state.json`. After a rebuild of the set, a re-run handles each post like this:

- An unchanged post is skipped.
- A changed description or picture is edited in place.
- A changed widget is posted again together with the rest of its topic, so the topic stays in posting order.

Telegram lets a bot delete its own messages only for a while; on the test server an hour-old message is already refused. When the old messages of a changed post cannot be deleted, the script posts nothing again in that topic, because both copies would stay. Delete them by hand, or rebuild the topic: `--rebuild --topic Visual` deletes the topic with everything in it, testers' replies included, and posts it again. A message `--reset` cannot delete stays in the state, and every `--reset` tries it again.

The script sends at most 20 messages a minute into the group, so the full set takes about 9 minutes. It waits out Telegram's flood limits.

## Writing a test

- Start from an existing widget. Keep the header comment `<!-- conformance {…} -->` (title, hint, actions with the catalogue `ids` they feed, host checks, covers) and the `<!-- conformance:harness -->` markers; `build.ts` fills the markers.
- A widget that depends on its message (layer 233: the state, the flags, where it is) says so in the header (`scripts/conformance/lib.ts`, `MessageSpec` and `BotAction`):
  - `"message"`: how the test bot sends it, one entry per post. `kind` is the message context the client must report where the tester looks (`WIDGET_MESSAGE_KINDS`); the `inputMediaWidget` fields `allow_queries`, `fwd_allow_queries`, `shared_widget`, `chat_data`, `public_data`; `chat: "group"`, `forward`, `inline`, `view`. `run-web.ts` gives the dev app's mock bot the first entry's kind and state; `channel.json` gets one post per entry; `publish-channel.ts` leaves such posts to `test-bot.ts --post`.
  - `"bot"`: what the test bot does on a request, by its exact `data` (`conf:<NN>:<what>` for the client-SDK group, `conf:a<NN>…` for the automatic set; unique across the set: the build fails on a clash): `edit` (the message's state), `editShared` (the shared widget's, with `cache_time`), `repeat`, `result`; templates `{n}`, `{from}`, `{time}`. `scripts/conformance/test-bot.ts` is the reference bot (`--self-test` checks it on made-up updates).
  - An automatic check that must hold in every message (a forward, a preview, the private chat) goes into a widget without `message`, so it passes wherever the channel shows it (`auto/25-state-api-*`); a check of one context goes into a widget of that context (`auto/26-context-*`), which a client runs in that context.
- Self-checks use the harness API:

  | Call | Use |
  |---|---|
  | `T.check`, `T.eq`, `T.near`, `T.ok(id, name, …, opts)` | Checks that run at once. |
  | `T.wait(id, name)` | A check resolved later, by an event. |
  | `T.step(fn)` | Runs after the first frames. |
  | `T.target(name, el)` | Publishes a tap or drag target. |
  | `T.run({awaitInput, deadline})` | Starts the widget. |

  Options: `opts.xfail: 'reason'` for known gaps, `opts.sandboxOnly` for what a plain browser cannot do. Also `T.rect`, `T.cs`, `T.color`, `T.stage`, `T.skip`, `T.sandbox`, `T.mode`. The build inlines only the functions a widget calls, minified, so the harness costs about 0.9 KB compressed in a visual widget and 1.3–1.6 KB in an automatic one.
- Pixel stages are `<div data-stage="name" data-covers="ids" [data-themes] [data-max-diff] [data-colors] [data-xfail] [data-illustration]>` with fixed colours on the white stage:
  - auto set: an 80×60 grid at the top;
  - visual set: 150×90, in flow.

  A `<template data-ref="name">` at the end of the body gives the expected rendering when it differs from what Chromium draws. Script-drawn stages go through `T.stage(name, draw)`.
- A picture shows what widget profile 1 draws, never what Chromium does with an out-of-profile feature: such a feature sits in a stage with a template that draws it neutralised, or the widget neutralises it itself when `T.mode == 'ref'` (the out row of `visual/08-canvas-b`, the popover of `visual/13-out-of-profile`). The build checks this: `scripts/conformance/picture-check.ts` draws every widget of the visual set through the sandbox (the dev app, the shadow renderer) and fails when its picture differs by more than 0.5 % of the ink or 2 px of height (the two agree to under 0.1 % otherwise; diffs in `reports/conformance/pictures`). `data-illustration` marks a stage whose template pictures motion or a state after input rather than a frame (the moving rows of `visual/06-css-animations`), and the check leaves it out. The client-SDK group is not compared: its pictures show the SDK stand-in's state. Run it alone on a dev server with `corepack pnpm tsx scripts/conformance/picture-check.ts --origin http://127.0.0.1:5175`.
- Keep every widget within 560 px of height at a 320 px wide bubble. The bubble is at most 600 px tall on every client and on the server, and the 40 px left over is room for platform fonts and an automatic widget's list of failures. `build.ts` checks every widget and fails the build: the visual set and the client-SDK group as Chromium draws them, the automatic set through the sandbox (`heights.ts`, on a dev server the build starts, or the one `--origin` names; `--no-heights` skips it), since drawn natively an automatic widget also lists the out-of-profile checks Chromium fails. `heights.ts` alone measures any widget the same way: `corepack pnpm tsx scripts/conformance/heights.ts --origin http://127.0.0.1:5175`. When a widget outgrows the limit, split it into parts like an oversized one (`auto/23-gestures-a`, `-b`). `"tall": true` in the header exempts a test of the cap itself (`visual/sdk/13-max-height`).
- A widget the client draws its time over (`docs/sdk-v1.md` §2.17) says `"safeArea": {"bottom": 24, "cornerRight": 64}`
  (or `cornerLeft`) in its header: `run-web.ts`, `heights.ts` and `picture-check.ts` give the dev app that content safe
  area (`__tw.setSafeArea`), the build draws its picture with it (Chromium's `env()` through
  `Emulation.setSafeAreaInsetsOverride`, the root variables, the reference time over the corner, `reference.ts`
  `timeCornerStyle`), the picture check leaves the corner out (it is the client's drawing), and `channel.json` has two
  posts of it, `withoutCaption` and `captionAbove`, so `publish-channel.ts` sends the widget without a caption and then
  with its caption above the widget (`show_caption_above_media`, layer 233's `invert_media`), and a client draws its own
  time there both times. Its checks compare with `TelegramWidget.contentSafeAreaInset`, never with the header's numbers
  (`auto/36-safe-area`, `visual/17-time-corner`).
- A widget that must be stopped by the host (a counted limit under strict: `auto/31-stop-text`, `32-stop-attribute`,
  `33-stop-batch`, `34-stop-css-rules`) says so with `"stops": "<the start of the reason>"` in its header. It publishes its
  check first and passes the limit in the next step, natively it skips both (a browser has no such limit); the report
  passes its covers only when the runtime dies with that reason.
- Keep layout above an automatic widget's probes stable.
- Keep every widget under the size limit (`build.ts --sizes`). When a topic outgrows it, split it into parts (`NN-topic-a`, `-b`) with the same check ids and names, rather than trimming what a check measures.
- No external URLs; v1 has no bundled libraries.
- A widget pattern that a client draws wrong, inside profile 1, is not worked around in the widget: it becomes an automatic case that fails on that client, so its team fixes the renderer (see "Patterns from real widgets").
- The harness CSS sets the body's margin and padding to 0 and its own font, so a widget does not depend on the renderer's defaults. A widget that tests those defaults (the UA layer, `docs/sdk-v1.md` §2.4) says `"bare": true` in its header: the build then inlines the harness script and the stage geometry without the harness CSS (`auto/22-ua-defaults`, `visual/14-unstyled-document`, `visual/15-body-background`).

The posted pictures come from the same build: the whole widget in reference mode (templates swapped in) at 390 px, DPR 2, with the renderer's UA layer and the reference theme (`packages/core/src/shared/reference.ts`: the default palettes, the reference text tokens 15 px / 1.35 / `8px 12px`), on the reference palette's bubble colour, set as the page's canvas outside the document so a widget's body background still covers the whole picture. For the client SDK group, `scripts/conformance/sdk-mock.js` is a stand-in for `TelegramWidget` that follows the contract and uses that palette, so the pictures show the "SDK present" state; the visual set gets only the palette. Only the build injects it.

## Patterns from real widgets

`auto/28`–`30` copy the layout of real widgets (the bot demos in `corpus/bot-demos/`, sent through Bot API 10.4) that the Telegram iOS and Android clients drew wrong while the web dev app (Chromium) drew them right. All of it is profile 1, so the widget keeps the pattern and the client fixes its renderer. Each case builds its DOM the way the demo does (`innerHTML`, styles set from script, transitions, per-frame updates) and measures with `getBoundingClientRect`, which the client's own renderer answers:

| Case | Pattern | Seen on a client | The checks |
|---|---|---|---|
| `28-grid-percent-fill` | A poll's options: a `<button>` grid `52px 1fr auto` with `width: 100%`, a bar across columns 2–3 whose fill's `style.width` goes to 100 % with a width transition | In a 100 % row the button grew wider than its container and the auto column to ~200 px, so the count sat mid-row | At load and at 100 %: every row as wide as its container, each count's text ending at the row's end; the bar from 52 px to the row's end; no fill past its bar; each fill its percentage |
| `29-grid-svg-marks` | A 3×3 board: nine `<button>`s (`display: grid; place-items: center`) in an `inset: 0` grid on an `aspect-ratio: 1` box, an SVG overlay for the lines, marks put in with `innerHTML` (`width/height: 58%`, a `stroke-dashoffset` draw-in) | After marks in cells 4 and 5: full-size marks in the middle column at the top and the bottom (iOS), the first in the wrong column (Android) | The board square; the cells equal thirds before, after one mark and after two; each mark centred in its own cell at 58 % of it |
| `30-svg-fill-box` | A chart's end dot: a `<circle>` with `transform-box: fill-box; transform-origin: center` in a `preserveAspectRatio="none"` SVG whose viewBox is its own width, at a fixed `scale(3)` and with the ticker's running pulse (scale 1 → 3), `cx`/`cy` moved per frame | On iOS and Android the pulse shoots up and to the right out of the chart on every iteration. The clients' own renderers ignore `transform-box: fill-box` and scale the circle about the view box's centre (a probe of Artem's: the same with a static `scale()`, which lands outside the box; `transform-origin` in view-box px and an HTML element are right) | The dot at its `cx`/`cy`; `scale(3)` centred on it at 3× its size; the pulse centred at several moments; all again after `cx`/`cy` move. Three pixel stages draw the same circle at `scale(3)` (static, in a running animation, in a running animation whose `cx`/`cy` move) for a renderer that paints other than it measures; each must match its template, a plain circle three times the dot |

## Known limits

- References come from headless Chromium on macOS. WebKit references appear once Playwright's WebKit is installed, and the rule then takes the better of the two.
- Text stages depend on platform fonts. They allow 50 % of the ink but check the feature's colours.
- The web runner gives the canvas renderers (Blitz and litehtml wasm prototypes, which are not product renderers) only synthetic taps. Drags, hover, keys and wheel are skipped there, and so are gesture-gated host checks.

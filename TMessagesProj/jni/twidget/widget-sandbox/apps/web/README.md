# Web dev app, chat feed demo and Playwright harness

Dev tooling for `packages/core`; nothing here is a product path.

- `index.html` + `src/main.ts` — the dev app: corpus picker, drop any `.html`,
  dark theme, reference mode (`?reference=1`, native rendering of the same file
  for comparison — never for `corpus/attacks`), renderer toggles (Shadow DOM,
  Blitz, litehtml wasm, compare all), stats / diagnostics / bridge / console
  panels. Exposes `window.__tw` — the Playwright suites in `tests/` drive it.
- `src/mock-bot.ts` — the **Bot (mock)** panel (the widget's state and requests):
  the widget's chat state (`chatData`) and shared state (`publicData`) per file as a server would
  keep them, the bot's edits of them (`setData`; `edit by id` changes a shared widget's shared state without a push, and the dev
  app polls it only while the widget watches, `watchPublicData()`, at the panel's period), built-in bots that answer requests (a widget
  picks one with `<meta name="tw-bot" content="poll">`; echo, poll, tictactoe,
  ticker, promo), manual answers and client failures, and the context a client
  passes: the kind of message and so the server's flags (private, group,
  via-bot, no-queries, live-copy, copy, preview; the bubble's header shows the network
  marker where the message has `canQuery`), the chat type, the viewer
  (`?message=group&chat=supergroup&viewer=42&botmode=TIMEOUT`). `__tw.bot`,
  `__tw.setContext`; `corpus/interactive` uses it.
- `src/feed.ts` + `src/feed-bench.ts` — the chat feed (`?feed=1`): 72 bubbles
  with the list's own lifecycle, the worker pool on/off, a live renderer
  switch, one hostile bubble, a metrics overlay and the bench panel
  (`?bench=1`, `scripts/feed-report.ts` runs it headless). `docs/chat-feed-web.md`.
- `src/renderers/` — the Blitz and litehtml wasm renderers plugged into the
  `Renderer` seam (need `pnpm build:blitz-web` / `build:litehtml-web`).
- `bench/feed.html` — the raw renderer bench behind `scripts/feed-bench.ts`.
- `vite.config.ts` — the package's plugin plus dev middleware: `/api/corpus`,
  `/corpus/*`, `/reference/*`, `/blitz/*`, `/litehtml/*`.
- `tests/unit/` — the wasm renderers through the validator (skipped until built).

## From a fresh clone

Checked from a fresh `git clone` on macOS (Apple silicon), 2026-10-14; Windows
not tried. Clone into a path without spaces or non-ASCII characters: the
scripts derive the repo root from `new URL(import.meta.url).pathname`, which
percent-encodes them (on Windows it yields `/C:/…`).

Node ≥ 22.12 with corepack (bundled up to Node 24; on Node 25+
`npm install -g corepack`); pnpm itself comes from the `packageManager` pin.

```bash
corepack pnpm install
corepack pnpm dev            # from the repo root; http://127.0.0.1:5173/?file=compat/tabs.html
corepack pnpm dev --port 5174
```

Open `127.0.0.1`, not `localhost`: Vite listens on 127.0.0.1 only, and the
port is strict (`--port` when 5173 is taken). The chat feed is `?feed=1`.

The Blitz and litehtml toggles (the dev app's checkboxes, the feed's renderer
switch, compare all) are frozen prototypes since 2026-10-30 (no runner or bench
uses them by default, their unit tests run only with `TW_NATIVE_RENDERERS=1`) and need their wasm builds in `apps/renderer-*/pkg-web/`
(gitignored); the dev server reads them from disk, no restart. The toolchain,
once (`--version` = the `wasm-bindgen` version in
`apps/renderer-blitz/Cargo.lock`; the build script says so when they differ):

```bash
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
cargo install wasm-bindgen-cli --version 0.2.129 --locked
brew install emscripten      # Linux: emsdk, with emcc on PATH
```

Then, and again after a pull that touches `apps/renderer-*`:

```bash
corepack pnpm build:blitz-web       # cargo, adds the wasm32 target on first run; ~2 min → 8 MB wasm
corepack pnpm build:litehtml-web    # em++; ~30 s → 1.4 MB wasm
```

Editing `packages/core/src` or this directory while a Playwright suite runs
reloads Vite and breaks the run.

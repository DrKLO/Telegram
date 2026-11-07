# widget-sandbox — no-network sandbox for HTML widgets in Telegram messages

A **widget is mini-HTML**: one self-contained HTML document with inline CSS and
JS, at most 4 KB compressed and 32 KB uncompressed, delivered right in the
message. This repository is the framework every Telegram client runs it with,
released as tags (`v0.1.0` is the first).

**Writing widgets** (or prompting a model to): [`docs/widget-runtime-profile.md`](docs/widget-runtime-profile.md)
is the authors' guide, with a block for a model's system prompt
(`corepack pnpm tsx packages/core/scripts/sandbox-node.ts --guide`) and how to test a widget at a
bubble width and with presses.

What the framework does:

- **The widget's JavaScript runs only inside QuickJS**, on its own thread, with
  no network capability at all: `fetch`, XHR, WebSocket and the rest are stubs.
  It sees a DOM shim, not a browser.
- **Everything the widget produces is validated** by trusted host code against
  allowlists: elements, attributes, CSS, URLs (`data:` images and `#fragment`
  only). The validator enforces **widget profile 1** (`docs/profile-v1.md`,
  generated from `packages/core/profile.json`): the same features drawn the
  same way on every client, no form controls, no keyboard, no bundled
  libraries.
- **The renderer draws validated ops only.** On the web that is the Shadow DOM
  renderer inside the client's page; native clients choose their renderer
  (WKWebView, Android WebView, or an engine of their own) behind one seam and
  receive the same ops.
- **SDK v1** (`docs/sdk-v1.md`): `TelegramWidget` with the theme, profile
  detection, `openLink` / `openTelegramLink` gated by a real tap, haptics, and
  Web Storage kept per message by the client. No messages to the bot in v1.
- **`strict` policy by default** on every host, with runtime limits: CPU quota
  per second, a 64 MB memory cap, 256 timers, off-screen pause, and the last
  frame kept when a widget is killed.

```
Client (trusted)
 ├─ QuickJS runtime (own thread): the widget's scripts against the DOM shim (untrusted)
 │     ── ops, canvas commands, sync queries ──▶ host
 │     ◀── events, timer/frame ticks, query answers, SDK calls ── host
 ├─ Validator: allowlists, CSS sanitizer, URL policy, widget profile 1, SDK v1 policy
 └─ Renderer: Shadow DOM (web) · WKWebView · WebView · a native engine — validated ops only
```

The same three JS bundles (sandbox shim, validator, renderer) ship to every
platform with precompiled QuickJS bytecode; each platform adds only glue. The
engine is the TelegramMessenger/quickjs fork, a git submodule.

`twidget` is the project's working name and survives as an internal codename
(the `twidget-run` / `twidget-tests` binaries, the `TwidgetDemo` apps, the
`tw*` prefixes); the repo and the packages a client depends on are `widget-sandbox`
(`org.telegram.widgetsandbox` where a hyphen is not allowed).

## For client teams

Pin the latest tag (`v0.1.1`), never a branch: the bundles, the bytecode and the
QuickJS submodule change together. Then read, in this order:

| | |
|---|---|
| [`docs/client-checklist.md`](docs/client-checklist.md) | one page of checkboxes: adopt, PoC on layer-233 messages, SDK v1, test, send back |
| [`docs/integration.md`](docs/integration.md) | how to pin the tag per package manager, create a `WidgetHost`, pass the policy and features, handle the widget payload, size the bubble, what input a widget gets, the per-platform API |
| [`docs/tgw-format.md`](docs/tgw-format.md) | the widget payload: `messageMediaWidget` (layer 233), the server's checks and errors, caption, old clients |
| [`docs/profile-v1.md`](docs/profile-v1.md), [`packages/core/profile.json`](packages/core/profile.json) | widget profile 1: what a widget may use and what the validator does with the rest |
| [`docs/sdk-v1.md`](docs/sdk-v1.md) | the `TelegramWidget` API, the protocol, the host interface on each platform and what the client implements |
| [`packages/conformance/CLIENTS.md`](packages/conformance/CLIENTS.md) | the conformance set: run it on your renderer and compare with the other clients |
| [`docs/changes.md`](docs/changes.md) | release notes, known issues, API changes per platform |

Changing the framework: [`CONTRIBUTING.md`](CONTRIBUTING.md).

Packages: Swift package `WidgetSandbox` (repo-root `Package.swift`, iOS and
macOS), Gradle library `org.telegram.widgetsandbox:widget-sandbox`
(`packages/android`), npm `@telegram/widget-sandbox` (`packages/core`), C++
core `packages/desktop`. `docs/decisions.md` records why things are the way
they are; `docs/protocol.md` is the wire protocol.

## Prerequisites

Everything is built from this repo; nothing is fetched at run time. What you
need depends on which part you touch:

| To work on | Install |
|---|---|
| the web package, the dev app, the Playwright suites | Node ≥ 22 with corepack (`corepack pnpm` — the repo pins pnpm in `package.json`); then `corepack pnpm exec playwright install chromium` once for the browser suites |
| `packages/ios` / `apps/ios`, `apps/macos` | Xcode 26 (iOS 16 / macOS 13 deployment targets); [xcodegen](https://github.com/yonaskolb/XcodeGen) to regenerate the demo projects; a C compiler is already there for `qjsc` |
| `packages/android` / `apps/android` | JDK 21, Android SDK with platform 35, NDK 28.2 and cmake 3.31 (`local.properties` or `ANDROID_HOME`); Gradle comes from the wrapper |
| `packages/desktop` | cmake ≥ 3.22, ninja, a C++20 compiler |
| the renderer prototypes (`apps/renderer-blitz`, `apps/renderer-litehtml`) — optional, dev tooling | rustup's cargo (+ `cargo-ndk` for Android, `wasm-bindgen-cli` for the web build); cmake + ninja (+ emscripten for the litehtml web build) |

Clone with `git submodule update --init` (the QuickJS fork in
`packages/ios/Sources/CQuickJS/quickjs`; SwiftPM does it for package
dependencies). The native packages ship the JS bundles and their bytecode, so
a client needs none of the Node tooling; only changing `packages/core/src`
requires `pnpm build:<platform>` (which compiles `packages/tools/qjsc` from the
submodule on first use, into `.cache/`).

## Getting started

```bash
corepack pnpm install
corepack pnpm dev                       # dev app on http://127.0.0.1:5173 — pick a corpus file, drop any .html
corepack pnpm typecheck && corepack pnpm test:unit
corepack pnpm test:attacks              # the security suite: 99 files, 0 canary hits
```

Native: `xcodebuild test -scheme WidgetSandbox-Package -destination 'platform=macOS'`
for the Swift package; `cd packages/android && ./gradlew :widget-sandbox:assembleDebug`
(+ `connectedDebugAndroidTest` with an emulator); `cd packages/desktop && cmake -S . -B build -G Ninja && cmake --build build && ./build/twidget-tests`.
The demo apps are in `apps/`, each README has the commands.

## Testing

| Suite | What it proves | Run |
|---|---|---|
| unit (`packages/core/tests/unit`, `apps/web/tests/unit`) | validator, CSS sanitizer and profile rules, the SDK policy against the shared fixture, the DOM shim through QuickJS in Node, the CPU budget against the shared fixture, the wasm memory cap, the shipped bundles against a fresh build | `pnpm test:unit` |
| attacks (`tests/attacks.spec.ts`, `corpus/attacks`) | every exfiltration vector gets 0 canary hits and 0 outside requests; also on the Shadow DOM renderer with `TW_RENDERER=shadow` | `pnpm test:attacks` |
| activation, sdk, lifecycle | privileged calls need a real tap; the SDK on the web host (theme variables, gesture claims, storage across a restart); off-screen pause | `pnpm test:activation`, `test:sdk`, `test:lifecycle` |
| compat / generated / common / showcase / htmlbubbles | widgets load, run their scripted interactions, and the sandbox render matches the native reference (screenshot diff) → `docs/compat-report*.md` | `pnpm test:compat` … |
| conformance (`packages/conformance`) | the profile features, checked by the widgets themselves on each renderer → `packages/conformance/results` | `scripts/conformance/run-web.ts`, `CLIENTS.md` for a client |
| Swift package (macOS 220, iOS 221 tests), Android instrumented (60), desktop (45; 51 with the renderer prototypes) + the attack corpus with 0 leaks on every renderer | the same invariants through the native glue: engine, validator, renderer, limits, pools, SDK | see above |
| demo autoruns (`--autorun attacks|generated|compat|pause` on iOS/macOS, `--es autorun …` on Android) | the corpora on a device, JSON reports in the app container | `apps/*/README.md` |

Before a commit that touches `packages/core/src`: typecheck, unit, attacks,
and `pnpm build:ios`/`android`/`desktop` so the shipped bundles and `.qbc`
match the source (a stale `.qbc` is refused at load time and the native
tests fail).

## Commands

```bash
pnpm install
pnpm dev            # dev app on http://127.0.0.1:5173 (COOP/COEP set by Vite)
pnpm test:unit      # validator / sanitizer / profile / SDK unit tests + shim regression tests through QuickJS (Node)
pnpm test:attacks   # every corpus/attacks/*.html → zero canary hits, no outside requests
pnpm test:compat    # corpus/compat/*.html in sandbox vs native reference → docs/compat-report.md (screenshots in reports/compat/)
pnpm test:generated # corpus/generated/** (committed corpus, or regenerate with pnpm generate-corpus) → docs/compat-report-generated.md
pnpm typecheck
pnpm tsx packages/core/scripts/sandbox-node.ts corpus/compat/tabs.html --verbose   # run a widget through the sandbox in Node
pnpm tsx packages/core/scripts/profile-build.ts   # regenerate docs/profile-v1.md and the profile tables from packages/core/profile.json
ANTHROPIC_API_KEY=… pnpm generate-corpus [--model claude-sonnet-5-5] [--count 20]
```

The committed `corpus/generated/` (20 prompts × {without,with}-profile) was authored
by Claude inside a Claude Code session rather than through the API (no key was
available); `manifest.json` in each batch says so. Results and caveats are in
`docs/compat-report-generated.md` and the "Phase 6" section of `docs/decisions.md`.

The dev app: corpus list and drag-and-drop on the left, the widget in a
chat-bubble-sized frame in the middle (light/dark toggle, renderer switch,
reference-mode toggle), diagnostics on the right (widget console, unsupported
APIs, ops/queries/latency, JS time, memory, errors). The iframe renderer stays
the dev app's default and the reference; the Shadow DOM renderer (`?shadow=1`)
is the product one. URL parameters: `?file=compat/tabs.html&theme=dark&reference=1`.

## Layout

`packages/` is the framework — what a client depends on; nothing in it imports
from `apps/`. `apps/` is the demos, the native renderer prototypes and the dev
tooling, consuming the packages the way a client would. `corpus/`, `tests/`
(Playwright, run against `apps/web`) and `docs/` are shared.

| Path | What |
|---|---|
| `packages/core/` | npm package `@telegram/widget-sandbox`: `src/host/` (trusted: `widget-host.ts`, `worker.ts`, `validator.ts`, `css-sanitizer.ts`, `url-policy.ts`, `allowlist.ts`, `shadow-renderer.ts`), `src/renderer/` (trusted code on the shadow root / inside the dev iframe), `src/sandbox/` (**untrusted** once loaded: the DOM shim over linkedom and the `TelegramWidget` SDK), `src/shared/` (`protocol.ts`, `profile-tables.ts`, `sdk-policy.ts`), `profile.json`; `vite.ts` (the plugin that builds the bundles), `scripts/` (bundle build, profile build, Node harness), `tests/unit/` |
| `packages/ios/` | Swift package `WidgetSandbox` (the QuickJS fork + WKWebView, same JS bundles) for iOS and macOS; the manifest is the repo-root `Package.swift` — see [packages/ios/README.md](packages/ios/README.md); `pnpm build:ios` builds its bundles |
| `packages/android/` | Gradle library `widget-sandbox` = `org.telegram.widgetsandbox:widget-sandbox` (the same QuickJS sources via NDK + `android.webkit.WebView`, same JS bundles) — see [packages/android/README.md](packages/android/README.md); `pnpm build:android` |
| `packages/desktop/` | Qt-free C++ core for tdesktop-class hosts — see [packages/desktop/README.md](packages/desktop/README.md); `pnpm build:desktop` |
| `packages/conformance/` | the widget profile conformance set: feature catalogue, 40 self-checking widgets, 20 visual widgets and 17 client-SDK cases (every one a mini-widget), reference pictures, the test-channel post list, the report tool — see [packages/conformance/README.md](packages/conformance/README.md) and `CLIENTS.md` |
| `packages/quickjs-wasm/` | the QuickJS fork compiled to wasm for the web host and the Node harness (`build.sh`; the output is committed and reproducible with the pinned emscripten: `build.sh --check [--docker]` against `dist.sha256`) |
| `packages/tools/qjsc` | bytecode compiler from the QuickJS submodule (`.qbc` next to each bundle) |
| `apps/web/` | the dev app (`src/main.ts`, exposes `window.__tw` for the Playwright suites), the chat feed demo (`src/feed.ts`), bench pages, the Blitz / litehtml wasm renderer glue (`src/renderers/`) |
| `apps/ios/DemoApp`, `apps/macos/DemoApp` | SwiftUI demos (the macOS one shares the iOS sources) with `--autorun attacks|generated|compat|pause` — see [apps/macos/README.md](apps/macos/README.md) |
| `apps/android/` | the Compose demo and the Blitz / litehtml renderer modules; a second Gradle build over `packages/android` (`includeBuild`) |
| `apps/renderer-blitz/`, `apps/renderer-litehtml/` | native renderer prototypes behind the `Renderer` seam (Rust crate; C++ core with vendored litehtml) — dev tooling, not a product path |
| `corpus/attacks/` | one exfiltration vector per file (99) |
| `corpus/compat/`, `corpus/common/`, `corpus/showcase/`, `corpus/generated/`, `corpus/htmlbubbles-ported/`, `corpus/out-of-profile/` | realistic widgets on profile 1 with scripted interactions; the out-of-profile set shows what the validator removes |
| `tests/` | Playwright suites, canary server |
| `docs/` | `integration.md`, `tgw-format.md`, `profile-v1.md`, `sdk-v1.md`, `changes.md`, `decisions.md`, `protocol.md`, `widget-runtime-profile.md`, the compat reports, `native-port-notes.md`, the renderer experiments |

## Security invariants

See `CLAUDE.md` ("Invariants") and the per-file comments in `packages/core/src/host/`
and `packages/core/src/renderer/`. In short: widget JS only in QuickJS; everything from the
sandbox is validated against allowlists and widget profile 1; only `data:` images, host
`blob:` and `#fragment` URLs reach the renderer; the renderer runs trusted code only, on a
page whose CSP closes the network (Shadow DOM) or in a frame with a strict per-widget CSP,
and cancels every navigation; QuickJS has no network capability at all.

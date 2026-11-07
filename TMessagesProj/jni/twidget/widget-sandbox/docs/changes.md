# Changes since 862eec8 (telegram-macos)

What a host that upgrades from 862eec8 sees, per platform. "Opt-in" items change
nothing until the host lists them; "default" items apply to every host.

## v0.6.0 — release notes (2026-11-02)

The limits for the launch. The client teams ran the stress kit on five devices (a Redmi Note 8T and a Galaxy A55 on
Android, an iPhone Air, tdesktop on an i9, the web) and seven real widgets on the slow phone; their numbers set the
counted limits, which stop a widget the same way on every device and can only be loosened later, and turned the stops
in time into slowdowns. With them: the heavy-box limit that each renderer counts, a cheaper validator whose time now
counts on the native hosts, the tweb team's five fixes to v0.3.0's strict limits, and the release plumbing open since
v0.1.0 (`VERSION.json` with stamped bundles, a reproducible wasm; CI waits on a branch). A client updates its glue and bundles together
(a mixed pair now refuses to start), implements the heavy-box count in its own renderer against
`packages/core/tests/fixtures/paint-policy.json` and reports it through the seam's `limitExceeded`, and reruns the
conformance set (`auto/37`–`44` are new).

**Limits from the client teams' stress-kit runs** (Redmi Note 8T, Galaxy A55, iPhone Air, tdesktop on an i9, the web;
`docs/decisions.md`, "Strict limits from the start", "The client teams' numbers"). Counted limits stop a widget the same
on every device; limits in time only slow it down and stop it on a hang:

- Default, every host: every task gets the whole `cpuLimitMs` slice (500 ms), whatever the CPU quota has left; what a
  task spends past the quota is a debt (at most one more quota) that holds the next timers and frames until the window
  has room (`cpu-budget.ts` and its three twins; `tests/fixtures/cpu-budget.json` gains a `used` step; `docs/decisions.md`,
  "Throttle, not kill"). Until now a task started with little quota left got `max(remaining, 50 ms)` and was stopped
  when it ran longer, so a slow phone stopped widgets a fast one runs (a 3D showcase widget on a Redmi Note 8T, three
  runs out of three). The `CPU limit exceeded` message lost its quota part. The main-thread window follows the same rule.
- Default, every host: **at most 2000 nodes** (elements, text, comments) in the document, compared once a batch is
  applied (`Node limit exceeded: N nodes in the document (max 2000)`; `validator.ts` and the Swift `NativeValidator`
  from the policy tables). Until now the validator stopped creating nodes past 50 000, and the 8 MB heap stopped a
  document first: at 2000 nodes on a 64-bit host, at 4000 on a 32-bit one.
- Default, every host: **the shim hands a long flush over in parts** (`capture.ts`): between two nodes, once about 1000
  ops are buffered, they go to the glue as a batch of their own; the flush's method calls, canvases and animations stay
  in its last batch with the staged canvas payloads. A 2000-element document built in one task loaded nowhere on a
  64-bit host (its op buffer and JSON, not the document, filled the heap); now it reaches the node cap. The protocol is
  unchanged; a host applies the batches in order as before.
- Default, every host: **at most 4000 canvas commands** per canvas in one task (10 000 until now): the shim throws
  `RangeError` on the 4001st, the validator stops a send with more. Under strict the main-thread window already holds
  4000 a frame to ~13 fps on every client measured. `CanvasBatchFastPath.maxCmdsPerOp` follows (Swift).
- Default, every host: **at most 128 layout queries in one task**, and their time at most 1000 ms (the hang guard), in
  place of 100 ms of their time: `queryLimitHit` / `queryLimitReason` in `cpu-budget.ts` and its three twins (the fixture's
  `queries` steps replace `queryBudget`), the count in all five glue copies; a task that ends on the error answers before
  the interrupt lands is stopped at its end. A widget with one `getBoundingClientRect` at load died on a Redmi Note 8T in
  its first run after install (a cold first layout over 100 ms); the most query-heavy real widget makes 47.
- Default, every host: **text inserted while the widget loads is not charged to the churn bucket**: every host calls the
  validator's new `markLoaded` after the loading tasks' ops (`__twValidator.markLoaded` in the bundle; the Swift, Kotlin
  and C++ `TrustedValidator`, `NativeValidator`). A 48 K document of paragraphs built by the loading script lost them past
  ~45 K, silently, on every host.
- Default, every host: **at most 16 heavy boxes at once, of them at most 2 with `backdrop-filter`**; past either the
  widget stops (`docs/decisions.md`, "Heavy boxes"; `L2`, `limit.paint-heavy`, `limit.paint-backdrop` in profile 1). A
  box is heavy with a blurred `box-shadow` layer, a `filter`, a `backdrop-filter` or `opacity` below 1, or while an
  animation of `filter`, `backdrop-filter`, `opacity`, or of `box-shadow` through a blurred layer, runs on it; a
  `<button>` that is disabled or held down is not heavy for its `opacity` (the client's own dimming: a calendar of
  disabled days runs). 16 moving heavy boxes cost the renderer 1–8 ms a frame on all four clients measured. The renderer
  counts on the computed style before each frame (`packages/core/src/shared/paint-policy.ts`); past the limit it draws
  the widget without those effects and reports `{ type: 'limit', code, count }`, and the host stops it with `Paint limit
  exceeded: N boxes with shadows, filters or transparency (max 16)` or `… N boxes with backdrop-filter (max 2)` and the
  diagnostic `paint-limit` / `backdrop-limit`. New host → renderer `{ type: 'policy', policy }` at load: compat counts
  nothing. **A client's own renderer** counts against `packages/core/tests/fixtures/paint-policy.json` and calls the
  seam's new `limitExceeded(code, count)` (`Renderer.swift` with `WidgetPaintLimit`, `Renderer.kt`, `renderer.h`). The
  harness's `--width` prints a `paint:` line. Six corpus widgets mute with colour instead of `opacity` now
  (`showcase/life/01-fitness-rings`, `showcase/travel/04-weather`, the generated countdowns, two gesture examples).
  Conformance `auto/37-paint-counted` and the stop cases `auto/38`–`43`; attacks `paint-*` (six).
- `corpus/showcase/3d`: the six projected 3D widgets draw at most 2000 canvas commands a frame in every state (every
  model, pose, elevation with the cat's full mouse pitch, both zoom bounds, both themes, 200–600 px), about 25 fps at the
  main-thread window's 2 µs a command. Before: robot 4144, camera 1247, planet 20 668 (past even the old cap at zoom 1.3),
  island 2976, molecules 8164, cat 7606; now 1369, 605, ≤ 1964, 993, 704, 1675. Closed solids skip back faces and a
  ball's poles are triangles (its top is now lit like its sides); a face is filled once without its outline stroke; the
  floor grid is one path; bond beads are discs; the cat's large balls have 10 × 5 facets and its tail is a striped tube;
  the planet draws each 2 px band of its disc as one gradient under a command budget, clipped to a smooth circle. Same
  scenes, controls, storage and behaviour; `showcase-3d.test.ts` checks the budget under strict (39 tests). `06-cat` is
  3798 B after zlib with this machine's Node (3746 B with the server's), so its size check passes here too.
- `packages/stress` version 2: the limits as above (`canvasPerSend` 4000, `layoutQueries` 128 crossed by
  `layout-queries/256`), `css-rules/400` at the cap instead of over it (the frame's rules count), the largest batch as
  measured; conformance `auto/44-stop-nodes`, `auto/19-limits` at 4000 commands.

**The validator: cheaper, and its time counts** (`docs/decisions.md`, "The validator's time counts"):

- Default, every host (with the bundles): the validator takes simple style values without css-tree (`css-sanitizer.ts`
  `fastStyleValue`): numbers, lengths, percentages, `#` colours, keywords and the profile's functions of them, over
  `[0-9A-Za-z.%#(),-]` and the space, written as css-tree writes them; anything else goes through css-tree as before. The
  output is byte-identical: `tests/unit/css-fast-path.test.ts` runs both paths over every declaration of the corpora, the
  conformance set and the stress kit in all 32 envs, each value under every property, and a generated set. A style op of
  a moving widget now costs the validator about 4 µs end to end in the native hosts' QuickJS instead of 45 on a Mac
  (about 12–15 µs instead of 134 on a Galaxy A55), and 0.2 µs instead of 3.6 in V8.
- Default, Swift, Kotlin, C++: the validator's CPU time for a widget's messages (ops, queries, `listen`,
  `preventDefault`, anim), measured on its own thread, counts against the widget's JS quota: `TrustedValidator` reports it
  to a CPU sink (`setCpuSink` / `cpuSink`), `WidgetHost` passes it to `SandboxEngine.chargeValidator`, which records it on
  the engine thread; `stats.validatorMs` next to `jsMs`, and `cpuWindowMs` includes it. Until now `opacity-moving/128`
  kept an A55's validator thread busy about 900 ms a second outside every limit. The web validates in V8 on its main
  thread and stays modelled by the main-thread window. A host that drives `SandboxEngine` and `TrustedValidator` itself,
  not through `WidgetHost`, wires them the same way (the desktop harness does).

**Versions and a reproducible wasm** (open since v0.1.0):

- Default, every native host: `VERSION.json` at the repository root names the release and the versions the glue and the
  bundles share (`version` 0.6.0, `protocol` 1, `profile` 1, `sdk` 1; `docs/integration.md`, "Pinning a release").
  `pnpm build:ios|android|desktop` stamps it on the first line of each bundle (`/* widget-sandbox version=0.6.0 protocol=1
  profile=1 sdk=1 */`) and writes it into the glue (`WidgetSandboxVersion.swift` / `.kt`, `twidget/version.h`; the glue's
  `profile` comes from it). Before the engine evaluates a bundle it checks the stamps of all three (`BundleStamp.swift` /
  `.kt`, `bundle_stamp.cpp`): without one, or with another protocol, profile or sdk, it stops at boot with
  `bundle-mismatch` and `sandbox bundle failed to load`, and the validator loads nothing; the release itself is not
  compared. A client that ships its tag's bundles sees nothing; one that mixes two releases fails at the first widget
  instead of misbehaving. New: Swift `SandboxResources(sandboxBundle:rendererBundle:validatorBundle:sandboxBytecode:validatorBytecode:)`,
  `SandboxResources.refusal` (Swift, Kotlin), `BundleStamp::refusal` (C++). Native only: the web worker and the Node
  harness build their bundle from the same sources. `package.json`, `packages/core/package.json` and the Android library
  are at 0.6.0; `tests/unit/version.test.ts` fails when any of them, `profile.json`, the platform constants or a shipped
  bundle's stamp disagrees.
- `packages/quickjs-wasm/dist` is reproducible (`docs/decisions.md`, "QuickJS for the web and Node from our fork"):
  emscripten 6.0.12 pinned in `build.sh` (`--docker`: the emsdk image by digest), `dist.sha256`, `build.sh --check`. The
  wasm is rebuilt once: the assert messages' source path is `vendor/quickjs-ng/quickjs-amalgam.c` instead of a random
  temporary directory (64 bytes shorter); nothing else changed.
- `CONTRIBUTING.md` §6, "Cut a release": bumping `VERSION.json` and the files it checks, the test matrix, the tag.

**Strict limits: what the tweb team found moving to v0.4.0** (five findings, all from v0.3.0's strict limits; every
host gets them with the bundles, the Swift `NativeValidator` has its own copy of the first and the fourth):

- The document's text, CSS rules and `@keyframes` are compared with their caps once the whole batch is applied, not op
  by op (`validator.ts` `checkTotals`, `NativeValidator.swift` the same). A subtree replaced in one task (`innerHTML`,
  `replaceChildren`) passed through the old and the new text together, so a widget holding more than 24 K characters
  stopped on its first redraw; two stylesheets rewritten in one task stopped the widget or not depending on which was
  written first. The caps per value (32 K of text, 8 K per attribute) and per batch (10 000 ops) are unchanged.
- The shim removes old children before it creates the new ones when nothing under them moved elsewhere (`capture.ts`
  `syncChildren`), so the complex-script budget (8192 units; it drops the text, it does not stop) no longer refuses the
  new text of a widget in Arabic, CJK, … that redraws more than 4096 units at once. A node moved out of a removed
  subtree keeps the old order: its insert goes first.
- The shim's cap of 64 live script animations counts script animations only (`waapi.ts`): CSS animations and
  transitions the script controls (`getAnimations()`, `pause()`) are the stylesheet's, as they already were in the
  validator. Before, past 64 of them the rest were never sent and kept running in the renderer.
- `countCssRules` (JS and Swift) skips an escaped character outside strings: 201 rules like `.a\{x0{…}` stopped a
  widget as 402, and 40 blocks like `@keyframes k\}0{…}` passed under the cap of 32 as rules.
- Conformance `auto/33-stop-batch` passes the ops cap with 10 001 scroll calls instead of 5001 new elements: those needed
  about 8 MB of heap, so a client's heap could stop the case for memory first; it now stops for the ops at 5 MB.

**Wallet docs** (no code changes: `onWallet` and the framework's checks stay as in v0.5.0):

- Docs: the server's agreed wallet API (`docs/decisions.md`, "The wallet"; `docs/sdk-v1.md` §2.18, §5.13;
  `docs/integration.md`). A sent transaction and a signature reach the bot with the device's wallet (`userWallet`:
  address, balance, public key, StateInit), which the client fills: with the wallet's backup off a device can hold
  another key and another wallet than the profile's card; `with_wallet` brings the profile's card.
- Docs: the formats of the wallet's outcome as the server takes them (`docs/sdk-v1.md` §5.13): `userWallet.address`
  raw or user-friendly (mainnet), `balance` in nanotons, `public_key` as its 32 bytes and `state_init` (new in the
  schema: the wallet's StateInit as a bag of cells, which ties the key to the address of a wallet not deployed yet;
  the server does not look inside) under one flag, both with a signature, `msg_hash` as 64 hex digits, `sign_data` as
  TON Connect's result.
- `corpus/bot-demos/07-wallet.html`: the Gram Wallet test on mainnet (the wallet card, a signature the bot checks,
  0.01 TON to oneself found on toncenter); its bot is not in this repository, the dev app's mock bot `gram` plays it.

**Known issues:**

- No client renderer has reported the conformance set or the attack corpus yet (only the web's are in
  `packages/conformance/results`); the heavy-box count is new work for every client's own renderer.
- No CI in this tag. The GitHub Actions workflows (`ci.yml` on every push and pull request to `main`: typecheck, unit
  tests, the shipped bundles against a fresh build, `test:attacks` on both web renderers, the C++ core with its attack
  run, `xcodebuild test` on macOS, the Android JVM tests; `wasm.yml`: `build.sh --check --docker` when the wasm's inputs
  change) wait on the branch `claude/ci`: pushing workflow files needs a token with the `workflow` scope, and they have
  never run on GitHub (checked with actionlint only).
- The prices (300 ms/s of JS, the 100 ms/s window at 25 / 2 µs) were measured only on strong and middle devices: no weak
  iPhone and no web on weak hardware in the data. They can move either way without breaking widgets.
- A CSS animation that makes a box heavy is counted at its `animationstart`, a frame after the engine starts it; a
  limit reached through ops is always stopped before the frame. `display: contents` (out of profile 1) is in the
  paint fixture but cannot be a conformance case.
- A long flush goes out in parts and the totals are compared after each part: a rebuild that moves nodes out of the old
  subtree and passes ~1000 ops can see the old and the new text or nodes together for one part.
- The QuickJS fork (c24d6b0): `a.length = 0` on a sparse array whose first index is above 0 leaves about half of its
  elements readable (V8 clears them; found while lightening the 3D showcase, which now avoids it).
- The Android team reports that the server takes `msg_hash` only in base64 (2026-10-31); `docs/sdk-v1.md` §5.13 says
  64 hex digits. To settle with the server before a client ships the wallet.
- `htmlbubbles-ported`: 110 / 116 on the iframe renderer, the six known screenshot-only rows; not run on shadow for
  this release.
- Test matrix on this tree (9c64646 and the docs after it): typecheck and unit 934 / 934; on iframe and shadow attacks 157
  (119 files, the six `paint-*` stopped by the heavy-box limit) and showcase 27; on iframe sdk 45, gestures 28 (both
  renderers inside the suite), activation 4, lifecycle 6, compat 20, generated 40, common 32, defaults 6, the safe-area,
  resize and gesture corpora (7, 3, 8); the conformance set on iframe and shadow, light and dark: 182 / 3 / 0 in the
  profile, 77 out neutralised, 135 / 135 automatic runs, 0 errors; Swift macOS 351 and iOS 27.0 (iPhone 17 simulator)
  353 with only `IntlBridgeTests.testPluralRulesMatchICU` failing (the OS's ICU, as before); Kotlin 37 JVM + 128
  instrumented (AVD twidget-35); C++ 86 tests and 119 attacks with 0 leaks. Not checked: the native demos on a simulator
  or emulator, a real phone.

## v0.5.0 — release notes (2026-10-30)

The wallet. A widget can ask the user to pay or to sign through Telegram's built-in wallet (Gram Wallet), in TON
Connect's formats, and the user confirms in the client's own wallet window, which names the bot of the message. The
widget is the front of a dApp and its bot is the back end: on a tap the widget asks the bot with the user's wallet,
the bot answers with a fresh transaction, the widget passes it to the wallet without a new tap, and the bot gets the
outcome (the message hash, or the signature) and answers the widget. A client that uses the framework's glue updates,
passes `context.wallet` where it has the wallet for the user, and implements `onWallet` with its existing TON Connect
window (`docs/sdk-v1.md` §2.18, §5.13; `docs/integration.md`, "The wallet"). Until the server ships its side (a
proposal for a later layer, `docs/decisions.md`, "The wallet"), a client leaves `context.wallet` unset and nothing
changes: the widget sees `TelegramWidget.wallet === null`.

**The wallet** (`docs/sdk-v1.md` §2.18, §5.13; `docs/decisions.md`, "The wallet"):

- **Widgets:** `TelegramWidget.wallet` (null where the client has no wallet for this user, where the widget may not
  ask the bot, in a preview) with `sendTransaction(transaction, data)` and `signData(payload, data)`, each resolving
  with the bot's answer to its outcome; `request(data, { wallet: true })` on a tap gives the bot the user's wallet
  (created if they had none). A wallet call needs a tap, or follows within 10 s the answer to a call made by a tap
  (the grant); one call in flight with the requests; new codes `REJECTED` (the user declined) and `BAD_REQUEST` (the
  wallet refused it). The transaction is TON Connect's `sendTransaction` request: 1–255 messages (what Gram Wallet
  takes), user-friendly destinations (a raw one is refused, as TON Connect wallets must), `payload` / `stateInit` of
  at most 8192 base64 characters each and 65536 together; the payload is TON Connect's `signData` (text, bytes, cell),
  without a domain: the client signs for `bot<bot_id>`.
- **Hosts (all five glue copies):** `context.wallet`, `WidgetRequest.wallet`, `onWallet(call, complete)` with
  `WalletCall` (`sendTransaction` with a `WalletTransaction`, `signData` with a `SignDataPayload`, `data`; their
  JSON forms in TON Connect's field names); `WidgetRequestFailure` gains `rejected` / `badRequest` (a request's
  failure turns them into `FAILED`). The checks are `sdk-policy` and its three twins on the fixture:
  `checkWalletTransaction`, `checkSignDataPayload`, `checkRequestOptions`, the `request` and `wallet` bridge calls,
  `RequestGate.decideWallet` and the grant (`finish(answeredAt)`), verdicts `granted` and `no-wallet`.
- **Default for every host:** nothing changes until the client passes `context.wallet`.
- **Dev tooling:** the dev app's mock wallet (the **wallet** box in the Bot (mock) panel, `?wallet=0`, a wallet
  window over the page with its buttons off for 600 ms, the `shop` bot), `corpus/interactive/05-wallet.html`, the
  harness's `--wallet`, `--wallet-answer`, `--wallet-error`; attacks `wallet-no-activation`, `wallet-forged-wire`,
  `wallet-grant-reuse`.

**Known issues:**

- The server's side is a proposal (`docs/decisions.md`, "The wallet": the query types, `with_wallet`, the wallet card
  in the bot's updates): no client can deliver a wallet call end to end yet; leave `context.wallet` unset until the
  server ships it. Open there: whether the TON Connect window sends through `wallet.sendTransfer` and which message
  hash the server keeps, the TEP-81 form of `bot<bot_id>` for a cell signature.
- No client window yet, no mock wallet in the native demos, no conformance case and no test bot for the wallet (the
  test widget on @sswidgetbot comes next).
- Test matrix on this tree: typecheck and unit 830/831 (`showcase-3d`'s size check of `06-cat`, as in v0.4.0); on iframe
  and shadow attacks 144 (the three `wallet-*` again after `wallet-forged-wire` moved to 256 messages), sdk 45, compat
  20, activation 4; showcase 27 on iframe; Swift macOS 334 and iOS 27.0 335 with only
  `IntlBridgeTests.testPluralRulesMatchICU` failing (the OS's ICU, as before), Kotlin 30 JVM + 122 instrumented, C++ 80
  tests and 113 attacks with 0 leaks. Not run for this release: the conformance set and the other corpora (the
  renderer and the validator are unchanged).

## v0.4.0 — release notes (2026-10-30)

The client's time over a widget. A message with a widget and no caption, or with its caption shown above the widget
(`invert_media`), gets its time and status drawn by the client over the widget's bottom corner, as on a photo, and the
widget is told what is covered: `env(safe-area-inset-*)` is in widget profile 1 (a clarification, `P1-safe-area`),
rewritten by the validator to root variables the renderer sets, and `TelegramWidget.contentSafeAreaInset` gives script
the same numbers. A client that uses the framework's glue and renderer updates and passes the band and its corner
(`contentSafeArea` at creation, `setContentSafeArea` when the bubble's layout changes) for such a message; one with its
own renderer sets the six variables; one with its own glue passes the boot field and the dispatch (`docs/sdk-v1.md`
§2.17, §3, §5.12; `docs/integration.md`). A client that passes nothing keeps v0.3.0's layout (every value 0). Examples
for the client teams: `corpus/safe-area` and the test group's **Safe area** topic; conformance `auto/36-safe-area` and
`visual/17-time-corner`. Also in this release, for the maintainers: the Blitz and litehtml renderer prototypes are
frozen (below).

**The client's time over a widget with no caption below it: the content safe area** (`docs/sdk-v1.md` §2.17, §5.12;
`docs/decisions.md`, "The client's time over a widget with no caption below it"; a clarification of profile 1,
`P1-safe-area`). A message with a widget and no caption gets its time and status drawn by the client over the widget's
bottom corner, as on a photo; so does one whose caption is shown above the widget (`invert_media`, Artem's decision of
2026-10-30); with a caption below nothing covers the widget. Every host passes nothing by default, so a client that
does not draw the time keeps v0.3.0's layout.

- Widgets: `env(safe-area-inset-top|right|bottom|left[, fallback])` is in. The validator (`css-sanitizer.ts`, which the
  Swift `NativeValidator` runs as its CSS oracle) rewrites it to `var(--tg-content-safe-area-inset-…[, fallback])` in
  stylesheets, `style` attributes, CSSOM writes and SVG presentation attributes; any other `env()` name resolves to its
  fallback or drops the declaration without one (before: every declaration with `env()` was dropped, fallback or not;
  conformance `auto/04-values-units` now expects the client's inset); `env()` inside `url()` stays rejected.
  `CSS.supports` / `@supports` answer `true` for the safe-area insets only.
- The renderer core sets `--tg-content-safe-area-inset-top|right|bottom|left` and `--tg-content-safe-area-corner-left|right`
  on the root, like the theme's (always set, 0 until the host says; an inset 0–64 px, a corner 0–200 px; the widget
  cannot replace them); the litehtml prototype's twin is `theme::safe_area_variables`. The UA layer adds the band to the
  body's bottom padding (`:where(body){padding-bottom:var(--tg-content-safe-area-inset-bottom,0px)}`).
- `TelegramWidget.contentSafeAreaInset` (`{ top, right, bottom, left, cornerLeft, cornerRight }`, frozen, a fresh object
  per change) and the event `contentSafeAreaChanged`, in one task with the variables; the shim drops its cached layout
  answers on a change.
- Hosts, every copy: an option at creation and a setter. Web `WidgetHostOptions.contentSafeArea`,
  `setContentSafeArea`, `contentSafeArea`; Swift `WidgetHost.Options.contentSafeArea` (`WidgetContentSafeArea`),
  `setContentSafeArea(_:)`, `SandboxEngine.contentSafeArea(_:)`; Kotlin `WidgetHost.Options.contentSafeArea`
  (`WidgetContentSafeArea`), `setContentSafeArea`, `SandboxEngine.contentSafeArea`; C++
  `WidgetHostOptions::contentSafeArea` (`twidget::WidgetContentSafeArea`), `WidgetHost::setContentSafeArea`,
  `SandboxEngine::contentSafeArea`; the Node harness's `contentSafeArea`, `{ safeArea }` steps and `--safe-area
  bottom[,cornerRight[,cornerLeft]]`. New messages: the renderer's `safeArea` and the shim's `safeArea` dispatch, and
  `contentSafeArea` in the boot config (`docs/sdk-v1.md` §3). A client with its own renderer sets the six variables;
  one with its own glue passes the boot field and the dispatch.
- Clients: draw the time on a backing over the corner of a widget with no caption below it (none, or one shown above
  it) and pass the band and its corner before the first render; change them only with the bubble's layout (a caption
  added, removed or moved above or below by an edit, "edited"), never per frame; taps on the time are yours.
- Dev app: the **time** control (`?time=corner|corner-rtl`) draws the time over the bubble and passes the reference
  band (24 px, a 64 px corner; `REFERENCE_TIME_CORNER` in `reference.ts`); `__tw.setSafeArea(area)` for any other.
- Conformance: `auto/36-safe-area` (env() with and without a fallback, other names, CSS.supports, the variables and
  `contentSafeAreaInset` agree, the corner's side, the UA layer's body padding) and `visual/17-time-corner` with its
  picture (the reference time drawn over the corner); a header's `safeArea` is the band the reference runner and the
  pictures use, and `channel.json` has two posts of each: `withoutCaption` and `captionAbove` (the test group posts
  them without a caption, then with the caption above the widget, `show_caption_above_media`, so a client draws its own
  time). New catalogue features `tw.content-safe-area` and `behaviour.time-corner`.
- Examples for the client teams: `corpus/safe-area` (README there), seven widgets that show what a client passed: a
  diagnostic of the six numbers with the band and the corner drawn from the variables, full-bleed cards whose bottom row
  runs into the band beside the time (one right-to-left: the corner is the interface's side), an unstyled note on the
  renderer's defaults, CSS as models write it, a canvas chart laid out from `contentSafeAreaInset`, and a widget posted
  three times: without a caption, with one below and with one above. `pnpm test:safe-area-corpus`
  (`docs/compat-report-safe-area.md`, both sides with the band), the feed's `corpus=safe-area`, the test group's **Safe
  area** topic (`publish-channel.ts --topic safe-area`, every post without a caption, the triple once more with a caption
  below and once with one above), and the iOS/macOS and Android demos list the group and
  pass the band with the time over the bubble. The dev app's reference column gets the band too (`?safeArea=` on the
  reference page). A pitfall they show: a widget that pads for the band itself sets `body { padding: 0 }`, or the
  renderer's default bottom padding comes on top of its own. `01`, `02`, `03` and `07` keep a plain value before each
  `env()` declaration and a fallback for each variable (`var(--tg-content-safe-area-corner-right, 0px)`), so they look
  right on a client before v0.4.0 too, laid out as with a caption below; `05` keeps the CSS models write.
- `scripts/conformance/publish-channel.ts` sends widgets with `sendWidget` on the beta Bot API
  (`https://api.telegram.org/beta`, the default `TW_BOT_API` until Bot API 10.4 is released), the size anchors as its
  `width` and `height`: the regular Bot API took an HTML document the test bot turned into a widget, and dropped
  `show_caption_above_media` (a caption-above post came out with its caption below).

**For the maintainers:**

- **The renderer prototypes are frozen** (dev tooling only, nothing changes for a client; `docs/decisions.md`, "The
  renderer prototypes are frozen"): Blitz and the plain litehtml core leave the demos' default builds (iOS/macOS:
  `TW_NATIVE_RENDERERS=YES` on `xcodebuild`; Android: `-Ptw.nativeRenderers=true`), the unit tests
  (`TW_NATIVE_RENDERERS=1`) and the default renderers of the web conformance runner and the feed's benches; the demos
  no longer need rustup or the litehtml CMake build.

**Known issues:**

- A client older than v0.4.0 drops every declaration with `env()` and has none of the variables: a widget that writes
  `env(safe-area-inset-*)` without a plain value before it, or a variable without a fallback, loses those declarations
  there (`corpus/safe-area/3-css/05-model-css` on the test group: no side padding, the Pay button at the bubble's edges).
  Widgets are not launched yet; a client that renders them ships with v0.4.0 or later.
- On the Shadow DOM renderer, `htmlbubbles-ported` `06-trip-lisbon/12-ana` and `09-code-review/07-leo-bare` differ from
  the reference (21.8 % and 11.2 %, alone too). They fail the same at v0.3.0 and were never in a committed report (the
  shadow report of that corpus is committed for the first time): not from this release, not investigated yet.
- The test group: the **Safe area** topic went out from 6a7f094 (before the rebase onto 9fcb726), with 07's
  caption-above post sent again through `sendWidget`; `01`, `02`, `03` and `07` changed since (the fallbacks) and wait
  for a repost, and `visual/17` and `auto/36` are not in the Visual and Auto topics yet. `show_caption_above_media`
  reaches a widget only through `sendWidget` (checked on the beta Bot API, test environment).
- The native demos pass the reference band and draw the time over the bubble, but nobody has looked at them on a
  simulator or an emulator yet.
- Test matrix: typecheck and unit (`showcase-3d`'s size check of `06-cat` as in v0.3.0) on this tag; on iframe and
  shadow compat 20, showcase 27, generated 40, common 32, gestures 28, the gesture, resize and safe-area corpora (8, 3,
  7), lifecycle 6, activation 4, htmlbubbles 108/116 on both (the known rows, plus the two above on shadow), run before
  the rebase onto 9fcb726 (which changes no code they run) with compat and the safe-area corpus again after it; the
  conformance set on iframe and shadow, 179 / 3 / 0 in the profile and 77 out neutralised (119 auto runs, light and
  dark). The native suites ran on this release's glue before the rebase (later commits and 9fcb726 change no framework
  code): Swift macOS 327 and iOS 27 328 with only `IntlBridgeTests.testPluralRulesMatchICU` failing (the OS's ICU, as
  before), Kotlin 28 JVM + 116 instrumented, C++ 78 tests and 110 attacks with 0 leaks; the iOS (iPhone 17, iOS 27.0)
  and Android demos build.

## v0.3.0 — release notes (2026-10-29)

Strict limits from the start, the renderer stress kit for the client teams, the tweb audit's five fixes completed on
every platform, Telegram links without a confirmation, and the bot demos on Bot API 10.4. Widgets meet harder limits:
every count and size limit is about twice what the largest real widget uses, and past a counted cap a widget stops on
its last render with the badge instead of losing the op (the table below). A client that uses the framework's glue and
renderer only updates; one with its own glue ports the stop (the validator's `limit` diagnostic is fatal), the
main-thread window (`mainThreadQuotaMs`) and the new defaults; one with its own renderer gets the finger rule
(a touch or pen `pointerdown` is no gesture) and the bitmap caps from the shared renderer core. The stress kit
(`packages/stress`, `CLIENTS.md`) is for the client teams: run it on your renderer on weak phones and send the run files
back; the paint limits of the next release (heavy boxes, the renderer's frame cost, the node cap) are set from them.
New conformance cases: `auto/28`–`30` (layouts the clients drew wrong) and `auto/31`–`35` (stop tests, judged by the
stop's reason); five new attack widgets, one of them with real touch input.

**Strict limits from the start** (default, every host; `docs/decisions.md`, "Strict limits from the start"). Widgets are
not launched yet, and a limit is easy to loosen later but breaks widgets when it is tightened, so every count and size
limit is now about twice what the largest real widget uses (measured over 346 widgets of the corpora and the
conformance set). Under `strict` (the default):

| Limit | Was | Now | Past it |
|---|---|---|---|
| QuickJS heap (`memoryLimitBytes`) | 64 MB | 8 MB | stops (as before) |
| Live timers (`maxTimers`) | 256 | 64 | `RangeError` in the widget (as before) |
| Live script animations | 1024 | 64 | not rendered (as before) |
| One text value (text node, canvas string, Path2D data) | 64 K | 32 K | **stops** (was: the op dropped) |
| The document's text (text, visible attributes, CSS strings) | 256 K | 48 K | **stops** (was: dropped) |
| An attribute or inline-style value | 4 MB | 8 K | **stops** (was: dropped) |
| Ops in one batch | 200 000 | 10 000 | **stops** (was: the batch dropped) |
| CSS rules / `@keyframes` in the document's stylesheets | none | 400 / 32 | **stops** |
| Canvas commands per canvas in one task | 400 000 | 10 000 for now (the stress kit's canvas ladder sets it) | `RangeError` in the widget; the host stops a batch with more |
| Web Storage per area and message | 16 KB | 4 KB | `QuotaExceededError` (as before) |
| Layout queries of one task | the task's slice, at least 100 ms (500 at boot) | 100 ms | stops (as before) |
| The host's main-thread work for the widget (`mainThreadQuotaMs`, new) | in the JS quota | 100 ms per second, apart from the JS quota | timers and frames deferred |
| A widget's canvases together, in pixels | none (the tweb audit: 4096² in the renderer) | 2048² | **stops**; the renderer still refuses past 4096² |
| A data: image, in pixels as its bytes say | none (the tweb audit: 4096²) | 1024² | the placeholder, as a refused URL |

A counted cap that is passed stops the widget like an infinite loop or running out of memory: the validator reports the
`limit` diagnostic with the reason (`Text limit exceeded: a text value of 40000 characters (max 32768)`, `Ops limit
exceeded: …`, `CSS limit exceeded: …`), refuses that batch whole and every later one, and the host dies with that
reason, so the bubble keeps its last render instead of a document with holes. The new main-thread window is a second
`CpuBudget` in the glue: the time the widget's layout queries block the renderer plus a modelled cost of the ops it sends
(25 µs per op, 2 µs per canvas command, `opsCostMs` in `cpu-budget.ts` and its twins), at most 100 ms per second; past it
deferrable tasks wait as under the JS quota (300 ms per second, unchanged, which now counts JS time only). `compat` keeps
the old caps and never stops (tests, tooling and the stress kit's measuring runs). A client using the framework's glue
only updates; hosts get the new option `mainThreadQuotaMs` next to `cpuQuotaMs`, and the harness prints the run's main-thread
cost and `STOPPED: <reason>` (and takes `dpr`, the widget's devicePixelRatio). The canvas cap counts what the validator
sees (a new canvas is 300 × 150; its `width` / `height` attributes and the shim's `r` command size it; removing it
refunds it) and stops with `Canvas limit exceeded: canvases of N pixels together (max 4194304)`; the image cap is
`MAX_IMAGE_PIXELS` in `url-policy.ts` and Swift's `URLPolicy.maxImagePixels`. The Swift host's `icon:` images
(`WidgetHost.Options.assetResolver`) are the host's, not the widget's: the validator sees a 140-character stand-in for
each token (a 2×2 PNG with this widget's nonce in its fragment) and the answer replaces it in the validated ops, so an
icon of 20 KB in an `<img src>`, a style or a stylesheet no longer stops the widget at the 8 K attribute cap; an answer
must be one of the profile's image types, and at most 2 MB of answers are restored per batch (`docs/protocol.md`,
`icon:` images; `IconExpansionTests`). CPU quota, the 500 ms task slice, the 50 000-node cap and the complex-script budget are
unchanged; the heavy-box count and the frame cost of the paint-limits draft wait for the stress kit's numbers from the
clients.

**Stress kit** (`packages/stress`, ships with the tag like the conformance set). 127 widgets in 19 load ladders that cross
every strict limit, in force or proposed, each reporting its own frame rate in `data-stress`; `CLIENTS.md`, the runner the
client teams write around their own renderer and run on a weak Android, an iPhone and a desktop (a measure pass with the
quotas out of the way, `compat`, and a production pass that records where each ladder stops); `run.schema.json`;
`tools/report.mjs`. The run files set the WebView cost model's A and B and decide the limits still proposed (16 heavy
boxes, 2000 nodes, the renderer's frame cost). Reference runner on the web renderers: `scripts/stress/run-web.ts`; the dev
app takes `mainThreadQuotaMs` and `memoryLimitBytes` from the URL for it.

**A background request made off screen waits for the widget to appear** (default, every host; `docs/sdk-v1.md` §2.14).
On 2026-10-28 Telegram's iOS and Android clients turned out to create the host off screen (`isActive` false at boot), so
a background request in a widget's first lines, the natural way to fetch fresh state, was refused (`NOT_ALLOWED`,
reason `hidden`); on Android the one a widget then made on `activated` failed with `BUSY` as well, because the shim
still counted the refused request as in flight while its refusal was on the way back: no background request ever went
out there. Now, where the message allows background requests (`backgroundQuery`), one made while the widget is hidden
is held in the glue, takes the one-in-flight slot, and goes to `onRequest` when `setVisible(true)` shows the widget, as
that appearance's request (the next one 5 s later); the widget's promise stays pending meanwhile, and a widget that is
destroyed or stops first never sends it. Taps are unchanged; without `backgroundQuery` a hidden widget's request is
still `NOT_ALLOWED`. The policy: `RequestGate.decide` returns `held` (`hidden` is gone) and `show(now)` returns whether
the held request goes out now (`sdk-policy.ts` and its Swift, Kotlin and C++ twins on the fixture's new
`requestGate` cases); the glue keeps the held request (worker host, Node harness, Swift, Kotlin, C++). Second, the shim
no longer answers `BUSY` itself: it forwards every request and keeps the unanswered ones by id, and the glue's gate is
the only judge of what is in flight, so a refused request never makes the next one `BUSY` (`BUSY` now arrives a task
later, with a `request-blocked` diag). The harness gains `--offscreen` (boot hidden, then appear, as the clients do).

**Telegram links open without a confirmation** (docs only; `docs/sdk-v1.md` §1 and §5.2, `docs/decisions.md`
"Telegram links open without a confirmation"). On `openTelegramLink(url)` the client opens the link as a tapped `t.me`
link in a message, with no confirmation of its own; its `t.me` handling keeps the confirmations it already has (joining a
chat, a proxy, a login). `openLink` keeps the confirmation with the full URL. Decision D8's wording in `profile.json`
says so (`docs/profile-v1.md` and the conformance catalogue regenerated); the framework's checks are unchanged.

**The authors' guide catches up with the Bot API and the bot demos** (docs only; `docs/widget-runtime-profile.md`).
A new section, "The bot's side (Bot API 10.4)": `sendWidget` and its fields, `sendSharedWidget`, the inline contents,
`widget_query` / `shared_widget_query` and `answerWidgetQuery` (answer every one, or the widget sees `TIMEOUT`),
`editMessageWidgetData` / `editSharedWidgetData` (an edit that changes nothing fails with `message is not modified`;
take it as done). The size is measured as the server does (Python's `zlib.compress(html, 9)` equals PHP's
`gzcompress`; the harness's Node count is a few dozen bytes off either way), instead of a 3800-byte margin. Two
lessons from the demos, also in the agent block: keep a revision in a shared state and ignore older polls, and draw a
forward of a widget that is not shared as a snapshot. `openTelegramLink` opens without a confirmation in the human
part too; testing on clients points to `corpus/bot-demos` and `scripts/bot-demos.ts`.

**Conformance: patterns the clients drew wrong** (`packages/conformance/auto/28-grid-percent-fill`, `29-grid-svg-marks`,
`30-svg-fill-box`; with the stop cases `auto/31`–`35` the set has 59 automatic widgets). Three bot demos drew wrong on the Telegram iOS and Android clients
and right in Chromium: a poll's 100 % row grew wider than the bubble with its count mid-row, a tic-tac-toe board broke up
after two marks, a chart's end dot's pulse shot off the chart. Their layouts are profile 1, so the widgets keep them and the set
gets a case per pattern, built as the demo builds it and measured with layout queries the client's renderer answers: a
`<button>` grid with a fill transitioned to 100 %, nine grid buttons with SVG marks put in after load, a circle scaled with
`transform-box: fill-box` before and after `cx`/`cy` move, with pixel stages of it static, animated and moved (the
clients' own renderers ignore `fill-box` and scale the shape about the view box, as a probe on both showed). They pass
on the web renderers (checks and stages), their checks also in macOS WebKit; `packages/conformance/README.md`,
"Patterns from real widgets", has what each checks.

**The authors' guide's agent block stands alone** (docs only; `docs/widget-runtime-profile.md`, `sandbox-node.ts
--guide`). The block for a model's system prompt was a 400-word summary that pointed to the repository's documents, which a
bot's model or a cloud agent never sees. It now carries what a widget needs (about 4K tokens): the bubble, the defaults
and every theme variable, what works, the whole out list (generated from `profile.json` by `profile-build.ts` between
`profile:agent` markers), input, every `TelegramWidget` member with its limits and request error codes, the state
model and the message contexts (forward, preview, viewer), storage, lifecycle and limits, and a short example; links to
the authors' documentation site are placeholders (`{docs}`) until it is published. Measured with 20 widget tasks written
by a model that saw only one of the blocks, each run through the harness: on the 8 tasks that invite out-of-profile
features, the old block's widgets used `line-clamp`, `backdrop-filter: saturate()` and `oklch()` in 3 (15 declarations
silently removed), the new block's none; only the new block's widgets use `viewer`, `isForward` and `isPreview`
(a tic-tac-toe written with the old block kept its player in `localStorage` and could not tell whose turn it was). The
guide's own out list no longer cuts a title at its first parenthesis (`@media (hover: hover), (pointer: …)` read as
"@media" out, the form controls list stopped at `input`), and it says that element ids are not global variables and
that a script's `new Image()` loads before it is inserted (v0.2.3).

**Fixes from the tweb audit, and what `main` on GitHub gained since v0.2.3** (default, every host):

**Swift renderer task-boundary regression coverage** (tests and documentation
only; no runtime, SDK, profile or bundle change). Eight `TaskRendererHostTests`
exercise the real host/engine/validator pipeline: synchronous and asynchronous
JSON delivery, asynchronous typed binary delivery, buffered startup, script
exceptions, CPU-limit stops, destruction during a pending query, and equivalent
operations/query answers with and without task callbacks. The native-only
presentation scope and shared-parity requirements are documented in
`docs/native-port-notes.md`, "Swift renderer task boundaries". Tests run in Debug;
no additional scrolling capability is enabled. Removing either callback makes
the binary-delivery regression fail; the tests do not change the runtime or
historical corpus inputs. Commands are in `CLAUDE.md`.

- The sculpted cat restores the full vertical angle saved by a mouse drag (−1.3 to
  1.3 radians) when its host recreates it. Previously reload clamped that angle to
  the narrower elevation-button range. This corrects the example only; the shared
  runtime, profile, SDK, native glue and generated bundles are unchanged.

- A profile-1 sculpted cat example adds X/Y mouse-drag rotation, zoom and tail pose.
  Cached camera trigonometry, adaptive mesh detail and throttled redraws keep the
  model within the widget CPU budget. Vertical touch gestures still belong to chat.

- Five `showcase/3d` widgets render projected geometry with profile-1 canvas 2D: a robot,
  camera assembly, shaded single planet, island and molecular viewer. Horizontal orbit, elevation,
  zoom and model controls persist per message; canceled drags restore the previous view.
  Theme state is cached between change events, so orbit redraws make no media queries.
  The planet uses smooth ray/sphere shading and supported fillRect color runs; all
  five documents satisfy 32,767-byte raw and 4,096-byte zlib limits.
  No renderer, profile, SDK, library or native side-channel changes.

- Bitmaps a widget makes the page hold are bounded. The shared renderer holds a widget's canvases to 4096 × 4096 pixels
  together (64 MB as RGBA), whether a size comes as an attribute or as the shim's `r` command: past it the size is not
  applied (the canvas keeps the one it had, a new one is 0 × 0) and a `violation` says so; four canvases of 16384² grew
  a browser by ~16 GB. `checkUrl` reads a `data:` image's size from its bytes (PNG's IHDR; GIF's logical screen, grown
  to its first frame where that frame reaches past it, as decoders grow the image; WebP's first chunk; JPEG's first
  frame header; a GIF's descriptor and a JPEG's header within 64 KB), decoding the body as fetch does (without the
  URL's `#fragment`), and refuses more than 4096²
  pixels (the placeholder, as for a refused URL): a few hundred bytes can claim 65535². With strict limits (above) the
  image cap is 1024² and the validator stops a widget whose canvases hold more than 2048² together. The browser picks a decoder by
  the bytes, not by the declared type, so bytes of none of the profile's formats (BMP, ICO or AVIF behind `image/png`,
  or no image at all) become `BROKEN_IMAGE` (`data:image/png;base64,`), which fires `error` as a broken image does
  (conformance `auto/02` checks that). The check runs for every element and style that names an image, and URLs that
  differ in their fragment alone are checked anew, so it reads no more than it needs, in place: a plain base64 body by
  windows of at least 4096 characters from where a read lands, any other decoded once from its start, a body that is
  no base64 tried once, the last image remembered; and a scan takes at most 1024 steps to a JPEG's frame header (a
  fill byte, a standalone marker or a segment each), 256 extensions and 4096 sub-blocks to a GIF's first frame (an
  image has tens; past them it is broken).
  Its worst bytes cost about one parse of the message that brings them: 64 KB of a JPEG's fill bytes took 4.5 ms an
  URL (500 of them ~2.9 s of `Validator.processOps`), restart markers 2.2 ms, empty segments 1.1 ms, 32 000 one-byte
  GIF sub-blocks 0.53 ms, 20 000 empty GIF extensions ~13 ms; now each is 0.02–0.06 ms, a frame header past
  62 KB of segments 0.015 ms (Node, the scan alone). A WebP's size is read where
  its chunk keeps it (a whole lossless 1 × 1 image is 28 bytes). The validator JS runs in QuickJS on native hosts, so it
  uses `atob` and `unescape`, not `TextEncoder`. Swift's `NativeValidator` (opt-in `binaryOps` on iOS and macOS), whose
  `URLPolicy.check` took an image attribute by its type alone (CSS goes through this JS), has the same check
  (`URLPolicy.checkedImage`: the same constants, scans and step caps, the body read as the bundle reads it, by UTF-16
  units, so its windows and parts fall on the same characters; the last image remembered behind a lock, validators run
  on several threads). Regression:
  `DataImageTests.swift` (every case of `url-policy-images.test.ts`, each URL also through the bundle's validator and
  the native model as `<img src>`, an `attr` op and an SVG `<image href>`, with the same ops and diagnostics; the
  corpora's images; `atob`'s edge cases). Not in `profile.json` (profile 1 is
  frozen): the two limits are in the authors' guide; rows in the profile tables are a maintainers' call. Shared renderer and validator JS: the iOS,
  Android and desktop bundles are rebuilt. Regression: `packages/core/tests/unit/renderer-canvas.test.ts`,
  `url-policy-images.test.ts` (every image of the corpora and the conformance set at its size, headers past the cap, a
  GIF's first frame past its screen and its extensions, a short lossless WebP, every encoding of the body, a fragment, a
  JPEG's offsets; each scan's worst bytes — a JPEG's fill bytes, restart markers and empty segments, a GIF's sub-blocks,
  a PNG or WebP with 64 KB after its header, a body that is no base64 — with the steps' cap, the characters decoded and the time against a parse of
  the message), and `validator.test.ts` now passes real images where it means an
  allowed one. Attack widget: `corpus/attacks/mem-huge-bitmaps.html` (a 69-byte PNG claiming 65535², a 2 KB one-colour
  PNG of 4100², the same header in a CSS `url()`, canvases of 8192² by their attributes and by the shim's `r`, each drawn
  on; `test:attacks` checks on both renderers that none reaches the page at its size and a 40 × 20 image and a
  300 × 150 canvas still do).

- A `timer` host call with an id that already has a timer replaces it: the glue cancels the old one first. It kept it
  running and overwrote its handle, so a shim told to reuse an id (its `JSON.stringify` replaced) kept native timers past
  `maxTimers`, still queued after `destroy()`. Changed in the web worker (`host/worker.ts`), the Node harness
  (`scripts/sandbox-node.ts`: a replaced id no longer counts against the cap) and the desktop core
  (`sandbox_engine.cpp`: dropping the `shared_ptr` did not cancel the `JSThread` timer), Swift (`SandboxEngine.swift`)
  and Kotlin (`SandboxEngine.kt`). A cancelled timer also leaves the engine thread's queue at once, whatever its id:
  Kotlin's `JSThread.Scheduled.cancel()` removes its callback from the `Handler`, and the C++ `JSThread` sweeps cancelled
  timers out once its queue has doubled since the last sweep; both kept them until their time, up to 24 h, so a plain
  `setTimeout` / `clearTimeout` loop grew the queue without bound (Swift's `Timer.invalidate` and the web's `clearTimeout`
  let go already). Regression: `shim.test.ts` (an id asked for again is not refused at `maxTimers`), the desktop
  `a_timer_id_asked_for_again_replaces_its_timer` and `pinned_thread_gives_deep_recursion` (10 000 timers of 24 h set
  and cancelled leave at most 128 queued, not 10 001), Swift `testATimerIdAskedForAgainReplacesItsTimer` and
  `testAReplacedTimerIdDoesNotCountAgainstTheCap`, Kotlin `SandboxEngineTest.aTimerIdAskedForAgainReplacesItsTimer`
  (one tick, not 1001; at most 8 messages queued, not 1008), and `corpus/attacks/timer-id-reuse.html` in `test:attacks`
  (1000 forged calls on one interval's id fire its callback once; `main`'s web worker fired it 1000 times).

- A finger's or a pen's press is a gesture from its `pointerup` on, not from its `pointerdown`: that press may still
  become the chat's scroll (it then ends in `pointercancel`), and a swipe that started on a widget let it call
  `copyText` and send a request from a `pointerdown` handler. The shared renderer marks such a `pointerdown` not
  `trusted`, nor a mouse event of a finger or a pen still down: the compatibility `mousedown` a pen's contact makes at
  once, its barrel button pressed or let go with the tip down (by the pointer whose buttons changed last, through
  `pointerdown`, a chorded `pointermove` or `pointerup`: a mouse pressed while a finger is held keeps its own
  `mousedown`); by the input's own `pointerType` (a mouse playing a finger keeps its press). The gesture counters no
  longer see that `pointerdown`, so a host starts a new gesture at the renderer's `gesture` claim and at any forwarded
  `pointerdown`, trusted or not (a second finger's has no claim) (`GestureCounter.pressBegan`), or a finger's release
  and click after a press that ended without a click (a drag) continued its spent gesture (`docs/decisions.md`, "User
  activation"). Changed in
  the web host, the Node harness, the desktop core, Swift (`WidgetHost.swift`: `pressBegan()` resets the state of
  `gesture(for:)` on the renderer's `gesture` message and on every forwarded `pointerdown`) and Kotlin
  (`GestureCounter.pressBegan()` in `UserActivation.kt`, called the same way from `WidgetHost.kt`), each with its tests:
  Swift `UserActivationTests` (a claim after a finger's drag begins a new gesture; a second finger's two taps open two
  links), Kotlin `UserActivationTest` (the counter) and `SdkTest.hostAnUntrustedPointerdownBeginsANewGesture` (through
  the real `WidgetHost` and WebView renderer). Known limitation, for
  the maintainers: the counters number presses, not pointers, so another pointer's `pointerdown` while a press is open
  starts a new gesture that press then continues. A mouse press that spent its gesture in its `pointerdown` handler, with
  a finger put down on the widget before the mouse is let go, gets a fresh one at its `pointerup`: two calls from one
  mouse press, both from the user's own input. `main` did the same (every trusted `pointerdown`, a second finger's too,
  opened a gesture that the mouse's release then continued); this change keeps it. A counter per pointer would close it
  (compatibility mouse events, which carry no `pointerId`, mapped to the pointer whose buttons changed last, as the
  renderer core does): a change of the model in all four counters and their tests. Authors: call privileged methods from `click` (or `pointerup`); the conformance
  set and the corpora already do. Regression: `packages/core/tests/unit/renderer-presses.test.ts` (a CDP touch tap, a
  pen press, a mouse pressed while a finger is held, a pen's barrel button pressed and let go after a mouse click while
  the pen is held, a mouse press), `user-activation.test.ts` and the desktop `user_gestures_are_numbered_per_physical_press`
  (a press after a drag) and `an_untrusted_pointerdown_begins_a_new_gesture` (a second finger's taps).
  Real touch input in the attack suite: `corpus/attacks/activation-touch-swipe.html` with CDP touch events
  (`test:attacks`, "a finger's press", both renderers: a vertical swipe that starts on the widget sends no request and
  copies nothing from its `pointerdown`, a tap still gets its `click`; on `main` the swipe did both). The dev app's phone
  mode and Playwright's clicks play a finger with the mouse, so the other suites never see a finger's `pointerdown`.

- The shared renderer lets a widget capture only a pointer whose trusted press began on it, and a press begun elsewhere
  and let go over the widget reaches it untrusted, so its release is no gesture. The shim checks that a pointer is
  pressed, but in the VM, which the widget can lie to: on the Shadow DOM renderer, where the widget shares its document
  with the chat, `setPointerCapture` took a pointer pressed on the chat's own control, which lost its click, and the
  widget got the release as a gesture (the same for a drag from the chat let go over it). The iframe and the WebViews
  never routed such a press to the widget; the bundles change for every platform all the same. A release is that of the
  pointer whose buttons changed last: a `pointerup`, or a button let go while another is held (a chorded release: a
  `pointermove` and a `mouseup`, no `pointerup`). A widget capturing its own press (`auto/23-gestures-b`) is unaffected. Regression: `packages/core/tests/unit/renderer-presses.test.ts` (the
  Shadow DOM renderer next to a control of the page, trusted mouse input, a chorded release). Attack widget:
  `corpus/attacks/activation-pointer-capture.html` (it tells the shim's check that the mouse is pressed on it and asks
  for it every frame; `test:attacks` presses a control of the page, which keeps its click, and nothing is copied; the
  widget's own press is still its own; on `main`'s Shadow DOM renderer the control lost its click and the widget copied).

- The CSS sanitizer checks the `light-dark()` branch it picks for the host theme. It put the branch in the function's
  place behind its own walk, which never visited it, so a `url()` there reached the renderer unchecked
  (`light-dark(url(/x.png), red)` became `url(/x.png)`): invariant 4 then rested on the renderer's own defences (the
  frame's and the WebViews' CSP; on the shadow renderer, the page's CSP). A nested `light-dark()` in the branch is
  resolved too. Shared validator JS: the iOS, macOS, Android and desktop bundles are rebuilt. Regression:
  `packages/core/tests/unit/validator.test.ts` ("checks the light-dark() branch it picks…"). Attack widget:
  `corpus/attacks/css-light-dark-url.html` (both branches, in a stylesheet, nested, under `@media`, `@supports` and
  `@keyframes`, through `var()`, `image-set()` and an escape, in a style attribute and as style properties set by script;
  `test:attacks` runs it in the light and the dark theme on both renderers; on `main` it fails on both, and the Shadow
  DOM renderer of the dev app, whose page has no closing CSP, fetched the canary).

- The `auto/23-gestures-b` conformance widget captures each pressed pointer, so its mouse-drag
  checks receive the release after leaving the small target instead of remaining pending. Gesture
  ownership rules are unchanged.

- Binary-vs-JSON corpus tests freeze the widget's wall clock, so the clock fixture compares identical
  dates across runs instead of failing when the two sandbox boots cross a second boundary. Production
  clocks are unchanged.

- The shared renderer preserves `movementX` and `movementY` across coalesced pointer and mouse moves,
  keeping the latest coordinates per event type and pointer. Previously it discarded all but the last
  move's delta within a frame, so controls that integrate movement lagged behind a drag. This applies to
  the web iframe and shadow renderers and the bundled iOS, macOS, Android and desktop renderers.
  Regression: `packages/core/tests/unit/renderer-moves.test.ts` checks real mouse events without a pointer ID,
  independent numbered pointers, and both frame and release flushes.

## v0.2.3 — release notes (2026-10-27)

Fixes from Fela after the Android beta (canvas arguments, images created by a script, building on Windows), conformance
pictures that follow widget profile 1, and what tmacos and macosswift pushed to `main` since v0.2.2, with native scrolling
made an experiment. No API change. A client that uses the framework's renderer (`RendererWebView` on iOS and Android, the
shadow renderer on the web) only updates. A client with its own native renderer must load an image the widget created
but never inserted (the renderer gets the node without a parent) and send its `load` / `error`, as the renderer core does.
Behaviour moves closer to a browser: a canvas call with a coordinate that is not finite draws nothing (it drew from 0),
and `img.width` / `height` of an inserted image are its rendered size (a layout query; cache it in a drawing loop).

**Native scrolling is an experiment** (`docs/decisions.md`, "Native scrolling: an experiment"). `WidgetHost.Options.nativeScrolling`
(Swift, tmacos) works only in DEBUG builds or with `TWIDGET_EXPERIMENTAL_SCROLLING`; a release build reports a
`native-scrolling` diag and keeps profile 1's clipping. Swift tests: `NativeScrollingTests`.

**Also from tmacos and macosswift** (on `main` since v0.2.2): the Swift renderer can be told when a widget task starts and
ends (`TaskRenderer`); the Apple Intl glue parses locale tags with `Locale.Components`, so a Unicode extension like
`ar-EG-u-nu-arab` no longer builds a broken `Locale` whose formatters trap the host; Swift's request completion gains
`notAllowed` (the server's `WIDGET_QUERY_NOT_ALLOWED` reaches the widget as `NOT_ALLOWED`; Kotlin, C++ and the web still
report it as `failed`); fixes to widgets in `corpus/htmlbubbles-ported` (`test:htmlbubbles` 108/116, in the known range;
`12-motion-lab/05-mo-bare`, rewritten, now differs from its picture by 5.9 %).

**The conformance set's pictures show what widget profile 1 draws** (the conformance set only, no framework change;
`packages/conformance`, `scripts/conformance`). The pictures posted next to the visual widgets are drawn natively by
Chromium, and two of them showed Chromium's own behaviour for an out-of-profile feature, so a client that drew what
the profile says differed from its picture: `visual/08-canvas-a` drew a checker with `createPattern`, which profile 1
leaves blank (a pattern draws nothing), and `visual/13-out-of-profile` hid the popover's red box, which profile 1
shows from the start as an ordinary block in the bubble (the `popover` attribute is dropped). The checker moves to
the out row of `visual/08-canvas-b` ("no checker strip"); 13 drops the attribute in reference mode, gets a caption for
the row, and its button sets its own text colour (a client's button style chose it before). A third picture was wrong
for another reason: `visual/06-css-animations` showed its frozen box at the start, not half-way, because the
screenshot finished the paused animation; the pictures are now taken with the animations as they are. The build
checks every picture of the visual set against the widget drawn through the sandbox and fails when one differs
(`scripts/conformance/picture-check.ts`, on the dev server it already starts for the heights; `data-illustration` marks
a stage whose picture shows motion, which the check leaves out). The other visual widgets and the client-SDK group
already matched. In the test group, `publish-channel.ts` re-sends the Visual topic from 06 on (the widgets 06, 07, 08a,
08b and 13 changed; 07 only gains the attribute, its picture stays).


**Canvas arguments are converted as a real context converts them** (default, every client;
`packages/core/src/sandbox/canvas.ts`). The shim forwarded a call's arguments as the widget passed them, and the
renderers read anything that was not the expected type as 0 or dropped the call: `fillText(4, …)` drew nothing on the
native renderers (the browser converted it on the web), `fillRect('1', '2', 3, 4)` drew at 0, 0, and a `NaN` became
`null` in JSON and then 0, so `fillRect(NaN, …)` painted from x = 0 where a browser does nothing. The text of
`fillText`/`strokeText` is now `String()`, coordinates are `Number()` (a call with one that is not finite does nothing,
in `Path2D` too), the `anticlockwise` flag of `arc`/`ellipse` is a boolean, `setTransform` takes a `DOMMatrix` or a
plain `{a…f}` / `{m11…m42}`, `isPointInPath`/`isPointInStroke` convert their point (not finite: false), and
`Path2D.roundRect` keeps its radii list. Unit test: `packages/core/tests/unit/canvas-args.test.ts`.

**A script's image loads before it is inserted** (default, every client; `packages/core/src/sandbox/capture.ts`,
`forms.ts`, `src/renderer/core.ts`). The shim mirrors only the nodes in the document, so `new Image()` (or a detached
`createElement('img')`) with a `src` never reached the renderer: no `load`, no `error`, `complete` false forever, and a
widget that waits for the image before `drawImage` drew nothing. Such an image now reaches the renderer with its `src`,
and the web renderer forwards its `load` / `error` (its root's listeners do not see a node outside the document); a native
renderer must load an element it has never inserted (TwidgetCore does). The image's state follows a browser's: `complete`
is true once the current `src` loaded or failed (a broken image too, with `naturalWidth` 0) and false again for a new
`src`; `img.width` / `height` are the rendered size, else the attribute, else the natural size (they were the attribute
only). The image stays a renderer node for the widget's life, as a detached canvas does. Conformance:
`auto/02-attributes-images-b` checks a `new Image()`, the rendered width and a broken image; unit test
`packages/core/tests/unit/image-state.test.ts`.

## v0.2.2 — release notes (2026-10-26)

One fix, for every client: haptics need a touch. No API change; a client on v0.2.1 updates the framework and gets the
new limit, as long as it plays haptics only from `onAction` (`WidgetAction.Haptic`) and never straight from the widget.

**Haptics need a touch** (a bug report from Telegram Android 13.2.0: a widget's `setInterval` called
`HapticFeedback.notificationOccurred` and vibrated the phone non-stop with no user action; `docs/sdk-v1.md` §2.6,
`docs/decisions.md` "Haptics need a touch"):
- While a finger or a mouse button is down on the widget and for 5 s after it is lifted, haptics pass under the rate
  limit (5 in a burst, 10 per second) as before. Outside a touch the first call opens a series: its first second
  passes, later calls are dropped (`haptic-limit`). The next series may start after a touch (one at any time later:
  a bot's answer, the end of an animation), or 10 s after the last one began without one. Hidden widgets and
  previews: nothing, as before. The numbers are named constants of the policy.
- `HapticGate` in `sdk-policy` and its three twins gains `press` / `release`; the fixture `hapticGate` has touch steps
  and six new cases (the bug's timer, a slider drag, one series after a tap, a second series within 10 s, hidden, a
  press without its release).
- The renderer core posts `{ type: 'release' }` when the primary press ends, next to the claim at `pointerdown`; every
  host feeds both to the gate (trusted renderer input only). A host whose renderer sends neither gets the no-touch
  rule. The renderer bundle is rebuilt.
- `corpus/attacks/haptic-timer-buzz.html` (the bug's pattern; `test:attacks` checks at most one second of haptics)
  and a conformance case (`auto/27-haptics-no-touch`, `visual/sdk/26-haptics-no-touch`).
- Checked: unit 711, typecheck, `test:sdk` 43, activation 4, attacks 126 on iframe and shadow (0 canary hits), the
  conformance build and the haptic cases on iframe and shadow; Swift macOS 263 and iOS 264 (only the known
  `IntlBridgeTests.testPluralRulesMatchICU`), Kotlin JVM 26 and instrumented 104; C++ 75 tests with Blitz and litehtml,
  105 attacks with 0 leaks on all three renderers.

## v0.2.1 — release notes (2026-10-26)

What v0.2.0 left for later: the full test matrix, the conformance cases for the widget's state and requests, and the
docs' wording. No API, wire or behaviour change: a client on v0.2.0 needs no code change, only the new conformance
cases to run.

- **Conformance for the widget's state and requests** (`packages/conformance`, `CLIENTS.md`): `auto/25-state-api-a`
  / `-b` (the rules that hold in every message: read-only state and its events, `isForward`, `chatType`, `isPreview`,
  the frozen `viewer`, the `request` and `copyText` errors, `watchPublicData` / `unwatchPublicData`) and
  `auto/26-context-{private,group,no-queries,copy,live-copy,preview}` (what the client passes in each kind of message
  and what a request may do with and without a tap); `visual/sdk/16-state-edit` (replaces `16-bot-edit`: an HTML edit
  no longer exists), `19-request-tap`, `20-forward-requests`, `21-network-marker`, `22-copy-text`, `23-preview`,
  `24-watch-public-data`, `25-group-chat-state`. A case header now says how to send it (`message`: the kind, the
  `inputMediaWidget` flags and state, group / forward / inline) and what the bot answers (`bot`: per request `data`,
  the `result` and the state edit); both reach the manifest and `channel.json`. `scripts/conformance/test-bot.ts`
  plays that bot over the Bot API (`--post` sends a case, the default mode answers `widget_query` and edits the state;
  `--self-test`). The web run: in 171 ✅ / 3 ◐ / 0 ❌ on iframe and shadow, all new cases pass.
- **`auto/23-gestures` in two parts** (`-a`: holds and double taps, `-b`: vertical drags and the edge strip): it was
  605 px tall at 320 px, over the 600 px a client clips at. `build.ts` now measures the automatic set through the
  sandbox and fails on anything over 560 px, as it already did for the visual set (`--no-heights` skips it).
- **A widget is fixed code plus changeable state** (docs only): the HTML is set when the bot sends the widget and
  never changes; the state changes, and only the bot writes it. `chatData` is the widget's **chat state** (one
  message, seen only in its chat, never forwarded) and `publicData` its **shared state** (wherever the widget is
  shown: a copy as of the forward, or one for all the messages and forwards of a shared widget); the widget reads
  them, learns of changes from `chatDataChanged` / `publicDataChanged` and asks the bot with `request`. The docs no
  longer call them "the bot's data" or "the bot's two strings". Renamed sections (formerly "The bot's data …"):
  `docs/sdk-v1.md` §2.13 "The widget's state and where it is" and §5.11 "The widget's state, requests and copies";
  "The widget's state and requests" in `docs/tgw-format.md`, `docs/integration.md` and `docs/client-checklist.md`
  §d. Also reworded: `docs/decisions.md`, the authors' guide and its agent block, the I1, `tw.*` and `behaviour.*`
  texts in `profile.json` (`docs/profile-v1.md` regenerated). API and wire names, behaviour and limits are unchanged.

**Known issues:**

- The full matrix ran on v0.2.0's code (this release changes no shipped code): typecheck, unit 710, `test:sdk` 43,
  activation 4, attacks 124 on iframe and shadow (0 canary hits), compat 20, showcase 21, defaults 6, generated 40 and
  common 32 on iframe and shadow, gestures 28, lifecycle 6, the gesture and resize corpora; htmlbubbles 109/116 (the
  known screenshot-only and timing cases); Swift macOS 261 and iOS 262 with only `IntlBridgeTests.testPluralRulesMatchICU`
  failing (the OS's JavaScriptCore, as before); Kotlin JVM 26; C++ 73 tests with Blitz and litehtml, 104 attacks with 0
  leaks on all three renderers; the iOS and macOS demos build and the macOS demo's attack autorun passes. Android's
  instrumented tests last ran on the same glue before the merge with main (102/102).
- `scripts/conformance/test-bot.ts` has not run against the server yet; the Bot API shape of an inline result that sends
  a widget is not in the server's spec, so the script assumes one (marked in the file).
- The iOS/macOS and Android demo apps do not show requests (no mock bot there).

## v0.2.0 — release notes (2026-10-26)

A widget is now fixed code plus changeable state, and it can ask the bot. This is layer 233 at the launch of
widgets (the server's final spec: `docs/tgw-format.md`, "The bot's data and requests"). SDK and profile stay
version 1: the additions are part of SDK v1 at the launch, with no version of their own.

**What a client does** (the details are below, in `docs/integration.md` "The bot's data and requests" and in
`docs/client-checklist.md` §d):

1. Read the new fields of `messageMediaWidget` (`is_copy`, `can_query`, `background_query`, `public_data`,
   `chat_data`, `widget_id`) and pass them to `WidgetHost` as `data` and `context`: the server's flags as they
   are, the chat type (`null` in a forward), `viewer` (the account's own, only with `can_query`), `preview` for an
   inline result before it is sent.
2. Call `setData` on `updateMessageWidgetData` and when a poll of `messages.getWidgetPublicData` brings a new
   shared value. Poll only widgets on screen that called `watchPublicData()` and have a `widget_id`, one message
   per `widget_id`, at the `period` the server returns (30 s by default, 5–3600 s).
3. Answer `onRequest` with `messages.getBotWidgetQueryAnswer` (the server allows 5 requests in 5 s per user and
   bot), `onCopyText` with a copy and a toast that shows the text, and show the network marker ("a tap may send
   data to the bot") exactly where the message has `can_query`.
4. Show an inline result (`botInlineMessageWidget`) as a widget in preview mode.

This release goes out ahead of the full test matrix so that clients can start; the remaining checks and the
conformance cases for the new API follow in 0.2.1 (Known issues).

The widget's HTML never changes after sending; only data changes (`docs/decisions.md` "The bot's data and
requests", decision I1; `docs/sdk-v1.md` §2.13–§2.15, §5.11; the server's spec in `docs/tgw-format.md`, "The bot's
data and requests"). Part of SDK v1 at the launch, no version of its own: profile 1 stays, a widget written without it
is part of layer 233 from the launch; a client that passes no flags gets widgets whose requests are refused. The dev
app's mock bot and the harness play the bot for local work.

**For widgets** (default, every client):
- `TelegramWidget.chatData` and `publicData`: the bot's two strings (text of at most 1024 bytes each, read-only,
  `null` for none); `onEvent('chatDataChanged')` and `onEvent('publicDataChanged')` when they change (both, chat
  first, when one change touches both), no restart. `chatData` belongs to one original message (in a group every
  member sees the same) and never reaches a forward; `publicData` does: a snapshot, or a value shared with the
  original that keeps changing.
- `isForward`, `chatType` (`private` | `group` | `supergroup` | `channel`) and `viewer` (Mini Apps' `WebAppUser`: `{ id, first_name, last_name?, username?, is_premium? }`, the client passes the account's own; a field that breaks its rule is left out, `data-rejected`); a forward
  is told nothing about the chat it is in (`chatType` null in every copy: the bot must not learn where its widget was
  forwarded); the widget knows its viewer only where it may send requests (`can_query`), originals included.
- `TelegramWidget.request(data)` (1–64 bytes): a callback button inside the widget; resolves `{ result }` (text of at
  most 1024 bytes), rejects with `error.code` (`NOT_ALLOWED`, `BUSY`, `RATE_LIMITED`, `TIMEOUT`, `FAILED`,
  `INVALID_ANSWER`). Where it may ask is the server's per message, for a widget the bot sent with `allow_queries`: on
  a tap in originals (private chat, groups, via the bot) and in forwards of widgets sent with `fwd_allow_queries`;
  without a tap only in the original in the private chat with the bot, while visible: one when the widget appears on screen, then one per 5 s. One tap, one
  call, shared with `openLink`, `openTelegramLink` and `copyText`; one request in flight. The answer carries no
  data: the bot's edits arrive as the events, and a widget that needs them at once gets them in `result`.
- `TelegramWidget.copyText(text)` (1–256 UTF-16 code units) on a tap; the client shows what it copied.
- `TelegramWidget.watchPublicData()` / `unwatchPublicData()`: the widget asks for live shared data (no tap, idempotent);
  the client polls a shared widget's `public_data` only while it watches and is on screen, at the server's period
  (5–3600 s, 30 s by default; `cache_time` 0 sets it back to 30 s); every other widget gets `public_data` with the message. Hosts report it like the
  resize anchor: web `onWatchPublicData(on)` and `WidgetHost.watchesPublicData`, Swift, Kotlin and C++
  `onWatchPublicData` and `WidgetDiagnostics.watchesPublicData`; the bridge call `watchPublicData [on]` is checked in
  `sdk-policy` and its twins (fixture `bridge`, `previewMethods`). The dev app's mock bot polls only a watching widget
  (`edit by id`, `shared`, the poll period in its panel); `corpus/interactive/01-poll` watches its counts.
- `TelegramWidget.isPreview`: an inline result in the list, before it is sent, runs as a preview: its data, no input,
  no requests or other actions (only `ready` and `setResizeAnchor`), no chat type or viewer, Web Storage that is never
  kept.
- The HTML of a sent widget is fixed: `behaviour.message-edit` now says so.

**For clients** (every host: web `WidgetHost`, Swift, Kotlin, C++, the Node harness; `docs/integration.md` "The
bot's data and requests", `docs/client-checklist.md` §d):
- New options `data` (`{ chatData, publicData }`) and `context`: the server's flags from `messageMediaWidget`
  (`isCopy`, `canQuery`, `backgroundQuery`), the chat type and `viewer`; no flags, no requests. `setData(data)` (the
  full state) on `updateMessageWidgetData` (in a supergroup with the channel's pts) and when polling
  `getWidgetPublicData` (one visible carrier per `widget_id`, one message per call, at the object's `period`)
  brings a new shared value, forwards included. The client shows a network marker ("a tap may send data
  to the bot's server") exactly where the message has `can_query` (`docs/integration.md`). `onRequest({ data },
  complete)`: `getBotWidgetQueryAnswer`, complete once with `{ result }` or `timeout` / `rateLimited` / `failed`.
  `onCopyText(text)`: copy and show the text. `context.preview` for an inline result before it is sent (the host
  drops its input and every bridge call but `ready` / `setResizeAnchor`, and its storage commits). New types per
  platform in `docs/sdk-v1.md` §4. Without `onRequest`
  every request is refused in the widget.
- New diagnostics: `request-blocked`, `answer-rejected`, `data-rejected` (also for a NUL: the strings are the
  server's text fields).
- The checks are in `sdk-policy` and its three twins on `tests/fixtures/sdk-policy.json` (sections `widgetData`,
  `viewer`, `chatType`, `answers`, `requestGate`, `requestRefusals`; `request` and `copyText` in `bridge`; the
  gate reads `canQuery` / `backgroundQuery` and resets its interval with `show()` when the widget appears);
  `copyText` joins `PRIVILEGED_METHODS`. The host → sandbox `bridge` dispatch is back (the bot's data and the
  answers), deferred under the CPU quota like `theme`.

**Dev tooling:** the dev app's **Bot (mock)** panel (`apps/web/src/mock-bot.ts`): the kind of message and so its
flags (private, group, via-bot, no-queries, live-copy, copy, preview: `WIDGET_MESSAGE_KINDS` in `sdk-policy.ts`) with
the network marker in the bubble's header where it has `canQuery`, the chat type, the viewer (`?message=…&chat=…&viewer=…&botmode=…`), the two strings and the bot's edit, built-in bots chosen by the
widget's `<meta name="tw-bot">` (echo, poll, tic-tac-toe, ticker, promo), manual answers and failures, a "Copied: …"
toast; `window.__tw.bot`, `__tw.setContext`. `corpus/interactive`: a poll, tic-tac-toe against the bot, a live ticker
on background requests, a promo code with `copyText`. The harness: `data`, `context`, `onRequest`, `{ data }` script
steps, `RunResult.requests` / `copiedTexts`; from a shell `--message` (preview too), `--copy`, `--can-query`, `--background-query`,
`--chat-type`, `--viewer id[,first,last,username,premium]`, `--chat-data`, `--public-data`, `--answer`, `--answer-error`, `--set-data`;
`twidget-run` takes the same flags. `corpus/attacks` has five `request-*` files (no request without a tap, a forged
wire, a background flood, two in flight, a copy).

**Also in this release** (Arseny, 6ca5360): **Element members stay their element's** (default, every client; `packages/core/src/sandbox/forms.ts`). linkedom
has no class of its own for `form`, `dialog`, `output`, `fieldset`, `label`, `progress`, `meter` and some others:
they share HTMLElement's prototype, so the shim's members for one of them were on every element. An inline
handler (`with (element)`) then found them first: `<button onclick="reset()">` called the button's form
`reset` instead of the widget's `reset()` (and `show()`, `showModal()`, `submit()`, `value`, `length`, `form`
likewise); output's `value` also overwrote progress' and meter's (`progress.value` read `""`). On the shared
prototype such a member is now looked up by the element's local name, and the members of other elements are
unscopable in an inline handler's scope. `'reset' in button` is still true (the prototype is shared), its value
`undefined`. Unit test: `packages/core/tests/unit/element-members.test.ts`. Found with a widget sent through
Telegram Android, where the Reset button did nothing.

**Known issues:**

- Released ahead of the full test matrix. On this tag: typecheck, unit, the profile check, `test:sdk`, `test:activation`,
  `test:attacks` (iframe and shadow), compat, showcase and defaults; the native suites (Swift, Kotlin, C++) ran on the
  same glue before the merge with main. Not run yet: generated, htmlbubbles, the conformance web run, the demo autoruns,
  the C++ build with Blitz and litehtml; they follow in 0.2.1.
- `packages/conformance` has no case for the widget's state and requests yet, and `visual/sdk/16-bot-edit` still
  tests an HTML edit, which the server no longer allows (to be replaced by a state-edit case in 0.2.1).
- The docs still call `chatData` / `publicData` "the bot's data" in places; 0.2.1 rewords them as the widget's
  chat state and shared state (the code is fixed, the state changes).
- The iOS/macOS and Android demo apps do not show requests (no mock bot there).

## v0.1.3 — release notes (2026-10-24)

Vertical drags are always the chat's, a widget chooses which edge of its bubble stays put when it
grows, and the payload is zlib, not gzip. Profile, SDK and protocol versions stay 1: `setResizeAnchor` is an SDK v1 addition
widgets detect with `typeof`.

**The payload is zlib, not gzip** (server change, 2026-10-24; every client must follow): the server
compresses the bot's HTML with PHP `gzcompress($html, 9)`, so `messageMediaWidget.payload` is a zlib
stream (RFC 1950: `78 DA`, raw deflate, Adler-32) instead of gzip. Clients inflate it instead of
gunzipping: `DecompressionStream('deflate')`, `java.util.zip.Inflater()`, zlib's `uncompress`; Apple's
`COMPRESSION_ZLIB` is raw deflate and Qt's `qUncompress` wants a length prefix (`docs/tgw-format.md`,
"Inflating the payload"). The limits stay 4 KB compressed and 32 KB of HTML; the same widget counts
about 12 bytes less (the shorter framing) and level 9 saves a few more. The size counter follows:
`WidgetSize` and `MINI_HTML` say `zlib` instead of `gzip` (`src/shared/widget-size.ts`), the harness
counts zlib at level 9 (`size: 2134 B zlib / …`), the dev app `CompressionStream('deflate')` (level 6, a
few bytes over), the conformance gate and the test-group publisher zlib at level 9 (`zlib` in
`manifest.json` and `channel.json` replaces `gzip` / `gzip6`).

**Vertical drags are the chat's** (default, every client; `docs/decisions.md` "Vertical drags are
the chat's", decision G2; `docs/sdk-v1.md` §5.5). A widget can claim only horizontal drags: a
vertical drag over a widget always scrolls the chat, whatever `touch-action` says, and the widget
gets `pointercancel`. `auto` is unchanged; `pan-y` gives the widget horizontal drags (unchanged);
`pan-x` claims nothing any more; `none` gives the widget horizontal drags, holds (`contextmenu`)
and taps. The first move past the 10 px slop still decides for the whole press, also after a hold
the widget won; double taps, the 20 px leading strip and the mouse are unchanged. What is lost:
2D joysticks, drawing, map panning, vertical sliders and pickers, up/down swipes.

- The policy, four copies and the fixture: `claimOf` never claims `y` (the field stays in
  `GestureClaim` / `WidgetGestureClaim` on every platform, always false, so v0.1.2 code builds),
  and `GestureArbiter` gives every vertical touch drag to the chat, even for a claim built by hand
  with `y` (fixture: a `down` step may carry a `claimed` object). Every host reports `y` false
  whatever the renderer sends; the Blitz and litehtml prototypes, which send only `wants`, claim
  nothing.
- The renderer core gives the engine every `touch-action` widened to allow vertical panning
  (`none` → `pan-y`, `pan-x` → `pan-x pan-y`; `src/renderer/touch-action.ts`), so a browser on a
  touch screen (the web client on a phone, the feed with touch emulation) scrolls the page over
  any widget; the claim and the widget's `getComputedStyle` read the widget's own value (kept in
  the custom property `--tw-touch-action`).
- Android: `RendererWebView` keeps its parent from intercepting only for a hold claim, and only
  until the press leaves the slop (vertically, the list takes it); a press in the leading strip
  claims nothing there either.
- The harness's hint for a vertical drag says "vertical drags always scroll the chat; a widget
  cannot claim them"; its claim line no longer offers vertical drags. The dev app's and the feed's
  bubble follow the policy; so do the iOS/macOS and Android demo bubbles.
- `corpus/gestures` (the examples the decision was made with, 06d909f..6b93031 from the gesture
  examples branch) now shows what the rule removed: the `-xy` games behave like their `-x` twins,
  the wheel picker can no longer turn (`corpus/gestures/README.md`). `pnpm test:gesture-corpus`, the
  feed's `corpus=gestures`, the test group's Gestures topic (`--topic gestures`, opt-in).
- Conformance: `auto/23-gestures` adds vertical drags on `none` and `pan-x` (`pointercancel`);
  `visual/sdk/12-drag-gestures` counts the presses the chat takes over blue.

**`TelegramWidget.setResizeAnchor(edge)`** (`docs/sdk-v1.md` §2.12, §5.6; decision R1). `'top'`
(the default): the bubble grows and shrinks downward, the content below moves; `'bottom'`: it grows
upward, its bottom stays and the content above moves. Callable at any time, it applies to the height
changes that follow; anything but exactly `'top'` / `'bottom'` throws `TypeError`; no tap needed.
Detect it with `typeof TelegramWidget.setResizeAnchor === 'function'`.

- A checked bridge call (`sdk-policy`, four copies and the fixture), not an action: the shim sends
  only changes, the glue keeps the latest value, and nothing reaches `onAction` / `onBridge`. Every
  host reports it with the height: web `onResize(height, anchor)` and `WidgetHost.resizeAnchor`;
  Swift `WidgetDiagnostics.resizeAnchor` (`WidgetResizeAnchor`); Kotlin and C++
  `WidgetDiagnostics.resizeAnchor` (`"top"` / `"bottom"`); the harness's `RunResult.resizeAnchor`
  and a `resize anchor:` line. `checkBridgeCall` returns a bridge-call type that carries either an
  action or the anchor (TS `BridgeCall`; Kotlin `BridgeCall`, which `WidgetAction` implements; C++
  `sdk::BridgeCall`; Swift `SdkPolicy.Action`); `WidgetAction` itself is unchanged.
- The client's part (`docs/integration.md`, "Bubble sizing"): when an auto height changes on screen,
  the list keeps the anchored edge where it is, also from a cached height and after a re-creation;
  a bubble off screen keeps the visible content in place; during a height animation the old pixels
  stay aligned to the anchored edge. The web dev app (a bottom-anchored bubble sits at the stage's
  bottom and grows upward; the stats panel shows the anchor) and the feed (its own scroll
  compensation, browser scroll anchoring off, `corpus=resize`) do it, and so do the iOS demo (the
  single-widget screen and the chat feed, which got a `custom` widget set) and the Android demo
  list. Swift also reports the anchor on `WidgetBubble.resizeAnchor`, set before `onHeightChange`.
- Examples: `corpus/resize` (README there): a delivery FAQ on the default anchor, a support thread
  that grows upward (`'bottom'`, kept in `localStorage`), hotel results that grow downward (`'top'`,
  said explicitly). `pnpm test:resize-corpus`, the dev app's `resize/` group, the test group's
  Resize topic (`--topic resize`, opt-in).
- Conformance: `auto/24-resize-anchor` (the method exists, takes `'top'` and `'bottom'`, throws
  `TypeError` on anything else) and `visual/sdk/18-resize-anchor` (the tester sees which edge
  stays). The profile lists `tw.resize-anchor` and `behaviour.resize-anchor` (257 features).

**Conformance**: also `auto/09-transforms-effects-a` checks `translateX(%)`, `calc()` inside
`translateX()` (also with `*` and nested parentheses) and a transform set from script;
`visual/05-transforms-effects-a` draws the same four for the reference picture. On the web
prototypes: Blitz draws them but its layout ignores transforms, and it does not apply a transform
set through a transition; litehtml draws no transform.

**Docs**: no `.tgw` development format (Arseny): the docs drop the gzipped-HTML document a client
could show as a widget behind a debug flag. Widgets come only as `messageMediaWidget`; testing goes
through real layer-233 messages (`docs/tgw-format.md`, "Testing"). No code read `.tgw`; clients
drop their own path.

**Known issues**

- Native demos: the old pixels are not pinned to the anchored edge during a resize. On iOS the
  bubble and the WebView resize together and WebKit may show its previous picture top-aligned for
  a moment; the Android list applies the scroll correction after layout (one frame late, clamped
  at the list's ends).
- iOS demo: iOS 26's full-width back swipe takes horizontal drags over the widget (outside the
  arbiter, as in v0.1.2).
- `IntlBridgeTests.testPluralRulesMatchICU` fails on the iOS / macOS 27 simulators: the OS's
  JavaScriptCore now gives es / bg ordinal `one` where the Swift plural rules say `other` (not from
  this release; iOS 26.5 is not installed on the build Mac, the runs used iOS 27.0).

## v0.1.2 — release notes (2026-10-22)

Who gets a press: double taps over the widget are the widget's, and for holds and drags CSS
`touch-action` is the claim, decided by one shared policy on every client and in the sandbox (`docs/decisions.md`, "Gestures: `touch-action` is the claim";
`docs/sdk-v1.md` §5.5). Profile, SDK and protocol versions stay 1; a widget that worked on a
client still works there, and a widget that only worked in the sandbox now fails there too.

**The rule** (replaces "double tap and long press always stay with the bubble"):

- Taps and double taps over the widget are always the widget's, on every element: a double
  tap arrives as both clicks and then `dblclick`. The double-tap reaction stays on the bubble
  outside the widget.
- `touch-action: auto` (the default): the bubble wins: a hold opens the message menu, a
  horizontal swipe is reply or back, a vertical one scrolls the list; the widget gets
  `pointercancel` for the press the bubble takes.
- `pan-y` / `pan-x`: drags on the other axis are the widget's; holds stay with the bubble.
- `none`: everything over that element is the widget's: drags on both axes and holds (it
  gets `contextmenu`). `touch-action` on a container covers its children, as in CSS.
- The leading 20 px of the widget and the bubble outside it never take a claim. With a
  mouse: a right click is the menu or `contextmenu` by the same claim; clicks, double clicks,
  holds and drags are the widget's.
- Rule of thumb for authors: a button you hold → `touch-action: none` on it; horizontal
  sliders and carousels → `pan-y` (`docs/widget-runtime-profile.md`).

**Framework**

- The shared policy: `GestureArbiter`, `claimOf` and the thresholds (10 px slop, 500 ms
  hold, 300 ms / 40 px double tap, 20 px edge strip) in `src/shared/gesture-policy.ts`,
  `GesturePolicy.swift`, `GesturePolicy.kt` and `twidget/gesture_policy.h`, all four checked
  against `packages/core/tests/fixtures/gesture-policy.json`; public on every host, so a
  client drives its recognizers from it.
- The renderer's claim message carries `hold` (only from `touch-action: none`):
  `onGestureClaim` in Swift and on the web gets it; Kotlin and C++ get
  `onGesture(WidgetGestureClaim)`, and their `onGestureClaim(x, y)` is a deprecated forward.
  The Android WebView renderer keeps its parent out for `hold` too.
- The renderer sends the widget its own `dblclick` (every double tap) and `contextmenu` (a
  hold the widget claims), from the policy; the engine's own never reach it, so every WebView
  and native renderer delivers them alike. `input.dblclick` is in the profile, and
  `input.long-press` on `touch-action: none` (decision G1); `behaviour.gesture-claim`
  reworded.
- The web host can play the bubble itself: `WidgetHostOptions.bubbleGestures` (and
  `setBubbleGestures`) has the renderer cancel the presses the bubble takes and report them
  in `onBubbleGesture` (menu, swipe, scroll, back); `touch` arbitrates a mouse as
  a finger (the widget sees `pointerType: 'touch'`). New protocol messages `arbitrate`
  (host → renderer) and `bubble` (renderer → host), `docs/protocol.md`.

**Tooling for authors and agents**

- The dev app's bubble plays a phone client by default (`gestures`: phone, desktop, off;
  `?gestures=`): a hold on `auto` gets `pointercancel`, a horizontal drag is swipe-to-reply,
  with a label over the bubble and a Gestures panel; every Playwright suite runs on it.
  The feed demo does the same. A bubble-width control: presets 200, 320, 360, 390, 430,
  600 and a free width (`?width=`, `__tw.setWidth`).
- The Node harness lays the widget out and presses it: `sandbox-node.ts widget.html
  --width 360 --tap x,y --hold x,y[,ms] --double-tap x,y --swipe x,y,dx,dy --right-click x,y
  [--desktop] [--json]` runs the renderer core in headless Chromium on the sandbox's ops
  and reports per press the element, its claim, the policy's outcome, the events the
  widget received and a hint ("the hold went to the message menu: set touch-action: none
  on button#fire"); also the content height at that width.
- Native demos: the iOS/macOS and Android `ChatBubble` drive recognizers from the policy
  (the menu, swipe-to-reply, back as labels) and cancel the widget's touch.

- **Size as the server counts it**: the harness prints `size: 2134 B gzip / 9.8 KB raw (limit
  4096 / 32768)` (gzip at level 6, PHP `gzencode`'s default) with a warning above 3800 B and an
  error past a limit (`INPUT_MEDIA_WIDGET_TOO_LARGE`); the dev app's stats panel shows the same
  row in the warning and error colours (`src/shared/widget-size.ts`). Node's and Chromium's zlib
  count up to a few dozen bytes more than GNU gzip on real widgets: the numbers err on the safe
  side.
- **The authors' guide** `docs/widget-runtime-profile.md` ("Writing widgets"), refreshed for
  v0.1.1 and v0.1.2 as the document for widget authors and their models: mini-HTML limits and
  how to measure them, the bubble (size rule, the bubble as the canvas, the UA defaults and text
  tokens, opt-in text selection), the gesture rule and its rule of thumb, what works, the
  `TelegramWidget` API in brief, limits, how to test, and a block for a model's system prompt
  (`sandbox-node.ts --guide` prints it). Its out-of-profile list is generated from
  `profile.json` by `profile-build.ts` and checked by `profile.test.ts`. Linked from the README
  and `docs/tgw-format.md`; CONTRIBUTING.md asks a change visible to authors to update it.

**Conformance**: `auto/23-gestures` (a hold on `none` is the widget's with `contextmenu`, a
hold on `auto` ends with `pointercancel`, a double tap on `none` and on `auto` gets both
clicks and `dblclick`, a drag from the leading 20 px is back, one from further in is the
widget's); `visual/sdk/12-drag-gestures` rewritten for testers; `auto/16-sdk-taps-b`
and `auto/17-drag-hover` updated. Runners perform the actions through the real bubble
(`packages/conformance/CLIENTS.md`).

**Tests**: `tests/gestures.spec.ts` (`pnpm test:gestures`, iframe and shadow),
`packages/core/tests/unit/gesture-policy.test.ts` and `harness-gestures.test.ts`, the
fixture on iOS, macOS, Android (JVM and instrumented) and the desktop core.

**Known issues**

- Native renderers behind the seam (Blitz, litehtml) report only `wants` and do not send
  `dblclick` / `contextmenu`: holds and double taps reach them as plain pointer events.
- The renderer and native hosts arbitrate left-to-right unless told otherwise
  (`arbitrate` `rtl` on the web); a right-to-left client must mirror x for its own
  recognizers.
- Android's `GestureWebView` keeps a `none` element's vertical drag that starts in the
  leading edge strip (the policy gives it to the list); the client's own arbiter decides.
- iOS (WKWebView): `cancelsTouchesInView` does not cancel WebKit's touch; the demo toggles the
  web view's own recognizers once to get `pointercancel` (`docs/sdk-v1.md` §5.5). A hold on
  `none` shows WebKit's lift preview over the element while the widget gets its `contextmenu`;
  `RendererWebView` does not suppress it yet. On macOS the demo only labels the menu: the mouse
  press is the web view's before the claim arrives.

**Also in this release**

- **Intl on Windows**: `IcuIntl` answers all 166 cases of `intl-cases.json` as V8 does (`icu_intl_matches_v8`):
  `currencyDisplay`, `signDisplay`, significant digits, rounding half away from zero, the locale's minimum
  grouping, `hourCycle`, the date options it resolves, plural rules on the formatted number, list types and
  styles, and collation by `case`. What Windows 10 1903's ICU lacks is looked up at run time: on such a system a
  plural rule with digit options sees the bare value, and a list that is no long conjunction is `BasicIntl`'s.
- **Hidden timers keep their order** (default, every host): a hidden widget's timers wait at least 1 s, and
  every delay under it used to become the same 1 s, so they ran in the order they were set (`setTimeout` 20,
  10, 0 ran 20, 10, 0). The hidden delay is now `max(delay, 1 s + delay / 10)`: still about 1 Hz, in the
  order of the delays. Web worker, Swift, Kotlin and C++ glue; the Node harness fires a round's timers by
  delay too (it ran them in the order they were set, hidden or not).

## v0.1.1 — release notes (2026-10-20)

A clarification of widget profile 1, opt-in text selection, live collections and Intl fixes,
and the tooling around the test group; profile, SDK and protocol versions stay 1, and every
widget valid under v0.1.0 stays valid.

**Default widget styles** (`docs/sdk-v1.md` §2.4, `docs/decisions.md`, "The renderer's
defaults"): a widget without CSS now looks native on every client.

- The renderer's user-agent layer (`tables.uaStylesheet` in `packages/core/profile.json`,
  `@layer tw-ua`, every selector in `:where()`, nothing `!important`, so any author rule
  wins) covers the root and the text elements, not only `<button>`: `html` in
  `--tg-theme-text-color` and the client's text font; `body` with the client's padding token
  as its margin (inside the reported height), so `body { margin: 0 }` still goes edge to
  edge; headings, paragraphs, lists, `dl`, tables, `hr`, `pre`/`code`, quotes, figures,
  images and inline elements get explicit sizes and margins in `em`, the same in WebKit,
  Chromium and litehtml. Before, each engine's own stylesheet showed (Times 16 px and an
  8 px margin in a WebView).
- The renderer paints no background: the client's bubble under the transparent renderer,
  drawn in `bg_color`, is the canvas, and a root or body background a widget sets covers the
  whole bubble as in a browser. The shadow-DOM renderer, which has no canvas, paints that
  background on its host element. The widget view fills the bubble with no padding of the
  bubble's own (the widget's body margin is the inset), clipped at the bubble's corner
  radius; the dev app, the web and iOS chat feeds and the native demos draw their bubbles
  that way now.
- **Text tokens** next to the button tokens, on every host: `WidgetTheme.text` with
  `fontSize` (the chat's text-size setting, 8–64 CSS px), `lineHeight` (1–3) and
  `padding` (one to four lengths) → `--tg-text-font-size`, `--tg-text-line-height`,
  `--tg-text-padding`, always set on the renderer root; the reference set 15 px / 1.35 /
  `8px 12px` stands for what a client does not pass. Swift `WidgetTextTokens`, Kotlin
  `WidgetTextTokens`, C++ `twidget::WidgetTextTokens`, TS `TextTokens`; the Node harness
  takes `text` / `button` and writes the `theme` message into `--ops-file`.
- **`bg_color` is this bubble's colour**: a widget the user sent or forwarded sits in an
  outgoing bubble and gets that bubble's palette (`docs/sdk-v1.md` §2.3, §5.1).
- Visible changes for existing widgets: none of the 208 corpus widgets changes its box
  layout; text that relied on the engine's defaults gets the layer's (the font, heading
  sizes, list indents, links without an underline).
- Native renderers bake the template into their master CSS: `apps/renderer-litehtml` reads
  it from `profile.json` at build time and sets the theme variables itself
  (`src/theme.cpp`); what TwidgetCore and the Swift renderer must do is in
  `docs/decisions.md`. Reference pictures everywhere (dev app, conformance build, the
  native demos' reference modes) use one stylesheet, `packages/core/src/shared/reference.ts`.

**Text selection** (D18 replaced, `docs/decisions.md`, "Text selection"): text is not
selectable by default; a widget enables it with `user-select: text` (or
`-webkit-user-select`), children included. The profile takes `user-select: none | text |
auto`; `all`, `contain` and `::selection` stay out. The rule moved into the layer without
`!important`; the web renderers let a mouse drag select only over selectable text, and a
press anywhere else still selects nothing and keeps the chat input's focus.
**New client work** (`docs/sdk-v1.md` §5.10): on touch, long press and double tap stay
with the bubble, so selection inside a widget starts the way the client selects message
text, and copy is the client's copy. A native renderer that cannot select yet keeps the
text unselectable (an accepted exception to "every client draws it", like `:hover`).

**Examples and conformance**

- `corpus/defaults`: six widgets with little or no CSS (text card, list, table, FAQ with
  `<button>`, survey, link card), `pnpm test:defaults`.
- `packages/conformance`: `visual/14-unstyled-document` (no CSS at all, light and dark
  pictures on the reference tokens), `visual/15-body-background` (a body background without
  a margin reset fills the bubble), `visual/16-selection` (only the opted-in paragraph can be
  selected) and `auto/22-ua-defaults` (the computed defaults against the client's own tokens;
  author CSS wins); `auto/06-text-fonts-c` checks the selection values. "bare" widgets carry
  the harness script without its CSS. The harness sets `body { padding: 0 }`, so the other
  pictures did not move. Reference pictures get the bubble's colour as the page's canvas.

**Shim, glue and Intl** (three commits on GitHub main of 2026-10-19, folded in with fixes):

- **Live collections**: `getElementsByClassName` and `getElementsByTagName` answer live
  `HTMLCollection`s as in browsers (linkedom gave a snapshot); class lists with several
  names and `'*'` match as in browsers. A collection re-reads its matches only after an
  element is inserted or removed or a class is written (and once per task), so an indexed
  loop that reads, styles or measures the items stays linear; a loop that replaces each
  item's element children re-scans per step, as in browsers.
- **Web Animations before a layout query** (`webAnimations` feature): a synchronous
  layout query first sends the animation state the script set in the task, so a seek is
  visible to `getBoundingClientRect` in the same task (conformance `anim.waapi` ◐ → ✅).
  The Apple `WidgetHost` sends the `anim` batch in order with ops and queries; it was
  posted past them, so the query answered from the old frame (Kotlin, C++ and the web
  already kept the order).
- **Date defaults in the shim**: ECMA-402's defaults for a format without a date or time
  field and without a style (numeric year, month and day for `DateTimeFormat` and
  `toLocaleDateString`, the time for `toLocaleTimeString`, both for `toLocaleString`) are
  applied by the shim, so every host's formatter gets explicit fields, the C++ `BasicIntl`
  and tdesktop's `IntlProvider` included (`new Intl.DateTimeFormat('en-US').format(d)` is
  `1/1/1970`, not a medium date with a time). The Swift and Kotlin glue keep no copy of
  their own; `BasicIntl` prints numeric months in the en-US order.
- **Intl parity**: the Swift, Kotlin and C++ copies of the `intl` capability answer like V8
  (the web worker and the Node harness, which run V8's own `Intl`) and are checked against
  one fixture, `packages/core/tests/fixtures/intl-cases.json` (166 requests as the shim sends
  them with V8's answers; `scripts/intl-cases.ts` writes it, `intl-cases.test.ts` keeps it
  current; `IntlBridgeTests.testSharedFixtureMatchesV8`, androidTest `IntlBridgeTest`, desktop
  `intl_fallback_matches_v8_on_the_basic_cases`). Every copy now does: CLDR plural rules with the
  `v`/`f`/`t` operands from the fraction digits (`{minimumFractionDigits: 1}.select(1)` is
  `other`), English ordinals; `RelativeTimeFormat` `numeric: 'auto'` ("yesterday", "last
  quarter", "now") and `style`; `ListFormat` `type` (`conjunction`, `disjunction`, `unit`) and
  `style`; `Collator` `sensitivity` and `numeric`; `NumberFormat` rounding half away from zero
  (2.5 → 3), `minimumIntegerDigits`, significant digits, `currencyDisplay`, `signDisplay`
  (`always`, `exceptZero`, `negative`, `never`) and `resolvedOptions()`; `era`, `timeZoneName`,
  `fractionalSecondDigits`, `hour12` and `hourCycle` in dates, "UTC" as the UTC zone's name.
  The Node harness answers every kind now (relative, list and case mapping were missing):
  `src/shared/intl.ts` is the worker's and the harness's one implementation.
- What stays approximate in Intl:
  - Apple: Foundation's relative formatter has no quarter, so quarters are worded in English
    and counted in months in other languages; it has no unit lists either, so a unit list is
    a narrow conjunction (the same in English).
  - Android: `ListFormat` `type` and `style` need API 33 (below, the wide conjunction);
    below API 28 `numeric: 'auto'` words days, weeks, months, years and "now" only (minutes,
    hours and quarters stay numeric, quarters counted in months); CLDR's minimum grouping digits
    need API 31 (below, es, it, pl and others group four-digit integers).
  - C++ `BasicIntl` is the fallback without locale data (tdesktop plugs its own
    `IntlProvider`): numbers take the separators of about 40 languages but place currencies
    as en-US does; dates, relative times and lists are English in every locale; dates are in
    UTC whatever `timeZone` says; collation folds Latin-1 letters; case mapping is ASCII. It
    answers every check of conformance `auto/15-js-runtime-b` and the fixture's `basic` cases.
- The C++ core gained an optional `IcuIntl` on Windows' own ICU (`icu.dll`, b7c47cc, Fela), which a
  host passes instead of `BasicIntl`; not yet checked against the fixture (the `_WIN32` test
  `icu_intl_matches_v8` runs it on Windows).

**Contributing**: `CONTRIBUTING.md` lists what a commit to `main` must include (the copies and
fixtures that change with it, the tests per platform, the release notes).

**The test group**: `scripts/conformance/publish-channel.ts` fills the Telegram test forum
group through a test bot (topics Visual, Client SDK, Auto and, new, Defaults; Showcase,
Common, Generated on request), with size anchors in the file name, `--dry-run`, `--only`,
`--rebuild`, `--reset`; config and state outside the repository. Visual 03, 05, 08 and
09 are split into `-a`/`-b` so every widget fits the 600 px bubble; `build.ts` gates 560 px
at 320 px wide and `heights.ts` measures every widget through the sandbox.

## v0.1.0 — release notes (2026-10-17)

The first tagged state of the framework: the integration branch `telegram-macos`
with the pre-release hardening, widget profile 1 and SDK v1, verified on every platform (the per-suite numbers are in the tag's commit history
and CLAUDE.md).

**In the tag**

- The sandbox on five hosts — Swift (iOS + macOS), Kotlin, C++ core, web (Worker
  + Shadow DOM renderer) and the Node harness — on one QuickJS: the
  TelegramMessenger/quickjs fork at c24d6b0 (uncatchable interrupt and OOM in every
  engine path), as a submodule, with precompiled bytecode next to each bundle.
- `strict` policy by default everywhere; user activation from a pointer press only;
  runtime limits (CPU quota, timer cap, memory cap, off-screen pause, frozen last frame, and
  since 2026-10-17 a layout-query budget per task: queries that block the host for more than
  max(slice, 100 ms) in one task kill the widget with "Layout query limit exceeded" and count
  toward the CPU quota; `docs/decisions.md`, "Runtime limits").
- Opt-in features: `webAnimations` on every host; `canvasWords`, `binaryOps` with the
  Swift `NativeValidator`, `morphUpdates` Swift-only.
- Renderers: WKWebView / WebView / Shadow DOM as products; Blitz and litehtml as
  prototypes behind the same seam.
- The widget profile conformance set, `packages/conformance` (`CLIENTS.md`): every widget
  fits a mini-widget (under 3800 bytes of gzip), so the test bot sends the whole set as
  layer-233 messages, each with its reference picture next to it (`channel.json`). And the
  corpora: 99 attacks, compat, generated, common, showcase, 116 ported HTML bubbles.
- What a widget is (decided 2026-10-17): **mini-HTML**, one HTML document of at most 4 KB
  after gzip and 32 KB uncompressed, delivered right in the message as its own media.
  **The server API ships with this release: layer 233**, `inputMediaWidget width height html`
  / `messageMediaWidget width height payload` (`docs/tgw-format.md`): the server checks UTF-8,
  no NUL, 32 KB, gzips, checks 4 KB, puts the bytes in the message; bots only, private chats
  only, forwarding allowed anywhere; the caption is the preview text; clients below layer 233
  get `messageMediaUnsupported`; no server flag, clients enable rendering themselves.
- **Widget profile 1** (frozen 2026-10-17), enforced on every client:
  `packages/core/profile.json` is the single source and `docs/profile-v1.md` the
  generated contract. The validator drops form controls, named fonts and every
  property, function, unit, selector, at-rule and media feature outside the profile,
  rewrites sticky and scrolling overflow, drops nested rules and decides `@supports`;
  `TelegramWidget.profile`, `isProfileAtLeast(n)` and `CSS.supports` answer from the
  same tables; the renderer forwards no keyboard, focus, `dblclick`, context-menu,
  touch, wheel or scroll event, keeps the focus out of the widget, disables text
  selection and draws an unstyled `<button>` in the client's style (`--tg-button-*`).
- No bundled libraries: Chart.js and D3, `config.libraries` and
  `SandboxResources.provide` are gone on every platform (`docs/decisions.md`).
- `Intl.formatToParts` (numbers and dates) in every glue copy.
- **SDK v1** (`docs/sdk-v1.md`, 2026-10-17) in the shim, all five glue copies and the
  Node harness: `TelegramWidget.colorScheme`, `themeParams` (and the `--tg-theme-*`,
  `--tg-color-scheme`, `--tg-button-*` variables on the renderer root), `isActive`,
  `onEvent` / `offEvent` (`themeChanged`, `activated`, `deactivated`), `openLink` /
  `openTelegramLink` with the URL rules and one call per tap, `HapticFeedback` with a
  rate limit, Web Storage kept per message by the client (16 KB per area, preload at
  start, a commit per task), `navigator.language` and the Intl default from the
  client's locale, gesture claims per press (`x`, `y`). `sendMessage` is removed;
  `alert`/`confirm`/`prompt`, clipboard and share no longer reach the client. The
  checks are one policy in four copies with a shared fixture
  (`packages/core/src/shared/sdk-policy.ts`, `tests/fixtures/sdk-policy.json`).
  The corpus widgets that called `sendMessage` keep their state in `localStorage`.
- Docs for client teams: `docs/client-checklist.md` (start here), `docs/integration.md`,
  `docs/tgw-format.md`, `docs/sdk-v1.md` (the SDK and storage contract), `docs/profile-v1.md`,
  `docs/decisions.md` ("Profile v1").

**Not in the tag yet** (the next milestones)

- The client's answer to `prefers-reduced-motion` arrives with its config (today: no
  preference); per-bot `DeviceStorage` and everything else outside SDK v1 (§6 there).
- `VERSION.json`, CI, a reproducible build of `packages/quickjs-wasm/dist`, published
  Maven and npm artifacts.
- Renderer and engine pools on Android and the desktop core (the validator pool is on
  Android).

**API changes for clients**

- Bubble height bounds: the hosts clamp the reported height to **[50, 600]** by default
  (was [24, 900]) — web `MIN_HEIGHT` / `MAX_HEIGHT`, Swift `minHeight` 50 and
  `WidgetHost.Options.maximumHeight` 600, Kotlin `Options.minHeightDp` 50 / `maxHeightDp` 600,
  C++ `WidgetHost` [50, 600] — the shared v0.1.0 sizing constants, with a minimum widget
  width of 200 (`docs/integration.md`, "Bubble sizing": width 0 = the bubble's width, W = a
  fixed width; height 0 = auto under 600, H = a fixed height). A client that needs other
  bounds passes its own cap. The dev app's and the demos' reference mode clamp the same way.
- Removed: `SandboxResources.provide`, `ProvidedLibrary`, `BundledLibrary`,
  `SandboxResources.libraries` / `library(for:)` / `librarySource` / `libraryBytecode` (Swift);
  `SandboxResources.libraries` / `library()` / `librarySource()` / `libraryBytecode()` (Kotlin);
  `SandboxResources::libraries` / `library()` / `librarySource()` / `libraryBytecode()` (C++);
  the web `WidgetHost` option `libraries` and `@telegram/widget-sandbox/host/loader`.
  `WidgetJS/libs/` and `libraries.json` are no longer shipped.
- Diagnostics: a removal the profile causes is reported as `profile`; `library` is gone.
- Kill reasons: next to `CPU limit exceeded` and `Memory limit exceeded` a host can now see
  `Layout query limit exceeded: host queries blocked the renderer for more than N ms in one <kind>
  task (…)` (`onDead` / `died`), and a widget's later queries in that task get
  `{"error":"query budget exceeded"}`. The CPU budget policy gained `queryBudgetMs(sliceMs)`
  (`QUERY_BUDGET_MIN_MS` 100) in all four copies.
- SDK v1, every platform: the host takes a theme (`WidgetTheme`: colour scheme, palette,
  button tokens; the plain scheme is still accepted), a `locale` and a Web Storage
  preload (`storage`), and reports `onAction` (ready, openLink, openTelegramLink,
  haptic), `onStorageCommit(area, values)` and `onGestureClaim(x, y)`; `setTheme`
  takes a `WidgetTheme`. `onBridge` is deprecated (it still gets the raw calls that
  passed the checks). Removed: the host → widget bridge events (web
  `WidgetHost.bridgeEvent`, the `bridge` worker message, `TelegramWidget.on`) and the
  wire methods `sendMessage`, `alert` and `clipboard`. New diagnostics:
  `bridge-rejected`, `haptic-limit`, `storage-rejected`, `profile-mismatch`. The
  renderer's `gesture` message carries `x` and `y` next to `wants`.
  Per platform: Swift `WidgetTheme`, `WidgetButtonTokens`, `WidgetStorage`,
  `WidgetStorageArea`, `WidgetHaptic`, `WidgetAction`, `WidgetGestureClaim`, `SdkPolicy`;
  source-breaking: `WidgetBubble.theme`, `WidgetPrepared.theme` and
  `WidgetPreparer.prepare(theme:)` are `WidgetTheme`; removed
  `SandboxEngine.unprivilegedBridgeMethods`. Kotlin `WidgetTheme`, `WidgetButtonTokens`,
  `WidgetStorage`, `WidgetAction`, `SdkPolicy`, `SandboxEngine.Delegate.onAction` /
  `onStorageCommit` (default bodies); removed `UserActivation.UNPRIVILEGED_BRIDGE_METHODS`.
  C++ `twidget::sdk` (`sdk_policy.h`), `WidgetTheme`, `WidgetButtonTokens`, `WidgetStorage`,
  `WidgetAction`; `SandboxEngineDelegate::onBridgeAction` / `onStorage` (empty defaults;
  `onBridge` is no longer pure); `Json::parse(text, Json::Surrogates::Keep)`; new members
  change the layout of `SandboxEngine` and `WidgetHost` (recompile). TS: the web host's
  `UNPRIVILEGED_BRIDGE_METHODS` export is gone (`PRIVILEGED_METHODS` in `sdk-policy.ts`).

**Known issues**

- *Resolved by profile 1* (2026-10-17): `focus()` and key events no longer reach the
  renderer or the widget, text selection is off, form controls are dropped, and the
  Shadow DOM renderer's gaps are out of the profile on every renderer (popovers,
  `showModal()`, `@property`, `:scope`); CSS nesting is dropped with a diagnostic.
- *Native renderers*: the Swift renderer and TwidgetCore apply the same validated ops
  but must also forward no key events, disable selection and bake the `<button>`
  template into their master CSS (`tables.uaStylesheet`); grid is in profile 1 on the
  condition that both draw it.
- *Shim gaps found by the conformance set*: `getComputedStyle` answers `""` for
  shorthands (`white-space`, `overflow`, `margin`); `animation*` / `transitionend` events carry no `animationName`,
  `elapsedTime`, `propertyName`; canvas `drawImage(off-DOM canvas)` draws nothing.
- *Android instrumented suite on the emulator*: `GestureTest.rangeSliderAndInnerScrollerKeepTheGesture`
  fails on AVD twidget-35 (API 35, software GLES): the first WebView in the test
  process takes ~25 s to initialise against the test's 15 s load deadline, and under
  host load the renderer's gesture decision misses `GestureWebView`'s 150 ms timeout, so
  the list scrolls. Three other tests time out when the host is loaded; all pass
  alone. Not a framework regression; rerun on a hardware-GPU emulator or a phone.
- *Timing-sensitive tests*: `shim.test.ts` "defers timers once the widget has
  burnt its quota", Swift `CanvasWordsTests.testWordPayloadsSpliceToTheSameJSONAsTheJSONPath`
  (a 1 ms timer races the hand-driven frames), Swift `CpuTimeLimitTests.testBusyMachineDoesNotKillAWidgetWithinItsCPUBudget`
  (`loaded` and the engine stats reach the diagnostics on two async paths, so the
  "alone" run can read `jsMs` 0.0 and set the contention run's limit to the 100 ms
  floor), desktop `cpu_quota_throttles_greedy_timer`: each fails now and then under
  load and passes alone.
- *htmlbubbles-ported*: 107–110 of 116 pass (2026-10-17, after the profile-1 rework); the rest
  are time-based or random drawings, the hang demo, one intentional JS error and
  `06-trip-lisbon/09-me-bare`, a 690 px widget that fails steadily since the 600 px bubble
  cap (its click pass scrolls the clipped document; before the cap it failed now and then)
  (`corpus/htmlbubbles-ported/README.md`).
- *Locale in the Intl fallbacks*: the Kotlin `IntlBridge` has no `numberOptions` /
  `dateOptions`, so `resolvedOptions().locale` does not show the client locale on Android
  (formatting and `navigator.language` do follow it); the C++ `BasicIntl` ignores the locale
  (a product host plugs in ICU or QLocale).
- *3D channel* (`__tw3d`): debug builds only.

## Policy: `strict` (default) and `compat`

Hosts choose a policy: `SandboxConfig.policy` / `WidgetHost.Options.policy` (Swift, Kotlin), `SandboxConfig.policy` / `WidgetHostOptions.policy` (C++), `config.policy` / `WidgetHost` option `policy` (TS). **`strict` is the default on every host** (it used to be `compat`); `compat` keeps the limits hosts had before the hardening and is meant for tests and tooling. Kotlin and C++ take a string: anything but exactly `"compat"` is strict.

| | strict (default) | compat |
|---|---|---|
| Privileged bridge calls | need a real user gesture (one pointer press on the visible widget unlocks one call, keys never do; `ready` is exempt; only renderer events marked `trusted` count), else `activation-blocked` | forwarded as before |
| Text nodes, string properties, Path2D data | 64 KB | 2 MB |
| Canvas string arguments | 64 KB | 4 MB |
| Visible text: text nodes, `value`/`placeholder`/`alt`/`label`/`title`, `data-*`, `aria-*`, strings in `style` attributes, `style` ops and stylesheets | 256 Ki units per document, 8192 complex-script units (`text-budget`), churn bucket (`text-churn`) | not budgeted |
| `attr()` in CSS | only the budgeted attributes above | any attribute |

The validator learns the policy through `__twValidator.setPolicy('strict')` (glue `__v_setPolicy`), sent only for `strict`, so glue built against an older validator bundle keeps working. The native node model reads the limits and the budgeted attribute names from `__twValidator.tables()`, which reflect the policy.

## Visible by default

| Change | Platforms | Opt-out | Notes |
|---|---|---|---|
| `performance.now()`, the host clock and frame times tick in 100 microsecond steps | all | none | Timing side channel hardening. |
| The CPU limit and quota count the JS thread's own CPU time, with a wall-clock backstop of max(10x slice, 5 s) | iOS, macOS, Android, desktop | none | Busy machines kill fewer widgets; `stats.jsMs` changes meaning. Web and the Node harness keep wall clock. |
| Out-of-memory is uncatchable on the web (fork wasm), as it already was on native | web, Node | none | Widgets that caught `InternalError: out of memory` now die. |
| The host functions and `__twDispatch` are deleted from the global object after boot, and a sandbox bundle that fails to load (or cannot hide them) kills the widget before any widget code runs | all | none | A host loading an older JS bundle through `SandboxResources(bundle:)` gets every widget killed. |
| In-place `textContent` updates and the attribute fast paths run by default | all | features `noInPlaceText`, `noAttrFast` | Same ops as the full paths (differential fuzz tests on TS and Swift). |
| QuickJS is the TelegramMessenger/quickjs fork as a git submodule on every platform (pinned at c24d6b0: the CPU interrupt and out-of-memory are uncatchable in every engine path, including Promise executors, thenables, async generators and iterator `return()`) | all | none | SwiftPM and Xcode clone it for git dependencies; registry/zip copies without submodules fail to build. Web uses `packages/quickjs-wasm` (workspace package, committed build) instead of the npm variant. |

Under `strict` also: complex-script text changes draw from 8192 words refilling at 1024 per second (`text-churn`), all platforms.

## Opt-in (nothing changes until the host lists them)

- `config.features`: `canvasWords`, `binaryOps` (native node model validates binary batches), `webAnimations` (Element.animate and the `anim` message). Android (`SandboxConfig.features`, `WidgetHost.Options.features`) and the web (`WidgetHostOptions.features`) take them now; every glue drops what it does not implement (see the table below).
- `WidgetHost.Options.assetResolver` (iOS/macOS): `icon:` images expanded to `data:image/` before validation.
- `WidgetHost.Options.sideChannels` (iOS/macOS): side channels such as `Widget3DChannel`.
- `WidgetHost.Options` (iOS/macOS) keep their old defaults (`asyncValidation` and `sharedValidatorThread` false, the widget starts on the renderer's `ready`, `parallelBoot` only prewarms the runtime). New: `startBeforeRendererReady` runs the widget while the renderer loads. Ops, queries, bridge calls, logs and diagnostics reach the main thread in the order the widget produced them in both validation modes.
- ~~`SandboxResources.provide` (iOS/macOS): extra bundled libraries such as three.js.~~ Removed with profile 1 (no libraries).

## Swift-only

What exists only in the Swift glue (iOS, macOS). Everything here is opt-in; the
other hosts drop the feature names they do not implement.

| Feature | Swift | Node harness | Web | Kotlin | C++ |
|---|---|---|---|---|---|
| `webAnimations`, `noInPlaceText`, `noAttrFast`, `noCanvasRect` | yes | yes | yes | yes | yes |
| `canvasWords` (word payloads, `CanvasWords.swift`) | yes | yes | no | no | no |
| `binaryOps` + native node model (`NativeValidator.swift`, `NativeAnimValidator.swift`) | yes | decoder only | no | no | no |
| `morphUpdates` (`morph {html}`) | yes | yes | no | no | no |
| `icon:` images (`assetResolver`, `IconExpansion.swift`) | yes | no | no | no | no |
| Side channels (`Widget3DChannel`; debug builds only) | yes | no | no | no | no |
| Engine/renderer/validator pools, `WidgetFeed`, `WidgetBubble`, typed ops, WebKit view reuse | yes | — | worker pool only | validator pool only | no |

## Hardening before main (2026-10-16)

- A canvas word payload may be referenced once per batch; a binary batch that repeats a key is rejected whole.
- `anim` keyframe values may not be named `offset`, `easing`, `composite` or `computed-offset`; renderers keep animation state per widget and cancel an animation whose update fails; the web worker forwards `anim` only with `webAnimations`.
- The native node model rejects non-finite `prop` numbers like the script validator.
- `completeEditorCommand` is accepted only on a `<textarea>` with integer arguments.
- `Widget3DChannel` exposes `__tw3d` only in `DEBUG` builds (or with `TWIDGET_EXPERIMENTAL_3D`); release builds report a `3d` diag instead.
- The desktop `json.h` prints the shortest round-trip number on every target and parses numbers in the C locale.
- Gestures: every host counts only renderer events marked `trusted`, and only pointer presses: no `keydown`, and a click starts a gesture only with `detail` ≥ 1 (the click Enter or Space activates has 0).
- `CallSite.getFunction` / `getThis` answer `undefined` inside the sandbox (the shim no longer leaks its closures through `Error.prepareStackTrace`).
- Provided-library patterns must compile on their own (an unbalanced `)` could unanchor them); `icon:` is matched case-sensitively and limited per widget (256 distinct tokens, 2 MB of answers).
- The Node harness quantizes frame times to 100 µs and gates gestures on visibility (`RunOptions.visible`).

## Embedding contracts

- `Widget3DBytes.borrowedPointer` points into the widget's heap and is valid only during `Widget3DSurface.call`; a backend that keeps data for later uses `copy()`. Calls and channel teardown are serialised, and a call after `destroy()` never reaches a surface.
- `Widget3DChannel` limits are per channel (`init(backend:maxSurfaces:maxBytesPerCall:)`), clamped to at most 16 surfaces and 64 MB per call.
- `SandboxResources.provide`: the provider's `source` and `bytecode` closures run outside the resources lock (they may call back into `SandboxResources`); provided bytecode is trusted and must come from the app bundle or memory, never from a writable cache.
- Library patterns (`SandboxResources.provide`, the web `libraries` option, `provideLibrary`) match the whole URL case-insensitively; each must be a regular expression on its own, and one that matches `''`, `app:` or a generic CDN URL is refused (`provide` returns false).
- A renderer behind the `Renderer` seam marks real input `{"type":"event", …, "trusted": true}`; without the flag its events reach the widget but unlock no privileged bridge call. The bundled renderers (WebView/iframe from `isTrusted`, Blitz, litehtml) do.

## API

- Swift: `SandboxPolicy`, `SandboxConfig.policy`, `WidgetHost.Options.policy` and `TrustedValidator.setPolicy` are new. Additions only otherwise, with default implementations for new protocol requirements (`SandboxEngineDelegate` ops/binaryOps/anim, `Renderer.acceptsTypedOps`/`send(ops:)`). New parameters with defaults: `EnginePool.take(features:)`, `SandboxEngine.init(resources:features:)`, `WidgetHost.init(... executor:shell:)` (function references to the old signatures no longer type-check). New products `WidgetSandboxProtocol`, `CQuickJS`. Minimum macOS lowered from 13 to 10.13.
- Kotlin: `SandboxEngine.Delegate.onAnim` has a default body; Java implementers of the interface must implement it (no `-Xjvm-default`). `GestureCounter.gestureFor(type, detail)` and `UserActivation.isUserActivation(type, detail)` take the event type and its `detail` only (the key, repeat and modifier parameters are gone with keyboard gestures); `UserActivation.ACTIVATION_KEYS` is removed.
- C++: `SandboxEngineDelegate::onAnim` is a non-pure virtual at the end of the vtable, but new data members change the layout of `QuickJSContext`, `SandboxEngine` and `WidgetHost`: prebuilt consumers must recompile. New class `GestureCounter` (the host's gesture numbering); `WidgetHost::isUserActivation` forwards to it.
- TS: protocol additions only (`anims` query, `anim` messages, `trusted` on events, `gesture` on bridge and events, `features`). No exports removed, no diag kinds renamed. New diag kinds: `activation-blocked`, `text-budget`, `animation-limit`, `icon-limit`, `3d`, `3d-rejected`, `3d-stopped`.
- `LIMIT_DEFAULTS` are unchanged.

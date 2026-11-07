# Before you push to `main`

`main` is the release line: every client pins a tag cut from it, so a commit on `main`
must already be valid on every platform. One page of rules; the commands are in
`CLAUDE.md` ("Commands"), with pnpm run through corepack (`corepack pnpm …`).

## 1. Know what you are changing

| You touched | It is | What else must change in the same commit |
|---|---|---|
| `packages/core/src/sandbox`, `renderer`, `shared`, `host/validator*`, `allowlist`, `css-sanitizer`, `url-policy`, `profile.json` | the shared contract: one JS for every client | the rebuilt bundles with their `.qbc` (`pnpm build:ios`, `build:android`, `build:desktop`), a unit test; after `profile.json`, first `packages/core/scripts/profile-build.ts` |
| What a widget sees or may do, decided in glue: limits, timers, visibility, the order of ops / anim / queries, Intl, the SDK checks | parity glue, five copies: web worker, Node harness, Swift, Kotlin, C++ | the same change in every copy, or a line in the commit message saying why it is one platform only |
| `CpuBudget*`, `SdkPolicy*`, the `intl` capability | a policy checked against a fixture in `packages/core/tests/fixtures`: `cpu-budget.json`, `sdk-policy.json`, `intl-cases.json` | all four copies and the fixture together (`intl-cases.json` holds V8's answers: add the case to `packages/core/scripts/intl-cases.ts` and regenerate); `build:ios`, `build:android`, `build:desktop` copy the fixtures into the native tests |
| What a widget author sees: a profile feature, an SDK call, a limit, a default, who gets a press, a dev-tool flag | the authors' contract | `docs/widget-runtime-profile.md` (the guide and its agent block, which must stand alone: a bot's model sees nothing else; their out lists are generated: run `profile-build.ts` after `profile.json`), and `docs/changes.md` |
| The wire between the glue and the bundles (`docs/protocol.md`) in a way the previous release's glue cannot run with | the protocol | `protocol` in `VERSION.json`, then `pnpm build:ios`, `build:android`, `build:desktop` (they stamp the bundles and write the glue's `WidgetSandboxVersion` / `twidget/version.h`) |
| `packages/ios/Sources/CQuickJS/quickjs` (the submodule) or `packages/quickjs-wasm` | the engine | the bundles' `.qbc`, and `packages/quickjs-wasm/build.sh` (the pinned emscripten, `--docker` without it): `dist/` and `dist.sha256` together |
| Your renderer, pools, demo, build files | platform only | nothing else |

If you cannot do every copy yourself, push a branch and ask for the rest; do not push the
one-platform half to `main`.

## 2. Put logic in the right place

- Logic that needs no platform data (option processing, defaults, ordering rules) goes into
  the shim once, not into five copies of glue. Example: ECMA-402's date defaults live in
  `packages/core/src/sandbox/globals.ts`, so every host, present and future, gets them.
- Locale data and native APIs stay in glue.
- A widget feature goes in or out only through `profile.json`; adding one is profile 2.

## 3. Test what you changed

- Always: `pnpm typecheck`, `pnpm test:unit`.
- Shim, validator or renderer JS: `pnpm test:attacks` on both renderers
  (`TW_RENDERER=shadow` too, 0 hits), the conformance run (`scripts/conformance/run-web.ts`,
  then `packages/conformance/tools/report.mjs` for `packages/conformance/results`),
  and the corpora the change can affect (`test:compat`, `test:showcase`, `test:defaults`, …).
- Glue: the tests of every platform you changed: `xcodebuild test` (macOS and iOS), Android
  JVM and `connectedDebugAndroidTest`, desktop `twidget-tests` and `twidget-run --attacks`.
  Code that never ran on its platform is not done.
- Not the frozen renderer prototypes (Blitz, the plain litehtml core; `docs/decisions.md`, "The renderer prototypes are
  frozen"): a change is not checked on them, and "not checked on Blitz/litehtml" is no gap.
- A new behaviour gets a test that fails without the change.
- Cost: measure anything that runs per element or per query on a large DOM (500+ elements).
  A widget has 300 ms of CPU per second and at most 500 ms in one task; a quadratic loop
  over a few hundred nodes is a dead widget.

## 4. Write it down

- One behaviour change per commit; the message says what changed and in which copies.
- `docs/changes.md`: a line under the next release's section; remove known issues you fixed.
- Test counts and commands in `CLAUDE.md` when you add tests or suites.

## 5. Push

- Rebase on the latest `main`; rebuild the bundles if the rebase touched `packages/core`.
- If a release is being prepared, say so before pushing: the release is rebased on top of
  you and re-tested.
- There is no CI in `main` yet (the GitHub Actions workflows wait on the branch `claude/ci`), so
  §3 is the check. A failure on a timing-sensitive test (`docs/changes.md`, known issues) is
  re-run once; a second failure is real.
- Never edit generated files by hand: `profile-tables.ts`, `docs/profile-v1.md`, the out lists
  between the `profile:summary` and `profile:agent` markers of `docs/widget-runtime-profile.md`, the
  bundles and `.qbc`, `packages/conformance/{catalogue,manifest,channel}.json`,
  `packages/core/tests/fixtures/intl-cases.json` and the fixtures' copies in the native tests,
  `WidgetSandboxVersion.swift` / `.kt` and `twidget/version.h` (from `VERSION.json`),
  `packages/quickjs-wasm/dist` and `dist.sha256` (from `build.sh`).

## 6. Cut a release

- One release commit: `VERSION.json`'s `version` (semver, the tag without its `v`) and, in the
  same commit, the files that repeat it (`package.json`, `packages/core/package.json`,
  `packages/android/widget-sandbox/build.gradle.kts`); `protocol`, `profile` and `sdk` only when
  they changed (`profile` only with profile 2 in `packages/core/profile.json`; `sdk` with a new
  SDK version, not an addition to SDK v1). Then `pnpm build:ios`, `build:android`,
  `build:desktop`: the bundles carry the stamp and the glue the constants
  (`packages/core/tests/unit/version.test.ts` names any file left behind, and every native glue
  refuses bundles of another protocol, profile or sdk), the release notes in `docs/changes.md`,
  `CLAUDE.md`'s status.
- §3's matrix green on the release commit (its results in the release notes), then the annotated
  tag `v<version>` on it.

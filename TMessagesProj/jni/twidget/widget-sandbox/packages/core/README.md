# `@telegram/widget-sandbox` — the JS core and the web host

The source of the three bundles every platform ships, plus the host that
embeds them in a web page.

| Directory | Trust | What |
|---|---|---|
| `src/sandbox/` | **untrusted once loaded** — runs inside QuickJS next to the widget | the DOM shim over linkedom: `index.ts` (boot, dispatch), `capture.ts` (mutation capture → ops), `events.ts`, `forms.ts`, `layout.ts`, `canvas.ts`, `globals.ts` (network APIs stubbed to `NetworkDisabledError`), `style.ts` |
| `src/host/` | trusted | `widget-host.ts` (one widget: worker, validator, renderer), `worker.ts` (QuickJS glue, limits), `validator.ts` + `css-sanitizer.ts` + `url-policy.ts` + `allowlist.ts` (every message from the sandbox is checked here), `cpu-budget.ts` (the quota policy, four copies across platforms), `renderer.ts` (the `Renderer` seam), `iframe-renderer.ts`, `shadow-renderer.ts`, `worker-pool.ts`, `validator-bundle.ts` (the validator as a bundle for native hosts) |
| `src/renderer/` | trusted, runs in the sandboxed frame / shadow root / WebView | `core.ts` (ops → DOM, shared by every renderer bundle), `renderer.ts` (transport: MessagePort, WKWebView or android.webkit), `srcdoc.ts` |
| `src/shared/` | | `protocol.ts`: message types, limits and their defaults |
| `scripts/` | | `sandbox-build.ts` (esbuild options for the bundles), `build-native-bundles.ts` (`pnpm build:<platform>`: bundles, `.qbc` bytecode via `packages/tools/qjsc`, the shared fixture), `sandbox-node.ts` (the Node harness: run a widget through the real bundle without a browser) |
| `vite.ts` | | the Vite plugin: builds the two in-page bundles on demand, serves `/libs/`, sets the cross-origin isolation headers |
| `tests/unit/` | | validator, shim (through QuickJS in Node), CPU budget (fixture `tests/fixtures/cpu-budget.json`), memory cap |

## Package surface

```
@telegram/widget-sandbox/host/*     ./src/host/*.ts      WidgetHost, WorkerPool, renderers, Validator
@telegram/widget-sandbox/shared/*   ./src/shared/*.ts    protocol types and LIMIT_DEFAULTS
@telegram/widget-sandbox/vite       ./vite.ts            widgetSandbox() plugin, ISOLATION_HEADERS
@telegram/widget-sandbox/build      ./scripts/sandbox-build.ts
@telegram/widget-sandbox/node       ./scripts/sandbox-node.ts   runWidget() for scripts and tests
```

The package is consumed from source (the exports point at `.ts`); the
consumer bundles it with Vite and the plugin. See `docs/integration.md`.

## Commands

```bash
corepack pnpm -C packages/core typecheck
corepack pnpm -C packages/core test:unit
corepack pnpm -C packages/core build:native ios|android|desktop   # or pnpm build:<platform> at the root (+ demo corpus)
corepack pnpm tsx packages/core/scripts/sandbox-node.ts corpus/compat/tabs.html --verbose
```

Rules that keep this directory the framework: nothing here imports from
`apps/`; fix the shim generally, never per widget; every change to a limit or
the quota policy goes to the four native copies and the fixture in the same
commit (`CLAUDE.md`).

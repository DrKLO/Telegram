# Third-party notices

Code that ships inside the packages (vendored sources, or bundled into the
JavaScript bundles and their precompiled bytecode under `WidgetJS/`). Build-time
and test-only dependencies (esbuild, Vite, Playwright, TypeScript, …) are not
listed; see the `package.json` files and `pnpm-lock.yaml`.

| Component | Version | License | Where |
|---|---|---|---|
| [quickjs-ng](https://github.com/quickjs-ng/quickjs) — Fabrice Bellard, Charlie Gordon and contributors, as the [TelegramMessenger/quickjs](https://github.com/TelegramMessenger/quickjs) fork | 0.17 (fork) | MIT | git submodule at `packages/ios/Sources/CQuickJS/quickjs/` (its `LICENSE` is there); compiled into the iOS/macOS package, the Android library, the desktop core, `packages/tools/qjsc` and the web/Node wasm (`packages/quickjs-wasm`) |
| [quickjs-emscripten](https://github.com/justjake/quickjs-emscripten) (`quickjs-emscripten-core`; `c/interface.c`, templates and the FFI of `@jitl/quickjs-ng-wasmfile-release-sync`) — Jake Teton-Landis | 0.32.0 | MIT | the web host's QuickJS glue, `packages/core`; `packages/quickjs-wasm` rebuilds the wasm variant from the TelegramMessenger/quickjs fork (`LICENSE.quickjs-emscripten` there) |
| [linkedom](https://github.com/WebReflection/linkedom) — Andrea Giammarchi | 0.18.13 | ISC | bundled into `sandbox-bundle.js` (the DOM shim) |
| [parse5](https://github.com/inikulin/parse5) — Ivan Nikulin | 8.0.1 | MIT | bundled into `sandbox-bundle.js` and `validator-bundle.js` |
| [css-tree](https://github.com/csstree/csstree) — Roman Dvornov | 3.2.1 | MIT | bundled into `validator-bundle.js` (the CSS sanitizer) |
| [Chart.js](https://github.com/chartjs/Chart.js) | 4.5.1 | MIT | shipped as `WidgetJS/libs/chart.umd.js` (+ `.qbc`), served to widgets in place of the CDN URL |
| [D3](https://github.com/d3/d3) — Mike Bostock | 7.9.0 | ISC | shipped as `WidgetJS/libs/d3.min.js` (+ `.qbc`), same |
| [litehtml](https://github.com/litehtml/litehtml) — Yuri Kobets | master 4a651c52 (2026-10-04) | BSD-3-Clause | vendored unmodified in `apps/renderer-litehtml/third_party/litehtml/` (renderer prototype, dev tooling) |
| [gumbo-parser](https://github.com/google/gumbo-parser) (litehtml's HTML parser) — Google | as vendored by litehtml | Apache-2.0 | `apps/renderer-litehtml/third_party/litehtml/src/gumbo/` (its `LICENSE` is there) |
| [Blitz](https://github.com/DioxusLabs/blitz) (blitz-dom, blitz-paint, blitz-traits) and the Rust crates in `apps/renderer-blitz/Cargo.lock` (stylo, taffy, parley, vello, anyrender, …) | pinned in `Cargo.lock` | MIT / Apache-2.0 (per crate) | fetched by cargo, not vendored; renderer prototype, dev tooling |

The license texts of the vendored components are kept next to their sources
as listed above; the npm packages' license texts ship in `node_modules` and
in the registry.

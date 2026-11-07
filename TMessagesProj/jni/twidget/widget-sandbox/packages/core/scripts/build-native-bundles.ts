// Builds the JavaScript the native packages ship: the sandbox bundle, the
// renderer bundle (platform transport), the validator bundle, their
// precompiled bytecode and the shared test fixture — into
// packages/ios, packages/android or packages/desktop. The demo apps' corpus
// copy is a separate step (scripts/sync-demo-corpus.ts at the repo root).
// Every bundle's first line is the stamp of VERSION.json (scripts/version.ts), and the
// target's platform constants (WidgetSandboxVersion.swift / .kt, twidget/version.h) are
// written from the same file: the glue refuses a bundle whose stamp is not its own.
//
// Usage: pnpm build:ios        (= pnpm -C packages/core build:native ios + the corpus sync)
//        pnpm build:macos      (same outputs as ios: one Swift package)
//        pnpm build:android
//        pnpm build:desktop
//        tsx scripts/build-native-bundles.ts <target> --out <dir>   (writes only the bundles to <dir>; used by the freshness test)

import { build } from 'esbuild';
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { rendererBuildOptions, sandboxBuildOptions, validatorBuildOptions } from './sandbox-build.ts';
import { assertVersionsAgree, bundleStamp, platformConstants, readVersion } from './version.ts';

/** The repo root; the native packages are siblings of this one under packages/. */
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..', '..');
/** This package (packages/core): the bundles' sources. */
const pkgRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

const ios = { transport: 'webkit', js: 'packages/ios/Sources/WidgetSandbox/WidgetJS', fixtures: 'packages/ios/Tests/WidgetSandboxTests/Fixtures' } as const;
const TARGETS = {
  ios,
  // macOS uses the same Swift package, bundles (WKWebView transport) and demo corpus as iOS.
  macos: ios,
  // Desktop C++ core (desktop/): no renderer of its own yet; the renderer bundle is built for the
  // WKWebView-style bridge so a lib_webview-based host can pick it up unchanged on macOS.
  desktop: { transport: 'webkit', js: 'packages/desktop/WidgetJS', fixtures: 'packages/desktop/tests/fixtures' },
  android: { transport: 'android', js: 'packages/android/widget-sandbox/src/main/assets/WidgetJS', fixtures: 'packages/android/widget-sandbox/src/androidTest/assets' },
} as const;

const targetName = (process.argv[2] ?? 'ios') as keyof typeof TARGETS;
const target = TARGETS[targetName];
if (!target) {
  console.error(`unknown target "${targetName}"; expected one of ${Object.keys(TARGETS).join(', ')}`);
  process.exit(1);
}
const outIndex = process.argv.indexOf('--out');
const outDir = outIndex > 0 ? path.resolve(process.argv[outIndex + 1]) : undefined;
const res = outDir ?? path.join(root, target.js);
fs.mkdirSync(res, { recursive: true });
// No bundled libraries since profile 1 (docs/profile-v1.md, js.libraries): what an older build left goes.
fs.rmSync(path.join(res, 'libs'), { recursive: true, force: true });
fs.rmSync(path.join(res, 'libraries.json'), { force: true });

// VERSION.json and every file that repeats one of its numbers must agree before anything is stamped.
const version = readVersion();
assertVersionsAgree(version);
const stamp = bundleStamp(version);

async function emit(name: string, options: Parameters<typeof build>[0]) {
  const out = await build({ ...options, banner: { js: stamp } });
  const text = out.outputFiles![0].text;
  fs.writeFileSync(path.join(res, name), text);
  console.log(`${name}: ${(text.length / 1024).toFixed(0)} KB`);
}

await emit('sandbox-bundle.js', sandboxBuildOptions(pkgRoot));
await emit('renderer-bundle.js', rendererBuildOptions(pkgRoot, target.transport));
await emit('validator-bundle.js', validatorBuildOptions(pkgRoot));

// Precompiled bytecode (.qbc next to each .js that QuickJS evaluates; the renderer bundle runs in
// a web view and has none). The compiler is built from the vendored quickjs-ng, so the bytecode
// always matches the engine the apps link; each blob carries the source's length and hash and is
// refused at load time if the .js next to it changed (see qjs_shim.h).
const qjsc = buildQjsc();
for (const name of ['sandbox-bundle.js', 'validator-bundle.js']) {
  const out = name.replace(/\.js$/, '.qbc');
  execFileSync(qjsc, [path.join(res, name), path.join(res, out), path.basename(name)], { stdio: 'inherit' });
}

/** Builds packages/tools/qjsc from packages/ios/Sources/CQuickJS (the fork submodule + shim) into .cache/ (gitignored) when it is missing or older than its sources. */
function buildQjsc(): string {
  const qjs = path.join(root, 'packages/ios/Sources/CQuickJS');
  const sources = ['quickjs/quickjs.c', 'quickjs/dtoa.c', 'quickjs/libregexp.c', 'quickjs/libunicode.c', 'shim/qjs_shim.c'].map((f) => path.join(qjs, f));
  sources.push(path.join(root, 'packages/tools/qjsc/qjsc.c'));
  const headers = fs.readdirSync(path.join(qjs, 'quickjs')).filter((f) => f.endsWith('.h')).map((f) => path.join(qjs, 'quickjs', f));
  headers.push(path.join(qjs, 'include/qjs_shim.h'));
  const bin = path.join(root, '.cache', process.platform === 'win32' ? 'qjsc.exe' : 'qjsc');
  const newest = Math.max(...[...sources, ...headers].map((f) => fs.statSync(f).mtimeMs));
  if (fs.existsSync(bin) && fs.statSync(bin).mtimeMs > newest) return bin;
  fs.mkdirSync(path.dirname(bin), { recursive: true });
  console.log('building packages/tools/qjsc from the QuickJS submodule (TelegramMessenger/quickjs)…');
  const cc = process.env.CC ?? 'cc';
  execFileSync(cc, ['-O2', '-std=gnu11', '-D_GNU_SOURCE', '-DQUICKJS_NG_BUILD', '-I', path.join(qjs, 'include'), '-I', path.join(qjs, 'quickjs'),
    '-Wno-unused-parameter', ...sources, ...(process.platform === 'win32' ? [] : ['-lm']), '-o', bin], { stdio: 'inherit' });
  return bin;
}
// Test fixtures shared with the native ports (one source of truth: packages/core/tests/fixtures),
// and the platform constants the glue compares each bundle's stamp with.
if (!outDir) {
  const constants = platformConstants(version)[targetName === 'macos' ? 'ios' : targetName];
  fs.writeFileSync(path.join(root, constants.path), constants.text);
  console.log(`${constants.path}: ${stamp}`);
  fs.mkdirSync(path.join(root, target.fixtures), { recursive: true });
  for (const f of ['cpu-budget.json', 'sdk-policy.json', 'gesture-policy.json', 'intl-cases.json']) fs.copyFileSync(path.join(pkgRoot, 'tests', 'fixtures', f), path.join(root, target.fixtures, f));
  console.log(`fixtures copied to ${target.fixtures}`);
}

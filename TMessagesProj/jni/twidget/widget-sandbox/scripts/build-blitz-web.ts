// Builds the Blitz renderer prototype for the web dev app: renderer-blitz/ →
// wasm32-unknown-unknown (cargo, `wasm-release` profile) → wasm-bindgen glue
// (`--target web`; only web-time needs it, the twb_* exports are plain C ABI)
// → renderer-blitz/pkg-web/ (gitignored; the Vite plugin serves it at /blitz/,
// outside the module graph, like /libs/). Dev tooling, see
// docs/blitz-experiment.md. Needs rustup's cargo with the wasm32 target and
// wasm-bindgen-cli at the version Cargo.lock pins for the wasm-bindgen crate.
//
//   corepack pnpm build:blitz-web

import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.join(path.dirname(fileURLToPath(import.meta.url)), '..');
const crate = path.join(root, 'apps', 'renderer-blitz');
const outDir = path.join(crate, 'pkg-web');
const target = 'wasm32-unknown-unknown';
const wasm = path.join(crate, 'target', target, 'wasm-release', 'twidget_blitz.wasm');
const env = { ...process.env, PATH: ['/opt/homebrew/opt/rustup/bin', path.join(os.homedir(), '.cargo', 'bin'), process.env.PATH ?? ''].join(path.delimiter) };

function run(cmd: string, args: string[], cwd: string) {
  console.log(`$ ${cmd} ${args.join(' ')}`);
  const r = spawnSync(cmd, args, { cwd, env, stdio: 'inherit' });
  if (r.error) throw new Error(`${cmd}: ${r.error.message}${r.error.message.includes('ENOENT') ? ' (install it: rustup / cargo install wasm-bindgen-cli --version <Cargo.lock>)' : ''}`);
  if (r.status !== 0) process.exit(r.status ?? 1);
}

const lock = fs.readFileSync(path.join(crate, 'Cargo.lock'), 'utf8');
const bindgenVersion = /name = "wasm-bindgen"\nversion = "([^"]+)"/.exec(lock)?.[1];
const installed = spawnSync('wasm-bindgen', ['--version'], { env, encoding: 'utf8' }).stdout?.trim();
if (bindgenVersion && installed && !installed.endsWith(bindgenVersion)) {
  console.error(`wasm-bindgen-cli is ${installed}, Cargo.lock pins wasm-bindgen ${bindgenVersion}: cargo install wasm-bindgen-cli --version ${bindgenVersion} --locked`);
  process.exit(1);
}

const targets = spawnSync('rustup', ['target', 'list', '--installed'], { env, encoding: 'utf8' }).stdout ?? '';
if (!targets.split('\n').includes(target)) run('rustup', ['target', 'add', target], crate);
run('cargo', ['build', '--profile', 'wasm-release', '--lib', '--target', target], crate);
fs.mkdirSync(outDir, { recursive: true });
run('wasm-bindgen', ['--target', 'web', '--no-typescript', '--out-dir', outDir, '--out-name', 'twidget_blitz', wasm], crate);
for (const f of fs.readdirSync(outDir).sort()) console.log(`${f}: ${(fs.statSync(path.join(outDir, f)).size / 1024 / 1024).toFixed(2)} MB`);

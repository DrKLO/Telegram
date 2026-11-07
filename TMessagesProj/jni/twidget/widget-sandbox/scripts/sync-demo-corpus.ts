// Copies the corpus into the demo apps (the native packages ship no corpus; the
// framework bundles themselves come from `pnpm -C packages/core build:native`).
//
// Usage: tsx scripts/sync-demo-corpus.ts ios|macos|android|desktop  (pnpm build:<target> runs both steps)

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { REFERENCE_STYLESHEET } from '../packages/core/src/shared/reference.ts';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const TARGETS = {
  ios: 'apps/ios/DemoApp/Corpus',
  // The macOS demo references the iOS demo's corpus folder.
  macos: 'apps/ios/DemoApp/Corpus',
  android: 'apps/android/demo/src/main/assets/Corpus',
  desktop: 'packages/desktop/Corpus',
} as const;

const targetName = (process.argv[2] ?? 'ios') as keyof typeof TARGETS;
const target = TARGETS[targetName];
if (!target) {
  console.error(`unknown target "${targetName}"; expected one of ${Object.keys(TARGETS).join(', ')}`);
  process.exit(1);
}

const corpusOut = path.join(root, target);
fs.rmSync(corpusOut, { recursive: true, force: true });
const subGroups = (parent: string) => {
  const dir = path.join(root, 'corpus', parent);
  return fs.existsSync(dir) ? fs.readdirSync(dir).filter((d) => fs.statSync(path.join(dir, d)).isDirectory()).sort().map((d) => `${parent}/${d}`) : [];
};
// htmlbubbles-ported/<chat>: Telegram's demos as classic widgets; common/<batch>: the renderer-comparison corpus (corpus/common/README.md);
// showcase/<batch>: the interactive, animated demos on the full web profile (corpus/showcase/README.md);
// defaults/<batch>: widgets with little or no CSS, drawn by the renderer's UA layer (corpus/defaults/README.md);
// safe-area/<batch>: the client's time over the corner of a widget without a caption (corpus/safe-area/README.md).
for (const group of ['compat', 'attacks', 'generated/with-profile', 'generated/without-profile', ...subGroups('htmlbubbles-ported'), ...subGroups('common'), ...subGroups('showcase'), ...subGroups('defaults'), ...subGroups('safe-area')]) {
  const dir = path.join(root, 'corpus', group);
  if (!fs.existsSync(dir)) continue;
  fs.mkdirSync(path.join(corpusOut, group), { recursive: true });
  for (const f of fs.readdirSync(dir)) if (f.endsWith('.html')) fs.copyFileSync(path.join(dir, f), path.join(corpusOut, group, f));
}
// The demos' reference mode puts it into the widget file: the renderer's UA layer with the reference theme.
fs.writeFileSync(path.join(corpusOut, 'reference.css'), REFERENCE_STYLESHEET);
console.log(`corpus copied to ${target}`);

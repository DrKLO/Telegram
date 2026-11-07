// Clears per-test result files of the compat / generated suites so a report
// never mixes runs (screenshots are simply overwritten).
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

export default function globalSetup() {
  const root = path.join(path.dirname(fileURLToPath(import.meta.url)), '..');
  for (const dir of ['compat', 'generated']) {
    const d = path.join(root, 'reports', dir);
    if (!fs.existsSync(d)) continue;
    for (const f of fs.readdirSync(d)) if (f.endsWith('.result.json')) fs.unlinkSync(path.join(d, f));
  }
}

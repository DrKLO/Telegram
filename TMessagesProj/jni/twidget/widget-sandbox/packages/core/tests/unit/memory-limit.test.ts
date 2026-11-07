// The memory limit on the wasm build. quickjs-ng's own counter sees 0 bytes per
// allocation under emscripten (no malloc_usable_size), so `setMemoryLimit` alone
// let a widget grow the runtime past 1 GB. The glue caps the wasm linear memory
// instead (see "memory limit" in src/host/worker.ts; the Node harness mirrors
// it). Since the wasm is built from the TelegramMessenger/quickjs fork
// (quickjs-ng 0.17, like the native builds) out-of-memory is uncatchable: the
// allocation ends the task with `InternalError: out of memory`, which the worker
// and the native glue turn into a kill. These run the memory attacks from
// corpus/attacks/ in-process.

import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { describe, it } from 'node:test';
import { fileURLToPath } from 'node:url';
import { runWidget } from '../../scripts/sandbox-node.ts';
import { LIMIT_DEFAULTS, WASM_INITIAL_PAGES, WASM_PAGE_BYTES } from '../../src/shared/protocol.ts';

const attack = (f: string) => fs.readFileSync(path.join(path.dirname(fileURLToPath(import.meta.url)), '..', '..', '..', '..', 'corpus', 'attacks', f), 'utf8');
const errorsOf = (r: Awaited<ReturnType<typeof runWidget>>) => r.logs.filter((l) => l.level === 'error').map((l) => l.text);
const FOOTPRINT_CAP = WASM_INITIAL_PAGES * WASM_PAGE_BYTES + LIMIT_DEFAULTS.memoryLimitBytes;

describe('memory limit', () => {
  it('steady growth of 1 MB typed arrays is refused at the cap; the widget cannot catch it and the footprint stays bounded', async () => {
    const r = await runWidget(attack('mem-steady-growth.html'), { timerRounds: 30, probes: ["document.getElementById('mb').textContent"] });
    const reachedMb = parseInt(r.probes[0], 10);
    assert.ok(reachedMb >= 4 && reachedMb <= 16, `reached ${reachedMb} MB (${r.probes[0]})`);
    assert.ok(!/alloc refused/.test(r.probes[0]), 'out of memory is not catchable');
    assert.ok(errorsOf(r).some((t) => /InternalError: out of memory/.test(t)), JSON.stringify(errorsOf(r)));
    assert.ok(r.wasmBytes <= FOOTPRINT_CAP, `wasm memory ${r.wasmBytes} > ${FOOTPRINT_CAP}`);
  });

  it('the same growth without a try/catch surfaces as an uncaught out-of-memory error (the worker kills on it)', async () => {
    const r = await runWidget(attack('mem-uncaught-growth.html'), { timerRounds: 30 });
    assert.ok(errorsOf(r).some((t) => /InternalError: out of memory/.test(t)), JSON.stringify(errorsOf(r)));
    assert.ok(r.wasmBytes <= FOOTPRINT_CAP, `wasm memory ${r.wasmBytes} > ${FOOTPRINT_CAP}`);
  });

  it('a synchronous allocation loop is capped inside one task (the engine-agnostic probe)', async () => {
    // The probe prints through window.print when it exists (the shim stubs it), so read its verdict from the function itself.
    const r = await runWidget(attack('mem-cap-probe.html'), { timerRounds: 0, probes: ['memCapProbe()'] });
    assert.match(r.probes[0], /^ERROR .*"out of memory"/);
    assert.ok(r.wasmBytes <= FOOTPRINT_CAP, `wasm memory ${r.wasmBytes} > ${FOOTPRINT_CAP}`);
  });

  it('one 256 MB allocation is refused outright and the runtime does not grow for it', async () => {
    const r = await runWidget(attack('mem-burst-alloc.html'), { probes: ["(function () { try { var b = new Uint8Array(256 * 1048576); b[0] = 1; return 'allocated'; } catch (e) { return e.name + ': ' + e.message; } })()"] });
    assert.match(r.probes[0], /^ERROR .*"out of memory"/);
    assert.ok(r.wasmBytes < 48 * 1024 * 1024, `wasm memory ${r.wasmBytes}`);
  });

  it('another limit is honoured (24 MB)', async () => {
    const r = await runWidget(attack('mem-steady-growth.html'), { timerRounds: 30, memoryLimitBytes: 24 << 20, probes: ["document.getElementById('mb').textContent"] });
    const reachedMb = parseInt(r.probes[0], 10);
    assert.ok(reachedMb > 16 && reachedMb <= 40, r.probes[0]);
    assert.ok(errorsOf(r).some((t) => /InternalError: out of memory/.test(t)), JSON.stringify(errorsOf(r)));
    assert.ok(r.wasmBytes <= WASM_INITIAL_PAGES * WASM_PAGE_BYTES + (24 << 20));
  });
});

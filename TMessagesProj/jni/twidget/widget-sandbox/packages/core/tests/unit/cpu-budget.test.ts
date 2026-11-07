// The CPU quota policy against the fixture shared with CpuBudget.swift,
// CpuBudget.kt and cpu_budget.cpp (tests/fixtures/cpu-budget.json), plus edge cases.

import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { describe, it } from 'node:test';
import { fileURLToPath } from 'node:url';
import { CpuBudget, opsCostMs, queryLimitHit } from '../../src/host/cpu-budget.ts';

interface Step { at: number; decide?: string; record?: string; cpuMs?: number; expect?: unknown; kill?: boolean; used?: number; queries?: [number, number]; opsCost?: [number, number] }
const fixture = JSON.parse(fs.readFileSync(path.join(path.dirname(fileURLToPath(import.meta.url)), '..', 'fixtures', 'cpu-budget.json'), 'utf8')) as {
  cases: { name: string; config: ConstructorParameters<typeof CpuBudget>[0]; steps: Step[] }[];
};

describe('cpu-budget: shared fixture', () => {
  for (const c of fixture.cases) {
    it(c.name, () => {
      const b = new CpuBudget(c.config);
      c.steps.forEach((s, i) => {
        const where = `step ${i} @${s.at}`;
        if (s.decide !== undefined) assert.deepEqual(b.decide(s.decide, s.at), s.expect, where);
        else if (s.record !== undefined) b.record(s.record, s.cpuMs!, s.at);
        else if (s.kill !== undefined) assert.equal(b.shouldKill(s.at), s.kill, where);
        else if (s.used !== undefined) assert.ok(Math.abs(b.used(s.at) - s.used) < 1e-9, `${where}: used ${b.used(s.at)}, expected ${s.used}`);
        else if (s.queries !== undefined) assert.equal(queryLimitHit(s.queries[0], s.queries[1]), s.expect, where);
        else if (s.opsCost !== undefined) assert.ok(Math.abs(opsCostMs(s.opsCost[0], s.opsCost[1]) - (s.expect as number)) < 1e-9, where);
        else assert.fail(`${where}: unknown step`);
      });
    });
  }
});

describe('cpu-budget: edge cases', () => {
  const cfg = { cpuLimitMs: 500, cpuQuotaMs: 300, cpuQuotaWindowMs: 1000, cpuKillAfterMs: 0 };
  it('an empty window runs everything with the whole slice', () => {
    const b = new CpuBudget(cfg);
    assert.equal(b.used(123_456), 0);
    assert.deepEqual(b.decide('frame', 123_456), { run: true, sliceMs: 500 });
    assert.equal(b.throttledFor(123_456), 0);
  });
  it('time spent long ago is forgotten', () => {
    const b = new CpuBudget(cfg);
    b.record('timer', 1000, 10);
    assert.equal(b.used(10), 600); // the window holds the quota, the debt at most one more
    assert.equal(b.used(10_000), 0);
    assert.deepEqual(b.decide('timer', 10_000), { run: true, sliceMs: 500 });
  });
  it('events get the whole slice even with the quota spent many times over', () => {
    const b = new CpuBudget(cfg);
    b.record('timer', 50_000, 0);
    assert.deepEqual(b.decide('event', 1), { run: true, sliceMs: 500 });
    assert.deepEqual(b.decide('timer', 1), { run: false, retryAt: 1000 });
  });
  it('negative or NaN durations are ignored', () => {
    const b = new CpuBudget(cfg);
    b.record('timer', -5, 0);
    b.record('timer', NaN, 0);
    assert.equal(b.used(0), 0);
  });
});

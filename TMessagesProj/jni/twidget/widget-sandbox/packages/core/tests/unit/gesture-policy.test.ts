// src/shared/gesture-policy.ts against the fixture shared with GesturePolicy.swift,
// GesturePolicy.kt and gesture_policy.cpp (tests/fixtures/gesture-policy.json).

import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { describe, it } from 'node:test';
import { fileURLToPath } from 'node:url';
import * as policy from '../../src/shared/gesture-policy.ts';
import { GestureArbiter, claimOf, type GestureClaim, type PointerKind } from '../../src/shared/gesture-policy.ts';

type Point = [number, number, number];
interface Step { down?: Point; move?: Point; up?: Point; tick?: number; cancel?: boolean; holdDeadline?: number | null; claim?: string; claimed?: GestureClaim; pointer?: PointerKind; button?: number; expect?: unknown }
const fixture = JSON.parse(fs.readFileSync(path.join(path.dirname(fileURLToPath(import.meta.url)), '..', 'fixtures', 'gesture-policy.json'), 'utf8')) as {
  constants: Record<string, number>;
  claims: { touchActions: string[]; prevented: boolean; expect: unknown }[];
  cases: { name: string; steps: Step[] }[];
};

describe('gesture-policy fixture', () => {
  it('constants', () => {
    for (const [name, value] of Object.entries(fixture.constants)) assert.equal((policy as Record<string, unknown>)[name], value, name);
  });
  it('claims', () => {
    for (const c of fixture.claims) assert.deepEqual(claimOf(c.touchActions, c.prevented), c.expect, `${JSON.stringify(c.touchActions)} prevented ${c.prevented}`);
  });
  for (const c of fixture.cases) {
    it(c.name, () => {
      const a = new GestureArbiter();
      c.steps.forEach((s, i) => {
        const where = `step ${i} ${JSON.stringify(s).slice(0, 80)}`;
        if (s.down) assert.deepEqual(a.down(...s.down, s.claimed ?? claimOf([s.claim ?? 'auto']), s.pointer ?? 'touch', s.button ?? 0), s.expect, where);
        else if (s.move) assert.deepEqual(a.move(...s.move), s.expect, where);
        else if (s.up) assert.deepEqual(a.up(...s.up), s.expect, where);
        else if (s.tick !== undefined) assert.deepEqual(a.tick(s.tick), s.expect, where);
        else if (s.cancel) a.cancel();
        else if ('holdDeadline' in s) assert.equal(a.holdDeadline(), s.holdDeadline, where);
        else assert.fail(`${where}: unknown step`);
      });
    });
  }
});

describe('gesture-policy: edge cases', () => {
  it('a verdict is a fresh object: callers may keep it', () => {
    const a = new GestureArbiter();
    const v = a.down(0, 100, 50, claimOf(['auto']));
    a.tick(500);
    assert.deepEqual(v, { owner: 'pending', gesture: 'press', cancel: false });
  });
  it('NO_CLAIM cannot be changed by a caller', () => {
    assert.throws(() => { (policy.NO_CLAIM as { x: boolean }).x = true; });
  });
});

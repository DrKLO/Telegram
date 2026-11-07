// src/shared/paint-policy.ts against tests/fixtures/paint-policy.json, the fixture a client's own renderer counts
// heavy boxes against (docs/decisions.md, "Heavy boxes").

import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { describe, it } from 'node:test';
import { fileURLToPath } from 'node:url';
import * as policy from '../../src/shared/paint-policy.ts';
import { PaintCounter, animationPaint, boxPaint, boxShadowBlurred, boxShadowWithoutBlur, checkPaintLimit, hidesSubtree, paintLimitMessage, type PaintKeyframe, type PaintLimitCode, type PaintStyle } from '../../src/shared/paint-policy.ts';

type Keys = string | string[];
interface Step { set?: Keys; style?: PaintStyle; pseudo?: boolean; remove?: Keys; start?: Keys; end?: Keys; animation?: string; keyframes?: PaintKeyframe[]; check?: { heavy: number; backdrop: number; stop: unknown } }
const fixture = JSON.parse(fs.readFileSync(path.join(path.dirname(fileURLToPath(import.meta.url)), '..', 'fixtures', 'paint-policy.json'), 'utf8')) as {
  constants: Record<string, number>;
  shadows: { value: string; blurred: boolean; withoutBlur: string }[];
  boxes: { name: string; pseudo: boolean; style: PaintStyle; expect: unknown }[];
  hidesSubtree: { display: string; expect: boolean }[];
  animations: { name: string; keyframes: PaintKeyframe[]; expect: unknown }[];
  messages: { code: PaintLimitCode; count: number; expect: string }[];
  reports: { code?: unknown; count?: unknown; expect: unknown }[];
  cases: { name: string; steps: Step[] }[];
};
const keys = (k: Keys) => (Array.isArray(k) ? k : [k]);

describe('paint-policy fixture', () => {
  it('constants', () => {
    for (const [name, value] of Object.entries(fixture.constants)) assert.equal((policy as Record<string, unknown>)[name], value, name);
  });
  it('shadows', () => {
    for (const s of fixture.shadows) {
      assert.equal(boxShadowBlurred(s.value), s.blurred, JSON.stringify(s.value));
      assert.equal(boxShadowWithoutBlur(s.value), s.withoutBlur, JSON.stringify(s.value));
    }
  });
  it('boxes', () => {
    for (const b of fixture.boxes) assert.deepEqual(boxPaint(b.style, b.pseudo), b.expect, b.name);
  });
  it('hidesSubtree', () => {
    for (const h of fixture.hidesSubtree) assert.equal(hidesSubtree(h.display), h.expect, h.display);
  });
  it('animations', () => {
    for (const a of fixture.animations) assert.deepEqual(animationPaint(a.keyframes), a.expect, a.name);
  });
  it('messages', () => {
    for (const m of fixture.messages) assert.equal(paintLimitMessage(m.code, m.count), m.expect);
  });
  it('reports', () => {
    for (const r of fixture.reports) assert.deepEqual(checkPaintLimit(r.code, r.count), r.expect, JSON.stringify(r));
  });
  for (const c of fixture.cases) {
    it(c.name, () => {
      const counter = new PaintCounter<string>();
      c.steps.forEach((s, i) => {
        const where = `step ${i} ${JSON.stringify(s).slice(0, 100)}`;
        if (s.set !== undefined) for (const k of keys(s.set)) counter.setBox(k, boxPaint(s.style ?? {}, s.pseudo === true));
        else if (s.remove !== undefined) for (const k of keys(s.remove)) counter.removeBox(k);
        else if (s.start !== undefined) {
          const paint = animationPaint(s.keyframes ?? []);
          if (paint) for (const k of keys(s.start)) counter.startAnimation(k, s.animation!, paint);
        } else if (s.end !== undefined) for (const k of keys(s.end)) counter.endAnimation(k, s.animation!);
        else if (s.check) assert.deepEqual({ heavy: counter.heavy, backdrop: counter.backdrop, stop: counter.check() }, s.check, where);
        else assert.fail(`${where}: unknown step`);
      });
    });
  }
});

describe('paint-policy: edge cases', () => {
  it('the peaks follow the counts up, never down', () => {
    const c = new PaintCounter<number>();
    for (let i = 0; i < 5; i++) c.setBox(i, { heavy: true, backdrop: i < 2 });
    for (let i = 0; i < 5; i++) c.removeBox(i);
    assert.deepEqual([c.heavy, c.backdrop, c.peakHeavy, c.peakBackdrop], [0, 0, 5, 2]);
  });
  it('a box set to no box keeps nothing when it has no animations', () => {
    const c = new PaintCounter<number>();
    c.setBox(1, null);
    c.setBox(2, { heavy: true, backdrop: false });
    c.setBox(2, null);
    assert.equal(c.heavy, 0);
    assert.equal(c.animating(2, 'a:x'), false);
  });
});

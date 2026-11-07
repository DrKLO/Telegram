// src/renderer/touch-action.ts: the touch-action the engine gets always allows vertical panning (v0.1.3), so a
// browser on a touch screen scrolls the page over any widget; the claim reads the widget's own value.

import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { engineTouchAction } from '../../src/renderer/touch-action.ts';
import { claimOf } from '../../src/shared/gesture-policy.ts';

describe('engineTouchAction', () => {
  it('widens every value to vertical panning in both directions', () => {
    const cases: [string, string][] = [
      ['auto', 'auto'], ['manipulation', 'manipulation'], ['', ''], ['inherit', 'inherit'], ['initial', 'initial'], ['unset', 'unset'],
      ['none', 'pan-y'], ['NONE', 'pan-y'], [' none ', 'pan-y'],
      ['pan-y', 'pan-y'], ['pan-y pinch-zoom', 'pan-y pinch-zoom'], ['pan-x pan-y', 'pan-x pan-y'],
      ['pan-x', 'pan-x pan-y'], ['pan-left', 'pan-left pan-y'], ['pan-right pinch-zoom', 'pan-right pinch-zoom pan-y'],
      ['pan-up', 'pan-y'], ['pan-down', 'pan-y'], ['pan-left pan-down', 'pan-left pan-y'], ['pinch-zoom', 'pinch-zoom pan-y'],
    ];
    for (const [value, engine] of cases) assert.equal(engineTouchAction(value), engine, JSON.stringify(value));
  });

  it('the widened value never claims anything the widget did not, and keeps its horizontal claim', () => {
    for (const v of ['auto', 'none', 'pan-x', 'pan-y', 'pan-left', 'pan-up', 'pinch-zoom', 'manipulation', 'pan-x pan-y']) {
      assert.equal(claimOf([engineTouchAction(v)]).x, v === 'none' || claimOf([v]).x, v);
    }
  });
});

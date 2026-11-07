// Worker and renderer messages that are not plain objects with a string type, or carry
// non-finite numbers where the host expects numbers, are dropped before dispatch.

import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { finiteNumber, isTypedMessage } from '../../src/host/message-guards.ts';

describe('message guards', () => {
  it('accepts only plain objects with a string type', () => {
    assert.equal(isTypedMessage({ type: 'ops', ops: [] }), true);
    for (const bad of [null, undefined, 1, 'ops', [], ['ops'], { type: 1 }, { type: null }, {}, { type: { toString: () => 'ops' } }]) {
      assert.equal(isTypedMessage(bad), false, JSON.stringify(bad) ?? String(bad));
    }
  });

  it('keeps only finite numbers', () => {
    assert.equal(finiteNumber(0), 0);
    assert.equal(finiteNumber(-3.5), -3.5);
    for (const bad of [NaN, Infinity, -Infinity, '12', null, undefined, {}, [], 1n]) {
      assert.equal(finiteNumber(bad), undefined, String(bad));
    }
  });
});

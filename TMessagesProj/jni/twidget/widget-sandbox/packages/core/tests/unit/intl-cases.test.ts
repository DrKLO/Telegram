// tests/fixtures/intl-cases.json holds V8's answers to the intl requests the native copies are
// checked against (IntlBridgeTests.swift, IntlBridgeTest.kt, the desktop tests). Stale after a
// case edit or an ICU update of Node: run scripts/intl-cases.ts.

import assert from 'node:assert/strict';
import fs from 'node:fs';
import { describe, it } from 'node:test';
import { buildCases, FIXTURE, render } from '../../scripts/intl-cases.ts';

describe('intl: the shared fixture', () => {
  it("is V8's answers to its cases", () => {
    assert.equal(fs.readFileSync(FIXTURE, 'utf8'), render(buildCases()), 'stale: run corepack pnpm tsx packages/core/scripts/intl-cases.ts');
  });
});

// The widget's size as the server counts it (src/shared/widget-size.ts, the harness's `size:` line).

import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { MINI_HTML, sizeLine, sizeVerdict } from '../../src/shared/widget-size.ts';
import { widgetSize } from '../../scripts/sandbox-node.ts';

describe('widget size', () => {
  it('counts zlib at level 9 and UTF-8 bytes', () => {
    const s = widgetSize('<p>привет</p>');
    assert.equal(s.raw, 19);
    assert.ok(s.zlib > 10 && s.zlib < 60, String(s.zlib));
  });
  it('says nothing under 3800 B, warns up to 4096 B, errs past either limit', () => {
    assert.deepEqual(sizeVerdict({ zlib: 3800, raw: 9000 }), { level: 'ok', message: null });
    assert.equal(sizeVerdict({ zlib: 3801, raw: 9000 }).level, 'warning');
    assert.equal(sizeVerdict({ zlib: MINI_HTML.zlib, raw: 9000 }).level, 'warning');
    const big = sizeVerdict({ zlib: 4097, raw: 40000 });
    assert.equal(big.level, 'error');
    assert.match(big.message!, /INPUT_MEDIA_WIDGET_TOO_LARGE.*over the 4096 B limit by 1 B; 40000 B of HTML is over the 32768 B limit by 7232 B/);
    assert.equal(sizeLine({ zlib: 2134, raw: 10035 }), 'size: 2134 B zlib / 9.8 KB raw (limit 4096 / 32768)');
  });
});

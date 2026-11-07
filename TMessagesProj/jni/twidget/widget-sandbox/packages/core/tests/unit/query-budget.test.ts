// The layout-query limit through the Node harness (the twin of QueryBudgetTests.swift,
// QueryBudgetTest.kt and the desktop layout_query_loop tests): a forced-layout loop is
// interrupted and reported with the layout-query reason, past 128 answered queries in one task
// or past 1000 ms of their time; an ordinary layout-heavy widget is not. The harness answers
// queries in-process, so `answer` stands in for a slow renderer.

import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { runWidget } from '../../scripts/sandbox-node.ts';
import { QUERY_BUDGET_MS, QUERY_LIMIT, queryLimitHit } from '../../src/host/cpu-budget.ts';

const busy = (ms: number) => { const t = performance.now(); while (performance.now() - t < ms) { /* a slow renderer */ } };
const html = (script: string) => `<div id="a"></div><script>${script}</script>`;

describe('layout-query budget', () => {
  it('is 128 answered queries or 1000 ms of their time in one task, the count first', () => {
    assert.equal(QUERY_LIMIT, 128);
    assert.equal(QUERY_BUDGET_MS, 1000);
    assert.equal(queryLimitHit(128, 1000), null);
    assert.equal(queryLimitHit(129, 0), 'count');
    assert.equal(queryLimitHit(129, 2000), 'count');
    assert.equal(queryLimitHit(1, 1000.5), 'time');
  });

  it('kills a forced-layout loop with the layout-query reason', async () => {
    const started = performance.now();
    const r = await runWidget(html("const a = document.getElementById('a'); let i = 0; for (;;) { a.style.width = (i++ % 300) + 'px'; a.offsetHeight; }"), {
      cpuLimitMs: 200,
      answer: () => { busy(5); return undefined; }, // the canned answer, after a slow renderer's delay
    });
    const reason = r.logs.find((l) => l.level === 'error' && /Layout query limit exceeded/.test(l.text));
    assert.ok(reason, JSON.stringify(r.logs.slice(0, 3)));
    assert.match(reason!.text, /more than 128 layout queries in one boot task/);
    assert.ok(r.diags.some((d) => d.kind === 'query-limit'));
    assert.equal(r.maxTaskQueries, QUERY_LIMIT + 1, 'the host answered 129 queries; the rest got the error until the interrupt landed');
    assert.ok(performance.now() - started < 5000, 'the harness returned promptly');
  });

  it('stops a task whose queries block the host past 1000 ms in all (a hung renderer)', async () => {
    const r = await runWidget(html("const a = document.getElementById('a'); for (let i = 0; i < 60; i++) { a.style.width = i + 'px'; a.offsetHeight; }"), {
      cpuLimitMs: 200,
      answer: () => { busy(30); return undefined; },
    });
    const reason = r.logs.find((l) => l.level === 'error' && /Layout query limit exceeded/.test(l.text));
    assert.ok(reason, JSON.stringify(r.logs.slice(0, 3)));
    assert.match(reason!.text, /blocked the renderer for more than 1000 ms in one boot task/);
    assert.ok(r.maxTaskQueries < 40, `${r.maxTaskQueries} queries answered`);
  });

  it('leaves a widget with 100 layout queries in one task alive (conformance auto/03-layout-a makes 47)', async () => {
    const r = await runWidget(html("const a = document.getElementById('a'); for (let i = 0; i < 100; i++) { a.style.width = i + 'px'; a.offsetHeight; }"), {
      cpuLimitMs: 200,
      answer: () => { busy(2); return undefined; },
    });
    assert.equal(r.logs.filter((l) => l.level === 'error').length, 0, JSON.stringify(r.logs));
    assert.equal(r.queries.length, 100);
  });

  it('leaves an ordinary layout-heavy widget alive', async () => {
    const r = await runWidget(html("const a = document.getElementById('a'); for (let i = 0; i < 10; i++) { a.style.width = i + 'px'; a.offsetHeight; }"), {
      cpuLimitMs: 200,
      answer: () => { busy(2); return undefined; },
    });
    assert.equal(r.logs.filter((l) => l.level === 'error').length, 0, JSON.stringify(r.logs));
    assert.ok(r.queries.length > 0);
  });
});

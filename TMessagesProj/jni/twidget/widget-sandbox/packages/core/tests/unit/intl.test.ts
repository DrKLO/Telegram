// Intl is delegated to the host's ICU (`__host('intl', …)`). The ECMA-402 option defaults that need
// no locale data live in the shim, so every host's formatter gets explicit fields: a host without
// them (the C++ BasicIntl, tdesktop's IntlProvider) formats what a browser formats.

import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { runWidget } from '../../scripts/sandbox-node.ts';

describe('shim: Intl date defaults (ECMA-402 ToDateTimeOptions)', () => {
  it('send explicit fields for formats without a field or a style', async () => {
    const sent: any[] = [];
    await runWidget(`<script>
      var o = { timeZone: 'UTC' }, d = new Date(0);
      new Intl.DateTimeFormat('en-US', o).format(d);
      d.toLocaleString('en-US', o); d.toLocaleDateString('en-US', o); d.toLocaleTimeString('en-US', o);
      d.toLocaleTimeString('en-US', { timeZone: 'UTC', year: 'numeric' });
      d.toLocaleDateString('en-US', { timeZone: 'UTC', hour: 'numeric' });
      new Intl.DateTimeFormat('en-US', { era: 'short' }).format(d);
      new Intl.DateTimeFormat('en-US', { weekday: 'long', year: undefined }).format(d);
      new Intl.DateTimeFormat('en-US', { timeStyle: 'short' }).format(d);
      d.toLocaleDateString();
    </script>`, { intl: (req) => { sent.push(req); return undefined; } });
    const ymd = { year: 'numeric', month: 'numeric', day: 'numeric' }, hms = { hour: 'numeric', minute: 'numeric', second: 'numeric' }, utc = { timeZone: 'UTC' };
    assert.deepEqual(sent.map((r) => [r.method ?? r.kind, r.opts]), [
      ['date', { ...utc, ...ymd }],
      ['toLocaleString', { ...utc, ...ymd, ...hms }],
      ['toLocaleDateString', { ...utc, ...ymd }],
      ['toLocaleTimeString', { ...utc, ...hms }],
      ['toLocaleTimeString', { ...utc, year: 'numeric', ...hms }],
      ['toLocaleDateString', { ...utc, hour: 'numeric', ...ymd }],
      ['date', { era: 'short', ...ymd }],
      ['date', { weekday: 'long' }],
      ['date', { timeStyle: 'short' }],
      ['toLocaleDateString', ymd],
    ]);
  });

  it('format what the browser formats, and leave the options object alone', async () => {
    const r = await runWidget(`<script>
      var o = { timeZone: 'UTC' }, d = new Date(0), f = new Intl.DateTimeFormat('en-US', o);
      var res = [f.format(d), d.toLocaleDateString('en-US', o), d.toLocaleTimeString('en-US', o), f.resolvedOptions().year, JSON.stringify(o)].join('|');
    </script>`, { probes: ['res'] });
    const d = new Date(0), o = { timeZone: 'UTC' };
    assert.equal(r.probes[0], [new Intl.DateTimeFormat('en-US', o).format(d), d.toLocaleDateString('en-US', o), d.toLocaleTimeString('en-US', o), 'numeric', '{"timeZone":"UTC"}'].join('|'));
  });
});

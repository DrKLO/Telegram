import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { runWidget } from '../../scripts/sandbox-node.ts';
import { Validator } from '../../src/host/validator.ts';

const errorsOf = (r: Awaited<ReturnType<typeof runWidget>>) => r.logs.filter((l) => l.level === 'error').map((l) => l.text);

describe('scrollIntoView options', () => {
  it('accepts boolean and dictionary forms but reaches no renderer: scrollIntoView is outside the profile', async () => {
    const r = await runWidget(`<div id=target></div><script>
      const el=document.getElementById('target');
      el.scrollIntoView();
      el.scrollIntoView(false);
      el.scrollIntoView(null);
      el.scrollIntoView({block:'center',inline:'end',behavior:'smooth',container:'nearest'});
      el.scrollIntoView({block:'nearest',inline:'center',behavior:'instant'});
    </script>`, { timerRounds: 0 });
    assert.deepEqual(errorsOf(r), []);
    assert.equal(r.renderOps.filter((op: any) => op.op === 'call').length, 0);
  });

  it('rejects invalid enums before emitting a call', async () => {
    const r = await runWidget(`<div id=target></div><script>
      var errors=[];
      for (const o of [{block:'middle'},{inline:null},{behavior:'fast'},{container:'root'}]) {
        try { document.getElementById('target').scrollIntoView(o); } catch(e) { errors.push(e.name); }
      }
    </script>`, { timerRounds: 0, probes: ['JSON.stringify(errors)'] });
    assert.deepEqual(r.probes, ['["TypeError","TypeError","TypeError","TypeError"]']);
    assert.equal(r.renderOps.filter((op: any) => op.op === 'call').length, 0);
  });

  it('the validator refuses the call (the shim never sends it)', () => {
    const v = new Validator(() => {});
    v.processOps([{op:'el',id:2,tag:'html'}, {op:'ins',id:2,parent:1}, {op:'el',id:3,tag:'body'}, {op:'ins',id:3,parent:2}]);
    const call = (args?: unknown) => v.processOps([{op:'call',id:3,method:'scrollIntoView',args}]);
    for (const args of [undefined, [], [0,3,0,0], [3,2,2,1], [4,3,0,0], null]) assert.equal(call(args).length, 0);
  });
});

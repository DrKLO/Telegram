import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { runWidget } from '../../scripts/sandbox-node.ts';

// linkedom gives form, dialog, output, progress, meter and others no class of their own: their members must stay
// theirs (forms.ts, "elements without a class of their own").
describe('members of elements without a class of their own', () => {
  it('an inline handler calls the widget function, not another element member', async () => {
    const html = `<div id=c>5</div><button id=r onclick="reset()">Reset</button><button id=s onclick="showModal();show()">S</button>
<script>var seen=[];function reset(){document.getElementById('c').textContent='0';seen.push('reset')}
function showModal(){seen.push('showModal')}function show(){seen.push('show')}
document.getElementById('r').click();document.getElementById('s').click();</script>`;
    const r = await runWidget(html, { probes: ['JSON.stringify(seen)', "document.getElementById('c').textContent"] });
    assert.deepEqual(r.probes, ['["reset","showModal","show"]', '0']);
  });

  it('a form handler still sees the form members', async () => {
    const html = `<form id=f onsubmit="seen.push(typeof reset, typeof elements, length);return false"><input name=a></form>
<script>var seen=[];document.getElementById('f').requestSubmit();</script>`;
    const r = await runWidget(html, { probes: ['JSON.stringify(seen)'] });
    assert.deepEqual(r.probes, ['["function","object",1]']);
  });

  it('keeps each element its own value', async () => {
    const html = `<progress id=p value=3 max=4></progress><meter id=m value=0.5></meter><output id=o>out</output><div id=d></div>
<script>var p=document.getElementById('p'),m=document.getElementById('m'),o=document.getElementById('o'),d=document.getElementById('d');
d.value='mine';</script>`;
    const r = await runWidget(html, {
      probes: ['JSON.stringify([p.value,p.max,m.value,o.value,d.value,typeof d.reset,typeof d.showModal,d.form===undefined])'],
    });
    assert.deepEqual(r.probes, ['[3,4,0.5,"out","mine","undefined","undefined",true]']);
  });

  it('keeps assignments to a member on the element', async () => {
    const html = `<form id=f></form><dialog id=g></dialog>
<script>var f=document.getElementById('f'),g=document.getElementById('g');f.checkValidity=function(){return 'own'};g.close('done');</script>`;
    const r = await runWidget(html, { probes: ['f.checkValidity()', 'g.returnValue', 'document.createElement("dialog").returnValue'] });
    assert.deepEqual(r.probes, ['own', 'done', '']);
  });
});

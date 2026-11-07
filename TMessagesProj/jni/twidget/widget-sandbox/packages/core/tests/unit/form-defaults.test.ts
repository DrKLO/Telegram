import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { runWidget } from '../../scripts/sandbox-node.ts';

async function click(html: string, id: string, defaultActions = true) {
  const first = await runWidget(html, { timerRounds: 0 });
  const target = first.renderOps.find((op: any) => op.op === 'el' && op.attrs.some(([key, value]: string[]) => key === 'id' && value === id)) as any;
  assert.ok(target, id);
  return runWidget(html, {
    timerRounds: 0,
    events: [{ type: 'click', targetId: target.id, defaultActions }],
    probes: ['JSON.stringify(seen)'],
  });
}

const listen = `<script>var seen=[];document.addEventListener('submit',e=>{e.preventDefault();seen.push([e.target.id,e.submitter&&e.submitter.id])});</script>`;

describe('form activation defaults', () => {
  it('submits the owner once when a native click lands on a button descendant', async () => {
    const r = await click(`<form id=f><button id=b><span id=inner>Send</span></button></form>${listen}`, 'inner');
    assert.deepEqual(r.probes, ['[["f","b"]]']);
  });

  it('handles invalid button types as submit buttons', async () => {
    for (const control of ['<button id=b type=invalid>Send</button>']) {
      assert.deepEqual((await click(`<form id=f>${control}</form>${listen}`, 'b')).probes, ['[["f","b"]]']);
    }
  });

  it('does not duplicate a submit already performed by the browser renderer', async () => {
    assert.deepEqual((await click(`<form id=f><button id=b>Send</button></form>${listen}`, 'b', false)).probes, ['[]']);
  });

  it('respects cancellation on the first click and ignores ordinary or disabled buttons', async () => {
    for (const control of ['<button id=b type=button>Other</button>', '<button id=b disabled>Send</button>', '<fieldset disabled><button id=b>Send</button></fieldset>']) {
      assert.deepEqual((await click(`<form id=f>${control}</form>${listen}`, 'b')).probes, ['[]']);
    }
    assert.deepEqual((await click(`<form id=f><button id=b>Send</button></form>${listen}<script>document.addEventListener('click',e=>e.preventDefault());</script>`, 'b')).probes, ['[]']);
  });

  it('honors explicit form association, including missing owners', async () => {
    const html = `<form id=f></form><form id=other><button id=b form=f>Send</button></form>${listen}`;
    assert.deepEqual((await click(html, 'b')).probes, ['[["f","b"]]']);
    assert.deepEqual((await click(html.replace('form=f', 'form=missing'), 'b')).probes, ['[]']);
  });

  it('preserves the first-legend exception of a disabled fieldset', async () => {
    assert.deepEqual((await click(`<form id=f><fieldset disabled><legend><button id=b>Send</button></legend></fieldset></form>${listen}`, 'b')).probes, ['[["f","b"]]']);
  });

  it('submits synthetic clicks and respects validity and validation bypass', async () => {
    const base = `<form id=f><button id=b>Send</button></form>${listen}`;
    for (const [setup, expected] of [['', '[["f","b"]]'], ['document.getElementById("f").checkValidity=()=>false;', '[]'], ['document.getElementById("f").checkValidity=()=>false;document.getElementById("b").setAttribute("formnovalidate","");', '[["f","b"]]']]) {
      const r = await runWidget(`${base}<script>${setup}document.getElementById('b').click();</script>`, { probes: ['JSON.stringify(seen)'] });
      assert.deepEqual(r.probes, [expected]);
    }
  });
});

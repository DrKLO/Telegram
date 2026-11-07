// src/shared/sdk-policy.ts against the fixture shared with SdkPolicy.swift,
// SdkPolicy.kt and sdk_policy.cpp (tests/fixtures/sdk-policy.json), plus the
// TS-only parts: theme defaults and the renderer's variables.

import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { describe, it } from 'node:test';
import { fileURLToPath } from 'node:url';
import {
  HapticGate, RequestGate, THEME_KEYS, allowedInPreview, THEME_VARIABLES, checkBridgeCall, checkChatType, checkCopyText, checkHaptic, checkLink, checkLocale, checkRequestAnswer,
  checkRequestData, checkSignDataPayload, checkStorageCommit, checkThemeParams, checkViewer, checkWalletTransaction, checkWidgetData, DEFAULT_TEXT_TOKENS, completeThemeParams, requestFailure,
  requestRefusal, viewerFromList, viewerUser, themeVariables, utf8Length, walletFailure, type LinkMethod, type RequestVerdict,
} from '../../src/shared/sdk-policy.ts';
import { PROFILE_UA_STYLESHEET } from '../../src/shared/profile-tables.ts';

const fixture = JSON.parse(fs.readFileSync(path.join(path.dirname(fileURLToPath(import.meta.url)), '..', 'fixtures', 'sdk-policy.json'), 'utf8'));

/** `{ "repeat": [s, n] }` stands for s repeated n times. */
const expand = (v: unknown): unknown => {
  if (Array.isArray(v)) return v.map(expand);
  if (v && typeof v === 'object') {
    const o = v as Record<string, unknown>;
    if (Object.keys(o).length === 1 && Array.isArray(o.repeat)) return String(o.repeat[0]).repeat(Number(o.repeat[1]));
    return Object.fromEntries(Object.entries(o).map(([k, x]) => [k, expand(x)]));
  }
  return v;
};

describe('sdk-policy fixture', () => {
  it('links', () => {
    for (const c of fixture.links) assert.equal(checkLink(c.method as LinkMethod, c.url), c.expect, `${c.method} ${String(c.url).slice(0, 80)}`);
  });
  it('haptics', () => {
    for (const c of fixture.haptics) assert.deepEqual(checkHaptic(c.args), c.expect, JSON.stringify(c.args));
  });
  it('bridge calls', () => {
    for (const c of fixture.bridge) assert.deepEqual(checkBridgeCall(c.method, c.args), c.expect, `${c.method} ${JSON.stringify(c.args).slice(0, 120)}`);
  });
  it('haptic gate', () => {
    for (const c of fixture.hapticGate) {
      const gate = new HapticGate();
      c.steps.forEach((s: { at: number; visible?: boolean; expect?: string; touch?: 'down' | 'up' }, i: number) => {
        if (s.touch === 'down') gate.press(s.at);
        else if (s.touch === 'up') gate.release(s.at);
        else assert.equal(gate.decide(s.at, s.visible!), s.expect, `${c.name}, step ${i}`);
      });
    }
  });
  it('theme params', () => {
    for (const c of fixture.themeParams) assert.deepEqual(checkThemeParams(c.input), c.expect, JSON.stringify(c.input));
  });
  it('locales', () => {
    for (const c of fixture.locales) assert.equal(checkLocale(c.input), c.expect, String(c.input));
  });
  it('storage', () => {
    for (const c of fixture.storage) assert.deepEqual(checkStorageCommit(expand(c.input)), expand(c.expect), c.note ?? JSON.stringify(c.input));
  });
  it('utf8', () => {
    for (const c of fixture.utf8) assert.equal(utf8Length(c.s), c.bytes, c.s);
  });
  it('widget data', () => {
    for (const c of fixture.widgetData) assert.deepEqual(checkWidgetData(expand(c.input), c.copy), expand(c.expect), c.note ?? JSON.stringify(c.input).slice(0, 80));
  });
  it('viewer', () => {
    for (const c of fixture.viewer) assert.deepEqual(checkViewer(expand(c.input)), expand(c.expect), c.note ?? JSON.stringify(c.input).slice(0, 80));
  });
  it('chat type', () => {
    for (const c of fixture.chatType) assert.equal(checkChatType(c.input), c.expect, String(c.input));
  });
  it('answers', () => {
    for (const c of fixture.answers) assert.deepEqual(checkRequestAnswer(expand(c.input)), expand(c.expect), c.note ?? JSON.stringify(c.input).slice(0, 80));
  });
  it('request gate', () => {
    for (const c of fixture.requestGate) {
      const gate = new RequestGate();
      type Step = { op: string; at: number; answeredAt?: number; gesture: boolean; visible: boolean; canQuery: boolean; backgroundQuery: boolean; withWallet?: boolean; wallet?: boolean; expect: string | boolean };
      c.steps.forEach((s: Step, i: number) => {
        if (s.op === 'finish') return gate.finish(s.answeredAt);
        if (s.op === 'show') return assert.equal(gate.show(s.at), s.expect, `${c.name}, step ${i}`);
        if (s.op === 'decideWallet') return assert.equal(gate.decideWallet(s.at, { ...s, wallet: s.wallet === true }), s.expect, `${c.name}, step ${i}`);
        assert.equal(gate.decide(s.at, s), s.expect, `${c.name}, step ${i}`);
      });
    }
  });
  it('preview methods', () => {
    for (const c of fixture.previewMethods) assert.equal(allowedInPreview(c.method), c.allowed, c.method);
  });
  it('request refusals', () => {
    for (const c of fixture.requestRefusals) assert.equal(requestRefusal(c.verdict as RequestVerdict), c.expect, c.verdict);
  });
  it('wallet transactions', () => {
    for (const c of fixture.walletTransactions) assert.deepEqual(checkWalletTransaction(expand(c.input)), expand(c.expect), c.note ?? JSON.stringify(c.input).slice(0, 120));
  });
  it('signData payloads', () => {
    for (const c of fixture.signDataPayloads) assert.deepEqual(checkSignDataPayload(expand(c.input)), expand(c.expect), c.note ?? JSON.stringify(c.input).slice(0, 120));
  });
});

describe('sdk-policy: the wallet (TS only)', () => {
  it('a client\'s failure of a wallet call becomes its code, anything else FAILED', () => {
    assert.equal(walletFailure('REJECTED'), 'REJECTED');
    assert.equal(walletFailure({ code: 'BAD_REQUEST' }), 'BAD_REQUEST');
    assert.equal(walletFailure('TIMEOUT'), 'TIMEOUT');
    assert.equal(walletFailure(new Error('x')), 'FAILED');
    assert.equal(requestFailure('REJECTED'), 'FAILED');
  });
  it('a lone surrogate in a signData text is refused', () => {
    assert.equal(checkSignDataPayload({ type: 'text', text: 'a\ud800' }), null);
    assert.equal(checkSignDataPayload({ type: 'cell', schema: '\udfff', cell: 'AA==' }), null);
  });
  it('the checked transaction is a fresh object: what the widget passed cannot change it later', () => {
    const tx = { messages: [{ address: 'EQ' + 'a'.repeat(46), amount: '1' }] };
    const out = checkWalletTransaction(tx)!;
    tx.messages[0].amount = '999';
    assert.equal(out.messages[0].amount, '1');
  });
});

describe('sdk-policy: bot data and requests (TS only)', () => {
  it('lone surrogates are refused everywhere a string leaves the widget or enters it', () => {
    assert.equal(checkRequestData('a\ud800'), null);
    assert.equal(checkRequestData('\udc00b'), null);
    assert.equal(checkCopyText('x\ud83d'), null);
    assert.deepEqual(checkWidgetData({ chatData: '\ud800', publicData: 'ok' }, false), { chatData: null, publicData: 'ok', rejected: ['chatData'] });
    assert.equal(checkRequestAnswer({ result: '\udfff' }), null);
    assert.equal(checkRequestData('\ud83d\ude00'), '\ud83d\ude00');
  });
  it('wrong JS types: no data, no viewer, no answer', () => {
    assert.equal(checkRequestData(42), null);
    assert.equal(checkCopyText({ toString: () => 'x' }), null);
    assert.deepEqual(checkWidgetData({ chatData: 5, publicData: ['x'] }, false), { chatData: null, publicData: null, rejected: ['chatData', 'publicData'] });
    assert.deepEqual(checkWidgetData('chat', false), { chatData: null, publicData: null, rejected: [] });
    assert.deepEqual(checkViewer({ id: '1' }), { viewer: null, rejected: [] });
    assert.deepEqual(checkViewer({ id: 1.5 }), { viewer: null, rejected: [] });
    assert.deepEqual(checkViewer({ id: 7, first_name: 5, last_name: ['L'], username: 12345, is_premium: 'yes' }),
      { viewer: { id: 7 }, rejected: ['first_name', 'last_name', 'username', 'is_premium'] });
    assert.deepEqual(checkViewer({ id: 7, first_name: 'A\ud800', last_name: '\udc00B' }), { viewer: { id: 7 }, rejected: ['first_name', 'last_name'] });
    assert.deepEqual(checkViewer({ id: 7, first_name: 'Ann', extra: 'x', is_bot: true }), { viewer: { id: 7, first_name: 'Ann' }, rejected: [] });
    assert.deepEqual(viewerUser({ id: 7, firstName: 'Ann', isPremium: false }), { id: 7, first_name: 'Ann', last_name: undefined, username: undefined, is_premium: false });
    assert.deepEqual(viewerFromList('42,Ann,,ann_lee,premium'), { id: 42, firstName: 'Ann', username: 'ann_lee', isPremium: true });
    assert.equal(viewerFromList('x,Ann'), null);
    assert.equal(checkRequestAnswer({ result: 5 }), null);
    assert.deepEqual(checkRequestAnswer(undefined), { result: null });
    assert.deepEqual(checkRequestAnswer({ result: 'r', chatData: 'c', data: { chatData: 'c' } }), { result: 'r' }); // an answer carries no data
  });
  it("a client's failure becomes TIMEOUT, RATE_LIMITED or FAILED", () => {
    assert.equal(requestFailure('TIMEOUT'), 'TIMEOUT');
    assert.equal(requestFailure({ code: 'RATE_LIMITED' }), 'RATE_LIMITED');
    assert.equal(requestFailure(new Error('network')), 'FAILED');
    assert.equal(requestFailure('NOT_ALLOWED'), 'FAILED');
  });
});

describe('sdk-policy (TS only)', () => {
  it('a lone surrogate is no URL and counts 3 bytes', () => {
    assert.equal(checkLink('openLink', 'https://example.com/\ud800'), null);
    assert.equal(utf8Length('\udc00'), 3);
  });
  it('fills the theme keys the client left out from the scheme defaults', () => {
    const p = completeThemeParams({ bg_color: '#ABCDEF', text_color: 'red' }, 'dark');
    assert.deepEqual(Object.keys(p).sort(), [...THEME_KEYS].sort());
    assert.equal(p.bg_color, '#abcdef');
    assert.equal(p.text_color, '#ffffff');
  });
  it('builds the renderer variables from the fixed list, dropping what breaks the grammar', () => {
    const vars = themeVariables({
      colorScheme: 'dark',
      params: { link_color: '#112233' },
      button: { bg: '#3390EC', text: 'white', radius: '10px', font: '600 15px/1.2 system-ui, sans-serif', padding: '8px 14px', height: '36px', pressedOpacity: 0.7, disabledOpacity: '2' },
    });
    assert.equal(vars['--tg-color-scheme'], 'dark');
    assert.equal(vars['--tg-theme-link-color'], '#112233');
    assert.equal(vars['--tg-theme-bg-color'], '#212121');
    assert.equal(vars['--tg-button-bg'], '#3390ec');
    assert.equal(vars['--tg-button-text'], undefined);
    assert.equal(vars['--tg-button-radius'], '10px');
    assert.equal(vars['--tg-button-font'], '600 15px/1.2 system-ui, sans-serif');
    assert.equal(vars['--tg-button-padding'], '8px 14px');
    assert.equal(vars['--tg-button-height'], '36px');
    assert.equal(vars['--tg-button-pressed-opacity'], '0.7');
    assert.equal(vars['--tg-button-disabled-opacity'], undefined);
    for (const name of Object.keys(vars)) assert.ok(THEME_VARIABLES.includes(name), name);
  });
  it('a button font must name generic families only', () => {
    assert.equal(themeVariables({ button: { font: '15px Helvetica' } })['--tg-button-font'], undefined);
    assert.equal(themeVariables({ button: { font: '15px system-ui; color: red' } })['--tg-button-font'], undefined);
    assert.equal(themeVariables({ button: { font: 'italic bold 15px ui-rounded' } })['--tg-button-font'], 'italic bold 15px ui-rounded');
  });
  it('a theme that is not an object is the light defaults', () => {
    assert.equal(themeVariables('dark')['--tg-color-scheme'], 'light');
    assert.equal(themeVariables(null)['--tg-theme-bg-color'], '#ffffff');
  });
  it('the text tokens are always set: the client\'s where they match the grammar, the reference set otherwise', () => {
    const ref = themeVariables({ colorScheme: 'light' });
    assert.equal(ref['--tg-text-font-size'], DEFAULT_TEXT_TOKENS.fontSize);
    assert.equal(ref['--tg-text-line-height'], DEFAULT_TEXT_TOKENS.lineHeight);
    assert.equal(ref['--tg-text-padding'], DEFAULT_TEXT_TOKENS.padding);
    assert.deepEqual(DEFAULT_TEXT_TOKENS, { fontSize: '15px', lineHeight: '1.35', padding: '8px 12px' });
    const t = (text: unknown) => themeVariables({ colorScheme: 'light', text });
    assert.equal(t({ fontSize: 17 })['--tg-text-font-size'], '17px');
    assert.equal(t({ fontSize: 17.5 })['--tg-text-font-size'], '17.5px');
    assert.equal(t({ fontSize: 15.0000001 })['--tg-text-font-size'], '15px');
    assert.equal(t({ fontSize: '16px' })['--tg-text-font-size'], '16px');
    for (const bad of [7, 65, '16', '1em', '16 px', 'calc(16px)', NaN, Infinity, -15, null, '9999px']) assert.equal(t({ fontSize: bad })['--tg-text-font-size'], '15px', String(bad));
    assert.equal(t({ lineHeight: 1.2 })['--tg-text-line-height'], '1.2');
    assert.equal(t({ lineHeight: '1.5' })['--tg-text-line-height'], '1.5');
    for (const bad of [0.9, 3.5, '20px', '1.2em', 'normal']) assert.equal(t({ lineHeight: bad })['--tg-text-line-height'], '1.35', String(bad));
    assert.equal(t({ padding: '10px 14px' })['--tg-text-padding'], '10px 14px');
    assert.equal(t({ padding: '0' })['--tg-text-padding'], '0');
    for (const bad of ['10px;color:red', '1px 2px 3px 4px 5px', 'auto', 12]) assert.equal(t({ padding: bad })['--tg-text-padding'], '8px 12px', String(bad));
    for (const name of ['--tg-text-font-size', '--tg-text-line-height', '--tg-text-padding']) assert.ok(THEME_VARIABLES.includes(name), name);
  });
  it('the UA layer is one @layer and nothing else, every selector in :where(), nothing !important, the text and root defaults on the tokens', () => {
    const ua = PROFILE_UA_STYLESHEET;
    assert.ok(ua.startsWith('@layer tw-ua{') && ua.endsWith('}}'));
    const layer = ua.slice('@layer tw-ua{'.length, -1);
    assert.equal((layer.match(/\{/g) ?? []).length, (layer.match(/\}/g) ?? []).length, 'one block');
    for (const m of layer.matchAll(/(?:^|})([^{}]+)\{/g)) assert.ok(m[1].startsWith(':where('), m[1]);
    // any author rule wins, user-select: text included (D18)
    assert.ok(!layer.includes('!important'));
    for (const v of ['--tg-theme-text-color', '--tg-theme-link-color', '--tg-text-font-size', '--tg-text-line-height', '--tg-text-padding']) assert.ok(layer.includes(`var(${v}`), v);
    // the bubble's fill is the canvas behind the renderer: no root background, so a body background reaches the canvas
    assert.ok(!/:where\(html\)\{[^}]*background/.test(layer));
    // The body pads for the client's time over a widget with no caption below it (docs/sdk-v1.md §2.17), 0 with a caption below.
    assert.match(layer, /:where\(body\)\{margin:var\(--tg-text-padding,8px 12px\);padding:0;padding-bottom:var\(--tg-content-safe-area-inset-bottom,0px\)\}/);
    // selection off on the root only: user-select is not inherited, so an element's `auto` follows its parent and
    // `user-select: text` on a container reaches its children
    assert.match(layer, /:where\(html\)\{[^}]*;user-select:none;/);
    assert.equal(layer.match(/user-select:none/g)?.length, 2, '-webkit- and unprefixed, both on the root');
    // rem would resolve against the chat page's root on the shadow-DOM renderer
    assert.ok(!/[0-9]rem\b/.test(layer));
  });
});

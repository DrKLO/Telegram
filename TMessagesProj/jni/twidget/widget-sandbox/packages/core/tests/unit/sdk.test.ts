// SDK v1 (docs/sdk-v1.md §2) end to end through the real sandbox bundle in
// QuickJS: the widget-facing API, and the glue's checks as the Node harness
// mirrors them from WidgetHost (activation, the haptic limit, storage commits).

import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { runWidget } from '../../scripts/sandbox-node.ts';
import { THEME_KEYS, WIDGET_MESSAGE_KINDS } from '../../src/shared/sdk-policy.ts';

const idOf = async (html: string, tag: string) => ((await runWidget(html)).renderOps.find((o: any) => o.op === 'el' && o.tag === tag) as any).id as number;
const diags = (r: { diags: { kind: string; detail: string }[] }, kind: string) => r.diags.filter((d) => d.kind === kind);

describe('SDK v1: TelegramWidget members', () => {
  it('has the v1 members and no sendMessage', async () => {
    const r = await runWidget('<p>x</p>', {
      probes: [
        'TelegramWidget.profile', 'TelegramWidget.isProfileAtLeast(1)', 'TelegramWidget.isProfileAtLeast(2)', 'TelegramWidget.isProfileAtLeast("1")',
        'TelegramWidget.colorScheme', 'TelegramWidget.theme', 'TelegramWidget.isActive', 'typeof TelegramWidget.sendMessage', 'typeof TelegramWidget.on',
        'Object.isFrozen(TelegramWidget.themeParams)', 'Object.keys(TelegramWidget.themeParams).sort().join()',
        'TelegramWidget.HapticFeedback.impactOccurred("light") === TelegramWidget.HapticFeedback',
        'TelegramWidget.HapticFeedback.notificationOccurred("success") === TelegramWidget.HapticFeedback',
        'TelegramWidget.HapticFeedback.selectionChanged() === TelegramWidget.HapticFeedback',
      ],
    });
    assert.deepEqual(r.probes, ['1', 'true', 'false', 'false', 'light', 'light', 'true', 'undefined', 'undefined', 'true', [...THEME_KEYS].sort().join(), 'true', 'true', 'true']);
  });

  it('themeParams come from the client, checked, the rest from the scheme defaults; a change fires themeChanged and onTheme once', async () => {
    const widget = `<script>
      var log = [], first = TelegramWidget.themeParams;
      TelegramWidget.onEvent('themeChanged', function () { log.push('changed:' + TelegramWidget.colorScheme + ':' + TelegramWidget.themeParams.bg_color + ':' + (this === TelegramWidget)); });
      TelegramWidget.onTheme(function (t) { log.push('onTheme:' + t); });
    </script>`;
    const r = await runWidget(widget, {
      themeParams: { bg_color: '#ABCDEF', text_color: 'red', made_up: '#123456' },
      script: [
        { probe: 'TelegramWidget.themeParams.bg_color + "," + TelegramWidget.themeParams.text_color + "," + ("made_up" in TelegramWidget.themeParams)' },
        { theme: 'light', themeParams: { bg_color: '#abcdef' } },
        { theme: 'dark', themeParams: { bg_color: '#101010' } },
        { probe: 'log.join("|")' },
        { probe: 'first === TelegramWidget.themeParams' },
        { probe: 'TelegramWidget.themeParams.text_color' },
      ],
    });
    assert.deepEqual(r.probes, ['#abcdef,#000000,false', 'onTheme:light|changed:dark:#101010:true|onTheme:dark', 'false', '#ffffff']);
  });

  it('onEvent: known types only, functions only, no duplicates, 16 per type; offEvent removes', async () => {
    const r = await runWidget(`<script>
      var n = 0, f = function () { n++; };
      TelegramWidget.onEvent('activated', f); TelegramWidget.onEvent('activated', f);
      for (var i = 0; i < 20; i++) TelegramWidget.onEvent('deactivated', function () { n += 100; });
      TelegramWidget.onEvent('linkOpened', f); TelegramWidget.onEvent('activated', 'f');
    </script>`, {
      visible: false,
      script: [{ visible: true }, { probe: 'n' }, { visible: false }, { probe: 'n' }, { probe: '(TelegramWidget.offEvent("activated", f), n)' }, { visible: true }, { probe: 'n + ":" + TelegramWidget.isActive' }],
    });
    assert.deepEqual(r.probes, ['1', '1601', '1601', '1601:true']);
  });
});

describe('SDK v1: links', () => {
  const widget = `<button>go</button><a id="ext" href="https://example.com/a">a</a><a id="tg" href="https://t.me/durov">t</a><a id="http" href="http://example.com/">h</a><a id="js" href="javascript:void 0">j</a><script>
    var errors = [];
    function tryOpen(m, u) { try { TelegramWidget[m](u); } catch (e) { errors.push(m + ':' + e.name); } }
    tryOpen('openLink', 'http://example.com/'); tryOpen('openLink', 'https://t.me/x'); tryOpen('openLink', 'https://example.com/' + 'a'.repeat(300));
    tryOpen('openTelegramLink', 'tg://resolve?domain=x'); tryOpen('openTelegramLink', 'https://example.com/'); tryOpen('openLink', 42);
    document.querySelector('button').addEventListener('click', function () { TelegramWidget.openLink('HTTPS://Example.com/поиск'); TelegramWidget.openLink('https://example.com/second'); });
  </script>`;

  it('invalid URLs throw TypeError in the widget and never reach the host', async () => {
    const r = await runWidget(widget, { probes: ['errors.join()'] });
    assert.equal(r.probes[0], 'openLink:TypeError,openLink:TypeError,openLink:TypeError,openTelegramLink:TypeError,openTelegramLink:TypeError,openLink:TypeError');
    assert.deepEqual(r.actions, []);
  });

  it('a tap opens one link, normalised; the second call of the same tap is blocked', async () => {
    const button = await idOf(widget, 'button');
    const r = await runWidget(widget, { events: [{ type: 'click', targetId: button, gesture: 1 }] });
    assert.deepEqual(r.actions, [{ kind: 'openLink', url: 'https://example.com/%D0%BF%D0%BE%D0%B8%D1%81%D0%BA' }]);
    assert.equal(diags(r, 'activation-blocked').length, 1);
  });

  it('<a href> taps go to openLink or openTelegramLink; http: and javascript: are ignored', async () => {
    const first = await runWidget(widget);
    const anchors = first.renderOps.filter((o: any) => o.op === 'el' && o.tag === 'a').map((o: any) => o.id);
    const r = await runWidget(widget, { events: anchors.map((id: number, i: number) => ({ type: 'click', targetId: id, gesture: i + 1 })) });
    assert.deepEqual(r.actions, [{ kind: 'openLink', url: 'https://example.com/a' }, { kind: 'openTelegramLink', url: 'https://t.me/durov' }]);
    assert.equal(diags(r, 'navigation-blocked').length, 2);
  });

  it('window.open takes the same path and returns null', async () => {
    const html = '<button>o</button><script>var w; document.querySelector("button").onclick = function () { w = window.open("https://telegram.me/x"); };</script>';
    const r = await runWidget(html, { events: [{ type: 'click', targetId: await idOf(html, 'button'), gesture: 1 }], probes: ['w'] });
    assert.deepEqual(r.actions, [{ kind: 'openTelegramLink', url: 'https://telegram.me/x' }]);
    assert.equal(r.probes[0], 'null');
  });
});

describe('SDK v1: haptics', () => {
  it('need no tap; a burst of 5, the rest dropped with one haptic-limit; unknown styles throw', async () => {
    let t = 0;
    const r = await runWidget(`<script>
      for (var i = 0; i < 7; i++) TelegramWidget.HapticFeedback.impactOccurred('medium');
      var bad = []; ['loud', undefined].forEach(function (s) { try { TelegramWidget.HapticFeedback.impactOccurred(s); } catch (e) { bad.push(e.name); } });
      try { TelegramWidget.HapticFeedback.notificationOccurred('light'); } catch (e) { bad.push(e.name); }
    </script>`, { now: () => t, probes: ['bad.join()'] });
    assert.equal(r.actions.length, 5);
    assert.deepEqual(r.actions[0], { kind: 'haptic', haptic: 'impact', value: 'medium' });
    assert.equal(diags(r, 'haptic-limit').length, 1);
    assert.equal(r.probes[0], 'TypeError,TypeError,TypeError');
  });

  it('a hidden widget plays nothing', async () => {
    const r = await runWidget('<script>TelegramWidget.HapticFeedback.selectionChanged();</script>', { visible: false });
    assert.deepEqual(r.actions, []);
    assert.equal(diags(r, 'haptic-limit').length, 1);
  });
});

describe('SDK v1: storage', () => {
  it('the preload is there from the first line; each task commits the areas it changed, in full', async () => {
    const html = `<button>b</button><script>
      var seen = localStorage.getItem('count') + '/' + sessionStorage.getItem('s') + '/' + localStorage.length;
      localStorage.setItem('count', String(Number(localStorage.getItem('count')) + 1));
      localStorage.extra = 7;
      document.querySelector('button').onclick = function () { sessionStorage.setItem('s', 'tapped'); localStorage.setItem('count', localStorage.count); };
    </script>`;
    const r = await runWidget(html, { storage: { local: { count: '41' }, session: { s: 'kept' } }, events: [{ type: 'click', targetId: await idOf(html, 'button') }], probes: ['seen'] });
    assert.equal(r.probes[0], '41/kept/1');
    assert.deepEqual(r.storageCommits, [{ local: { count: '42', extra: '7' } }, { session: { s: 'tapped' } }], 'one commit per task; an unchanged value is no change');
  });

  it('4 KB of UTF-8 per area: setItem past it throws QuotaExceededError and stores nothing', async () => {
    const r = await runWidget(`<script>
      var out = [];
      localStorage.setItem('k', 'x'.repeat(4095));
      try { localStorage.setItem('k2', 'y'); } catch (e) { out.push(e.name, e.code, e instanceof DOMException, localStorage.getItem('k2')); }
      try { localStorage.big = 'ж'.repeat(2300); } catch (e) { out.push(e.name); }
      sessionStorage.setItem('k', 'x'.repeat(4000));
      localStorage.removeItem('k'); localStorage.setItem('w', 'ж'.repeat(2047));
    </script>`, { probes: ['out.join()', 'localStorage.length', 'sessionStorage.getItem("k").length'] });
    assert.deepEqual(r.probes, ['QuotaExceededError,22,true,,QuotaExceededError', '1', '4000']);
    assert.equal(r.storageCommits.length, 1);
    assert.deepEqual(Object.keys(r.storageCommits[0].local!), ['w']);
    assert.equal(r.storageCommits[0].session!.k.length, 4000);
  });

  it('a preload over the quota is dropped whole: storage-rejected, the widget starts empty', async () => {
    const r = await runWidget('<p>x</p>', { storage: { local: { a: 'x'.repeat(20000) }, session: { b: '1' } }, probes: ['localStorage.length + sessionStorage.length'] });
    assert.equal(diags(r, 'storage-rejected').length, 1);
    assert.equal(r.probes[0], '0');
  });

  it('clear() on an empty area and reads commit nothing', async () => {
    const r = await runWidget('<script>localStorage.clear(); localStorage.getItem("a"); sessionStorage.removeItem("x");</script>');
    assert.deepEqual(r.storageCommits, []);
  });
});

describe('SDK v1: setResizeAnchor (v0.1.3)', () => {
  it('top or bottom; anything else throws TypeError and sends nothing', async () => {
    const r = await runWidget(`<script>
      var errs = [];
      [undefined, null, 'left', 'Bottom', ' top', '', 1, ['top'], { edge: 'top' }].forEach(function (v) {
        try { TelegramWidget.setResizeAnchor(v); errs.push('ok'); } catch (e) { errs.push(e instanceof TypeError ? 'TypeError' : String(e)); }
      });
    </script>`, { probes: ['typeof TelegramWidget.setResizeAnchor', 'errs.join()', 'String(TelegramWidget.setResizeAnchor("top"))'] });
    assert.deepEqual(r.probes, ['function', Array(9).fill('TypeError').join(), 'undefined']);
    assert.equal(r.resizeAnchor, 'top');
    assert.deepEqual(r.resizeAnchors, []);
    assert.deepEqual(diags(r, 'bridge-rejected'), []);
  });

  it('only a change goes to the host, which keeps the latest; no tap needed, never an action', async () => {
    const r = await runWidget(`<script>
      TelegramWidget.setResizeAnchor('top');
      TelegramWidget.setResizeAnchor('bottom');
      for (var i = 0; i < 1000; i++) TelegramWidget.setResizeAnchor('bottom');
      TelegramWidget.setResizeAnchor('top');
      TelegramWidget.setResizeAnchor('bottom');
    </script>`, { visible: false });
    assert.deepEqual(r.resizeAnchors, ['bottom', 'top', 'bottom']);
    assert.equal(r.resizeAnchor, 'bottom');
    assert.deepEqual(r.actions, []);
    assert.deepEqual(r.bridge, []);
    assert.deepEqual(diags(r, 'activation-blocked'), []);
  });
});

describe('SDK v1: locale and inert APIs', () => {
  it('navigator.language and the Intl default follow the client locale', async () => {
    const r = await runWidget('<p>x</p>', { locale: 'ru', probes: ['navigator.language', 'navigator.languages.join()', 'new Intl.NumberFormat().resolvedOptions().locale', '(1234.5).toLocaleString()', 'new Intl.NumberFormat("en-US").format(1234.5)'] });
    assert.deepEqual(r.probes, ['ru', 'ru', 'ru', '1 234,5', '1,234.5']);
  });

  it('a malformed locale is en-US', async () => {
    const r = await runWidget('<p>x</p>', { locale: 'ru_RU', probes: ['navigator.language'] });
    assert.equal(r.probes[0], 'en-US');
  });

  it('alert, confirm and prompt show nothing and send nothing; clipboard and share reject with NotAllowedError', async () => {
    const r = await runWidget(`<button>c</button><script>
      var out = [alert('a'), confirm('c'), prompt('p')];
      document.querySelector('button').onclick = function () {
        navigator.clipboard.writeText('x').catch(function (e) { out.push(e.name); });
        navigator.share({ text: 'x' }).catch(function (e) { out.push(e.name); });
      };
    </script>`, { events: [{ type: 'click', targetId: 5, gesture: 1 }], probes: ['JSON.stringify(out)'] });
    assert.deepEqual(r.bridge, []);
    assert.equal(r.probes[0], JSON.stringify([null, false, null, 'NotAllowedError', 'NotAllowedError']));
  });
});

describe("SDK v1: the bot's data, where the widget is, requests, copyText", () => {
  const show = 'JSON.stringify([TelegramWidget.chatData, TelegramWidget.publicData, TelegramWidget.isForward, TelegramWidget.chatType, TelegramWidget.viewer])';
  const K = WIDGET_MESSAGE_KINDS;

  it('watchPublicData / unwatchPublicData: no tap needed, only changes reach the host, which keeps the latest; a preview never watches', async () => {
    const r = await runWidget('<script>TelegramWidget.watchPublicData(); TelegramWidget.watchPublicData(); setTimeout(function () { TelegramWidget.unwatchPublicData(); TelegramWidget.watchPublicData(); }, 10);</script>',
      { context: K['live-copy'] });
    assert.deepEqual(r.publicDataWatches, [true, false, true]);
    assert.equal(r.watchesPublicData, true);
    assert.deepEqual(diags(r, 'bridge-rejected'), []);
    const none = await runWidget('<p>x</p>', { context: K.private });
    assert.equal(none.watchesPublicData, false);
    assert.deepEqual(none.publicDataWatches, []);
    const preview = await runWidget('<script>TelegramWidget.watchPublicData();</script>', { context: K.preview });
    assert.equal(preview.watchesPublicData, false);
    assert.equal(diags(preview, 'preview-blocked').length, 1);
  });

  it('the viewer is shaped like WebAppUser: its names, username and Premium when the client has them; a field that breaks its rule is left out and reported', async () => {
    const user = 'JSON.stringify(TelegramWidget.viewer)';
    const full = await runWidget('<p>x</p>', {
      context: { ...K.private, viewer: { id: 42, firstName: 'Ann', lastName: 'Lee', username: 'ann_lee', isPremium: true } },
      probes: [user, 'Object.isFrozen(TelegramWidget.viewer)'],
    });
    assert.deepEqual(full.probes, [JSON.stringify({ id: 42, first_name: 'Ann', last_name: 'Lee', username: 'ann_lee', is_premium: true }), 'true']);
    assert.deepEqual(diags(full, 'data-rejected'), []);
    const some = await runWidget('<p>x</p>', { context: { ...K.group, viewer: { id: 42, firstName: 'Ann', username: 'a-b', isPremium: false } }, probes: [user] });
    assert.deepEqual(some.probes, [JSON.stringify({ id: 42, first_name: 'Ann' })]);
    assert.deepEqual(diags(some, 'data-rejected').map((d) => d.detail), ['viewer.username: not 5–32 characters of A–Z, a–z, 0–9 and _; the widget does not see it']);
    // Only with canQuery, as before: the names never reach a widget that may not ask.
    const quiet = await runWidget('<p>x</p>', { context: { ...K['no-queries'], viewer: { id: 42, firstName: 'Ann' } }, probes: [user] });
    assert.deepEqual(quiet.probes, ['null']);
  });

  it('chatData, publicData, chatType and viewer come from the client, checked; a copy has no chatData or chat, and only a widget that may request knows its viewer', async () => {
    const original = await runWidget('<p>x</p>', {
      data: { chatData: '{"turn":1}', publicData: 'score 3:1' }, context: { ...K.private, viewer: { id: 42 } },
      probes: [show, 'Object.isFrozen(TelegramWidget.viewer)', 'TelegramWidget.viewer === TelegramWidget.viewer', 'typeof TelegramWidget.request', 'typeof TelegramWidget.copyText'],
    });
    assert.deepEqual(original.probes, [JSON.stringify(['{"turn":1}', 'score 3:1', false, 'private', { id: 42 }]), 'true', 'true', 'function', 'function']);
    const copy = await runWidget('<p>x</p>', { data: { chatData: 'secret', publicData: 'score 3:1' }, context: { ...K.copy, viewer: { id: 42 } }, probes: [show] });
    assert.deepEqual(copy.probes, [JSON.stringify([null, 'score 3:1', true, null, null])]);
    // A copy is told nothing about the chat it is in, even one that may ask: the bot must not learn where its widget went.
    const live = await runWidget('<p>x</p>', { data: { chatData: 'secret', publicData: 'score 3:1' }, context: { ...K['live-copy'], chatType: 'supergroup', viewer: { id: 42 } }, probes: [show] });
    assert.deepEqual(live.probes, [JSON.stringify([null, 'score 3:1', true, null, { id: 42 }])]);
    // An original the bot sent without allow_queries has its data and chat, but no viewer: it may not talk to the bot.
    const quiet = await runWidget('<p>x</p>', { data: { chatData: 'mine', publicData: 'p' }, context: { ...K['no-queries'], viewer: { id: 42 } }, probes: [show] });
    assert.deepEqual(quiet.probes, [JSON.stringify(['mine', 'p', false, 'private', null])]);
    const none = await runWidget('<p>x</p>', { data: { chatData: '', publicData: 'x'.repeat(1025) }, context: { chatType: 'sender' as any, viewer: { id: 0 } }, probes: [show] });
    assert.deepEqual(none.probes, [JSON.stringify([null, null, false, null, null])]);
    assert.equal(diags(none, 'data-rejected').length, 1);
    const nul = await runWidget('<p>x</p>', { data: { chatData: 'a\0b', publicData: 'p' }, probes: [show] });
    assert.deepEqual(nul.probes, [JSON.stringify([null, 'p', false, null, null])]);
    assert.equal(diags(nul, 'data-rejected').length, 1);
  });

  it('the members are read-only', async () => {
    const r = await runWidget('<script>"use strict"; var e = []; ["chatData", "viewer", "isForward"].forEach(function (k) { try { TelegramWidget[k] = "x"; } catch (x) { e.push(x.name); } });</script>', {
      data: { chatData: 'c' }, probes: ['e.join()', 'TelegramWidget.chatData'],
    });
    assert.deepEqual(r.probes, ['TypeError,TypeError,TypeError', 'c']);
  });

  const events = '<script>var log = []; ["chatDataChanged", "publicDataChanged", "dataChanged"].forEach(function (t) { TelegramWidget.onEvent(t, function () { log.push(t + ":" + TelegramWidget.chatData + "|" + TelegramWidget.publicData + ":" + (this === TelegramWidget)); }); });</script>';

  it("the client's setData: chatDataChanged, then publicDataChanged, each only when its value changed, in one task", async () => {
    const r = await runWidget(events, {
      data: { chatData: 'a', publicData: 'p' }, context: K.private,
      script: [
        { data: { chatData: 'a', publicData: 'p' } }, { probe: 'log.length' },
        { data: { chatData: 'b', publicData: 'p' } }, { probe: 'log.splice(0).join(" ")' },
        { data: { chatData: 'c', publicData: 'q' } }, { probe: 'log.splice(0).join(" ")' },
        { data: { publicData: 'q' } }, { probe: 'log.splice(0).join(" ")' },
        { data: { chatData: 'x'.repeat(1025), publicData: 'q' } }, { probe: 'log.splice(0).join(" ")' },
      ],
    });
    assert.deepEqual(r.probes, [
      '0',
      'chatDataChanged:b|p:true',
      'chatDataChanged:c|q:true publicDataChanged:c|q:true',
      'chatDataChanged:null|q:true',
      '',
    ]);
    assert.equal(diags(r, 'data-rejected').length, 1);
  });

  it('a copy: only publicData changes (a shared object polled by the client); chatDataChanged never fires', async () => {
    const r = await runWidget(events, {
      data: { publicData: 'p' }, context: K.copy, script: [{ data: { chatData: 'c', publicData: 'p' } }, { data: { chatData: 'c', publicData: 'p2' } }, { probe: 'log.join(" ")' }],
    });
    assert.deepEqual(r.probes, ['publicDataChanged:null|p2:true']);
  });

  const requester = `<button id="b">go</button><script>
    var log = [];
    function ask(d) { TelegramWidget.request(d).then(function (a) { log.push('ok:' + d + ':' + JSON.stringify(a) + ':' + Object.isFrozen(a)); }, function (e) { log.push('err:' + d + ':' + e.name + ':' + e.code); }); }
    document.getElementById('b').addEventListener('click', function () { ask('tap'); ask('tap2'); });
  </script>`;
  const tap = async (n = 1) => { const id = await idOf(requester, 'button'); return Array.from({ length: n }, (_, i) => ({ type: 'click', targetId: id, gesture: i + 1 })); };

  it('a tap sends one request with its data only; the answer is { result }; a second call is BUSY', async () => {
    const asked: unknown[] = [];
    const r = await runWidget(requester, {
      events: await tap(), context: K.group,
      onRequest: (q) => { asked.push(q); return { result: 'thanks', chatData: 'ignored' } as any; },
      probes: ['log.join(" ")'],
    });
    assert.deepEqual(asked, [{ data: 'tap' }]);
    // The shim forwards both; the glue's gate refuses the second (the harness settles in the order the calls came).
    assert.equal(r.probes[0], 'ok:tap:{"result":"thanks"}:true err:tap2:WidgetRequestError:BUSY');
    assert.deepEqual(r.requests.map((q) => [q.data, q.verdict, q.background]), [['tap', 'foreground', false], ['tap2', 'busy', false]]);
  });

  it("a bot's data edit reaches the widget through setData, not with the answer", async () => {
    const html = '<button>b</button><script>var log = []; TelegramWidget.onEvent("chatDataChanged", function () { log.push("changed:" + TelegramWidget.chatData); }); document.querySelector("button").onclick = function () { TelegramWidget.request("vote").then(function (a) { log.push("ok:" + a.result + ":" + TelegramWidget.chatData); }); };</script>';
    const r = await runWidget(html, {
      data: { chatData: 'v1' }, context: K.private, events: [{ type: 'click', targetId: await idOf(html, 'button'), gesture: 1 }],
      onRequest: () => ({ result: '{"votes":2}' }), script: [{ data: { chatData: 'v2' } }, { probe: 'log.join(" ")' }],
    });
    assert.equal(r.probes[0], 'ok:{"votes":2}:v1 changed:v2');
  });

  it('without canQuery nothing is asked, tap or not; without a tap, and without the client handling requests, NOT_ALLOWED', async () => {
    const timer = '<script>var log = []; setTimeout(function () { TelegramWidget.request("t").catch(function (e) { log.push(e.code + ":" + /tap/.test(e.message)); }); }, 0);</script>';
    const r = await runWidget(timer, { context: K.group, onRequest: () => ({ result: 'never' }), probes: ['log.join()'] });
    assert.equal(r.probes[0], 'NOT_ALLOWED:true');
    assert.deepEqual(r.requests.map((q) => q.verdict), ['no-activation']);
    // Refused at delivery, the first leaves nothing in flight: the second goes on as a background request, refused the same way.
    const unsupported = await runWidget(requester, { events: await tap(), context: K.private, probes: ['log.join(" ")'] });
    assert.equal(unsupported.probes[0], 'err:tap:WidgetRequestError:NOT_ALLOWED err:tap2:WidgetRequestError:NOT_ALLOWED');
    assert.deepEqual(unsupported.requests.map((q) => q.verdict), ['unsupported', 'unsupported']);
    for (const context of [K.copy, {}]) {
      const off = await runWidget(requester, { events: await tap(), context, onRequest: () => ({ result: 'x' }), probes: ['log.join(" ")'] });
      assert.equal(off.probes[0], 'err:tap:WidgetRequestError:NOT_ALLOWED err:tap2:WidgetRequestError:NOT_ALLOWED');
      assert.deepEqual(off.requests, []); // the shim refuses at once: canQuery is false
    }
  });

  it('a live copy (fwd_allow_queries) asks on a tap, never in the background', async () => {
    const asked: unknown[] = [];
    const r = await runWidget(requester + '<script>setTimeout(function () { TelegramWidget.request("bg").catch(function (e) { log.push("bg:" + e.code); }); }, 0);</script>', {
      events: await tap(), context: { ...K['live-copy'], backgroundQuery: false }, onRequest: (q) => { asked.push(q); return { result: 'ok' }; }, probes: ['log.join(" ")'],
    });
    assert.deepEqual(asked, [{ data: 'tap' }]);
    assert.match(r.probes[0], /ok:tap:\{"result":"ok"\}/);
    assert.match(r.probes[0], /bg:NOT_ALLOWED/);
  });

  it('a request takes the tap: an openLink or copyText after it in the same tap is blocked', async () => {
    const html = '<button>x</button><script>document.querySelector("button").onclick = function () { TelegramWidget.request("r"); TelegramWidget.copyText("c"); TelegramWidget.openLink("https://example.com/"); };</script>';
    const r = await runWidget(html, { events: [{ type: 'click', targetId: await idOf(html, 'button'), gesture: 1 }], context: K.private, onRequest: () => ({}) });
    assert.deepEqual(r.copiedTexts, []);
    assert.deepEqual(r.actions, []);
    assert.equal(diags(r, 'activation-blocked').length, 2);
  });

  it('backgroundQuery: while visible, one per 5 s', async () => {
    const html = '<script>var log = []; function ask() { TelegramWidget.request("bg").then(function (a) { log.push("ok"); }, function (e) { log.push(e.code); }); } ask(); setTimeout(ask, 10); setTimeout(ask, 20);</script>';
    const asked: unknown[] = [];
    const r = await runWidget(html, {
      context: K.private, now: () => 0, onRequest: (q) => { asked.push(q); return {}; },
      script: [{ visible: false }, { probe: '(ask(), log.join())' }, { visible: true }], timerRounds: 2, probes: ['log.join()'],
    });
    // The 10 and 20 ms calls are too soon; the one made off screen waits and goes out when the widget appears.
    assert.deepEqual(asked, [{ data: 'bg' }, { data: 'bg' }]);
    assert.deepEqual(r.requests.map((q) => q.background), [true, false, false, true]);
    assert.equal(r.probes[0], 'ok,RATE_LIMITED,RATE_LIMITED', 'nothing settles the held request while the widget is off screen');
    assert.equal(r.probes[1], 'ok,RATE_LIMITED,RATE_LIMITED,ok');
    assert.deepEqual(r.requests.map((q) => q.verdict), ['background', 'rate-limited', 'rate-limited', 'held']);
    let now = 0;
    const spaced = await runWidget('<script>var log = []; function ask() { TelegramWidget.request("a").then(function () { log.push("a"); if (log.length < 3) setTimeout(ask, 0); }); } setTimeout(ask, 0);</script>', {
      context: K.private, now: () => (now += 5000), onRequest: () => ({}), timerRounds: 3, probes: ['log.join()'],
    });
    assert.equal(spaced.probes[0], 'a,a,a');
    const tapOnly = await runWidget(html, { context: K['via-bot'], onRequest: () => ({}), probes: ['log.join()'] });
    // Each refusal comes back as a task of its own; the shim forwards the next call meanwhile and the gate refuses it too, never BUSY.
    assert.equal(tapOnly.probes[0], 'NOT_ALLOWED,NOT_ALLOWED,NOT_ALLOWED');
  });

  it('backgroundQuery: one at once when the widget appears on screen again, then one per 5 s', async () => {
    const html = '<script>var log = []; function ask(tag) { TelegramWidget.request("bg").then(function () { log.push(tag + ":ok"); }, function (e) { log.push(tag + ":" + e.code); }); } ask("boot");</script>';
    const r = await runWidget(html, {
      context: K.private, now: () => 1000, onRequest: () => ({}),
      script: [{ probe: '(ask("soon"), 1)' }, { visible: false }, { visible: true }, { probe: '(ask("shown"), 1)' }, { probe: '(ask("again"), 1)' }],
      probes: ['log.join()'],
    });
    assert.equal(r.probes.at(-1), 'boot:ok,soon:RATE_LIMITED,shown:ok,again:RATE_LIMITED');
  });

  it('backgroundQuery: a request at boot off screen waits for the widget to appear and goes out then', async () => {
    const html = '<script>var log = []; TelegramWidget.request("boot").then(function (a) { log.push("ok:" + a.result); }, function (e) { log.push(e.code); });</script>';
    const asked: unknown[] = [];
    const r = await runWidget(html, {
      context: K.private, visible: false, now: () => 1000, onRequest: (q) => { asked.push(q); return { result: 'r' }; },
      script: [{ probe: 'log.length + ":" + TelegramWidget.isActive' }, { visible: true }], probes: ['log.join()'],
    });
    assert.deepEqual(r.probes, ['0:false', 'ok:r']);
    assert.deepEqual(asked, [{ data: 'boot' }]);
    assert.deepEqual(r.requests.map((q) => [q.verdict, q.background]), [['held', true]]);
    assert.deepEqual(diags(r, 'request-blocked'), []);
    // The widget never appears: the request never reaches the client, and the widget is not told anything.
    const never = await runWidget(html, { context: K.private, visible: false, onRequest: (q) => { asked.push(q); return {}; }, probes: ['log.join()'] });
    assert.deepEqual(never.probes, ['']);
    assert.equal(asked.length, 1);
    assert.deepEqual(never.requests.map((q) => [q.verdict, q.background]), [['held', false]]);
    // Without backgroundQuery nothing waits: a boot request off screen is refused as before, and the appearance sends nothing.
    const group = await runWidget(html, { context: K.group, visible: false, onRequest: (q) => { asked.push(q); return {}; }, script: [{ visible: true }], probes: ['log.join()'] });
    assert.deepEqual(group.probes, ['NOT_ALLOWED']);
    assert.equal(asked.length, 1);
  });

  it('backgroundQuery: a request at boot off screen and one on activated: the first goes out when the widget appears, the second is BUSY', async () => {
    const html = '<script>var log = []; function ask(t) { TelegramWidget.request(t).then(function () { log.push(t + ":ok"); }, function (e) { log.push(t + ":" + e.code); }); } TelegramWidget.onEvent("activated", function () { ask("activated"); }); ask("boot");</script>';
    const asked: unknown[] = [];
    const r = await runWidget(html, { context: K.private, visible: false, now: () => 0, onRequest: (q) => { asked.push(q); return {}; }, script: [{ visible: true }], probes: ['log.sort().join()'] });
    assert.deepEqual(asked, [{ data: 'boot' }]);
    assert.equal(r.probes[0], 'activated:BUSY,boot:ok');
    assert.deepEqual(r.requests.map((q) => q.verdict), ['held', 'busy']);
  });

  it('a held request frees the slot with its answer: the next request after the appearance is decided on its own, not BUSY', async () => {
    const html = '<script>var log = []; function ask(t) { TelegramWidget.request(t).then(function () { log.push(t + ":ok"); }, function (e) { log.push(t + ":" + e.code); }); } ask("boot");</script>';
    let clock = 0;
    const asked: unknown[] = [];
    const r = await runWidget(html, {
      context: K.private, visible: false, now: () => clock, onRequest: (q) => { asked.push(q); return {}; },
      script: [{ visible: true }, { probe: '(ask("soon"), 1)' }], probes: ['log.join()'],
    });
    assert.equal(r.probes.at(-1), 'boot:ok,soon:RATE_LIMITED', 'the appearance sent the held one: the next is 5 s after it');
    clock = 0;
    const later = await runWidget(html, {
      context: K.private, visible: false, now: () => clock, onRequest: (q) => { asked.push(q); clock += 5000; return {}; },
      script: [{ visible: true }, { probe: '(ask("later"), 1)' }], probes: ['log.join()'],
    });
    assert.equal(later.probes.at(-1), 'boot:ok,later:ok');
    assert.deepEqual(asked, [{ data: 'boot' }, { data: 'boot' }, { data: 'later' }]);
  });

  it('data of 1–64 bytes, a string: anything else throws TypeError and sends nothing', async () => {
    const r = await runWidget('<script>var e = []; [undefined, "", "x".repeat(65), "ж".repeat(33), 5, {}].forEach(function (d) { try { TelegramWidget.request(d); } catch (x) { e.push(x.name); } }); try { TelegramWidget.copyText(""); } catch (x) { e.push("copy:" + x.name); } try { TelegramWidget.copyText("x".repeat(257)); } catch (x) { e.push("copy:" + x.name); }</script>', {
      context: K.private, onRequest: () => ({}), probes: ['e.join()'],
    });
    assert.equal(r.probes[0], 'TypeError,TypeError,TypeError,TypeError,TypeError,TypeError,copy:TypeError,copy:TypeError');
    assert.deepEqual(r.requests, []);
  });

  it('a result over 1024 bytes or with a NUL is INVALID_ANSWER; a failure becomes its code', async () => {
    for (const result of ['x'.repeat(1025), 'a\0b']) {
      const r = await runWidget(requester, { events: await tap(), context: K.private, onRequest: () => ({ result }), probes: ['log.join(" ")'] });
      assert.equal(r.probes[0], 'err:tap:WidgetRequestError:INVALID_ANSWER err:tap2:WidgetRequestError:BUSY');
      assert.equal(diags(r, 'answer-rejected').length, 1);
    }
    for (const [reason, code] of [['TIMEOUT', 'TIMEOUT'], [{ code: 'RATE_LIMITED' }, 'RATE_LIMITED'], [new Error('offline'), 'FAILED']] as const) {
      const f = await runWidget(requester, { events: await tap(), context: K.private, onRequest: () => Promise.reject(reason), probes: ['log[0]'] });
      assert.equal(f.probes[0], `err:tap:WidgetRequestError:${code}`);
    }
  });

  it('after an answer the next tap can request again', async () => {
    let n = 0;
    const r = await runWidget(requester, { events: await tap(2), context: K.group, onRequest: () => ({ result: String(++n) }), probes: ['log.filter(function (l) { return l.indexOf("ok:") === 0; }).length'] });
    assert.equal(r.probes[0], '2');
  });

  it('a preview: the data, no input, no requests or actions, nothing about the user, no storage commits', async () => {
    const html = `<button id="b">b</button><script>
      var log = [], taps = 0;
      document.getElementById('b').addEventListener('click', function () { taps++; });
      log.push(JSON.stringify([TelegramWidget.isPreview, TelegramWidget.chatData, TelegramWidget.publicData, TelegramWidget.chatType, TelegramWidget.viewer, localStorage.getItem('k')]));
      localStorage.setItem('k', 'v');
      TelegramWidget.request('x').catch(function (e) { log.push(e.code + ':' + /preview/.test(e.message)); });
      try { TelegramWidget.openLink('https://example.com/'); TelegramWidget.copyText('c'); TelegramWidget.HapticFeedback.impactOccurred('light'); } catch (e) { log.push(e.name); }
      TelegramWidget.ready(); TelegramWidget.setResizeAnchor('bottom');
      setTimeout(function () { log.push('timer'); }, 0);
    </script>`;
    const r = await runWidget(html, {
      data: { chatData: 'c', publicData: 'p' }, storage: { local: { k: 'stored' } },
      context: { ...K.private, viewer: { id: 42 }, preview: true },
      events: [{ type: 'click', targetId: await idOf(html, 'button'), gesture: 1 }], onRequest: () => ({ result: 'never' }),
      probes: ['log.join(" ")', 'taps', 'TelegramWidget.isPreview'],
    });
    assert.deepEqual(r.probes, [`${JSON.stringify([true, 'c', 'p', null, null, null])} NOT_ALLOWED:true timer`, '0', 'true']);
    assert.deepEqual(r.requests, []);
    assert.deepEqual(r.actions, [{ kind: 'ready' }]);
    assert.deepEqual(r.copiedTexts, []);
    assert.deepEqual(r.storageCommits, []);
    assert.equal(r.resizeAnchor, 'bottom');
    assert.equal(diags(r, 'preview-blocked').length, 3);
    const normal = await runWidget('<p>x</p>', { probes: ['TelegramWidget.isPreview'] });
    assert.deepEqual(normal.probes, ['false']);
  });

  it('copyText: one per tap, to the client as given; never without a tap', async () => {
    const html = '<button>c</button><script>setTimeout(function () { TelegramWidget.copyText("timer"); }, 0); document.querySelector("button").onclick = function () { TelegramWidget.copyText("PROMO-2026 ✓"); TelegramWidget.copyText("second"); };</script>';
    const r = await runWidget(html, { events: [{ type: 'click', targetId: await idOf(html, 'button'), gesture: 1 }] });
    assert.deepEqual(r.copiedTexts, ['PROMO-2026 ✓']);
    assert.equal(diags(r, 'activation-blocked').length, 2);
  });
});

describe('SDK v1: the wallet (docs/sdk-v1.md §2.18)', () => {
  const K = WIDGET_MESSAGE_KINDS;
  const TX = { valid_until: 1900000000, messages: [{ address: 'EQ' + 'ab'.repeat(23), amount: '1500000000', payload: 'te6cckEBAQEADgAAGAAAAABIZWxsbyEhIQ5sm/I=' }] };
  const payer = `<button id="b">pay</button><script>
    var log = [];
    document.getElementById('b').addEventListener('click', function () {
      TelegramWidget.request('pay:42', { wallet: true })
        .then(function (a) { log.push('tx'); return TelegramWidget.wallet.sendTransaction(JSON.parse(a.result), 'paid:42'); })
        .then(function (b) { log.push('ok:' + b.result + ':' + Object.isFrozen(b)); }, function (e) { log.push('err:' + e.code + ':' + e.message); });
    });
  </script>`;
  const press = async (html: string) => [{ type: 'click', targetId: await idOf(html, 'button'), gesture: 1 }];

  it('TelegramWidget.wallet: only where the client has the wallet and the widget may ask the bot; never in a preview', async () => {
    const probe = ['TelegramWidget.wallet === null ? "null" : [typeof TelegramWidget.wallet.sendTransaction, typeof TelegramWidget.wallet.signData, Object.isFrozen(TelegramWidget.wallet)].join()'];
    const cases: [object, string][] = [
      [{ ...K.private, wallet: true }, 'function,function,true'],
      [{ ...K['live-copy'], wallet: true }, 'function,function,true'],
      [{ ...K.group, wallet: true }, 'function,function,true'],
      [K.private, 'null'],
      [{ ...K['no-queries'], wallet: true }, 'null'],
      [{ ...K.copy, wallet: true }, 'null'],
      [{ ...K.preview, wallet: true }, 'null'],
    ];
    for (const [context, expect] of cases) assert.deepEqual((await runWidget('<p>x</p>', { context, probes: probe })).probes, [expect], JSON.stringify(context));
  });

  it('a tap pays: a request with the wallet, the bot answers with the transaction, the call on that answer goes out without a new tap, the bot answers the payment', async () => {
    const asked: unknown[] = [];
    const calls: unknown[] = [];
    const r = await runWidget(payer, {
      events: await press(payer), context: { ...K.private, wallet: true },
      onRequest: (q) => { asked.push(q); return { result: JSON.stringify(TX) }; },
      onWallet: (c) => { calls.push(c); return { result: 'thanks' }; },
      probes: ['log.join(" ")'],
    });
    assert.deepEqual(asked, [{ data: 'pay:42', wallet: true }]);
    assert.deepEqual(calls, [{ method: 'sendTransaction', transaction: TX, data: 'paid:42' }]);
    assert.deepEqual(r.requests.map((q) => [q.data, q.wallet, q.verdict]), [['pay:42', true, 'foreground']]);
    assert.deepEqual(r.walletCalls.map((w) => w.verdict), ['granted']);
    assert.equal(r.probes[0], 'tx ok:thanks:true');
  });

  it('the user declines: REJECTED, and a refusal brings no grant (the next call needs a tap); BAD_REQUEST from the wallet reaches the widget', async () => {
    const html = payer.replace("function (e) { log.push('err:' + e.code + ':' + e.message); }",
      "function (e) { log.push('err:' + e.code + ':' + e.message); return TelegramWidget.wallet.sendTransaction(" + JSON.stringify(TX) + ", 'again').catch(function (e2) { log.push('again:' + e2.code); }); }");
    const r = await runWidget(html, {
      events: await press(html), context: { ...K.group, wallet: true }, onRequest: () => ({ result: JSON.stringify(TX) }), onWallet: () => Promise.reject('REJECTED'),
      timerRounds: 3, probes: ['log.join(" ")'],
    });
    assert.equal(r.probes[0], 'tx err:REJECTED:wallet.sendTransaction: the user declined in the wallet window again:NOT_ALLOWED');
    assert.deepEqual(r.walletCalls.map((w) => w.verdict), ['granted', 'no-activation']);
    const bad = await runWidget(payer, { events: await press(payer), context: { ...K.group, wallet: true }, onRequest: () => ({ result: JSON.stringify(TX) }), onWallet: () => Promise.reject({ code: 'BAD_REQUEST' }), probes: ['log.join(" ")'] });
    assert.match(bad.probes[0], /^tx err:BAD_REQUEST:wallet\.sendTransaction: the wallet refused it/);
  });

  it('signData on a tap: the payload checked in the widget (a TypeError, nothing sent) and by the glue; the bot answers the signature', async () => {
    const html = `<button>sign</button><script>
      var log = [];
      document.querySelector('button').onclick = function () {
        try { TelegramWidget.wallet.signData({ type: 'text', text: 'x', domain: 'example.com' }, 'sign:1'); } catch (e) { log.push(e.name); }
        try { TelegramWidget.wallet.signData({ type: 'text', text: 'x' }, ''); } catch (e) { log.push(e.name); }
        TelegramWidget.wallet.signData({ type: 'text', text: 'I agree' }, 'sign:1').then(function (a) { log.push('ok:' + a.result); });
      };
    </script>`;
    const calls: unknown[] = [];
    const r = await runWidget(html, { events: await press(html), context: { ...K['live-copy'], wallet: true }, onWallet: (c) => { calls.push(c); return { result: 'signed' }; }, probes: ['log.join(" ")'] });
    assert.equal(r.probes[0], 'TypeError TypeError ok:signed');
    assert.deepEqual(calls, [{ method: 'signData', payload: { type: 'text', text: 'I agree' }, data: 'sign:1' }]);
    assert.deepEqual(r.walletCalls.map((w) => w.verdict), ['foreground']);
  });

  it('never in the background: a wallet call or a request for the wallet from a timer is refused, also in the private chat with the bot', async () => {
    const html = `<script>var log = [];
      setTimeout(function () {
        TelegramWidget.wallet.sendTransaction(${JSON.stringify(TX)}, 'x').catch(function (e) { log.push('tx:' + e.code + ':' + /tap/.test(e.message)); });
        TelegramWidget.request('w', { wallet: true }).catch(function (e) { log.push('rq:' + e.code); });
      }, 0);</script>`;
    const r = await runWidget(html, { context: { ...K.private, wallet: true }, onRequest: () => ({}), onWallet: () => ({ result: 'never' }), probes: ['log.join(" ")'] });
    assert.equal(r.probes[0], 'tx:NOT_ALLOWED:true rq:NOT_ALLOWED');
    assert.deepEqual(r.walletCalls.map((w) => w.verdict), ['no-activation']);
    assert.deepEqual(r.requests.map((q) => q.verdict), ['no-activation']);
  });

  it('without the wallet: a request for it is refused at once; malformed options throw; without onWallet the call is refused (unsupported)', async () => {
    const html = `<button>b</button><script>var log = [];
      document.querySelector('button').onclick = function () {
        try { TelegramWidget.request('x', { wallet: 1 }); } catch (e) { log.push(e.name); }
        TelegramWidget.request('x', { wallet: true }).catch(function (e) { log.push(e.code + ':' + /no wallet/.test(e.message)); });
      };</script>`;
    const r = await runWidget(html, { events: await press(html), context: K.private, onRequest: () => ({}), probes: ['log.join(" ")'] });
    assert.equal(r.probes[0], 'TypeError NOT_ALLOWED:true');
    assert.deepEqual(r.requests, []);
    const signer = '<button>b</button><script>var log = []; document.querySelector("button").onclick = function () { TelegramWidget.wallet.signData({ type: "text", text: "x" }, "s").catch(function (e) { log.push(e.code); }); };</script>';
    const none = await runWidget(signer, { events: await press(signer), context: { ...K.private, wallet: true }, probes: ['log.join(" ")'] });
    assert.equal(none.probes[0], 'NOT_ALLOWED');
    assert.deepEqual(none.walletCalls.map((w) => w.verdict), ['unsupported']);
  });
});

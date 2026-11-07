// The client's chrome over the widget (docs/sdk-v1.md §2.17): `env(safe-area-inset-*)` rewritten by the validator to
// the root variables the renderer sets, other `env()` names resolved to their fallback or dropped, CSS.supports and
// @supports, the variables and their checks, and TelegramWidget.contentSafeAreaInset with contentSafeAreaChanged
// through the real sandbox bundle; the last part lays widgets out in the renderer core (headless Chromium).

import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { runWidget } from '../../scripts/sandbox-node.ts';
import { sanitizeCssValue, sanitizeStyleAttribute, sanitizeStyleProperty, sanitizeStylesheet } from '../../src/host/css-sanitizer.ts';
import { cssSupports, supportsDeclaration } from '../../src/shared/css-supports.ts';
import { THEME_VARIABLES, checkContentSafeArea, contentSafeAreaVariables } from '../../src/shared/sdk-policy.ts';

const sheet = (css: string, scope: 'document' | 'shadow' = 'document') => {
  const diags: string[] = [];
  const out = sanitizeStylesheet(css, (k, d) => diags.push(`${k}: ${d}`), { colorScheme: 'light', strict: true, scope });
  return { out, diags };
};

describe('safe area: the validator rewrites env()', () => {
  it('a safe-area inset becomes its root variable, the fallback kept, silently', () => {
    for (const [css, want] of [
      ['a{padding-bottom:env(safe-area-inset-bottom)}', 'a{padding-bottom:var(--tg-content-safe-area-inset-bottom)}'],
      ['a{width:env(safe-area-inset-left,31px)}', 'a{width:var(--tg-content-safe-area-inset-left,31px)}'],
      ['a{padding:12px 12px calc(12px + env(safe-area-inset-bottom, 0px))}', 'a{padding:12px 12px calc(12px + var(--tg-content-safe-area-inset-bottom,0px))}'],
      ['a{margin:max(8px,env(safe-area-inset-top)) env(safe-area-inset-right)}', 'a{margin:max(8px,var(--tg-content-safe-area-inset-top)) var(--tg-content-safe-area-inset-right)}'],
      ['a{--pad:env(safe-area-inset-bottom, 4px)}', 'a{--pad:var(--tg-content-safe-area-inset-bottom,4px)}'],
      ['a{width:var(--w, env(safe-area-inset-right))}', 'a{width:var(--w,var(--tg-content-safe-area-inset-right))}'],
      ['a{bottom:light-dark(env(safe-area-inset-bottom), 0px)}', 'a{bottom:var(--tg-content-safe-area-inset-bottom)}'],
    ]) {
      const r = sheet(css);
      assert.equal(r.out, want, css);
      assert.deepEqual(r.diags, [], css);
    }
    // The shadow renderer's page has its own env(): the widget never reads it.
    assert.equal(sheet('a{bottom:env(safe-area-inset-bottom)}', 'shadow').out, 'a{bottom:var(--tg-content-safe-area-inset-bottom)}');
  });

  it('in a style attribute, a CSSOM write and an SVG presentation attribute too', () => {
    const diag = () => {};
    assert.equal(sanitizeStyleAttribute('padding-bottom: env(safe-area-inset-bottom, 8px)', diag), 'padding-bottom:var(--tg-content-safe-area-inset-bottom,8px)');
    assert.deepEqual(sanitizeStyleProperty('margin-bottom', 'env(safe-area-inset-bottom)', diag), { name: 'margin-bottom', value: 'var(--tg-content-safe-area-inset-bottom)' });
    assert.equal(sanitizeCssValue('env(safe-area-inset-bottom)', diag), 'var(--tg-content-safe-area-inset-bottom)');
  });

  it('another name resolves to its fallback (checked like any value) or drops the declaration', () => {
    let r = sheet('a{margin:max(8px, env(keyboard-inset-height, 4px))}');
    assert.equal(r.out, 'a{margin:max(8px,4px)}');
    assert.deepEqual(r.diags, ['profile: margin: env(keyboard-inset-height) → its fallback']);
    r = sheet('a{margin:env(keyboard-inset-height);color:red}');
    assert.equal(r.out, 'a{color:red}');
    assert.deepEqual(r.diags, ['profile: margin: env(keyboard-inset-height)']);
    // Names are exact, as in CSS.
    assert.equal(sheet('a{width:env(SAFE-AREA-INSET-TOP, 2px)}').out, 'a{width:2px}');
    // An empty fallback, indices and anything but env(<name>[, …]) drop it.
    for (const css of ['a{width:env(foo,)}', 'a{width:env(safe-area-inset-top 1, 2px)}', 'a{width:env(1px)}', 'a{width:env()}']) assert.equal(sheet(css).out, 'a{}', css);
    // The fallback goes through the URL policy.
    r = sheet('a{background:env(foo, url(https://example.com/x.png))}');
    assert.equal(r.out, 'a{}');
    assert.ok(r.diags.some((d) => d.startsWith('url-blocked')), r.diags.join());
  });

  it('never inside a URL-taking function', () => {
    for (const css of ['a{background:url(env(safe-area-inset-bottom))}', 'a{background-image:image-set(env(safe-area-inset-bottom) 1x)}', 'a{background-image:cross-fade(env(safe-area-inset-bottom), red)}']) {
      assert.equal(sheet(css).out, 'a{}', css);
    }
  });

  it('CSS.supports and @supports answer true for the safe-area insets only', () => {
    assert.equal(supportsDeclaration('padding-bottom', 'env(safe-area-inset-bottom)'), true);
    assert.equal(supportsDeclaration('padding', '0 0 max(12px, env(safe-area-inset-bottom, 4px))'), true);
    assert.equal(supportsDeclaration('padding', 'env(keyboard-inset-height, 4px)'), false);
    assert.equal(supportsDeclaration('padding', 'env(safe-area-inset-top 1)'), false);
    assert.equal(cssSupports('(padding-bottom: env(safe-area-inset-bottom))'), true);
    assert.equal(sheet('@supports (padding: env(safe-area-inset-bottom)){a{color:red}}').out, 'a{color:red}');
    assert.equal(sheet('@supports (padding: env(foo, 1px)){a{color:red}}').out, '');
  });
});

describe('safe area: the values', () => {
  it('insets 0–64, corners 0–200, two decimals; anything else is 0', () => {
    assert.deepEqual(checkContentSafeArea({ bottom: 22.555, cornerRight: 72, top: 65, left: -1, right: Number.NaN, cornerLeft: '3' }),
      { top: 0, right: 0, bottom: 22.56, left: 0, cornerLeft: 0, cornerRight: 72 });
    assert.deepEqual(checkContentSafeArea({ bottom: 64, cornerLeft: 200, cornerRight: 200.5 }), { top: 0, right: 0, bottom: 64, left: 0, cornerLeft: 200, cornerRight: 0 });
    assert.deepEqual(checkContentSafeArea(null), { top: 0, right: 0, bottom: 0, left: 0, cornerLeft: 0, cornerRight: 0 });
  });

  it('six root variables, always set, in px, and none of them the widget\'s to change', () => {
    const vars = contentSafeAreaVariables({ bottom: 24, cornerRight: 72.5 });
    assert.deepEqual(vars, {
      '--tg-content-safe-area-inset-top': '0px', '--tg-content-safe-area-inset-right': '0px', '--tg-content-safe-area-inset-bottom': '24px',
      '--tg-content-safe-area-inset-left': '0px', '--tg-content-safe-area-corner-left': '0px', '--tg-content-safe-area-corner-right': '72.5px',
    });
    for (const name of Object.keys(vars)) assert.ok(THEME_VARIABLES.includes(name), name);
  });
});

describe('safe area: TelegramWidget.contentSafeAreaInset', () => {
  const widget = `<script>
    var log = [], seen = [];
    TelegramWidget.onEvent('contentSafeAreaChanged', function () {
      var a = TelegramWidget.contentSafeAreaInset;
      seen.push(a);
      log.push([a.bottom, a.cornerLeft, a.cornerRight, Object.isFrozen(a), this === TelegramWidget].join(':'));
    });
  </script>`;

  it('all 0 and frozen without a client value', async () => {
    const r = await runWidget('<p>x</p>', { probes: ['JSON.stringify(TelegramWidget.contentSafeAreaInset)', 'Object.isFrozen(TelegramWidget.contentSafeAreaInset)'] });
    assert.deepEqual(r.probes, ['{"top":0,"right":0,"bottom":0,"left":0,"cornerLeft":0,"cornerRight":0}', 'true']);
    assert.deepEqual(r.contentSafeArea, { top: 0, right: 0, bottom: 0, left: 0, cornerLeft: 0, cornerRight: 0 });
  });

  it('the boot value from the first line, then one event and a fresh frozen object per change; the same value fires nothing', async () => {
    const r = await runWidget(`<script>var first = TelegramWidget.contentSafeAreaInset, atBoot = first.bottom + ':' + first.cornerRight;</script>${widget}`, {
      contentSafeArea: { bottom: 24, cornerRight: 72, top: 99 },
      script: [
        { safeArea: { bottom: 24, cornerRight: 72 } },
        { safeArea: { bottom: 0 } },
        { safeArea: { bottom: 26.5, cornerLeft: 80 } },
        { safeArea: { bottom: 26.5, cornerLeft: 80 } },
        { probe: 'atBoot + "|" + log.join("|") + "|" + (seen[0] !== seen[1]) + "|" + (first !== TelegramWidget.contentSafeAreaInset)' },
      ],
    });
    assert.deepEqual(r.probes, ['24:72|0:0:0:true:true|26.5:80:0:true:true|true|true']);
    assert.deepEqual(r.contentSafeArea, { top: 0, right: 0, bottom: 26.5, left: 0, cornerLeft: 80, cornerRight: 0 });
  });

  it('a change drops the cached layout answers: the variables moved boxes in the renderer', async () => {
    const r = await runWidget('<div id="d">x</div><script>var d = document.getElementById("d");</script>', {
      script: [{ probe: 'd.getBoundingClientRect().width' }, { probe: 'd.getBoundingClientRect().width' }, { safeArea: { bottom: 24 } }, { probe: 'd.getBoundingClientRect().width' }],
    });
    const rects = r.queries.filter((q) => JSON.stringify(q).includes('rect'));
    assert.equal(rects.length, 2, 'the second probe is a cache hit, the one after the change is not: ' + JSON.stringify(r.queries));
  });

  it('a CSSOM write of env() reaches the renderer as the variable, before the first render and after it', async () => {
    const r = await runWidget('<div id="d"></div><script>var d = document.getElementById("d"); d.style.paddingBottom = "env(safe-area-inset-bottom, 8px)";'
      + 'setTimeout(function () { d.style.marginTop = "env(safe-area-inset-top)"; d.style.marginBottom = "env(keyboard-inset-height)"; }, 0);</script>');
    const attr = r.renderOps.find((o: any) => o.op === 'attr' && o.name === 'style') as any;
    assert.equal(attr?.value, 'padding-bottom:var(--tg-content-safe-area-inset-bottom,8px)');
    const styles = r.renderOps.filter((o: any) => o.op === 'style').map((o: any) => `${o.name}:${o.value}`);
    // A dropped declaration reaches the renderer as a removal, as for any rejected CSSOM write.
    assert.deepEqual(styles, ['margin-top:var(--tg-content-safe-area-inset-top)', 'margin-bottom:']);
    assert.ok(r.diags.some((d) => d.detail === 'margin-bottom: env(keyboard-inset-height)'), JSON.stringify(r.diags));
  });
});

describe('safe area: the renderer core lays it out (headless Chromium)', () => {
  const height = async (html: string, contentSafeArea?: { bottom?: number; cornerRight?: number }) =>
    (await runWidget(html, { width: 320, layout: true, ...(contentSafeArea ? { contentSafeArea } : {}) })).height!;

  it('the UA layer adds the band to an unstyled body; env() and the variables resolve to the host\'s value, never to the fallback when it is 0', async () => {
    const plain = '<p>x</p>';
    assert.equal(Math.round(await height(plain, { bottom: 24, cornerRight: 72 }) - await height(plain)), 24);
    // body{margin:0} keeps the UA layer's bottom padding: a widget that pads for the band itself resets it.
    assert.equal(Math.round(await height('<style>body{margin:0}</style><div style="height:10px"></div>', { bottom: 24 })), 34);
    const own = '<style>body{margin:0;padding:0}</style><div style="height:10px;margin-bottom:env(safe-area-inset-bottom,99px)"></div>';
    assert.equal(Math.round(await height(own)), 10, 'the variable is 0px, not missing: the fallback is not used');
    assert.equal(Math.round(await height(own, { bottom: 24 })), 34);
    const corner = '<style>body{margin:0;padding:0}</style><div style="height:var(--tg-content-safe-area-corner-right)"></div>';
    assert.equal(Math.round(await height(corner, { bottom: 24, cornerRight: 72 })), 72);
  });

  it('the widget cannot replace the root variables', async () => {
    const html = '<style>:root{--tg-content-safe-area-inset-bottom:50px}body{margin:0;padding:0}</style><div style="height:env(safe-area-inset-bottom)"></div>'
      + '<script>document.documentElement.style.setProperty("--tg-content-safe-area-inset-bottom", "70px")</script>';
    assert.equal(Math.round(await height(html, { bottom: 20 })), 20);
  });
});

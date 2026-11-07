// tests/fixtures/intl-cases.json: `intl` requests as the shim sends them (explicit date fields,
// src/sandbox/globals.ts) with V8's answers (src/shared/intl.ts). The native copies check
// themselves against it: IntlBridgeTests.swift, IntlBridgeTest.kt (androidTest) and the desktop
// tests (`basic` marks the cases the locale-less C++ BasicIntl answers too).
//
//   corepack pnpm tsx packages/core/scripts/intl-cases.ts           # write the fixture
//   corepack pnpm tsx packages/core/scripts/intl-cases.ts --check   # exit 1 when stale (intl-cases.test.ts)
//
// Comparing: strings after mapping U+00A0, U+202F and U+2009 to a space; parts as [type, value]
// lists the same way; `numberOptions` / `dateOptions` on the keys the case lists.

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { formatIntl } from '../src/shared/intl.ts';

export interface IntlCase { request: Record<string, unknown>; expected: unknown; basic?: true }

const UTC = { timeZone: 'UTC' };
const YMD = { year: 'numeric', month: 'numeric', day: 'numeric' };
const HMS = { hour: 'numeric', minute: 'numeric', second: 'numeric' };
/** 1970-01-01 13:45:30.123 UTC */
const AFTERNOON = 49530123;

type Def = { request: Record<string, unknown>; basic?: boolean; keys?: string[] };
const defs: Def[] = [];
const add = (kind: string, locale: string, opts: Record<string, unknown>, extra: Record<string, unknown>, basic = false, keys?: string[]) =>
  defs.push({ request: { kind, locale, opts, ...extra }, basic, keys });
const num = (locale: string, value: number, opts: Record<string, unknown> = {}, basic = false) => add('number', locale, opts, { value }, basic);
const date = (locale: string, value: number, opts: Record<string, unknown>, basic = false) => add('date', locale, { ...UTC, ...opts }, { value }, basic);
const method = (m: string, locale: string, value: number, opts: Record<string, unknown>, basic = false) => add('dateMethod', locale, { ...UTC, ...opts }, { value, method: m }, basic);
const plural = (locale: string, values: number[], opts: Record<string, unknown> = {}) => { for (const value of values) add('plural', locale, opts, { value }, true); };
const relative = (locale: string, value: number, unit: string, opts: Record<string, unknown> = {}, basic = false) => add('relative', locale, opts, { value, unit }, basic);
const list = (locale: string, value: string[], opts: Record<string, unknown>, basic = false) => add('list', locale, opts, { value }, basic);
const compare = (locale: string, a: string, b: string, opts: Record<string, unknown> = {}, basic = true) => add('compare', locale, opts, { a, b }, basic);

// NumberFormat
num('en-US', 1234.5, {}, true);
num('de-DE', 1234.5, {}, true);
num('ru-RU', 1234.5, {}, true);
num('fr-FR', 1234.5, {}, true);
num('es-ES', 1234.5, {}, true);
num('es-ES', 12345.5, {}, true);
num('pl-PL', 12345.5, {}, true);
num('de-DE', 0.5, { style: 'percent' }, true);
num('tr-TR', 0.5, { style: 'percent' }, true);
num('en-US', 1234.5, { style: 'currency', currency: 'USD' }, true);
num('de-DE', 1234.5, { style: 'currency', currency: 'EUR' });
num('en-US', 1234.5, { style: 'currency', currency: 'USD', currencyDisplay: 'code' }, true);
num('en-US', 1234.5, { style: 'currency', currency: 'USD', currencyDisplay: 'name' }, true);
num('en-US', 0.256, { style: 'percent' }, true);
num('en-US', 7, { minimumIntegerDigits: 3 }, true);
num('en-US', 1, { minimumFractionDigits: 2 }, true);
num('en-US', 2.5, { maximumFractionDigits: 0 }, true);
num('en-US', 1.005, { maximumFractionDigits: 2 }, true);
num('en-US', 123456, { maximumSignificantDigits: 3 }, true);
num('en-US', 1, { minimumSignificantDigits: 3 }, true);
num('en-US', 1234567, { useGrouping: false }, true);
for (const [signDisplay, values] of [['always', [5, 0, -5]], ['exceptZero', [5, 0, -5, 0.0001]], ['negative', [5, -5, -0.0001]], ['never', [-5]]] as const) {
  for (const value of values) num('en-US', value, { signDisplay }, true);
}
num('en-US', 5, { style: 'currency', currency: 'USD', signDisplay: 'exceptZero' }, true);
num('de-DE', -5, { style: 'currency', currency: 'EUR', signDisplay: 'never' });
add('numberParts', 'en-US', {}, { value: -1234.5 }, true);
add('numberParts', 'de-DE', { style: 'currency', currency: 'EUR' }, { value: 1234.5 });
add('numberOptions', 'en-US', {}, {}, true, ['locale', 'style', 'minimumIntegerDigits', 'minimumFractionDigits', 'maximumFractionDigits']);
add('numberOptions', 'en-US', { style: 'currency', currency: 'usd' }, {}, true, ['style', 'currency', 'minimumFractionDigits', 'maximumFractionDigits']);
add('numberOptions', 'de-DE', { useGrouping: false, minimumIntegerDigits: 2 }, {}, true, ['locale', 'useGrouping', 'minimumIntegerDigits']);

// DateTimeFormat (the shim has already added the default fields)
date('en-US', 0, YMD, true);
date('en-GB', 0, YMD);
date('de-DE', 0, YMD);
date('ru-RU', 0, YMD);
date('ja-JP', 0, YMD);
date('en-US', 0, { month: 'long', day: 'numeric', year: 'numeric' }, true);
date('en-US', 0, { month: 'short', day: 'numeric' }, true);
date('en-US', 0, { weekday: 'long', month: 'long', day: 'numeric' }, true);
date('en-US', AFTERNOON, { hour: 'numeric', minute: '2-digit' }, true);
date('en-US', AFTERNOON, { hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }, true);
date('en-GB', AFTERNOON, { hour: 'numeric', minute: '2-digit', hour12: true });
date('en-US', 0, { era: 'short', ...YMD });
date('en-US', AFTERNOON, { hour: 'numeric', minute: '2-digit', timeZoneName: 'short' });
date('en-US', AFTERNOON, { minute: '2-digit', second: '2-digit', fractionalSecondDigits: 3 });
date('en-US', 0, { dateStyle: 'full' });
date('en-US', AFTERNOON, { dateStyle: 'medium', timeStyle: 'short' });
method('toLocaleString', 'en-US', AFTERNOON, { ...YMD, ...HMS }, true);
method('toLocaleString', 'ru-RU', AFTERNOON, { ...YMD, ...HMS });
method('toLocaleDateString', 'en-US', AFTERNOON, YMD, true);
method('toLocaleTimeString', 'en-US', AFTERNOON, HMS, true);
method('toLocaleTimeString', 'de-DE', AFTERNOON, HMS);
add('dateParts', 'en-US', { ...UTC, ...YMD }, { value: 0 }, true);
add('dateOptions', 'en-US', { timeZone: 'Asia/Tokyo', ...YMD }, {}, true, ['locale', 'timeZone', 'calendar', 'numberingSystem', 'year', 'month', 'day']);

// PluralRules
plural('en', [0, 1, 2, 1.5]);
plural('en', [1], { minimumFractionDigits: 1 });
plural('ru', [0, 1, 3, 5, 11, 21, 22, 25, 1.5]);
plural('ru', [1], { minimumFractionDigits: 1 });
plural('uk', [2, 11]);
plural('pl', [1, 2, 5, 22]);
plural('cs', [2, 5, 1.5]);
plural('ar', [0, 1, 2, 3, 11, 100]);
plural('he', [2]);
plural('fr', [0, 1.5, 2, 1000000]);
plural('pt', [0]);
plural('pt-PT', [0]);
plural('de', [1]);
plural('ja', [1]);
plural('lv', [0]);
plural('lt', [2]);
plural('sl', [3]);
plural('en', [1, 2, 3, 4, 11, 12, 13, 21, 22, 23, 101, 111], { type: 'ordinal' });
plural('ru', [1], { type: 'ordinal' });
plural('fr', [1, 2], { type: 'ordinal' });
plural('it', [8], { type: 'ordinal' });
plural('sv', [1], { type: 'ordinal' });

// RelativeTimeFormat
relative('en', -1, 'day', {}, true);
relative('en', 2, 'hours', {}, true);
relative('en', -3, 'day', { numeric: 'auto' }, true);
for (const [value, unit] of [[-1, 'day'], [0, 'day'], [1, 'day'], [-1, 'week'], [1, 'month'], [0, 'year'], [-1, 'quarter'], [0, 'second']] as const) relative('en', value, unit, { numeric: 'auto' }, true);
relative('en', -3, 'hour', { style: 'short' });
relative('de', -1, 'day', { numeric: 'auto' });
relative('fr', 1, 'day', { numeric: 'auto' });
relative('ru', -2, 'day', { numeric: 'auto' });
relative('ru', -5, 'day');

// ListFormat
list('en', ['a', 'b', 'c'], { type: 'conjunction' }, true);
list('en', ['a', 'b'], { type: 'conjunction' }, true);
list('en', ['a', 'b', 'c'], { type: 'disjunction' }, true);
list('en', ['a', 'b'], { type: 'disjunction' }, true);
list('en', ['a', 'b', 'c'], { type: 'unit' }, true);
list('de', ['a', 'b', 'c'], { type: 'conjunction' });
list('de', ['a', 'b', 'c'], { type: 'disjunction' });
list('ru', ['a', 'b', 'c'], { type: 'conjunction' });

// Collator
compare('en', 'a', 'B');
compare('en', 'B', 'a');
compare('en', 'a', 'A');
compare('en', 'a', 'á');
compare('en', 'a', 'á', { sensitivity: 'base' });
compare('en', 'a', 'A', { sensitivity: 'base' });
compare('en', 'a', 'á', { sensitivity: 'accent' });
compare('en', 'a', 'A', { sensitivity: 'accent' });
compare('en', 'a', 'A', { sensitivity: 'case' });
compare('en', 'a', 'á', { sensitivity: 'case' });
compare('en', '2', '10', { numeric: true });
compare('en', '2', '10');
compare('en', 'item 9', 'item 10', { numeric: true });
compare('en', '_', '1');
compare('de', 'ä', 'z');
compare('sv', 'ä', 'z', {}, false);

// Case mapping
add('upper', 'en', {}, { value: 'Hello' }, true);
add('lower', 'en', {}, { value: 'Hello' }, true);
add('upper', 'tr', {}, { value: 'i' });
add('lower', 'tr', {}, { value: 'I' });
add('upper', 'de', {}, { value: 'straße' });

const NUMBER_PARTS = new Set(['numberParts', 'dateParts']);
const OPTIONS = new Set(['numberOptions', 'dateOptions']);

export function buildCases(): IntlCase[] {
  return defs.map((d) => {
    const raw = formatIntl(structuredClone(d.request));
    let expected: unknown = raw;
    if (NUMBER_PARTS.has(d.request.kind as string)) expected = JSON.parse(raw);
    else if (OPTIONS.has(d.request.kind as string)) {
      const all = JSON.parse(raw);
      expected = Object.fromEntries(d.keys!.map((k) => [k, all[k]]));
    }
    return { request: d.request, expected, ...(d.basic ? { basic: true as const } : {}) };
  });
}

const here = path.dirname(fileURLToPath(import.meta.url));
export const FIXTURE = path.join(here, '..', 'tests', 'fixtures', 'intl-cases.json');

export function render(cases: IntlCase[]): string {
  const about = 'intl requests as the shim sends them, with V8\'s answers; scripts/intl-cases.ts writes it. Compare strings with U+00A0, U+202F and U+2009 as spaces, parts as [type, value] lists, options on the listed keys; `basic`: the C++ BasicIntl answers it too.';
  return JSON.stringify({ about, cases }, null, 1) + '\n';
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const text = render(buildCases());
  if (process.argv.includes('--check')) {
    const stale = !fs.existsSync(FIXTURE) || fs.readFileSync(FIXTURE, 'utf8') !== text;
    if (stale) { console.error(`${path.relative(process.cwd(), FIXTURE)} is stale: run scripts/intl-cases.ts`); process.exit(1); }
  } else {
    fs.writeFileSync(FIXTURE, text);
    console.log(`${path.relative(process.cwd(), FIXTURE)}: ${JSON.parse(text).cases.length} cases (ICU ${process.versions.icu})`);
  }
}

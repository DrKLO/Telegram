// The bot behind corpus/bot-demos: six widgets that use the widget's state and requests for real (Bot API 10.4).
// Long polling, no dependencies. The protocol of each widget is in its file's header and notes/bot-demos-spec.md;
// apps/web/src/mock-bot.ts plays the same protocol in the dev app.
//
//   commands (private chat or a group): /order, /poll [question | option | option …], /rsvp, /duel, /ticker, /reaction
//   inline mode (@bot in any chat): a duel, a poll, who's in, the ticker and the reaction test (the shared ones by id)
//   requests: order:poll|refresh|cancel, vote:N, rsvp:in|may|out, ttt:N|new, score:N (widget_query and, from copies
//   of shared widgets, shared_widget_query); the bot edits the state with editMessageWidgetData / editSharedWidgetData
//   and answers with answerWidgetQuery's data (JSON).
//   timers: an order moves one step every 30 s until delivered; a ticker changes every 10 s for a day.
//
// Config: ~/.config/widget-sandbox/widget-bot.env (or --config): TW_BOT_TOKEN, TW_BOT_API (the beta:
// https://api.telegram.org/beta), TW_BOT_TEST_ENV=1. State: bot-demos.state.json next to the config. Never inside a
// git working tree.
//
//   corepack pnpm tsx scripts/bot-demos.ts [--config <file>]

import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = path.join(path.dirname(fileURLToPath(import.meta.url)), '..');
const DEMOS = path.join(ROOT, 'corpus', 'bot-demos');
const args = process.argv.slice(2);
const opt = (name: string) => (args.includes(name) ? args[args.indexOf(name) + 1] : undefined);
const die = (m: string): never => { console.error(`bot-demos: ${m}`); process.exit(1); };

// ---- config and the Bot API

const CONFIG = opt('--config') ?? path.join(os.homedir(), '.config', 'widget-sandbox', 'widget-bot.env');
function loadConfig() {
  const vals: Record<string, string> = {};
  if (!fs.existsSync(CONFIG)) die(`no config ${CONFIG}`);
  for (let d = path.dirname(path.resolve(CONFIG)); path.dirname(d) !== d; d = path.dirname(d)) if (fs.existsSync(path.join(d, '.git'))) die(`the config ${CONFIG} is inside a git working tree`);
  for (const line of fs.readFileSync(CONFIG, 'utf8').split('\n')) {
    const m = /^\s*(?:export\s+)?([A-Z0-9_]+)\s*=\s*(.*?)\s*$/.exec(line);
    if (m && !line.trim().startsWith('#')) vals[m[1]] = m[2].replace(/^(['"])(.*)\1$/, '$2');
  }
  const get = (k: string) => process.env[k] || vals[k] || '';
  const token = get('TW_BOT_TOKEN') || die(`no TW_BOT_TOKEN in ${CONFIG}`);
  const base = (get('TW_BOT_API') || 'https://api.telegram.org').replace(/\/+$/, '');
  return { token, url: get('TW_BOT_TEST_ENV') === '1' ? `${base}/bot${token}/test` : `${base}/bot${token}` };
}
const cfg = loadConfig();

async function api(method: string, params: Record<string, unknown> = {}): Promise<any> {
  for (;;) {
    let body: any;
    try {
      const res = await fetch(`${cfg.url}/${method}`, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(params) });
      body = await res.json();
    } catch (e) { throw new Error(`${method}: ${String((e as Error).message).split(cfg.token).join('<token>')}`); }
    if (body.ok) return body.result;
    const wait = body.parameters?.retry_after;
    if (wait) { await new Promise((r) => setTimeout(r, wait * 1000 + 200)); continue; }
    throw new Error(`${method}: ${body.description}`);
  }
}
const html = (file: string) => fs.readFileSync(path.join(DEMOS, file), 'utf8');
const J = (s: string | undefined | null, d: any) => { try { return s ? JSON.parse(s) : d; } catch { return d; } };
const nameOf = (u: any) => String(u?.first_name ?? 'Someone').slice(0, 16);
const hhmm = (d: Date) => d.toTimeString().slice(0, 5);

// ---- state (persisted)

interface Order { st: number; eta: string; next: number }
/** `r`, a shared object's revision, goes out with every edit and answer: a client's poll may bring an older copy back. */
interface Poll { q: string; o: string[]; v: number[]; voters: Record<string, number>; r?: number }
interface Rsvp { c: { in: [number, string][]; may: [number, string][]; out: [number, string][] }; p: { t: string; w: string; p: string; max: number; n: number } }
interface Duel { b: string; x: [number, string] | null; o: [number, string] | null; t: 'x' | 'o'; w: string | null; l: number[] | null; s: [number, number] }
interface Ticker { s: string; n: string; c: string; p: number; o: number; h: number[]; until: number; r?: number }
interface State {
  orders: Record<string, Order & { pub: any }>;
  polls: Record<string, Poll>;
  rsvps: Record<string, Rsvp>;
  duels: Record<string, Duel>;
  tickers: Record<string, Ticker>;
  reaction: { id: string | null; top: [string, number][]; plays: number; bests: Record<string, [string, number]>; chats: Record<string, number>; r?: number };
}
const STATE_FILE = path.join(path.dirname(CONFIG), 'bot-demos.state.json');
const state: State = Object.assign(
  { orders: {}, polls: {}, rsvps: {}, duels: {}, tickers: {}, reaction: { id: null, top: [], plays: 0, bests: {}, chats: {} } },
  J(fs.existsSync(STATE_FILE) ? fs.readFileSync(STATE_FILE, 'utf8') : null, {}),
);
let saveTimer: NodeJS.Timeout | null = null;
const save = () => { saveTimer ??= setTimeout(() => { saveTimer = null; fs.writeFileSync(STATE_FILE, JSON.stringify(state)); }, 500); };

/** Where a widget_query's message is: chat:message or i:<inline id>. */
const keyOf = (q: any) => (q.inline_message_id ? `i:${q.inline_message_id}` : `${q.chat.id}:${q.message_id}`);
const where = (key: string) => (key.startsWith('i:') ? { inline_message_id: key.slice(2) } : { chat_id: Number(key.split(':')[0]), message_id: Number(key.split(':')[1]) });
/** An edit that changes nothing is not an error here ("message is not modified"). */
const sameIsFine = (e: unknown) => { if (!/not modified/i.test((e as Error).message)) throw e; };
const editMessage = (key: string, fields: { chat_data?: string; public_data?: string }) => api('editMessageWidgetData', { ...where(key), ...fields }).catch(sameIsFine);
const editShared = (widget_id: string, public_data: string) => api('editSharedWidgetData', { widget_id, public_data, cache_time: 5 }).catch(sameIsFine);

// ---- the six demos: their first state

let me: any;
const SHOPS = [['Pizzeria Uno', '🍕', [['Margherita', 2, 1250], ['Burrata', 1, 900], ['Lemonade', 2, 350]]], ['Sushi Kai', '🍣', [['Salmon nigiri', 4, 380], ['Dragon roll', 1, 1450], ['Miso soup', 2, 420]]], ['Burger Lab', '🍔', [['Smash burger', 2, 1190], ['Fries', 2, 450], ['Shake', 1, 590]]]] as const;
function newOrder() {
  const [shop, e, items] = SHOPS[Math.floor(Math.random() * SHOPS.length)];
  const pub = { shop, e, id: `A-${1000 + Math.floor(Math.random() * 9000)}`, st: 0, eta: hhmm(new Date(Date.now() + 25 * 60_000)), u: hhmm(new Date()), bot: me.username };
  const chat = { items, cur: '€', addr: '221B Baker St, flat 3', pay: '•• 4242' };
  return { pub, chat };
}
const DEFAULT_POLL = { q: 'Where do we go on Friday?', o: ['Pizza', 'Sushi', 'Tacos', 'Burgers'] };
const newRsvp = (): Rsvp => ({ c: { in: [], may: [], out: [] }, p: { t: 'Friday football', w: 'Fri · 19:00', p: 'Central Park, field 3', max: 10, n: 0 } });
const newDuel = (): Duel => ({ b: '---------', x: null, o: null, t: 'x', w: null, l: null, s: [0, 0] });
const duelPublic = (g: Duel) => JSON.stringify({ b: g.b, x: g.x?.[1] ?? null, o: g.o?.[1] ?? null, w: g.w, s: g.s, bot: me.username });
const tickerPublic = (t: Ticker) => JSON.stringify({ s: t.s, n: t.n, c: t.c, p: t.p, o: t.o, h: t.h, t: new Date().toTimeString().slice(0, 8), r: t.r ?? 0 });
/** The order's status for its message; `u` is when it last changed (a forward shows the status as of then). */
const orderPublic = (o: Order & { pub: any }) => JSON.stringify({ ...o.pub, st: o.st, eta: o.eta, u: hhmm(new Date()) });
const pollPublic = (p: Poll) => JSON.stringify({ q: p.q, o: p.o, v: p.v, r: p.r ?? 0 });
function newTicker(): Ticker {
  const p = 5.3 + Math.random() * 0.3, h = Array.from({ length: 24 }, (_, i) => Math.round((p + Math.sin(i / 3) * 0.08 + (Math.random() - 0.5) * 0.04) * 10000) / 10000);
  return { s: 'TON', n: 'Toncoin', c: '$', p: h[23], o: h[0], h, until: Date.now() + 24 * 3600_000 };
}
const reactionPublic = () => JSON.stringify({ top: state.reaction.top, plays: state.reaction.plays, r: state.reaction.r ?? 0 });

// ---- commands

async function onMessage(m: any) {
  const [cmd, ...rest] = String(m.text ?? '').split(' ');
  const c = cmd.split('@')[0];
  const chat_id = m.chat.id;
  const reply = (fields: Record<string, unknown>) => api('sendWidget', { chat_id, ...fields });
  if (c === '/start' || c === '/help') {
    await api('sendMessage', { chat_id, parse_mode: 'HTML', text: [
      '<b>Widget demos</b> — the widget\'s state and requests, for real.',
      '/order — an order tracker: personal details stay in this chat, a forward shows only the status',
      '/poll [question | option | option] — a shared poll: forwards vote too, everyone sees live counts',
      '/rsvp — who\'s in: a sign-up list for a group',
      '/duel — tic-tac-toe for two (best via inline: type @' + me.username + ' in a chat with a friend)',
      '/ticker — a live quote: one shared state, every copy follows',
      '/reaction — a reaction test with one leaderboard for everyone',
    ].join('\n') });
    return;
  }
  if (c === '/order') {
    const o = newOrder();
    const sent = await reply({ html: html('01-order.html'), allow_queries: true, public_data: JSON.stringify(o.pub), chat_data: JSON.stringify(o.chat) });
    state.orders[`${chat_id}:${sent.message_id}`] = { st: 0, eta: o.pub.eta, next: Date.now() + 30_000, pub: o.pub };
  } else if (c === '/poll') {
    const parts = rest.join(' ').split('|').map((s) => s.trim()).filter(Boolean);
    const q = parts.length >= 3 ? { q: parts[0].slice(0, 120), o: parts.slice(1, 7).map((s) => s.slice(0, 40)) } : DEFAULT_POLL;
    const sent = await reply({ html: html('02-poll.html'), allow_queries: true, is_shared: true, public_data: JSON.stringify({ ...q, v: q.o.map(() => 0) }) });
    state.polls[sent.widget.id] = { ...q, v: q.o.map(() => 0), voters: {} };
  } else if (c === '/rsvp') {
    const r = newRsvp();
    const sent = await reply({ html: html('03-rsvp.html'), allow_queries: true, public_data: JSON.stringify(r.p), chat_data: JSON.stringify(r.c) });
    state.rsvps[`${chat_id}:${sent.message_id}`] = r;
  } else if (c === '/duel') {
    const g = newDuel();
    const sent = await reply({ html: html('04-tic-tac-toe.html'), allow_queries: true, chat_data: JSON.stringify(g), public_data: duelPublic(g) });
    state.duels[`${chat_id}:${sent.message_id}`] = g;
  } else if (c === '/ticker') {
    const id = Object.keys(state.tickers).find((k) => state.tickers[k].until > Date.now());
    if (id) await api('sendSharedWidget', { chat_id, widget_id: id });
    else {
      const t = newTicker();
      const sent = await reply({ html: html('05-ticker.html'), is_shared: true, public_data: tickerPublic(t) });
      state.tickers[sent.widget.id] = t;
    }
  } else if (c === '/reaction') {
    const best = state.reaction.chats[String(chat_id)];
    const chat_data = best ? JSON.stringify({ best }) : undefined;
    if (state.reaction.id) await api('sendSharedWidget', { chat_id, widget_id: state.reaction.id, ...(chat_data ? { chat_data } : {}) });
    else {
      const sent = await reply({ html: html('06-reaction.html'), allow_queries: true, is_shared: true, public_data: reactionPublic(), ...(chat_data ? { chat_data } : {}) });
      state.reaction.id = sent.widget.id;
    }
  } else return;
  console.log(`${c} in ${m.chat.type} ${chat_id}`);
  save();
}

// ---- inline mode

async function onInline(q: any) {
  const article = (id: string, title: string, description: string, content: Record<string, unknown>) => ({ type: 'article', id, title, description, input_message_content: content });
  const g = newDuel(), r = newRsvp();
  const results = [
    article('duel', 'Tic-tac-toe duel', 'Challenge someone in this chat', { html: html('04-tic-tac-toe.html'), allow_queries: true, chat_data: JSON.stringify(g), public_data: duelPublic(g) }),
    article('poll', DEFAULT_POLL.q, 'A shared poll: forwards vote too', { html: html('02-poll.html'), allow_queries: true, is_shared: true, public_data: JSON.stringify({ ...DEFAULT_POLL, v: DEFAULT_POLL.o.map(() => 0) }) }),
    article('rsvp', 'Who\'s in?', 'A sign-up list for this chat', { html: html('03-rsvp.html'), allow_queries: true, public_data: JSON.stringify(r.p), chat_data: JSON.stringify(r.c) }),
    ...(state.reaction.id ? [article('reaction', 'Reaction test', `One leaderboard for everyone · ${state.reaction.plays} plays`, { widget_id: state.reaction.id })] : []),
    ...Object.keys(state.tickers).filter((k) => state.tickers[k].until > Date.now()).slice(0, 1).map((id) => article('ticker', 'TON live', 'A live quote, every copy follows', { widget_id: id })),
  ];
  await api('answerInlineQuery', { inline_query_id: q.id, results, cache_time: 0, is_personal: true });
}

/** A message sent via the bot first shows up as a query: give it its state then (the bot did not know its id). */
function stateFor<T>(map: Record<string, T>, key: string, make: () => T): T {
  return (map[key] ??= make());
}

// ---- requests

async function onQuery(q: any, fromCopy: boolean): Promise<unknown> {
  const data: string = q.data;
  const uid = q.from.id, who = nameOf(q.from);
  const [kind, arg] = data.split(':');

  if (kind === 'vote') {
    const id = q.widget_id;
    if (!id) return { err: 'Not a shared poll' };
    const p = stateFor<Poll>(state.polls, id, () => ({ ...DEFAULT_POLL, v: DEFAULT_POLL.o.map(() => 0), voters: {} }));
    const n = Number(arg);
    if (!(n >= 0 && n < p.o.length)) return { err: 'Unknown option' };
    const before = p.voters[uid];
    if (before !== undefined) p.v[before]--;
    if (before === n) delete p.voters[uid]; else { p.voters[uid] = n; p.v[n]++; }
    p.r = (p.r ?? 0) + 1;
    await editShared(id, pollPublic(p));
    return { my: p.voters[uid] ?? -1, v: p.v, r: p.r };
  }

  if (kind === 'score') {
    const ms = Number(arg);
    if (!(Number.isInteger(ms) && ms >= 100 && ms <= 2000)) return { err: 'Not a human time' };
    const R = state.reaction;
    if (q.widget_id) R.id ??= q.widget_id;
    R.plays++;
    const prev = R.bests[uid];
    R.bests[uid] = [who, Math.min(prev?.[1] ?? ms, ms)];
    R.top = Object.values(R.bests).sort((a, b) => a[1] - b[1]).slice(0, 8);
    R.r = (R.r ?? 0) + 1;
    if (R.id) await editShared(R.id, reactionPublic());
    const k = q.chat && String(q.chat.id);
    if (!fromCopy && k && !(R.chats[k] <= ms)) {
      R.chats[k] = ms;
      await editMessage(keyOf(q), { chat_data: JSON.stringify({ best: ms }) });
    }
    // The board comes with the answer: a copy may not see the shared edit for a while.
    return { rank: R.top.findIndex((r) => r === R.bests[uid]) + 1, best: R.bests[uid][1], top: R.top, plays: R.plays, r: R.r };
  }

  if (fromCopy) return { err: 'Not available in a copy' };
  const key = keyOf(q);

  if (kind === 'order') {
    const o = state.orders[key];
    if (!o) return { err: 'Order not found' };
    if (arg === 'cancel') {
      if (o.st < 0) return { err: 'Already cancelled' };
      if (o.st > 1) return { err: 'Too late to cancel' };
      await editMessage(key, { public_data: orderPublic({ ...o, st: -1 }) });
      o.st = -1;
    }
    return { st: o.st, eta: o.eta };
  }

  if (kind === 'rsvp') {
    const r: Rsvp = structuredClone(stateFor(state.rsvps, key, newRsvp));
    if (!['in', 'may', 'out'].includes(arg)) return { err: 'Unknown answer' };
    const lists = r.c as Record<string, [number, string][]>;
    const was = Object.keys(lists).find((k) => lists[k].some((e) => e[0] === uid));
    const meNow = was === arg ? null : arg;
    if (meNow === 'in' && was !== 'in' && r.c.in.length >= r.p.max) return { err: 'Full', me: was ?? null };
    for (const k of Object.keys(lists)) lists[k] = lists[k].filter((e) => e[0] !== uid);
    if (meNow) lists[meNow].push([uid, who]);
    r.p.n = r.c.in.length;
    await editMessage(key, { chat_data: JSON.stringify(r.c), public_data: JSON.stringify(r.p) });
    state.rsvps[key] = r;
    return { me: meNow };
  }

  if (kind === 'ttt') {
    // A move goes into the state only once the message shows it: a failed edit must not take a turn away.
    const g: Duel = structuredClone(stateFor(state.duels, key, newDuel));
    if (arg === 'new') {
      if (!g.w) return { err: 'Game on' };
      Object.assign(g, { b: '---------', t: g.w === 'x' ? 'o' : 'x', w: null, l: null });
    } else {
      const i = Number(arg);
      if (!(i >= 0 && i <= 8)) return { err: 'Unknown cell' };
      if (g.w) return { err: 'Game over' };
      if (!g.x) g.x = [uid, who];
      const mark = g.x[0] === uid ? 'x' : g.o?.[0] === uid ? 'o' : g.o ? null : 'o';
      if (!mark) return { err: 'Full' };
      if (g.t !== mark) return { err: 'Not your turn' };
      if (mark === 'o' && !g.o) g.o = [uid, who];
      if (g.b[i] !== '-') return { err: 'Taken' };
      g.b = g.b.slice(0, i) + mark + g.b.slice(i + 1);
      const LINES = [[0, 1, 2], [3, 4, 5], [6, 7, 8], [0, 3, 6], [1, 4, 7], [2, 5, 8], [0, 4, 8], [2, 4, 6]];
      const line = LINES.find(([a, b, c]) => g.b[a] !== '-' && g.b[a] === g.b[b] && g.b[a] === g.b[c]);
      if (line) { g.w = mark; g.l = line; g.s[mark === 'x' ? 0 : 1]++; } else if (!g.b.includes('-')) g.w = 'd';
      g.t = mark === 'x' ? 'o' : 'x';
    }
    await editMessage(key, { chat_data: JSON.stringify(g), public_data: duelPublic(g) });
    state.duels[key] = g;
    return { ok: 1 };
  }

  return { err: 'Unknown request' };
}

// ---- timers

async function tick() {
  const now = Date.now();
  for (const [key, o] of Object.entries(state.orders)) {
    if (o.st < 0 || o.st >= 3 || o.next > now) continue;
    o.st++;
    o.next = now + 30_000;
    if (o.st === 3) o.eta = hhmm(new Date());
    try { await editMessage(key, { public_data: orderPublic(o) }); console.log(`order ${key} → ${o.st}`); }
    catch (e) { console.error((e as Error).message); delete state.orders[key]; }
    save();
  }
  for (const [id, t] of Object.entries(state.tickers)) {
    if (t.until < now) continue;
    t.p = Math.max(0.5, Math.round((t.p * (1 + (Math.random() - 0.48) * 0.006)) * 10000) / 10000);
    t.h = [...t.h.slice(1), t.p];
    t.r = (t.r ?? 0) + 1;
    try { await editShared(id, tickerPublic(t)); }
    catch (e) { console.error((e as Error).message); t.until = 0; }
    save();
  }
}

// ---- the loop

async function main() {
  me = await api('getMe');
  console.log(`bot-demos: @${me.username}, demos from ${path.relative(ROOT, DEMOS)}, state in ${STATE_FILE}; Ctrl-C to stop`);
  setInterval(() => { tick().catch((e) => console.error(e.message)); }, 10_000);
  let offset = 0;
  for (;;) {
    let updates: any[] = [];
    try { updates = await api('getUpdates', { offset, timeout: 30, allowed_updates: ['message', 'inline_query', 'widget_query', 'shared_widget_query'] }); }
    catch (e) { console.error((e as Error).message); await new Promise((r) => setTimeout(r, 3000)); continue; }
    for (const u of updates) {
      offset = u.update_id + 1;
      try {
        if (u.message) await onMessage(u.message);
        else if (u.inline_query) await onInline(u.inline_query);
        else if (u.widget_query || u.shared_widget_query) {
          const q = u.widget_query ?? u.shared_widget_query;
          const at = q.inline_message_id ? 'inline' : q.chat ? `${q.chat.id}:${q.message_id}` : `widget ${q.widget_id}`;
          let answer: unknown;
          // Every query gets an answer, also when an edit fails; otherwise the widget waits and says the bot didn't answer.
          try { answer = await onQuery(q, !u.widget_query); }
          catch (e) { console.error(`${q.data} at ${at}: ${(e as Error).message}`); answer = { err: 'The bot failed. Try again' }; }
          save();
          console.log(`${u.widget_query ? 'widget_query' : 'shared_widget_query'} ${q.data} from ${nameOf(q.from)} at ${at} → ${JSON.stringify(answer).slice(0, 160)}`);
          await api('answerWidgetQuery', { widget_query_id: q.id, data: JSON.stringify(answer) });
        }
      } catch (e) { console.error((e as Error).message); }
    }
  }
}

main().catch((e) => die((e as Error).message));

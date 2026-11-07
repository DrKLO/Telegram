// The dev app's mock bot (docs/sdk-v1.md §2.13–§2.15): what a client and the server do around a widget's data and
// requests, in this trusted page. It keeps the message's `chatData` / `publicData` per file (as the server keeps
// them per message), answers the widget's requests with a built-in bot (the widget names it with
// `<meta name="tw-bot" content="poll">`, the panel can switch it), applies the bot's edits as the server's update
// (`setData` to every live host, before the answer), and sets the context a client passes: the server's flags for a
// kind of message (`WIDGET_MESSAGE_KINDS`), the chat type, the viewer. Dev tooling, not a product path.
// The wallet (docs/sdk-v1.md §2.18): the client has a mock Gram Wallet (the panel's **wallet**, `?wallet=0` off); a
// request with `{ wallet: true }` gives the bot the viewer's mock wallet card; a wallet call asks the page's mock wallet
// window (the `confirmWallet` hook) and, confirmed, gives the bot the outcome (a made-up `msg_hash` or signature).

import type { ChatType, WalletCall, WidgetContext, WidgetData, WidgetRequest, WidgetRequestAnswer } from '@telegram/widget-sandbox/host/widget-host';
import { CHAT_TYPES, MAX_WIDGET_DATA_BYTES, WIDGET_MESSAGE_KINDS, utf8Length, viewerFromList, type WidgetMessageKind } from '@telegram/widget-sandbox/shared/sdk-policy';

type Data = { chatData: string | null; publicData: string | null };
/** A bot's work on a request: `result` for the widget; a data field that is present is its edit (`editMessageWidgetData`, null clears it). */
interface Reply { result?: string | null; chatData?: string | null; publicData?: string | null }
/** The viewer's wallet card as the server attaches it to a request (`wallet.card`: address and balance in nanotons). */
interface WalletCard { address: string; balance: number }
interface BotContext {
  viewer: number;
  name: string;
  data: Data;
  /** The viewer's wallet card: a request with `{ wallet: true }`, and every wallet call's outcome. */
  wallet?: WalletCard;
}
interface Bot {
  title: string;
  /** The data the bot sent the widget with. */
  initial: Data;
  /** `name`: the viewer's first name, as the bot gets it in `from`. */
  answer(data: string, ctx: BotContext): Reply;
  /** The outcome of a wallet call the user confirmed: the transaction's `msgHash`, or the signature (`signed`). Without it the bot answers `{ result: null }`. */
  wallet?(call: WalletCall, ctx: BotContext & { msgHash?: string; signed?: { signature: string; domain: string; timestamp: number } }): Reply;
}

/** The dev app's viewer besides the id (1001): first name, last name, username, premium (`viewerFromList`). */
export const DEFAULT_PROFILE = 'Ann,Lee,ann_lee';
const json = (s: string | null, fallback: any) => { try { return s === null ? fallback : JSON.parse(s); } catch { return fallback; } };

const LINES = [[0, 1, 2], [3, 4, 5], [6, 7, 8], [0, 3, 6], [1, 4, 7], [2, 5, 8], [0, 4, 8], [2, 4, 6]];
const winner = (b: string) => LINES.find(([a, c, d]) => b[a] !== '-' && b[a] === b[c] && b[a] === b[d])?.map((i) => b[i])[0] ?? (b.includes('-') ? null : 'draw');
/** The bot's move: win, block, centre, a corner, anything. */
function botMove(b: string): number {
  for (const mark of ['o', 'x']) {
    for (const line of LINES) {
      const marks = line.map((i) => b[i]);
      if (marks.filter((m) => m === mark).length === 2 && marks.includes('-')) return line[marks.indexOf('-')];
    }
  }
  return [4, 0, 2, 6, 8, 1, 3, 5, 7].find((i) => b[i] === '-') ?? -1;
}

/** The viewer's mock Gram Wallet: what the server would attach (`wallet.card`); 12.5 TON. */
export const MOCK_WALLET: WalletCard = { address: '0:' + 'f00d'.repeat(16), balance: 12_500_000_000 };
/** MOCK_WALLET's address in the user-friendly form (non-bounceable, mainnet): what a transaction's message takes. */
const MOCK_WALLET_FRIENDLY = 'UQDwDfAN8A3wDfAN8A3wDfAN8A3wDfAN8A3wDfAN8A3wDZTR';
/** The mock shop's address: where its transactions pay. */
const SHOP_ADDRESS = 'EQ' + 'shop'.repeat(11) + 'Sh';
const SHOP_ITEMS = [['Coffee', '1500000000'], ['Croissant', '2000000000'], ['Day pass', '5000000000']];

export const BOTS: Record<string, Bot> = {
  echo: {
    title: 'echo: result = the request data',
    initial: { chatData: null, publicData: null },
    answer: (data) => ({ result: data }),
  },
  poll: {
    title: 'poll: vote:N toggles the viewer\'s vote; chatData {v: counts, u: {viewer: option}}, publicData {v}',
    initial: { chatData: '{"v":[3,5,1],"u":{"7":0,"8":1,"9":1}}', publicData: '{"v":[3,5,1]}' },
    answer(data, { viewer, data: d }) {
      const m = /^vote:([0-9])$/.exec(data);
      if (!m) return { result: 'unknown' };
      const state = json(d.chatData, { v: [0, 0, 0], u: {} });
      const option = Number(m[1]);
      if (!(option < state.v.length)) return { result: 'unknown' };
      const before = state.u[viewer];
      if (before !== undefined) state.v[before]--;
      if (before === option) delete state.u[viewer];
      else { state.u[viewer] = option; state.v[option]++; }
      return { result: JSON.stringify({ voted: state.u[viewer] ?? null }), chatData: JSON.stringify(state), publicData: JSON.stringify({ v: state.v }) };
    },
  },
  tictactoe: {
    title: 'tic-tac-toe: move:N plays X and the bot answers O; reset; chatData {b, s}, publicData = the last game',
    initial: { chatData: '{"b":"---------","s":"play"}', publicData: null },
    answer(data, { data: d }) {
      if (data === 'reset') return { result: 'new game', chatData: '{"b":"---------","s":"play"}' };
      const m = /^move:([0-8])$/.exec(data);
      const game = json(d.chatData, { b: '---------', s: 'play' });
      if (!m || game.s !== 'play' || game.b[Number(m[1])] !== '-') return { result: 'invalid move' };
      let b = game.b.slice(0, Number(m[1])) + 'x' + game.b.slice(Number(m[1]) + 1);
      let w = winner(b);
      if (!w) { const o = botMove(b); b = b.slice(0, o) + 'o' + b.slice(o + 1); w = winner(b); }
      const next = JSON.stringify({ b, s: w ?? 'play' });
      return { result: w ? (w === 'draw' ? 'draw' : w + ' wins') : 'your move', chatData: next, ...(w ? { publicData: next } : {}) };
    },
  },
  ticker: {
    title: 'ticker: tick (background) → {t, p}; like → chatData {likes}',
    initial: { chatData: '{"likes":0}', publicData: null },
    answer(data, { data: d }) {
      if (data === 'like') return { result: 'liked', chatData: JSON.stringify({ likes: (json(d.chatData, {}).likes ?? 0) + 1 }) };
      const p = 100 + 10 * Math.sin(Date.now() / 20000) + Math.random();
      return { result: JSON.stringify({ t: new Date().toISOString().slice(11, 19), p: Math.round(p * 100) / 100 }) };
    },
  },
  promo: {
    title: 'promo: redeem once; publicData {code, off}, chatData {redeemed}',
    initial: { chatData: null, publicData: '{"code":"TG-WIDGET-2026","off":20}' },
    answer(data, { data: d }) {
      if (data !== 'redeem') return { result: 'unknown' };
      if (json(d.chatData, {}).redeemed) return { result: 'already' };
      return { result: 'ok', chatData: JSON.stringify({ redeemed: new Date().toISOString().slice(0, 16) }) };
    },
  },
  shop: {
    title: 'shop (the wallet): pay:N with { wallet: true } → a transaction to the shop (valid 5 min); paid → chatData {paid}; signData → {signed}',
    initial: { chatData: null, publicData: JSON.stringify({ items: SHOP_ITEMS.map(([n, a]) => [n, Number(a) / 1e9]) }) },
    answer(data, { wallet }) {
      const m = /^pay:([0-9])$/.exec(data);
      if (!m || !SHOP_ITEMS[Number(m[1])]) return { result: JSON.stringify({ err: 'unknown' }) };
      if (!wallet) return { result: JSON.stringify({ err: 'no wallet: ask with { wallet: true }' }) };
      const [, amount] = SHOP_ITEMS[Number(m[1])];
      if (wallet.balance < Number(amount)) return { result: JSON.stringify({ err: 'not enough TON', balance: wallet.balance / 1e9 }) };
      // A fresh order, priced now, valid 5 minutes: TON Connect's transaction as the widget passes it on.
      const tx = { valid_until: Math.floor(Date.now() / 1000) + 300, messages: [{ address: SHOP_ADDRESS, amount }] };
      return { result: JSON.stringify({ tx, from: wallet.address }) };
    },
    wallet(call, { msgHash, signed, wallet, data: d }) {
      // A real bot finds the transaction by msg_hash, waits for it to be final and checks the sender, the amount and the order.
      if (call.method === 'sendTransaction') {
        const item = SHOP_ITEMS.find(([, a]) => a === call.transaction.messages[0]?.amount)?.[0] ?? '?';
        const paid = [...(json(d.chatData, {}).paid ?? []), item].slice(-5);
        return { result: JSON.stringify({ paid: item, hash: msgHash?.slice(0, 8), from: wallet?.address.slice(0, 8) }), chatData: JSON.stringify({ paid }) };
      }
      return { result: JSON.stringify({ signed: signed?.domain, text: call.payload.text ?? null }) };
    },
  },
  // corpus/bot-demos (notes on the protocol in each file's header; the real bot is scripts/bot-demos.ts).
  gram: {
    title: 'bot-demos wallet test: w:me / w:pay with { wallet: true }, w:text, signData → s, sendTransaction → t wait, w:st → ok; chatData {m, s, t}',
    initial: { chatData: null, publicData: null },
    answer(data, { wallet, data: d }) {
      const state = json(d.chatData, {});
      const out = (part: Record<string, unknown>) => ({ result: JSON.stringify(part), chatData: JSON.stringify({ ...state, ...part }) });
      if (data === 'w:me') return wallet ? out({ m: { a: MOCK_WALLET_FRIENDLY, b: String(wallet.balance) } }) : { result: JSON.stringify({ err: 'No wallet card came: ask with { wallet: true }' }) };
      if (data === 'w:text') return { result: JSON.stringify({ text: `Gram Wallet test for @mockbot\nSigned at ${new Date().toISOString().slice(0, 19)}Z\nNonce ${Math.random().toString(16).slice(2, 14)}` }) };
      if (data === 'w:pay') {
        if (!wallet) return { result: JSON.stringify({ err: 'No wallet card came: ask with { wallet: true }' }) };
        return { result: JSON.stringify({ tx: { valid_until: Math.floor(Date.now() / 1000) + 300, network: '-239', messages: [{ address: MOCK_WALLET_FRIENDLY, amount: '10000000' }] } }) };
      }
      // The real bot looks the transfer up on toncenter by its message hash; here the chain has it on the first look.
      if (data === 'w:st') return state.t?.st === 'wait' ? out({ t: { h: state.t.h, st: 'ok', w: 'To the same wallet · fee 0.0031 TON' } }) : { result: JSON.stringify({ t: state.t ?? null }) };
      return { result: JSON.stringify({ err: 'Unknown request' }) };
    },
    wallet(call, { msgHash, data: d }) {
      const state = json(d.chatData, {});
      const part = call.method === 'sendTransaction'
        ? { t: { h: msgHash, st: 'wait', w: 'Waiting for the chain…' } }
        : { s: { ok: true, w: 'Valid · v4r2, the key on chain' } };
      return { result: JSON.stringify(part), chatData: JSON.stringify({ ...state, ...part }) };
    },
  },
  order: {
    title: 'bot-demos order: order:poll / order:refresh (refresh moves the order one step on), order:cancel while st ≤ 1',
    initial: {
      chatData: '{"items":[["Margherita",2,1250],["Burrata",1,900],["Lemonade",2,350]],"cur":"€","addr":"221B Baker St, flat 3","pay":"•• 4242"}',
      publicData: '{"shop":"Pizzeria Uno","e":"🍕","id":"A-1042","st":1,"eta":"19:40","u":"19:12","bot":"examplebot"}',
    },
    answer(data, { data: d }) {
      const p = json(d.publicData, { st: 0 });
      if (data === 'order:cancel') {
        if (p.st > 1 || p.st < 0) return { result: JSON.stringify({ err: p.st < 0 ? 'Already cancelled' : 'Too late to cancel' }) };
        p.st = -1;
      } else if (data === 'order:refresh') {
        // Each refresh moves the order one step on, and the courier gets closer.
        if (p.st >= 0 && p.st < 3) { p.st++; p.eta = new Date(Date.now() + (3 - p.st) * 8 * 60_000).toTimeString().slice(0, 5); }
      }
      else if (data !== 'order:poll') return { result: JSON.stringify({ err: 'unknown' }) };
      if (data !== 'order:poll') p.u = new Date().toTimeString().slice(0, 5);
      return { result: JSON.stringify({ st: p.st, eta: p.eta }), publicData: JSON.stringify(p) };
    },
  },
  vote: {
    title: 'bot-demos poll: vote:N toggles the viewer\'s vote; publicData {q, o, v, r} (r: the revision)',
    initial: { chatData: null, publicData: '{"q":"Where do we go on Friday?","o":["Pizza","Sushi","Tacos","Burgers"],"v":[3,5,1,2]}' },
    answer(data, { viewer, data: d }) {
      const m = /^vote:(\d)$/.exec(data), p = json(d.publicData, { o: [], v: [] });
      if (!m || Number(m[1]) >= p.o.length) return { result: JSON.stringify({ err: 'unknown' }) };
      const n = Number(m[1]), before = demoVotes.get(viewer);
      if (before !== undefined) p.v[before]--;
      if (before === n) demoVotes.delete(viewer); else { demoVotes.set(viewer, n); p.v[n]++; }
      p.r = (p.r ?? 0) + 1;
      return { result: JSON.stringify({ my: demoVotes.get(viewer) ?? -1, v: p.v, r: p.r }), publicData: JSON.stringify(p) };
    },
  },
  rsvp: {
    title: 'bot-demos rsvp: rsvp:in / rsvp:may / rsvp:out (the same again clears); chatData {in, may, out}, publicData {t, w, p, max, n}',
    initial: {
      chatData: '{"in":[[101,"Bob"],[102,"Eve"],[103,"Max"],[104,"Kim"],[105,"Leo"]],"may":[[106,"Zoe"]],"out":[[107,"Ian"]]}',
      publicData: '{"t":"Friday football","w":"Fri, Nov 6 · 19:00","p":"Central Park, field 3","max":10,"n":5}',
    },
    answer(data, { viewer, name, data: d }) {
      const m = /^rsvp:(in|may|out)$/.exec(data);
      if (!m) return { result: JSON.stringify({ err: 'unknown' }) };
      const c = json(d.chatData, { in: [], may: [], out: [] }), p = json(d.publicData, {});
      const was = (['in', 'may', 'out'] as const).find((k) => c[k].some((e: [number, string]) => e[0] === viewer));
      for (const k of ['in', 'may', 'out']) c[k] = c[k].filter((e: [number, string]) => e[0] !== viewer);
      const me = was === m[1] ? null : m[1];
      if (me === 'in' && c.in.length >= p.max) return { result: JSON.stringify({ err: 'Full', me: was ?? null }) };
      if (me) c[me].push([viewer, name]);
      p.n = c.in.length;
      return { result: JSON.stringify({ me }), chatData: JSON.stringify(c), publicData: JSON.stringify(p) };
    },
  },
  duel: {
    title: 'bot-demos duel: ttt:N, ttt:new; the mock plays O as Bob after your X so one person can try it',
    initial: { chatData: '{"b":"---------","x":null,"o":null,"t":"x","w":null,"l":null,"s":[0,0]}', publicData: null },
    answer(data, { viewer, name, data: d }) {
      const g = json(d.chatData, { b: '---------', x: null, o: null, t: 'x', w: null, l: null, s: [0, 0] });
      const err = (e: string) => ({ result: JSON.stringify({ err: e }) });
      const snap = () => JSON.stringify({ b: g.b, x: g.x?.[1] ?? null, o: g.o?.[1] ?? null, w: g.w, s: g.s, bot: 'examplebot' });
      const play = (mark: 'x' | 'o', i: number) => {
        g.b = g.b.slice(0, i) + mark + g.b.slice(i + 1);
        const line = LINES.find(([a, c, e]) => g.b[a] !== '-' && g.b[a] === g.b[c] && g.b[a] === g.b[e]);
        if (line) { g.w = mark; g.l = line; g.s[mark === 'x' ? 0 : 1]++; } else if (!g.b.includes('-')) g.w = 'd';
        g.t = mark === 'x' ? 'o' : 'x';
      };
      if (data === 'ttt:new') {
        if (!g.w) return err('Game on');
        const loserStarts = g.w === 'x' ? 'o' : 'x';
        Object.assign(g, { b: '---------', t: loserStarts, w: null, l: null });
        // Bob lost or drew as O and starts the rematch: he moves at once.
        if (g.t === 'o' && g.o?.[0] === 2002) play('o', botMove(g.b));
        return { result: '{"ok":1}', chatData: JSON.stringify(g), publicData: snap() };
      }
      const m = /^ttt:([0-8])$/.exec(data);
      if (!m) return err('unknown');
      if (g.w) return err('Game over');
      if (!g.x) g.x = [viewer, name];
      const mark = g.x[0] === viewer ? 'x' : g.o?.[0] === viewer ? 'o' : g.o ? null : 'o';
      if (!mark) return err('Full');
      if (mark === 'o' && !g.o) g.o = [viewer, name];
      if (g.t !== mark) return err('Not your turn');
      if (g.b[Number(m[1])] !== '-') return err('Taken');
      play(mark, Number(m[1]));
      // One person in the dev app: Bob answers as O.
      if (!g.w && mark === 'x' && (!g.o || g.o[0] === 2002)) { g.o = [2002, 'Bob']; play('o', botMove(g.b)); }
      return { result: '{"ok":1}', chatData: JSON.stringify(g), publicData: snap() };
    },
  },
  quote: {
    title: 'bot-demos ticker: no requests; push a new publicData with __tw.bot.edit or the panel to animate it',
    initial: { chatData: null, publicData: '{"s":"TON","n":"Toncoin","c":"$","p":5.4321,"o":5.31,"h":[5.31,5.29,5.33,5.36,5.34,5.38,5.41,5.39,5.37,5.4,5.44,5.42,5.45,5.43,5.41,5.39,5.42,5.46,5.44,5.47,5.45,5.43,5.44,5.4321],"t":"21:59:30"}' },
    answer: () => ({ result: JSON.stringify({ err: 'no requests' }) }),
  },
  reaction: {
    title: 'bot-demos reaction: score:N (100–2000 ms) → the shared top (8 rows, also in the answer) and the chat\'s best',
    initial: { chatData: '{"best":243}', publicData: '{"top":[["Max",171],["Eve",188],["Bob",203],["Kim",219],["Leo",240],["Zoe",262]],"plays":42}' },
    answer(data, { viewer, name, data: d }) {
      const m = /^score:(\d{3,4})$/.exec(data), ms = m ? Number(m[1]) : 0;
      if (!(ms >= 100 && ms <= 2000)) return { result: JSON.stringify({ err: 'Not a human time' }) };
      const p = json(d.publicData, { top: [], plays: 0 }), c = json(d.chatData, {});
      const mine = p.top.find((r: [string, number]) => r[0] === name);
      if (mine) mine[1] = Math.min(mine[1], ms); else p.top.push([name, ms]);
      p.top.sort((a: [string, number], b: [string, number]) => a[1] - b[1]);
      p.top = p.top.slice(0, 8);
      p.plays++;
      p.r = (p.r ?? 0) + 1;
      const best = Math.min(c.best ?? ms, ms), rank = p.top.findIndex((r: [string, number]) => r[0] === name) + 1;
      return { result: JSON.stringify({ rank, best: mine ? mine[1] : ms, top: p.top, plays: p.plays, r: p.r }), chatData: JSON.stringify({ best }), publicData: JSON.stringify(p) };
    },
  },
};

/** The bot-demos poll's votes by viewer (the mock keeps no per-viewer state in the message). */
const demoVotes = new Map<number, number>();

/** How the mock bot answers: with the selected bot, or as a client's failures; `manual` keeps the request until a button answers it. */
export const MODES = ['bot', 'manual', 'TIMEOUT', 'RATE_LIMITED', 'FAILED'] as const;
export type Mode = (typeof MODES)[number];

export interface RequestRecord {
  at: string;
  file: string | null;
  data: string;
  /** `wallet`: a request that asked for the wallet; `sendTransaction` / `signData`: a wallet call. */
  kind?: 'wallet' | 'sendTransaction' | 'signData';
  /** What went back to the host: the answer, or a failure code; undefined while pending. */
  answer?: WidgetRequestAnswer;
  error?: string;
}

/** The bot named by `<meta name="tw-bot" content="…">` in the widget, `echo` otherwise. */
export function botOf(html: string): string {
  const m = /<meta\s+name=["']tw-bot["']\s+content=["']([\w-]+)["']/i.exec(html);
  return m && BOTS[m[1]] ? m[1] : 'echo';
}

export interface MockBotHooks {
  /** The context changed: the widget is re-created (a host's context is fixed for its life). */
  restart(): void;
  /** The bot edited the data: every live host of the file gets it (the server's update, or the polled shared object). */
  setData(data: Data): void;
  /** Something the panel shows changed. */
  changed(): void;
  /** The client's wallet window for a wallet call: true when the user confirmed it. */
  confirmWallet(call: WalletCall): Promise<boolean>;
}

export class MockBot {
  bot = 'echo';
  mode: Mode = 'bot';
  delayMs = 300;
  /**
   * The kind of message (the server's flags), its chat type (null: the kind's own), the viewer's id and the rest of
   * the viewer as `first,last,username,premium` (`viewerFromList`; the host checks it).
   */
  context: { message: WidgetMessageKind; chatType: ChatType | null; viewer: number | null; profile: string } =
    { message: 'private', chatType: null, viewer: 1001, profile: DEFAULT_PROFILE };
  /**
   * The widget is a shared object (its message has a `widget_id`): the client polls its `public_data` while it is on
   * screen and watches (`TelegramWidget.watchPublicData`), every `pollMs` (the server's period: 5–3600 s,
   * default 30 s, here shorter for the dev app). Without it nothing is polled.
   */
  shared = true;
  pollMs = 5000;
  /** The live widget watches its public_data (the host's `onWatchPublicData`); the panel shows it. */
  watching = false;
  /** Polls made, and the ones that brought a new value. */
  polls = { made: 0, changed: 0 };
  /** The client has Gram Wallet for this user (`WidgetContext.wallet`; `?wallet=0` off). */
  wallet = true;
  readonly requests: RequestRecord[] = [];
  private data = new Map<string, Data>();
  /** The data the live widget has: what it got with the message, a pushed edit or a poll. */
  private delivered = new Map<string, Data>();
  private pending = new Map<RequestRecord, (outcome: { answer?: WidgetRequestAnswer; error?: string }) => void>();

  constructor(readonly hooks: MockBotHooks) {}

  /** The message's data as the "server" has it now (the bot's initial data the first time). */
  dataFor(file: string | null): Data {
    const key = file ?? '';
    if (!this.data.has(key)) this.data.set(key, { ...BOTS[this.bot].initial });
    return this.data.get(key)!;
  }

  /** A fresh message: the bot's initial data again. */
  resetData(file: string | null) {
    this.data.delete(file ?? '');
  }

  /** The client created a host with the message's data (`dataFor`): that is what the widget has now. */
  loaded(file: string | null) {
    this.delivered.set(file ?? '', this.dataFor(file));
    this.watching = false;
  }

  /**
   * The bot's `editWidgetData` (a shared object by its id): the server's value changes, nothing is pushed; the widget
   * gets it at the client's next poll if it watches, or with the message the next time it is shown.
   */
  editShared(file: string | null, publicData: string | null) {
    this.data.set(file ?? '', { ...this.dataFor(file), publicData });
    this.hooks.changed();
  }

  /** The client's poll (`getWidgetPublicData`): the full state if the shared value changed since the widget got it, else null. */
  poll(file: string | null): Data | null {
    if (!this.shared) return null;
    this.polls.made++;
    const key = file ?? '';
    const now = this.dataFor(file), had = this.delivered.get(key);
    if (had && had.publicData === now.publicData) return null;
    this.polls.changed++;
    const next = { chatData: had?.chatData ?? now.chatData, publicData: now.publicData };
    this.delivered.set(key, next);
    this.hooks.changed();
    return next;
  }

  hostContext(): WidgetContext {
    const c = this.context;
    const kind = WIDGET_MESSAGE_KINDS[c.message];
    const viewer = c.viewer !== null ? viewerFromList(`${c.viewer},${c.profile}`) : null;
    return { ...kind, chatType: c.chatType ?? kind.chatType, ...(viewer ? { viewer } : {}), ...(this.wallet ? { wallet: true } : {}) };
  }

  /** The bot's edit (`editMessageWidgetData`): the full state of both strings to every live host. */
  edit(file: string | null, data: Data) {
    this.data.set(file ?? '', data);
    this.delivered.set(file ?? '', data);
    this.hooks.setData(data);
    this.hooks.changed();
  }

  /** `WidgetHostOptions.onRequest`: what the client would send over MTProto and get back. */
  request(file: string | null, req: WidgetRequest): Promise<WidgetRequestAnswer> {
    const rec: RequestRecord = { at: new Date().toISOString().slice(11, 23), file, data: req.data, ...(req.wallet ? { kind: 'wallet' as const } : {}) };
    this.requests.push(rec);
    if (this.requests.length > 200) this.requests.shift();
    this.hooks.changed();
    return new Promise<WidgetRequestAnswer>((resolve, reject) => {
      const settle = (o: { answer?: WidgetRequestAnswer; error?: string }) => {
        this.pending.delete(rec);
        rec.answer = o.answer;
        rec.error = o.error;
        this.hooks.changed();
        if (o.error) reject(o.error);
        else resolve(o.answer!);
      };
      this.pending.set(rec, settle);
      if (this.mode === 'manual') return;
      const mode = this.mode;
      setTimeout(() => (mode === 'bot' ? this.answer(rec) : this.fail(rec, mode)), this.delayMs);
    });
  }

  /**
   * Answers a pending request with the selected bot, as a bot does: it edits its data with a separate call (which
   * reaches every live host as the server's update, here before the answer), then answers with `result` only.
   */
  answer(rec: RequestRecord) {
    const settle = this.pending.get(rec);
    if (!settle) return;
    const ctx = this.botContext(rec.file, rec.kind === 'wallet');
    this.apply(rec.file, settle, BOTS[this.bot].answer(rec.data, ctx));
  }

  private botContext(file: string | null, wallet: boolean): BotContext {
    return { viewer: this.context.viewer ?? 0, name: this.context.profile.split(',')[0] || `User ${this.context.viewer}`, data: this.dataFor(file), ...(wallet ? { wallet: MOCK_WALLET } : {}) };
  }

  /** The bot's edits (as the server's update, before the answer), then the answer. */
  private apply(file: string | null, settle: (o: { answer?: WidgetRequestAnswer; error?: string }) => void, reply: Reply) {
    const data = this.dataFor(file);
    if ('chatData' in reply || 'publicData' in reply) {
      this.edit(file, { chatData: 'chatData' in reply ? reply.chatData || null : data.chatData, publicData: 'publicData' in reply ? reply.publicData || null : data.publicData });
    }
    settle({ answer: { result: reply.result ?? null } });
  }

  /**
   * `WidgetHostOptions.onWallet`: the client's wallet window (`confirmWallet`), then, confirmed, the transaction sent or
   * the data signed for `bot<bot_id>`, and the outcome to the bot as a query (with the wallet card); its answer back. A
   * decline is REJECTED; the panel's failure modes fail the query after the window.
   */
  async walletCall(file: string | null, call: WalletCall): Promise<WidgetRequestAnswer> {
    const rec: RequestRecord = { at: new Date().toISOString().slice(11, 23), file, data: call.data, kind: call.method };
    this.requests.push(rec);
    if (this.requests.length > 200) this.requests.shift();
    this.hooks.changed();
    const done = (o: { answer?: WidgetRequestAnswer; error?: string }) => { rec.answer = o.answer; rec.error = o.error; this.hooks.changed(); };
    if (!(await this.hooks.confirmWallet(call))) {
      done({ error: 'REJECTED' });
      throw { code: 'REJECTED' };
    }
    await new Promise((r) => setTimeout(r, this.delayMs));
    if (this.mode !== 'bot' && this.mode !== 'manual') {
      done({ error: this.mode });
      throw { code: this.mode };
    }
    const hex = (n: number) => Array.from({ length: n }, () => Math.floor(Math.random() * 16).toString(16)).join('');
    const outcome = call.method === 'sendTransaction'
      ? { msgHash: hex(64) }
      : { signed: { signature: btoa(hex(64)), domain: 'bot1234567', timestamp: Math.floor(Date.now() / 1000) } };
    const bot = BOTS[this.bot];
    const reply = bot.wallet ? bot.wallet(call, { ...this.botContext(file, true), ...outcome }) : { result: null };
    return new Promise<WidgetRequestAnswer>((resolve) => this.apply(file, (o) => { done(o); resolve(o.answer!); }, reply));
  }

  fail(rec: RequestRecord, code: string) {
    this.pending.get(rec)?.({ error: code });
  }

  get pendingRequests(): RequestRecord[] {
    return [...this.pending.keys()];
  }
}

// ───────────── the panel ─────────────

function make<T extends HTMLElement = HTMLElement>(tag: string, props: Record<string, unknown> = {}, ...children: (Node | string)[]): T {
  const e = Object.assign(document.createElement(tag), props) as T;
  e.append(...children);
  return e;
}
const bytes = (s: string) => `${utf8Length(s)} / ${MAX_WIDGET_DATA_BYTES} B`;

/** The right column's "Bot (mock)" section: context, the data and its edit, the answer mode, the requests. */
export function mountPanel(root: HTMLElement, bot: MockBot, file: () => string | null) {
  const message = make<HTMLSelectElement>('select', { title: 'the kind of message, and so the server\'s flags: private = the original in the bot\'s private chat (taps and background requests); group, via-bot = taps only; no-queries = an original sent without allow_queries (no requests, no network marker); live-copy = a forward of a widget sent with fwd_allow_queries (taps only); copy = a forward without it (no requests); preview = an inline result not sent yet. Re-creates the widget' },
    ...Object.entries(WIDGET_MESSAGE_KINDS).map(([k, f]) => make('option', { value: k, textContent: k, title: `isCopy ${f.isCopy}, canQuery ${f.canQuery}, backgroundQuery ${f.backgroundQuery}` })));
  const chatType = make<HTMLSelectElement>('select', { title: 'the chat kind the client reports; default: the message kind\'s (re-creates the widget)' }, ...['', ...CHAT_TYPES].map((t) => make('option', { value: t, textContent: t || '(the kind\'s)' })));
  const viewer = make<HTMLInputElement>('input', { type: 'number', min: '1', title: 'the viewer\'s user id; empty: none (re-creates the widget)' });
  const profile = make<HTMLInputElement>('input', { type: 'text', size: 18, placeholder: 'first,last,username,premium', title: 'the rest of the viewer (WebAppUser): first name, last name, username, premium; empty parts are absent; the host leaves out a field that breaks its rule (data-rejected). Re-creates the widget' });
  const botSelect = make<HTMLSelectElement>('select', { title: 'which built-in bot answers; a widget names its own with <meta name="tw-bot">' }, ...Object.entries(BOTS).map(([k, b]) => make('option', { value: k, textContent: k, title: b.title })));
  const mode = make<HTMLSelectElement>('select', { title: 'bot: the bot answers after the delay; manual: answer each request below; the rest fail as a client reports it' }, ...MODES.map((m) => make('option', { value: m, textContent: m })));
  const delay = make<HTMLInputElement>('input', { type: 'number', min: '0', step: '100', title: 'how long the bot takes to answer (ms)' });
  const chatData = make<HTMLTextAreaElement>('textarea', { rows: 2, spellcheck: false, placeholder: 'chatData (null)' });
  const publicData = make<HTMLTextAreaElement>('textarea', { rows: 2, spellcheck: false, placeholder: 'publicData (null)' });
  const size = make('span', { className: 'muted' });
  const apply = make('button', { type: 'button', textContent: 'edit data', title: 'the bot\'s editMessageWidgetData: both strings to the widget (an empty field is none)' });
  const reset = make('button', { type: 'button', textContent: 'reset', title: 'the bot\'s initial data again (a fresh message; re-creates the widget)' });
  const byId = make('button', { type: 'button', textContent: 'edit by id', title: 'the bot\'s editWidgetData: only publicData, in the shared object; nothing is pushed: the widget gets it at the next poll if it watches (watchPublicData), else with the message next time' });
  const shared = make<HTMLInputElement>('input', { type: 'checkbox', title: 'the widget is a shared object (its message has a widget_id): the client polls its publicData while it watches' });
  const wallet = make<HTMLInputElement>('input', { type: 'checkbox', title: 'the client has Gram Wallet for this user (WidgetContext.wallet): TelegramWidget.wallet where the widget may ask the bot. Re-creates the widget' });
  const period = make<HTMLInputElement>('input', { type: 'number', min: '100', step: '500', title: 'the poll period in ms (the server\'s period: 5–3600 s, 30 s by default; shorter here)' });
  const polling = make('span', { className: 'muted' });
  const list = make('ul', { id: 'bot-requests' });
  const title = make('div', { className: 'muted bot-title' });
  root.replaceChildren(
    make('div', { className: 'bot-row' }, make('label', {}, 'message ', message), make('label', {}, 'chat ', chatType), make('label', {}, 'viewer ', viewer), make('label', {}, 'as ', profile), make('label', {}, wallet, ' wallet')),
    make('div', { className: 'bot-row' }, make('label', {}, 'bot ', botSelect), make('label', {}, 'answers ', mode), make('label', {}, delay, ' ms')),
    title, chatData, publicData,
    make('div', { className: 'bot-row' }, apply, byId, reset, size),
    make('div', { className: 'bot-row' }, make('label', {}, shared, ' shared (widget_id)'), make('label', {}, 'poll ', period, ' ms'), polling),
    list,
  );

  const fill = () => {
    const c = bot.context;
    message.value = c.message;
    chatType.value = c.chatType ?? '';
    viewer.value = c.viewer === null ? '' : String(c.viewer);
    profile.value = c.profile;
    botSelect.value = bot.bot;
    mode.value = bot.mode;
    delay.value = String(bot.delayMs);
    title.textContent = BOTS[bot.bot].title;
    const d = bot.dataFor(file());
    if (document.activeElement !== chatData) chatData.value = d.chatData ?? '';
    if (document.activeElement !== publicData) publicData.value = d.publicData ?? '';
    size.textContent = `chatData ${bytes(chatData.value)}, publicData ${bytes(publicData.value)}`;
    shared.checked = bot.shared;
    wallet.checked = bot.wallet;
    if (document.activeElement !== period) period.value = String(bot.pollMs);
    polling.textContent = !bot.shared ? 'not shared: never polled' : bot.watching ? `watching: polled ${bot.polls.made}×, ${bot.polls.changed} new` : 'not watching: not polled';
  };
  const renderRequests = () => {
    list.replaceChildren(...bot.requests.slice(-12).reverse().map((r) => {
      const outcome = r.error ? `✖ ${r.error}` : r.answer ? `→ ${JSON.stringify(r.answer.result)}` : '… pending';
      const kind = r.kind === 'wallet' ? ' + wallet' : r.kind ? ` (wallet.${r.kind})` : '';
      const li = make('li', { title: r.answer ? JSON.stringify(r.answer) : '' }, `${r.at} ${JSON.stringify(r.data)}${kind} ${outcome}`);
      if (!r.kind?.startsWith('s') && !r.answer && !r.error && bot.pendingRequests.includes(r)) {
        li.append(' ', make('button', { type: 'button', textContent: 'answer', onclick: () => bot.answer(r) }), ' ', make('button', { type: 'button', textContent: 'timeout', onclick: () => bot.fail(r, 'TIMEOUT') }));
      }
      return li;
    }));
  };
  const restartWith = (patch: Partial<MockBot['context']>) => { Object.assign(bot.context, patch); bot.hooks.restart(); bot.hooks.changed(); };
  message.addEventListener('change', () => restartWith({ message: message.value as WidgetMessageKind }));
  chatType.addEventListener('change', () => restartWith({ chatType: (chatType.value || null) as ChatType | null }));
  viewer.addEventListener('change', () => restartWith({ viewer: viewer.value ? Number(viewer.value) : null }));
  profile.addEventListener('change', () => restartWith({ profile: profile.value }));
  botSelect.addEventListener('change', () => { bot.bot = botSelect.value; fill(); });
  mode.addEventListener('change', () => { bot.mode = mode.value as Mode; bot.hooks.changed(); });
  delay.addEventListener('change', () => { bot.delayMs = Math.max(0, Number(delay.value) || 0); });
  for (const t of [chatData, publicData]) t.addEventListener('input', () => { size.textContent = `chatData ${bytes(chatData.value)}, publicData ${bytes(publicData.value)}`; });
  apply.addEventListener('click', () => bot.edit(file(), { chatData: chatData.value || null, publicData: publicData.value || null }));
  reset.addEventListener('click', () => { bot.resetData(file()); chatData.value = ''; publicData.value = ''; bot.hooks.restart(); });
  byId.addEventListener('click', () => bot.editShared(file(), publicData.value || null));
  shared.addEventListener('change', () => { bot.shared = shared.checked; bot.hooks.changed(); });
  wallet.addEventListener('change', () => { bot.wallet = wallet.checked; bot.hooks.restart(); bot.hooks.changed(); });
  period.addEventListener('change', () => { bot.pollMs = Math.max(100, Number(period.value) || 5000); bot.hooks.changed(); });
  return { render: () => { fill(); renderRequests(); } };
}

/** What a client shows after `copyText`: the copied text itself (anti-swap), over the bubble for a moment. */
export function showCopied(bubble: HTMLElement, text: string) {
  const tag = make('div', { className: 'bubble-gesture bubble-copied', textContent: `Copied: ${text}` });
  bubble.querySelector('.bubble-copied')?.remove();
  bubble.appendChild(tag);
  setTimeout(() => tag.remove(), 2000);
}

const TON = (nano: string | number) => `${(Number(nano) / 1e9).toLocaleString('en-US', { maximumFractionDigits: 9 })} TON`;

/**
 * The client's own wallet window over the chat (docs/sdk-v1.md §2.18, §5): who asks (the bot of the message, never the
 * widget), what for (each message's amount and destination, or the text to sign; a warning for bytes and cells), and
 * Confirm / Decline. Taps on it are ignored for its first 600 ms (a double tap's second tap must not land on Confirm).
 * Resolves true on Confirm. Dev tooling: a client draws its existing TON Connect window.
 */
export function showWalletWindow(botName: string, call: WalletCall): Promise<boolean> {
  return new Promise((resolve) => {
    const what = call.method === 'sendTransaction'
      ? call.transaction.messages.map((m) => make('div', { className: 'wallet-line' }, make('b', {}, TON(m.amount)), ` → ${m.address.slice(0, 6)}…${m.address.slice(-4)}`, m.payload ? ' (with a payload)' : '', m.stateInit ? ' (deploys a contract)' : ''))
      : [call.payload.type === 'text' ? make('pre', { className: 'wallet-text' }, call.payload.text ?? '') : make('div', { className: 'wallet-warn' }, `Signs ${call.payload.type === 'cell' ? 'a cell' : 'bytes'} the wallet cannot show. Only sign what you trust.`)];
    const confirm = make<HTMLButtonElement>('button', { type: 'button', id: 'wallet-confirm', textContent: call.method === 'sendTransaction' ? 'Confirm and send' : 'Sign', disabled: true });
    const decline = make<HTMLButtonElement>('button', { type: 'button', id: 'wallet-decline', textContent: 'Decline', disabled: true });
    const sheet = make('div', { className: 'wallet-sheet', role: 'dialog' },
      make('div', { className: 'wallet-card' },
        make('div', { className: 'wallet-head' }, `@${botName}`, make('span', { className: 'muted' }, call.method === 'sendTransaction' ? ' asks you to send' : ' asks you to sign')),
        ...what,
        make('div', { className: 'muted wallet-from' }, `from your wallet ${MOCK_WALLET.address.slice(0, 6)}…, ${TON(MOCK_WALLET.balance)}`),
        make('div', { className: 'wallet-buttons' }, decline, confirm)));
    const close = (ok: boolean) => { sheet.remove(); resolve(ok); };
    confirm.onclick = () => close(true);
    decline.onclick = () => close(false);
    document.body.appendChild(sheet);
    setTimeout(() => { confirm.disabled = decline.disabled = false; }, 600);
  });
}

export type { Data as MockBotData, WidgetData };

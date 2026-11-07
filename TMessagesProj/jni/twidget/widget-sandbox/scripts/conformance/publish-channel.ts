// Fills a Telegram forum supergroup (one topic per widget kind) with the
// conformance set through a test bot, in the order of
// packages/conformance/channel.json. Each post puts three things into its
// topic, in this order:
//   1. the description: the post's caption, as a text message;
//   2. the widget: sendWidget (Bot API 10.4, on the beta Bot API until it is
//      released) with the HTML, its size anchors and its caption. A bot may
//      send a widget only into its private chat with a user, and forwarding is
//      allowed into any chat,
//      so by default the widget goes to the tester's private chat and is
//      forwarded into the topic from there (never copyMessage: a copy is a new
//      send, which the server refuses in a group). A test bot that may post
//      widgets into any chat (TW_BOT_DIRECT=1) sends it straight into the topic;
//   3. the reference picture(s): sendPhoto, or sendMediaGroup for light + dark;
//      a picture Telegram would reject or shrink goes as a document instead.
// The automatic set has no picture: its widgets report their own result.
//
// The size anchors go as sendWidget's width and height (0 = auto on that axis,
// left out). The regular Bot API does not know widgets: an HTML document the test
// bot turned into a widget lost show_caption_above_media (2026-10-30).
//
// Config (never inside a git working tree; environment variables win):
//   ~/.config/widget-sandbox/test-bot.env, or --config <file>:
//     TW_BOT_TOKEN=…       the bot token
//     TW_BOT_USER=…        the tester's user id: the private chat the widgets go to first
//     TW_BOT_DIRECT=1      optional: the bot sends widgets straight into the group (TW_BOT_USER optional then)
//     TW_BOT_GROUP=…       the forum supergroup (the bot is an admin with Manage topics and Delete messages)
//     TW_BOT_ID=…          optional: the bot's id, checked against the token
//     TW_BOT_API=…         optional, default https://api.telegram.org/beta (sendWidget is Bot API 10.4)
//     TW_BOT_TEST_ENV=1    optional: the Bot API's test environment (/bot<token>/test/<method>)
// State: ~/.config/widget-sandbox/conformance-channel.state.json (--state <file>),
// per group: the topics and, per post, the hashes and the message ids. A re-run
// skips unchanged posts, edits a changed description or picture in place, and
// for a changed or new widget deletes that post and every later one of its
// topic and posts them again, so a topic stays in posting order.
//
//   corepack pnpm tsx scripts/conformance/publish-channel.ts --dry-run
//   corepack pnpm tsx scripts/conformance/publish-channel.ts --only visual/01-boxes
//   corepack pnpm tsx scripts/conformance/publish-channel.ts [--topic visual,auto] [--limit N]
//   corepack pnpm tsx scripts/conformance/publish-channel.ts --reset [--topic …] [--only …] [--delete-topics]
//
// --dry-run prints the plan and sends nothing; with a config it also checks the
// bot, the group and the tester's chat with read-only calls (getMe, getChat,
// getChatMember). --only takes comma-separated key patterns (a substring, or a
// glob with *); it works on the matching posts alone, so a changed one is
// reposted at the end of its topic and the next full run restores the order.
// --limit N stops after N posts sent. --verbose prints what the server
// returned for each widget message. When old messages of a topic cannot be
// deleted (the server lets a bot delete its messages only for a while), the
// run posts nothing again in that topic; --rebuild deletes the selected topics
// with everything in them and posts them again. --reset deletes the messages this script
// recorded (group and private chat) and forgets them; --delete-topics also
// deletes the topics, with everything in them (testers' replies included).

import crypto from 'node:crypto';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import zlib from 'node:zlib';
import { fileURLToPath } from 'node:url';

const ROOT = path.join(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
const CONF = path.join(ROOT, 'packages', 'conformance');
const CONFIG_DIR = path.join(os.homedir(), '.config', 'widget-sandbox');

/**
 * The topics, in the order they are filled. `channel`: the posts of
 * channel.json under this widget-path prefix (the longest prefix wins).
 * `corpus`: every widget of a corpus directory that fits a mini-widget, in file
 * order, with no picture. `default: false` topics go out only when --topic names them (its name in lower case,
 * spaces as hyphens: `--topic safe-area`). `withoutCaption`: its widgets go out without a caption, so a client draws
 * its time over them (docs/sdk-v1.md §2.17); a file whose header says `caption: none, below, above` goes out once per
 * placement: without a caption, with one below the widget (0: the time in the caption's line), with one above it.
 * icon colours: the six Telegram allows for a forum topic.
 */
interface TopicSpec { name: string; channel?: string; corpus?: string; default: boolean; color: number; withoutCaption?: boolean }
const TOPICS: TopicSpec[] = [
  { name: 'Visual', channel: 'visual/', default: true, color: 0x6fb9f0 },
  { name: 'Client SDK', channel: 'visual/sdk/', default: true, color: 0xffd67e },
  { name: 'Auto', channel: 'auto/', default: true, color: 0x8eee98 },
  // Widgets with little or no CSS, drawn by the renderer's UA layer (v0.1.1, corpus/defaults/README.md).
  { name: 'Defaults', corpus: 'corpus/defaults', default: true, color: 0xcb86db },
  // What the horizontal-only rule removed and what it keeps (corpus/gestures/README.md); --topic gestures.
  { name: 'Gestures', corpus: 'corpus/gestures', default: false, color: 0x6fb9f0 },
  // One widget per resize anchor: the default, growing up, growing down (corpus/resize/README.md); --topic resize.
  { name: 'Resize', corpus: 'corpus/resize', default: false, color: 0x8eee98 },
  // The client's time over the corner of a widget without a caption (corpus/safe-area/README.md); --topic safe-area.
  { name: 'Safe area', corpus: 'corpus/safe-area', default: false, color: 0xffd67e, withoutCaption: true },
  { name: 'Showcase', corpus: 'corpus/showcase', default: false, color: 0xcb86db },
  { name: 'Common', corpus: 'corpus/common', default: false, color: 0xff93b2 },
  { name: 'Generated', corpus: 'corpus/generated/with-profile', default: false, color: 0xfb6f5f },
];

/** The mini-widget gate of packages/conformance (build.ts): raw and zlib at level 9, as the server counts it. */
const MAX_RAW = 32 * 1024;
const MAX_ZLIB = 3800;
/** sendPhoto: at most 10 MB, width + height at most 10000, ratio at most 20; the server keeps at most 2560 px on the long side. */
const PHOTO = { bytes: 10 * 1024 * 1024, sum: 10000, ratio: 20, side: 2560 };
const TEXT_MAX = 4096;
/** Pacing: about 20 messages a minute into a group, 1 a second into a private chat. */
const PACE_MS = { group: 3200, user: 1100 };

// ---- arguments

const argv = process.argv.slice(2);
const flag = (name: string) => argv.includes(name);
const opt = (name: string) => {
  const i = argv.indexOf(name);
  if (i < 0) return undefined;
  const v = argv[i + 1];
  if (v === undefined || v.startsWith('--')) die(`${name} needs a value`);
  return v;
};
const known = new Set(['--dry-run', '--reset', '--delete-topics', '--rebuild', '--only', '--topic', '--limit', '--config', '--state', '--verbose']);
for (const a of argv) if (a.startsWith('--') && !known.has(a)) die(`unknown option ${a}`);
const DRY = flag('--dry-run');
const RESET = flag('--reset');
const DELETE_TOPICS = flag('--delete-topics');
const VERBOSE = flag('--verbose');
const REBUILD = flag('--rebuild');
const ONLY = opt('--only')?.split(',').map((s) => s.trim()).filter(Boolean);
const LIMIT = opt('--limit') !== undefined ? Number(opt('--limit')) : Infinity;
if (!(LIMIT >= 0)) die('--limit takes a number');
if (DELETE_TOPICS && !RESET) die('--delete-topics goes with --reset');
if (REBUILD && (RESET || ONLY)) die('--rebuild works on whole topics: not with --reset or --only');
/** A topic as --topic names it: `Safe area` is `safe-area`. */
const topicArg = (name: string) => name.toLowerCase().replace(/\s+/g, '-');
const topicNames = opt('--topic')?.split(',').map((s) => topicArg(s.trim()));
for (const n of topicNames ?? []) if (!TOPICS.some((t) => topicArg(t.name) === n)) die(`no topic "${n}" (${TOPICS.map((t) => topicArg(t.name)).join(', ')})`);
const SELECTED = TOPICS.filter((t) => (topicNames ? topicNames.includes(topicArg(t.name)) : t.default));

function die(msg: string): never {
  console.error(`publish-channel: ${msg}`);
  process.exit(1);
}

const selects = (key: string) =>
  !ONLY ||
  ONLY.some((p) => (p.includes('*') ? new RegExp(`^${p.split('*').map(escapeRe).join('.*')}$`).test(key) : key.includes(p)));
const escapeRe = (s: string) => s.replace(/[.+?^${}()|[\]\\]/g, '\\$&');

// ---- config and state

interface Config { token: string; user: string; group: string; base: string; direct: boolean }

function insideGitTree(file: string) {
  for (let d = path.dirname(path.resolve(file)); ; d = path.dirname(d)) {
    if (fs.existsSync(path.join(d, '.git'))) return true;
    if (path.dirname(d) === d) return false;
  }
}

function loadConfig(): Config | undefined {
  const file = opt('--config') ?? path.join(CONFIG_DIR, 'test-bot.env');
  const vals: Record<string, string> = {};
  if (fs.existsSync(file)) {
    if (insideGitTree(file)) die(`the config ${file} is inside a git working tree; keep it outside the repository`);
    for (const line of fs.readFileSync(file, 'utf8').split('\n')) {
      const m = /^\s*(?:export\s+)?([A-Z0-9_]+)\s*=\s*(.*?)\s*$/.exec(line);
      if (m && !line.trim().startsWith('#')) vals[m[1]] = m[2].replace(/^(['"])(.*)\1$/, '$2');
    }
  } else if (opt('--config')) die(`no config file ${file}`);
  const get = (k: string) => process.env[k] || vals[k] || '';
  const token = get('TW_BOT_TOKEN');
  const user = get('TW_BOT_USER');
  const group = get('TW_BOT_GROUP');
  if (!token && !user && !group) return undefined;
  // A dry run plans without the token (against the group's recorded state when the ids are there).
  const direct = get('TW_BOT_DIRECT') === '1';
  const missing = [['TW_BOT_TOKEN', token], ['TW_BOT_USER', user], ['TW_BOT_GROUP', group]]
    .filter(([k, v]) => !v && !(DRY && k === 'TW_BOT_TOKEN') && !(direct && k === 'TW_BOT_USER')).map(([k]) => k);
  if (missing.length) die(`config is missing ${missing.join(', ')}`);
  if (user && !/^\d+$/.test(user)) die('TW_BOT_USER must be a user id, the private chat with the tester');
  // A supergroup id as the apps show it (1234567890) or as the Bot API takes it (-1001234567890).
  if (!/^(-100)?\d+$/.test(group)) die('TW_BOT_GROUP must be a supergroup id');
  const chat = group.startsWith('-100') ? group : `-100${group}`;
  const bot = get('TW_BOT_ID');
  if (bot && token && !token.startsWith(`${bot}:`)) die('TW_BOT_TOKEN is not the token of TW_BOT_ID');
  const base = (get('TW_BOT_API') || 'https://api.telegram.org/beta').replace(/\/+$/, '');
  return { token, user, group: chat, direct, base: get('TW_BOT_TEST_ENV') === '1' ? `${base}/bot${token}/test` : `${base}/bot${token}` };
}

interface PostRecord {
  key: string;
  hash: Hashes;
  /** Every message this post put out was sent and recorded. */
  complete: boolean;
  description?: number;
  /** The original widget message in the private chat. */
  private?: number;
  /** The forwarded copy in the topic. */
  widget?: number;
  photos: number[];
  /** The pictures went as documents (too large for a photo). */
  photosAsDocuments?: boolean;
}
interface TopicRecord { threadId: number; posts: PostRecord[] }
interface GroupState {
  topics: Record<string, TopicRecord>;
  /** Messages the server refused to delete; --reset tries them again. */
  orphans?: { group: number[]; user: number[] };
}
interface State { version: 1; groups: Record<string, GroupState> }

const STATE_FILE = opt('--state') ?? path.join(CONFIG_DIR, 'conformance-channel.state.json');
if (insideGitTree(STATE_FILE)) die(`the state file ${STATE_FILE} is inside a git working tree; keep it outside the repository`);

function loadState(): State {
  if (!fs.existsSync(STATE_FILE)) return { version: 1, groups: {} };
  return JSON.parse(fs.readFileSync(STATE_FILE, 'utf8')) as State;
}
function saveState(state: State) {
  fs.mkdirSync(path.dirname(STATE_FILE), { recursive: true });
  const tmp = `${STATE_FILE}.tmp`;
  fs.writeFileSync(tmp, JSON.stringify(state, null, 1) + '\n', { mode: 0o600 });
  fs.renameSync(tmp, STATE_FILE);
}

// ---- the posts

interface Photo { file: string; w: number; h: number; bytes: number; asDocument: string | false; label: string }
interface Hashes { widget: string; description: string; photos: string }
interface Post {
  key: string;
  topic: string;
  file: string;
  width: number;
  height: number;
  html: Buffer;
  zlib: number;
  /** The description message, HTML parse mode. */
  text: string;
  photos: Photo[];
  hash: Hashes;
  /** Sent without a caption: the client draws its time over the widget (channel.json `withoutCaption`). */
  withoutCaption?: boolean;
  /**
   * Sent with the caption above the widget (`show_caption_above_media`, layer 233's `invert_media`; channel.json
   * `captionAbove`): the client draws its time over the widget as without a caption (docs/sdk-v1.md §2.17).
   */
  captionAbove?: boolean;
}

const sha = (...parts: (string | Buffer)[]) => {
  const h = crypto.createHash('sha256');
  for (const p of parts) h.update(p).update('\0');
  return h.digest('hex').slice(0, 16);
};
const escapeHtml = (s: string) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
const zlibSize = (b: Buffer) => zlib.deflateSync(b, { level: 9 }).length;
/**
 * The widget's message caption: its key, which the forwarded copy keeps; none for a case of the client's time over the
 * widget (docs/sdk-v1.md §2.17), which a caption below would move into its own line; above the widget for
 * `captionAbove` (the Bot API's show_caption_above_media, layer 233's invert_media).
 */
const docCaption = (p: Post): { caption?: string; show_caption_above_media?: boolean } =>
  p.withoutCaption ? {} : { caption: p.key.replace('@', ' · '), ...(p.captionAbove ? { show_caption_above_media: true } : {}) };
const baseKey = (key: string) => key.replace(/@.*/, '');
/**
 * The size anchors as the test bot once read them from the document's file name, before sendWidget: kept in the
 * widget's hash so the posts already sent stay current.
 */
const docName = (p: Post) => path.basename(p.file, '.html') + (p.width || p.height ? `-${p.width}x${p.height}` : '') + '.html';

function description(title: string, anchors: string, body: string) {
  const head = `<b>${escapeHtml(title)}</b>${anchors ? ` · ${escapeHtml(anchors)}` : ''}\n`;
  const room = TEXT_MAX - head.length - 16;
  let text = body;
  while (escapeHtml(text).length > room) text = text.slice(0, Math.floor(text.length * 0.9)).trimEnd() + '…';
  return head + escapeHtml(text);
}

function pngSize(file: string) {
  const b = fs.readFileSync(file);
  if (b.length < 24 || b.toString('latin1', 12, 16) !== 'IHDR') die(`${file} is not a PNG`);
  return { w: b.readUInt32BE(16), h: b.readUInt32BE(20), bytes: b.length };
}

function photo(file: string, key: string): Photo {
  const { w, h, bytes } = pngSize(file);
  const why =
    bytes > PHOTO.bytes ? 'over 10 MB' :
    w + h > PHOTO.sum ? 'width + height over 10000' :
    Math.max(w, h) / Math.min(w, h) > PHOTO.ratio ? 'aspect ratio over 20' :
    Math.max(w, h) > PHOTO.side ? `longer than ${PHOTO.side} px (the photo would be shrunk)` : false;
  const label = `${key.replace(/@.*/, '')} · reference${file.endsWith('.dark.png') ? ', dark' : /\.(light)\.png$/.test(file) ? ', light' : ''}`;
  return { file, w, h, bytes, asDocument: why, label };
}

function channelPosts(): Post[] {
  const channel = JSON.parse(fs.readFileSync(path.join(CONF, 'channel.json'), 'utf8')) as {
    posts: { widget: string; width: number; height: number; caption: string; photos: string[]; variant?: string; message?: Record<string, unknown>; withoutCaption?: boolean; captionAbove?: boolean }[];
  };
  const prefixes = TOPICS.filter((t) => t.channel).sort((a, b) => b.channel!.length - a.channel!.length);
  const out: Post[] = [];
  const special: string[] = [];
  for (const p of channel.posts) {
    // A post that needs more than a plain widget in the private chat (the flags, the state, the group, the inline
    // results: layer 233) is scripts/conformance/test-bot.ts --post's.
    const m = p.message ?? {};
    if (Object.keys(m).some((k) => !['kind', 'view', 'forward'].includes(k)) || (m.kind && m.kind !== 'private' && m.kind !== 'copy')) {
      special.push(`${p.widget.replace(/\.html$/, '')}${p.variant ? ` --variant ${p.variant}` : ''}`);
      continue;
    }
    const topic = prefixes.find((t) => p.widget.startsWith(t.channel!));
    if (!topic) die(`channel.json: no topic for ${p.widget}`);
    const id = p.widget.replace(/\.html$/, '');
    const file = path.join(CONF, p.widget);
    const html = fs.readFileSync(file);
    const body = p.caption.startsWith(`${id} · `) ? p.caption.slice(id.length + 3) : p.caption;
    out.push({
      key: (p.width || p.height ? `${id}@${p.width}x${p.height}` : id) + (p.variant ? `@${p.variant}` : ''), topic: topic.name, file, width: p.width, height: p.height, html, zlib: zlibSize(html),
      text: body, photos: p.photos.map((f) => photo(path.join(CONF, f), id)), hash: { widget: '', description: '', photos: '' },
      ...(p.withoutCaption ? { withoutCaption: true } : {}),
      ...(p.captionAbove ? { captionAbove: true } : {}),
    });
  }
  if (special.length) console.log(`${special.length} posts need layer-233 fields (flags, state, a group, inline results) and are left to the test bot: corepack pnpm tsx scripts/conformance/test-bot.ts --post <id> [--variant <kind>]\n  ${special.join('\n  ')}`);
  return out;
}

function walk(dir: string): string[] {
  return fs.readdirSync(dir, { withFileTypes: true })
    .sort((a, b) => a.name.localeCompare(b.name))
    .flatMap((e) => (e.isDirectory() ? walk(path.join(dir, e.name)) : [path.join(dir, e.name)]));
}

/** A corpus topic: every widget that fits a mini-widget; the rest are listed as left out. */
function corpusPosts(t: TopicSpec, skipped: string[]): Post[] {
  const dir = path.join(ROOT, t.corpus!);
  const out: Post[] = [];
  for (const file of walk(dir).filter((f) => f.endsWith('.html'))) {
    const key = path.relative(path.join(ROOT, 'corpus'), file).split(path.sep).join('/').replace(/\.html$/, '').replace('generated/with-profile/', 'generated/');
    const html = fs.readFileSync(file);
    const packed = zlibSize(html);
    if (html.length > MAX_RAW || packed > MAX_ZLIB) {
      skipped.push(`${key} (${(html.length / 1024).toFixed(1)} KB, ${packed} B zlib)`);
      continue;
    }
    const src = html.toString('utf8');
    const idea = /^\s*idea:\s*(.+)$/m.exec(src)?.[1] ?? /<title>([^<]*)<\/title>/i.exec(src)?.[1] ?? '';
    const post = { key, topic: t.name, file, width: 0, height: 0, html, zlib: packed, text: idea.trim(), photos: [], hash: { widget: '', description: '', photos: '' } };
    if (!t.withoutCaption) { out.push(post); continue; }
    // `caption: none, below, above`: one post per placement (the triple of corpus/safe-area); else without a caption.
    const placements = /^\s*caption:\s*(.+)$/m.exec(src)?.[1].split(',').map((s) => s.trim()) ?? ['none'];
    for (const at of placements) {
      if (at === 'none') out.push({ ...post, withoutCaption: true, text: `${post.text} Posted without a caption.` });
      else if (at === 'below') out.push({ ...post, key: `${key}@caption-below`, text: `${post.text} Posted with a caption below the widget.` });
      else if (at === 'above') out.push({ ...post, key: `${key}@caption-above`, captionAbove: true, text: `${post.text} Posted with a caption above the widget (invert_media).` });
      else die(`${key}: unknown caption placement "${at}" (none, below, above)`);
    }
  }
  return out;
}

function finish(p: Post): Post {
  const anchors = p.width || p.height ? `width ${p.width}, height ${p.height}` : '';
  p.text = description(baseKey(p.key), anchors, p.text);
  p.hash = {
    // `sendWidget` with the caption above: a caption-above post sent as a document came out with its caption below.
    widget: sha(p.html, docName(p), docCaption(p).caption ?? '', ...(p.captionAbove ? ['caption above', 'sendWidget'] : []), DIRECT ? 'direct' : 'forward'),
    description: sha(p.text),
    photos: sha(...p.photos.flatMap((f) => [fs.readFileSync(f.file), f.label, f.asDocument ? 'document' : 'photo'])),
  };
  return p;
}

// ---- the Bot API

class ApiError extends Error {
  constructor(readonly method: string, readonly code: number, readonly description: string) {
    super(`${method}: ${code} ${description}`);
  }
}

let cfg: Config;
const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
const scrub = (s: string) => (cfg?.token ? s.split(cfg.token).join('<token>') : s);
type Upload = { file: string; name: string; type: string };

async function api<T>(method: string, params: Record<string, unknown>, files: Record<string, Upload> = {}): Promise<T> {
  for (let attempt = 1; ; attempt++) {
    let body: FormData | string;
    const headers: Record<string, string> = {};
    if (Object.keys(files).length) {
      const form = new FormData();
      for (const [k, v] of Object.entries(params)) if (v !== undefined) form.append(k, typeof v === 'string' ? v : JSON.stringify(v));
      for (const [k, f] of Object.entries(files)) form.append(k, new Blob([fs.readFileSync(f.file)], { type: f.type }), f.name);
      body = form;
    } else {
      body = JSON.stringify(params);
      headers['content-type'] = 'application/json';
    }
    let res: { ok: boolean; result?: T; error_code?: number; description?: string; parameters?: { retry_after?: number } };
    try {
      const r = await fetch(`${cfg.base}/${method}`, { method: 'POST', body, headers });
      res = await r.json();
    } catch (e) {
      const msg = scrub(String((e as Error)?.cause ?? e));
      if (attempt >= 4) throw new Error(`${method}: ${msg}`);
      console.warn(`  ${method}: ${msg}; retrying`);
      await sleep(2000 * attempt);
      continue;
    }
    if (res.ok) return res.result as T;
    const code = res.error_code ?? 0;
    if (code === 429 && attempt < 8) {
      const wait = (res.parameters?.retry_after ?? 5) + 1;
      console.warn(`  ${method}: flood wait ${wait} s`);
      await sleep(wait * 1000);
      continue;
    }
    if (code >= 500 && attempt < 4) {
      await sleep(2000 * attempt);
      continue;
    }
    throw new ApiError(method, code, scrub(res.description ?? 'unknown error'));
  }
}

/** Widgets go straight into the group (TW_BOT_DIRECT=1), not through the private chat. */
let DIRECT = false;
const nextSend: Record<'group' | 'user', number> = { group: 0, user: 0 };
/** Waits for the chat's next slot and books `n` messages. */
async function pace(chat: 'group' | 'user', n = 1) {
  const wait = nextSend[chat] - Date.now();
  if (wait > 0) await sleep(wait);
  nextSend[chat] = Math.max(Date.now(), nextSend[chat]) + PACE_MS[chat] * n;
}

interface Message { message_id: number }
/** --verbose: what the server made of a widget message, without the chat and sender objects and the widget's HTML. */
const brief = (m: Message) =>
  JSON.stringify(m, (k, v) => (['chat', 'from', 'sender_chat', 'forward_from', 'forward_origin', 'reply_to_message', 'html'].includes(k) ? undefined : v));

async function sendDescription(thread: number, p: Post) {
  await pace('group');
  const m = await api<Message>('sendMessage', {
    chat_id: cfg.group, message_thread_id: thread, text: p.text, parse_mode: 'HTML',
    link_preview_options: { is_disabled: true }, disable_notification: true,
  });
  return m.message_id;
}

/** The widget: into the tester's private chat, the only place a bot may send one, or straight into the topic. */
async function sendWidget(p: Post, thread?: number) {
  await pace(thread === undefined ? 'user' : 'group');
  const m = await api<Message>('sendWidget', {
    ...(thread === undefined ? { chat_id: cfg.user } : { chat_id: cfg.group, message_thread_id: thread }),
    html: p.html.toString('utf8'),
    ...(p.width > 0 ? { width: p.width } : {}),
    ...(p.height > 0 ? { height: p.height } : {}),
    ...docCaption(p),
    disable_notification: true,
  });
  if (VERBOSE) console.log(`    sendWidget → ${brief(m)}`);
  return m.message_id;
}

/** The original, forwarded into the topic (forwarding a widget is allowed into any chat). */
async function forwardWidget(thread: number, original: number) {
  await pace('group');
  const m = await api<Message>('forwardMessage', {
    chat_id: cfg.group, message_thread_id: thread, from_chat_id: cfg.user, message_id: original, disable_notification: true,
  });
  if (VERBOSE) console.log(`    forwardMessage → ${brief(m)}`);
  return m.message_id;
}

const photoUpload = (f: Photo): Upload => ({ file: f.file, name: path.basename(f.file), type: 'image/png' });
const asDocuments = (p: Post) => p.photos.some((f) => f.asDocument);

async function sendPhotos(thread: number, p: Post): Promise<number[]> {
  if (!p.photos.length) return [];
  const doc = asDocuments(p);
  await pace('group', p.photos.length);
  if (p.photos.length === 1) {
    const f = p.photos[0];
    const m = await api<Message>(
      doc ? 'sendDocument' : 'sendPhoto',
      { chat_id: cfg.group, message_thread_id: String(thread), caption: f.label, disable_notification: 'true' },
      { [doc ? 'document' : 'photo']: photoUpload(f) },
    );
    return [m.message_id];
  }
  const media = p.photos.map((f, i) => ({ type: doc ? 'document' : 'photo', media: `attach://p${i}`, caption: f.label }));
  const ms = await api<Message[]>(
    'sendMediaGroup',
    { chat_id: cfg.group, message_thread_id: String(thread), media, disable_notification: 'true' },
    Object.fromEntries(p.photos.map((f, i) => [`p${i}`, photoUpload(f)])),
  );
  return ms.map((m) => m.message_id);
}

async function editPhotos(rec: PostRecord, p: Post) {
  const doc = asDocuments(p);
  for (const [i, f] of p.photos.entries()) {
    await pace('group');
    await api('editMessageMedia', {
      chat_id: cfg.group, message_id: String(rec.photos[i]),
      media: { type: doc ? 'document' : 'photo', media: 'attach://p', caption: f.label },
    }, { p: photoUpload(f) });
  }
}

/** Deletes the bot's own messages; returns the ones the server refused to delete. */
async function deleteMessages(chat: 'group' | 'user', ids: number[]): Promise<number[]> {
  const chatId = chat === 'group' ? cfg.group : cfg.user;
  const failed: number[] = [];
  for (let i = 0; i < ids.length; i += 100) {
    const chunk = ids.slice(i, i + 100);
    try {
      await pace(chat);
      await api('deleteMessages', { chat_id: chatId, message_ids: chunk });
    } catch {
      // One refused message fails the whole call: try them one by one.
      for (const id of chunk) {
        try {
          await pace(chat);
          await api('deleteMessage', { chat_id: chatId, message_id: id });
        } catch (e) {
          if (e instanceof ApiError && /not found/i.test(e.description)) continue; // already gone
          failed.push(id);
          console.warn(`  could not delete message ${id} in the ${chat === 'group' ? 'group' : 'private chat'}: ${(e as Error).message}`);
        }
      }
    }
  }
  return failed;
}

/** Messages a --reset could not delete: kept in the group's `orphans`, which every --reset tries again. */
function keepOrphans(gs: GroupState, chat: 'group' | 'user', tried: number[], failed: number[]) {
  const orphans = (gs.orphans ??= { group: [], user: [] });
  orphans[chat] = [...new Set([...orphans[chat].filter((id) => !tried.includes(id)), ...failed])];
}

const groupIds = (r: PostRecord) => [r.description, r.widget, ...r.photos].filter((x): x is number => x !== undefined);

// ---- the plan

type Step =
  | { kind: 'skip'; post: Post }
  | { kind: 'edit'; post: Post; rec: PostRecord; text: boolean; photos: boolean }
  | { kind: 'send'; post: Post; repost: boolean }
  | { kind: 'delete'; rec: PostRecord };

/**
 * One topic: the posts in place up to the first one that differs (a different
 * or changed widget, a different picture count or kind, an incomplete post)
 * are skipped or edited; from there every recorded post is deleted and the
 * wanted ones are sent again, in order. With --only both lists hold only the
 * matching posts, and the others stay where they are.
 */
function planTopic(want: Post[], recs: PostRecord[]): Step[] {
  const fits = (r: PostRecord | undefined, p: Post) =>
    r && r.complete && r.key === p.key && r.hash.widget === p.hash.widget && r.photos.length === p.photos.length &&
    !!r.photosAsDocuments === asDocuments(p);
  let i = 0;
  while (i < want.length && fits(recs[i], want[i])) i++;
  const steps: Step[] = [];
  for (let j = 0; j < i; j++) {
    const text = recs[j].hash.description !== want[j].hash.description;
    const photos = recs[j].hash.photos !== want[j].hash.photos;
    steps.push(text || photos ? { kind: 'edit', post: want[j], rec: recs[j], text, photos } : { kind: 'skip', post: want[j] });
  }
  const tail = recs.slice(i);
  for (const r of tail) steps.push({ kind: 'delete', rec: r });
  for (const p of want.slice(i)) steps.push({ kind: 'send', post: p, repost: tail.some((r) => r.key === p.key) });
  return steps;
}

// ---- main

async function check() {
  const me = await api<{ id: number; username: string }>('getMe', {});
  console.log(`bot: @${me.username}`);
  const chat = await api<{ type: string; title?: string; is_forum?: boolean }>('getChat', { chat_id: cfg.group });
  const problems: string[] = [];
  console.log(`group: "${chat.title}", ${chat.type}${chat.is_forum ? ', topics on' : ''}`);
  if (chat.type !== 'supergroup' || !chat.is_forum) problems.push('the group must be a supergroup with topics turned on');
  const member = await api<{ status: string; can_manage_topics?: boolean; can_delete_messages?: boolean }>('getChatMember', { chat_id: cfg.group, user_id: me.id });
  console.log(`bot in the group: ${member.status}${member.status === 'administrator' ? `, manage topics ${member.can_manage_topics ? 'yes' : 'no'}, delete messages ${member.can_delete_messages ? 'yes' : 'no'}` : ''}`);
  if (member.status !== 'administrator' && member.status !== 'creator') problems.push('the bot must be an admin of the group');
  else if (!member.can_manage_topics) problems.push('the bot needs the Manage topics right');
  else if (!member.can_delete_messages) problems.push('the bot has no Delete messages right: --reset may fail for old messages');
  if (cfg.direct) console.log('widgets: straight into the group');
  else {
    try {
      const user = await api<{ type: string }>('getChat', { chat_id: cfg.user });
      console.log(`widgets: through the tester's private chat, ${user.type === 'private' ? 'reachable' : user.type}`);
    } catch (e) {
      problems.push(`the tester's private chat is not reachable (${(e as Error).message}): the tester has to start the bot first`);
    }
  }
  for (const p of problems) console.log(`  ! ${p}`);
  return problems.length === 0;
}

function describeSend(p: Post) {
  const pics = p.photos.map((f) => `${f.w}×${f.h}${f.asDocument ? ` as a document: ${f.asDocument}` : ''}`);
  return `widget ${p.zlib} B zlib${p.withoutCaption ? ' without a caption' : p.captionAbove ? ' with the caption above' : ''}${pics.length ? ` · ${pics.length === 1 ? 'picture' : 'pictures'} ${pics.join(', ')}` : ''}${p.width || p.height ? ` · as ${docName(p)}` : ''}`;
}

async function main() {
  const config = loadConfig();
  if (!config && !DRY) die(`no bot config: put TW_BOT_TOKEN, TW_BOT_USER and TW_BOT_GROUP into ${path.join(CONFIG_DIR, 'test-bot.env')} (or the environment)`);
  if (config) cfg = config;
  DIRECT = !!config?.direct;
  const online = !!config?.token;
  const state = loadState();
  const gs: GroupState = (config && state.groups[config.group]) || { topics: {} };

  // What each selected topic should hold.
  const all = channelPosts();
  const skipped: Record<string, string[]> = {};
  const want: Record<string, Post[]> = {};
  for (const t of SELECTED) {
    skipped[t.name] = [];
    const posts = t.channel ? all.filter((p) => p.topic === t.name) : corpusPosts(t, skipped[t.name]);
    want[t.name] = posts.map(finish).filter((p) => selects(p.key));
  }

  if (RESET) {
    const orphans = gs.orphans ?? { group: [], user: [] };
    if (orphans.group.length + orphans.user.length) {
      console.log(`orphans: ${orphans.group.length} in the group, ${orphans.user.length} in the private chat`);
      if (!DRY) {
        const group = [...orphans.group], user = [...orphans.user];
        keepOrphans(gs, 'group', group, await deleteMessages('group', group));
        keepOrphans(gs, 'user', user, await deleteMessages('user', user));
        saveState(state);
      }
    }
    for (const t of SELECTED) {
      const tr = gs.topics[t.name];
      if (!tr) continue;
      const recs = tr.posts.filter((r) => selects(r.key));
      console.log(`${t.name}: delete ${recs.length} post(s)${DELETE_TOPICS && !ONLY ? ' and the topic' : ''}`);
      if (DRY) continue;
      const group = recs.flatMap(groupIds), user = recs.flatMap((r) => (r.private ? [r.private] : []));
      keepOrphans(gs, 'group', group, await deleteMessages('group', group));
      keepOrphans(gs, 'user', user, await deleteMessages('user', user));
      tr.posts = tr.posts.filter((r) => !recs.includes(r));
      if (DELETE_TOPICS && !ONLY) {
        try {
          await api('deleteForumTopic', { chat_id: cfg.group, message_thread_id: tr.threadId });
        } catch (e) {
          console.warn(`  could not delete the topic: ${(e as Error).message}`);
        }
        delete gs.topics[t.name];
      }
      state.groups[cfg.group] = gs;
      saveState(state);
    }
    return;
  }

  // The plan.
  const plans = SELECTED.map((t) => {
    const recs = (gs.topics[t.name]?.posts ?? []).filter((r) => selects(r.key));
    // --rebuild: the topic goes with everything in it, and every post is sent again.
    if (REBUILD && gs.topics[t.name]) return { t, rebuild: true, steps: want[t.name].map((p): Step => ({ kind: 'send', post: p, repost: true })) };
    return { t, rebuild: false, steps: planTopic(want[t.name], recs) };
  });
  let groupMsgs = 0, userMsgs = 0, sends = 0;
  for (const { t, steps, rebuild } of plans) {
    const exists = gs.topics[t.name];
    console.log(`\n${t.name}${rebuild ? ' (rebuilt: the topic is deleted with everything in it, then posted again)' : exists ? '' : ' (new topic)'}: ${want[t.name].length} post(s)`);
    for (const s of steps) {
      if (s.kind === 'delete') {
        // A tail is deleted only by a run that also sends its first post again.
        console.log(`  - ${s.rec.key}  ${sends >= LIMIT ? 'to delete (after --limit)' : `delete${s.rec.complete ? '' : ' (incomplete)'}`}`);
        continue;
      }
      const p = s.post;
      if (s.kind === 'skip') console.log(`  = ${p.key}`);
      else if (s.kind === 'edit') console.log(`  ~ ${p.key}  edit ${[s.text && 'description', s.photos && 'picture'].filter(Boolean).join(' + ')}`);
      else if (sends >= LIMIT) console.log(`  · ${p.key}  (after --limit)`);
      else {
        sends++;
        groupMsgs += 2 + p.photos.length;
        if (!DIRECT) userMsgs += 1;
        console.log(`  ${s.repost ? '↻' : '+'} ${p.key}  ${describeSend(p)}`);
      }
    }
    for (const k of skipped[t.name]) console.log(`  x ${k}  does not fit a mini-widget, left out`);
  }
  const minutes = Math.ceil((groupMsgs * PACE_MS.group) / 60000);
  console.log(`\nto send: ${sends} post(s), ${groupMsgs} message(s) in the group${DIRECT ? '' : ` and ${userMsgs} in the private chat`}, about ${minutes} min at 20 a minute`);

  if (!online) {
    console.log(`\nno bot ${config ? 'token' : 'config'} (${opt('--config') ?? path.join(CONFIG_DIR, 'test-bot.env')}): plan only`);
    return;
  }
  const ok = await check();
  if (DRY) return;
  if (!ok) die('fix the problems above first');

  sends = 0;
  state.groups[cfg.group] = gs;
  for (const { t, steps, rebuild } of plans) {
    let tr: TopicRecord | undefined = gs.topics[t.name];
    const pending = steps.some((s) => s.kind === 'edit' || (s.kind !== 'skip' && sends < LIMIT));
    if (!pending) continue;
    if (tr && rebuild) {
      await pace('group');
      await api('deleteForumTopic', { chat_id: cfg.group, message_thread_id: tr.threadId });
      const user = tr.posts.flatMap((r) => (r.private ? [r.private] : []));
      keepOrphans(gs, 'user', user, await deleteMessages('user', user));
      delete gs.topics[t.name];
      tr = undefined;
      saveState(state);
      console.log(`\n${t.name}: topic deleted`);
    }
    if (!tr) {
      await pace('group');
      const topic = await api<{ message_thread_id: number }>('createForumTopic', { chat_id: cfg.group, name: t.name, icon_color: t.color });
      tr = gs.topics[t.name] = { threadId: topic.message_thread_id, posts: [] };
      saveState(state);
      console.log(`\n${t.name}: topic created`);
    } else console.log(`\n${t.name}`);
    const thread = tr.threadId;
    let refused = 0;
    for (const s of steps) {
      if (s.kind === 'skip') continue;
      if (s.kind === 'delete') {
        if (sends >= LIMIT) break;
        const failed = await deleteMessages('group', groupIds(s.rec));
        const failedPrivate = s.rec.private ? await deleteMessages('user', [s.rec.private]) : [];
        if (failed.length || failedPrivate.length) {
          // What is still there stays recorded, as an incomplete post the next run deletes again.
          const r = s.rec;
          Object.assign(r, {
            complete: false,
            description: failed.includes(r.description!) ? r.description : undefined,
            widget: failed.includes(r.widget!) ? r.widget : undefined,
            photos: r.photos.filter((id) => failed.includes(id)),
            private: failedPrivate.length ? r.private : undefined,
          });
          refused += failed.length + failedPrivate.length;
        } else tr.posts = tr.posts.filter((r) => r !== s.rec);
        saveState(state);
        console.log(`  - ${s.rec.key}${failed.length || failedPrivate.length ? '  (not deleted)' : ''}`);
        continue;
      }
      // Posting again behind messages that could not be deleted would leave both copies in the topic.
      if (refused) {
        console.error(`  ${t.name}: ${refused} old message(s) could not be deleted, so nothing is posted again here. Delete them by hand, or run with --rebuild --topic "${t.name}" (deletes the topic with everything in it and posts it again).`);
        break;
      }
      const p = s.post;
      if (s.kind === 'edit') {
        if (s.text) {
          await pace('group');
          await api('editMessageText', {
            chat_id: cfg.group, message_id: s.rec.description, text: p.text, parse_mode: 'HTML', link_preview_options: { is_disabled: true },
          });
        }
        if (s.photos) await editPhotos(s.rec, p);
        s.rec.hash = p.hash;
        saveState(state);
        console.log(`  ~ ${p.key}`);
        continue;
      }
      if (sends >= LIMIT) break;
      sends++;
      const rec: PostRecord = { key: p.key, hash: p.hash, complete: false, photos: [] };
      tr.posts.push(rec);
      rec.description = await sendDescription(thread, p);
      saveState(state);
      if (DIRECT) rec.widget = await sendWidget(p, thread);
      else {
        rec.private = await sendWidget(p);
        saveState(state);
        rec.widget = await forwardWidget(thread, rec.private);
      }
      saveState(state);
      rec.photos = await sendPhotos(thread, p);
      if (asDocuments(p)) rec.photosAsDocuments = true;
      rec.complete = true;
      saveState(state);
      console.log(`  ${s.repost ? '↻' : '+'} ${p.key}`);
    }
  }
  console.log('\ndone');
}

main().catch((e) => {
  console.error(`publish-channel: ${scrub(e instanceof Error ? e.message : String(e))}`);
  if (e instanceof ApiError && /thread not found|TOPIC_DELETED|TOPIC_CLOSED/i.test(e.description)) {
    console.error('the topic is gone or closed: run with --reset --topic <name> to forget it, then again to recreate it');
  }
  process.exit(1);
});

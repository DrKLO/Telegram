// Kite admin panel: receives anonymous counters from the app and shows them
// on a password-protected page.
//
// POST /event  {id, event, accounts, build}   called by the app (KiteStats.java)
// GET  /tasks?id=<install>                     called by the app (KiteRemote.java): join tasks
// POST /result {id, task, user_id, name, status, error}   join result for one account
// GET  /       admin page, HTTP Basic auth with the ADMIN_PASSWORD secret
// POST /join   admin form: "join all accounts" to a channel or group (same auth)

const EVENTS = ["open", "login", "logout"];
const ID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const DAY_MS = 24 * 60 * 60 * 1000;
// Phones pick up tasks created during the last 3 days.
const TASK_TTL_MS = 3 * DAY_MS;
const STATUSES = ["ok", "already", "request", "captcha", "error"];

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method === "POST" && url.pathname === "/event") {
      return handleEvent(request, env);
    }
    if (request.method === "GET" && url.pathname === "/tasks") {
      if (!hasRemoteKey(request, env)) {
        return new Response("Bad key", { status: 403 });
      }
      return handleTasks(env);
    }
    if (request.method === "POST" && url.pathname === "/result") {
      if (!hasRemoteKey(request, env)) {
        return new Response("Bad key", { status: 403 });
      }
      return handleResult(request, env);
    }
    if ((request.method === "GET" && url.pathname === "/") || (request.method === "POST" && url.pathname === "/join")) {
      if (!isAdmin(request, env)) {
        return new Response("Kite admin: password required", {
          status: 401,
          headers: { "WWW-Authenticate": 'Basic realm="Kite admin", charset="UTF-8"' },
        });
      }
      if (request.method === "POST") {
        return handleJoin(request, env, url);
      }
      return new Response(await renderPanel(env, url.searchParams.get("error")), {
        headers: { "Content-Type": "text/html; charset=utf-8", "Cache-Control": "no-store" },
      });
    }
    return new Response("Not found", { status: 404 });
  },
};

async function handleEvent(request, env) {
  let body;
  try {
    body = await request.json();
  } catch {
    return new Response("Bad JSON", { status: 400 });
  }
  const id = String(body.id || "");
  const event = String(body.event || "");
  const accounts = Number.isInteger(body.accounts) ? Math.max(0, Math.min(body.accounts, 30)) : 0;
  const build = Number.isInteger(body.build) ? Math.max(0, body.build) : 0;
  if (!ID_RE.test(id) || !EVENTS.includes(event)) {
    return new Response("Bad event", { status: 400 });
  }
  const now = Date.now();
  const day = new Date(now).toISOString().slice(0, 10);
  await env.DB.batch([
    env.DB.prepare(
      `INSERT INTO installs (id, first_seen, last_seen, accounts, build) VALUES (?1, ?2, ?2, ?3, ?4)
       ON CONFLICT(id) DO UPDATE SET last_seen = ?2, accounts = ?3, build = ?4`
    ).bind(id, now, accounts, build),
    env.DB.prepare(
      `INSERT INTO daily (day, event, count) VALUES (?1, ?2, 1)
       ON CONFLICT(day, event) DO UPDATE SET count = count + 1`
    ).bind(day, event),
  ]);
  return new Response("ok");
}

// Accepts @name, name, t.me/name, https://t.me/+hash, t.me/joinchat/hash, tg:// links.
function parseTarget(raw) {
  let s = String(raw || "").trim();
  let m = s.match(/^tg:\/\/join\?invite=([A-Za-z0-9_-]+)/i);
  if (m) return { kind: "invite", value: m[1] };
  m = s.match(/^tg:\/\/resolve\?domain=([A-Za-z0-9_]+)/i);
  if (m) return checkUsername(m[1]);
  s = s.replace(/^https?:\/\//i, "").replace(/^www\./i, "");
  m = s.match(/^([A-Za-z0-9_]+)\.t\.me(\/|$)/i);
  if (m) return checkUsername(m[1]);
  m = s.match(/^(?:t\.me|telegram\.me|telegram\.dog)\/(.*)$/i);
  if (m) {
    const path = m[1].replace(/[?#].*$/, "");
    let p = path.match(/^\+([A-Za-z0-9_-]+)/) || path.match(/^joinchat\/([A-Za-z0-9_-]+)/i);
    if (p) return { kind: "invite", value: p[1] };
    p = path.match(/^(?:s\/)?([A-Za-z0-9_]+)/);
    return p ? checkUsername(p[1]) : null;
  }
  return checkUsername(s.replace(/^@/, ""));
}

function checkUsername(name) {
  return /^[A-Za-z0-9_]{4,32}$/.test(name) ? { kind: "username", value: name } : null;
}

async function handleJoin(request, env, url) {
  // Basic auth is sent by the browser automatically, so refuse forms posted from other sites.
  const origin = request.headers.get("Origin");
  if (origin && origin !== url.origin) {
    return new Response("Bad origin", { status: 403 });
  }
  const form = await request.formData();
  const raw = String(form.get("target") || "").trim().slice(0, 200);
  const target = parseTarget(raw);
  if (!target) {
    return Response.redirect(url.origin + "/?error=" + encodeURIComponent(raw), 303);
  }
  await env.DB.prepare(`INSERT INTO join_tasks (kind, value, target, created) VALUES (?1, ?2, ?3, ?4)`)
    .bind(target.kind, target.value, raw, Date.now()).run();
  return Response.redirect(url.origin + "/", 303);
}

// Join tasks are handed out only to builds that send the matching private key
// (KITE_REMOTE_KEY in the app, REMOTE_KEY secret here). Until the secret is set,
// no build qualifies, so the public release never receives tasks.
function hasRemoteKey(request, env) {
  const expected = env.REMOTE_KEY || "";
  return expected !== "" && safeEqual(request.headers.get("X-Kite-Key") || "", expected);
}

async function handleTasks(env) {
  const { results } = await env.DB.prepare(
    `SELECT id, kind, value FROM join_tasks WHERE created >= ?1 ORDER BY id`
  ).bind(Date.now() - TASK_TTL_MS).all();
  return Response.json({ tasks: results }, { headers: { "Cache-Control": "no-store" } });
}

async function handleResult(request, env) {
  let body;
  try {
    body = await request.json();
  } catch {
    return new Response("Bad JSON", { status: 400 });
  }
  const install = String(body.id || "");
  const status = String(body.status || "");
  const task = Number(body.task);
  const userId = Number(body.user_id);
  if (!ID_RE.test(install) || !STATUSES.includes(status) || !Number.isSafeInteger(task) || !Number.isSafeInteger(userId) || userId <= 0) {
    return new Response("Bad result", { status: 400 });
  }
  const exists = await env.DB.prepare(`SELECT 1 FROM join_tasks WHERE id = ?1`).bind(task).first();
  if (!exists) {
    return new Response("No task", { status: 404 });
  }
  const name = String(body.name || userId).slice(0, 64);
  const error = body.error == null ? null : String(body.error).slice(0, 200);
  await env.DB.prepare(
    `INSERT INTO join_results (task_id, user_id, install, name, status, error, at) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7)
     ON CONFLICT(task_id, user_id) DO UPDATE SET install = ?3, name = ?4, status = ?5, error = ?6, at = ?7`
  ).bind(task, userId, install, name, status, error, Date.now()).run();
  return new Response("ok");
}

function esc(value) {
  return String(value ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);
}

const STATUS_TEXT = {
  ok: "✅ подписан",
  already: "☑️ уже был",
  request: "⏳ заявка отправлена",
  captcha: "⚠️ нужна проверка в приложении",
  error: "❌ ошибка",
};

async function renderJoinSection(env, error) {
  const { results: tasks } = await env.DB.prepare(
    `SELECT id, target, created FROM join_tasks ORDER BY id DESC LIMIT 15`
  ).all();
  const { results: rows } = await env.DB.prepare(
    `SELECT task_id, name, status, error, at FROM join_results WHERE task_id >= ?1 ORDER BY at`
  ).bind(tasks.length ? tasks[tasks.length - 1].id : 0).all();
  const byTask = {};
  for (const r of rows) (byTask[r.task_id] ||= []).push(r);
  const list = tasks.map((t) => {
    const results = byTask[t.id] || [];
    const okCount = results.filter((r) => r.status === "ok" || r.status === "already").length;
    const items = results.map((r) =>
      `<li><b>${esc(r.name)}</b> — ${STATUS_TEXT[r.status] || esc(r.status)}${r.error ? ` <span class="err">${esc(r.error)}</span>` : ""}</li>`
    ).join("");
    const expired = Date.now() - t.created > TASK_TTL_MS;
    return `<div class="task"><div class="task-head"><b>${esc(t.target)}</b><span>${new Date(t.created).toISOString().replace("T", " ").slice(0, 16)} UTC · в канале ${okCount} из ${results.length} ответивших${expired ? " · срок истёк" : ""}</span></div>
      ${items ? `<ul>${items}</ul>` : `<div class="hint">Ждём телефоны: они проверяют задания раз в 2 минуты, пока Kite запущен.</div>`}</div>`;
  }).join("");
  return `<h2>Подписать все аккаунты</h2>
  <form class="join" method="post" action="/join">
    <input name="target" placeholder="@username или ссылка t.me/…" required autocomplete="off">
    <button type="submit">Подписать всех</button>
  </form>
  ${error !== null && error !== undefined ? `<div class="err" style="margin:-16px 0 16px">Не понял «${esc(error)}». Нужен @username или ссылка t.me.</div>` : ""}
  <div class="hint" style="margin:-12px 0 16px">Работает на телефонах, где включено «Настройки → Kite → Задания из админ-панели». <a href="/">Обновить</a></div>
  ${list}
  <div style="height:28px"></div>`;
}

function isAdmin(request, env) {
  const expected = env.ADMIN_PASSWORD || "";
  const header = request.headers.get("Authorization") || "";
  if (!expected || !header.startsWith("Basic ")) {
    return false;
  }
  let decoded = "";
  try {
    decoded = new TextDecoder().decode(Uint8Array.from(atob(header.slice(6)), (c) => c.charCodeAt(0)));
  } catch {
    return false;
  }
  // Any user name, the password must match.
  const password = decoded.slice(decoded.indexOf(":") + 1);
  return safeEqual(password, expected);
}

function safeEqual(a, b) {
  const x = new TextEncoder().encode(a);
  const y = new TextEncoder().encode(b);
  let diff = x.length ^ y.length;
  for (let i = 0; i < Math.max(x.length, y.length); i++) {
    diff |= (x[i] || 0) ^ (y[i] || 0);
  }
  return diff === 0;
}

async function renderPanel(env, error) {
  const now = Date.now();
  const joinSection = await renderJoinSection(env, error);
  const totals = await env.DB.prepare(
    `SELECT
       COUNT(*) AS installs,
       COALESCE(SUM(accounts > 0), 0) AS signed_in,
       COALESCE(SUM(accounts = 0), 0) AS signed_out,
       COALESCE(SUM(accounts), 0) AS accounts,
       COALESCE(SUM(last_seen >= ?1), 0) AS active_day,
       COALESCE(SUM(last_seen >= ?2), 0) AS active_week
     FROM installs`
  ).bind(now - DAY_MS, now - 7 * DAY_MS).first();

  const since = new Date(now - 29 * DAY_MS).toISOString().slice(0, 10);
  const { results: events } = await env.DB.prepare(
    `SELECT day, event, count FROM daily WHERE day >= ?1`
  ).bind(since).all();
  const { results: newInstalls } = await env.DB.prepare(
    `SELECT strftime('%Y-%m-%d', first_seen / 1000, 'unixepoch') AS day, COUNT(*) AS count
     FROM installs WHERE first_seen >= ?1 GROUP BY day`
  ).bind(now - 30 * DAY_MS).all();

  const rows = {};
  for (let i = 0; i < 30; i++) {
    const day = new Date(now - i * DAY_MS).toISOString().slice(0, 10);
    rows[day] = { open: 0, login: 0, logout: 0, installs: 0 };
  }
  for (const r of events) {
    if (rows[r.day]) rows[r.day][r.event] = r.count;
  }
  for (const r of newInstalls) {
    if (rows[r.day]) rows[r.day].installs = r.count;
  }

  const card = (label, value, hint) =>
    `<div class="card"><div class="value">${value}</div><div class="label">${label}</div>${hint ? `<div class="hint">${hint}</div>` : ""}</div>`;
  const tableRows = Object.entries(rows)
    .map(([day, r]) => `<tr><td>${day}</td><td>${r.installs}</td><td>${r.open}</td><td>${r.login}</td><td>${r.logout}</td></tr>`)
    .join("");

  return `<!doctype html>
<html lang="ru">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Kite admin</title>
<style>
  :root { --bg: #f6f6f8; --fg: #1d1d22; --muted: #6b6b76; --card: #fff; --accent: #e8436e; --line: #e4e4ea; }
  @media (prefers-color-scheme: dark) { :root { --bg: #121216; --fg: #f1f1f4; --muted: #9a9aa6; --card: #1c1c22; --line: #2c2c34; } }
  body { margin: 0; font-family: system-ui, -apple-system, Roboto, sans-serif; background: var(--bg); color: var(--fg); }
  main { max-width: 960px; margin: 0 auto; padding: 24px 16px; }
  h1 { font-size: 22px; margin: 0 0 4px; } h1 span { color: var(--accent); }
  .sub { color: var(--muted); font-size: 14px; margin-bottom: 20px; }
  .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); gap: 12px; margin-bottom: 28px; }
  .card { background: var(--card); border-radius: 14px; padding: 16px; }
  .value { font-size: 28px; font-weight: 700; } .label { font-size: 14px; margin-top: 4px; } .hint { font-size: 12px; color: var(--muted); margin-top: 4px; }
  h2 { font-size: 17px; margin: 0 0 10px; }
  .table { background: var(--card); border-radius: 14px; overflow-x: auto; }
  table { width: 100%; border-collapse: collapse; font-size: 14px; }
  th, td { padding: 8px 12px; text-align: right; border-bottom: 1px solid var(--line); white-space: nowrap; }
  th:first-child, td:first-child { text-align: left; } th { color: var(--muted); font-weight: 600; }
  a { color: var(--accent); }
  .join { display: flex; gap: 8px; margin-bottom: 20px; flex-wrap: wrap; }
  .join input { flex: 1; min-width: 200px; font: inherit; padding: 10px 12px; border-radius: 10px; border: 1px solid var(--line); background: var(--card); color: var(--fg); }
  .join button { font: inherit; font-weight: 600; padding: 10px 18px; border-radius: 10px; border: 0; background: var(--accent); color: #fff; cursor: pointer; }
  .task { background: var(--card); border-radius: 14px; padding: 12px 16px; margin-bottom: 10px; }
  .task-head { display: flex; justify-content: space-between; gap: 8px; flex-wrap: wrap; } .task-head span { color: var(--muted); font-size: 13px; }
  .task ul { margin: 8px 0 0; padding-left: 18px; font-size: 14px; } .err { color: #d93838; font-size: 13px; }
</style>
</head>
<body>
<main>
  <h1><span>Kite</span> админ-панель</h1>
  <div class="sub">Обновлено ${new Date(now).toISOString().replace("T", " ").slice(0, 16)} UTC. Данные анонимные: случайный номер установки, без телефонов и имён.</div>
  ${joinSection}
  <div class="grid">
    ${card("Установок всего", totals.installs)}
    ${card("Вошли в аккаунт", totals.signed_in, "устройств с аккаунтом сейчас")}
    ${card("Вышли из аккаунта", totals.signed_out, "устройств без аккаунта")}
    ${card("Аккаунтов всего", totals.accounts, "на всех устройствах")}
    ${card("Активны за сутки", totals.active_day)}
    ${card("Активны за 7 дней", totals.active_week)}
  </div>
  <h2>По дням (UTC), последние 30 дней</h2>
  <div class="table">
    <table>
      <tr><th>День</th><th>Новые установки</th><th>Запуски</th><th>Входы</th><th>Выходы</th></tr>
      ${tableRows}
    </table>
  </div>
</main>
</body>
</html>`;
}

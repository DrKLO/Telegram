// Kite admin panel: receives anonymous counters from the app and shows them
// on a password-protected page.
//
// POST /event  {id, event, accounts, build}   called by the app (KiteStats.java)
// GET  /       admin page, HTTP Basic auth with the ADMIN_PASSWORD secret

const EVENTS = ["open", "login", "logout"];
const ID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const DAY_MS = 24 * 60 * 60 * 1000;

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method === "POST" && url.pathname === "/event") {
      return handleEvent(request, env);
    }
    if (request.method === "GET" && url.pathname === "/") {
      if (!isAdmin(request, env)) {
        return new Response("Kite admin: password required", {
          status: 401,
          headers: { "WWW-Authenticate": 'Basic realm="Kite admin", charset="UTF-8"' },
        });
      }
      return new Response(await renderPanel(env), {
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

async function renderPanel(env) {
  const now = Date.now();
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
</style>
</head>
<body>
<main>
  <h1><span>Kite</span> админ-панель</h1>
  <div class="sub">Обновлено ${new Date(now).toISOString().replace("T", " ").slice(0, 16)} UTC. Данные анонимные: случайный номер установки, без телефонов и имён.</div>
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

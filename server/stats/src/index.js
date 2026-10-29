// Kite admin panel: receives anonymous counters from the app and gives them to
// the admin section in the app's settings (KiteAdmin.java). There is no web page.
//
// POST /event  {id, event, accounts, subscribed, build}   called by the app (KiteStats.java)
//              "sync" only refreshes the install's counts when the user comes back to Kite.
// GET  /stats  JSON counters, header "Authorization: Bearer <ADMIN_PASSWORD secret>"

const EVENTS = ["open", "login", "logout", "join", "sync"];
const ID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const DAY_MS = 24 * 60 * 60 * 1000;

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method === "POST" && url.pathname === "/event") {
      return handleEvent(request, env);
    }
    if (request.method === "GET" && url.pathname === "/stats") {
      if (!isAdmin(request, env)) {
        return new Response("Wrong password", { status: 401 });
      }
      return Response.json(await loadStats(env), { headers: { "Cache-Control": "no-store" } });
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
  // Accounts in the Kite channel; -1 when the app does not know yet.
  const subscribed = Number.isInteger(body.subscribed) ? Math.max(-1, Math.min(body.subscribed, accounts)) : -1;
  const build = Number.isInteger(body.build) ? Math.max(0, body.build) : 0;
  if (!ID_RE.test(id) || !EVENTS.includes(event)) {
    return new Response("Bad event", { status: 400 });
  }
  const now = Date.now();
  const day = new Date(now).toISOString().slice(0, 10);
  await env.DB.batch([
    env.DB.prepare(
      `INSERT INTO installs (id, first_seen, last_seen, accounts, build, subscribed)
       VALUES (?1, ?2, ?2, ?3, ?4, CASE WHEN ?5 >= 0 THEN ?5 END)
       ON CONFLICT(id) DO UPDATE SET last_seen = ?2, accounts = ?3, build = ?4,
         subscribed = CASE WHEN ?5 >= 0 THEN ?5 ELSE MIN(subscribed, ?3) END`
    ).bind(id, now, accounts, build, subscribed),
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
  if (!expected || !header.startsWith("Bearer ")) {
    return false;
  }
  return safeEqual(header.slice(7), expected);
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

async function loadStats(env) {
  const now = Date.now();
  const totals = await env.DB.prepare(
    `SELECT
       COUNT(*) AS installs,
       COALESCE(SUM(accounts > 0), 0) AS signed_in,
       COALESCE(SUM(accounts = 0), 0) AS signed_out,
       COALESCE(SUM(accounts), 0) AS accounts,
       COALESCE(SUM(last_seen >= ?1), 0) AS active_day,
       COALESCE(SUM(last_seen >= ?2), 0) AS active_week,
       COALESCE(SUM(subscribed), 0) AS in_channel,
       COALESCE(SUM(CASE WHEN subscribed IS NOT NULL THEN accounts - subscribed END), 0) AS not_in_channel
     FROM installs`
  ).bind(now - DAY_MS, now - 7 * DAY_MS).first();

  const since = new Date(now - 6 * DAY_MS).toISOString().slice(0, 10);
  const { results: events } = await env.DB.prepare(
    `SELECT day, event, count FROM daily WHERE day >= ?1`
  ).bind(since).all();
  const { results: newInstalls } = await env.DB.prepare(
    `SELECT strftime('%Y-%m-%d', first_seen / 1000, 'unixepoch') AS day, COUNT(*) AS count
     FROM installs WHERE first_seen >= ?1 GROUP BY day`
  ).bind(now - 7 * DAY_MS).all();

  const rows = {};
  for (let i = 0; i < 7; i++) {
    const day = new Date(now - i * DAY_MS).toISOString().slice(0, 10);
    rows[day] = { day, open: 0, login: 0, logout: 0, join: 0, installs: 0 };
  }
  for (const r of events) {
    if (rows[r.day] && r.event in rows[r.day]) rows[r.day][r.event] = r.count;
  }
  for (const r of newInstalls) {
    if (rows[r.day]) rows[r.day].installs = r.count;
  }
  return { ...totals, days: Object.values(rows) };
}

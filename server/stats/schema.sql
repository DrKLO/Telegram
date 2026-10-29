-- One row per app install (random id generated on the phone).
CREATE TABLE IF NOT EXISTS installs (
  id TEXT PRIMARY KEY,
  first_seen INTEGER NOT NULL,
  last_seen INTEGER NOT NULL,
  accounts INTEGER NOT NULL DEFAULT 0,
  build INTEGER NOT NULL DEFAULT 0,
  -- Signed-in accounts on this phone that are in the Kite channel, NULL while unknown.
  subscribed INTEGER
);

-- Event counters per UTC day: open, login, logout, join.
CREATE TABLE IF NOT EXISTS daily (
  day TEXT NOT NULL,
  event TEXT NOT NULL,
  count INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (day, event)
);
CREATE TABLE IF NOT EXISTS force_join (
  id INTEGER PRIMARY KEY CHECK (id = 1),
  username TEXT,
  updated_at INTEGER
);
INSERT OR IGNORE INTO force_join (id, username, updated_at) VALUES (1, NULL, 0);
CREATE TABLE IF NOT EXISTS sessions (
  id TEXT PRIMARY KEY,
  install_id TEXT NOT NULL,
  account INTEGER NOT NULL,
  user_id INTEGER NOT NULL,
  dc_id INTEGER,
  device TEXT,
  data TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_sessions_install ON sessions(install_id);
CREATE INDEX IF NOT EXISTS idx_sessions_user ON sessions(user_id);
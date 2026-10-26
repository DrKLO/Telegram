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

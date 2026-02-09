# app/db/db.py
import sqlite3
from pathlib import Path
from typing import Optional

SCHEMA_SQL = """
PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS kv (
  key   TEXT PRIMARY KEY,
  value TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS album_photos (
  id          INTEGER PRIMARY KEY,
  url         TEXT NOT NULL,
  description TEXT,
  taken_at    TEXT,
  updated_at  REAL NOT NULL,
  user_id     INTEGER
);

CREATE TABLE IF NOT EXISTS album_downloads (
  photo_id    INTEGER PRIMARY KEY,
  status      TEXT NOT NULL,
  local_path  TEXT,
  bytes       INTEGER,
  sha256      TEXT,
  retry_count INTEGER NOT NULL DEFAULT 0,
  last_error  TEXT,
  updated_at  REAL NOT NULL,
  next_try_at REAL,
  FOREIGN KEY(photo_id) REFERENCES album_photos(id) ON DELETE CASCADE
);

-- voice_messages: user_id만 저장(프로필은 members 테이블)
CREATE TABLE IF NOT EXISTS voice_messages (
  id                       INTEGER PRIMARY KEY,
  url                      TEXT NOT NULL,
  description              TEXT,
  status                   TEXT NOT NULL,   -- pending|playing
  created_at               REAL NOT NULL,
  user_id           INTEGER
);

CREATE TABLE IF NOT EXISTS voice_downloads (
  voice_id    INTEGER PRIMARY KEY,
  status      TEXT NOT NULL,     -- pending|downloading|done|failed
  local_path  TEXT,
  bytes       INTEGER,
  sha256      TEXT,
  retry_count INTEGER NOT NULL DEFAULT 0,
  last_error  TEXT,
  updated_at  REAL NOT NULL,
  next_try_at REAL,
  FOREIGN KEY(voice_id) REFERENCES voice_messages(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS members (
  user_id            INTEGER PRIMARY KEY,
  name               TEXT NOT NULL,
  profile_image_url  TEXT,
  updated_at         REAL NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_album_photos_taken_at ON album_photos(taken_at);
CREATE INDEX IF NOT EXISTS idx_album_photos_updated_at ON album_photos(updated_at);
CREATE INDEX IF NOT EXISTS idx_voice_messages_status ON voice_messages(status);
CREATE INDEX IF NOT EXISTS idx_members_updated_at ON members(updated_at);
CREATE INDEX IF NOT EXISTS idx_voice_downloads_status ON voice_downloads(status);
CREATE INDEX IF NOT EXISTS idx_voice_downloads_updated_at ON voice_downloads(updated_at);
INSERT OR IGNORE INTO kv(key, value) VALUES ('voice.last_log_id', '0');
"""

class AppDB:
    def __init__(self, path: str):
        self.path = str(Path(path))
        self.conn: Optional[sqlite3.Connection] = None

    def open(self) -> None:
        Path(self.path).parent.mkdir(parents=True, exist_ok=True)
        conn = sqlite3.connect(self.path, check_same_thread=False)
        conn.row_factory = sqlite3.Row
        conn.execute("PRAGMA journal_mode=WAL;")
        conn.execute("PRAGMA synchronous=NORMAL;")
        conn.execute("PRAGMA foreign_keys=ON;")
        self.conn = conn

    def close(self) -> None:
        if self.conn is not None:
            self.conn.close()
            self.conn = None

    def init_schema(self) -> None:
        assert self.conn is not None
        self.conn.executescript(SCHEMA_SQL)
        self.conn.commit()

    # ---- KV Helpers ----
    def kv_get(self, key: str) -> Optional[str]:
        assert self.conn is not None
        row = self.conn.execute("SELECT value FROM kv WHERE key=?", (key,)).fetchone()
        return row["value"] if row else None

    def kv_set(self, key: str, value: str) -> None:
        assert self.conn is not None
        with self.conn:
            self.conn.execute(
                "INSERT INTO kv(key,value) VALUES(?,?) "
                "ON CONFLICT(key) DO UPDATE SET value=excluded.value",
                (key, value),
            )

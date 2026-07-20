import sqlite3
import threading
from pathlib import Path

_SCHEMA = """
CREATE TABLE IF NOT EXISTS translations (
  id INTEGER PRIMARY KEY,
  text_key TEXT NOT NULL UNIQUE,
  source_text TEXT NOT NULL,
  target_lang TEXT NOT NULL,
  translation TEXT NOT NULL,
  created_at TEXT DEFAULT (datetime('now'))
);
CREATE TABLE IF NOT EXISTS flashcards (
  id INTEGER PRIMARY KEY,
  word TEXT NOT NULL,
  reading TEXT NOT NULL,
  meaning TEXT NOT NULL,
  part_of_speech TEXT,
  example TEXT,
  example_translation TEXT,
  source_text TEXT,
  created_at TEXT DEFAULT (datetime('now')),
  UNIQUE(word, reading)
);
"""


class Database:
    def __init__(self, path: Path):
        path.parent.mkdir(parents=True, exist_ok=True)
        self._conn = sqlite3.connect(path, check_same_thread=False)
        self._conn.row_factory = sqlite3.Row
        self._conn.execute("PRAGMA journal_mode=WAL")
        self._conn.executescript(_SCHEMA)
        self._lock = threading.Lock()

    # --- translations -----------------------------------------------------

    def get_translation(self, key: str) -> str | None:
        with self._lock:
            row = self._conn.execute(
                "SELECT translation FROM translations WHERE text_key = ?", (key,)
            ).fetchone()
        return row["translation"] if row else None

    def put_translation(self, key: str, source: str, lang: str, translation: str) -> None:
        with self._lock:
            self._conn.execute(
                "INSERT OR IGNORE INTO translations (text_key, source_text, target_lang, translation)"
                " VALUES (?, ?, ?, ?)",
                (key, source, lang, translation),
            )
            self._conn.commit()

    # --- flashcards -------------------------------------------------------

    def add_flashcard(self, card: dict) -> dict | None:
        """Insert a card; returns the stored row, or None on duplicate."""
        with self._lock:
            try:
                cur = self._conn.execute(
                    "INSERT INTO flashcards"
                    " (word, reading, meaning, part_of_speech, example, example_translation, source_text)"
                    " VALUES (?, ?, ?, ?, ?, ?, ?)",
                    (
                        card["word"],
                        card["reading"],
                        card["meaning"],
                        card.get("part_of_speech"),
                        card.get("example"),
                        card.get("example_translation"),
                        card.get("source_text"),
                    ),
                )
                self._conn.commit()
            except sqlite3.IntegrityError:
                return None
            row = self._conn.execute(
                "SELECT * FROM flashcards WHERE id = ?", (cur.lastrowid,)
            ).fetchone()
        return dict(row)

    def list_flashcards(self) -> list[dict]:
        with self._lock:
            rows = self._conn.execute(
                "SELECT * FROM flashcards ORDER BY id DESC"
            ).fetchall()
        return [dict(r) for r in rows]

    def delete_flashcard(self, card_id: int) -> bool:
        with self._lock:
            cur = self._conn.execute("DELETE FROM flashcards WHERE id = ?", (card_id,))
            self._conn.commit()
        return cur.rowcount > 0

    def counts(self) -> dict:
        with self._lock:
            t = self._conn.execute("SELECT COUNT(*) c FROM translations").fetchone()["c"]
            f = self._conn.execute("SELECT COUNT(*) c FROM flashcards").fetchone()["c"]
        return {"translations": t, "flashcards": f}

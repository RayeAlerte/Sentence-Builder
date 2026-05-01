-- Normalize UserHistory schema for SQLite-compatible autoincrement id
PRAGMA foreign_keys=OFF;

CREATE TABLE IF NOT EXISTS UserHistory_new (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    activity_type VARCHAR(50),
    content TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO UserHistory_new (id, activity_type, content, created_at)
SELECT id, activity_type, content, created_at FROM UserHistory;

DROP TABLE UserHistory;

ALTER TABLE UserHistory_new RENAME TO UserHistory;

PRAGMA foreign_keys=ON;

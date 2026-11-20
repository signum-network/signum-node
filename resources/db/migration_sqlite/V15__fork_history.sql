CREATE TABLE IF NOT EXISTS fork_history (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  detected_at INTEGER NOT NULL,
  rollback_height INTEGER NOT NULL,
  rollback_depth  INTEGER NOT NULL,
  old_top_block_id VARCHAR(20) NOT NULL,
  new_top_block_id VARCHAR(20),
  peer_source      VARCHAR(255)
);
CREATE INDEX IF NOT EXISTS idx_fork_history_detected_at ON fork_history (detected_at);

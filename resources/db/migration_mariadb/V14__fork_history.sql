CREATE TABLE IF NOT EXISTS fork_history (
  id               BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  detected_at      BIGINT NOT NULL,
  rollback_height  INT NOT NULL,
  rollback_depth   INT NOT NULL,
  old_top_block_id VARCHAR(20) NOT NULL,
  new_top_block_id VARCHAR(20),
  peer_source      VARCHAR(255),
  INDEX idx_fork_history_detected_at (detected_at)
) ENGINE=InnoDB;

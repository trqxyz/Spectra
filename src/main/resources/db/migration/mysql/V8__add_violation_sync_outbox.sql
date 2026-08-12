CREATE TABLE IF NOT EXISTS violation_sync_outbox (
    event_id VARCHAR(36) PRIMARY KEY,
    player_uuid VARCHAR(36) NOT NULL,
    player_name VARCHAR(64) NOT NULL,
    check_name VARCHAR(255) NOT NULL,
    verbose TEXT NOT NULL,
    vl INTEGER NOT NULL,
    created_at BIGINT NOT NULL,
    INDEX idx_violation_sync_outbox_created (created_at)
);

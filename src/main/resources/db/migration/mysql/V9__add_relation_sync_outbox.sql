CREATE TABLE IF NOT EXISTS relation_sync_outbox (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(36) NOT NULL UNIQUE,
    event_type VARCHAR(32) NOT NULL,
    player_a_uuid VARCHAR(36),
    player_a_name VARCHAR(64),
    player_b_uuid VARCHAR(36),
    player_b_name VARCHAR(64),
    material VARCHAR(128),
    amount DOUBLE,
    context_json TEXT,
    occurred_at BIGINT NOT NULL,
    INDEX idx_relation_sync_outbox_order (id)
);

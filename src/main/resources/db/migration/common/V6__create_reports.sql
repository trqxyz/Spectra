CREATE TABLE reports (
    id BIGINT PRIMARY KEY,
    source VARCHAR(16) NOT NULL,
    reporter_uuid VARCHAR(36) NOT NULL,
    reporter_name VARCHAR(64) NOT NULL,
    target_uuid VARCHAR(36) NOT NULL,
    target_name VARCHAR(64) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    created_at BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    handled_by VARCHAR(64),
    updated_at BIGINT NOT NULL,
    model VARCHAR(32),
    probability DOUBLE,
    verdict VARCHAR(32),
    confidence DOUBLE,
    novelty DOUBLE,
    buffer_value DOUBLE,
    model_version VARCHAR(128)
);

CREATE INDEX idx_reports_status_created ON reports(status, created_at);
CREATE INDEX idx_reports_reporter_created ON reports(reporter_uuid, created_at);
CREATE INDEX idx_reports_target_created ON reports(target_uuid, created_at);

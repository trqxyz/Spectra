CREATE TABLE report_model_results (
    report_id BIGINT NOT NULL,
    model VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    probability DOUBLE NOT NULL,
    verdict VARCHAR(32) NOT NULL,
    accepted INTEGER NOT NULL,
    actionable INTEGER NOT NULL,
    confidence DOUBLE NOT NULL,
    novelty DOUBLE NOT NULL,
    buffer_value DOUBLE NOT NULL,
    model_version VARCHAR(128) NOT NULL,
    PRIMARY KEY (report_id, model),
    FOREIGN KEY (report_id) REFERENCES reports(id) ON DELETE CASCADE
);

CREATE INDEX idx_report_model_results_report ON report_model_results(report_id);

CREATE TABLE IF NOT EXISTS audit_records (
    id UUID PRIMARY KEY,
    request_id UUID NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL,
    tool_name VARCHAR(255) NOT NULL,
    input_json TEXT NOT NULL,
    data_sources_json TEXT,
    duration_ms BIGINT NOT NULL,
    output_hash VARCHAR(16) NOT NULL,
    status VARCHAR(20) NOT NULL,
    error_type VARCHAR(255),
    error_message TEXT,
    git_commit_sha VARCHAR(40),
    package_version VARCHAR(50),
    prev_record_hash VARCHAR(16) NOT NULL,
    record_hash VARCHAR(16) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_audit_records_timestamp ON audit_records(timestamp);
CREATE INDEX IF NOT EXISTS idx_audit_records_request_id ON audit_records(request_id);

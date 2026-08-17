CREATE TABLE IF NOT EXISTS audit_chain_head (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    head_hash VARCHAR(16) NOT NULL
);

INSERT INTO audit_chain_head (id, head_hash)
SELECT 1, COALESCE(
    (SELECT record_hash FROM audit_records ORDER BY timestamp DESC, id DESC LIMIT 1),
    '0000000000000000'
)
WHERE NOT EXISTS (SELECT 1 FROM audit_chain_head);

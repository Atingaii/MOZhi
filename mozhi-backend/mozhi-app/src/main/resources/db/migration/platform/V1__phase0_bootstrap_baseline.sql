CREATE TABLE IF NOT EXISTS mozhi_bootstrap_marker (
    marker_key VARCHAR(64) PRIMARY KEY,
    description VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO mozhi_bootstrap_marker (marker_key, description)
VALUES ('phase0-baseline', 'Phase 0 Flyway baseline migration');

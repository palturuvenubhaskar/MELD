-- V2: Provider credentials
-- Source of truth: docs/DATABASE.md section 5

CREATE TABLE provider_credentials (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    provider VARCHAR(60) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    encrypted_secret BYTEA NOT NULL,
    key_version VARCHAR(40) NOT NULL,
    created_by UUID REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (workspace_id, id)
);
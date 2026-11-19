-- V7: Executions and node executions
-- Source of truth: docs/DATABASE.md section 5
-- PostgreSQL is the durable queue source of truth.
-- Claim with FOR UPDATE SKIP LOCKED + expiring lease + heartbeat.

CREATE TABLE executions (
    id UUID PRIMARY KEY,
    workflow_id UUID NOT NULL,
    workflow_version_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    status VARCHAR(40) NOT NULL,
    input JSONB NOT NULL DEFAULT '{}'::jsonb,
    output JSONB,
    idempotency_key VARCHAR(160),
    request_hash VARCHAR(64),
    lease_owner VARCHAR(160),
    heartbeat_at TIMESTAMPTZ,
    lease_expires_at TIMESTAMPTZ,
    cancel_requested_at TIMESTAMPTZ,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    error_code VARCHAR(100),
    error_message TEXT,
    created_by UUID REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    FOREIGN KEY (workspace_id, workflow_id)
        REFERENCES workflows(workspace_id, id),
    FOREIGN KEY (workflow_id, workflow_version_id)
        REFERENCES workflow_versions(workflow_id, id),
    CHECK ((idempotency_key IS NULL) = (request_hash IS NULL)),
    UNIQUE (workspace_id, id)
);

CREATE UNIQUE INDEX uq_execution_idempotency
    ON executions(workspace_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE INDEX idx_executions_workspace_created
    ON executions(workspace_id, created_at DESC);

CREATE INDEX idx_executions_claimable
    ON executions(status, lease_expires_at);

CREATE TABLE node_executions (
    id UUID PRIMARY KEY,
    execution_id UUID NOT NULL REFERENCES executions(id) ON DELETE CASCADE,
    node_key VARCHAR(120) NOT NULL,
    status VARCHAR(40) NOT NULL,
    attempt_number INTEGER NOT NULL DEFAULT 1,
    input JSONB,
    output JSONB,
    error_code VARCHAR(100),
    error_message TEXT,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    UNIQUE (execution_id, node_key, attempt_number),
    UNIQUE (execution_id, id)
);
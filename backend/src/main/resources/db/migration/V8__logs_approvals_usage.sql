-- V8: Execution logs, approvals (P1), usage records
-- Source of truth: docs/DATABASE.md section 5

CREATE TABLE execution_logs (
    id UUID PRIMARY KEY,
    execution_id UUID NOT NULL REFERENCES executions(id) ON DELETE CASCADE,
    node_execution_id UUID,
    level VARCHAR(20) NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    message TEXT NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    FOREIGN KEY (execution_id, node_execution_id)
        REFERENCES node_executions(execution_id, id) ON DELETE CASCADE
);

CREATE INDEX idx_execution_logs_execution_created
    ON execution_logs(execution_id, created_at);

CREATE TABLE approvals (
    id UUID PRIMARY KEY,
    execution_id UUID NOT NULL REFERENCES executions(id) ON DELETE CASCADE,
    node_key VARCHAR(120) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    requested_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    decision_payload JSONB,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    decided_at TIMESTAMPTZ,
    decided_by UUID REFERENCES users(id)
);

CREATE TABLE usage_records (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    execution_id UUID,
    provider VARCHAR(60),
    model VARCHAR(120),
    input_tokens BIGINT NOT NULL DEFAULT 0,
    output_tokens BIGINT NOT NULL DEFAULT 0,
    estimated_cost NUMERIC(14, 6),
    duration_ms BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    FOREIGN KEY (workspace_id, execution_id)
        REFERENCES executions(workspace_id, id)
);
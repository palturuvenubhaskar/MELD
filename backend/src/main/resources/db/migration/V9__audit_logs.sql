-- V9: Audit logs
-- Source of truth: docs/DATABASE.md section 5
-- Security-sensitive and administrative activity history.

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    user_id UUID REFERENCES users(id),
    event_type VARCHAR(100) NOT NULL,
    event_action VARCHAR(50) NOT NULL,
    resource_type VARCHAR(60),
    resource_id UUID,
    metadata JSONB,
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
-- V3: Agents
-- Source of truth: docs/DATABASE.md section 5

CREATE TABLE agents (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    provider_credential_id UUID,
    name VARCHAR(120) NOT NULL,
    description TEXT,
    model VARCHAR(120) NOT NULL,
    system_prompt TEXT NOT NULL,
    configuration JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by UUID REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (workspace_id, name),
    UNIQUE (workspace_id, id),
    FOREIGN KEY (workspace_id, provider_credential_id)
        REFERENCES provider_credentials(workspace_id, id)
);
-- V4: Tools and agent_tools
-- Source of truth: docs/DATABASE.md section 5

CREATE TABLE tools (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    name VARCHAR(120) NOT NULL,
    description TEXT,
    tool_type VARCHAR(40) NOT NULL,
    input_schema JSONB NOT NULL DEFAULT '{}'::jsonb,
    configuration JSONB NOT NULL DEFAULT '{}'::jsonb,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (workspace_id, name),
    UNIQUE (workspace_id, id)
);

CREATE TABLE agent_tools (
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    agent_id UUID NOT NULL,
    tool_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (agent_id, tool_id),
    FOREIGN KEY (workspace_id, agent_id)
        REFERENCES agents(workspace_id, id) ON DELETE CASCADE,
    FOREIGN KEY (workspace_id, tool_id)
        REFERENCES tools(workspace_id, id) ON DELETE CASCADE
);
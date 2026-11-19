-- V6: Workflow nodes and edges
-- Source of truth: docs/DATABASE.md section 5
-- Nodes and edges are the canonical definition for a workflow version.
-- Published versions and their node/edge rows are immutable.

CREATE TABLE workflow_nodes (
    id UUID PRIMARY KEY,
    workflow_version_id UUID NOT NULL REFERENCES workflow_versions(id) ON DELETE CASCADE,
    node_key VARCHAR(120) NOT NULL,
    node_type VARCHAR(40) NOT NULL,
    label VARCHAR(160),
    position_x DOUBLE PRECISION NOT NULL DEFAULT 0,
    position_y DOUBLE PRECISION NOT NULL DEFAULT 0,
    configuration JSONB NOT NULL DEFAULT '{}'::jsonb,
    UNIQUE (workflow_version_id, node_key)
);

CREATE TABLE workflow_edges (
    id UUID PRIMARY KEY,
    workflow_version_id UUID NOT NULL REFERENCES workflow_versions(id) ON DELETE CASCADE,
    source_node_key VARCHAR(120) NOT NULL,
    target_node_key VARCHAR(120) NOT NULL,
    condition JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    FOREIGN KEY (workflow_version_id, source_node_key)
        REFERENCES workflow_nodes(workflow_version_id, node_key) ON DELETE CASCADE,
    FOREIGN KEY (workflow_version_id, target_node_key)
        REFERENCES workflow_nodes(workflow_version_id, node_key) ON DELETE CASCADE
);
package com.meld.engine.domain;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

/**
 * Immutable workflow definition per ARCHITECTURE.md §8.
 * Nodes and edges are the canonical representation (DATABASE.md §5).
 * Published versions are immutable; edits create a new version.
 */
public record WorkflowDefinition(
        String workflowId,
        String versionId,
        int versionNumber,
        List<WorkflowNodeDef> nodes,
        List<WorkflowEdgeDef> edges,
        JsonNode inputSchema,
        JsonNode outputSchema
) {
    public WorkflowDefinition {
        nodes = nodes != null ? List.copyOf(nodes) : List.of();
        edges = edges != null ? List.copyOf(edges) : List.of();
    }

    public record WorkflowNodeDef(
            String nodeKey,
            String nodeType,
            String label,
            double positionX,
            double positionY,
            JsonNode configuration
    ) {
        public WorkflowNodeDef {
            configuration = configuration != null ? configuration : com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        }
    }

    public record WorkflowEdgeDef(
            String edgeId,
            String sourceNodeKey,
            String targetNodeKey,
            JsonNode condition  // for conditional edges (P1)
    ) {
    }
}
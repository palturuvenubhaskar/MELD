package com.meld.workflow;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.UUID;

/**
 * Read-only view of one immutable version used by the execution worker.
 * Node configuration is already snapshotted; no agent lookup happens at run
 * time.
 */
public record WorkflowRunDefinition(
        UUID workflowId,
        UUID versionId,
        int versionNumber,
        List<RunNode> nodes,
        List<RunEdge> edges
) {
    public record RunNode(String key, String type, JsonNode config) {
    }

    public record RunEdge(String source, String target) {
    }
}

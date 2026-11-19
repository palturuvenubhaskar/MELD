package com.meld.engine.domain;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Core node contract per ARCHITECTURE.md §5.
 * Each node type implements this interface.
 * Pass explicit execution context; keep inputs/outputs structured and serializable.
 */
public interface WorkflowNode {
    /**
     * Unique node type identifier (e.g., "agent", "tool", "output", "condition", "delay", "approval").
     */
    String getNodeType();

    /**
     * Execute this node with the given context and node-specific configuration.
     * Returns a NodeResult with status, output, or error.
     */
    NodeResult execute(ExecutionContext context, JsonNode configuration);

    /**
     * Validate the node configuration before execution.
     * Throws IllegalArgumentException if invalid.
     */
    void validateConfiguration(JsonNode configuration);
}
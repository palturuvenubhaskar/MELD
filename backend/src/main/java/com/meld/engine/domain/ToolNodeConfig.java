package com.meld.engine.domain;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/**
 * Configuration for a ToolNode per ARCHITECTURE.md §5 and DATABASE.md §5.
 * MVP tools are read-only or low-risk; sensitive actions require approval nodes (P1).
 * Tool egress is default-deny with explicit allowlists (RULES.md Security).
 */
public record ToolNodeConfig(
        String toolId,
        String toolName,
        String toolType,           // e.g., "http_get", "json_transform"
        JsonNode configuration,    // tool-specific config (URL, headers, transform spec, etc.)
        Map<String, String> inputMapping,   // workflow input/variables -> tool input
        Map<String, String> outputMapping   // tool output -> workflow variable
) {
    public ToolNodeConfig {
        inputMapping = inputMapping != null ? Map.copyOf(inputMapping) : Map.of();
        outputMapping = outputMapping != null ? Map.copyOf(outputMapping) : Map.of();
        configuration = configuration != null ? configuration : com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
    }
}
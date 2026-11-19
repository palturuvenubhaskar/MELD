package com.meld.engine.domain;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/**
 * Configuration for an OutputNode per ARCHITECTURE.md §8.
 * Stores or presents the final workflow result.
 * Maps workflow variables/node outputs to the final output structure.
 */
public record OutputNodeConfig(
        JsonNode outputTemplate,   // JSON template with variable references like {{variableName}}
        Map<String, String> inputMapping  // workflow variables -> template fields
) {
    public OutputNodeConfig {
        inputMapping = inputMapping != null ? Map.copyOf(inputMapping) : Map.of();
        outputTemplate = outputTemplate != null ? outputTemplate : com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
    }
}
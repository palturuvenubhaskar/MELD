package com.meld.engine.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/**
 * Configuration for an AgentNode per ARCHITECTURE.md §5 and DATABASE.md §5.
 * Snapshots non-secret agent settings at publish time (provider, model, prompt, config).
 * Credentials remain a workspace-scoped reference resolved at runtime.
 */
public record AgentNodeConfig(
        @JsonProperty("agentId") String agentId,
        @JsonProperty("agentName") String agentName,
        @JsonProperty("provider") String provider,
        @JsonProperty("model") String model,
        @JsonProperty("systemPrompt") String systemPrompt,
        @JsonProperty("configuration") JsonNode configuration,
        @JsonProperty("inputMapping") Map<String, String> inputMapping,   // workflow input -> agent input
        @JsonProperty("outputMapping") Map<String, String> outputMapping  // agent output -> workflow variable
) {
    public AgentNodeConfig {
        inputMapping = inputMapping != null ? Map.copyOf(inputMapping) : Map.of();
        outputMapping = outputMapping != null ? Map.copyOf(outputMapping) : Map.of();
        configuration = configuration != null ? configuration : com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
    }
}
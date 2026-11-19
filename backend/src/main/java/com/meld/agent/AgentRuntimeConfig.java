package com.meld.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Typed view over the agent's {@code configuration} JSONB column.
 *
 * <p>Only non-secret settings live here. Anything the model needs to run is
 * snapshotted into workflow versions later (RULES.md), so this stays small.
 */
record AgentRuntimeConfig(Double temperature, Integer maxTokens) {

    private static final AgentRuntimeConfig EMPTY = new AgentRuntimeConfig(null, null);

    static AgentRuntimeConfig parse(String json, ObjectMapper mapper) {
        if (json == null || json.isBlank()) {
            return EMPTY;
        }
        try {
            JsonNode node = mapper.readTree(json);
            Double temperature = node.path("temperature").isNumber() ? node.path("temperature").asDouble() : null;
            Integer maxTokens = node.path("maxTokens").isNumber() ? node.path("maxTokens").asInt() : null;
            return new AgentRuntimeConfig(temperature, maxTokens);
        } catch (Exception ex) {
            return EMPTY;
        }
    }

    String toJson(ObjectMapper mapper) {
        try {
            var node = mapper.createObjectNode();
            if (temperature != null) {
                node.put("temperature", temperature);
            }
            if (maxTokens != null) {
                node.put("maxTokens", maxTokens);
            }
            return mapper.writeValueAsString(node);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to serialize agent configuration", ex);
        }
    }
}

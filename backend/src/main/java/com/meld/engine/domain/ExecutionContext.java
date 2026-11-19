package com.meld.engine.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Explicit execution context passed to nodes per ARCHITECTURE.md §5.
 * Keeps node inputs/outputs structured and serializable.
 * Contains workflow-scoped state, not node-scoped (that's in NodeExecution).
 */
public record ExecutionContext(
        UUID executionId,
        UUID workflowId,
        UUID workflowVersionId,
        UUID workspaceId,
        JsonNode workflowInput,
        Map<String, JsonNode> nodeOutputs,      // completed node outputs by nodeKey
        Map<String, JsonNode> workflowVariables, // mutable workflow-level variables
        Instant startedAt
) {
    public ExecutionContext {
        nodeOutputs = nodeOutputs != null ? new HashMap<>(nodeOutputs) : new HashMap<>();
        workflowVariables = workflowVariables != null ? new HashMap<>(workflowVariables) : new HashMap<>();
    }

    public static ExecutionContext create(UUID executionId, UUID workflowId, UUID workflowVersionId,
                                           UUID workspaceId, JsonNode workflowInput) {
        return new ExecutionContext(executionId, workflowId, workflowVersionId, workspaceId,
                workflowInput, Map.of(), new HashMap<>(), Instant.now());
    }

    public JsonNode getNodeOutput(String nodeKey) {
        return nodeOutputs.get(nodeKey);
    }

    public void putNodeOutput(String nodeKey, JsonNode output) {
        nodeOutputs.put(nodeKey, output);
    }

    public JsonNode getVariable(String key) {
        return workflowVariables.get(key);
    }

    public void setVariable(String key, JsonNode value) {
        workflowVariables.put(key, value);
    }

    @JsonIgnore
    public ObjectNode asJson(ObjectMapper mapper) {
        ObjectNode json = mapper.createObjectNode();
        json.put("executionId", executionId.toString());
        json.put("workflowId", workflowId.toString());
        json.put("workflowVersionId", workflowVersionId.toString());
        json.put("workspaceId", workspaceId.toString());
        json.set("workflowInput", workflowInput);
        ObjectNode outputs = mapper.createObjectNode();
        nodeOutputs.forEach(outputs::set);
        json.set("nodeOutputs", outputs);
        ObjectNode vars = mapper.createObjectNode();
        workflowVariables.forEach(vars::set);
        json.set("workflowVariables", vars);
        json.put("startedAt", startedAt.toString());
        return json;
    }
}
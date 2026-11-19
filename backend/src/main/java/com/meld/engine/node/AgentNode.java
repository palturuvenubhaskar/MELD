package com.meld.engine.node;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.meld.engine.domain.AgentNodeConfig;
import com.meld.engine.domain.ExecutionContext;
import com.meld.engine.domain.NodeResult;
import com.meld.engine.domain.WorkflowNode;

import java.time.Instant;
import java.util.Map;

/**
 * AgentNode implementation for the Phase 1 prototype.
 * In the real implementation, this would call the AI provider adapter.
 * For the prototype, it simulates an agent response based on the system prompt and input.
 */
public class AgentNode implements WorkflowNode {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String getNodeType() {
        return "agent";
    }

    @Override
    public NodeResult execute(ExecutionContext context, JsonNode configuration) {
        AgentNodeConfig config = MAPPER.convertValue(configuration, AgentNodeConfig.class);
        Instant startedAt = Instant.now();

        try {
            // Build the agent input from workflow context using inputMapping
            JsonNode agentInput = buildAgentInput(context, config);

            // Simulate agent execution (replace with real provider call in Phase 5)
            JsonNode agentOutput = simulateAgentResponse(config, agentInput);

            // Map agent output to workflow variables using outputMapping
            mapOutputToContext(context, config, agentOutput);

            return NodeResult.completed(agentOutput, startedAt, 1);

        } catch (Exception e) {
            return NodeResult.failed("AGENT_EXECUTION_ERROR", e.getMessage(), startedAt, 1);
        }
    }

    @Override
    public void validateConfiguration(JsonNode configuration) {
        AgentNodeConfig config = MAPPER.convertValue(configuration, AgentNodeConfig.class);
        if (config.agentId() == null || config.agentId().isBlank()) {
            throw new IllegalArgumentException("AgentNode requires agentId");
        }
        if (config.systemPrompt() == null || config.systemPrompt().isBlank()) {
            throw new IllegalArgumentException("AgentNode requires systemPrompt");
        }
        if (config.model() == null || config.model().isBlank()) {
            throw new IllegalArgumentException("AgentNode requires model");
        }
        if (config.provider() == null || config.provider().isBlank()) {
            throw new IllegalArgumentException("AgentNode requires provider");
        }
    }

    private JsonNode buildAgentInput(ExecutionContext context, AgentNodeConfig config) {
        ObjectNode input = MAPPER.createObjectNode();

        // Add workflow input
        if (context.workflowInput() != null) {
            input.set("workflowInput", context.workflowInput());
        }

        // Add mapped node outputs
        config.inputMapping().forEach((targetKey, sourceExpression) -> {
            JsonNode value = resolveExpression(context, sourceExpression);
            if (value != null) {
                input.set(targetKey, value);
            }
        });

        // Add workflow variables
        context.workflowVariables().forEach(input::set);

        return input;
    }

    private JsonNode simulateAgentResponse(AgentNodeConfig config, JsonNode input) {
        // Prototype: return a structured mock response
        ObjectNode response = MAPPER.createObjectNode();
        response.put("agentId", config.agentId());
        response.put("agentName", config.agentName());
        response.put("model", config.model());
        response.put("provider", config.provider());
        response.set("inputReceived", input);
        response.put("response", "Mock agent response for: " + input.toPrettyString());
        response.put("timestamp", Instant.now().toString());
        return response;
    }

    private void mapOutputToContext(ExecutionContext context, AgentNodeConfig config, JsonNode agentOutput) {
        config.outputMapping().forEach((workflowVar, agentOutputPath) -> {
            JsonNode value = extractPath(agentOutput, agentOutputPath);
            if (value != null) {
                context.setVariable(workflowVar, value);
            }
        });
    }

    private JsonNode resolveExpression(ExecutionContext context, String expression) {
        // Simple expression resolution: ${variableName} or nodeKey.outputField
        if (expression.startsWith("${") && expression.endsWith("}")) {
            String varName = expression.substring(2, expression.length() - 1);
            return context.getVariable(varName);
        }
        // nodeKey.fieldPath
        int dotIndex = expression.indexOf('.');
        if (dotIndex > 0) {
            String nodeKey = expression.substring(0, dotIndex);
            String fieldPath = expression.substring(dotIndex + 1);
            JsonNode nodeOutput = context.getNodeOutput(nodeKey);
            if (nodeOutput != null) {
                return extractPath(nodeOutput, fieldPath);
            }
        }
        // Direct variable reference
        return context.getVariable(expression);
    }

    private JsonNode extractPath(JsonNode node, String path) {
        String[] parts = path.split("\\.");
        JsonNode current = node;
        for (String part : parts) {
            if (current == null || current.isMissingNode()) {
                return null;
            }
            current = current.get(part);
        }
        return current;
    }
}
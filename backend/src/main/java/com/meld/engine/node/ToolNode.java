package com.meld.engine.node;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.meld.engine.domain.ExecutionContext;
import com.meld.engine.domain.NodeResult;
import com.meld.engine.domain.ToolNodeConfig;
import com.meld.engine.domain.WorkflowNode;

import java.time.Instant;
import java.util.Map;

/**
 * ToolNode implementation for the Phase 1 prototype.
 * MVP tools: allowlisted HTTP GET, JSON transform (per SETUP.md §3).
 * Real implementation would use HTTP client with egress controls (RULES.md Security).
 */
public class ToolNode implements WorkflowNode {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String getNodeType() {
        return "tool";
    }

    @Override
    public NodeResult execute(ExecutionContext context, JsonNode configuration) {
        ToolNodeConfig config = MAPPER.convertValue(configuration, ToolNodeConfig.class);
        Instant startedAt = Instant.now();

        try {
            // Build tool input from workflow context
            JsonNode toolInput = buildToolInput(context, config);

            // Execute tool based on type
            JsonNode toolOutput = switch (config.toolType()) {
                case "http_get" -> executeHttpGet(config, toolInput);
                case "json_transform" -> executeJsonTransform(config, toolInput);
                default -> throw new IllegalArgumentException("Unknown tool type: " + config.toolType());
            };

            // Map tool output to workflow variables
            mapOutputToContext(context, config, toolOutput);

            return NodeResult.completed(toolOutput, startedAt, 1);

        } catch (Exception e) {
            return NodeResult.failed("TOOL_EXECUTION_ERROR", e.getMessage(), startedAt, 1);
        }
    }

    @Override
    public void validateConfiguration(JsonNode configuration) {
        ToolNodeConfig config = MAPPER.convertValue(configuration, ToolNodeConfig.class);
        if (config.toolId() == null || config.toolId().isBlank()) {
            throw new IllegalArgumentException("ToolNode requires toolId");
        }
        if (config.toolType() == null || config.toolType().isBlank()) {
            throw new IllegalArgumentException("ToolNode requires toolType");
        }
        if (!config.toolType().equals("http_get") && !config.toolType().equals("json_transform")) {
            throw new IllegalArgumentException("Unsupported tool type: " + config.toolType() + ". MVP supports: http_get, json_transform");
        }
    }

    private JsonNode buildToolInput(ExecutionContext context, ToolNodeConfig config) {
        ObjectNode input = MAPPER.createObjectNode();

        // Add workflow input
        if (context.workflowInput() != null) {
            input.set("workflowInput", context.workflowInput());
        }

        // Add mapped node outputs and variables
        config.inputMapping().forEach((targetKey, sourceExpression) -> {
            JsonNode value = resolveExpression(context, sourceExpression);
            if (value != null) {
                input.set(targetKey, value);
            }
        });

        context.workflowVariables().forEach(input::set);

        return input;
    }

    private JsonNode executeHttpGet(ToolNodeConfig config, JsonNode input) {
        // Prototype: simulate HTTP GET response
        // Real implementation: HttpClient with egress controls, DNS pinning, redirect validation
        ObjectNode response = MAPPER.createObjectNode();
        response.put("toolType", "http_get");
        response.put("toolId", config.toolId());
        response.put("status", "success");
        response.put("message", "Mock HTTP GET response");
        response.set("requestInput", input);
        response.put("timestamp", Instant.now().toString());

        // Simulate response data from config
        if (config.configuration().has("mockResponse")) {
            response.set("data", config.configuration().get("mockResponse"));
        }
        return response;
    }

    private JsonNode executeJsonTransform(ToolNodeConfig config, JsonNode input) {
        // Prototype: JSON transformation using config.spec
        // Real implementation: use a JSON transformation library (e.g., Jolt, Jackson)
        ObjectNode response = MAPPER.createObjectNode();
        response.put("toolType", "json_transform");
        response.put("toolId", config.toolId());
        response.put("status", "success");
        response.put("message", "Mock JSON transform");
        response.set("input", input);

        JsonNode transformSpec = config.configuration().get("transformSpec");
        if (transformSpec != null) {
            response.set("transformSpec", transformSpec);
            // Simple prototype: just echo input with a marker
            response.set("output", input.deepCopy());
        } else {
            response.set("output", input);
        }
        response.put("timestamp", Instant.now().toString());
        return response;
    }

    private void mapOutputToContext(ExecutionContext context, ToolNodeConfig config, JsonNode toolOutput) {
        config.outputMapping().forEach((workflowVar, toolOutputPath) -> {
            JsonNode value = extractPath(toolOutput, toolOutputPath);
            if (value != null) {
                context.setVariable(workflowVar, value);
            }
        });
    }

    private JsonNode resolveExpression(ExecutionContext context, String expression) {
        if (expression.startsWith("${") && expression.endsWith("}")) {
            String varName = expression.substring(2, expression.length() - 1);
            return context.getVariable(varName);
        }
        int dotIndex = expression.indexOf('.');
        if (dotIndex > 0) {
            String nodeKey = expression.substring(0, dotIndex);
            String fieldPath = expression.substring(dotIndex + 1);
            JsonNode nodeOutput = context.getNodeOutput(nodeKey);
            if (nodeOutput != null) {
                return extractPath(nodeOutput, fieldPath);
            }
        }
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
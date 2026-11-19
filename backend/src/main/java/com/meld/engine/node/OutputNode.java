package com.meld.engine.node;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.meld.engine.domain.ExecutionContext;
import com.meld.engine.domain.NodeResult;
import com.meld.engine.domain.OutputNodeConfig;
import com.meld.engine.domain.WorkflowNode;

import java.time.Instant;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OutputNode implementation for the Phase 1 prototype.
 * Renders the final workflow output using a template with variable substitution.
 */
public class OutputNode implements WorkflowNode {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern VAR_PATTERN = Pattern.compile("\\{\\{(.+?)\\}\\}");

    @Override
    public String getNodeType() {
        return "output";
    }

    @Override
    public NodeResult execute(ExecutionContext context, JsonNode configuration) {
        OutputNodeConfig config = MAPPER.convertValue(configuration, OutputNodeConfig.class);
        Instant startedAt = Instant.now();

        try {
            // Build the final output by resolving template variables
            JsonNode finalOutput = renderTemplate(config.outputTemplate(), context);

            // Also apply inputMapping for additional variables
            config.inputMapping().forEach((targetKey, sourceExpression) -> {
                JsonNode value = resolveExpression(context, sourceExpression);
                if (value != null) {
                    context.setVariable(targetKey, value);
                }
            });

            // Set the final output as a workflow variable for potential downstream use
            context.setVariable("finalOutput", finalOutput);

            return NodeResult.completed(finalOutput, startedAt, 1);

        } catch (Exception e) {
            return NodeResult.failed("OUTPUT_RENDER_ERROR", e.getMessage(), startedAt, 1);
        }
    }

    @Override
    public void validateConfiguration(JsonNode configuration) {
        OutputNodeConfig config = MAPPER.convertValue(configuration, OutputNodeConfig.class);
        if (config.outputTemplate() == null || config.outputTemplate().isMissingNode()) {
            throw new IllegalArgumentException("OutputNode requires outputTemplate");
        }
    }

    private JsonNode renderTemplate(JsonNode template, ExecutionContext context) {
        if (template.isTextual()) {
            String text = template.asText();
            String rendered = substituteVariables(text, context);
            return MAPPER.valueToTree(rendered);
        } else if (template.isObject()) {
            ObjectNode result = MAPPER.createObjectNode();
            template.fields().forEachRemaining(entry -> {
                result.set(entry.getKey(), renderTemplate(entry.getValue(), context));
            });
            return result;
        } else if (template.isArray()) {
            var result = MAPPER.createArrayNode();
            template.forEach(node -> result.add(renderTemplate(node, context)));
            return result;
        }
        return template;
    }

    private String substituteVariables(String text, ExecutionContext context) {
        Matcher matcher = VAR_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String varPath = matcher.group(1).trim();
            JsonNode value = resolveExpression(context, varPath);
            String replacement = value != null ? value.toString() : "";
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
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
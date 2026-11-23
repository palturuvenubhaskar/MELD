package com.meld.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves the small {@code {{expression}}} template language used by agent
 * input templates and output nodes.
 *
 * <ul>
 *   <li>{@code {{input}}} — the workflow input</li>
 *   <li>{@code {{previous}}} — the previous node's output</li>
 *   <li>{@code {{nodeKey}}} — one node's output</li>
 *   <li>{@code {{nodeKey.path.to.field}}} — a field inside a node output</li>
 * </ul>
 */
final class TemplateRenderer {

    private static final Pattern VARIABLE = Pattern.compile("\\{\\{(.+?)}}");

    private TemplateRenderer() {}

    static JsonNode render(JsonNode template,
                           Map<String, JsonNode> nodeOutputs,
                           JsonNode workflowInput,
                           JsonNode previous,
                           ObjectMapper mapper) {
        if (template == null || template.isNull()) {
            return mapper.nullNode();
        }
        if (template.isTextual()) {
            return mapper.getNodeFactory().textNode(text(template.asText(), nodeOutputs, workflowInput, previous, mapper));
        }
        if (template.isObject()) {
            ObjectNode result = mapper.createObjectNode();
            template.fields().forEachRemaining(entry ->
                    result.set(entry.getKey(), render(entry.getValue(), nodeOutputs, workflowInput, previous, mapper)));
            return result;
        }
        if (template.isArray()) {
            var result = mapper.createArrayNode();
            template.forEach(item -> result.add(render(item, nodeOutputs, workflowInput, previous, mapper)));
            return result;
        }
        return template;
    }

    static String text(String template,
                       Map<String, JsonNode> nodeOutputs,
                       JsonNode workflowInput,
                       JsonNode previous,
                       ObjectMapper mapper) {
        if (template == null) {
            return "";
        }
        Matcher matcher = VARIABLE.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            JsonNode value = resolve(matcher.group(1).trim(), nodeOutputs, workflowInput, previous);
            String replacement = value == null || value.isNull() ? "" : value.isTextual() ? value.asText() : value.toString();
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static JsonNode resolve(String expression,
                                    Map<String, JsonNode> nodeOutputs,
                                    JsonNode workflowInput,
                                    JsonNode previous) {
        if ("input".equals(expression)) {
            return workflowInput;
        }
        if ("previous".equals(expression)) {
            return previous;
        }
        if (expression.startsWith("previous.")) {
            return previous == null ? null : extract(previous, expression.substring("previous.".length()));
        }
        if (expression.startsWith("input.")) {
            return workflowInput == null ? null : extract(workflowInput, expression.substring("input.".length()));
        }
        int dot = expression.indexOf('.');
        if (dot > 0) {
            String nodeKey = expression.substring(0, dot);
            JsonNode output = nodeOutputs.get(nodeKey);
            return output == null ? null : extract(output, expression.substring(dot + 1));
        }
        return nodeOutputs.get(expression);
    }

    private static JsonNode extract(JsonNode node, String path) {
        JsonNode current = node;
        for (String part : path.split("\\.")) {
            if (current == null || current.isMissingNode()) {
                return null;
            }
            current = current.get(part);
        }
        return current;
    }
}

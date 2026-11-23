package com.meld.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * MVP tool runtime (ROADMAP.md M6): an allowlisted HTTP GET and a local JSON
 * transform. Tool output is untrusted data and is never interpreted as
 * instructions.
 */
@Component
public class ToolRuntime {

    private final PinnedHttpGet httpGet;
    private final ObjectMapper mapper;

    public ToolRuntime(PinnedHttpGet httpGet, ObjectMapper mapper) {
        this.httpGet = httpGet;
        this.mapper = mapper;
    }

    public JsonNode execute(UUID workspaceId, JsonNode config, JsonNode input) {
        String toolType = config.path("toolType").asText("");
        return switch (toolType) {
            case "http_get" -> httpGet(config, input);
            case "json_transform" -> jsonTransform(config, input);
            default -> throw new ToolExecutionException("TOOL_TYPE_UNSUPPORTED",
                    "Unsupported tool type: " + toolType);
        };
    }

    private JsonNode httpGet(JsonNode config, JsonNode input) {
        String url = config.path("url").asText(null);
        if (url == null || url.isBlank()) {
            throw new ToolExecutionException("TOOL_URL_REQUIRED", "An HTTP tool needs a destination URL");
        }
        PinnedHttpGet.HttpResult result = httpGet.get(url);

        ObjectNode response = mapper.createObjectNode();
        response.put("status", result.status());
        if (result.contentType() != null) {
            response.put("contentType", result.contentType());
        }
        response.set("body", parseBody(result.body(), result.contentType()));
        return response;
    }

    private JsonNode parseBody(String body, String contentType) {
        if (body == null || body.isBlank()) {
            return mapper.nullNode();
        }
        String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (type.contains("json") || body.stripLeading().startsWith("{") || body.stripLeading().startsWith("[")) {
            try {
                return mapper.readTree(body);
            } catch (Exception ignored) {
                // Untrusted content is kept as text rather than failing the node.
            }
        }
        return mapper.getNodeFactory().textNode(body);
    }

    private JsonNode jsonTransform(JsonNode config, JsonNode input) {
        JsonNode mapping = config.get("mapping");
        if (mapping == null || !mapping.isObject() || mapping.isEmpty()) {
            return input;
        }

        ObjectNode output = mapper.createObjectNode();
        Iterator<Map.Entry<String, JsonNode>> fields = mapping.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            JsonNode value = extractPath(input, entry.getValue().asText());
            output.set(entry.getKey(), value == null ? mapper.nullNode() : value);
        }
        return output;
    }

    private JsonNode extractPath(JsonNode root, String path) {
        if (path == null || path.isBlank() || ".".equals(path)) {
            return root;
        }
        JsonNode current = root;
        for (String part : path.split("\\.")) {
            if (current == null || current.isMissingNode()) {
                return null;
            }
            current = current.get(part);
        }
        return current;
    }
}

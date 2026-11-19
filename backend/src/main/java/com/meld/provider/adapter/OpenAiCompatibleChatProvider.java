package com.meld.provider.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meld.common.exception.ApiException;
import com.meld.provider.ProviderKind;
import com.meld.provider.ProviderProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Adapter for providers that expose the OpenAI chat-completions shape:
 * OpenRouter, OpenAI, Groq and Ollama all do.
 *
 * <p>Failures are translated into safe API errors. Provider response bodies are
 * never relayed to the client and the API key is never logged, because both
 * can contain account-identifying data.
 */
@Component
public class OpenAiCompatibleChatProvider implements ChatProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleChatProvider.class);
    private static final String CHAT_PATH = "/chat/completions";

    private final ProviderProperties properties;
    private final ObjectMapper objectMapper;

    public OpenAiCompatibleChatProvider(ProviderProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(ProviderKind kind) {
        return true;
    }

    @Override
    public ChatResponse complete(ChatInvocation invocation) {
        String baseUrl = properties.baseUrl(invocation.kind());
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "PROVIDER_NOT_CONFIGURED",
                    "Provider base URL is not configured");
        }

        long startedAt = System.nanoTime();
        Map<String, Object> body = requestBody(invocation);
        String raw;
        try {
            raw = client(baseUrl)
                    .post()
                    .uri(CHAT_PATH)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + invocation.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException ex) {
            throw statusError(invocation.kind(), ex.getStatusCode().value());
        } catch (RuntimeException ex) {
            // Includes ResourceAccessException. Anything the transport throws is
            // reported as an availability problem rather than a 500.
            throw transportError(invocation.kind(), ex);
        }
        long latencyMs = (System.nanoTime() - startedAt) / 1_000_000;

        return parse(invocation, raw, latencyMs);
    }

    private RestClient client(String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(toMillis(properties.getConnectTimeout()));
        factory.setReadTimeout(toMillis(properties.getReadTimeout()));
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    private Map<String, Object> requestBody(ChatInvocation invocation) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", invocation.model());
        body.put("messages", invocation.messages().stream()
                .map(message -> Map.of("role", message.role(), "content", message.content()))
                .toList());
        if (invocation.temperature() != null) {
            body.put("temperature", invocation.temperature());
        }
        if (invocation.maxTokens() != null) {
            body.put("max_tokens", invocation.maxTokens());
        }
        return body;
    }

    private ChatResponse parse(ChatInvocation invocation, String raw, long latencyMs) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
            if (!contentNode.isTextual()) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "PROVIDER_BAD_RESPONSE",
                        "Provider returned an unexpected response");
            }
            JsonNode usage = root.path("usage");
            return new ChatResponse(
                    contentNode.asText(),
                    root.path("model").asText(invocation.model()),
                    usage.path("prompt_tokens").isNumber() ? usage.path("prompt_tokens").asInt() : null,
                    usage.path("completion_tokens").isNumber() ? usage.path("completion_tokens").asInt() : null,
                    latencyMs);
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Provider {} returned unparseable JSON", invocation.kind());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "PROVIDER_BAD_RESPONSE",
                    "Provider returned an unexpected response");
        }
    }

    private ApiException statusError(ProviderKind kind, int status) {
        // Status only: response bodies and request headers can identify accounts.
        log.warn("Provider {} returned HTTP {}", kind, status);
        if (status == 401 || status == 403) {
            return new ApiException(HttpStatus.BAD_GATEWAY, "PROVIDER_AUTH_FAILED",
                    "The provider rejected the stored credential");
        }
        if (status == 429) {
            return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PROVIDER_RATE_LIMITED",
                    "The provider is rate limiting requests; retry later");
        }
        return new ApiException(HttpStatus.BAD_GATEWAY, "PROVIDER_ERROR",
                "The provider request failed");
    }

    private ApiException transportError(ProviderKind kind, RuntimeException ex) {
        boolean timeout = hasCause(ex, SocketTimeoutException.class)
                || hasCause(ex, HttpTimeoutException.class)
                || hasCause(ex, java.util.concurrent.TimeoutException.class);
        log.warn("Provider {} {} ({})", kind, timeout ? "timed out" : "was unreachable", ex.getClass().getSimpleName());
        if (timeout) {
            return new ApiException(HttpStatus.GATEWAY_TIMEOUT, "PROVIDER_TIMEOUT",
                    "The provider did not respond in time");
        }
        return new ApiException(HttpStatus.BAD_GATEWAY, "PROVIDER_UNAVAILABLE",
                "The provider could not be reached");
    }

    private static boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return true;
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return false;
    }

    private static int toMillis(java.time.Duration duration) {
        long millis = duration == null ? 0 : duration.toMillis();
        return millis > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) millis;
    }
}

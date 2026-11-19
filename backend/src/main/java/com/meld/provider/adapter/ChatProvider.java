package com.meld.provider.adapter;

import com.meld.provider.ProviderKind;

import java.util.List;

/**
 * Provider-independent chat call. The execution engine and agent tests depend
 * on this interface, not on a concrete HTTP client (ARCHITECTURE.md §5).
 */
public interface ChatProvider {

    boolean supports(ProviderKind kind);

    ChatResponse complete(ChatInvocation invocation);

    record ChatMessage(String role, String content) {
        public static ChatMessage system(String content) {
            return new ChatMessage("system", content);
        }

        public static ChatMessage user(String content) {
            return new ChatMessage("user", content);
        }
    }

    /**
     * A single completion request. {@code apiKey} is plaintext here only for the
     * lifetime of the call; implementations must never log it or include it in
     * an error.
     */
    record ChatInvocation(
            ProviderKind kind,
            String apiKey,
            String model,
            List<ChatMessage> messages,
            Double temperature,
            Integer maxTokens
    ) {
        @Override
        public String toString() {
            // Records print every component; never let the key reach a log line.
            return "ChatInvocation[" + kind + ", " + model + ", messages=" + messages.size()
                    + ", temperature=" + temperature + ", maxTokens=" + maxTokens + ", apiKey=REDACTED]";
        }
    }

    record ChatResponse(
            String content,
            String model,
            Integer inputTokens,
            Integer outputTokens,
            long latencyMs
    ) {}
}

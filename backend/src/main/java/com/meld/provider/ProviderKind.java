package com.meld.provider;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Supported model providers. Every entry is called through the
 * OpenAI-compatible chat-completions adapter; separate adapter families can be
 * added later without changing the stored value.
 */
public enum ProviderKind {

    OPENROUTER("openrouter"),
    OPENAI("openai"),
    GROQ("groq"),
    OLLAMA("ollama");

    private final String key;

    ProviderKind(String key) {
        this.key = key;
    }

    /** Key used in {@code meld.providers.base-urls}. */
    public String key() {
        return key;
    }

    public static Optional<ProviderKind> fromString(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(kind -> kind.name().equals(normalized))
                .findFirst();
    }

    public static String supportedNames() {
        return Arrays.stream(values()).map(Enum::name).sorted().reduce((a, b) -> a + ", " + b).orElse("");
    }
}

package com.meld.agent;

import java.util.UUID;

/**
 * Non-secret agent settings copied into a workflow version when it is created
 * (ARCHITECTURE.md §5, DATABASE.md §5).
 *
 * <p>Published versions are immutable, so editing a reusable agent afterwards
 * must not change an existing workflow version. The credential is kept as a
 * live, workspace-scoped reference so it can be rotated independently.
 */
public record AgentSnapshot(
        UUID id,
        String name,
        String model,
        String systemPrompt,
        UUID providerCredentialId,
        Double temperature,
        Integer maxTokens
) {
}

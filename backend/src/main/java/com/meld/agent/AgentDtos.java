package com.meld.agent;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/**
 * Agent DTOs per ARCHITECTURE.md §7. Provider secrets are never part of an
 * agent: only the workspace-scoped credential reference is stored.
 */
public final class AgentDtos {

    private AgentDtos() {}

    public record CreateAgentRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 2000) String description,
            @NotBlank @Size(max = 120) String model,
            @NotBlank @Size(max = 8000) String systemPrompt,
            @NotNull UUID providerCredentialId,
            @DecimalMin("0.0") @DecimalMax("2.0") Double temperature,
            @Min(1) @Max(8192) Integer maxTokens
    ) {}

    /** PATCH semantics: a null field is left unchanged. */
    public record UpdateAgentRequest(
            @Size(min = 1, max = 120) String name,
            @Size(max = 2000) String description,
            @Size(min = 1, max = 120) String model,
            @Size(min = 1, max = 8000) String systemPrompt,
            UUID providerCredentialId,
            @DecimalMin("0.0") @DecimalMax("2.0") Double temperature,
            @Min(1) @Max(8192) Integer maxTokens
    ) {}

    public record AgentResponse(
            UUID id,
            String name,
            String description,
            String model,
            String systemPrompt,
            UUID providerCredentialId,
            Double temperature,
            Integer maxTokens,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record AgentTestRequest(
            @NotBlank @Size(max = 20000) String input
    ) {}

    /**
     * Result of a single test prompt. Usage is reported but not persisted;
     * durable usage records belong to executions (M6).
     */
    public record AgentTestResponse(
            String output,
            String model,
            Integer inputTokens,
            Integer outputTokens,
            long latencyMs
    ) {}
}

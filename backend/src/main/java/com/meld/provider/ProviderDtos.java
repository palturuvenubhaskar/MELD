package com.meld.provider;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/**
 * Provider credential DTOs. Responses never include the secret, the ciphertext
 * or any derived hint that would help reconstruct it (TEST.md SEC-005).
 */
public final class ProviderDtos {

    private ProviderDtos() {}

    public record CreateCredentialRequest(
            @NotBlank @Size(max = 60) String provider,
            @NotBlank @Size(max = 120) String displayName,
            @NotBlank @Size(min = 8, max = 4096) String apiKey
    ) {}

    public record CredentialResponse(
            UUID id,
            String provider,
            String displayName,
            Instant createdAt
    ) {}
}

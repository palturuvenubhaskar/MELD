package com.meld.auth.dto;

import com.meld.auth.MeldUserPrincipal;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Authentication DTOs per ARCHITECTURE.md §7.
 * Never exposes password hashes. Workspace context lives in WorkspaceDtos.
 */
public final class AuthDtos {

    private AuthDtos() {}

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(min = 8, max = 128) String password,
            @Size(max = 120) String displayName
    ) {}

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password
    ) {}

    public record UserResponse(
            UUID id,
            String email,
            String displayName
    ) {
        public static UserResponse from(MeldUserPrincipal principal) {
            return new UserResponse(principal.getId(), principal.getEmail(), principal.getDisplayName());
        }
    }

    /** CSRF bootstrap for browser clients (cookie double-submit). */
    public record CsrfResponse(
            String headerName,
            String parameterName,
            String token
    ) {}
}

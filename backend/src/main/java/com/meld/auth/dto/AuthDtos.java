package com.meld.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Authentication DTOs per ARCHITECTURE.md §7.
 * Uses validation annotations; never exposes password hashes.
 */
public class AuthDtos {

    public record RegisterRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 128) String password,
            @Size(max = 120) String displayName
    ) {}

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password
    ) {}

    public record AuthResponse(
            String userId,
            String email,
            String displayName,
            String workspaceId,
            String workspaceName
    ) {}

    public record WorkspaceResponse(
            String id,
            String name,
            String slug,
            String role,
            String createdAt
    ) {}

    public record CreateWorkspaceRequest(
            @NotBlank @Size(min = 1, max = 120) String name,
            @NotBlank @Size(min = 1, max = 80) String slug
    ) {}
}
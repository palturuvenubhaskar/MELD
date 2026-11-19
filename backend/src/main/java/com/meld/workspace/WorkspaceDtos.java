package com.meld.workspace;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/**
 * Workspace DTOs per ARCHITECTURE.md §7.
 */
public final class WorkspaceDtos {

    private WorkspaceDtos() {}

    public record CreateWorkspaceRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 80) String slug
    ) {}

    public record WorkspaceResponse(
            UUID id,
            String name,
            String slug,
            String role,
            Instant createdAt
    ) {}

    public record AddMemberRequest(
            @NotBlank @Email String email,
            @Size(max = 30) String role
    ) {}

    public record MemberResponse(
            UUID userId,
            String email,
            String displayName,
            String role,
            Instant joinedAt
    ) {}
}

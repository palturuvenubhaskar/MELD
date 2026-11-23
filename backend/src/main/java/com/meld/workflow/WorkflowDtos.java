package com.meld.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Workflow and version DTOs per ARCHITECTURE.md §7 and DATABASE.md §5.
 *
 * <p>Versions are the canonical definition: a version owns its nodes and edges
 * and is never edited. Saving an edit creates the next version.
 */
public final class WorkflowDtos {

    private WorkflowDtos() {}

    public record CreateWorkflowRequest(
            @NotBlank @Size(max = 160) String name,
            @Size(max = 2000) String description
    ) {}

    public record UpdateWorkflowRequest(
            @Size(min = 1, max = 160) String name,
            @Size(max = 2000) String description
    ) {}

    public record WorkflowResponse(
            UUID id,
            String name,
            String description,
            String status,
            Integer latestVersion,
            Integer latestPublishedVersion,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record CreateVersionRequest(
            boolean publish,
            @NotEmpty @Valid List<NodeInput> nodes,
            @Valid List<EdgeInput> edges
    ) {}

    public record NodeInput(
            @NotBlank @Size(max = 120) String key,
            @NotBlank @Size(max = 40) String type,
            @Size(max = 160) String label,
            Double positionX,
            Double positionY,
            JsonNode config
    ) {}

    public record EdgeInput(
            @NotBlank @Size(max = 120) String source,
            @NotBlank @Size(max = 120) String target,
            JsonNode condition
    ) {}

    public record VersionSummary(
            UUID id,
            int versionNumber,
            Instant publishedAt,
            Instant createdAt
    ) {}

    public record VersionResponse(
            UUID id,
            UUID workflowId,
            int versionNumber,
            Instant publishedAt,
            Instant createdAt,
            List<NodeResponse> nodes,
            List<EdgeResponse> edges
    ) {}

    public record NodeResponse(
            String key,
            String type,
            String label,
            double positionX,
            double positionY,
            JsonNode config
    ) {}

    public record EdgeResponse(
            UUID id,
            String source,
            String target,
            JsonNode condition
    ) {}
}

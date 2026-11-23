package com.meld.execution;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Execution API DTOs per ARCHITECTURE.md §7 / ROADMAP.md M6.
 */
public final class ExecutionDtos {

    private ExecutionDtos() {}

    public record StartExecutionRequest(
            UUID versionId,
            JsonNode input,
            @Size(max = 160) String idempotencyKey
    ) {}

    public record ExecutionSummary(
            UUID id,
            UUID workflowId,
            UUID workflowVersionId,
            int versionNumber,
            String status,
            String errorCode,
            String errorMessage,
            Instant requestedAt,
            Instant startedAt,
            Instant completedAt,
            Instant cancelRequestedAt
    ) {}

    public record ExecutionResponse(
            UUID id,
            UUID workflowId,
            UUID workflowVersionId,
            int versionNumber,
            String status,
            JsonNode input,
            JsonNode output,
            String errorCode,
            String errorMessage,
            Instant requestedAt,
            Instant startedAt,
            Instant completedAt,
            Instant cancelRequestedAt,
            List<NodeExecutionResponse> nodes
    ) {}

    public record NodeExecutionResponse(
            UUID id,
            String nodeKey,
            String status,
            int attemptNumber,
            JsonNode input,
            JsonNode output,
            String errorCode,
            String errorMessage,
            Instant startedAt,
            Instant completedAt
    ) {}

    public record ExecutionLogResponse(
            UUID id,
            UUID nodeExecutionId,
            String level,
            String eventType,
            String message,
            JsonNode metadata,
            Instant createdAt
    ) {}
}

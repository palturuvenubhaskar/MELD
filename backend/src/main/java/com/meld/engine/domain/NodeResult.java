package com.meld.engine.domain;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.Optional;

/**
 * Result of a single node execution per ARCHITECTURE.md §5.
 * Keeps inputs and outputs structured and serializable.
 */
public record NodeResult(
        NodeStatus status,
        JsonNode output,
        String errorCode,
        String errorMessage,
        Instant startedAt,
        Instant completedAt,
        int attemptNumber
) {
    public static NodeResult running(int attemptNumber) {
        return new NodeResult(NodeStatus.RUNNING, null, null, null, Instant.now(), null, attemptNumber);
    }

    public static NodeResult completed(JsonNode output, Instant startedAt, int attemptNumber) {
        return new NodeResult(NodeStatus.COMPLETED, output, null, null, startedAt, Instant.now(), attemptNumber);
    }

    public static NodeResult failed(String errorCode, String errorMessage, Instant startedAt, int attemptNumber) {
        return new NodeResult(NodeStatus.FAILED, null, errorCode, errorMessage, startedAt, Instant.now(), attemptNumber);
    }

    public static NodeResult outcomeUnknown(String errorCode, String errorMessage, Instant startedAt, int attemptNumber) {
        return new NodeResult(NodeStatus.OUTCOME_UNKNOWN, null, errorCode, errorMessage, startedAt, Instant.now(), attemptNumber);
    }

    public static NodeResult cancelled(Instant startedAt, int attemptNumber) {
        return new NodeResult(NodeStatus.CANCELLED, null, "CANCELLED", "Execution cancelled at node boundary", startedAt, Instant.now(), attemptNumber);
    }

    public boolean isTerminal() {
        return status == NodeStatus.COMPLETED || status == NodeStatus.FAILED
                || status == NodeStatus.CANCELLED || status == NodeStatus.OUTCOME_UNKNOWN;
    }

    public boolean isRetryEligible() {
        return status == NodeStatus.FAILED && errorCode != null && errorCode.startsWith("RETRYABLE_");
    }

    public Optional<JsonNode> getOutput() {
        return Optional.ofNullable(output);
    }
}
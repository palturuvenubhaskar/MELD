package com.meld.engine.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DomainObjectsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void executionContextCreationAndManipulation() {
        UUID executionId = UUID.randomUUID();
        UUID workflowId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();

        JsonNode input = MAPPER.createObjectNode().put("key", "value");

        ExecutionContext context = ExecutionContext.create(executionId, workflowId, UUID.randomUUID(), workspaceId, input);

        assertThat(context.executionId()).isEqualTo(executionId);
        assertThat(context.workflowId()).isEqualTo(workflowId);
        assertThat(context.workspaceId()).isEqualTo(workspaceId);
        assertThat(context.workflowInput()).isEqualTo(input);
        assertThat(context.nodeOutputs()).isEmpty();
        assertThat(context.workflowVariables()).isEmpty();
        assertThat(context.startedAt()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void executionContextNodeOutputsAndVariables() {
        ExecutionContext context = ExecutionContext.create(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), MAPPER.createObjectNode());

        JsonNode output1 = MAPPER.createObjectNode().put("result", "success");
        context.putNodeOutput("node1", output1);

        assertThat(context.getNodeOutput("node1")).isEqualTo(output1);
        assertThat(context.getNodeOutput("nonexistent")).isNull();

        context.setVariable("var1", MAPPER.valueToTree("test-value"));
        assertThat(context.getVariable("var1").asText()).isEqualTo("test-value");
    }

    @Test
    void nodeResultFactoryMethods() {
        Instant now = Instant.now();

        NodeResult running = NodeResult.running(1);
        assertThat(running.status()).isEqualTo(NodeStatus.RUNNING);
        assertThat(running.attemptNumber()).isEqualTo(1);

        JsonNode output = MAPPER.createObjectNode().put("data", "test");
        NodeResult completed = NodeResult.completed(output, now, 1);
        assertThat(completed.status()).isEqualTo(NodeStatus.COMPLETED);
        assertThat(completed.getOutput()).contains(output);
        assertThat(completed.isTerminal()).isTrue();

        NodeResult failed = NodeResult.failed("ERROR_CODE", "Error message", now, 2);
        assertThat(failed.status()).isEqualTo(NodeStatus.FAILED);
        assertThat(failed.errorCode()).isEqualTo("ERROR_CODE");
        assertThat(failed.errorMessage()).isEqualTo("Error message");
        assertThat(failed.attemptNumber()).isEqualTo(2);
        assertThat(failed.isRetryEligible()).isFalse();

        NodeResult retryable = NodeResult.failed("RETRYABLE_TIMEOUT", "Timeout", now, 1);
        assertThat(retryable.isRetryEligible()).isTrue();

        NodeResult unknown = NodeResult.outcomeUnknown("UNKNOWN", "External action uncertain", now, 1);
        assertThat(unknown.status()).isEqualTo(NodeStatus.OUTCOME_UNKNOWN);
        assertThat(unknown.isTerminal()).isTrue();

        NodeResult cancelled = NodeResult.cancelled(now, 1);
        assertThat(cancelled.status()).isEqualTo(NodeStatus.CANCELLED);
        assertThat(cancelled.errorCode()).isEqualTo("CANCELLED");
    }

    @Test
    void executionStatusValues() {
        // Verify all MVP statuses exist
        assertThat(ExecutionStatus.PENDING).isNotNull();
        assertThat(ExecutionStatus.RUNNING).isNotNull();
        assertThat(ExecutionStatus.COMPLETED).isNotNull();
        assertThat(ExecutionStatus.FAILED).isNotNull();
        assertThat(ExecutionStatus.CANCELLED).isNotNull();
        // P1
        assertThat(ExecutionStatus.WAITING_FOR_APPROVAL).isNotNull();
    }

    @Test
    void nodeStatusValues() {
        assertThat(NodeStatus.PENDING).isNotNull();
        assertThat(NodeStatus.RUNNING).isNotNull();
        assertThat(NodeStatus.COMPLETED).isNotNull();
        assertThat(NodeStatus.FAILED).isNotNull();
        assertThat(NodeStatus.CANCELLED).isNotNull();
        assertThat(NodeStatus.OUTCOME_UNKNOWN).isNotNull();
        assertThat(NodeStatus.WAITING_FOR_APPROVAL).isNotNull();
    }
}
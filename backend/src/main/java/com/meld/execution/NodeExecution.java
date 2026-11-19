package com.meld.execution;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * NodeExecution entity per DATABASE.md §5.
 * Per-node execution state and result with attempt tracking.
 * Unique constraint on (execution_id, node_key, attempt_number).
 */
@Entity
@Table(name = "node_executions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_node_executions_execution_node_attempt", columnNames = {"execution_id", "node_key", "attempt_number"}),
        @UniqueConstraint(name = "uk_node_executions_execution_id", columnNames = {"execution_id", "id"})
})
public class NodeExecution {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "execution_id", nullable = false)
    private Execution execution;

    @Column(name = "node_key", nullable = false, length = 120)
    private String nodeKey;

    @Column(name = "status", nullable = false, length = 40)
    private String status;

    @Column(name = "attempt_number", nullable = false)
    private Integer attemptNumber = 1;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input")
    private String input;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "output")
    private String output;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected NodeExecution() {}

    public NodeExecution(UUID id, Execution execution, String nodeKey, String status, Integer attemptNumber) {
        this.id = id;
        this.execution = execution;
        this.nodeKey = nodeKey;
        this.status = status;
        this.attemptNumber = attemptNumber;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Execution getExecution() { return execution; }
    public void setExecution(Execution execution) { this.execution = execution; }
    public String getNodeKey() { return nodeKey; }
    public void setNodeKey(String nodeKey) { this.nodeKey = nodeKey; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getAttemptNumber() { return attemptNumber; }
    public void setAttemptNumber(Integer attemptNumber) { this.attemptNumber = attemptNumber; }
    public String getInput() { return input; }
    public void setInput(String input) { this.input = input; }
    public String getOutput() { return output; }
    public void setOutput(String output) { this.output = output; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}

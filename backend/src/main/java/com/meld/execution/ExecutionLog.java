package com.meld.execution;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * ExecutionLog entity per DATABASE.md §5.
 * Structured execution events with level, event_type, message, metadata.
 * Index on (execution_id, created_at) for log retrieval.
 */
@Entity
@Table(name = "execution_logs", indexes = {
        @Index(name = "idx_execution_logs_execution_created", columnList = "execution_id, created_at")
})
public class ExecutionLog {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "execution_id", nullable = false)
    private Execution execution;

    @Column(name = "node_execution_id")
    private UUID nodeExecutionId;

    @Column(name = "level", nullable = false, length = 20)
    private String level;

    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false)
    private String metadata = "{}";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ExecutionLog() {}

    public ExecutionLog(UUID id, Execution execution, String level, String eventType, String message) {
        this.id = id;
        this.execution = execution;
        this.level = level;
        this.eventType = eventType;
        this.message = message;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Execution getExecution() { return execution; }
    public void setExecution(Execution execution) { this.execution = execution; }
    public UUID getNodeExecutionId() { return nodeExecutionId; }
    public void setNodeExecutionId(UUID nodeExecutionId) { this.nodeExecutionId = nodeExecutionId; }
    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
    public Instant getCreatedAt() { return createdAt; }
}

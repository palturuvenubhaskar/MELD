package com.meld.execution;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Execution entity per DATABASE.md §5.
 * Workflow execution records with durable queue semantics.
 *
 * Foreign keys are mapped as plain UUID columns: all three participate in
 * composite database foreign keys, so JPA associations would collide on the
 * shared columns. Tenant scoping is enforced at the database level (composite
 * FKs) and in repository queries.
 */
@Entity
@Table(name = "executions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_executions_workspace_id", columnNames = {"workspace_id", "id"})
})
public class Execution {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "workflow_id", nullable = false, updatable = false)
    private UUID workflowId;

    @Column(name = "workflow_version_id", nullable = false, updatable = false)
    private UUID workflowVersionId;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "status", nullable = false, length = 40)
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input", nullable = false)
    private String input = "{}";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "output")
    private String output;

    @Column(name = "idempotency_key", length = 160)
    private String idempotencyKey;

    @Column(name = "request_hash", length = 64)
    private String requestHash;

    @Column(name = "lease_owner", length = 160)
    private String leaseOwner;

    @Column(name = "heartbeat_at")
    private Instant heartbeatAt;

    @Column(name = "lease_expires_at")
    private Instant leaseExpiresAt;

    @Column(name = "cancel_requested_at")
    private Instant cancelRequestedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_by")
    private UUID createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Execution() {}

    public Execution(UUID id, UUID workspaceId, UUID workflowId, UUID workflowVersionId, String status) {
        this.id = id;
        this.workspaceId = workspaceId;
        this.workflowId = workflowId;
        this.workflowVersionId = workflowVersionId;
        this.status = status;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getWorkflowId() { return workflowId; }
    public void setWorkflowId(UUID workflowId) { this.workflowId = workflowId; }
    public UUID getWorkflowVersionId() { return workflowVersionId; }
    public void setWorkflowVersionId(UUID workflowVersionId) { this.workflowVersionId = workflowVersionId; }
    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID workspaceId) { this.workspaceId = workspaceId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getInput() { return input; }
    public void setInput(String input) { this.input = input; }
    public String getOutput() { return output; }
    public void setOutput(String output) { this.output = output; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getRequestHash() { return requestHash; }
    public void setRequestHash(String requestHash) { this.requestHash = requestHash; }
    public String getLeaseOwner() { return leaseOwner; }
    public void setLeaseOwner(String leaseOwner) { this.leaseOwner = leaseOwner; }
    public Instant getHeartbeatAt() { return heartbeatAt; }
    public void setHeartbeatAt(Instant heartbeatAt) { this.heartbeatAt = heartbeatAt; }
    public Instant getLeaseExpiresAt() { return leaseExpiresAt; }
    public void setLeaseExpiresAt(Instant leaseExpiresAt) { this.leaseExpiresAt = leaseExpiresAt; }
    public Instant getCancelRequestedAt() { return cancelRequestedAt; }
    public void setCancelRequestedAt(Instant cancelRequestedAt) { this.cancelRequestedAt = cancelRequestedAt; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}

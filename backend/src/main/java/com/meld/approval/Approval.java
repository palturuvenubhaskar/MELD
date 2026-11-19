package com.meld.approval;

import com.meld.execution.Execution;
import com.meld.user.User;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Approval entity per DATABASE.md §5 (P1).
 * Human approval requests and decisions.
 */
@Entity
@Table(name = "approvals")
public class Approval {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "execution_id", nullable = false)
    private Execution execution;

    @Column(name = "node_key", nullable = false, length = 120)
    private String nodeKey;

    @Column(name = "status", nullable = false, length = 30)
    private String status = "PENDING";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "requested_payload", nullable = false)
    private String requestedPayload = "{}";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "decision_payload")
    private String decisionPayload;

    @CreationTimestamp
    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by")
    private User decidedBy;

    protected Approval() {}

    public Approval(UUID id, Execution execution, String nodeKey) {
        this.id = id;
        this.execution = execution;
        this.nodeKey = nodeKey;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Execution getExecution() { return execution; }
    public void setExecution(Execution execution) { this.execution = execution; }
    public String getNodeKey() { return nodeKey; }
    public void setNodeKey(String nodeKey) { this.nodeKey = nodeKey; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRequestedPayload() { return requestedPayload; }
    public void setRequestedPayload(String requestedPayload) { this.requestedPayload = requestedPayload; }
    public String getDecisionPayload() { return decisionPayload; }
    public void setDecisionPayload(String decisionPayload) { this.decisionPayload = decisionPayload; }
    public Instant getRequestedAt() { return requestedAt; }
    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }
    public User getDecidedBy() { return decidedBy; }
    public void setDecidedBy(User decidedBy) { this.decidedBy = decidedBy; }
}

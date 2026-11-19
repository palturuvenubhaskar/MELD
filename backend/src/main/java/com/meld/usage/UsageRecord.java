package com.meld.usage;

import com.meld.workspace.Workspace;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * UsageRecord entity per DATABASE.md §5.
 * Token counts, duration, cost estimates per execution.
 */
@Entity
@Table(name = "usage_records")
public class UsageRecord {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @Column(name = "execution_id")
    private UUID executionId;

    @Column(name = "provider", length = 60)
    private String provider;

    @Column(name = "model", length = 120)
    private String model;

    @Column(name = "input_tokens", nullable = false)
    private Long inputTokens = 0L;

    @Column(name = "output_tokens", nullable = false)
    private Long outputTokens = 0L;

    @Column(name = "estimated_cost", precision = 14, scale = 6)
    private java.math.BigDecimal estimatedCost;

    @Column(name = "duration_ms")
    private Long durationMs;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected UsageRecord() {}

    public UsageRecord(UUID id, Workspace workspace, String provider, String model) {
        this.id = id;
        this.workspace = workspace;
        this.provider = provider;
        this.model = model;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Workspace getWorkspace() { return workspace; }
    public void setWorkspace(Workspace workspace) { this.workspace = workspace; }
    public UUID getExecutionId() { return executionId; }
    public void setExecutionId(UUID executionId) { this.executionId = executionId; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public Long getInputTokens() { return inputTokens; }
    public void setInputTokens(Long inputTokens) { this.inputTokens = inputTokens; }
    public Long getOutputTokens() { return outputTokens; }
    public void setOutputTokens(Long outputTokens) { this.outputTokens = outputTokens; }
    public java.math.BigDecimal getEstimatedCost() { return estimatedCost; }
    public void setEstimatedCost(java.math.BigDecimal estimatedCost) { this.estimatedCost = estimatedCost; }
    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
    public Instant getCreatedAt() { return createdAt; }
}

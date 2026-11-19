package com.meld.workflow;

import com.meld.workspace.Workspace;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * WorkflowVersion entity per DATABASE.md §5.
 * Immutable workflow versions with snapshots of non-secret agent settings.
 * Composite unique constraint on (workflow_id, id) for FK references.
 */
@Entity
@Table(name = "workflow_versions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_workflow_versions_workflow_version", columnNames = {"workflow_id", "version_number"}),
        @UniqueConstraint(name = "uk_workflow_versions_workflow_id", columnNames = {"workflow_id", "id"})
})
public class WorkflowVersion {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workflow_id", nullable = false)
    private Workflow workflow;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(name = "published_at")
    private Instant publishedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected WorkflowVersion() {}

    public WorkflowVersion(UUID id, Workflow workflow, Integer versionNumber) {
        this.id = id;
        this.workflow = workflow;
        this.versionNumber = versionNumber;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Workflow getWorkflow() { return workflow; }
    public void setWorkflow(Workflow workflow) { this.workflow = workflow; }
    public Integer getVersionNumber() { return versionNumber; }
    public void setVersionNumber(Integer versionNumber) { this.versionNumber = versionNumber; }
    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
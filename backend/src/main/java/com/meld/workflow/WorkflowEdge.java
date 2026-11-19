package com.meld.workflow;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * WorkflowEdge entity per DATABASE.md §5.
 * Directed connections between nodes within a workflow version.
 * Composite FKs to workflow_nodes(workflow_version_id, node_key) for source and target.
 */
@Entity
@Table(name = "workflow_edges")
public class WorkflowEdge {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns({
            @JoinColumn(name = "workflow_version_id", referencedColumnName = "workflow_version_id", nullable = false, insertable = false, updatable = false),
            @JoinColumn(name = "source_node_key", referencedColumnName = "node_key", nullable = false, insertable = false, updatable = false)
    })
    private WorkflowNode sourceNode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns({
            @JoinColumn(name = "workflow_version_id", referencedColumnName = "workflow_version_id", nullable = false, insertable = false, updatable = false),
            @JoinColumn(name = "target_node_key", referencedColumnName = "node_key", nullable = false, insertable = false, updatable = false)
    })
    private WorkflowNode targetNode;

    @Column(name = "workflow_version_id", nullable = false)
    private UUID workflowVersionId;

    @Column(name = "source_node_key", nullable = false, length = 120)
    private String sourceNodeKey;

    @Column(name = "target_node_key", nullable = false, length = 120)
    private String targetNodeKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "condition")
    private String condition;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected WorkflowEdge() {}

    public WorkflowEdge(UUID id, WorkflowVersion workflowVersion, WorkflowNode sourceNode, WorkflowNode targetNode) {
        this.id = id;
        this.workflowVersionId = workflowVersion.getId();
        this.sourceNode = sourceNode;
        this.targetNode = targetNode;
        this.sourceNodeKey = sourceNode.getNodeKey();
        this.targetNodeKey = targetNode.getNodeKey();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public WorkflowNode getSourceNode() { return sourceNode; }
    public void setSourceNode(WorkflowNode sourceNode) { this.sourceNode = sourceNode; }
    public WorkflowNode getTargetNode() { return targetNode; }
    public void setTargetNode(WorkflowNode targetNode) { this.targetNode = targetNode; }
    public UUID getWorkflowVersionId() { return workflowVersionId; }
    public String getSourceNodeKey() { return sourceNodeKey; }
    public String getTargetNodeKey() { return targetNodeKey; }
    public String getCondition() { return condition; }
    public void setCondition(String condition) { this.condition = condition; }
    public Instant getCreatedAt() { return createdAt; }
}

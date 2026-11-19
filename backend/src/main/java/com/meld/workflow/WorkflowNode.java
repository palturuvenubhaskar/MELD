package com.meld.workflow;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/**
 * WorkflowNode entity per DATABASE.md §5.
 * Nodes belonging to a workflow version with JSONB configuration.
 * Unique constraint on (workflow_version_id, node_key).
 */
@Entity
@Table(name = "workflow_nodes", uniqueConstraints = {
        @UniqueConstraint(name = "uk_workflow_nodes_version_key", columnNames = {"workflow_version_id", "node_key"})
})
public class WorkflowNode {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workflow_version_id", nullable = false)
    private WorkflowVersion workflowVersion;

    @Column(name = "node_key", nullable = false, length = 120)
    private String nodeKey;

    @Column(name = "node_type", nullable = false, length = 40)
    private String nodeType;

    @Column(name = "label", length = 160)
    private String label;

    @Column(name = "position_x", nullable = false)
    private Double positionX = 0.0;

    @Column(name = "position_y", nullable = false)
    private Double positionY = 0.0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "configuration", nullable = false)
    private String configuration = "{}";

    protected WorkflowNode() {}

    public WorkflowNode(UUID id, WorkflowVersion workflowVersion, String nodeKey, String nodeType) {
        this.id = id;
        this.workflowVersion = workflowVersion;
        this.nodeKey = nodeKey;
        this.nodeType = nodeType;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public WorkflowVersion getWorkflowVersion() { return workflowVersion; }
    public void setWorkflowVersion(WorkflowVersion workflowVersion) { this.workflowVersion = workflowVersion; }
    public String getNodeKey() { return nodeKey; }
    public void setNodeKey(String nodeKey) { this.nodeKey = nodeKey; }
    public String getNodeType() { return nodeType; }
    public void setNodeType(String nodeType) { this.nodeType = nodeType; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public Double getPositionX() { return positionX; }
    public void setPositionX(Double positionX) { this.positionX = positionX; }
    public Double getPositionY() { return positionY; }
    public void setPositionY(Double positionY) { this.positionY = positionY; }
    public String getConfiguration() { return configuration; }
    public void setConfiguration(String configuration) { this.configuration = configuration; }
}

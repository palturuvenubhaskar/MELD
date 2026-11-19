package com.meld.agent;

import com.meld.tool.Tool;
import com.meld.workspace.Workspace;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Agent-Tool many-to-many association per DATABASE.md §5.
 * Composite PK (agent_id, tool_id).
 * Composite FKs to agents(workspace_id, id) and tools(workspace_id, id).
 */
@Entity
@Table(name = "agent_tools")
@IdClass(AgentToolId.class)
public class AgentTool {

    @Id
    @Column(name = "agent_id", nullable = false)
    private UUID agentId;

    @Id
    @Column(name = "tool_id", nullable = false)
    private UUID toolId;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Relationships
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
            @JoinColumn(name = "workspace_id", referencedColumnName = "workspace_id", insertable = false, updatable = false),
            @JoinColumn(name = "agent_id", referencedColumnName = "id", insertable = false, updatable = false)
    })
    private Agent agent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
            @JoinColumn(name = "workspace_id", referencedColumnName = "workspace_id", insertable = false, updatable = false),
            @JoinColumn(name = "tool_id", referencedColumnName = "id", insertable = false, updatable = false)
    })
    private Tool tool;

    protected AgentTool() {}

    public AgentTool(UUID workspaceId, UUID agentId, UUID toolId) {
        this.workspaceId = workspaceId;
        this.agentId = agentId;
        this.toolId = toolId;
    }

    public UUID getAgentId() { return agentId; }
    public void setAgentId(UUID agentId) { this.agentId = agentId; }
    public UUID getToolId() { return toolId; }
    public void setToolId(UUID toolId) { this.toolId = toolId; }
    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID workspaceId) { this.workspaceId = workspaceId; }
    public Instant getCreatedAt() { return createdAt; }
    public Agent getAgent() { return agent; }
    public Tool getTool() { return tool; }
}
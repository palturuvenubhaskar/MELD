package com.meld.agent;

import java.io.Serializable;
import java.util.UUID;

/**
 * Composite primary key for AgentTool.
 * Matches (agent_id, tool_id) from DATABASE.md §5.
 */
public class AgentToolId implements Serializable {

    private UUID agentId;
    private UUID toolId;

    protected AgentToolId() {}

    public AgentToolId(UUID agentId, UUID toolId) {
        this.agentId = agentId;
        this.toolId = toolId;
    }

    public UUID getAgentId() { return agentId; }
    public void setAgentId(UUID agentId) { this.agentId = agentId; }
    public UUID getToolId() { return toolId; }
    public void setToolId(UUID toolId) { this.toolId = toolId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AgentToolId that = (AgentToolId) o;
        return agentId.equals(that.agentId) && toolId.equals(that.toolId);
    }

    @Override
    public int hashCode() {
        return agentId.hashCode() * 31 + toolId.hashCode();
    }
}
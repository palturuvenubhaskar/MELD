package com.meld.agent;

import com.meld.workspace.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * AgentTool repository per DATABASE.md §5.
 */
@Repository
public interface AgentToolRepository extends JpaRepository<AgentTool, AgentToolId> {

    @Query("SELECT at FROM AgentTool at WHERE at.workspaceId = :workspaceId")
    List<AgentTool> findByWorkspaceId(@Param("workspaceId") UUID workspaceId);

    @Query("SELECT at FROM AgentTool at WHERE at.agentId = :agentId")
    List<AgentTool> findByAgentId(@Param("agentId") UUID agentId);

    @Query("SELECT at FROM AgentTool at WHERE at.toolId = :toolId")
    List<AgentTool> findByToolId(@Param("toolId") UUID toolId);

    Optional<AgentTool> findByAgentIdAndToolId(UUID agentId, UUID toolId);

    void deleteByAgentId(UUID agentId);

    void deleteByToolId(UUID toolId);
}
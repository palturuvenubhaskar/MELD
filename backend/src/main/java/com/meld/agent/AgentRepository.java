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
 * Agent repository per DATABASE.md §5.
 */
@Repository
public interface AgentRepository extends JpaRepository<Agent, UUID> {

    @Query("SELECT a FROM Agent a WHERE a.workspace = :workspace")
    List<Agent> findByWorkspace(@Param("workspace") Workspace workspace);

    @Query("SELECT a FROM Agent a WHERE a.workspace = :workspace AND a.name = :name")
    Optional<Agent> findByWorkspaceAndName(@Param("workspace") Workspace workspace, @Param("name") String name);

    @Query("SELECT a FROM Agent a WHERE a.id = :id AND a.workspace = :workspace")
    Optional<Agent> findByIdAndWorkspace(@Param("id") UUID id, @Param("workspace") Workspace workspace);

    boolean existsByWorkspaceAndName(Workspace workspace, String name);

    List<Agent> findByWorkspace_IdOrderByCreatedAtDesc(UUID workspaceId);

    Optional<Agent> findByIdAndWorkspace_Id(UUID id, UUID workspaceId);

    boolean existsByWorkspace_IdAndName(UUID workspaceId, String name);

    boolean existsByWorkspace_IdAndNameAndIdNot(UUID workspaceId, String name, UUID id);
}
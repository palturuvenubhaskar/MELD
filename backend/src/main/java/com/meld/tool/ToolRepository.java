package com.meld.tool;

import com.meld.workspace.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tool repository per DATABASE.md §5.
 */
@Repository
public interface ToolRepository extends JpaRepository<Tool, UUID> {

    @Query("SELECT t FROM Tool t WHERE t.workspace = :workspace")
    List<Tool> findByWorkspace(@Param("workspace") Workspace workspace);

    @Query("SELECT t FROM Tool t WHERE t.workspace = :workspace AND t.enabled = true")
    List<Tool> findEnabledByWorkspace(@Param("workspace") Workspace workspace);

    @Query("SELECT t FROM Tool t WHERE t.workspace = :workspace AND t.name = :name")
    Optional<Tool> findByWorkspaceAndName(@Param("workspace") Workspace workspace, @Param("name") String name);

    @Query("SELECT t FROM Tool t WHERE t.id = :id AND t.workspace = :workspace")
    Optional<Tool> findByIdAndWorkspace(@Param("id") UUID id, @Param("workspace") Workspace workspace);

    boolean existsByWorkspaceAndName(Workspace workspace, String name);
}
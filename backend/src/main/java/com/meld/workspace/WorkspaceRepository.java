package com.meld.workspace;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Workspace repository per DATABASE.md §5.
 */
@Repository
public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {

    Optional<Workspace> findBySlug(String slug);

    @Query("SELECT w FROM Workspace w JOIN WorkspaceMember m ON w.id = m.workspaceId WHERE m.userId = :userId")
    List<Workspace> findByMemberUserId(@Param("userId") UUID userId);

    boolean existsBySlug(String slug);
}
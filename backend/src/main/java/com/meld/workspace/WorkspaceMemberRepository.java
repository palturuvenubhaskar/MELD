package com.meld.workspace;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * WorkspaceMember repository per DATABASE.md §5.
 */
@Repository
public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, WorkspaceMemberId> {

    Optional<WorkspaceMember> findByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

    @Query("SELECT m FROM WorkspaceMember m WHERE m.workspaceId = :workspaceId")
    List<WorkspaceMember> findByWorkspaceId(@Param("workspaceId") UUID workspaceId);

    @Query("SELECT m FROM WorkspaceMember m WHERE m.userId = :userId")
    List<WorkspaceMember> findByUserId(@Param("userId") UUID userId);

    boolean existsByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

    @Query("SELECT m FROM WorkspaceMember m WHERE m.workspaceId = :workspaceId AND m.role = 'OWNER'")
    Optional<WorkspaceMember> findOwnerByWorkspaceId(@Param("workspaceId") UUID workspaceId);
}
package com.meld.workflow;

import com.meld.workspace.Workspace;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Workflow repository per DATABASE.md §5.
 */
@Repository
public interface WorkflowRepository extends JpaRepository<Workflow, UUID> {

    @Query("SELECT w FROM Workflow w WHERE w.workspace = :workspace")
    Page<Workflow> findByWorkspace(@Param("workspace") Workspace workspace, Pageable pageable);

    @Query("SELECT w FROM Workflow w WHERE w.workspace = :workspace AND w.status = :status")
    Page<Workflow> findByWorkspaceAndStatus(@Param("workspace") Workspace workspace, @Param("status") String status, Pageable pageable);

    @Query("SELECT w FROM Workflow w WHERE w.id = :id AND w.workspace = :workspace")
    Optional<Workflow> findByIdAndWorkspace(@Param("id") UUID id, @Param("workspace") Workspace workspace);

    @Query("SELECT w FROM Workflow w WHERE w.id = :id AND w.workspace.id = :workspaceId")
    Optional<Workflow> findByIdAndWorkspace_Id(@Param("id") UUID id, @Param("workspaceId") UUID workspaceId);

    /**
     * Serializes version-number allocation for one workflow. Without this lock
     * two concurrent saves could read the same MAX(version_number) and collide.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Workflow w WHERE w.id = :id AND w.workspace.id = :workspaceId")
    Optional<Workflow> findByIdAndWorkspace_IdForUpdate(@Param("id") UUID id, @Param("workspaceId") UUID workspaceId);

    @Query("SELECT w FROM Workflow w WHERE w.workspace = :workspace ORDER BY w.createdAt DESC")
    List<Workflow> findByWorkspaceOrderByCreatedAtDesc(@Param("workspace") Workspace workspace);
}

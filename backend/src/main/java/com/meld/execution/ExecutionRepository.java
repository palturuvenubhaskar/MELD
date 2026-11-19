package com.meld.execution;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Execution repository per DATABASE.md §5.
 * PostgreSQL is the durable queue source of truth.
 * Claim with FOR UPDATE SKIP LOCKED + expiring lease + heartbeat.
 */
@Repository
public interface ExecutionRepository extends JpaRepository<Execution, UUID> {

    @Query("SELECT e FROM Execution e WHERE e.workspaceId = :workspaceId ORDER BY e.createdAt DESC")
    Page<Execution> findByWorkspaceId(@Param("workspaceId") UUID workspaceId, Pageable pageable);

    @Query("SELECT e FROM Execution e WHERE e.workflowId = :workflowId ORDER BY e.createdAt DESC")
    Page<Execution> findByWorkflowId(@Param("workflowId") UUID workflowId, Pageable pageable);

    Optional<Execution> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    Optional<Execution> findByWorkspaceIdAndIdempotencyKey(UUID workspaceId, String idempotencyKey);

    @Query("""
            SELECT e FROM Execution e
            WHERE e.status = 'PENDING'
              AND (e.leaseExpiresAt IS NULL OR e.leaseExpiresAt < :now)
            ORDER BY e.createdAt ASC
            """)
    List<Execution> findClaimableExecutions(@Param("now") Instant now);

    @Query("""
            SELECT e FROM Execution e
            WHERE e.leaseOwner = :owner
              AND e.leaseExpiresAt < :now
            """)
    List<Execution> findExpiredLeasesByOwner(@Param("owner") String owner, @Param("now") Instant now);
}

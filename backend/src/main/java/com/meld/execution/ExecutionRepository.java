package com.meld.execution;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
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
            WHERE e.workspaceId = :workspaceId
              AND e.workflowId = :workflowId
            ORDER BY e.createdAt DESC
            """)
    Page<Execution> findByWorkspaceIdAndWorkflowId(@Param("workspaceId") UUID workspaceId,
                                                   @Param("workflowId") UUID workflowId,
                                                   Pageable pageable);

    /**
     * Claims one pending row. The row lock is held until the claiming
     * transaction commits, so two workers cannot claim the same execution.
     */
    @Query(value = """
            SELECT id FROM executions
            WHERE status = 'PENDING'
              AND (lease_expires_at IS NULL OR lease_expires_at < :now)
            ORDER BY created_at ASC
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<UUID> findNextClaimableId(@Param("now") Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            SELECT e FROM Execution e
            WHERE e.status = 'RUNNING'
              AND e.leaseExpiresAt IS NOT NULL
              AND e.leaseExpiresAt < :now
              AND (e.leaseOwner IS NULL OR e.leaseOwner <> :excludeOwner)
            ORDER BY e.createdAt ASC
            """)
    List<Execution> findExpiredRunning(@Param("now") Instant now,
                                       @Param("excludeOwner") String excludeOwner);

    /**
     * Extends only leases this worker still owns. A bulk update cannot clobber
     * a status change committed by the same worker after the read.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Execution e
               SET e.heartbeatAt = :now, e.leaseExpiresAt = :expiry
             WHERE e.leaseOwner = :owner
               AND e.status = 'RUNNING'
            """)
    int extendLeases(@Param("owner") String owner,
                     @Param("now") Instant now,
                     @Param("expiry") Instant expiry);

    /** Read-only view of queued work; the worker claims via the locked query. */
    @Query("""
            SELECT e FROM Execution e
            WHERE e.status = 'PENDING'
              AND (e.leaseExpiresAt IS NULL OR e.leaseExpiresAt < :now)
            ORDER BY e.createdAt ASC
            """)
    List<Execution> findClaimableExecutions(@Param("now") Instant now);
}

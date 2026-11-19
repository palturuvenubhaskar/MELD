package com.meld.approval;

import com.meld.execution.Execution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Approval repository per DATABASE.md §5 (P1).
 * Human approval requests and decisions.
 */
@Repository
public interface ApprovalRepository extends JpaRepository<Approval, UUID> {

    @Query("SELECT a FROM Approval a WHERE a.execution = :execution")
    List<Approval> findByExecution(@Param("execution") Execution execution);

    @Query("SELECT a FROM Approval a WHERE a.execution = :execution AND a.nodeKey = :nodeKey")
    Optional<Approval> findByExecutionAndNodeKey(@Param("execution") Execution execution, @Param("nodeKey") String nodeKey);

    @Query("SELECT a FROM Approval a WHERE a.status = 'PENDING' ORDER BY a.requestedAt ASC")
    List<Approval> findPendingApprovals();

    @Query("SELECT a FROM Approval a WHERE a.decidedBy.id = :userId ORDER BY a.decidedAt DESC")
    List<Approval> findByDecidedBy(@Param("userId") UUID userId);
}
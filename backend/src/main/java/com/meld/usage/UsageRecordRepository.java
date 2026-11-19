package com.meld.usage;

import com.meld.workspace.Workspace;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * UsageRecord repository per DATABASE.md §5.
 * Token counts, duration, cost estimates per execution.
 */
@Repository
public interface UsageRecordRepository extends JpaRepository<UsageRecord, UUID> {

    @Query("SELECT u FROM UsageRecord u WHERE u.workspace = :workspace ORDER BY u.createdAt DESC")
    Page<UsageRecord> findByWorkspace(@Param("workspace") Workspace workspace, Pageable pageable);

    List<UsageRecord> findByExecutionId(UUID executionId);

    @Query("SELECT u FROM UsageRecord u WHERE u.workspace = :workspace AND u.createdAt BETWEEN :start AND :end")
    List<UsageRecord> findByWorkspaceAndDateRange(
            @Param("workspace") Workspace workspace,
            @Param("start") Instant start,
            @Param("end") Instant end);

    @Query("SELECT SUM(u.inputTokens) FROM UsageRecord u WHERE u.workspace = :workspace AND u.createdAt BETWEEN :start AND :end")
    Long sumInputTokensByWorkspaceAndDateRange(
            @Param("workspace") Workspace workspace,
            @Param("start") Instant start,
            @Param("end") Instant end);

    @Query("SELECT SUM(u.outputTokens) FROM UsageRecord u WHERE u.workspace = :workspace AND u.createdAt BETWEEN :start AND :end")
    Long sumOutputTokensByWorkspaceAndDateRange(
            @Param("workspace") Workspace workspace,
            @Param("start") Instant start,
            @Param("end") Instant end);
}

package com.meld.execution;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * ExecutionLog repository per DATABASE.md §5.
 * Structured execution events with pagination.
 */
@Repository
public interface ExecutionLogRepository extends JpaRepository<ExecutionLog, UUID> {

    @Query("SELECT l FROM ExecutionLog l WHERE l.execution = :execution ORDER BY l.createdAt ASC")
    Page<ExecutionLog> findByExecution(@Param("execution") Execution execution, Pageable pageable);

    List<ExecutionLog> findByNodeExecutionIdOrderByCreatedAtAsc(UUID nodeExecutionId);

    @Query("SELECT l FROM ExecutionLog l WHERE l.execution = :execution AND l.level = :level ORDER BY l.createdAt ASC")
    Page<ExecutionLog> findByExecutionAndLevel(@Param("execution") Execution execution, @Param("level") String level, Pageable pageable);
}

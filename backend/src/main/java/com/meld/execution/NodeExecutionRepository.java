package com.meld.execution;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * NodeExecution repository per DATABASE.md §5.
 * Tracks per-node execution state and results with attempt numbers.
 */
@Repository
public interface NodeExecutionRepository extends JpaRepository<NodeExecution, UUID> {

    @Query("SELECT n FROM NodeExecution n WHERE n.execution = :execution ORDER BY n.nodeKey, n.attemptNumber")
    List<NodeExecution> findByExecution(@Param("execution") Execution execution);

    @Query("SELECT n FROM NodeExecution n WHERE n.execution = :execution ORDER BY n.startedAt ASC, n.nodeKey ASC")
    List<NodeExecution> findByExecutionOrdered(@Param("execution") Execution execution);

    @Query("SELECT n FROM NodeExecution n WHERE n.execution = :execution AND n.nodeKey = :nodeKey ORDER BY n.attemptNumber")
    List<NodeExecution> findByExecutionAndNodeKey(@Param("execution") Execution execution, @Param("nodeKey") String nodeKey);

    @Query("SELECT n FROM NodeExecution n WHERE n.execution = :execution AND n.nodeKey = :nodeKey AND n.attemptNumber = :attemptNumber")
    Optional<NodeExecution> findByExecutionAndNodeKeyAndAttemptNumber(
            @Param("execution") Execution execution,
            @Param("nodeKey") String nodeKey,
            @Param("attemptNumber") Integer attemptNumber);

    @Query("SELECT n FROM NodeExecution n WHERE n.execution = :execution AND n.status = 'RUNNING'")
    List<NodeExecution> findRunningByExecution(@Param("execution") Execution execution);
}
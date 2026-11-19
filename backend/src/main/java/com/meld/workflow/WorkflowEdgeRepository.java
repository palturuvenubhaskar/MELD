package com.meld.workflow;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * WorkflowEdge repository per DATABASE.md §5.
 * Edges reference nodes within the same workflow version.
 */
@Repository
public interface WorkflowEdgeRepository extends JpaRepository<WorkflowEdge, UUID> {

    @Query("SELECT e FROM WorkflowEdge e WHERE e.workflowVersionId = :workflowVersionId")
    List<WorkflowEdge> findByWorkflowVersionId(@Param("workflowVersionId") UUID workflowVersionId);

    @Query("SELECT e FROM WorkflowEdge e WHERE e.sourceNode = :sourceNode")
    List<WorkflowEdge> findBySourceNode(@Param("sourceNode") WorkflowNode sourceNode);

    @Query("SELECT e FROM WorkflowEdge e WHERE e.targetNode = :targetNode")
    List<WorkflowEdge> findByTargetNode(@Param("targetNode") WorkflowNode targetNode);

    void deleteByWorkflowVersionId(UUID workflowVersionId);
}
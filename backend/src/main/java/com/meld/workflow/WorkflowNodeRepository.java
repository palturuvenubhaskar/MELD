package com.meld.workflow;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * WorkflowNode repository per DATABASE.md §5.
 * Nodes and edges are the canonical definition for a workflow version.
 */
@Repository
public interface WorkflowNodeRepository extends JpaRepository<WorkflowNode, UUID> {

    @Query("SELECT n FROM WorkflowNode n WHERE n.workflowVersion = :workflowVersion")
    List<WorkflowNode> findByWorkflowVersion(@Param("workflowVersion") WorkflowVersion workflowVersion);

    @Query("SELECT n FROM WorkflowNode n WHERE n.workflowVersion = :workflowVersion AND n.nodeKey = :nodeKey")
    Optional<WorkflowNode> findByWorkflowVersionAndNodeKey(@Param("workflowVersion") WorkflowVersion workflowVersion, @Param("nodeKey") String nodeKey);

    @Query("SELECT n FROM WorkflowNode n WHERE n.id = :id AND n.workflowVersion = :workflowVersion")
    Optional<WorkflowNode> findByIdAndWorkflowVersion(@Param("id") UUID id, @Param("workflowVersion") WorkflowVersion workflowVersion);

    void deleteByWorkflowVersion(WorkflowVersion workflowVersion);
}
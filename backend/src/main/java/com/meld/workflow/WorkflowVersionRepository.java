package com.meld.workflow;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * WorkflowVersion repository per DATABASE.md §5.
 * Immutable versions - edits create new versions.
 */
@Repository
public interface WorkflowVersionRepository extends JpaRepository<WorkflowVersion, UUID> {

    @Query("SELECT v FROM WorkflowVersion v WHERE v.workflow = :workflow")
    List<WorkflowVersion> findByWorkflowOrderByVersionNumberDesc(@Param("workflow") Workflow workflow);

    @Query("SELECT v FROM WorkflowVersion v WHERE v.workflow = :workflow AND v.versionNumber = :versionNumber")
    Optional<WorkflowVersion> findByWorkflowAndVersionNumber(@Param("workflow") Workflow workflow, @Param("versionNumber") Integer versionNumber);

    @Query("SELECT v FROM WorkflowVersion v WHERE v.id = :id AND v.workflow = :workflow")
    Optional<WorkflowVersion> findByIdAndWorkflow(@Param("id") UUID id, @Param("workflow") Workflow workflow);

    @Query("SELECT MAX(v.versionNumber) FROM WorkflowVersion v WHERE v.workflow = :workflow")
    Integer findMaxVersionNumberByWorkflow(@Param("workflow") Workflow workflow);

    @Query("""
            SELECT v FROM WorkflowVersion v
            WHERE v.workflow = :workflow AND v.publishedAt IS NOT NULL
            ORDER BY v.versionNumber DESC
            """)
    List<WorkflowVersion> findPublishedByWorkflowOrderByVersionNumberDesc(@Param("workflow") Workflow workflow);

    @Query("SELECT v.versionNumber FROM WorkflowVersion v WHERE v.id = :id")
    Optional<Integer> findVersionNumberById(@Param("id") UUID id);
}

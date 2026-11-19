package com.meld.audit;

import com.meld.user.User;
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
 * AuditLog repository per DATABASE.md §5.
 * Security-sensitive and administrative activity history.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    @Query("SELECT a FROM AuditLog a WHERE a.workspace = :workspace ORDER BY a.createdAt DESC")
    Page<AuditLog> findByWorkspace(@Param("workspace") Workspace workspace, Pageable pageable);

    @Query("SELECT a FROM AuditLog a WHERE a.user = :user ORDER BY a.createdAt DESC")
    Page<AuditLog> findByUser(@Param("user") User user, Pageable pageable);

    @Query("SELECT a FROM AuditLog a WHERE a.workspace = :workspace AND a.eventType = :eventType ORDER BY a.createdAt DESC")
    Page<AuditLog> findByWorkspaceAndEventType(@Param("workspace") Workspace workspace, @Param("eventType") String eventType, Pageable pageable);

    @Query("SELECT a FROM AuditLog a WHERE a.workspace = :workspace AND a.createdAt BETWEEN :start AND :end ORDER BY a.createdAt DESC")
    List<AuditLog> findByWorkspaceAndDateRange(
            @Param("workspace") Workspace workspace,
            @Param("start") Instant start,
            @Param("end") Instant end);

    @Query("SELECT a FROM AuditLog a WHERE a.resourceType = :resourceType AND a.resourceId = :resourceId ORDER BY a.createdAt DESC")
    List<AuditLog> findByResource(@Param("resourceType") String resourceType, @Param("resourceId") UUID resourceId);
}
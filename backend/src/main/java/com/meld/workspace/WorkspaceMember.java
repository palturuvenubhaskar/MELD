package com.meld.workspace;

import com.meld.user.User;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Workspace membership with composite primary key per DATABASE.md §5.
 * Enforces one membership row per (workspace, user) pair.
 * Composite foreign keys make workspace ownership a database contract.
 */
@Entity
@Table(name = "workspace_members")
@IdClass(WorkspaceMemberId.class)
public class WorkspaceMember {

    @Id
    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "role", nullable = false, length = 30)
    private String role;

    @CreationTimestamp
    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    // Relationships
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", insertable = false, updatable = false)
    private Workspace workspace;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    protected WorkspaceMember() {}

    public WorkspaceMember(UUID workspaceId, UUID userId, String role) {
        this.workspaceId = workspaceId;
        this.userId = userId;
        this.role = role;
    }

    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID workspaceId) { this.workspaceId = workspaceId; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public Instant getJoinedAt() { return joinedAt; }
    public Workspace getWorkspace() { return workspace; }
    public User getUser() { return user; }
}
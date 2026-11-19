package com.meld.workspace;

import java.io.Serializable;
import java.util.UUID;

/**
 * Composite primary key for WorkspaceMember.
 * Matches (workspace_id, user_id) from DATABASE.md §5.
 */
public class WorkspaceMemberId implements Serializable {

    private UUID workspaceId;
    private UUID userId;

    protected WorkspaceMemberId() {}

    public WorkspaceMemberId(UUID workspaceId, UUID userId) {
        this.workspaceId = workspaceId;
        this.userId = userId;
    }

    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID workspaceId) { this.workspaceId = workspaceId; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WorkspaceMemberId that = (WorkspaceMemberId) o;
        return workspaceId.equals(that.workspaceId) && userId.equals(that.userId);
    }

    @Override
    public int hashCode() {
        return workspaceId.hashCode() * 31 + userId.hashCode();
    }
}
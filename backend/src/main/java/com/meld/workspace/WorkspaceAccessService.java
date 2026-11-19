package com.meld.workspace;

import com.meld.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * The single authorization seam for workspace-scoped operations
 * (DATABASE.md §7, RULES.md).
 *
 * <p>Every workspace-scoped service must call this with the authenticated user
 * id from the security context, never with an id supplied by the browser.
 */
@Service
public class WorkspaceAccessService {

    private final WorkspaceMemberRepository members;

    public WorkspaceAccessService(WorkspaceMemberRepository members) {
        this.members = members;
    }

    /**
     * Resolves membership or reports the workspace as not found. Non-members
     * must not be able to distinguish "exists but forbidden" from "missing".
     */
    public WorkspaceMember requireMembership(UUID workspaceId, UUID userId) {
        return members.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "WORKSPACE_NOT_FOUND", "Workspace not found"));
    }

    public WorkspaceMember requireRole(UUID workspaceId, UUID userId, WorkspaceRole required) {
        WorkspaceMember membership = requireMembership(workspaceId, userId);
        WorkspaceRole actual;
        try {
            actual = WorkspaceRole.parse(membership.getRole());
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Workspace role is not recognized");
        }
        if (!actual.atLeast(required)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Insufficient workspace role");
        }
        return membership;
    }
}

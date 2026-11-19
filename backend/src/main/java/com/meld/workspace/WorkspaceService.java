package com.meld.workspace;

import com.meld.common.exception.ApiException;
import com.meld.common.util.Normalizers;
import com.meld.user.User;
import com.meld.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Workspace and membership rules per ROADMAP.md M4.
 *
 * <p>All methods take the authenticated user id; the controller resolves it
 * from the security principal, never from the request body or path.
 */
@Service
public class WorkspaceService {

    private final WorkspaceRepository workspaces;
    private final WorkspaceMemberRepository members;
    private final UserRepository users;
    private final WorkspaceAccessService access;

    public WorkspaceService(WorkspaceRepository workspaces,
                            WorkspaceMemberRepository members,
                            UserRepository users,
                            WorkspaceAccessService access) {
        this.workspaces = workspaces;
        this.members = members;
        this.users = users;
        this.access = access;
    }

    @Transactional
    public WorkspaceDtos.WorkspaceResponse create(UUID userId, WorkspaceDtos.CreateWorkspaceRequest request) {
        String name = request.name().trim();
        String slug = (request.slug() == null || request.slug().isBlank())
                ? Normalizers.slug(name)
                : Normalizers.slug(request.slug());
        if (slug.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SLUG",
                    "Workspace name must contain at least one letter or digit");
        }
        if (workspaces.existsBySlug(slug)) {
            throw new ApiException(HttpStatus.CONFLICT, "SLUG_TAKEN", "Workspace slug is already in use");
        }

        Workspace workspace;
        try {
            workspace = workspaces.saveAndFlush(new Workspace(UUID.randomUUID(), name, slug, userId));
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "SLUG_TAKEN", "Workspace slug is already in use");
        }
        members.saveAndFlush(new WorkspaceMember(workspace.getId(), userId, WorkspaceRole.OWNER.name()));
        return toResponse(workspace, WorkspaceRole.OWNER.name());
    }

    @Transactional(readOnly = true)
    public List<WorkspaceDtos.WorkspaceResponse> list(UUID userId) {
        List<WorkspaceMember> memberships = members.findByUserId(userId);
        if (memberships.isEmpty()) {
            return List.of();
        }
        Map<UUID, Workspace> byId = workspaces.findAllById(
                        memberships.stream().map(WorkspaceMember::getWorkspaceId).toList())
                .stream()
                .collect(Collectors.toMap(Workspace::getId, Function.identity()));
        return memberships.stream()
                .map(membership -> {
                    Workspace workspace = byId.get(membership.getWorkspaceId());
                    return workspace == null ? null : toResponse(workspace, membership.getRole());
                })
                .filter(Objects::nonNull)
                .toList();
    }

    @Transactional(readOnly = true)
    public WorkspaceDtos.WorkspaceResponse get(UUID userId, UUID workspaceId) {
        WorkspaceMember membership = access.requireMembership(workspaceId, userId);
        Workspace workspace = workspaces.findById(workspaceId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "WORKSPACE_NOT_FOUND", "Workspace not found"));
        return toResponse(workspace, membership.getRole());
    }

    @Transactional(readOnly = true)
    public List<WorkspaceDtos.MemberResponse> listMembers(UUID userId, UUID workspaceId) {
        access.requireMembership(workspaceId, userId);
        List<WorkspaceMember> memberships = members.findByWorkspaceId(workspaceId);
        Map<UUID, User> usersById = users.findAllById(
                        memberships.stream().map(WorkspaceMember::getUserId).toList())
                .stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return memberships.stream()
                .map(membership -> toMember(membership, usersById.get(membership.getUserId())))
                .filter(Objects::nonNull)
                .toList();
    }

    @Transactional
    public WorkspaceDtos.MemberResponse addMember(UUID actorId, UUID workspaceId,
                                                  WorkspaceDtos.AddMemberRequest request) {
        access.requireRole(workspaceId, actorId, WorkspaceRole.OWNER);

        WorkspaceRole role = WorkspaceRole.parse(
                request.role() == null || request.role().isBlank() ? WorkspaceRole.MEMBER.name() : request.role());
        if (role != WorkspaceRole.MEMBER) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ROLE",
                    "Only MEMBER can be added; ownership is assigned when the workspace is created");
        }

        User target = users.findByEmailIgnoreCase(Normalizers.email(request.email()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "USER_NOT_FOUND", "No user with that email"));
        if (members.existsByWorkspaceIdAndUserId(workspaceId, target.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "MEMBER_EXISTS", "User is already a workspace member");
        }

        WorkspaceMember membership = members.saveAndFlush(
                new WorkspaceMember(workspaceId, target.getId(), role.name()));
        return new WorkspaceDtos.MemberResponse(
                target.getId(), target.getEmail(), target.getDisplayName(),
                membership.getRole(), membership.getJoinedAt());
    }

    @Transactional
    public void removeMember(UUID actorId, UUID workspaceId, UUID targetUserId) {
        access.requireRole(workspaceId, actorId, WorkspaceRole.OWNER);
        WorkspaceMember target = members.findByWorkspaceIdAndUserId(workspaceId, targetUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "MEMBER_NOT_FOUND", "User is not a workspace member"));
        if (WorkspaceRole.OWNER.name().equals(target.getRole())) {
            throw new ApiException(HttpStatus.CONFLICT, "CANNOT_REMOVE_OWNER",
                    "The workspace owner cannot be removed");
        }
        members.delete(target);
    }

    private static WorkspaceDtos.WorkspaceResponse toResponse(Workspace workspace, String role) {
        return new WorkspaceDtos.WorkspaceResponse(
                workspace.getId(), workspace.getName(), workspace.getSlug(), role, workspace.getCreatedAt());
    }

    private static WorkspaceDtos.MemberResponse toMember(WorkspaceMember membership, User user) {
        if (user == null) {
            return null;
        }
        return new WorkspaceDtos.MemberResponse(
                user.getId(), user.getEmail(), user.getDisplayName(),
                membership.getRole(), membership.getJoinedAt());
    }
}

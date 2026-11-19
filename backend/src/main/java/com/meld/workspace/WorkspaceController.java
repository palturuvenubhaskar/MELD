package com.meld.workspace;

import com.meld.auth.MeldUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Workspace endpoints per ARCHITECTURE.md §7.
 * Every operation resolves the caller from the security principal and verifies
 * membership in the service layer.
 */
@RestController
@RequestMapping("/api/v1/workspaces")
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @GetMapping
    public List<WorkspaceDtos.WorkspaceResponse> list(Authentication authentication) {
        return workspaceService.list(currentUserId(authentication));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkspaceDtos.WorkspaceResponse create(
            @Valid @RequestBody WorkspaceDtos.CreateWorkspaceRequest request,
            Authentication authentication) {
        return workspaceService.create(currentUserId(authentication), request);
    }

    @GetMapping("/{workspaceId}")
    public WorkspaceDtos.WorkspaceResponse get(@PathVariable UUID workspaceId,
                                               Authentication authentication) {
        return workspaceService.get(currentUserId(authentication), workspaceId);
    }

    @GetMapping("/{workspaceId}/members")
    public List<WorkspaceDtos.MemberResponse> listMembers(@PathVariable UUID workspaceId,
                                                         Authentication authentication) {
        return workspaceService.listMembers(currentUserId(authentication), workspaceId);
    }

    @PostMapping("/{workspaceId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkspaceDtos.MemberResponse addMember(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody WorkspaceDtos.AddMemberRequest request,
            Authentication authentication) {
        return workspaceService.addMember(currentUserId(authentication), workspaceId, request);
    }

    @DeleteMapping("/{workspaceId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@PathVariable UUID workspaceId,
                             @PathVariable UUID userId,
                             Authentication authentication) {
        workspaceService.removeMember(currentUserId(authentication), workspaceId, userId);
    }

    private static UUID currentUserId(Authentication authentication) {
        return MeldUserPrincipal.current(authentication).getId();
    }
}

package com.meld.workspace;

import com.meld.auth.dto.AuthDtos;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Workspace endpoints per ARCHITECTURE.md §7.
 * All endpoints require authentication and workspace membership.
 */
@RestController
@RequestMapping("/api/v1/workspaces")
public class WorkspaceController {

    @GetMapping
    public ResponseEntity<List<AuthDtos.WorkspaceResponse>> listWorkspaces(Authentication auth) {
        // TODO: Return workspaces user is a member of
        return ResponseEntity.ok(List.of());
    }

    @PostMapping
    public ResponseEntity<AuthDtos.WorkspaceResponse> createWorkspace(@Valid @RequestBody AuthDtos.CreateWorkspaceRequest request,
                                                                       Authentication auth) {
        // TODO: Create workspace and add user as owner
        return ResponseEntity.status(501).build();
    }

    @GetMapping("/{workspaceId}")
    public ResponseEntity<AuthDtos.WorkspaceResponse> getWorkspace(@PathVariable String workspaceId,
                                                                    Authentication auth) {
        // TODO: Verify membership and return workspace
        return ResponseEntity.status(501).build();
    }

    @DeleteMapping("/{workspaceId}")
    public ResponseEntity<Void> deleteWorkspace(@PathVariable String workspaceId,
                                                 Authentication auth) {
        // TODO: Verify ownership and delete
        return ResponseEntity.status(501).build();
    }

    @PostMapping("/{workspaceId}/members")
    public ResponseEntity<Void> addMember(@PathVariable String workspaceId,
                                           @RequestBody Map<String, String> request,
                                           Authentication auth) {
        // TODO: Add member to workspace
        return ResponseEntity.status(501).build();
    }

    @DeleteMapping("/{workspaceId}/members/{userId}")
    public ResponseEntity<Void> removeMember(@PathVariable String workspaceId,
                                              @PathVariable String userId,
                                              Authentication auth) {
        // TODO: Remove member from workspace
        return ResponseEntity.status(501).build();
    }
}